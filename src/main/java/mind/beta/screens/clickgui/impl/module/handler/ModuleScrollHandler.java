package mind.beta.screens.clickgui.impl.module.handler;

import lombok.Getter;

@Getter
public class ModuleScrollHandler {

    private double moduleTargetScroll = 0, moduleDisplayScroll = 0;
    private double settingTargetScroll = 0, settingDisplayScroll = 0;
    private float moduleScrollTopFade = 0f, moduleScrollBottomFade = 0f;
    private float settingScrollTopFade = 0f, settingScrollBottomFade = 0f;

    private float lastSettingsPanelHeight = 0f;
    private float lastModuleListHeight = 0f;
    private long lastScrollUpdateTime = System.currentTimeMillis();

    private static final float SCROLL_SPEED = 12f;
    private static final float FADE_SPEED = 8f;
    private static final float CORNER_INSET = 3f;
    private static final float MODULE_ITEM_HEIGHT = 22f;

    public void resetModuleScroll() {
        moduleTargetScroll = moduleDisplayScroll = 0;
    }

    public void resetSettingScroll() {
        settingTargetScroll = settingDisplayScroll = 0;
    }

    public void update(float delta) {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastScrollUpdateTime) / 1000f, 0.1f);
        lastScrollUpdateTime = currentTime;

        moduleDisplayScroll = smoothScroll(moduleDisplayScroll, moduleTargetScroll, deltaTime);
        settingDisplayScroll = smoothScroll(settingDisplayScroll, settingTargetScroll, deltaTime);
    }

    private double smoothScroll(double current, double target, float deltaTime) {
        double diff = target - current;

        if (Math.abs(diff) < 0.5) {
            return target;
        }

        double factor = 1.0 - Math.exp(-SCROLL_SPEED * deltaTime);
        return current + diff * factor;
    }

    public void updateFades(int moduleCount, float totalSettingHeight, float moduleListHeight, float settingsPanelHeight) {
        lastSettingsPanelHeight = settingsPanelHeight;
        lastModuleListHeight = moduleListHeight;

        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastScrollUpdateTime) / 1000f, 0.1f);

        float effectiveModuleHeight = moduleListHeight - CORNER_INSET * 2 - 2f;
        float totalModuleHeight = Math.max(0f, moduleCount * (MODULE_ITEM_HEIGHT + 2f) - 2f);
        float maxModuleScroll = Math.max(0f, totalModuleHeight - effectiveModuleHeight);
        float maxSettingScroll = Math.max(0f, totalSettingHeight - settingsPanelHeight);

        if (settingTargetScroll < -maxSettingScroll) {
            settingTargetScroll = -maxSettingScroll;
        } else if (settingTargetScroll > 0) {
            settingTargetScroll = 0;
        }

        moduleScrollTopFade = updateFade(moduleScrollTopFade, moduleDisplayScroll < -0.5f, deltaTime);
        moduleScrollBottomFade = updateFade(moduleScrollBottomFade, moduleDisplayScroll > -maxModuleScroll + 0.5f && maxModuleScroll > 0, deltaTime);
        settingScrollTopFade = updateFade(settingScrollTopFade, settingDisplayScroll < -0.5f, deltaTime);
        settingScrollBottomFade = updateFade(settingScrollBottomFade, settingDisplayScroll > -maxSettingScroll + 0.5f && maxSettingScroll > 0, deltaTime);
    }

    private float updateFade(float current, boolean condition, float deltaTime) {
        float target = condition ? 1f : 0f;
        float diff = target - current;
        if (Math.abs(diff) < 0.01f) return target;
        return current + diff * FADE_SPEED * deltaTime;
    }

    public void handleModuleScroll(double vertical, float listHeight, int moduleCount) {
        float effectiveHeight = listHeight - CORNER_INSET * 2 - 2f;
        float totalHeight = Math.max(0f, moduleCount * (MODULE_ITEM_HEIGHT + 2f) - 2f);
        float maxScroll = Math.max(0f, totalHeight - effectiveHeight);
        moduleTargetScroll = Math.max(-maxScroll, Math.min(0, moduleTargetScroll + vertical * 25));
    }

    public void handleSettingScroll(double vertical, float panelHeight, float totalSettingHeight) {
        float maxScroll = Math.max(0f, totalSettingHeight - panelHeight);
        settingTargetScroll = Math.max(-maxScroll, Math.min(0, settingTargetScroll + vertical * 25));
    }

    public void scrollToModule(int moduleIndex, int totalModules) {
        float moduleY = moduleIndex * (MODULE_ITEM_HEIGHT + 2);
        float visibleHeight = lastModuleListHeight - CORNER_INSET * 2 - 4;
        float centerOffset = (visibleHeight - MODULE_ITEM_HEIGHT) / 2f;
        float targetScroll = -(moduleY - centerOffset);

        float totalHeight = Math.max(0f, totalModules * (MODULE_ITEM_HEIGHT + 2f) - 2f);
        float maxScroll = Math.max(0f, totalHeight - visibleHeight);
        targetScroll = Math.max(-maxScroll, Math.min(0, targetScroll));

        moduleTargetScroll = targetScroll;
    }

    public void correctSettingScrollPosition(float totalSettingHeight) {
        if (lastSettingsPanelHeight <= 0) return;

        float maxScroll = Math.max(0f, totalSettingHeight - lastSettingsPanelHeight);

        if (settingTargetScroll < -maxScroll) {
            settingTargetScroll = -maxScroll;
        } else if (settingTargetScroll > 0) {
            settingTargetScroll = 0;
        }
    }
}
