/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.network.Network;
import mind.beta.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AutoLeave extends ModuleStructure {
    SelectSetting leaveType = new SelectSetting("Disconnect Type")
            .value("Hub", "Main Menu").selected("Main Menu");

    MultiSelectSetting triggerSetting = new MultiSelectSetting("Triggers", "Select conditions for disconnecting")
            .value("Players", "Staff").selected("Players", "Staff");

    SliderSettings distanceSetting = new SliderSettings("Maximum Distance", "Maximum distance for triggering automatic disconnection")
            .setValue(10).range(5, 40).visible(() -> triggerSetting.isSelected("Players"));

    public AutoLeave() {
        super("AutoLeave", "Auto Leave", ModuleCategory.MISC);
        settings(leaveType, triggerSetting, distanceSetting);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (Network.isPvp()) return;

        if (triggerSetting.isSelected("Players"))
            mc.world.getPlayers().stream().filter(p -> mc.player.distanceTo(p) < distanceSetting.getValue() && mc.player != p && !FriendUtils.isFriend(p)).findFirst().ifPresent(p -> leave(p.getName().copy().append(" - Appeared nearby " + mc.player.distanceTo(p) + "m")));
    }

    public void leave(Text text) {
        switch (leaveType.getSelected()) {
            case "Hub" -> {
                mc.getNetworkHandler().sendChatCommand("hub");
            }
            case "Main Menu" ->
                    mc.getNetworkHandler().getConnection().disconnect(Text.of("[Auto Leave] \n").copy().append(text));
        }
        setState(false);
    }
}