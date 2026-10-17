package shtaros.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small local guest library. No account, cookies, or credentials are stored. */
public final class ShtarOSLibrary {
    private static final List<SongMetadata> recent = new ArrayList<>();
    private static boolean loaded;
    private ShtarOSLibrary() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/shtaros-recent.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) return;
            for (JsonElement element : JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                String id = item.get("id").getAsString();
                if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                recent.add(new SongMetadata(id, item.get("title").getAsString(),
                        item.get("author").getAsString(), item.get("durationLabel").getAsString(),
                        item.get("duration").getAsInt()));
                if (recent.size() >= 30) break;
            }
        } catch (Exception e) {
            System.err.println("[ShtarOS] Could not load local guest library: " + e.getMessage());
        }
    }

    public static List<SongMetadata> suggestions() {
        load();
        return List.copyOf(recent);
    }

    public static void remember(SongMetadata song) {
        load();
        if (song == null || !song.id().matches("[A-Za-z0-9_-]{11}")) return;
        recent.removeIf(item -> item.id().equals(song.id()));
        recent.add(0, song);
        while (recent.size() > 30) recent.remove(recent.size() - 1);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            JsonArray array = new JsonArray();
            for (SongMetadata item : recent) {
                JsonObject row = new JsonObject();
                row.addProperty("id", item.id());
                row.addProperty("title", item.title());
                row.addProperty("author", item.author());
                row.addProperty("durationLabel", item.durationStr());
                row.addProperty("duration", item.durationSeconds());
                array.add(row);
            }
            Files.writeString(path, array.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[ShtarOS] Could not save local guest library: " + e.getMessage());
        }
    }
}
