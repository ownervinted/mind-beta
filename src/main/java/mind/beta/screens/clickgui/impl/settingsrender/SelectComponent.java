package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.font.Fonts;

import java.awt.*;
import java.util.List;

public class SelectComponent extends AbstractSettingComponent {
    private final SelectSetting selectSetting;
    private float hoverAnimation = 0f;

    private float descScrollOffset = 0f;
    private boolean descScrollingRight = true;
    private long descScrollPauseTime = 0;

    private String previousSelected = "";
    private float selectedTextAlpha = 1f;
    private float selectedTextSlide = 1f;
    private float newSelectedTextAlpha = 0f;
    private float newSelectedTextSlide = 0f;
    private String animatingFromText = "";
    private boolean isAnimatingSelection = false;

    private long lastUpdateTime = System.currentTimeMillis();

    private static final float ANIMATION_SPEED = 10f;
    private static final float VALUE_WIDTH = 82f;
    private static final float VALUE_HEIGHT = 16f;
    private static final long SCROLL_PAUSE_DURATION = 2000;
    private static final float SCROLL_PIXELS_PER_SECOND = 20f;
    private static final float DESC_PADDING = 8f;
    private static final float SELECTION_ANIMATION_SPEED = 14f;

    public SelectComponent(SelectSetting setting) {
        super(setting);
        this.selectSetting = setting;
        this.previousSelected = setting.getSelected();
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
        lastUpdateTime = currentTime;
        return deltaTime;
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(speed, 1f);
    }

    private void updateSelectionAnimation(float deltaTime) {
        String currentSelected = selectSetting.getSelected();

        if (!currentSelected.equals(previousSelected) && !isAnimatingSelection) {
            animatingFromText = previousSelected;
            isAnimatingSelection = true;
            selectedTextAlpha = 1f;
            selectedTextSlide = 1f;
            newSelectedTextAlpha = 0f;
            newSelectedTextSlide = 0f;
        }

        if (isAnimatingSelection) {
            selectedTextAlpha = lerp(selectedTextAlpha, 0f, deltaTime * SELECTION_ANIMATION_SPEED);
            selectedTextSlide = lerp(selectedTextSlide, 0f, deltaTime * SELECTION_ANIMATION_SPEED);

            if (selectedTextAlpha < 0.45f) {
                newSelectedTextAlpha = lerp(newSelectedTextAlpha, 1f, deltaTime * SELECTION_ANIMATION_SPEED);
                newSelectedTextSlide = lerp(newSelectedTextSlide, 1f, deltaTime * SELECTION_ANIMATION_SPEED);
            }

            if (newSelectedTextAlpha > 0.99f && newSelectedTextSlide > 0.99f) {
                isAnimatingSelection = false;
                previousSelected = currentSelected;
                selectedTextAlpha = 1f;
                selectedTextSlide = 1f;
                newSelectedTextAlpha = 1f;
                newSelectedTextSlide = 1f;
            }
        } else {
            previousSelected = currentSelected;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        updateSelectionAnimation(deltaTime);

        boolean hovered = isValueHover(mouseX, mouseY);
        hoverAnimation = lerp(hoverAnimation, hovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);

        Fonts.MEDIUM.draw(selectSetting.getName(), x + 0.5f, y + height / 2 - 3.25f, 6.5f,
                applyAlpha(ThemeColors.color(226, 237, 248, 215)).getRGB());

        float valueX = x + width - VALUE_WIDTH - 1f;
        float valueY = y + height / 2f - VALUE_HEIGHT / 2f;

        int backgroundAlpha = (int) ((8f + hoverAnimation * 12f) * alphaMultiplier);
        if (backgroundAlpha > 0) {
            Render2D.rect(valueX, valueY, VALUE_WIDTH, VALUE_HEIGHT,
                    ThemeColors.color(255, 255, 255, backgroundAlpha).getRGB(), 5f);
        }

        renderSelectedText(valueX, valueY);
    }

    public void renderDropdown(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void renderSelectedText(float valueX, float valueY) {
        float maxTextWidth = VALUE_WIDTH - 10f;
        float textY = valueY + VALUE_HEIGHT / 2f - 3.1f;

        Scissor.enable(valueX + 4f, valueY, maxTextWidth + 2f, VALUE_HEIGHT, 2);

        if (isAnimatingSelection) {
            if (selectedTextAlpha > 0.01f) {
                String oldText = truncateText(animatingFromText, maxTextWidth, 6f);
                float slide = (1f - selectedTextSlide) * -8f;
                int alpha = (int) (190f * selectedTextAlpha * alphaMultiplier);
                Fonts.MEDIUM.draw(oldText, valueX + 5f + slide, textY, 6f,
                        ThemeColors.color(145, 172, 198, alpha).getRGB());
            }

            if (newSelectedTextAlpha > 0.01f) {
                String newText = truncateText(selectSetting.getSelected(), maxTextWidth, 6f);
                float slide = (1f - newSelectedTextSlide) * 8f;
                int alpha = (int) (220f * newSelectedTextAlpha * alphaMultiplier);
                Fonts.MEDIUM.draw(newText, valueX + 5f + slide, textY, 6f,
                        ThemeColors.color(218, 233, 248, alpha).getRGB());
            }
        } else {
            String selected = truncateText(selectSetting.getSelected(), maxTextWidth, 6f);
            Fonts.MEDIUM.draw(selected, valueX + 5f, textY, 6f,
                    applyAlpha(ThemeColors.color(207, 226, 245, 225)).getRGB());
        }

        Scissor.disable();
    }

    private String truncateText(String text, float maxWidth, float textSize) {
        if (Fonts.MEDIUM.getWidth(text, textSize) <= maxWidth) return text;
        String truncated = text;
        while (Fonts.MEDIUM.getWidth(truncated + "..", textSize) > maxWidth && truncated.length() > 1) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "..";
    }

    private void renderScrollingDescription(String description, float deltaTime) {
        float descY = y + height / 2 + 0.5f;
        float valueX = x + width - VALUE_WIDTH - 1f;
        float availableWidth = valueX - x - DESC_PADDING;
        float descWidth = Fonts.MEDIUM.getWidth(description, 5);

        if (descWidth <= availableWidth) {
            descScrollOffset = 0;
            Fonts.MEDIUM.draw(description, x + 0.5f, descY, 5,
                    applyAlpha(ThemeColors.color(128, 128, 128, 128)).getRGB());
        } else {
            updateDescScrollAnimation(deltaTime, descWidth, availableWidth);
            float maxScroll = descWidth - availableWidth + 5;
            float currentScroll = descScrollOffset * maxScroll;

            Scissor.enable(x, descY - 2, availableWidth, 10, 2);
            Fonts.MEDIUM.draw(description, x + 0.5f - currentScroll, descY, 5,
                    applyAlpha(ThemeColors.color(128, 128, 128, 128)).getRGB());
            Scissor.disable();
        }
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

    private boolean isValueHover(double mouseX, double mouseY) {
        float valueX = x + width - VALUE_WIDTH - 1f;
        float valueY = y + height / 2f - VALUE_HEIGHT / 2f;
        return mouseX >= valueX && mouseX <= valueX + VALUE_WIDTH
                && mouseY >= valueY && mouseY <= valueY + VALUE_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isValueHover(mouseX, mouseY)) return false;
        if (button == 1 && selectSetting.getName().equals("Rotation") && selectSetting.isSelected("Custom")) {
            mind.beta.modules.impl.combat.Aura aura = mind.beta.modules.impl.combat.Aura.getInstance();
            mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.toggle(x + width - 1f, y, aura == null ? null : aura::reloadCustomRotationSettings);
            return true;
        }
        if (button != 0) return false;

        List<String> options = selectSetting.getList();
        if (options == null || options.isEmpty()) return true;

        String current = selectSetting.getSelected();
        int currentIndex = options.indexOf(current);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.size();
        selectSetting.setSelected(options.get(nextIndex));
        return true;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return isValueHover(mouseX, mouseY);
    }

    public boolean isExpanded() {
        return false;
    }

    public float getTotalHeight() {
        return Math.max(16f, height);
    }
}
