package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.setting.implement.ButtonSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.*;

public class ButtonComponent extends AbstractSettingComponent {
    private final ButtonSetting buttonSetting;
    private float pressAnimation = 0f;
    private float hoverAnimation = 0f;
    private float scaleAnimation = 1f;
    private float rippleAnimation = 0f;
    private float rippleX = 0f;
    private float rippleY = 0f;
    private boolean wasPressed = false;
    private boolean rippleActive = false;

    private long lastUpdateTime = System.currentTimeMillis();

    private static final float ANIMATION_SPEED = 8f;
    private static final float FAST_ANIMATION_SPEED = 12f;
    private static final float BUTTON_WIDTH = 48f;
    private static final float BUTTON_HEIGHT = 12f;

    public ButtonComponent(ButtonSetting setting) {
        super(setting);
        this.buttonSetting = setting;
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
        lastUpdateTime = currentTime;
        return deltaTime;
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) {
            return target;
        }
        return current + diff * Math.min(speed, 1f);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();

        boolean hovered = isButtonHover(mouseX, mouseY);

        hoverAnimation = lerp(hoverAnimation, hovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);

        float scaleTarget = wasPressed ? 0.95f : (hovered ? 1.02f : 1f);
        scaleAnimation = lerp(scaleAnimation, scaleTarget, deltaTime * FAST_ANIMATION_SPEED);

        pressAnimation = lerp(pressAnimation, wasPressed ? 1f : 0f, deltaTime * FAST_ANIMATION_SPEED);

        if (rippleActive) {
            rippleAnimation += deltaTime * 3f;
            if (rippleAnimation >= 1f) {
                rippleAnimation = 0f;
                rippleActive = false;
            }
        }

        if (pressAnimation < 0.05f && wasPressed) {
            wasPressed = false;
        }

        Fonts.MEDIUM.draw(buttonSetting.getName(), x + 0.5f, y + height / 2 - 3.25f, 6.5f, applyAlpha(ThemeColors.color(226, 237, 248, 220)).getRGB());

        renderButton(mouseX, mouseY);
    }

    private void renderButton(int mouseX, int mouseY) {
        float buttonX = x + width - BUTTON_WIDTH - 2;
        float buttonY = y + height / 2 - BUTTON_HEIGHT / 2;

        float scaledWidth = BUTTON_WIDTH * scaleAnimation;
        float scaledHeight = BUTTON_HEIGHT * scaleAnimation;
        float scaledX = buttonX - (scaledWidth - BUTTON_WIDTH) / 2;
        float scaledY = buttonY - (scaledHeight - BUTTON_HEIGHT) / 2;

        float pressOffset = pressAnimation * 1f;
        scaledY += pressOffset;

        int bgAlpha = clamp((int)((30 + hoverAnimation * 20 + pressAnimation * 15) * alphaMultiplier));
        int bgGray = clamp((int)(35 + hoverAnimation * 15 + pressAnimation * 20));
        Color bgColor = ThemeColors.color(18 + (int)(10 * hoverAnimation), 12 + (int)(8 * hoverAnimation), 30 + (int)(18 * hoverAnimation), bgAlpha);

        Render2D.rect(scaledX, scaledY, scaledWidth, scaledHeight, bgColor.getRGB(), 4f);

        if (rippleActive && rippleAnimation > 0) {
            float currentRippleSize = 20 * rippleAnimation;
            float rippleAlpha = (1f - rippleAnimation) * 0.4f;

            int rippleAlphaInt = clamp((int)(255 * rippleAlpha * alphaMultiplier));

            float localRippleX = rippleX - scaledX;
            float localRippleY = rippleY - scaledY;

            Render2D.rect(
                    scaledX + localRippleX - currentRippleSize / 2,
                    scaledY + localRippleY - currentRippleSize / 2,
                    currentRippleSize, currentRippleSize,
                    ThemeColors.color(32, 144, 255, rippleAlphaInt).getRGB(),
                    currentRippleSize / 2
            );
        }

        renderButtonContent(scaledX, scaledY, scaledWidth, scaledHeight);
    }

    private void renderButtonContent(float buttonX, float buttonY, float buttonWidth, float buttonHeight) {
        String buttonText = buttonSetting.getButtonName() != null ? buttonSetting.getButtonName() : "Run";

        float textWidth = Fonts.BOLD.getWidth(buttonText, 5);
        float textX = buttonX + (buttonWidth - textWidth) / 2f;
        float textY = buttonY + buttonHeight / 2 - 3f;

        int textAlpha = clamp((int)((180 + hoverAnimation * 50 + pressAnimation * 25) * alphaMultiplier));
        int textGray = clamp((int)(180 + hoverAnimation * 40 + pressAnimation * 30));
        Color textColor = ThemeColors.color(textGray, textGray, textGray, textAlpha);

        Fonts.BOLD.draw(buttonText, textX, textY, 5, textColor.getRGB());
    }

    private void renderPlayIcon(float iconX, float iconY, float size) {
        int iconGray = clamp((int)(170 + hoverAnimation * 50 + pressAnimation * 30));
        Color iconColor = ThemeColors.color(iconGray, iconGray, iconGray);

        float triangleWidth = size * 0.8f;
        float triangleHeight = size;

        Render2D.rect(iconX, iconY, triangleWidth * 0.4f, triangleHeight, iconColor.getRGB(), 1f);

        float dotSize = size * 0.35f;
        float dotX = iconX + triangleWidth * 0.5f;
        float dotY = iconY + (triangleHeight - dotSize) / 2;
        Render2D.rect(dotX, dotY, dotSize, dotSize, iconColor.getRGB(), dotSize / 2);
    }

    private boolean isButtonHover(double mouseX, double mouseY) {
        float buttonX = x + width - BUTTON_WIDTH - 2;
        float buttonY = y + height / 2 - BUTTON_HEIGHT / 2;
        return mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH &&
                mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isButtonHover(mouseX, mouseY) && button == 0) {
            if (buttonSetting.getRunnable() != null) {
                buttonSetting.getRunnable().run();
            }
            wasPressed = true;
            pressAnimation = 1f;

            rippleActive = true;
            rippleAnimation = 0f;
            rippleX = (float) mouseX;
            rippleY = (float) mouseY;

            return true;
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
}
