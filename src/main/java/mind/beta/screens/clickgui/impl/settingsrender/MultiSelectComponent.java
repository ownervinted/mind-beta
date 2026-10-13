package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.pipeline.BlurPipeline;
import mind.beta.util.render.shader.Scissor;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;

public class MultiSelectComponent extends AbstractSettingComponent {
    private final MultiSelectSetting multiSelectSetting;
    private boolean expanded = false;
    private float expandAnimation = 0f;
    private float hoverAnimation = 0f;
    private float scrollOffset = 0f;
    private float scrollOffsetAnimated = 0f;
    private boolean scrollingRight = true;
    private long scrollPauseTime = 0;

    private float descScrollOffset = 0f;
    private boolean descScrollingRight = true;
    private long descScrollPauseTime = 0;

    private final Map<String, Float> optionHoverAnimations = new HashMap<>();
    private final Map<String, Float> itemAlphaAnimations = new HashMap<>();
    private final Map<String, Float> itemXPositions = new HashMap<>();
    private final Map<String, Float> itemTargetPositions = new HashMap<>();
    private final Map<String, Float> itemRevealAnimations = new HashMap<>();
    private final Map<String, Float> optionRevealAnimations = new HashMap<>();
    private final Set<String> previousSelected = new HashSet<>();

    private float noneAlphaAnimation = 0f;
    private long lastUpdateTime = System.currentTimeMillis();

    private static final float ANIMATION_SPEED = 10f;
    private static final float COLLAPSE_SPEED = 12f;
    private static final long SCROLL_PAUSE_DURATION = 2000;
    private static final float BOX_WIDTH = 82f;
    private static final float BOX_HEIGHT = 14f;
    private static final float OPTION_HEIGHT = 17f;
    private static final float SCROLL_PIXELS_PER_SECOND = 20f;
    private static final float DESC_PADDING = 8f;
    private static final float ITEM_ANIMATION_SPEED = 5f;
    private static final float POSITION_ANIMATION_SPEED = 5.5f;
    private static final float OPTION_REVEAL_SPEED = 12f;
    private static final BlurPipeline BLUR = new BlurPipeline();

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.multiSelectSetting = setting;
        for (String option : setting.getList()) {
            optionHoverAnimations.put(option, 0f);
        }
        previousSelected.addAll(setting.getSelected());

        float initX = 0;
        for (String item : setting.getList()) {
            if (setting.isSelected(item)) {
                itemAlphaAnimations.put(item, 1f);
                itemXPositions.put(item, initX);
                itemTargetPositions.put(item, initX);
                initX += Fonts.MEDIUM.getWidth(item + ", ", 6f);
            }
        }

        noneAlphaAnimation = setting.getSelected().isEmpty() ? 1f : 0f;
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.05f);
        lastUpdateTime = currentTime;
        return Math.max(deltaTime, 0.001f);
    }

    private float lerp(float current, float target, float speed) {
        float factor = Math.min(speed, 1f);
        return current + (target - current) * factor;
    }

    private void updateItemAnimations(float deltaTime) {
        List<String> currentSelected = multiSelectSetting.getSelected();

        for (String item : multiSelectSetting.getList()) {
            boolean selected = currentSelected.contains(item);
            float reveal = itemRevealAnimations.getOrDefault(item, selected ? 1f : 0f);
            reveal = lerp(reveal, selected ? 1f : 0f, deltaTime * ITEM_ANIMATION_SPEED);
            itemRevealAnimations.put(item, reveal);

            if (!itemAlphaAnimations.containsKey(item)) {
                itemAlphaAnimations.put(item, selected ? 1f : 0f);
                itemXPositions.put(item, selected ? 0f : -12f);
                itemTargetPositions.put(item, 0f);
            }
        }

        float targetX = 0f;
        for (String item : multiSelectSetting.getList()) {
            float reveal = itemRevealAnimations.getOrDefault(item, 0f);
            if (reveal <= 0.001f) {
                itemTargetPositions.put(item, -Fonts.MEDIUM.getWidth(item + ", ", 6f));
                continue;
            }

            itemTargetPositions.put(item, targetX);
            targetX += Fonts.MEDIUM.getWidth(item + ", ", 6f) * reveal;
        }

        for (String item : multiSelectSetting.getList()) {
            float reveal = itemRevealAnimations.getOrDefault(item, 0f);
            float alpha = itemAlphaAnimations.getOrDefault(item, 0f);
            float targetAlpha = reveal;
            itemAlphaAnimations.put(item, lerp(alpha, targetAlpha, deltaTime * ITEM_ANIMATION_SPEED));

            float target = itemTargetPositions.getOrDefault(item, 0f);
            float current = itemXPositions.getOrDefault(item, -12f);
            float startOffset = -Fonts.MEDIUM.getWidth(item + ", ", 6f) * (1f - reveal);
            float animatedTarget = target + startOffset;
            itemXPositions.put(item, lerp(current, animatedTarget, deltaTime * POSITION_ANIMATION_SPEED));
        }

        boolean hasVisibleItems = false;
        for (float alpha : itemAlphaAnimations.values()) {
            if (alpha > 0.01f) {
                hasVisibleItems = true;
                break;
            }
        }

        float noneTarget = (!hasVisibleItems && currentSelected.isEmpty()) ? 1f : 0f;
        noneAlphaAnimation = lerp(noneAlphaAnimation, noneTarget, deltaTime * ITEM_ANIMATION_SPEED);

        previousSelected.clear();
        previousSelected.addAll(currentSelected);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        updateItemAnimations(deltaTime);

        boolean hovered = isMainHover(mouseX, mouseY);
        hoverAnimation = lerp(hoverAnimation, hovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        expandAnimation = lerp(expandAnimation, expanded ? 1f : 0f, 1f - (float) Math.exp(-Math.max(.1f, delta) * .65f));

        Fonts.MEDIUM.draw(multiSelectSetting.getName(), x + 0.5f, y + height / 2 - 3.25f, 6.5f,
                applyAlpha(ThemeColors.color(226, 237, 248, 215)).getRGB());

        float boxX = x + width - BOX_WIDTH - 1f;
        float boxY = y + height / 2f - BOX_HEIGHT / 2f;
        int bgAlpha = (int)((8f + hoverAnimation * 12f) * alphaMultiplier);
        if (bgAlpha > 0) {
            Render2D.rect(boxX, boxY, BOX_WIDTH, BOX_HEIGHT,
                    ThemeColors.color(255, 255, 255, bgAlpha).getRGB(), 5f);
        }

        renderSelectedText(boxX, boxY, BOX_WIDTH, BOX_HEIGHT, deltaTime);
    }

    public void renderDropdown(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = Math.min(Math.max(delta, 0.001f), 0.05f);
        float target = expanded ? 1f : 0f;
        expandAnimation = lerp(
                expandAnimation,
                target,
                1f - (float) Math.exp(-Math.max(.1f, delta) * .65f)
        );

        if (expandAnimation <= 0.005f) {
            return;
        }

        float reveal = smoothStep(expandAnimation);
        float scale = Math.max(.002f, reveal);
        float boxX = x + width - BOX_WIDTH - 1f;
        float boxY = y + height / 2f - BOX_HEIGHT / 2f;
        float startY = boxY + BOX_HEIGHT + 3f;
        float fullHeight = multiSelectSetting.getList().size() * OPTION_HEIGHT;
        float visibleHeight = fullHeight * scale;
        float panelAlpha = alphaMultiplier;

        int blurAlpha = (int) (92f * panelAlpha);

        if (blurAlpha > 0 && visibleHeight > 0.5f) {
            BLUR.drawBlur(
                    boxX,
                    startY,
                    BOX_WIDTH,
                    visibleHeight,
                    8f,
                    new float[]{6f, 6f, 6f, 6f},
                    ThemeColors.color(6, 19, 33, blurAlpha).getRGB()
            );
        }

        if (visibleHeight < 1f) {
            return;
        }

        Render2D.rect(
                boxX,
                startY,
                BOX_WIDTH,
                visibleHeight,
                ThemeColors.color(
                        6,
                        19,
                        33,
                        (int) (118f * panelAlpha)
                ).getRGB(),
                6f
        );

        Scissor.enable(
                boxX,
                startY,
                BOX_WIDTH,
                visibleHeight,
                2
        );

        float optionY = startY;

        for (String option : multiSelectSetting.getList()) {
            boolean selected = multiSelectSetting.isSelected(option);

            float selectedAnim = optionHoverAnimations.getOrDefault(
                    option + "_selected",
                    selected ? 1f : 0f
            );

            selectedAnim = lerp(
                    selectedAnim,
                    selected ? 1f : 0f,
                    deltaTime * 9f
            );

            optionHoverAnimations.put(
                    option + "_selected",
                    selectedAnim
            );
            int textAlpha = (int) (225f * panelAlpha);
            int neutral = ThemeColors.isLight() ? 0xFF555D69 : 0xFFE1E7F0;
            int themed = ThemeColors.selected().accent();
            int red = Math.round(((neutral >> 16) & 255) * (1f - selectedAnim) + ((themed >> 16) & 255) * selectedAnim);
            int green = Math.round(((neutral >> 8) & 255) * (1f - selectedAnim) + ((themed >> 8) & 255) * selectedAnim);
            int blue = Math.round((neutral & 255) * (1f - selectedAnim) + (themed & 255) * selectedAnim);

            Fonts.MEDIUM.draw(
                    option,
                    boxX + 7f,
                    optionY + OPTION_HEIGHT / 2f - 3f,
                    6f,
                    (textAlpha << 24) | (red << 16) | (green << 8) | blue
            );

            optionY += OPTION_HEIGHT;
        }

        Scissor.disable();
    }

    private void renderSelectedText(float boxX, float boxY, float boxWidth, float boxHeight, float deltaTime) {
        float textY = boxY + boxHeight / 2f - 3f;
        float availableWidth = boxWidth - 8f;
        float baseX = boxX + 4f;

        Scissor.enable(boxX + 1f, boxY, availableWidth + 2f, boxHeight, 2);

        if (noneAlphaAnimation > 0.01f) {
            int alpha = (int)(200f * noneAlphaAnimation * alphaMultiplier);
            Fonts.MEDIUM.draw("None", baseX, textY, 6f,
                    ThemeColors.color(160, 160, 165, alpha).getRGB());
        }

        float totalWidth = 0f;
        for (String item : multiSelectSetting.getList()) {
            float itemAlpha = itemAlphaAnimations.getOrDefault(item, 0f);
            if (itemAlpha <= 0.01f) continue;
            totalWidth += Fonts.MEDIUM.getWidth(item + ", ", 6f) * itemAlpha;
        }

        if (totalWidth <= availableWidth) {
            scrollOffset = 0f;
            scrollOffsetAnimated = lerp(scrollOffsetAnimated, 0f, deltaTime * POSITION_ANIMATION_SPEED);
        } else {
            updateScrollAnimation(deltaTime, totalWidth, availableWidth);
            scrollOffsetAnimated = lerp(scrollOffsetAnimated, scrollOffset, deltaTime * POSITION_ANIMATION_SPEED);
        }

        float maxScroll = Math.max(0f, totalWidth - availableWidth + 5f);
        float currentScroll = scrollOffsetAnimated * maxScroll;

        for (String item : multiSelectSetting.getList()) {
            float itemAlpha = itemAlphaAnimations.getOrDefault(item, 0f);
            if (itemAlpha <= 0.01f) continue;

            String displayText = item;
            boolean hasNext = false;
            for (String next : multiSelectSetting.getList()) {
                if (next.equals(item)) {
                    hasNext = false;
                    continue;
                }
                if (itemAlphaAnimations.getOrDefault(next, 0f) > 0.01f) {
                    hasNext = true;
                    break;
                }
            }
            if (hasNext) displayText += ", ";

            float itemX = itemXPositions.getOrDefault(item, 0f);
            float renderX = baseX + itemX - currentScroll;
            int alpha = (int)(205f * itemAlpha * alphaMultiplier);
            if (alpha > 0) {
                Fonts.MEDIUM.draw(displayText, renderX, textY, 6f,
                        ThemeColors.color(195, 195, 200, alpha).getRGB());
            }
        }

        Scissor.disable();
    }

    private void renderScrollingDescription(String description, float deltaTime) {
        float descY = y + height / 2 + 0.5f;
        float boxX = x + width - BOX_WIDTH - 1f;
        float availableWidth = boxX - x - DESC_PADDING;
        float descWidth = Fonts.MEDIUM.getWidth(description, 5);

        if (descWidth <= availableWidth) {
            descScrollOffset = 0;
            Fonts.MEDIUM.draw(description, x + 0.5f, descY, 5,
                    applyAlpha(ThemeColors.color(128, 128, 128, 128)).getRGB());
            return;
        }

        updateDescScrollAnimation(deltaTime, descWidth, availableWidth);
        float maxScroll = descWidth - availableWidth + 5;
        float currentScroll = descScrollOffset * maxScroll;

        Scissor.enable(x, descY - 2, availableWidth, 10, 2);
        Fonts.MEDIUM.draw(description, x + 0.5f - currentScroll, descY, 5,
                applyAlpha(ThemeColors.color(128, 128, 128, 128)).getRGB());
        Scissor.disable();
    }

    private void updateDescScrollAnimation(float deltaTime, float textWidth, float availableWidth) {
        long currentTime = System.currentTimeMillis();
        if (descScrollPauseTime > 0) {
            if (currentTime - descScrollPauseTime < SCROLL_PAUSE_DURATION) return;
            descScrollPauseTime = 0;
        }

        float scrollDistance = textWidth - availableWidth + 5;
        if (scrollDistance <= 0) {
            descScrollOffset = 0;
            return;
        }

        float scrollSpeed = SCROLL_PIXELS_PER_SECOND / scrollDistance;
        if (descScrollingRight) {
            descScrollOffset += deltaTime * scrollSpeed;
            if (descScrollOffset >= 1f) {
                descScrollOffset = 1f;
                descScrollingRight = false;
                descScrollPauseTime = currentTime;
            }
        } else {
            descScrollOffset -= deltaTime * scrollSpeed;
            if (descScrollOffset <= 0f) {
                descScrollOffset = 0f;
                descScrollingRight = true;
                descScrollPauseTime = currentTime;
            }
        }
    }

    private void updateScrollAnimation(float deltaTime, float textWidth, float availableWidth) {
        long currentTime = System.currentTimeMillis();
        if (scrollPauseTime > 0) {
            if (currentTime - scrollPauseTime < SCROLL_PAUSE_DURATION) return;
            scrollPauseTime = 0;
        }

        float scrollDistance = textWidth - availableWidth + 5;
        if (scrollDistance <= 0) {
            scrollOffset = 0;
            return;
        }

        float scrollSpeed = SCROLL_PIXELS_PER_SECOND / scrollDistance;
        if (scrollingRight) {
            scrollOffset += deltaTime * scrollSpeed;
            if (scrollOffset >= 1f) {
                scrollOffset = 1f;
                scrollingRight = false;
                scrollPauseTime = currentTime;
            }
        } else {
            scrollOffset -= deltaTime * scrollSpeed;
            if (scrollOffset <= 0f) {
                scrollOffset = 0f;
                scrollingRight = true;
                scrollPauseTime = currentTime;
            }
        }
    }

    private float smoothStep(float value) {
        value = clamp01(value);
        return value * value * (3f - 2f * value);
    }

    private float easeOutCubic(float value) {
        value = clamp01(value);
        float inv = 1f - value;
        return 1f - inv * inv * inv;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private boolean isMainHover(double mouseX, double mouseY) {
        float boxX = x + width - BOX_WIDTH - 1f;
        float boxY = y + height / 2f - BOX_HEIGHT / 2f;
        return mouseX >= boxX && mouseX <= boxX + BOX_WIDTH
                && mouseY >= boxY && mouseY <= boxY + BOX_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float boxX = x + width - BOX_WIDTH - 1f;
        float boxY = y + height / 2f - BOX_HEIGHT / 2f;

        if (isMainHover(mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }

        if (expanded && expandAnimation > 0.8f) {
            float startY = boxY + BOX_HEIGHT + 3f;
            float optionY = startY;
            for (String option : multiSelectSetting.getList()) {
                if (mouseX >= boxX && mouseX <= boxX + BOX_WIDTH
                        && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT) {
                    if (multiSelectSetting.isSelected(option)) {
                        multiSelectSetting.getSelected().remove(option);
                    } else {
                        multiSelectSetting.getSelected().add(option);
                    }
                    return true;
                }
                optionY += OPTION_HEIGHT;
            }
        }
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isExpanded() {
        return expanded || expandAnimation > 0.01f;
    }

    public float getTotalHeight() {
        return Math.max(16f, height);
    }
}
