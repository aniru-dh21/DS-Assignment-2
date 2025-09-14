import org.junit.*;
import Weather.server.AggregationServer;
import Weather.content.ContentServer;
import Weather.client.GETClient;

import java.io.*;
import java.util.concurrent.*;

public class ConcurrencyTest {
    private static final int PORT = 6789;
    private static Thread serverThread;

    @BeforeClass
    public static void startServer() {
        serverThread = new Thread(() -> {
            try {
                new AggregationServer(PORT).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    @Test
    public void testConcurrentPuts() throws Exception {
        // Create three test weather files
        File f1 = new File("station1.txt");
        try (PrintWriter pw = new PrintWriter(f1)) {
            pw.println("id:S1");
            pw.println("name:Station One");
            pw.println("air_temp:20.0");
        }

        File f2 = new File("station2.txt");
        try (PrintWriter pw = new PrintWriter(f2)) {
            pw.println("id:S2");
            pw.println("name:Station Two");
            pw.println("air_temp:21.0");
        }

        File f3 = new File("station3.txt");
        try (PrintWriter pw = new PrintWriter(f3)) {
            pw.println("id:S3");
            pw.println("name:Station Three");
            pw.println("air_temp:22.0");
        }

        // Run three ContentServers concurrently
        ExecutorService exec = Executors.newFixedThreadPool(3);
        exec.submit(() -> runContent("station1.txt"));
        exec.submit(() -> runContent("station2.txt"));
        exec.submit(() -> runContent("station3.txt"));
        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        Thread.sleep(2000); // give time for processing

        // Capture GETClient output
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(baos));
        GETClient.main(new String[]{"localhost:" + PORT});
        System.setOut(oldOut);

        String output = baos.toString();

        // All stations should appear
        Assert.assertTrue(output.contains("S1"));
        Assert.assertTrue(output.contains("S2"));
        Assert.assertTrue(output.contains("S3"));

        // Lamport clock should appear in headers (GETClient logs it)
        Assert.assertTrue(output.contains("Lamport-Clock"));
    }

    private void runContent(String file) {
        try {
            ContentServer.main(new String[]{"localhost:" + PORT, file});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void cleanup() {
        File f1 = new File("station1.txt");
        File f2 = new File("station2.txt");
        File f3 = new File("station3.txt");
        File f4 = new File("weather.json");
        f1.delete();
        f2.delete();
        f3.delete();
        f4.delete();
    }
}
