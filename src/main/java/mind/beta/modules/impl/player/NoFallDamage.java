/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.util.move.MoveUtil;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoFallDamage extends ModuleStructure {

    SelectSetting mode = new SelectSetting("Mode")
            .value("SlothAC")
            .selected("SlothAC");

    public NoFallDamage() {
        super("NoFallDamage", "No Fall Damage", ModuleCategory.PLAYER);
        settings(mode);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.fallDistance > 0 && MoveUtil.getDistanceToGround() > 4) {
            mc.player.setVelocity(0, 0, 0);
        }
    }
}