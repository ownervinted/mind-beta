package mind.beta.screens.clickgui.impl.background.render;

import mind.beta.util.render.theme.ThemeColors;
import java.awt.Color;

public final class GuiTheme {

    public static final int PANEL_RED = 4;
    public static final int PANEL_GREEN = 11;
    public static final int PANEL_BLUE = 20;
    public static final int PANEL_ALPHA = 190;

    private GuiTheme() {
    }

    public static int panelColor(float alphaMultiplier) {
        float alpha = Math.max(0f, Math.min(1f, alphaMultiplier));
        return ThemeColors.color(
                PANEL_RED,
                PANEL_GREEN,
                PANEL_BLUE,
                (int) (PANEL_ALPHA * alpha)
        ).getRGB();
    }
}
