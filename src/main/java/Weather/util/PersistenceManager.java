package Weather.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class PersistenceManager {
    private final File storageFile;
    private final Gson gson;

    public PersistenceManager(String filename) {
        this.storageFile = new File(filename);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // Save aggregated data atomically
    public synchronized void save(JsonArray data) throws IOException {
        File tempFile = new File(storageFile.getAbsolutePath() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(gson.toJson(data));
            Files.move(tempFile.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Clean up temp file if something goes wrong
            tempFile.delete();
            throw e;
        }
    }

    // Load existing JSON data if available
    public synchronized JsonArray load() throws IOException {
        if (!storageFile.exists()) {
            return new JsonArray();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return JsonParser.parseString(sb.toString()).getAsJsonArray();
        }
    }
}
