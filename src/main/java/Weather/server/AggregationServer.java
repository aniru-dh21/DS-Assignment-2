package Weather.server;

import com.google.gson.*;
import Weather.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private final int port;
    private final LamportClock clock = new LamportClock();

    // Shared State
    private final Map<String, JsonObject> weatherData = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();
    private final PersistenceManager persistence = new PersistenceManager("weather.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Queue for concurrency control
    private final BlockingQueue<RequestTask> requestQueue = new PriorityBlockingQueue<>();

    public AggregationServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        System.out.println("Starting Aggregation Server on port " + port + "...");

        // Recover from persistence
        try {
            weatherData.putAll(persistence.load());
            for (String id : weatherData.keySet()) {
                lastUpdate.put(id, System.currentTimeMillis());
            }
            System.out.println("Recovered " + weatherData.size() + " entries from persistence.");
        } catch (Exception e) {
            System.out.println("No valid persistence found, starting fresh");
        }

        // Start worker thread to process requests
        Thread workerThread = new Thread(this::processRequests);
        workerThread.setDaemon(false);
        workerThread.start();
        System.out.println("Request processing thread started.");

        // Expiry thread
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            int removed = 0;
            Iterator<Map.Entry<String, Long>> iterator = lastUpdate.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (now - entry.getValue() > 30000) {
                    String id = entry.getKey();
                    weatherData.remove(id);
                    iterator.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                System.out.println("Expired " + removed + " entries due to timeout.");
                persistSafely();
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Listen for clients
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Aggregation running on port " + port);
        System.out.println("Waiting for client connections...");

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("\n[" + java.time.LocalTime.now() + "] New client connected: " +
                        clientSocket.getRemoteSocketAddress());

                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private void processRequests() {
        System.out.println("Request processor thread started and waiting for requests...");
        while (true) {
            try {
                RequestTask task = requestQueue.take(); // block until request
                System.out.println("Processing request with Lamport time: " + task.lamportTime);
                task.run();
                System.out.println("Request processed successfully.");
            } catch (Exception e) {
                System.err.println("Error processing requests: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void persistSafely() {
        try {
            persistence.save(weatherData);
            System.out.println("Data persisted successfully. Total Entries: " + weatherData.size());
        } catch (IOException e) {
            System.err.println("Persistence failed: " + e.getMessage());
        }
    }

    // Wraps client requests into tasks
    private class RequestTask implements Runnable, Comparable<RequestTask> {
        private final HttpRequest request;
        private final PrintWriter out;
        private final int lamportTime;

        RequestTask(HttpRequest request, PrintWriter out, int lamportTime) {
            this.request = request;
            this.out = out;
            this.lamportTime = lamportTime;
        }

        @Override
        public void run() {
            System.out.println("Executing " + request.method + " request");
            HttpResponse response = new HttpResponse();

            switch (request.method) {
                case "GET":
                    System.out.println("Processing GET request...");
                    JsonArray arr = new JsonArray();
                    weatherData.values().forEach(arr::add);

                    response.statusCode = 200;
                    response.statusMessage = "OK";
                    response.body = gson.toJson(arr);
                    response.headers.put("Content-Type", "application/json");
                    response.headers.put("Content-Length", String.valueOf(response.body.length()));
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));

                    System.out.println("GET response prepared. Returning " + weatherData.size() + " entries.");
                    break;

                case "PUT":
                    System.out.println("Processing PUT request...");
                    if (request.body == null || request.body.isEmpty()) {
                        System.out.println("PUT request has empty body");
                        response.statusCode = 204;
                        response.statusMessage = "No Content";
                    } else {
                        try {
                            System.out.println("PUT request body: " + request.body);
                            JsonObject newEntry = gson.fromJson(request.body, JsonObject.class);
                            String id = newEntry.get("id").getAsString();

                            boolean isNew = !weatherData.containsKey(id);
                            weatherData.put(id, newEntry);
                            lastUpdate.put(id, System.currentTimeMillis());

                            System.out.println("Successfully " + (isNew ? "added new" : "updated existing") +
                                    " entry with ID: " + id);
                            System.out.println("Total entries now: " + weatherData.size());

                            persistSafely();

                            response.statusCode = isNew ? 201 : 200;
                            response.statusMessage = (response.statusCode == 201) ? "Created" : "OK";
                            response.body = "{ \"ack\": true }";
                            response.headers.put("Content-Type", "application/json");
                            response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        } catch (Exception e) {
                            System.err.println("Error processing PUT request: " + e.getMessage());
                            e.printStackTrace();
                            response.statusCode = 500;
                            response.statusMessage = "Internal Server Error";
                            response.body = "{ \"error\": \"" + e.getMessage() + "\" }";
                            response.headers.put("Content-Type", "application/json");
                            response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        }
                    }
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));
                    break;

                default:
                    System.out.println("Unsupported method: " + request.method);
                    response.statusCode = 400;
                    response.statusMessage = "Bad Request";
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));
            }

            String responseString = response.buildResponse();
            System.out.println("Sending response:");
            System.out.println("Status: " + response.statusCode + " " + response.statusMessage);
            System.out.println("Headers: " + response.headers);
            System.out.println("Body length: " + (response.body != null ? response.body.length() : 0));

            out.write(responseString);
            out.flush();
            System.out.println("Response sent to client.");
        }

        @Override
        public int compareTo(RequestTask other) {
            return Integer.compare(this.lamportTime, other.lamportTime);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            System.out.println("ClientHandler started for: " + socket.getRemoteSocketAddress());

            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                System.out.println("Parsing incoming HTTP request...");
                HttpRequest request = HttpParser.parseRequest(in);

                if (request == null) {
                    System.err.println("Failed to parse HTTP request = request is null");
                    return;
                }

                System.out.println("Successfully parsed request:");
                System.out.println("Method: " + request.method);
                System.out.println("Path: " + request.path);
                System.out.println("Headers: " + request.headers);
                System.out.println("Body Length: " + (request.body != null ? request.body.length() : 0));

                // Update Lamport Clock
                int lamportTime = clock.tick(); // local event
                System.out.println("Local Lamport time after tick: " + lamportTime);

                if (request.headers.containsKey("Lamport-Clock")) {
                    int clientClock = Integer.parseInt(request.headers.get("Lamport-Clock"));
                    lamportTime = clock.update(clientClock);
                    System.out.println("Updated Lamport time after client sync: " + lamportTime);
                }

                // Add task to queue
                System.out.println("Adding request to processing queue...");
                requestQueue.put(new RequestTask(request, out, lamportTime));
                System.out.println("Request added to queue. Queue size: " + requestQueue.size());
            } catch (Exception e) {
                System.err.println("Error in ClientHandler: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    System.out.println("Client socket closed.");
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }

    public static void main (String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer(port).start();
    }
}