/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PlayerCollisionEvent;
import mind.beta.events.impl.PushEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;

public class NoPush extends ModuleStructure {

    MultiSelectSetting ignoreSetting = new MultiSelectSetting("Ignore", "")
            .value("Water", "Blocks", "Entity Collisions", "Powder Snow", "Berries");

    public NoPush() {
        super("AntiPush", "Anti Push", ModuleCategory.PLAYER);
        settings(ignoreSetting);
    }

    @EventHandler
    public void onPush(PushEvent e) {
        switch (e.getType()) {
            case PushEvent.Type.COLLISION -> e.setCancelled(ignoreSetting.isSelected("Entity Collisions"));
            case PushEvent.Type.WATER -> e.setCancelled(ignoreSetting.isSelected("Water"));
            case PushEvent.Type.BLOCK -> e.setCancelled(ignoreSetting.isSelected("Blocks"));
        }
    }

    @EventHandler
    public void onPlayerCollision(PlayerCollisionEvent e) {
        Block block = e.getBlock();
        if (block.equals(Blocks.POWDER_SNOW)) e.setCancelled(ignoreSetting.isSelected("Powder Snow"));
        else if (block.equals(Blocks.SWEET_BERRY_BUSH)) e.setCancelled(ignoreSetting.isSelected("Berries"));
    }
}