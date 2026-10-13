/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.rotations;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;

import java.security.SecureRandom;

public class CrystalRotation extends RotationMode {
    private final SecureRandom random = new SecureRandom();

    private long nextProfileUpdate;
    private float targetYawFactor = 0.82F;
    private float targetPitchFactor = 0.76F;
    private float yawFactor = 0.82F;
    private float pitchFactor = 0.76F;

    public CrystalRotation() {
        super("Crystal");
    }

    @Override
    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d, Entity entity) {
        long now = System.currentTimeMillis();

        if (now >= nextProfileUpdate) {
            nextProfileUpdate = now + random.nextInt(320, 650);
            targetYawFactor = randomRange(0.72F, 0.90F);
            targetPitchFactor = randomRange(0.66F, 0.86F);
        }

        yawFactor = MathHelper.lerp(0.16F, yawFactor, targetYawFactor);
        pitchFactor = MathHelper.lerp(0.15F, pitchFactor, targetPitchFactor);

        Rotation delta = RotationUtils.calculateDelta(currentRotation, targetRotation);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();

        float yawDistance = Math.abs(yawDelta);
        float pitchDistance = Math.abs(pitchDelta);

        float yawEase = easeOutCubic(MathHelper.clamp(yawDistance / 90.0F, 0.0F, 1.0F));
        float pitchEase = easeOutCubic(MathHelper.clamp(pitchDistance / 60.0F, 0.0F, 1.0F));

        float maxYaw = 62.0F * (0.38F + yawEase * 0.62F);
        float maxPitch = 42.0F * (0.42F + pitchEase * 0.58F);

        float moveYaw = MathHelper.clamp(yawDelta, -maxYaw, maxYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -maxPitch, maxPitch);

        float yawLerp = MathHelper.clamp(yawFactor * (0.72F + yawEase * 0.36F), 0.38F, 0.94F);
        float pitchLerp = MathHelper.clamp(pitchFactor * (0.72F + pitchEase * 0.34F), 0.34F, 0.90F);

        if (yawDistance < 5.0F) {
            yawLerp = Math.min(yawLerp, 0.34F);
        }

        if (yawDistance < 2.0F) {
            yawLerp = Math.min(yawLerp, 0.20F);
        }

        if (pitchDistance < 4.0F) {
            pitchLerp = Math.min(pitchLerp, 0.30F);
        }

        if (pitchDistance < 1.5F) {
            pitchLerp = Math.min(pitchLerp, 0.18F);
        }

        Rotation result = new Rotation(currentRotation.getYaw(), currentRotation.getPitch());
        result.setYaw(MathHelper.lerp(yawLerp, currentRotation.getYaw(), currentRotation.getYaw() + moveYaw));
        result.setPitch(MathHelper.clamp(
                MathHelper.lerp(pitchLerp, currentRotation.getPitch(), currentRotation.getPitch() + movePitch),
                -90.0F,
                90.0F
        ));

        return result;
    }

    private float easeOutCubic(float value) {
        float x = 1.0F - value;
        return 1.0F - x * x * x;
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
