package mind.beta.screens.clickgui.impl.configs.handler;

import lombok.Getter;
import lombok.Setter;
import mind.beta.util.config.ConfigSystem;
import mind.beta.util.config.impl.ConfigPath;
import mind.beta.util.config.impl.ConfigSerializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConfigDataHandler {

    private final List<String> configs = new ArrayList<>();
    private final ConfigAnimationHandler animationHandler;

    private String selectedConfig = null;
    private boolean isCreating = false;
    private String newConfigName = "";

    private double scrollOffset = 0;
    private double targetScrollOffset = 0;
    private float scrollTopFade = 0f;
    private float scrollBottomFade = 0f;

    public ConfigDataHandler(ConfigAnimationHandler animationHandler) {
        this.animationHandler = animationHandler;
    }

    private final ConfigDetailsStore details = new ConfigDetailsStore();
    private String activeConfig;
    private String lastError = "";

    public void refreshConfigs() {
        List<String> found = new ArrayList<>();
        try {
            Path directory = ConfigPath.getConfigDirectory();
            Files.createDirectories(directory);
            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .map(name -> name.substring(0, name.length() - 5))
                        .filter(this::validName)
                        .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                        .forEach(found::add);
            }
            configs.clear();
            configs.addAll(found);
            details.refresh(configs);
            animationHandler.initItemAnimations(configs);
            if (selectedConfig != null && !configs.contains(selectedConfig)) {
                selectedConfig = null;
            }
            if (activeConfig != null && !configs.contains(activeConfig)) {
                activeConfig = null;
            }
            lastError = "";
        } catch (Exception e) {
            lastError = "Could not read configurations";
        }
    }

    public ConfigDetailsStore.Details getDetails(String name) {
        return details.get(name);
    }

    public boolean updateDetails(String name, String description, String tag, int cover, boolean favorite) {
        return details.update(name, description, tag, cover, favorite);
    }

    public boolean updateDetails(String name, String description, String tag, int cover, boolean favorite, String author) {
        return details.update(name, description, tag, cover, favorite, author);
    }

    public boolean setAuthor(String name, String author) {
        return details.setAuthor(name, author);
    }

    public boolean toggleFavorite(String name) {
        ConfigDetailsStore.Details info = details.get(name);
        return details.update(name, info.description(), info.tag(), info.cover(), !info.favorite());
    }

    public void updateScroll(float deltaTime) {
        scrollOffset += (targetScrollOffset - scrollOffset) * 12f * deltaTime;
    }

    public void updateScrollFades(float visibleHeight) {
        float contentHeight = configs.size() * 27f;

        if (contentHeight <= visibleHeight) {
            scrollTopFade = 0f;
            scrollBottomFade = 0f;
            return;
        }

        float maxScroll = contentHeight - visibleHeight;
        scrollTopFade = (float) Math.min(1f, -scrollOffset / 20f);
        scrollBottomFade = (float) Math.min(1f, (maxScroll + scrollOffset) / 20f);
    }

    public void handleScroll(double vertical, float visibleHeight) {
        float contentHeight = configs.size() * 27f;
        float maxScroll = Math.max(0, contentHeight - visibleHeight);
        targetScrollOffset += vertical * 25;
        targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset));
    }

    private boolean validName(String name) {
        if (name == null || name.isBlank() || name.length() > 32
                || name.equalsIgnoreCase("autoconfig")) {
            return false;
        }
        if (!name.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
            return false;
        }
        return !name.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    }

    private Path configPath(String name) {
        if (!validName(name)) {
            throw new IllegalArgumentException("Invalid configuration name");
        }
        return ConfigPath.getConfigDirectory().resolve(name + ".json");
    }

    public boolean saveConfig(String name) {
        return writeConfig(name, false);
    }

    public boolean refreshConfig(String name) {
        return writeConfig(name, true);
    }

    private boolean writeConfig(String name, boolean replace) {
        Path temporary = null;
        try {
            Path target = configPath(name);
            Files.createDirectories(target.getParent());
            if (!replace && Files.exists(target)) {
                lastError = "A configuration with this name already exists";
                return false;
            }
            if (replace && !Files.isRegularFile(target)) {
                lastError = "Select an existing configuration";
                return false;
            }
            String json = new ConfigSerializer().serialize();
            temporary = Files.createTempFile(target.getParent(), "mind-", ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            if (replace) {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.move(temporary, target);
            }
            selectedConfig = activeConfig = name;
            refreshConfigs();
            return true;
        } catch (IllegalArgumentException e) {
            lastError = "Use letters, numbers, _ or - (up to 32 characters)";
            return false;
        } catch (Exception e) {
            lastError = "Could not save configuration";
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public boolean loadConfig(String name) {
        try {
            String json = Files.readString(configPath(name), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("modules")
                    || !parsed.getAsJsonObject().get("modules").isJsonObject()) {
                lastError = "This file is not a Mind configuration";
                return false;
            }
            new ConfigSerializer().deserialize(json);
            selectedConfig = activeConfig = name;
            ConfigSystem system = ConfigSystem.getInstance();
            if (system != null) {
                system.save();
            }
            lastError = "";
            return true;
        } catch (Exception e) {
            lastError = "Could not load configuration";
            return false;
        }
    }

    public boolean deleteConfig(String name) {
        try {
            boolean deleted = Files.deleteIfExists(configPath(name));
            refreshConfigs();
            return deleted;
        } catch (Exception e) {
            lastError = "Could not delete configuration";
            return false;
        }
    }

    public void toggleCreating() {
        isCreating = !isCreating;
        if (!isCreating) {
            newConfigName = "";
        }
    }

    public void appendChar(char chr) {
        if (newConfigName.length() < 32 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-')) {
            newConfigName += chr;
        }
    }

    public void removeLastChar() {
        if (!newConfigName.isEmpty()) {
            newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
        }
    }

    public void clearNewConfigName() {
        newConfigName = "";
    }
}
