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
            System.out.println("No valid persistence found, starting fresh.");
        }

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
        System.out.println("AggregationServer running on port " + port);

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
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

    private class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            BufferedReader in = null;
            PrintWriter out = null;

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                HttpRequest request = HttpParser.parseRequest(in);

                if (request == null) {
                    System.err.println("Failed to parse HTTP request");
                    return;
                }

                // Update Lamport Clock
                int lamportTime = clock.tick(); // local event

                if (request.headers.containsKey("Lamport-Clock")) {
                    int clientClock = Integer.parseInt(request.headers.get("Lamport-Clock"));
                    lamportTime = clock.update(clientClock);
                }

                processRequest(request, out, lamportTime);

            } catch (Exception e) {
                System.err.println("Error in ClientHandler: " + e.getMessage());
            } finally {
                // Close resources
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client connection: " + e.getMessage());
                }
            }
        }

        private void processRequest(HttpRequest request, PrintWriter out, int lamportTime) {
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

                            boolean isNew = !weatherData.containsKey(id);
                            weatherData.put(id, newEntry);
                            lastUpdate.put(id, System.currentTimeMillis());

                            persistSafely();

                            response.statusCode = isNew ? 201 : 200;
                            response.statusMessage = (response.statusCode == 201) ? "Created" : "OK";
                            response.body = "{ \"ack\": true }";
                            response.headers.put("Content-Type", "application/json");
                            response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        } catch (Exception e) {
                            System.err.println("Error processing PUT request: " + e.getMessage());
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
                    response.statusCode = 400;
                    response.statusMessage = "Bad Request";
                    response.headers.put("Lamport-Clock", String.valueOf(lamportTime));
            }

            String responseString = response.buildResponse();
            out.write(responseString);
            out.flush();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer(port).start();
    }
}