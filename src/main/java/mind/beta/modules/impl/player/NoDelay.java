/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;

public class NoDelay extends ModuleStructure {

    public MultiSelectSetting ignoreSetting = new MultiSelectSetting("Type", "")
            .value("Jump", "Right Click", "Block Breaking Delay").selected("Jump");

    public NoDelay() {
        super("NoDelay", "No Delay", ModuleCategory.PLAYER);
        settings(ignoreSetting);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        if (ignoreSetting.isSelected("Block Breaking Delay")) mc.interactionManager.blockBreakingCooldown = 0;
        if (ignoreSetting.isSelected("Jump")) mc.player.jumpingCooldown = 0;
        if (ignoreSetting.isSelected("Right Click")) mc.itemUseCooldown = 0;
    }
}