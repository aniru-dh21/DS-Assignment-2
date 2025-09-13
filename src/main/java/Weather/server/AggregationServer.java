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
        // Recover from persistence
        try {
            weatherData.putAll(persistence.load());
            for (String id : weatherData.keySet()) {
                lastUpdate.put(id, System.currentTimeMillis());
            }
            System.out.println("Recovered " + weatherData.size() + " entries from persistence.");
        } catch (Exception e) {
            System.out.println("No valid persistence found, starting fresh.");
        }

        // Start worker thread to process requests
        new Thread(this::processRequests).start();

        // Expiry thread
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            lastUpdate.forEach((id, time) -> {
                if (now - time > 30_000) {
                    weatherData.remove(id);
                    lastUpdate.remove(id);
                }
            });
            persistSafely();
        }, 5, 5, TimeUnit.SECONDS);

        // Listen for clients
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("AggregationServer running on port " + port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private void processRequests() {
        while (true) {
            try {
                RequestTask task = requestQueue.take(); // block until request
                task.run(); // execute in order
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void persistSafely() {
        try {
            persistence.save(weatherData);
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
            HttpResponse response = new HttpResponse();

            switch (request.method) {
                case "GET":
                    JsonArray arr = new JsonArray();
                    weatherData.values().forEach(arr::add);

                    response.statusCode = 200;
                    response.statusMessage = "OK";
                    response.body = gson.toJson(arr);
                    response.headers.put("Content-Type", "application/json");
                    response.headers.put("Content-Length", String.valueOf(response.body.length()));
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));
                    break;

                case "PUT":
                    if (request.body == null || request.body.isEmpty()) {
                        response.statusCode = 204;
                        response.statusMessage = "No Content";
                    } else {
                        try {
                            JsonObject newEntry = gson.fromJson(request.body, JsonObject.class);
                            String id = newEntry.get("id").getAsString();
                            weatherData.put(id, newEntry);
                            lastUpdate.put(id, System.currentTimeMillis());

                            persistSafely();

                            response.statusCode = weatherData.size() == 1 ? 201 : 200;
                            response.statusMessage = (response.statusCode == 201) ? "Created" : "OK";
                            response.body = "{ \"ack\": true }";
                            response.headers.put("Content-Type", "application/json");
                            response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        } catch (Exception e) {
                            response.statusCode = 500;
                            response.statusMessage = "Internal Server Error";
                        }
                    }
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));
                    break;

                default:
                    response.statusCode = 400;
                    response.statusMessage = "Bad Request";
            }

            out.write(response.buildResponse());
            out.flush();
        }

        @Override
        public int compareTo(RequestTask other) {
            return Integer.compare(this.lamportTime, other.lamportTime);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler (Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                HttpRequest request = HttpParser.parseRequest(in);
                if (request == null) return;

                // Update Lamport clock
                int lamportTime = clock.tick(); // local event
                if (request.headers.containsKey("Lamport-Clock")) {
                    int clientClock = Integer.parseInt(request.headers.get("Lamport-Clock"));
                    lamportTime = clock.update(clientClock);
                }

                // Add task to queue
                requestQueue.put(new RequestTask(request, out, lamportTime));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer(port).start();
    }
}