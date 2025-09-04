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
        request.method = parts[0];
        request.path = parts[1];
        request.path = parts[2];

        while (!(line = in.readLine()).isEmpty()) {
            String[] headerParts = line.split(":", 2);
            if (headerParts.length == 2) {
                request.headers.put(headerParts[0].trim(), headerParts[1].trim());
            }
        }

        if (request.headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(request.headers.get("Content-Length"));
            char[] buf = new char[length];
            in.read(buf, 0, length);
            request.body = new String(buf);
        }

        return request;
    }
}
