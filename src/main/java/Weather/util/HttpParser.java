package Weather.util;

import java.io.BufferedReader;
import java.io.IOException;

public class HttpParser {
    public static HttpRequest parseRequest(BufferedReader in) throws IOException {
        HttpRequest request = new HttpRequest();

        String line = in.readLine();
        if (line == null || line.isEmpty()) {
            return null;
        }

        String[] parts = line.split(" ");
        if (parts.length < 3) {
            System.err.println("Invalid HTTP request line: " + line);
            return null;
        }

        request.method = parts[0];
        request.path = parts[1];
        request.version = parts[2];

        // Read Headers
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            String[] headerParts = line.split(":", 2);
            if (headerParts.length == 2) {
                request.headers.put(headerParts[0].trim(), headerParts[1].trim());
            }
        }

        // Read body if Content-Length is present
        if (request.headers.containsKey("Content-Length")) {
            try {
                int length = Integer.parseInt(request.headers.get("Content-Length"));
                if (length > 0) {
                    char[] buf = new char[length];
                    int bytesRead = in.read(buf, 0, length);
                    if (bytesRead > 0) {
                        request.body = new String(buf, 0, bytesRead);
                    }
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid Content-Length: " + request.headers.get("Content-Length"));
            }
        }

        return request;
    }
}