package Weather.server;

import Weather.util.HttpParser;
import Weather.util.HttpRequest;
import Weather.util.HttpResponse;
import Weather.util.LamportClock;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private final int port;
    private final LamportClock clock = new LamportClock();
    private final Map<String, JsonObject> Data = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public AggregationServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is running on the port " + port);

        // Use of Threads to clean expired data
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            lastUpdate.forEach((id, time) -> {
                if (now - time > 30000) {
                    Data.remove(id);
                    lastUpdate.remove(id);
                }
            });
        }, 5, 5, TimeUnit.SECONDS);

        // Accepting Clients
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                HttpRequest request = HttpParser.parseRequest(in);
                HttpResponse response = new HttpResponse();

                if (request == null) {
                    response.statusCode = 400;
                    response.statusMessage = "Bad Request";
                    out.write(response.buildResponse());
                    out.flush();
                    return;
                }

                // Handle Lamport clock from request
                if (request.headers.containsKey("Lamport-Clock")) {
                    int clientTime = Integer.parseInt(request.headers.get("Lamport-Clock"));
                    clock.update(clientTime);
                }

                clock.tick();

                switch (request.method) {
                    case "GET":
                        handleGet(response);
                        break;
                    case "PUT":
                        handlePut(request, response);
                        break;
                    default:
                        response.statusCode = 400;
                        response.statusMessage = "Bad Request";
                }

                // Add Lamport Clock to response
                response.headers.put("Lamport-Clock", String.valueOf(clock.getTime()));
                sendResponse(out, response);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleGet(HttpResponse response) {
        if (Data.isEmpty()) {
            response.statusCode = 200;
            response.statusMessage = "OK";
            response.body = "{ \"message\" : \"No weather data available\" }";
        } else {
            response.statusCode = 200;
            response.statusMessage = "OK";

            // Return all weather data or the most recent one
            if (Data.size() == 1) {
                response.body = gson.toJson(Data.values().iterator().next());
            } else {
                // Return all data as an array
                JsonObject allData = new JsonObject();
                allData.add("weather_data", gson.toJsonTree(Data.values()));
                response.body = gson.toJson(allData);
            }
        }

        response.headers.put("Content-Type", "application/json");
        response.headers.put("Content-Length", String.valueOf(response.body.length()));
    }

    private void handlePut(HttpRequest request, HttpResponse response) {
        if (request.body == null || request.body.isEmpty()) {
            response.statusCode = 204;
            response.statusMessage = "No Content";
            return;
        }

        try {
            // Parse the incoming weather data
            JsonObject weatherData = JsonParser.parseString(request.body).getAsJsonObject();

            // Use weather station ID as key, or generate one if not present
            String stationId = weatherData.has("id") ?
                    weatherData.get("id").getAsString() :
                    "station_" + System.currentTimeMillis();

            // Store the data
            Data.put(stationId, weatherData);
            lastUpdate.put(stationId, System.currentTimeMillis());

            response.statusCode = 201;
            response.statusMessage = "Created";
            response.body = "{ \"ack\": true, \"station_id\": \"" + stationId + "\" }";
            response.headers.put("Content-Type", "application/json");
            response.headers.put("Content-Length", String.valueOf(response.body.length()));

        } catch (Exception e) {
            System.err.println("Error processing PUT request: " + e.getMessage());
            response.statusCode = 400;
            response.statusMessage = "Bad Request";
            response.body = "{ \"error\": \"Invalid JSON data\" }";
            response.headers.put("Content-Type", "application/json");
            response.headers.put("Content-Length", String.valueOf(response.body.length()));
        }
    }

    private void sendResponse(PrintWriter out, HttpResponse response) {
        try {
            String responseStr = response.buildResponse();
            out.write(responseStr);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer(port).start();
    }
}
