/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;

@Setter
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RotationPlan implements IMinecraft {
    Rotation rotation;
    Vec3d vec3d;
    Entity entity;
    RotationMode angleSmooth;
    RotationConfig rotationConfig;
    int ticksUntilReset;
    float resetThreshold;
    public boolean moveCorrection;
    @Getter(AccessLevel.PUBLIC)
    public boolean freeCorrection;
    public boolean changeLook = false;


    public Rotation nextRotation(Rotation fromRotation, boolean isResetting) {
        if (isResetting) {
            return angleSmooth.limitAngleChange(fromRotation, RotationUtils.fromVec2f(mc.player.getRotationClient()));
        }
        return angleSmooth.limitAngleChange(fromRotation, rotation, vec3d, entity);
    }
}