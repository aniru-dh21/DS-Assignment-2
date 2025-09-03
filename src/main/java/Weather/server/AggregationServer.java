package Weather.server;

import Weather.util.LamportClock;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class AggregationServer {
    private static final int DEFAULT_PORT = 8080;
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
                // Todo: Parsing and Handling Requests
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
