/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.move.MoveUtil;
import mind.beta.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Jesus extends ModuleStructure {

    SelectSetting mode = new SelectSetting("Mode")
            .value("Matrix", "Verus", "Polar")
            .selected("Matrix");

    SliderSettings polarSpeed = new SliderSettings("Polar Speed", "Movement speed on water")
            .range(0.01f, 0.2f)
            .setValue(0.08f)
            .visible(() -> mode.isSelected("Polar"));

    StopWatch timer = new StopWatch();

    @NonFinal
    boolean isMoving;

    @NonFinal
    int tickCounter = 0;

    float melonBallSpeed = 0.44F;

    public Jesus() {
        super("Jesus", ModuleCategory.MOVEMENT);
        settings(mode, polarSpeed);
    }

    @Override
    public void deactivate() {
        tickCounter = 0;
    }

    @EventHandler
    public void tick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.isSelected("Matrix")) {
            handleMatrixMode();
        } else if (mode.isSelected("Verus")) {
            handleVerusMode();
        } else if (mode.isSelected("Polar")) {
            handleFunTimeNewMode();
        }
    }

    private void handleMatrixMode() {
        if (mc.player.isTouchingWater() || mc.player.isInLava()) {
            StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
            StatusEffectInstance slowEffect = mc.player.getStatusEffect(StatusEffects.SLOWNESS);
            ItemStack offHandItem = mc.player.getOffHandStack();

            String itemName = offHandItem.getName().getString();
            float appliedSpeed = 0F;

            if (itemName.contains("Ломтик Дыни") && speedEffect != null && speedEffect.getAmplifier() == 2) {
                appliedSpeed = 0.4283F * 1.15F;
            } else {
                if (speedEffect != null) {
                    if (speedEffect.getAmplifier() == 2) {
                        appliedSpeed = melonBallSpeed * 1.15F;
                    } else if (speedEffect.getAmplifier() == 1) {
                        appliedSpeed = melonBallSpeed;
                    }
                } else {
                    appliedSpeed = melonBallSpeed * 0.68F;
                }
            }

            if (slowEffect != null) {
                appliedSpeed *= 0.85f;
            }

            MoveUtil.setVelocity(appliedSpeed);

            isMoving = mc.options.forwardKey.isPressed()
                    || mc.options.backKey.isPressed()
                    || mc.options.leftKey.isPressed()
                    || mc.options.rightKey.isPressed();

            if (!isMoving) {
                mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
            }

            double yMotion = mc.options.jumpKey.isPressed() ? 0.019 : 0.003;
            mc.player.setVelocity(mc.player.getVelocity().x, yMotion, mc.player.getVelocity().z);
        }
    }

    private void handleVerusMode() {
        if (mc.player.isTouchingWater() || mc.player.isInLava()) {
            StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
            StatusEffectInstance slowEffect = mc.player.getStatusEffect(StatusEffects.SLOWNESS);

            float appliedSpeed = 0.47F;

            if (speedEffect != null) {
                if (speedEffect.getAmplifier() == 2) {
                    appliedSpeed = 0.47F * 1.2F;
                } else if (speedEffect.getAmplifier() == 1) {
                    appliedSpeed = 0.47F * 1.05F;
                }
            } else {
                appliedSpeed = 0.47F * 0.7F;
            }

            if (slowEffect != null) {
                appliedSpeed *= 0.8f;
            }

            MoveUtil.setVelocity(appliedSpeed);

            isMoving = mc.options.forwardKey.isPressed()
                    || mc.options.backKey.isPressed()
                    || mc.options.leftKey.isPressed()
                    || mc.options.rightKey.isPressed();

            if (!isMoving) {
                mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
            }

            double yMotion = mc.options.jumpKey.isPressed() ? 0.025 : 0.005;
            mc.player.setVelocity(mc.player.getVelocity().x, yMotion, mc.player.getVelocity().z);
        }
    }

    private void handleFunTimeNewMode() {
        if (mc.player.isInFluid() || mc.player.isTouchingWater()) {
            tickCounter++;
            if (tickCounter > 2) tickCounter = 0;

            if (MoveUtil.hasPlayerMovement()) {
                double speed = polarSpeed.getValue();

                double yaw = Math.toRadians(mc.player.getYaw());
                double motionX = -Math.sin(yaw) * speed;
                double motionZ = Math.cos(yaw) * speed;

                double motionY;
                if (tickCounter == 0) {
                    motionY = 0.05;
                } else if (tickCounter == 2) {
                    motionY = -0.05;
                } else {
                    motionY = 0;
                }

                mc.player.setVelocity(motionX, motionY, motionZ);
            } else {
                mc.player.setVelocity(0, 0, 0);
            }
        } else {
            tickCounter = 0;
        }
    }
}