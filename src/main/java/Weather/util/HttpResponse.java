package Weather.util;

import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    public String version = "HTTP/1.1";
    public int statusCode;
    public String statusMessage;
    public Map<String, String> headers = new HashMap<>();
    public String body;

    public String buildResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append(version).append(" ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        if (body != null) {
            sb.append(body);
        }
        return sb.toString();
    }
}
