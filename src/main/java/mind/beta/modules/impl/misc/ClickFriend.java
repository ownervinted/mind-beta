/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.KeyEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickFriend extends ModuleStructure {

    BindSetting friendBind = new BindSetting("Add Friend", "Add or remove a friend");

    public ClickFriend() {
        super("ClickFriend", "Click Friend", ModuleCategory.MISC);
        settings(friendBind);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(friendBind.getKey()) && mc.crosshairTarget instanceof EntityHitResult result && result.getEntity() instanceof PlayerEntity player) {
            if (FriendUtils.isFriend(player)) FriendUtils.removeFriend(player);
            else FriendUtils.addFriend(player);
        }
    }
}