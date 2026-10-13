/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.SwimmingEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WaterSpeed extends ModuleStructure {

    SelectSetting modeSetting = new SelectSetting("Mode")
            .value("Polar")
            .selected("Polar");

    BooleanSetting iceBoost = new BooleanSetting("Boost Under Ice", "Speed up when your head touches ice")
            .setValue(true);

    SliderSettings iceBoostSpeed = new SliderSettings("Speed Under Ice", "Speed multiplier under ice")
            .range(1.0f, 3.0f)
            .setValue(1.5f)
            .visible(() -> iceBoost.isValue());

    public WaterSpeed() {
        super("WaterSpeed", "Water Speed", ModuleCategory.MOVEMENT);
        settings(modeSetting, iceBoost, iceBoostSpeed);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (modeSetting.isSelected("Polar") && mc.player.isSwimming() && mc.player.isOnGround()) {
            mc.player.jump();
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        }

        if (iceBoost.isValue() && mc.player.isSwimming() && isHeadUnderIce()) {
            applyIceBoost();
        }
    }

    private void applyIceBoost() {
        float speedMultiplier = iceBoostSpeed.getValue();

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());

        double baseSpeed = 0.04 * speedMultiplier;

        double horizontalSpeed = Math.cos(pitch) * baseSpeed;
        double motionX = -Math.sin(yaw) * horizontalSpeed;
        double motionZ = Math.cos(yaw) * horizontalSpeed;

        mc.player.setVelocity(
                mc.player.getVelocity().x + motionX,
                mc.player.getVelocity().y,
                mc.player.getVelocity().z + motionZ
        );
    }

    @EventHandler
    public void onSwimming(SwimmingEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (modeSetting.isSelected("Polar")) {
            processSwimmingBoost(e);
        }
    }

    private void processSwimmingBoost(SwimmingEvent e) {
        if (mc.options.jumpKey.isPressed()) {
            float pitch = RotationManager.INSTANCE.getRotation().getPitch();
            float boost = pitch >= 0 ? MathHelper.clamp(pitch / 45, 1, 2) : 0.5F;
            e.getVector().y = 0.1 * boost;
        }

        if (iceBoost.isValue() && isHeadUnderIce()) {
            float speedMultiplier = iceBoostSpeed.getValue();
            e.getVector().x *= speedMultiplier;
            e.getVector().z *= speedMultiplier;
        }
    }

    private boolean isHeadUnderIce() {
        if (mc.player == null || mc.world == null) return false;

        BlockPos headPos = mc.player.getBlockPos().up(1);
        BlockPos aboveHeadPos = mc.player.getBlockPos().up(2);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos checkPos = headPos.add(dx, 0, dz);
                BlockPos checkPosAbove = aboveHeadPos.add(dx, 0, dz);

                if (isIceBlock(checkPos) || isIceBlock(checkPosAbove)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isIceBlock(BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.ICE ||
                block == Blocks.PACKED_ICE ||
                block == Blocks.BLUE_ICE ||
                block == Blocks.FROSTED_ICE;
    }
}