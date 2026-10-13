package mind.beta.util.render.theme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BackgroundMode {
    private static final Path FILE = Path.of("config", "mind", "blur-background.txt");
    private static boolean enabled = load();

    private BackgroundMode() {}

    public static boolean isBlur() {
        return false;
    }

    public static void toggle() {
        enabled = !enabled;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Boolean.toString(enabled));
        } catch (Exception ignored) {}
    }

    private static boolean load() {
        try { return Files.exists(FILE) && Boolean.parseBoolean(Files.readString(FILE).trim()); }
        catch (Exception ignored) { return false; }
    }
}
