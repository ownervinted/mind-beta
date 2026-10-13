/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.timer.TimerUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServerRPSpoofer extends ModuleStructure {
    private ResourcePackAction currentAction = ResourcePackAction.WAIT;
    private final TimerUtil counter = TimerUtil.create();

    public ServerRPSpoofer() {
        super("ServerRPSpoof", "Server RP Spoof", ModuleCategory.MISC);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof ResourcePackSendS2CPacket) {
            currentAction = ResourcePackAction.ACCEPT;
            e.cancel();
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler != null) {
            processResourcePackAction(networkHandler);
        }
    }

    private void processResourcePackAction(ClientPlayNetworkHandler networkHandler) {
        if (currentAction == ResourcePackAction.ACCEPT) {
            networkHandler.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            currentAction = ResourcePackAction.SEND;
            counter.resetCounter();
        } else if (currentAction == ResourcePackAction.SEND && counter.isReached(300L)) {
            networkHandler.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            currentAction = ResourcePackAction.WAIT;
        }
    }

    @Override
    public void deactivate() {
        currentAction = ResourcePackAction.WAIT;
        super.deactivate();
    }

    public enum ResourcePackAction {
        ACCEPT, SEND, WAIT;
    }
}