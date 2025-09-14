package Weather.content;

import com.google.gson.*;
import Weather.util.LamportClock;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

public class ContentServer {
    private final LamportClock clock = new LamportClock();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String host;
    private final int port;
    private final String filePath;

    public ContentServer(String host, int port, String filePath) {
        this.host = host;
        this.port = port;
        this.filePath = filePath;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ContentServer <host:port> <datafile>");
            return;
        }

        String[] parts = args[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        String filePath = args[1];

        System.out.println("Starting ContentServer:");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("FilePath: " + filePath);

        ContentServer cs = new ContentServer(host, port, filePath);
        cs.start();
    }

    public void start() {
        System.out.println("ContentServer started. Will send data every 20 seconds...");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("\n[" + java.time.LocalTime.now() + "] Attempting to read file and send PUT request...");

                // Check if file exists
                File file = new File(filePath);
                if (!file.exists()) {
                    System.err.println("Error: File does not exist: " + filePath);
                    return;
                }

                if (!file.canRead()) {
                    System.err.println("Error: Cannot read file: " + filePath);
                    return;
                }

                JsonObject weatherJson = readFile(filePath);
                System.out.println("Successfully read file. Data: " + gson.toJson(weatherJson));

                sendPut(weatherJson);
                System.out.println("PUT request completed successfully.");
            } catch (Exception e) {
                System.err.println("Failed to PUT: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 20, TimeUnit.SECONDS);

        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("ContentServer interrupted, shutting down...");
            scheduler.shutdown();
        }
    }

    private JsonObject readFile(String path) throws IOException {
        JsonObject json = new JsonObject();
        System.out.println("Reading file: " + path);

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                lineCount++;
                System.out.println("Line " + lineCount + ": " + line);

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    json.addProperty(key, value);
                    System.out.println("Added: " + key + " = " + value);
                } else {
                    System.out.println("Skipped invalid line (no ':' separator)");
                }
            }
            System.out.println("Total lines read: " + lineCount);
        }

        if (json.isEmpty()) {
            System.out.println("Warning: No valid data found in file!");
        }

        return json;
    }

    private void sendPut(JsonObject json) throws IOException {
        System.out.println("Preparing PUT request...");

        clock.tick(); // local event
        String body = gson.toJson(json);

        System.out.println("Request body: " + body);
        System.out.println("Lamport clock time: " + clock.getTime());

        String request =
                "PUT /weather.json HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "User-Agent: ContentServer/1.0\r\n" +
                        "Lamport-Clock: " + clock.getTime() + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + body.length() + "\r\n" +
                        "\r\n" +
                        body;

        System.out.println("Full HTTP request:");
        System.out.println(request.replace("\r\n", "\\r\\n\n"));

        System.out.println("Connecting to " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Connected Successfully. Sending request...");

            out.write(request);
            out.flush();

            System.out.println("Request sent. Reading response...");

            String line;
            boolean responseReceived = false;
            while ((line = in.readLine()) != null) {
                responseReceived = true;
                System.out.println("[Server Response] " + line);

                if (line.isEmpty()) {
                    break;
                }

                if (line.startsWith("Lamport-Clock:")) {
                    int serverTime = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(serverTime);
                    System.out.println("Updated Lamport clock to: " + clock.getTime());
                }
            }

            if (!responseReceived) {
                System.out.println("Warning: No response received from server");
            }

            System.out.println("Connection closed.");
        } catch (java.net.ConnectException e) {
            System.err.println("ERROR: Could not connect to server at " + host + ":" + port);
            System.err.println("Make sure the AggregationServer is running on that address.");
            throw e;
        } catch (java.net.UnknownHostException e) {
            System.err.println("ERROR: Unknown host: " + host);
            throw e;
        }
    }
}