/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.EntityColorEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.ColorUtil;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeeInvisible extends ModuleStructure {

    SliderSettings alphaSetting = new SliderSettings("Opacity", "Player opacity").setValue(0.5f).range(0.1F, 1);

    public SeeInvisible() {
        super("SeeInvisible", "See Invisible", ModuleCategory.RENDER);
        settings(alphaSetting);
    }

    @EventHandler
    public void onEntityColor(EntityColorEvent e) {
        e.setColor(ColorUtil.multAlpha(e.getColor(), alphaSetting.getValue()));
        e.cancel();
    }

}
