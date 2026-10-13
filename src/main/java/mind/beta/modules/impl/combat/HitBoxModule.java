/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.BoundingBoxControlEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HitBoxModule extends ModuleStructure {
    SliderSettings xzExpandSetting = new SliderSettings("XZ Expansion", "Expand the hitbox along the X and Z axes")
            .setValue(0.2F).range(0.0F, 3.0F);

    SliderSettings yExpandSetting = new SliderSettings("Y Expansion", "Expand the hitbox along the Y axis")
            .setValue(0.0F)
            .range(0.0F, 3.0F);

    public HitBoxModule() {
        super("HitBox", "Hit Box", ModuleCategory.COMBAT);
        settings(xzExpandSetting, yExpandSetting);
    }

    @EventHandler
    public void onBoundingBoxControl(BoundingBoxControlEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            Box box = event.getBox();

            float xzExpand = xzExpandSetting.getValue();
            float yExpand = yExpandSetting.getValue();
            Box changedBox = new Box(box.minX - xzExpand / 2.0f, box.minY - yExpand / 2.0f,
                    box.minZ - xzExpand / 2.0f, box.maxX + xzExpand / 2.0f,
                    box.maxY + yExpand / 2.0f, box.maxZ + xzExpand / 2.0f);

            if (living != mc.player && !FriendUtils.isFriend(living)) {
                event.setBox(changedBox);
            }
        }
    }
}
