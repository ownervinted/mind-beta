package mind.beta.screens.hud;

import mind.beta.Initialization;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.modules.impl.player.NameProtect;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.pipeline.BlurPipeline;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends AbstractHudElement {

    private static final BlurPipeline BLUR = new BlurPipeline();

    private static final float HEIGHT = 19f;
    private static final float RADIUS = 9.5f;
    private static final float NAME_SIZE = 7.2f;
    private static final float TEXT_SIZE = 6.8f;
    private static final float PADDING_X = 7f;
    private static final float GAP = 3f;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    public Watermark() {
        super("Watermark", 4, 5, 140, (int) HEIGHT, false);
        startAnimation();
    }

    @Override
    public boolean visible() {
        return true;
    }

    @Override
    public void tick() {
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) {
            return;
        }

        String nickname = getDisplayNickname();

        String time = LocalTime.now().format(TIME_FORMAT);
        String name = "Mind.club";
        String beta = "[beta]";
        String separator = " | ";

        float nameWidth = getWaveTextWidth(name, NAME_SIZE);
        float betaWidth = Fonts.MEDIUM.getWidth(beta, TEXT_SIZE);
        float sepWidth = Fonts.MEDIUM.getWidth(separator, TEXT_SIZE);
        float nickWidth = Fonts.MEDIUM.getWidth(nickname, TEXT_SIZE);
        float timeWidth = Fonts.MEDIUM.getWidth(time, TEXT_SIZE);

        float width = PADDING_X * 2f
                + nameWidth
                + GAP
                + betaWidth
                + sepWidth
                + nickWidth
                + sepWidth
                + timeWidth;

        setWidth((int) Math.ceil(width));
        setHeight((int) HEIGHT);

        HudPanelStyle.panel(x, y, width, HEIGHT, RADIUS, 255);

        float textY = y + HEIGHT * 0.5f - NAME_SIZE * 0.5f + 0.2f;
        float cursorX = x + PADDING_X;

        cursorX = drawWaveText(name, cursorX, textY, NAME_SIZE);
        cursorX += GAP;

        Fonts.MEDIUM.draw(
                beta,
                cursorX,
                y + HEIGHT * 0.5f - TEXT_SIZE * 0.5f + 0.35f,
                TEXT_SIZE,
                ThemeColors.color(150, 156, 168, 218).getRGB()
        );
        cursorX += betaWidth;

        Fonts.MEDIUM.draw(
                separator,
                cursorX,
                y + HEIGHT * 0.5f - TEXT_SIZE * 0.5f + 0.35f,
                TEXT_SIZE,
                ThemeColors.color(110, 118, 132, 195).getRGB()
        );
        cursorX += sepWidth;

        Fonts.MEDIUM.draw(
                nickname,
                cursorX,
                y + HEIGHT * 0.5f - TEXT_SIZE * 0.5f + 0.35f,
                TEXT_SIZE,
                ThemeColors.color(226, 230, 236, 232).getRGB()
        );
        cursorX += nickWidth;

        Fonts.MEDIUM.draw(
                separator,
                cursorX,
                y + HEIGHT * 0.5f - TEXT_SIZE * 0.5f + 0.35f,
                TEXT_SIZE,
                ThemeColors.color(110, 118, 132, 195).getRGB()
        );
        cursorX += sepWidth;

        Fonts.MEDIUM.draw(
                time,
                cursorX,
                y + HEIGHT * 0.5f - TEXT_SIZE * 0.5f + 0.35f,
                TEXT_SIZE,
                ThemeColors.color(170, 178, 190, 220).getRGB()
        );
    }

    private String getDisplayNickname() {
        String nickname = mc.player != null
                ? mc.player.getName().getString()
                : mc.getSession().getUsername();

        Initialization initialization = Initialization.getInstance();

        if (initialization == null || initialization.getManager() == null) {
            return nickname;
        }

        if (initialization.getManager().getModuleRepository() == null) {
            return nickname;
        }

        for (ModuleStructure module : initialization.getManager().getModuleRepository().modules()) {
            if (module instanceof NameProtect nameProtect) {
                if (nameProtect.isState()) {
                    String protectedName = nameProtect.getNameSetting().getText();

                    if (protectedName != null && !protectedName.isBlank()) {
                        return protectedName;
                    }
                }

                break;
            }
        }

        return nickname;
    }

    private float drawWaveText(String text, float x, float y, float size) {
        long now = System.currentTimeMillis();
        float cursor = x;

        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            float wave = (float) ((Math.sin(now * 0.0055 + i * 0.85) + 1.0) * 0.5);
            wave = wave * wave * (3f - 2f * wave);

            int color = blendColor(
                    ThemeColors.color(225, 228, 234, 235),
                    ThemeColors.color(0, 128, 255, 245),
                    wave
            );

            Fonts.MEDIUM.draw(ch, cursor, y, size, color);
            cursor += Fonts.MEDIUM.getWidth(ch, size);
        }

        return cursor;
    }

    private float getWaveTextWidth(String text, float size) {
        float width = 0f;

        for (int i = 0; i < text.length(); i++) {
            width += Fonts.MEDIUM.getWidth(
                    String.valueOf(text.charAt(i)),
                    size
            );
        }

        return width;
    }

    private int blendColor(Color from, Color to, float t) {
        float progress = Math.max(0f, Math.min(1f, t));

        int r = Math.round(
                from.getRed() + (to.getRed() - from.getRed()) * progress
        );

        int g = Math.round(
                from.getGreen() + (to.getGreen() - from.getGreen()) * progress
        );

        int b = Math.round(
                from.getBlue() + (to.getBlue() - from.getBlue()) * progress
        );

        int a = Math.round(
                from.getAlpha() + (to.getAlpha() - from.getAlpha()) * progress
        );

        return new Color(r, g, b, a).getRGB();
    }
}