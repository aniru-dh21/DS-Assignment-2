package Weather.client;

import Weather.util.LamportClock;

public class GETClient {
    private final LamportClock clock = new LamportClock();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GETClient <server:port>");
            return;
        }

        String serverAddr = args[0];
        GETClient client = new GETClient();
    }
}
