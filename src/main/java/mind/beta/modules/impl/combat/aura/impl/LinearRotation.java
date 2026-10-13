/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.impl;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;


public class LinearRotation extends RotationMode {
    public LinearRotation() {
        super("Linear");
    }
    public static final LinearRotation INSTANCE = new LinearRotation();

    @Override
    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d, Entity entity) {
        Rotation rotationDelta = RotationUtils.calculateDelta(currentRotation, targetRotation);
        float yawDelta = rotationDelta.getYaw();
        float pitchDelta = rotationDelta.getPitch();

        float rotationDifference = (float) Math.hypot(yawDelta, pitchDelta);

        float straightLineYaw = Math.abs(yawDelta / rotationDifference) * 360.0F;
        float straightLinePitch = Math.abs(pitchDelta / rotationDifference) * 360.0F;

        float newYaw = currentRotation.getYaw() + Math.min(Math.max(yawDelta, -straightLineYaw), straightLineYaw);
        float newPitch = currentRotation.getPitch() + Math.min(Math.max(pitchDelta, -straightLinePitch), straightLinePitch);

        return new Rotation(newYaw, newPitch);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
