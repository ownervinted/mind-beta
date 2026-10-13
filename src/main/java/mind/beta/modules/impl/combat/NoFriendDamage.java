/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.InteractEntityEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoFriendDamage extends ModuleStructure {

    public NoFriendDamage() {
        super("NoFriendDamage", "No Friend Damage", ModuleCategory.COMBAT);
    }

    @EventHandler
    public void onAttack(InteractEntityEvent e) {
        e.setCancelled(FriendUtils.isFriend(e.getEntity()));
    }
}

