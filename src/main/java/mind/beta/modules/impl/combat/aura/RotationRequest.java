/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RotationRequest {
    public static RotationRequest DEFAULT = new RotationRequest(new LinearRotation(), true, true);
    public static boolean moveCorrection, freeCorrection;
    boolean requestMoveCorrection;
    boolean requestFreeCorrection;
    RotationMode angleSmooth;
    RotationConfig rotationConfig;
    int resetThreshold = 1;

    public RotationRequest(boolean moveCorrection, boolean freeCorrection) {
        this(new LinearRotation(), moveCorrection, freeCorrection);
    }

    public RotationRequest(boolean moveCorrection) {
        this(new LinearRotation(), moveCorrection, true);
    }

    public RotationRequest(RotationMode angleSmooth, boolean moveCorrection, boolean freeCorrection) {
        this(angleSmooth, null, moveCorrection, freeCorrection);
    }

    public RotationRequest(RotationMode angleSmooth, RotationConfig rotationConfig, boolean moveCorrection, boolean freeCorrection) {
        this.angleSmooth = angleSmooth;
        this.rotationConfig = rotationConfig;
        this.requestMoveCorrection = moveCorrection;
        this.requestFreeCorrection = freeCorrection;
    }

    public RotationPlan createRotationPlan(Rotation rotation, Vec3d vec, Entity entity, int reset) {
        return new RotationPlan(rotation, vec, entity, angleSmooth, rotationConfig, reset, resetThreshold, requestMoveCorrection, requestFreeCorrection);
    }

    public RotationPlan createRotationPlan(Rotation rotation, Vec3d vec, Entity entity, boolean moveCorrection, boolean freeCorrection) {
        return new RotationPlan(rotation, vec, entity, angleSmooth, rotationConfig, 1, resetThreshold, moveCorrection, freeCorrection);
    }
}