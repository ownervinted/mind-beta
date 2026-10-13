package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.render.theme.BackgroundMode;
import mind.beta.util.render.pipeline.BlurPipeline;
import net.minecraft.util.Identifier;
import mind.beta.Initialization;
import mind.beta.screens.clickgui.impl.background.render.GuiTheme;
import mind.beta.util.render.Render2D;
import java.awt.Color;


final class HudPanelStyle {
    private static final BlurPipeline MODE_BLUR = new BlurPipeline();
    private static final Identifier BACKGROUND =
            Identifier.of("mind", "textures/background/pink.png");
    private static final float IMAGE_ASPECT = 5120f / 2880f;
    private static final float IMAGE_BLUR = 2.5f;

    private HudPanelStyle() {}

    static int accent(int alpha) {
        return ThemeColors.accentText(clampAlpha(alpha));
    }

    static int text(int alpha) {
        return ThemeColors.color(228, 232, 238, clampAlpha(alpha)).getRGB();
    }

    static void panel(float x, float y, float width, float height, float radius, int alpha) {
        panel(x, y, width, height, radius, alpha, false);
    }

    static void originalPanel(float x, float y, float width, float height, float radius, int alpha) {
        panel(x, y, width, height, radius, alpha, true);
    }

    private static void panel(float x, float y, float width, float height, float radius, int alpha, boolean original) {
        if (width <= 0f || height <= 0f || alpha <= 0) return;
        int opacity = clampAlpha(alpha);
        if (BackgroundMode.isBlur()) {
            int accent = ThemeColors.selected().accent();
            int baseR = ThemeColors.isLight() ? 247 : 16;
            int baseG = ThemeColors.isLight() ? 249 : 21;
            int baseB = ThemeColors.isLight() ? 253 : 32;
            float tint = ThemeColors.isLight() ? .12f : .30f;
            int red = Math.round(baseR * (1f - tint) + ((accent >>> 16) & 255) * tint);
            int green = Math.round(baseG * (1f - tint) + ((accent >>> 8) & 255) * tint);
            int blue = Math.round(baseB * (1f - tint) + (accent & 255) * tint);
            int overlayAlpha = Math.round(opacity * (ThemeColors.isLight() ? .48f : .68f));
            int overlay = (overlayAlpha << 24) | (red << 16) | (green << 8) | blue;
            MODE_BLUR.drawBlur(x, y, width, height, 15f,
                    new float[]{radius, radius, radius, radius}, overlay);
            return;
        }
        if (!original && ThemeColors.isLight()) {
            Render2D.rect(x, y, width, height,
                    (opacity << 24) | 0xF7F9FD, radius);
            return;
        }
        float aspect = width / height;
        float uSpan = Math.min(1f, aspect / IMAGE_ASPECT);
        float vSpan = Math.min(1f, IMAGE_ASPECT / aspect);
        float u0 = (1f - uSpan) * 0.5f;
        float v0 = (1f - vSpan) * 0.5f;
        int color = original ? (opacity << 24) | 0xFFFFFF : ThemeColors.textureTint(opacity);
        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawBlurredTexture(BACKGROUND, x, y, width, height,
                        u0, v0, u0 + uSpan, v0 + vSpan,
                        new int[]{color, color, color, color},
                        new float[]{radius, radius, radius, radius}, IMAGE_BLUR);
        float fade = opacity / 255f;
        Render2D.rect(x, y, width, height,
                original
                        ? new Color(0, 64, 128, Math.round(42f * fade)).getRGB()
                        : ThemeColors.color(0, 64, 128, Math.round(42f * fade)).getRGB(), radius);
        Render2D.rect(x, y, width, height,
                original
                        ? new Color(GuiTheme.PANEL_RED, GuiTheme.PANEL_GREEN, GuiTheme.PANEL_BLUE,
                                Math.round(175f * fade)).getRGB()
                        : ThemeColors.color(GuiTheme.PANEL_RED, GuiTheme.PANEL_GREEN, GuiTheme.PANEL_BLUE,
                                Math.round(175f * fade)).getRGB(), radius);
    }

    static void headerDivider(float x, float y, float width, int alpha) {
        Render2D.rect(x + 8f, y + 19f, Math.max(0f, width - 16f), 0.6f,
                ThemeColors.color(0, 128, 255, Math.round(32f * clampAlpha(alpha) / 255f)).getRGB());
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
