package mind.beta.screens.clickgui.impl.background.render;

import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.render.theme.ClientTheme;
import mind.beta.util.render.theme.BackgroundMode;
import mind.beta.util.render.pipeline.BlurPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import mind.beta.util.render.Render2D;

import java.awt.Color;

public class BackgroundRenderer {

    private static final BlurPipeline MODE_BLUR = new BlurPipeline();
    private static final Identifier BACKGROUND_IMAGE =
            Identifier.of("mind", "textures/background/pink.png");

    private static final float GUI_WIDTH = 400f;
    private static final float GUI_HEIGHT = 250f;
    private static final float CATEGORY_WIDTH = 92f;
    private static final float CONTENT_TOP = 32f;
    private static final int CONTENT_ALPHA = 180;

    private static final float GUI_RADIUS = 8f;

    public void renderBackgroundImage(
            float bgX,
            float bgY,
            float width,
            float height,
            float alphaMultiplier
    ) {
        float alpha = clamp(alphaMultiplier);

        if (alpha <= 0.01f) {
            return;
        }

        if (BackgroundMode.isBlur()) {
            MODE_BLUR.drawBlur(bgX, bgY, width, height, 18f,
                    new float[]{GUI_RADIUS, GUI_RADIUS, GUI_RADIUS, GUI_RADIUS},
                    ThemeColors.isLight() ? 0x66F7F9FD : 0x99101520);
            return;
        }

        if (ThemeColors.selected() == ClientTheme.IVORY) {
            Render2D.rect(bgX, bgY, width, height,
                    ((int) (255f * alpha) << 24) | 0xF8FAFF, GUI_RADIUS);
            return;
        }

        int color = ThemeColors.textureTint((int) (255f * alpha));

        Render2D.texture(
                BACKGROUND_IMAGE,
                bgX,
                bgY,
                width,
                height,
                0f,
                0f,
                1f,
                1f,
                color,
                1f,
                GUI_RADIUS
        );
    }

    public void render(
            DrawContext context,
            float bgX,
            float bgY,
            float alphaMultiplier
    ) {
        renderBackgroundImage(
                bgX,
                bgY,
                GUI_WIDTH,
                GUI_HEIGHT,
                alphaMultiplier
        );

        float alpha = clamp(alphaMultiplier);

        Render2D.rect(
                bgX,
                bgY,
                GUI_WIDTH,
                GUI_HEIGHT,
                ThemeColors.color(
                        0,
                        64,
                        128,
                        (int) (42 * alpha)
                ).getRGB(),
                GUI_RADIUS
        );
    }

    public void renderCategoryPanel(
            float bgX,
            float bgY,
            float bgHeight,
            float alphaMultiplier
    ) {
        float alpha = clamp(alphaMultiplier);

        if (alpha <= 0.01f) {
            return;
        }

        int contentAlpha =
                (int) (CONTENT_ALPHA * alpha);

        Render2D.rect(
                bgX,
                bgY,
                CATEGORY_WIDTH,
                bgHeight,
                GuiTheme.panelColor(alpha),
                GUI_RADIUS,
                0f,
                0f,
                GUI_RADIUS
        );

        Render2D.rect(
                bgX + CATEGORY_WIDTH,
                bgY + CONTENT_TOP,
                GUI_WIDTH - CATEGORY_WIDTH,
                bgHeight - CONTENT_TOP,
                ThemeColors.color(
                        4,
                        11,
                        20,
                        contentAlpha
                ).getRGB(),
                0f,
                0f,
                GUI_RADIUS,
                0f
        );
    }

    private float clamp(float value) {
        return Math.max(
                0f,
                Math.min(
                        1f,
                        value
                )
        );
    }
}