/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.Instance;
import mind.beta.util.timer.StopWatch;

import java.util.Random;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ElytraMotion extends ModuleStructure {

    public static Fly getInstance() {
        return Instance.get(Fly.class);
    }
    @NonFinal

    StopWatch timer = new StopWatch();
    @NonFinal
    Vec3d targetPosition = null;
    @NonFinal
    Random random = new Random();
    @NonFinal double rotationAngle = 0.0;

    public ElytraMotion() {
        super("ElytraMotion", "Elytra Motion", ModuleCategory.MOVEMENT);
        this.settings();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!state || mc.player == null || mc.world == null || !mc.player.isGliding()) return;

        Aura aura = Instance.get(Aura.class);

        if (aura.isState()) {
            handleAuraMotion(aura);
        }
    }

    private void handleAuraMotion(Aura aura) {
        if (aura.isState() && aura.target != null && mc.player.distanceTo(aura.target) < aura.getAttackrange().getValue() - 1F) {
            mc.player.setVelocity(0, 0.02, 0);
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        Aura aura = Instance.get(Aura.class);
        if (aura.isState() && aura.target != null && mc.player.distanceTo(aura.target) < aura.getAttackrange().getValue() - 1F) {
            switch (e.getPacket()) {
                default -> {
                }
            }
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }
}