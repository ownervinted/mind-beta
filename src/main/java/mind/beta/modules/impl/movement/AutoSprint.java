/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.Getter;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.util.Instance;

public class AutoSprint extends ModuleStructure {
    public static AutoSprint getInstance() {
        return Instance.get(AutoSprint.class);
    }

    private static volatile boolean serverSprintState = false;

    @Getter
    private final BooleanSetting noReset = new BooleanSetting("Keep Sprinting", "Don't reset sprint for crits")
            .setValue(false);

    public AutoSprint() {
        super("AutoSprint", ModuleCategory.MOVEMENT);
        settings(noReset);
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.SEND)
            return;
        if (!(event.getPacket() instanceof ClientCommandC2SPacket packet))
            return;

        if (packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
            if (serverSprintState) {
                event.cancel();
                return;
            }
            serverSprintState = true;
        } else if (packet.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
            if (!serverSprintState) {
                event.cancel();
                return;
            }
            serverSprintState = false;
        }
    }

    public static boolean isServerSprinting() {
        return serverSprintState;
    }

    public static void resetServerState() {
        serverSprintState = false;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null)
            return;

        processSprint();
    }

    private void processSprint() {
        if (AttackManager.isLegitSprintResetPending()) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            return;
        }

        boolean horizontal = mc.player.horizontalCollision && !mc.player.collidedSoftly;
        boolean sneaking = mc.player.isSneaking() && !mc.player.isSwimming();
        boolean canSprint = !horizontal && mc.player.forwardSpeed > 0;

        if (sneaking)
            return;

        if (canSprint && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public void deactivate() {
        resetServerState();
    }
}
