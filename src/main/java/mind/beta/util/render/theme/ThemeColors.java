package mind.beta.util.render.theme;

import java.awt.Color;

public final class ThemeColors {
    private static volatile ClientTheme selected = ClientTheme.MIDNIGHT;

    private ThemeColors() {
    }

    public static ClientTheme selected() {
        return selected;
    }

    public static void select(ClientTheme theme) {
        selected = theme == null ? ClientTheme.MIDNIGHT : theme;
    }

    public static Color color(int red, int green, int blue) {
        return color(red, green, blue, 255);
    }

    public static Color color(int red, int green, int blue, int alpha) {
        Color original = new Color(red, green, blue, alpha);
        ClientTheme theme = selected;
        if (theme == ClientTheme.MIDNIGHT) {
            return original;
        }
        float[] source = Color.RGBtoHSB(red, green, blue, null);
        int brightness = Math.max(red, Math.max(green, blue));
        if (theme == ClientTheme.IVORY) {
            if (brightness <= 75) {
                int shade = Math.round(238f - brightness * 0.22f);
                return new Color(shade - 4, shade - 2, Math.min(255, shade + 3), alpha);
            }
            if (source[1] < 0.28f) {
                int shade = Math.max(28, Math.min(145, Math.round(146f - (brightness - 84f) * 0.68f)));
                return new Color(shade, Math.min(255, shade + 5), Math.min(255, shade + 14), alpha);
            }
        }
        if (source[1] < 0.01f || source[0] < 0.50f || source[0] > 0.76f) {
            return original;
        }
        int accent = theme.accent();
        float[] target = Color.RGBtoHSB((accent >>> 16) & 255, (accent >>> 8) & 255, accent & 255, null);
        int rgb = Color.HSBtoRGB(target[0], source[1] * target[1], source[2] * target[2]);
        return new Color((alpha << 24) | (rgb & 0xFFFFFF), true);
    }

    public static boolean isLight() {
        return selected == ClientTheme.IVORY;
    }

    public static int dim(int alpha) {
        return (clamp(alpha) << 24) | 0x000000;
    }

    public static int accentText(int alpha) {
        if (isLight()) {
            return (clamp(alpha) << 24) | (selected.accent() & 0xFFFFFF);
        }
        return color(205, 227, 255, clamp(alpha)).getRGB();
    }

    public static int switchTrack(float progress, float hover, int alpha) {
        float t = Math.max(0f, Math.min(1f, progress));
        int off = isLight() ? 0xFFB7C2D2 : 0xFF555A68;
        int on = selected.accent();
        int rgb = mix(off, on, t);
        float visibility = isLight() ? (0.88f + 0.12f * t) : (0.64f + 0.36f * t);
        int a = clamp(Math.round(clamp(alpha) * visibility));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int switchKnob(float progress, int alpha) {
        float t = Math.max(0f, Math.min(1f, progress));
        int off = isLight() ? 0xFFF9FBFF : 0xFFE0E5EE;
        int on = 0xFFFFFFFF;
        return (clamp(alpha) << 24) | (mix(off, on, t) & 0xFFFFFF);
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 255) * (1f - t) + ((to >> 16) & 255) * t);
        int g = Math.round(((from >> 8) & 255) * (1f - t) + ((to >> 8) & 255) * t);
        int b = Math.round((from & 255) * (1f - t) + (to & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static int textureTint(int alpha) {
        int rgb = 0xFFFFFF;
        if (selected != ClientTheme.MIDNIGHT && selected != ClientTheme.IVORY) {
            int accent = selected.accent();
            int red = Math.round(255f * 0.65f + ((accent >>> 16) & 255) * 0.35f);
            int green = Math.round(255f * 0.65f + ((accent >>> 8) & 255) * 0.35f);
            int blue = Math.round(255f * 0.65f + (accent & 255) * 0.35f);
            rgb = (red << 16) | (green << 8) | blue;
        }
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }
}
