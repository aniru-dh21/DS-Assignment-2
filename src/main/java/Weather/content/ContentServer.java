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

        ContentServer cs = new ContentServer(host, port, filePath);
        cs.start();
    }

    public void start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject weatherJson = readFile(filePath);
                sendPut(weatherJson);
            } catch (Exception e) {
                System.err.println("Failed to PUT: " + e.getMessage());
            }
        }, 0, 20, TimeUnit.SECONDS);
    }

    private JsonObject readFile(String path) throws IOException {
        JsonObject json = new JsonObject();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    json.addProperty(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return json;
    }

    private void sendPut(JsonObject json) throws IOException {
        clock.tick(); // local event
        String body = gson.toJson(json);

        String request =
                "PUT /weather.json HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "User-Agent: ContentServer/1.0\r\n" +
                        "Lamport-Clock: " + clock.getTime() + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + body.length() + "\r\n" +
                        "\r\n" +
                        body;

        try (Socket socket = new Socket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.write(request);
            out.flush();

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                System.out.println("[ContentServer] " + line);
                if (line.startsWith("Lamport-Clock:")) {
                    int serverTime = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(serverTime);
                }
            }
        }
    }
}