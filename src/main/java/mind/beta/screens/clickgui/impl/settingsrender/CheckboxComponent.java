package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.*;

public class CheckboxComponent extends AbstractSettingComponent {
    private final BooleanSetting booleanSetting;
    private float switchAnimation;
    private float hoverAnimation;
    private long lastAnimationTime = System.currentTimeMillis();

    private static final float SWITCH_WIDTH = 20f;
    private static final float SWITCH_HEIGHT = 10f;
    private static final float KNOB_SIZE = 8f;

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.booleanSetting = setting;
        this.switchAnimation = setting.isValue() ? 1f : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastAnimationTime) / 1000f, 0.1f);
        lastAnimationTime = currentTime;

        hoverAnimation = animateTowards(hoverAnimation, hovered ? 1f : 0f, 8f, deltaTime);
        switchAnimation = animateTowards(switchAnimation, booleanSetting.isValue() ? 1f : 0f, 10f, deltaTime);

        int textValue = 190 + (int) (35f * switchAnimation);
        Fonts.MEDIUM.draw(booleanSetting.getName(), x + 0.5f, y + height / 2 - 3.25f, 6,
                ThemeColors.color(textValue, textValue, Math.min(255, textValue + 5), (int) (200 * alphaMultiplier)).getRGB());

        float switchX = x + width - SWITCH_WIDTH - 1f;
        float switchY = y + height / 2f - SWITCH_HEIGHT / 2f;

        float progress = Math.max(0f, Math.min(1f, switchAnimation));

        int offR = 7;
        int offG = 24;
        int offB = 41;
        int onR = 0;
        int onG = 128;
        int onB = 255;

        int trackR = (int) (offR + (onR - offR) * progress);
        int trackG = (int) (offG + (onG - offG) * progress);
        int trackB = (int) (offB + (onB - offB) * progress);
        int trackAlpha = (int) ((85 + 35 * hoverAnimation + 45 * progress) * alphaMultiplier);

        Render2D.rect(
                switchX,
                switchY,
                SWITCH_WIDTH,
                SWITCH_HEIGHT,
                ThemeColors.isLight()
                        ? ThemeColors.switchTrack(progress, hoverAnimation, Math.min(255, (int) (alphaMultiplier * 255f)))
                        : ThemeColors.color(trackR, trackG, trackB, Math.min(255, trackAlpha)).getRGB(),
                SWITCH_HEIGHT / 2f
        );

        float knobTravel = SWITCH_WIDTH - KNOB_SIZE - 2f;
        float knobX = switchX + 1f + knobTravel * progress;
        float knobY = switchY + (SWITCH_HEIGHT - KNOB_SIZE) / 2f;

        int knobR = (int) (111 + 121 * progress);
        int knobG = (int) (137 + 106 * progress);
        int knobB = (int) (161 + 94 * progress);
        int knobAlpha = (int) ((175 + 80 * progress) * alphaMultiplier);

        Render2D.rect(
                knobX,
                knobY,
                KNOB_SIZE,
                KNOB_SIZE,
                ThemeColors.isLight()
                        ? ThemeColors.switchKnob(progress, Math.min(255, knobAlpha))
                        : ThemeColors.color(knobR, knobG, knobB, Math.min(255, knobAlpha)).getRGB(),
                KNOB_SIZE / 2f
        );
    }

    private float animateTowards(float current, float target, float speed, float deltaTime) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) {
            return target;
        }
        return Math.max(0f, Math.min(1f, current + diff * speed * deltaTime));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHover(mouseX, mouseY) && button == 0) {
            booleanSetting.setValue(!booleanSetting.isValue());
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
