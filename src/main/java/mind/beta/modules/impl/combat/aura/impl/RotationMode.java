/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.impl;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.aura.Rotation;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class RotationMode implements IMinecraft {
    String name;

    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation) {
        return limitAngleChange(currentRotation, targetRotation, null, null);
    }

    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d) {
        return limitAngleChange(currentRotation, targetRotation, vec3d, null);
    }

    public abstract Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d, Entity entity);

    public abstract Vec3d randomValue();

    public boolean bypassManagerSmoothing() {
        return false;
    }
}