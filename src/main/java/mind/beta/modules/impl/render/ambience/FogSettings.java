/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render.ambience;

import mind.beta.modules.module.setting.Setting;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;

public final class FogSettings {

    public final BooleanSetting enabled = new BooleanSetting("Fog", "Separate atmospheric fog")
            .setValue(false);

    public final SliderSettings density = new SliderSettings("Fog Density", "How quickly fog obscures distant objects")
            .range(10, 200)
            .setValue(100)
            .visible(this::isEnabled);

    public final SliderSettings startDistance = new SliderSettings("Fog Start", "Distance at which fog begins")
            .range(0, 160)
            .setValue(18)
            .visible(this::isEnabled);

    public final SliderSettings distance = new SliderSettings("Fog Range", "Length of the smooth fog transition")
            .range(16, 320)
            .setValue(112)
            .visible(this::isEnabled);

    public final SelectSetting colorMode = new SelectSetting("Fog Color")
            .value("Auto", "Custom")
            .selected("Auto")
            .visible(this::isEnabled);

    public final SliderSettings colorStrength = new SliderSettings("Color Strength", "Blending strength of the custom fog color")
            .range(0, 100)
            .setValue(65)
            .visible(() -> isEnabled() && colorMode.isSelected("Custom"));

    public final ColorSetting color = new ColorSetting("Fog Tint", "Custom atmospheric fog color")
            .value(0xFF6D82B4)
            .visible(() -> isEnabled() && colorMode.isSelected("Custom"));

    public Setting[] settings() {
        return new Setting[]{
                enabled,
                density,
                startDistance,
                distance,
                colorMode,
                colorStrength,
                color
        };
    }

    public boolean isEnabled() {
        return enabled.isValue();
    }

    public float getDensity() {
        return density.getValue() / 100.0F;
    }

    public float getStartDistance() {
        return startDistance.getValue();
    }

    public float getDistance() {
        return distance.getValue();
    }

    public float getColorStrength() {
        return colorStrength.getValue() / 100.0F;
    }
}
