/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoRender extends ModuleStructure {

    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    public MultiSelectSetting modeSetting = new MultiSelectSetting("Elements", "Select elements to ignore")
            .value("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage", "Nausea", "Scoreboard", "BossBar")
            .selected("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage", "Nausea");

    public NoRender() {
        super("NoRender", "No Render", ModuleCategory.RENDER);
        settings(modeSetting);
    }
}