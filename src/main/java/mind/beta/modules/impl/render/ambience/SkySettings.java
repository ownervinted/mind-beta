/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render.ambience;

import mind.beta.modules.module.setting.Setting;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;

public final class SkySettings {

    public final SelectSetting preset = new SelectSetting("Sky")
            .value("Vanilla", "Fantasy", "Nebula", "Aurora", "Deep Space")
            .selected("Fantasy");

    public final SliderSettings brightness = new SliderSettings("Sky Brightness", "Overall brightness of the custom sky")
            .range(20, 180)
            .setValue(100)
            .visible(this::isCustom);

    public final SliderSettings animationSpeed = new SliderSettings("Sky Speed", "Animation speed of clouds and atmospheric effects")
            .range(0, 200)
            .setValue(100)
            .visible(this::isCustom);

    public final SliderSettings starIntensity = new SliderSettings("Stars", "Number and brightness of nighttime stars")
            .range(0, 200)
            .setValue(90)
            .visible(this::isCustom);

    public final SliderSettings effectIntensity = new SliderSettings("Atmosphere", "Strength of nebulae, auroras, and colored atmospheric effects")
            .range(0, 200)
            .setValue(100)
            .visible(this::isCustom);

    public final BooleanSetting clouds = new BooleanSetting("Clouds", "Volumetric procedural clouds")
            .setValue(true)
            .visible(this::isCustom);

    public final SliderSettings cloudAmount = new SliderSettings("Cloud Density", "Amount and density of volumetric clouds")
            .range(0, 200)
            .setValue(100)
            .visible(() -> isCustom() && clouds.isValue());

    public final SelectSetting cloudQuality = new SelectSetting("Cloud Quality")
            .value("Low", "Medium", "High")
            .selected("Medium")
            .visible(() -> isCustom() && clouds.isValue());

    public final BooleanSetting shootingStars = new BooleanSetting("Shooting Stars", "Occasional brief shooting stars")
            .setValue(true)
            .visible(this::isCustom);

    public final SliderSettings shootingStarRate = new SliderSettings("Star Frequency", "Shooting star frequency")
            .range(0, 100)
            .setValue(10)
            .visible(() -> isCustom() && shootingStars.isValue());

    public final SliderSettings shootingStarBrightness = new SliderSettings("Star Brightness", "Shooting star brightness")
            .range(10, 180)
            .setValue(70)
            .visible(() -> isCustom() && shootingStars.isValue());

    public Setting[] settings() {
        return new Setting[]{
                preset,
                brightness,
                animationSpeed,
                starIntensity,
                effectIntensity,
                clouds,
                cloudAmount,
                cloudQuality,
                shootingStars,
                shootingStarRate,
                shootingStarBrightness
        };
    }

    public boolean isCustom() {
        return !preset.isSelected("Vanilla");
    }

    public int getPresetId() {
        if (preset.isSelected("Fantasy")) return 1;
        if (preset.isSelected("Nebula")) return 2;
        if (preset.isSelected("Aurora")) return 3;
        if (preset.isSelected("Deep Space")) return 4;
        return 0;
    }

    public float getBrightness() {
        return brightness.getValue() / 100.0F;
    }

    public float getAnimationSpeed() {
        return animationSpeed.getValue() / 100.0F;
    }

    public float getStarIntensity() {
        return starIntensity.getValue() / 100.0F;
    }

    public float getEffectIntensity() {
        return effectIntensity.getValue() / 100.0F;
    }

    public float getCloudAmount() {
        return clouds.isValue() ? cloudAmount.getValue() / 100.0F : 0.0F;
    }

    public int getCloudQualityId() {
        if (cloudQuality.isSelected("Low")) return 0;
        if (cloudQuality.isSelected("High")) return 2;
        return 1;
    }

    public float getShootingStarRate() {
        return shootingStars.isValue() ? shootingStarRate.getValue() / 100.0F : 0.0F;
    }

    public float getShootingStarBrightness() {
        return shootingStars.isValue() ? shootingStarBrightness.getValue() / 100.0F : 0.0F;
    }
}
