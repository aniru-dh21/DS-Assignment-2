package Weather.server;

import Weather.util.LamportClock;

import java.util.concurrent.ConcurrentHashMap;

public class AggregationServer {
    private static final int DEFAULT_PORT = 8080;
    private final int port;
    private final LamportClock clock = new LamportClock();
    private final Map<String, JSONObject> Data = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();

    public AggregationServer(int port) {
        this.port = port;
    }
}
