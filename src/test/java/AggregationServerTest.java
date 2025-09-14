import org.junit.*;
import Weather.server.AggregationServer;
import Weather.client.GETClient;
import Weather.util.HttpRequest;
import Weather.util.HttpResponse;
import Weather.util.HttpParser;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class AggregationServerTest {
    private static final int PORT = 5678;
    private static Thread serverThread;
    private static AggregationServer server;

    @BeforeClass
    public static void startServer() {
        serverThread = new Thread(() -> {
            try {
                server = new AggregationServer(PORT);
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Give server time to start
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    @Test
    public void testPutAndGet() throws Exception {
        // Create test data
        String testData = "{\n" +
                "  \"id\": \"TEST123\",\n" +
                "  \"name\": \"JUnit Test Station\",\n" +
                "  \"air_temp\": \"25.5\"\n" +
                "}";

        // Send PUT request directly
        sendPutRequest(testData);

        // Wait for processing
        Thread.sleep(1000);

        // Verify with GET
        String response = sendGetRequest();
        Assert.assertTrue("GET should contain station ID", response.contains("TEST123"));
        Assert.assertTrue("GET should contain station name", response.contains("JUnit Test Station"));
        Assert.assertTrue("GET should contain temperature", response.contains("25.5"));
    }

    @Test
    public void testMultipleStations() throws Exception {
        // Send multiple stations
        String station1 = "{\n" +
                "  \"id\": \"MULTI1\",\n" +
                "  \"name\": \"Station One\",\n" +
                "  \"air_temp\": \"20.0\"\n" +
                "}";

        String station2 = "{\n" +
                "  \"id\": \"MULTI2\",\n" +
                "  \"name\": \"Station Two\",\n" +
                "  \"air_temp\": \"22.0\"\n" +
                "}";

        sendPutRequest(station1);
        sendPutRequest(station2);

        Thread.sleep(1000);

        String response = sendGetRequest();
        Assert.assertTrue("Should contain both stations",
                response.contains("MULTI1") && response.contains("MULTI2"));
    }

    @Test
    public void testUpdateExistingStation() throws Exception {
        // Send initial data
        String initialData = "{\n" +
                "  \"id\": \"UPDATE1\",\n" +
                "  \"name\": \"Update Station\",\n" +
                "  \"air_temp\": \"15.0\"\n" +
                "}";

        String response1 = sendPutRequest(initialData);
        Assert.assertTrue("First PUT should return 201 Created", response1.contains("201"));

        // Update the same station
        String updatedData = "{\n" +
                "  \"id\": \"UPDATE1\",\n" +
                "  \"name\": \"Update Station\",\n" +
                "  \"air_temp\": \"18.0\"\n" +
                "}";

        String response2 = sendPutRequest(updatedData);
        Assert.assertTrue("Second PUT should return 200 OK", response2.contains("200"));

        // Verify updated data
        String getResponse = sendGetRequest();
        Assert.assertTrue("Should contain updated temperature", getResponse.contains("18.0"));
        Assert.assertFalse("Should not contain old temperature", getResponse.contains("15.0"));
    }

    @Test
    public void testLamportClock() throws Exception {
        String testData = "{\n" +
                "  \"id\": \"LAMPORT1\",\n" +
                "  \"name\": \"Lamport Test\",\n" +
                "  \"air_temp\": \"16.0\"\n" +
                "}";

        String response = sendPutRequest(testData);
        Assert.assertTrue("Response should contain Lamport-Clock header",
                response.contains("Lamport-Clock:"));
    }

    @Test
    public void testEmptyPutRequest() throws Exception {
        String response = sendPutRequest("");
        Assert.assertTrue("Empty PUT should return 204 No Content", response.contains("204"));
    }

    // Helper method to send PUT requests directly via socket
    private String sendPutRequest(String jsonData) throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String request = "PUT /weather.json HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "User-Agent: JUnitTest/1.0\r\n" +
                    "Lamport-Clock: 1\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + jsonData.length() + "\r\n" +
                    "\r\n" +
                    jsonData;

            out.write(request);
            out.flush();

            // Read response
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
                if (line.isEmpty()) break;
            }

            return response.toString();
        }
    }

    // Helper method to send GET requests
    private String sendGetRequest() throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String request = "GET /weather.json HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "User-Agent: JUnitTest/1.0\r\n" +
                    "Lamport-Clock: 1\r\n" +
                    "\r\n";

            out.write(request);
            out.flush();

            // Read headers
            String line;
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            // Read body
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                in.read(buffer, 0, contentLength);
                return new String(buffer);
            }

            return "";
        }
    }

    @AfterClass
    public static void cleanup() {
        // Clean up test files
        File persistenceFile = new File("weather.json");
        if (persistenceFile.exists()) {
            persistenceFile.delete();
        }
    }
}