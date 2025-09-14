import Weather.content.ContentServer;
import Weather.client.GETClient;

import java.io.*;
import java.util.concurrent.*;

/**
 * StressTester: spawns multiple ContentServers and GETClients
 * to test AggregationServer under heavy load.
 */
public class StressTester {
    private static final String HOST = "localhost";
    private static final int PORT = 4567; // make sure your server runs on this port

    public static void main(String[] args) throws Exception {
        int numStations = 10;   // number of concurrent ContentServers
        int numClients = 5;     // number of concurrent GETClients
        int durationSec = 60;   // run for 1 minute

        System.out.println("Starting stress test with " + numStations + " ContentServers and "
                + numClients + " GETClients for " + durationSec + " seconds...");

        ExecutorService pool = Executors.newCachedThreadPool();

        // Launch ContentServers (re-PUT every 20s)
        for (int i = 1; i <= numStations; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    File f = new File("station" + id + ".txt");
                    try (PrintWriter pw = new PrintWriter(f)) {
                        pw.println("id:ST" + id);
                        pw.println("name:Station " + id);
                        pw.println("air_temp:" + (15 + id));
                    }
                    ContentServer.main(new String[]{HOST + ":" + PORT, f.getName()});
                } catch (Exception e) {
                    System.err.println("[ContentServer-" + id + "] " + e.getMessage());
                }
            });
        }

        // Launch GETClients periodically
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numClients);
        for (int j = 1; j <= numClients; j++) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream oldOut = System.out;
                    System.setOut(new PrintStream(baos));
                    GETClient.main(new String[]{HOST + ":" + PORT});
                    System.setOut(oldOut);

                    String output = baos.toString();
                    System.out.println("[GETClient] Response:\n" + output.split("\n")[0]); // log first line
                } catch (Exception e) {
                    System.err.println("[GETClient] " + e.getMessage());
                }
            }, 2, 5, TimeUnit.SECONDS); // fetch every 5s
        }

        // Let it run for durationSec
        Thread.sleep(durationSec * 1000);

        System.out.println("Stress test finished. Shutting down...");
        pool.shutdownNow();
        scheduler.shutdownNow();
    }
}
