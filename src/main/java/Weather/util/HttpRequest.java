package Weather.util;

import java.util.*;

public class HttpRequest {
    public String method;
    public String path;
    public String version;
    public Map<String, String> headers = new HashMap<>();
    public String body;

    @Override
    public String toString() {
        return method + " " + path + " " + "\n" + headers + "\n" + body;
    }
}
