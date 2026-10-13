/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.util.math.MathUtils;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

@Getter
@Setter
@ToString
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Rotation {
    public static Rotation DEFAULT = new Rotation(0, 0);
    float yaw, pitch;


    public static Rotation fromTargetHead(Vec3d playerPos, Vec3d targetPos, double targetHeight) {
        double headY = targetPos.y + targetHeight * 0.9;

        double deltaX = targetPos.x - playerPos.x;
        double deltaY = headY - (playerPos.y + 1.5);
        double deltaZ = targetPos.z - playerPos.z;

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        yaw = wrapDegrees(yaw);

        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float pitch = (float) Math.toDegrees(-Math.atan2(deltaY, horizontalDistance));
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        return new Rotation(yaw, pitch);
    }

    public Rotation adjustSensitivity() {
        double gcd = MathUtils.computeGcd();

        Rotation previousRotation = RotationManager.INSTANCE.getServerRotation();

        float adjustedYaw = adjustAxis(yaw, previousRotation.yaw, gcd);
        float adjustedPitch = adjustAxis(pitch, previousRotation.pitch, gcd);

        return new Rotation(adjustedYaw, MathHelper.clamp(adjustedPitch, -90f, 90f));
    }

    public Rotation random(float f) {
        return new Rotation(yaw + MathUtils.getRandom(-f, f), pitch + MathUtils.getRandom(-f, f));
    }

    private float adjustAxis(float axisValue, float previousValue, double gcd) {
        float delta = axisValue - previousValue;
        return previousValue + Math.round(delta / gcd) * (float) gcd;
    }

    public final Vec3d toVector() {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    public Rotation addYaw(float yaw) {
        return new Rotation(this.yaw + yaw, this.pitch);
    }

    public Rotation addPitch(float pitch) {
        this.pitch = MathHelper.clamp(this.pitch + pitch, -90, 90);
        return this;
    }

    public Rotation of(Rotation rotation) {
        return new Rotation(rotation.getYaw(), rotation.getPitch());
    }

    @ToString
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class VecRotation {
        final Rotation rotation;
        final Vec3d vec;
    }
}