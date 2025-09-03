package Weather.content;

import Weather.util.LamportClock;

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
}
