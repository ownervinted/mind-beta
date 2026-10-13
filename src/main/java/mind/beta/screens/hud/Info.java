package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.modules.impl.render.Hud;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.Color;

public class Info extends AbstractHudElement {

    private static final float HEIGHT = 19f;
    private static final float RADIUS = 9.5f;
    private static final float FONT_SIZE = 6.5f;
    private static final float PADDING_X = 8f;
    private static final float GROUP_GAP = 7f;

    private double lastX;
    private double lastZ;
    private double currentBps;
    private double displayBps;
    private double targetBps;
    private long lastUpdateTime;

    private static final double BPS_SMOOTHING = 0.05;
    private static final double DISPLAY_SMOOTHING = 0.03;

    public Info() {
        super("Info", 10, 28, 150, (int) HEIGHT, false);
        startAnimation();
    }

    @Override
    public void tick() {
    }

    private double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0 || mc.player == null) return;

        boolean showBps = Hud.getInstance() != null && Hud.getInstance().showBps.isValue();

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;

        if (lastUpdateTime > 0 && deltaTime > 0) {
            double dx = mc.player.getX() - lastX;
            double dz = mc.player.getZ() - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instantBps = distance / deltaTime;

            currentBps += (instantBps - currentBps) * BPS_SMOOTHING;
            targetBps = roundToStep(currentBps, 0.50);
        }

        displayBps += (targetBps - displayBps) * DISPLAY_SMOOTHING;

        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastUpdateTime = currentTime;

        String xValue = String.valueOf((int) mc.player.getX());
        String yValue = String.valueOf((int) mc.player.getY());
        String zValue = String.valueOf((int) mc.player.getZ());
        String bpsValue = String.format("%.2f", roundToStep(displayBps, 0.50));

        float xGroup = groupWidth("X", xValue);
        float yGroup = groupWidth("Y", yValue);
        float zGroup = groupWidth("Z", zValue);
        float bpsGroup = showBps ? groupWidth("B/S", bpsValue) : 0f;

        float width = PADDING_X * 2f + xGroup + GROUP_GAP + yGroup + GROUP_GAP + zGroup;

        if (showBps) {
            width += GROUP_GAP + 0.7f + GROUP_GAP + bpsGroup;
        }

        setWidth((int) Math.ceil(width));
        setHeight((int) HEIGHT);

        float x = getX();
        float y = getY();
        int panelAlpha = Math.max(0, Math.min(255, alpha));

        HudPanelStyle.panel(x, y, width, HEIGHT, RADIUS, panelAlpha);

        float cursor = x + PADDING_X;
        float textY = y + HEIGHT * 0.5f - FONT_SIZE * 0.5f + 0.2f;

        cursor = drawGroup("X", xValue, cursor, textY, panelAlpha);
        cursor += GROUP_GAP;
        cursor = drawGroup("Y", yValue, cursor, textY, panelAlpha);
        cursor += GROUP_GAP;
        cursor = drawGroup("Z", zValue, cursor, textY, panelAlpha);

        if (showBps) {
            cursor += GROUP_GAP;

            Render2D.rect(
                    cursor,
                    y + 5f,
                    0.7f,
                    HEIGHT - 10f,
                    ThemeColors.color(0, 128, 255, Math.round(24f * panelAlpha / 255f)).getRGB(),
                    0f
            );

            cursor += GROUP_GAP + 0.7f;
            drawGroup("B/S", bpsValue, cursor, textY, panelAlpha);
        }
    }

    private float groupWidth(String label, String value) {
        return Fonts.MEDIUM.getWidth(label, FONT_SIZE)
                + 3f
                + Fonts.MEDIUM.getWidth(value, FONT_SIZE);
    }

    private float drawGroup(
            String label,
            String value,
            float x,
            float y,
            int alpha
    ) {
        Fonts.MEDIUM.draw(
                label,
                x,
                y,
                FONT_SIZE,
                ThemeColors.color(145, 156, 170, Math.round(205f * alpha / 255f)).getRGB()
        );

        float labelWidth = Fonts.MEDIUM.getWidth(label, FONT_SIZE);
        float valueX = x + labelWidth + 3f;

        Fonts.MEDIUM.draw(
                value,
                valueX,
                y,
                FONT_SIZE,
                HudPanelStyle.text(alpha)
        );

        return valueX + Fonts.MEDIUM.getWidth(value, FONT_SIZE);
    }
}
