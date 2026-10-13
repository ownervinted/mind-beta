package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import mind.beta.Initialization;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.GuiTransform;
import mind.beta.util.render.font.Fonts;
import mind.beta.screens.clickgui.impl.background.render.GuiTheme;
import mind.beta.util.string.KeyHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class HotKeys extends AbstractHudElement {

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

    private final List<BindEntry> entries = new ArrayList<>();

    private float visibilityAnimation;
    private float blurAnimation;
    private float widthAnimation = CLOSED_WIDTH;
    private float heightAnimation = BASE_HEIGHT;

    private long lastFrameTime = System.currentTimeMillis();
    private long lastKeyChange;
    private String currentRandomKey = "A";

    private static final class BindEntry {
        private final String name;
        private String key;
        private boolean visible;
        private float animation;
        private float renderOffsetY = Float.NaN;

        private BindEntry(String name, String key) {
            this.name = name;
            this.key = key;
            this.visible = true;
        }
    }

    public HotKeys() {
        super("HotKeys", 300, 40, (int) CLOSED_WIDTH, (int) BASE_HEIGHT, true);
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
        if (Initialization.getInstance() == null
                || Initialization.getInstance().getManager() == null
                || Initialization.getInstance().getManager().getModuleProvider() == null) {
            return;
        }

        for (BindEntry entry : entries) {
            entry.visible = false;
        }

        List<ModuleStructure> modules = Initialization.getInstance()
                .getManager()
                .getModuleProvider()
                .getModuleStructures();

        for (ModuleStructure module : modules) {
            if (!module.isState() || module.getKey() == GLFW.GLFW_KEY_UNKNOWN) {
                continue;
            }

            String name = module.getName();
            String key = KeyHelper.getKeyName(module.getKey());

            BindEntry entry = findEntry(name);

            if (entry == null) {
                entries.add(new BindEntry(name, key));
            } else {
                entry.key = key;
                entry.visible = true;
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.name));

        boolean chat = isChat(mc.currentScreen);

        if (!hasTargetEntries() && chat) {
            long now = System.currentTimeMillis();

            if (now - lastKeyChange >= 1000L) {
                List<String> keys = List.of("A", "B", "C", "D", "E");
                currentRandomKey = keys.get(new Random().nextInt(keys.size()));
                lastKeyChange = now;
            }
        }
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
                        "Example Module",
                        currentRandomKey.toUpperCase(),
                        contentX,
                        y + heightAnimation * 0.5f,
                        contentWidth,
                        1f
                );
            } else {
                float currentY = y + VERTICAL_PADDING;

                for (BindEntry entry : entries) {
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
                            entry.key.toUpperCase(),
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
                        BACKGROUND_IMAGE, x, y, width, height,
                        u0, v0, u0 + uSpan, v0 + vSpan,
                        new int[]{tint, tint, tint, tint},
                        new float[]{RADIUS, RADIUS, RADIUS, RADIUS},
                        BACKGROUND_BLUR
                );

        Render2D.rect(x, y, width, height,
                ThemeColors.color(0, 64, 128, Math.round(42f * alpha / 255f)).getRGB(), RADIUS);
    }

    private void renderLeftMark(
            float x,
            float y,
            float drawHeight,
            float leftWidth
    ) {
        String icon = "b";
        float iconSize = 8f;
        float iconWidth = Fonts.HUD_ICON.getWidth(icon, iconSize);

        Fonts.HUD_ICON.draw(
                icon,
                x + leftWidth * 0.5f - iconWidth * 0.5f,
                y + drawHeight * 0.5f - iconSize * 0.5f + 0.5f,
                iconSize,
                accentTextColor()
        );
    }

    private void renderRow(
            String name,
            String key,
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
        float baseNameWidth = Fonts.MEDIUM.getWidth(name, FONT_SIZE);
        float baseKeyWidth = Fonts.MEDIUM.getWidth(key, FONT_SIZE);
        float keyWidth = Fonts.MEDIUM.getWidth(key, fontSize);

        float keyPadding = 4f * cardScale;
        float keyBoxWidth = baseKeyWidth * cardScale + keyPadding * 2f;

        float nameX = contentX + 6f;
        float textY = centerY - fontSize * 0.5f + 0.35f;

        float keyX = contentX + contentWidth - 5f - keyBoxWidth;

        Fonts.MEDIUM.draw(
                name,
                nameX,
                textY,
                fontSize,
                ThemeColors.color(228, 232, 238, 236).getRGB()
        );

        Fonts.MEDIUM.draw(
                key,

                keyX + keyBoxWidth * 0.5f - keyWidth * 0.5f,
                textY,
                fontSize,
                accentTextColor()
        );
    }

    private float calculateWidth(boolean example) {
        float target = MIN_OPEN_WIDTH;

        if (example) {
            float name = Fonts.MEDIUM.getWidth("Example Module", FONT_SIZE);
            float key = Fonts.MEDIUM.getWidth(currentRandomKey.toUpperCase(), FONT_SIZE);
            return Math.max(target, LEFT_WIDTH + 6f + name + 9f + key + 8f + 5f);
        }

        for (BindEntry entry : entries) {
            if (!entry.visible && entry.animation < 0.005f) {
                continue;
            }

            float name = Fonts.MEDIUM.getWidth(entry.name, FONT_SIZE);
            float key = Fonts.MEDIUM.getWidth(entry.key.toUpperCase(), FONT_SIZE);

            target = Math.max(
                    target,
                    LEFT_WIDTH + 6f + name + 9f + key + 8f + 5f
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

        for (BindEntry entry : entries) {
            result += ROW_HEIGHT * easeInOutCubic(entry.animation);
        }

        return result;
    }

    private void updateEntryAnimations(float delta) {
        for (BindEntry entry : entries) {
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

    private BindEntry findEntry(String name) {
        for (BindEntry entry : entries) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }

        return null;
    }

    private boolean hasTargetEntries() {
        for (BindEntry entry : entries) {
            if (entry.visible) {
                return true;
            }
        }

        return false;
    }

    private boolean hasVisibleEntries() {
        for (BindEntry entry : entries) {
            if (entry.animation > 0.005f) {
                return true;
            }
        }

        return false;
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

    private float softEase(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        float smooth = t * t * (3f - 2f * t);
        return t + (smooth - t) * 0.35f;
    }

    private float smootherStep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
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

    private float approach(float current, float target, float delta, float speed) {
        float factor = 1f - (float) Math.exp(-speed * delta);
        return current + (target - current) * factor;
    }

    private float spring(float current, float target, float delta, float speed) {
        float factor = 1f - (float) Math.exp(-speed * delta);
        float eased = 1f - (1f - factor) * (1f - factor);
        return current + (target - current) * eased;
    }

    private float easeOutQuart(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return 1f - (float) Math.pow(1f - t, 4f);
    }

    private float easeOutQuint(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return 1f - (float) Math.pow(1f - t, 5f);
    }

    private int clampAlpha(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
