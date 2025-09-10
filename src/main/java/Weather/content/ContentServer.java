package Weather.content;

import Weather.util.LamportClock;

import java.io.*;
import java.net.Socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ContentServer {
    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ContentServer <port> <data>");
            return;
        }

        String serverAddr = args[0];
        String[] parts = serverAddr.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        String filePath = args[1];

        ContentServer cs = new ContentServer();
        JsonObject weatherJson = cs.readFile(filePath);
        cs.sendPut(host, port, weatherJson);
    }

    private JsonObject readFile(String path) throws IOException {
        JsonObject json = new JsonObject();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    json.addProperty(parts[0], parts[1].trim());
                }
            }
        }
        return json;
    }

    private void sendPut(String host, int port, JsonObject json) throws IOException {
        clock.tick();

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

            // Read server response
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) break;
                System.out.println(line);
                if (line.startsWith("Lamport-Clock:")) {
                    int serverTime = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(serverTime);
                }
            }
        }
    }
}
