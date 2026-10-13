package mind.beta.screens.clickgui.impl.theme;

import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.BackgroundMode;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;

public final class ThemeBlurPopup {
    public static final ThemeBlurPopup INSTANCE = new ThemeBlurPopup();
    private static final float WIDTH = 56f;
    private static final float HEIGHT = 20f;
    private float x, y;
    private boolean open;
    private float progress;

    private ThemeBlurPopup() {}

    public void toggle(float anchorX, float anchorY) {
        if (open) { open = false; return; }
        x = anchorX;
        y = anchorY;
        open = true;
    }

    public void close() { open = false; }

    public boolean click(double mx, double my, int button) {
        if (!open) return false;
        if (mx < x || mx > x + WIDTH || my < y || my > y + HEIGHT) {
            close();
            return true;
        }
        if (button == 0) {
            BackgroundMode.toggle();
            close();
        }
        return true;
    }

    public void render(DrawContext context, float delta) {
        float target = open ? 1f : 0f;
        progress += (target - progress) * (1f - (float) Math.exp(-.33f * Math.max(.1f, delta)));
        if (progress < .003f && !open) { progress = 0f; return; }
        float scale = Math.max(.015f, progress);
        float w = WIDTH * scale, h = HEIGHT * scale;
        float px = x + (WIDTH - w) * .5f, py = y + (HEIGHT - h) * .5f;
        int alpha = Math.min(255, (int) (230 * progress));
        Render2D.rect(px, py, w, h,
                ThemeColors.isLight() ? ((alpha << 24) | 0xF3F5F9) : ((alpha << 24) | 0x151B25), h * .5f);
        if (progress > .68f) {
            int textColor = ThemeColors.isLight() ? 0xFF20232B : 0xFFE7EDF8;
            float size = 7f * scale;
            float textWidth = Fonts.BOLD.getWidth("Blur", size);
            Fonts.BOLD.draw("Blur", px + (w - textWidth) * .5f,
                    py + (h - size) * .5f - .4f * scale, size, textColor);
            if (BackgroundMode.isBlur()) {
                Render2D.rect(px + w - 9f * scale, py + (h - 3f * scale) * .5f,
                        3f * scale, 3f * scale, ThemeColors.selected().accent(), 2f * scale);
            }
        }
    }
}
