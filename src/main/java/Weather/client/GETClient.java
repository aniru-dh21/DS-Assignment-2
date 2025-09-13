package Weather.client;

import Weather.util.LamportClock;

import java.io.*;
import java.net.Socket;

public class GETClient {
    private final LamportClock clock = new LamportClock();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GETClient <host:port>");
            return;
        }

        String[] parts = args[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        GETClient client = new GETClient();
        client.sendGet(host, port);
    }

    private void sendGet(String host, int port) throws Exception {
        clock.tick(); // local event
        String request =
                "GET /weather.json HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "User-Agent: GETClient/1.0\r\n" +
                        "Lamport-Clock: " + clock.getTime() + "\r\n" +
                        "\r\n";

        try (Socket socket = new Socket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.write(request);
            out.flush();

            // Status + headers
            String line;
            int contentLength = 0;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) break;
                System.out.println("[GETClient] " + line);

                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
                if (line.startsWith("Lamport-Clock:")) {
                    int serverTime = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(serverTime);
                    System.out.println("[GETClient] Updated Lamport Clock = " + clock.getTime());
                }
            }

            // Body
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                in.read(buf, 0, contentLength);
                String body = new String(buf);
                System.out.println("\n[GETClient] Weather Data:\n" + body);
            }
        }
    }
}