package Weather.util;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PersistenceManager {
    private final File storageFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PersistenceManager(String filename) {
        this.storageFile = new File(filename);
    }

    // Save aggregated data atomically
    public synchronized void save(Map<String, JsonObject> data) throws IOException {
        File tempFile = new File(storageFile.getAbsoluteFile() + ".tmp");
        try (Writer writer = new FileWriter(tempFile)) {
            gson.toJson(data.values(), writer);
        }
        Files.move(tempFile.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // Load existing JSON data if available
    public synchronized Map<String, JsonObject> load() throws IOException {
        Map<String, JsonObject> map = new HashMap<>();
        if (!storageFile.exists()) {
            return map;
        }

        try (Reader reader = new FileReader(storageFile)) {
            JsonArray arr = gson.fromJson(reader, JsonArray.class);
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id")) {
                    map.put(obj.get("id").getAsString(), obj);
                }
            }
        }
        return map;
    }
}