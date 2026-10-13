package mind.beta.screens.clickgui.impl.configs.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mind.beta.util.config.impl.ConfigPath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
import java.util.Map;

public final class ConfigDetailsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Details> entries = new HashMap<>();

    public record Details(String description, String tag, int cover, boolean favorite, long modified, String author) {
    }

    public void refresh(Iterable<String> names) {
        JsonObject saved = new JsonObject();
        Path directory = ConfigPath.getConfigDirectory();
        try {
            Path file = directory.resolve(".config-ui.json");
            if (Files.isRegularFile(file)) {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    saved = parsed.getAsJsonObject();
                }
            }
        } catch (Exception ignored) {
        }
        entries.clear();
        for (String name : names) {
            String description = "Local configuration";
            String tag = "Custom";
            String author = "Unknown";
            int cover = Math.floorMod(name.hashCode(), 4);
            boolean favorite = false;
            long modified = 0L;
            try {
                modified = Files.getLastModifiedTime(directory.resolve(name + ".json")).toMillis();
                JsonElement element = saved.get(name);
                if (element != null && element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    author = string(object, "author", author, 40);
                    description = string(object, "description", description, 100);
                    tag = string(object, "tag", tag, 96);
                    cover = object.has("cover") ? Math.floorMod(object.get("cover").getAsInt(), 4) : cover;
                    favorite = object.has("favorite") && object.get("favorite").getAsBoolean();
                }
            } catch (Exception ignored) {
            }
            entries.put(name, new Details(description, tag, cover, favorite, modified, author));
        }
    }

    private String string(JsonObject object, String key, String fallback, int limit) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String text = value.getAsString().replace('\n', ' ').replace('\r', ' ');
        return text.substring(0, Math.min(limit, text.length()));
    }

    public Details get(String name) {
        return entries.getOrDefault(name, new Details("Local configuration", "Custom",
                Math.floorMod(name.hashCode(), 4), false, 0L, "Unknown"));
    }

    public boolean setAuthor(String name, String author) {
        Details previous = entries.get(name);
        return previous != null && update(name, previous.description(), previous.tag(), previous.cover(),
                previous.favorite(), author);
    }

    public boolean update(String name, String description, String tag, int cover, boolean favorite) {
        Details previous = entries.get(name);
        return previous != null && update(name, description, tag, cover, favorite, previous.author());
    }

    public boolean update(String name, String description, String tag, int cover, boolean favorite, String author) {
        Details previous = entries.get(name);
        if (previous == null) {
            return false;
        }
        String resolvedAuthor = author == null || author.isBlank() ? "Unknown" : author.strip();
        if (resolvedAuthor.length() > 40) {
            resolvedAuthor = resolvedAuthor.substring(0, 40);
        }
        entries.put(name, new Details(description, tag, Math.floorMod(cover, 4), favorite,
                previous.modified(), resolvedAuthor));
        Path temporary = null;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Details> entry : entries.entrySet()) {
                Details details = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("author", details.author());
                json.addProperty("description", details.description());
                json.addProperty("tag", details.tag());
                json.addProperty("cover", details.cover());
                json.addProperty("favorite", details.favorite());
                root.add(entry.getKey(), json);
            }
            Path target = ConfigPath.getConfigDirectory().resolve(".config-ui.json");
            temporary = Files.createTempFile(target.getParent(), "mind-ui-", ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            entries.put(name, previous);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
