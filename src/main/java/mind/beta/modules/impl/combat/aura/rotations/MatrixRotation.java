/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.rotations;

import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class MatrixRotation extends RotationMode {

    public MatrixRotation() {
        super("Matrix");
    }

    @Override
    public Rotation limitAngleChange(
            Rotation currentRotation,
            Rotation targetRotation,
            Vec3d vec3d,
            Entity entity
    ) {
        if (!isFinite(targetRotation)) {
            return isFinite(currentRotation)
                    ? new Rotation(currentRotation.getYaw(), MathHelper.clamp(currentRotation.getPitch(), -90.0F, 90.0F))
                    : new Rotation(0.0F, 0.0F);
        }

        float yaw = targetRotation.getYaw();
        if (isFinite(currentRotation)) {
            yaw = currentRotation.getYaw()
                    + MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        }

        return new Rotation(
                yaw,
                MathHelper.clamp(targetRotation.getPitch(), -90.0F, 90.0F)
        );
    }

    private boolean isFinite(Rotation rotation) {
        return rotation != null
                && Float.isFinite(rotation.getYaw())
                && Float.isFinite(rotation.getPitch());
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
