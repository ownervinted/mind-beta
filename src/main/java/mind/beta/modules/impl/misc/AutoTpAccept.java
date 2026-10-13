/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.util.network.Network;
import mind.beta.util.repository.friend.FriendUtils;

import java.util.Arrays;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoTpAccept extends ModuleStructure {
    private final String[] teleportMessages = new String[]{
            "has requested teleport",
            "просит телепортироваться",
            "хочет телепортироваться к вам",
            "просит к вам телепортироваться"
    };
    private boolean canAccept;

    private final BooleanSetting friendSetting = new BooleanSetting("Friends Only", "Accept requests only from friends").setValue(true);

    public AutoTpAccept() {
        super("AutoTpAccept", "Auto Tp Accept", ModuleCategory.MISC);
        settings(friendSetting);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof GameMessageS2CPacket m) {
            String message = m.content().getString();
            boolean validPlayer = !friendSetting.isValue() || FriendUtils.getFriends().stream().anyMatch(s -> message.contains(s.getName()));
            if (isTeleportMessage(message)) {
                canAccept = validPlayer;
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!Network.isPvp() && canAccept) {
            mc.player.networkHandler.sendChatCommand("tpaccept");
            canAccept = false;
        }
    }

    private boolean isTeleportMessage(String message) {
        return Arrays.stream(this.teleportMessages).map(String::toLowerCase).anyMatch(message::contains);
    }
}