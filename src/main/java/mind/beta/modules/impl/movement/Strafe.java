/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import net.minecraft.client.MinecraftClient;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.move.MoveUtil;

public class Strafe extends ModuleStructure {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public SelectSetting mode = new SelectSetting("Mode")
            .value("Matrix", "Grim")
            .selected("Matrix");
    SliderSettings speed = new SliderSettings("Speed", "Select the strafe speed")
            .setValue(0.42F).range(0F, 1F).visible(() -> mode.isSelected("Matrix"));

    private float lastYaw, lastPitch;
    private final Rotation rot = new Rotation(0, 0);

    public Strafe() {
        super("Strafe", "Strafe", ModuleCategory.MOVEMENT);
        settings(mode, speed);
    }

    public static Strafe getInstance() {
        return Instance.get(Strafe.class);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        boolean moving = MoveUtil.hasPlayerMovement();

        float yaw = mc.player.getYaw();

        if (mode.isSelected("Matrix")) {
            handleMatrixMode(moving, yaw);
        } else if (mode.isSelected("Grim")) {
            handleGrimMode(moving, yaw);
        }

        lastYaw = yaw;
        lastPitch = 0;
    }

    private void handleMatrixMode(boolean moving, float yaw) {
        if (moving) {
            yaw = MoveUtil.moveYaw(mc.player.getYaw());
            double motion = speed.getValue() * 1.5f;
            MoveUtil.setVelocity(motion);
        } else {
            MoveUtil.setVelocity(0);
        }
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    private void handleGrimMode(boolean moving, float yaw) {
        if (moving) {
            RotationRequest.freeCorrection = true;
            yaw = MoveUtil.moveYaw(mc.player.getYaw());
            rot.setYaw(yaw);
            rot.setPitch(mc.player.getPitch());
            if (Aura.getInstance().target == null) {
                RotationManager.INSTANCE.rotateTo(rot, RotationRequest.DEFAULT, TaskPriority.HIGH_IMPORTANCE_1, this);
            }
        }
    }

    @Override
    public void activate() {
        super.activate();
        lastYaw = mc.player != null ? mc.player.getYaw() : 0;
        lastPitch = mc.player != null ? mc.player.getPitch() : 0;
    }
}