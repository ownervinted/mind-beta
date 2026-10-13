/* Copyright Mind */
package mind.beta.modules.impl.combat.aura.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import mind.beta.modules.impl.combat.aura.RotationConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

public final class RotationBuilder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final RotationBuilder INSTANCE = new RotationBuilder();

    public Profile profile = new Profile();
    public String savedConfig = "";
    public int schemaVersion = 12;
    private Map<String, Profile> presets = new LinkedHashMap<>();

    public static final class Point {
        public float x;
        public float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Profile {
        public String name = "Custom";
        public String aim = "Sticky";

        public float acquire = 32f;
        public float track = 17f;
        public float returning = 24f;
        public float yaw = 45f;
        public float pitch = 28f;
        public float acceleration = .34f;
        public float deceleration = .28f;
        public float smoothing = .75f;
        public float jitter = 0f;
        public float outside = 3f;
        public int leadTicks = 2;

        public float stickyX = .5f;
        public float stickyY = .65f;

        public List<Point> multiPoints = new ArrayList<>(List.of(new Point(.5f, .65f)));
        public float pointSwitchTicks = 6f;

        public float snapOutsideSpeed = 65f;
        public float snapHoldTicks = 1f;
        public float snapReturnSpeed = 85f;
        public float snapTriggerTicks = 0f;

        public float x1 = .25f;
        public float y1 = .1f;
        public float x2 = .75f;
        public float y2 = .9f;

        public RotationConfig config() {
            return new RotationConfig(acquire, track, returning, yaw, pitch, acceleration, deceleration, smoothing);
        }

        public float curve(float t) {
            t = clamp(t, 0f, 1f);
            float lo = 0f;
            float hi = 1f;
            for (int i = 0; i < 18; i++) {
                float mid = (lo + hi) * .5f;
                if (bezier(mid, x1, x2) < t) lo = mid;
                else hi = mid;
            }
            return bezier((lo + hi) * .5f, y1, y2);
        }

        private static float bezier(float t, float a, float b) {
            float s = 1f - t;
            return 3f * s * s * t * a + 3f * s * t * t * b + t * t * t;
        }
    }

    public synchronized void loadEmbeddedProfile(Profile embedded) {
        loadEmbeddedLibrary(embedded, null, "");
    }

    public synchronized void loadEmbeddedLibrary(Profile active, Map<String, Profile> entries, String selected) {
        profile = sanitize(active == null ? new Profile() : active);
        presets = new LinkedHashMap<>();
        if (entries != null) {
            for (Map.Entry<String, Profile> entry : entries.entrySet()) {
                String key = validName(entry.getKey());
                if (key != null && entry.getValue() != null && !presets.containsKey(key)) {
                    presets.put(key, sanitize(entry.getValue()));
                }
            }
        }
        savedConfig = selected != null && presets.containsKey(selected) ? selected : "";
    }

    public synchronized Map<String, Profile> exportPresets() {
        return new LinkedHashMap<>(presets);
    }

    public synchronized Profile active() {
        return profile;
    }

    public synchronized List<String> configs() {
        return presets.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public synchronized String savePreset(String name) {
        String safe = validName(name);
        if (safe == null || configs().stream().anyMatch(n -> n.equalsIgnoreCase(safe))) return "";
        return writePreset(safe);
    }

    public synchronized String updatePreset(String name) {
        String safe = validName(name);
        if (safe == null || !presets.containsKey(safe)) return "";
        return writePreset(safe);
    }

    private String writePreset(String safe) {
        profile = sanitize(profile);
        profile.name = safe;
        presets.put(safe, GSON.fromJson(GSON.toJson(profile), Profile.class));
        savedConfig = safe;
        return safe;
    }

    public synchronized boolean loadPreset(String name) {
        Profile stored = presets.get(name);
        if (stored == null) return false;
        profile = sanitize(GSON.fromJson(GSON.toJson(stored), Profile.class));
        savedConfig = name;
        return true;
    }

    public synchronized boolean deletePreset(String name) {
        if (presets.remove(name) == null) return false;
        if (name.equals(savedConfig)) savedConfig = "";
        return true;
    }

    private static String validName(String name) {
        if (name == null) return null;
        String text = name.trim();
        if (text.isEmpty() || text.length() > 48 || text.endsWith(".") || text.endsWith(" ") || text.equals(".") || text.equals("..")) {
            return null;
        }
        for (char ch : text.toCharArray()) {
            if (ch < 32 || "\\/:*?\"<>|".indexOf(ch) >= 0) return null;
        }
        String stem = text.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (List.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9").contains(stem)) {
            return null;
        }
        return text;
    }

    private static Profile sanitize(Profile profile) {
        if (profile == null) return new Profile();
        if (profile.name == null) profile.name = "Custom";
        if (!List.of("Sticky", "Out of hitbox", "Snap").contains(profile.aim)) profile.aim = "Sticky";

        profile.acquire = clamp(profile.acquire, 1f, 100f);
        profile.track = clamp(profile.track, 1f, 100f);
        profile.returning = clamp(profile.returning, 1f, 100f);
        profile.yaw = clamp(profile.yaw, 1f, 180f);
        profile.pitch = clamp(profile.pitch, 1f, 90f);
        profile.acceleration = clamp(profile.acceleration, .01f, 1f);
        profile.deceleration = clamp(profile.deceleration, .01f, 1f);
        profile.smoothing = clamp(profile.smoothing, .05f, 1f);
        profile.jitter = clamp(profile.jitter, 0f, 4f);
        profile.outside = clamp(profile.outside, .5f, 18f);
        profile.leadTicks = Math.max(1, Math.min(5, profile.leadTicks));

        profile.stickyX = clamp(profile.stickyX, .05f, .95f);
        profile.stickyY = clamp(profile.stickyY, .05f, .95f);

        if (profile.multiPoints == null) profile.multiPoints = new ArrayList<>();
        List<Point> cleaned = new ArrayList<>();
        for (Point point : profile.multiPoints) {
            if (point != null && cleaned.size() < 12) {
                cleaned.add(new Point(clamp(point.x, .05f, .95f), clamp(point.y, .05f, .95f)));
            }
        }
        if (cleaned.isEmpty()) cleaned.add(new Point(.5f, .65f));
        profile.multiPoints = cleaned;
        profile.pointSwitchTicks = clamp(profile.pointSwitchTicks, 1f, 40f);

        profile.snapOutsideSpeed = clamp(profile.snapOutsideSpeed, 1f, 180f);
        profile.snapHoldTicks = clamp(profile.snapHoldTicks, 0f, 5f);
        profile.snapReturnSpeed = clamp(profile.snapReturnSpeed, 1f, 180f);
        profile.snapTriggerTicks = clamp(profile.snapTriggerTicks, 0f, 3f);

        profile.x1 = clamp(profile.x1, 0f, 1f);
        profile.y1 = clamp(profile.y1, 0f, 1f);
        profile.x2 = clamp(profile.x2, 0f, 1f);
        profile.y2 = clamp(profile.y2, 0f, 1f);
        return profile;
    }

    public synchronized void save() {
    }

    public static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
