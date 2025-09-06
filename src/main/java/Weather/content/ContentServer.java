package Weather.content;

import Weather.util.LamportClock;

import java.io.*;

import org.json.JSONObject;

public class ContentServer {
    private final LamportClock clock = new LamportClock();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ContentServer <port> <data>");
            return;
        }

        String serverAddr = args[0];
        String filePath = args[1];

        ContentServer cs = new ContentServer();
    }

    private JSONObject readFile(String path) throws IOException {
        JSONObject json = new JSONObject();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    json.put(parts[0], parts[1].trim());
                }
            }
        }
        return json;
    }

    private void sendPut(String serverAddr, JSONObject json) throws IOException {
        // Todo: Building PUT HTTP Request and sending request via socket
    }
}
