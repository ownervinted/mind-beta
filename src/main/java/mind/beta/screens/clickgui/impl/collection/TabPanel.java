package mind.beta.screens.clickgui.impl.collection;

import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TabPanel {
    public static final float WIDTH = 292f;
    public static final float HEIGHT = 204f;
    private static final long DURATION = 380_000_000L;
    private final Map<String, Motion> motions = new HashMap<>();
    private final Map<String, TextLayers> labels = new HashMap<>();
    private final Map<String, Long> presses = new HashMap<>();
    private boolean active;
    private float from;
    private float progress;
    private long start;
    private long entryStart;
    private long lastFrame;
    private long frameId;
    private long scrollUntil;
    private float delta;

    public void reset(boolean active) {
        this.active = active;
        from = progress = 0f;
        start = entryStart = lastFrame = System.nanoTime();
        motions.clear();
        labels.clear();
        presses.clear();
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        update();
        from = progress;
        this.active = active;
        start = System.nanoTime();
        if (active) {
            entryStart = start;
        }
        presses.clear();
    }

    private void update() {
        float duration = active ? DURATION : 300_000_000L;
        float t = Math.max(0f, Math.min(1f, (System.nanoTime() - start) / duration));
        float ease = t * t * t * (t * (t * 6f - 15f) + 10f);
        progress = from + ((active ? 1f : 0f) - from) * ease;
    }

    public Frame frame(float bgX, float bgY, float alpha) {
        long now = System.nanoTime();
        delta = Math.min(0.1f, Math.max(0f, (now - lastFrame) / 1_000_000_000f));
        lastFrame = now;
        frameId++;
        return snapshot(bgX, bgY, alpha);
    }

    public Frame snapshot(float bgX, float bgY, float alpha) {
        update();
        float scale = 0.965f + 0.035f * progress;
        return new Frame(bgX + 98f + WIDTH * (1f - scale) / 2f,
                bgY + 38f + HEIGHT * (1f - scale) / 2f + (1f - progress) * 9f,
                scale, progress * alpha);
    }

    public boolean isActive() {
        return active && progress > 0.025f;
    }

    public float delta() {
        return delta;
    }

    public float animate(String key, float target, float initial, float speed) {
        Motion motion = motions.computeIfAbsent(key, ignored -> new Motion(initial));
        if (motion.frame != frameId) {
            motion.step(target, delta, speed);
            motion.frame = frameId;
        }
        return motion.value;
    }

    public float value(String key, float fallback) {
        Motion motion = motions.get(key);
        return motion == null ? fallback : motion.value;
    }

    public float hover(String key, boolean hovered) {
        return animate("hover:" + key, active && hovered ? 1f : 0f, 0f, 22f);
    }

    public float entry(String key, int visibleIndex) {
        long delay = Math.min(4, Math.max(0, visibleIndex)) * 28_000_000L;
        boolean ready = active && (entryValue(key) > 0.05f || System.nanoTime() - entryStart > delay);
        return animate("entry:" + key, ready ? 1f : 0f, 0f, 22f);
    }

    public float entryValue(String key) {
        return value("entry:" + key, 0f);
    }

    public void press(String key) {
        presses.put(key, System.nanoTime() + 100_000_000L);
    }

    public float pressAmount(String key) {
        return animate("press:" + key,
                System.nanoTime() < presses.getOrDefault(key, 0L) ? 1f : 0f, 0f, 30f);
    }

    public void release() {
        presses.replaceAll((key, until) -> Math.min(until, System.nanoTime() + 30_000_000L));
    }

    public void scrolled() {
        scrollUntil = System.nanoTime() + 900_000_000L;
    }

    public void label(Frame f, String key, String text, float x, float y, float size,
                      float width, boolean bold, int red, int green, int blue, float opacity) {
        TextLayers layers = labels.computeIfAbsent(key, ignored -> new TextLayers(text));
        layers.draw(f, text, x, y, size, width, bold, key.startsWith("button-text:"),
                red, green, blue, opacity, delta, frameId);
    }

    public void button(Frame f, String key, String title, float x, float y, float width, float height,
                       boolean hovered, boolean enabled, boolean primary) {
        float hover = hover("button:" + key, hovered && enabled);
        float available = animate("available:" + key, enabled ? 1f : 0f, enabled ? 1f : 0f, 18f);
        float pressed = pressAmount(key);
        float inset = pressed * 0.7f;
        Frame b = f.shift(0f, pressed * 0.65f).opacity(0.3f + 0.7f * available);
        if (primary) {
            b.gradient(x + inset, y + inset, width - 2f * inset, height - 2f * inset,
                    b.color(34, 97, 179, 175f + 45f * hover),
                    b.color(14, 48, 94, 235f), 5f);
            b.rect(x + 6f, y + 0.6f, width - 12f, 0.45f, b.color(195, 223, 255, 28f + 22f * hover), 0f);
        } else {
            b.rect(x + inset, y + inset, width - 2f * inset, height - 2f * inset,
                    b.color(110, 165, 240, 3f + 15f * hover), 5f);
        }
        label(b, "button-text:" + key, title, x + 3f,
                y + (height - 6f) / 2f, 6f, width - 6f, false,
                218, 234, 255, primary ? 245f : 185f + 50f * hover);
    }

    public void scrollBar(Frame f, float x, float top, float visible, float content, float scroll, boolean hovered) {
        float present = animate("scrollbar:" + x, content > visible ? 1f : 0f, 0f, 18f);
        float show = animate("scrollbar-hover:" + x,
                hovered || System.nanoTime() < scrollUntil ? 1f : 0.2f, 0.2f, 16f);
        if (present < 0.001f || content <= visible) {
            return;
        }
        float h = animate("thumb-height:" + x, Math.max(18f, visible * visible / content),
                Math.max(18f, visible * visible / content), 20f);
        h = Math.min(visible, h);
        float targetPosition = top + scroll / (content - visible) * (visible - h);
        float position = animate("thumb-position:" + x, targetPosition, targetPosition, 26f);
        position = Math.max(top, Math.min(top + visible - h, position));
        f.rect(x, position, 1f + 0.5f * show, h,
                f.color(138, 181, 235, 145f * present * show), 1f);
    }

    public void toast(Frame f, String text, long until, boolean error) {
        float show = animate("toast", System.currentTimeMillis() < until ? 1f : 0f, 0f, 22f);
        if (show <= 0.001f) {
            return;
        }
        String shown = trim(text, 256f, 5.8f);
        float width = Math.min(280f, Fonts.MEDIUM.getWidth(shown, 5.8f) + 26f);
        float x = (WIDTH - width) / 2f;
        Frame t = f.shift(0f, 7f * (1f - show)).opacity(show);
        t.rect(x - 2f, 178f, width + 4f, 26f, t.color(0, 0, 0, 45), 7f);
        t.gradient(x, 180f, width, 22f, t.color(15, 31, 52, 250), t.color(6, 16, 29, 250), 6f);
        t.rect(x + 8f, 188f, 3f, 3f,
                error ? t.color(235, 140, 140, 245) : t.color(135, 186, 250, 245), 1.5f);
        label(t, "notice", shown, x + 16f, 188f, 5.8f, width - 22f, false, 213, 231, 255, 245);
    }

    public void retainItems(java.util.Set<String> names) {
        String[] prefixes = {"entry:", "hover:row:", "selected:", "toggle:", "loaded:", "row-y:"};
        motions.keySet().removeIf(key -> {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    return !names.contains(key.substring(prefix.length()));
                }
            }
            return false;
        });
    }

    public static boolean inside(double x, double y, float rx, float ry, float w, float h) {
        return x >= rx && x <= rx + w && y >= ry && y <= ry + h;
    }

    public static String trim(String text, float width, float size) {
        if (text == null) {
            return "";
        }
        text = text.replace('\n', ' ').replace('\r', ' ');
        if (Fonts.MEDIUM.getWidth(text, size) <= width) {
            return text;
        }
        int end = text.length();
        while (end > 0 && Fonts.MEDIUM.getWidth(text.substring(0, end) + "...", size) > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + "...";
    }

    private static final class Motion {
        private float value;
        private float velocity;
        private long frame = Long.MIN_VALUE;

        private Motion(float value) {
            this.value = value;
        }

        private void step(float target, float dt, float speed) {
            float difference = value - target;
            float c = velocity + speed * difference;
            float decay = (float) Math.exp(-speed * dt);
            value = target + (difference + c * dt) * decay;
            velocity = (velocity - speed * c * dt) * decay;
            if (Math.abs(value - target) < 0.0001f && Math.abs(velocity) < 0.001f) {
                value = target;
                velocity = 0f;
            }
        }
    }

    private static final class TextLayers {
        private final Map<String, Motion> layers = new LinkedHashMap<>();
        private String current;

        private TextLayers(String text) {
            current = text;
            layers.put(text, new Motion(1f));
        }

        private void draw(Frame f, String text, float x, float y, float size, float width, boolean bold, boolean centered,
                          int red, int green, int blue, float opacity, float dt, long frame) {
            if (!text.equals(current)) {
                current = text;
                layers.computeIfAbsent(text, ignored -> new Motion(0f));
            }
            Iterator<Map.Entry<String, Motion>> iterator = layers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Motion> entry = iterator.next();
                Motion motion = entry.getValue();
                if (motion.frame != frame) {
                    motion.step(entry.getKey().equals(current) ? 1f : 0f, dt, 24f);
                    motion.frame = frame;
                }
                if (motion.value < 0.001f && !entry.getKey().equals(current)) {
                    iterator.remove();
                    continue;
                }
                Frame layer = f.shift(0f, (entry.getKey().equals(current) ? 1f : -1f)
                        * 3f * (1f - motion.value)).opacity(motion.value);
                String shown = trim(entry.getKey(), width, size);
                int color = layer.color(red, green, blue, opacity);
                float textX = centered ? x + (width - Fonts.MEDIUM.getWidth(shown, size)) / 2f : x;
                if (bold) {
                    layer.bold(shown, textX, y, size, color);
                } else {
                    layer.text(shown, textX, y, size, color);
                }
            }
        }
    }

    public record Frame(float x, float y, float scale, float alpha) {
        public double localX(double mouseX) {
            return (mouseX - x) / scale;
        }

        public double localY(double mouseY) {
            return (mouseY - y) / scale;
        }

        public Frame shift(float dx, float dy) {
            return new Frame(x + dx * scale, y + dy * scale, scale, alpha);
        }

        public Frame opacity(float multiplier) {
            return new Frame(x, y, scale, alpha * Math.max(0f, Math.min(1f, multiplier)));
        }

        public int color(int red, int green, int blue, float opacity) {
            return ThemeColors.color(red, green, blue,
                    Math.max(0, Math.min(255, Math.round(opacity * alpha)))).getRGB();
        }

        public void rect(float rx, float ry, float w, float h, int color, float radius) {
            Render2D.rect(x + rx * scale, y + ry * scale, w * scale, h * scale, color, radius * scale);
        }

        public void gradient(float rx, float ry, float w, float h, int top, int bottom, float radius) {
            Render2D.gradientRect(x + rx * scale, y + ry * scale, w * scale, h * scale,
                    new int[]{top, top, bottom, bottom}, radius * scale);
        }

        public void text(String text, float rx, float ry, float size, int color) {
            Fonts.MEDIUM.draw(text, x + rx * scale, y + ry * scale, size * scale, color);
        }

        public void regular(String text, float rx, float ry, float size, int color) {
            Fonts.REGULAR.draw(text, x + rx * scale, y + ry * scale, size * scale, color);
        }

        public void bold(String text, float rx, float ry, float size, int color) {
            Fonts.BOLD.draw(text, x + rx * scale, y + ry * scale, size * scale, color);
        }

        public void eyebrow(String text, float rx, float ry, int color) {
            float cursor = rx;
            for (int i = 0; i < text.length(); i++) {
                String letter = text.substring(i, i + 1);
                regular(letter, cursor, ry, 4.5f, color);
                cursor += Fonts.REGULAR.getWidth(letter, 4.5f) + 0.75f;
            }
        }
    }
}
