/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.DeathScreenEvent;
import mind.beta.events.impl.PacketEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;

@SuppressWarnings("all")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AutoRespawn extends ModuleStructure {

    SelectSetting modeSetting = new SelectSetting("Mode").value("Default");

    public AutoRespawn() {
        super("AutoRespawn", "Auto Respawn", ModuleCategory.PLAYER);
        settings(modeSetting);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
    }

    @EventHandler
    public void onDeathScreen(DeathScreenEvent e) {
        if (modeSetting.isSelected("Default")) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
    }
}