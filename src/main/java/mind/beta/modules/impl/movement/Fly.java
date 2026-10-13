/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Fly extends ModuleStructure {
    public static Fly getInstance() {
        return Instance.get(Fly.class);
    }

    SelectSetting mode = new SelectSetting("Mode")
            .value("Normal", "Dragon Fly")
            .selected("Normal");

    SliderSettings speedXZ = new SliderSettings("XZ Speed", "Horizontal speed")
            .setValue(1.5F).range(1.0F, 10.0F)
            .visible(() -> !mode.isSelected("PolarUp"));
    SliderSettings speedY = new SliderSettings("Y Speed", "Vertical speed")
            .setValue(1.5F).range(0.0F, 10.0F)
            .visible(() -> !mode.isSelected("PolarUp"));

    @NonFinal
    StopWatch timer = new StopWatch();

    public Fly() {
        super("Fly", ModuleCategory.MOVEMENT);
        settings(mode, speedXZ, speedY);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!state || mc.player == null || mc.world == null) return;

        if (mode.isSelected("Normal")) {
            handleNormalMode();
        } else if (mode.isSelected("Dragon Fly")) {
            handleDragonFlyMode();
        }
    }

    private void handleNormalMode() {
        double motionY = getMotionY();
        setMotion(speedXZ.getValue());
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(v.x, motionY, v.z);
    }

    private void handleDragonFlyMode() {
        if (mc.player.getAbilities().flying) {
            setMotion(speedXZ.getValue());
            double motionY = 0.0;
            if (mc.options.jumpKey.isPressed()) {
                motionY = speedY.getValue();
            }
            if (mc.options.sneakKey.isPressed()) {
                motionY = -speedY.getValue();
            }
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, motionY, v.z);
        }
    }

    private double getMotionY() {
        if (mc.options.sneakKey.isPressed()) {
            return -speedY.getValue();
        } else if (mc.options.jumpKey.isPressed()) {
            return speedY.getValue();
        }
        return 0.0;
    }

    private void setMotion(float speed) {
        float yaw = mc.player.getYaw();
        float f = mc.player.forwardSpeed;
        float s = mc.player.sidewaysSpeed;
        float speedScale = speed / 3.0F;
        double x = 0.0;
        double z = 0.0;
        if (f != 0.0F || s != 0.0F) {
            float yawRad = yaw * ((float)Math.PI / 180.0F);
            x = -MathHelper.sin(yawRad) * speedScale * f + MathHelper.cos(yawRad) * speedScale * s;
            z = MathHelper.cos(yawRad) * speedScale * f + MathHelper.sin(yawRad) * speedScale * s;
        }
        mc.player.setVelocity(x, mc.player.getVelocity().y, z);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        timer.reset();
    }
}