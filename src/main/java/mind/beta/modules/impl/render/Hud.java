/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.*;
import mind.beta.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends ModuleStructure {
    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }

    public MultiSelectSetting interfaceSettings = new MultiSelectSetting("Elements", "Interface element settings")
            .value("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "test",
                    "TargetHud",


                    "Info",
                    "Notifications")

            .selected("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",


                    "Info",
                    "Notifications");

    public BooleanSetting showBps = new BooleanSetting("Show BPS", "Show blocks per second")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Info"));

    public BooleanSetting showTps = new BooleanSetting("Show TPS", "Show TPS in the watermark")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Watermark"));

    public Hud() {
        super("Hud", ModuleCategory.RENDER);
        settings(interfaceSettings, showBps, showTps);
    }
}