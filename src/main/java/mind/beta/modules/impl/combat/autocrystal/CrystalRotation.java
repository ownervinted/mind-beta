/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.autocrystal;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;

public class CrystalRotation extends RotationMode {

    private float yawVelocity;
    private float pitchVelocity;

    public CrystalRotation() {
        super("Crystal");
    }

    @Override
    public Rotation limitAngleChange(
            Rotation currentRotation,
            Rotation targetRotation,
            Vec3d vec3d,
            Entity entity
    ) {
        Rotation delta = RotationUtils.calculateDelta(currentRotation, targetRotation);

        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();

        float yawDistance = Math.abs(yawDelta);
        float pitchDistance = Math.abs(pitchDelta);

        float targetYawVelocity = MathHelper.clamp(yawDistance * 0.62F + 3.5F, 3.0F, 54.0F);
        float targetPitchVelocity = MathHelper.clamp(pitchDistance * 0.56F + 2.4F, 2.0F, 38.0F);

        yawVelocity = MathHelper.lerp(0.42F, yawVelocity, targetYawVelocity);
        pitchVelocity = MathHelper.lerp(0.38F, pitchVelocity, targetPitchVelocity);

        if (yawDistance < 7.0F) {
            yawVelocity = Math.min(yawVelocity, Math.max(1.0F, yawDistance * 0.42F));
        }

        if (pitchDistance < 6.0F) {
            pitchVelocity = Math.min(pitchVelocity, Math.max(0.8F, pitchDistance * 0.40F));
        }

        float moveYaw = MathHelper.clamp(yawDelta, -yawVelocity, yawVelocity);
        float movePitch = MathHelper.clamp(pitchDelta, -pitchVelocity, pitchVelocity);

        return new Rotation(
                currentRotation.getYaw() + moveYaw,
                MathHelper.clamp(currentRotation.getPitch() + movePitch, -90.0F, 90.0F)
        );
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
