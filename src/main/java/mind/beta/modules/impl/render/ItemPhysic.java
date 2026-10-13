/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemPhysic extends ModuleStructure {
    public static ItemPhysic getInstance() {
        return Instance.get(ItemPhysic.class);
    }

    public SelectSetting mode = new SelectSetting("Physics").value("Normal").selected("Normal");

    public ItemPhysic() {
        super("ItemPhysic", "Item Physic", ModuleCategory.RENDER);

    }

    @EventHandler
    public void onTick(TickEvent e) {
    }
}