package mind.beta.util.render.font;

import java.util.LinkedHashMap;
import java.util.Map;

public class Fonts {

    private static final Map<String, String> FONT_REGISTRY = new LinkedHashMap<>();

    public static final Font SEMIBOLD = register("semibold", "semibold");
    public static final Font REGULAR = register("regular", "regular");
    public static final Font MIND_FONT = register("icons-mind", "icons-mind");
    public static final Font HUD_ICON = register("hudicon", "hudicon");
    public static final Font BOLD = register("bold", "bold");
    public static final Font MEDIUM = register("medium", "medium");

    private static Font register(String name, String path) {
        FONT_REGISTRY.put(name, path);
        return new Font(name);
    }

    public static Map<String, String> getRegistry() {
        return FONT_REGISTRY;
    }

    private Fonts() {}
}