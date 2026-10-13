package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import mind.beta.Initialization;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.screens.clickgui.impl.background.render.GuiTheme;
import mind.beta.util.render.GuiTransform;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class Potions extends AbstractHudElement {

    private static final Identifier BACKGROUND_IMAGE =
            Identifier.of("mind", "textures/background/pink.png");
    private static final float BACKGROUND_ASPECT = 5120f / 2880f;
    private static final float BACKGROUND_BLUR = 2.5f;

    private static final float CLOSED_WIDTH = 28f;
    private static final float MIN_OPEN_WIDTH = 76f;
    private static final float BASE_HEIGHT = 19f;
    private static final float LEFT_WIDTH = 22f;
    private static final float ROW_HEIGHT = 13f;
    private static final float VERTICAL_PADDING = 3f;
    private static final float RADIUS = 9.5f;
    private static final float FONT_SIZE = 7.0f;
    private static int accentTextColor() {
        return ThemeColors.accentText(230);
    }

    private static final int PANEL_R = GuiTheme.PANEL_RED;
    private static final int PANEL_G = GuiTheme.PANEL_GREEN;
    private static final int PANEL_B = GuiTheme.PANEL_BLUE;
    private static final int CONTENT_ALPHA = 175;

    private final List<PotionEntry> entries = new ArrayList<>();

    private float visibilityAnimation;
    private float blurAnimation;
    private float widthAnimation = CLOSED_WIDTH;
    private float heightAnimation = BASE_HEIGHT;

    private long lastFrameTime = System.currentTimeMillis();

    private static final class PotionEntry {
        private final String id;
        private String name;
        private String duration;
        private boolean visible;
        private float animation;
        private float renderOffsetY = Float.NaN;

        private PotionEntry(String id, String name, String duration) {
            this.id = id;
            this.name = name;
            this.duration = duration;
            this.visible = true;
        }
    }

    public Potions() {
        super("Potions", 300, 100, (int) CLOSED_WIDTH, (int) BASE_HEIGHT, true);
    }

    @Override
    public boolean visible() {
        return visibilityAnimation > 0.01f
                || blurAnimation > 0.01f
                || hasVisibleEntries()
                || isChat(mc.currentScreen);
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        syncEntries();
        this.lastTickDelta = tickDelta;
        drawDraggable(context, 255);
    }

    @Override
    public void tick() {
        syncEntries();
    }

    private void syncEntries() {
        for (PotionEntry entry : entries) {
            entry.visible = false;
        }

        if (mc.player != null) {
            Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();

            for (StatusEffectInstance effect : effects) {
                if (!effect.shouldShowIcon()) {
                    continue;
                }

                String id = effect.getEffectType().getKey()
                        .map(key -> key.getValue().toString())
                        .orElse("unknown_" + effect.hashCode());

                String name = effect.getEffectType().value().getName().getString();
                String duration = formatDuration(effect.getDuration());

                PotionEntry entry = findEntry(id);

                if (entry == null) {
                    entries.add(new PotionEntry(id, name, duration));
                } else {
                    entry.name = name;
                    entry.duration = duration;
                    entry.visible = true;
                }
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.name));
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        long now = System.currentTimeMillis();
        float delta = Math.min((now - lastFrameTime) / 1000f, 0.05f);
        lastFrameTime = now;

        if (alpha <= 0) {
            return;
        }

        updateEntryAnimations(delta);
        entries.removeIf(entry -> !entry.visible && entry.animation <= 0f);

        boolean chat = isChat(mc.currentScreen);
        boolean example = !hasTargetEntries() && chat;
        boolean shouldShow = hasTargetEntries() || example;

        visibilityAnimation = moveLinear(
                visibilityAnimation,
                shouldShow ? 1f : 0f,
                delta,
                shouldShow ? 7.8f : 9.0f
        );

        blurAnimation = visibilityAnimation;

        if (visibilityAnimation <= 0f && !shouldShow) {
            widthAnimation = CLOSED_WIDTH;
            heightAnimation = BASE_HEIGHT;
            this.width = (int) CLOSED_WIDTH;
            this.height = (int) BASE_HEIGHT;
            return;
        }

        float targetWidth = calculateWidth(example);
        float targetHeight = calculateHeight(example);

        widthAnimation = dampSnap(widthAnimation, targetWidth, delta, 34f, 0.08f);
        heightAnimation = targetHeight;

        this.width = (int) Math.ceil(widthAnimation);
        this.height = (int) Math.ceil(heightAnimation);

        float x = getX();
        float y = getY();

        float open = easeInOutCubic(visibilityAnimation);
        float centerX = x + widthAnimation * 0.5f;
        float centerY = y + heightAnimation * 0.5f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(open, open);
        context.getMatrices().translate(-centerX, -centerY);

        GuiTransform.begin(open, centerX, centerY);

        try {
            renderBackgroundImage(x, y, widthAnimation, heightAnimation, alpha);

            Render2D.rect(
                    x,
                    y,
                    widthAnimation,
                    heightAnimation,
                    ThemeColors.color(PANEL_R, PANEL_G, PANEL_B, CONTENT_ALPHA).getRGB(),
                    RADIUS
            );

            renderLeftMark(
                    x,
                    y,
                    heightAnimation,
                    LEFT_WIDTH
            );

            Render2D.rect(
                    x + LEFT_WIDTH,
                    y + 6f,
                    0.7f,
                    Math.max(0f, heightAnimation - 12f),
                    ThemeColors.color(0, 128, 255, 18).getRGB(),
                    0f
            );

            float contentX = x + LEFT_WIDTH;
            float contentWidth = Math.max(0f, widthAnimation - LEFT_WIDTH);

            if (example) {
                renderRow(
                        "Example Effect",
                        "00:00",
                        contentX,
                        y + heightAnimation * 0.5f,
                        contentWidth,
                        1f
                );
            } else {
                float currentY = y + VERTICAL_PADDING;

                for (PotionEntry entry : entries) {
                    if (entry.animation <= 0f) {
                        continue;
                    }

                    float rowProgress = easeInOutCubic(entry.animation);
                    float animatedRowHeight = ROW_HEIGHT * rowProgress;
                    float targetCenter = currentY + animatedRowHeight * 0.5f;

                    if (Float.isNaN(entry.renderOffsetY)) {
                        entry.renderOffsetY = targetCenter - y;
                    } else {
                        entry.renderOffsetY = dampSnap(
                                entry.renderOffsetY,
                                targetCenter - y,
                                delta,
                                34f,
                                0.015f
                        );
                    }

                    float rowCenter = y + entry.renderOffsetY;

                    renderRow(
                            entry.name,
                            entry.duration,
                            contentX,
                            rowCenter,
                            contentWidth,
                            rowProgress
                    );

                    currentY += animatedRowHeight;
                }
            }
        } finally {
            GuiTransform.end();
            context.getMatrices().popMatrix();
        }
    }

    private void renderBackgroundImage(float x, float y, float width, float height, int alpha) {
        if (width <= 0f || height <= 0f) {
            return;
        }
        if (mind.beta.util.render.theme.BackgroundMode.isBlur()) {
            HudPanelStyle.panel(x, y, width, height, RADIUS, alpha);
            return;
        }
        if (ThemeColors.isLight()) {
            Render2D.rect(x, y, width, height,
                    (Math.max(0, Math.min(255, alpha)) << 24) | 0xF7F9FD, RADIUS);
            return;
        }

        float panelAspect = width / height;
        float uSpan = Math.min(1f, panelAspect / BACKGROUND_ASPECT);
        float vSpan = Math.min(1f, BACKGROUND_ASPECT / panelAspect);
        float u0 = (1f - uSpan) * 0.5f;
        float v0 = (1f - vSpan) * 0.5f;
        int tint = ThemeColors.textureTint(alpha);

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawBlurredTexture(
                        BACKGROUND_IMAGE,
                        x,
                        y,
                        width,
                        height,
                        u0,
                        v0,
                        u0 + uSpan,
                        v0 + vSpan,
                        new int[]{tint, tint, tint, tint},
                        new float[]{RADIUS, RADIUS, RADIUS, RADIUS},
                        BACKGROUND_BLUR
                );

        Render2D.rect(
                x,
                y,
                width,
                height,
                ThemeColors.color(0, 64, 128, Math.round(42f * alpha / 255f)).getRGB(),
                RADIUS
        );
    }

    private void renderLeftMark(
            float x,
            float y,
            float drawHeight,
            float leftWidth
    ) {
        String icon = "I";
        float iconSize = 8f;
        float iconWidth = Fonts.MIND_FONT.getWidth(icon, iconSize);

        Fonts.MIND_FONT.draw(
                icon,
                x + leftWidth * 0.5f - iconWidth * 0.5f,
                y + drawHeight * 0.5f - iconSize * 0.5f + 0.5f,
                iconSize,
                accentTextColor()
        );
    }

    private void renderRow(
            String name,
            String duration,
            float contentX,
            float centerY,
            float contentWidth,
            float rowProgress
    ) {
        if (rowProgress <= 0.001f) {
            return;
        }

        float cardScale = smoothStep(rowProgress);

        float fontSize = FONT_SIZE * cardScale;
        float baseDurationWidth = Fonts.MEDIUM.getWidth(duration, FONT_SIZE);
        float durationWidth = Fonts.MEDIUM.getWidth(duration, fontSize);

        float durationPadding = 4f * cardScale;
        float durationBoxWidth = baseDurationWidth * cardScale + durationPadding * 2f;

        float nameX = contentX + 6f;
        float textY = centerY - fontSize * 0.5f + 0.35f;

        float durationX = contentX + contentWidth - 5f - durationBoxWidth;

        Fonts.MEDIUM.draw(
                name,
                nameX,
                textY,
                fontSize,
                ThemeColors.color(228, 232, 238, 236).getRGB()
        );

        Fonts.MEDIUM.draw(
                duration,
                durationX + durationBoxWidth * 0.5f - durationWidth * 0.5f,
                textY,
                fontSize,
                accentTextColor()
        );
    }

    private float calculateWidth(boolean example) {
        float target = MIN_OPEN_WIDTH;

        if (example) {
            float name = Fonts.MEDIUM.getWidth("Example Effect", FONT_SIZE);
            float duration = Fonts.MEDIUM.getWidth("00:00", FONT_SIZE);
            return Math.max(target, LEFT_WIDTH + 6f + name + 9f + duration + 8f + 5f);
        }

        for (PotionEntry entry : entries) {
            if (!entry.visible && entry.animation < 0.005f) {
                continue;
            }

            float name = Fonts.MEDIUM.getWidth(entry.name, FONT_SIZE);
            float duration = Fonts.MEDIUM.getWidth(entry.duration, FONT_SIZE);

            target = Math.max(
                    target,
                    LEFT_WIDTH + 6f + name + 9f + duration + 8f + 5f
            );
        }

        if (!hasTargetEntries() && !hasVisibleEntries()) {
            target = CLOSED_WIDTH;
        }

        return target;
    }

    private float calculateHeight(boolean example) {
        if (example) {
            return BASE_HEIGHT;
        }

        float rows = getAnimatedRowsHeight();

        if (rows <= ROW_HEIGHT) {
            return BASE_HEIGHT;
        }

        return Math.max(BASE_HEIGHT, rows + VERTICAL_PADDING * 2f);
    }

    private float getAnimatedRowsHeight() {
        float result = 0f;

        for (PotionEntry entry : entries) {
            result += ROW_HEIGHT * easeInOutCubic(entry.animation);
        }

        return result;
    }

    private void updateEntryAnimations(float delta) {
        for (PotionEntry entry : entries) {
            entry.animation = moveLinear(
                    entry.animation,
                    entry.visible ? 1f : 0f,
                    delta,
                    entry.visible ? 9.5f : 11.5f
            );

            if (entry.animation < 0.0001f) {
                entry.animation = 0f;
                entry.renderOffsetY = Float.NaN;
            } else if (entry.animation > 0.9999f) {
                entry.animation = 1f;
            }
        }
    }

    private PotionEntry findEntry(String id) {
        for (PotionEntry entry : entries) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }

        return null;
    }

    private boolean hasTargetEntries() {
        for (PotionEntry entry : entries) {
            if (entry.visible) {
                return true;
            }
        }

        return false;
    }

    private boolean hasVisibleEntries() {
        for (PotionEntry entry : entries) {
            if (entry.animation > 0.005f) {
                return true;
            }
        }

        return false;
    }

    private String formatDuration(int ticks) {
        if (ticks == -1) {
            return "∞";
        }

        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    private float easeInOutCubic(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3f) * 0.5f;
    }

    private float dampSnap(float current, float target, float delta, float speed, float snapDistance) {
        float diff = target - current;

        if (Math.abs(diff) <= snapDistance) {
            return target;
        }

        float factor = 1f - (float) Math.exp(-speed * delta);
        float next = current + diff * factor;

        if (Math.abs(target - next) <= snapDistance) {
            return target;
        }

        return next;
    }

    private float smoothStep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    private float moveLinear(float current, float target, float delta, float speed) {
        float step = speed * delta;

        if (current < target) {
            return Math.min(target, current + step);
        }

        if (current > target) {
            return Math.max(target, current - step);
        }

        return current;
    }
}
