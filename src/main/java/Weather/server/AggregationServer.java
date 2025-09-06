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

import org.json.JSONObject;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private final int port;
    private final LamportClock clock = new LamportClock();
    private final Map<String, JSONObject> Data = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();

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

                switch (request.method) {
                    case "GET":
                        response.statusCode = 200;
                        response.statusMessage = "OK";
                        response.body = "{ \"message\" : \"Weather Feed\" }";
                        response.headers.put("Content-Type", "application/json");
                        response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        break;

                    case "PUT":
                        if (request.body == null || request.body.isEmpty()) {
                            response.statusCode = 204;
                            response.statusMessage = "No Content";
                        } else {
                            response.statusCode = 201;
                            response.statusMessage = "Created";
                            response.body = "{ \"ack\": true }";
                            response.headers.put("Content-Type", "application/json");
                            response.headers.put("Content-Length", String.valueOf(response.body.length()));
                        }

                        break;

                    default:
                        response.statusCode = 400;
                        response.statusMessage = "Bad Request";
                }

                out.write(response.buildResponse());
                out.flush();

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
