/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.rotations;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.Initialization;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import mind.beta.modules.impl.combat.aura.target.RaycastHelper;

import java.security.SecureRandom;

public class ExtendedRotation extends RotationMode {
    private final SecureRandom random = new SecureRandom();

    private Entity lastEntity;

    private Vec3d pointFrom = new Vec3d(0.5, 0.68, 0.5);
    private Vec3d pointTo = new Vec3d(0.5, 0.68, 0.5);
    private long pointStartTime;
    private long pointEndTime;

    private long speedRefreshTime;
    private float targetTrackSpeed = 0.34F;
    private float targetYawSpeed = 0.34F;
    private float targetPitchSpeed = 0.30F;
    private float currentYawSpeed = 0.34F;
    private float currentPitchSpeed = 0.30F;

    private double yawPhase = random.nextDouble() * Math.PI * 2.0;
    private double pitchPhase = random.nextDouble() * Math.PI * 2.0;
    private double yawFrequency = randomRange(0.0024, 0.0058);
    private double pitchFrequency = randomRange(0.0017, 0.0043);
    private float yawNoiseAmplitude = randomRange(0.18F, 0.65F);
    private float pitchNoiseAmplitude = randomRange(0.08F, 0.32F);

    private int lastAttackCount = -1;
    private long snapStartTime;
    private long snapEndTime;
    private float snapYawOffset;
    private float snapPitchOffset;

    public ExtendedRotation() {
        super("Extended");
    }

    @Override
    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d, Entity entity) {
        AttackManager attackManager = Initialization.getInstance()
                .getManager()
                .getAttackPerpetrator()
                .getAttackHandler();

        Aura aura = Aura.getInstance();
        long now = System.currentTimeMillis();

        if (entity == null || !aura.isState()) {
            resetTargetState(entity);
            return rotateBack(currentRotation, targetRotation);
        }

        if (lastEntity != entity) {
            resetForEntity(entity, now, attackManager.getCount());
        }

        boolean attackReady = attackManager.canAttack(aura.getConfig(), 0);
        boolean preAimWindow = attackManager.canAttack(aura.getConfig(), 2);
        boolean attackWindow = attackReady || preAimWindow;

        updatePostAttackSnap(attackManager, now, attackWindow);
        updateAimPoint(now, attackWindow);
        updateSpeedProfile(now, attackWindow);

        Vec3d aimPoint = getAimPoint(entity, now, attackWindow);
        Rotation desiredRotation = RotationUtils.calculateAngle(aimPoint);

        boolean lookingAtHitbox = RaycastHelper.rayTrace(
                RotationManager.INSTANCE.getRotation().toVector(),
                4.0,
                entity.getBoundingBox()
        );

        if (isSnapActive(now) && !attackWindow) {
            float snapProgress = MathHelper.clamp(
                    (float) (now - snapStartTime) / (float) Math.max(1L, snapEndTime - snapStartTime),
                    0.0F,
                    1.0F
            );

            float snapCurve = (float) Math.sin(Math.PI * snapProgress);
            desiredRotation.setYaw(desiredRotation.getYaw() + snapYawOffset * snapCurve);
            desiredRotation.setPitch(desiredRotation.getPitch() + snapPitchOffset * snapCurve);
        }

        if (!attackWindow) {
            float idleFactor = lookingAtHitbox ? randomRange(0.45F, 0.72F) : 1.0F;
            desiredRotation.setYaw(desiredRotation.getYaw() + humanNoiseYaw(now) * idleFactor);
            desiredRotation.setPitch(desiredRotation.getPitch() + humanNoisePitch(now) * idleFactor);
        }

        Rotation delta = RotationUtils.calculateDelta(currentRotation, desiredRotation);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();
        float difference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        if (attackWindow) {
            currentYawSpeed = MathHelper.lerp(0.42F, currentYawSpeed, randomRange(0.86F, 0.98F));
            currentPitchSpeed = MathHelper.lerp(0.38F, currentPitchSpeed, randomRange(0.82F, 0.96F));
        } else {
            currentYawSpeed = MathHelper.lerp(0.10F, currentYawSpeed, targetYawSpeed);
            currentPitchSpeed = MathHelper.lerp(0.10F, currentPitchSpeed, targetPitchSpeed);
        }

        float distanceEase = MathHelper.clamp(difference / 75.0F, 0.12F, 1.0F);
        float yawSpeed = MathHelper.clamp(currentYawSpeed * (0.55F + distanceEase * 0.65F), 0.04F, 1.0F);
        float pitchSpeed = MathHelper.clamp(currentPitchSpeed * (0.50F + distanceEase * 0.55F), 0.035F, 1.0F);

        if (!attackWindow && !isSnapActive(now)) {
            if (lookingAtHitbox) {
                yawSpeed *= randomRange(0.34F, 0.58F);
                pitchSpeed *= randomRange(0.32F, 0.56F);
            } else {
                yawSpeed *= randomRange(0.72F, 1.02F);
                pitchSpeed *= randomRange(0.70F, 0.98F);
            }
        }

        float maxYawStep = attackWindow ? randomRange(54.0F, 78.0F) : randomRange(16.0F, 34.0F);
        float maxPitchStep = attackWindow ? randomRange(34.0F, 52.0F) : randomRange(9.0F, 22.0F);

        float easedYaw = easeOutCubic(MathHelper.clamp(Math.abs(yawDelta) / 90.0F, 0.0F, 1.0F));
        float easedPitch = easeOutCubic(MathHelper.clamp(Math.abs(pitchDelta) / 60.0F, 0.0F, 1.0F));

        float yawLimit = Math.max(0.35F, maxYawStep * (0.28F + easedYaw * 0.72F));
        float pitchLimit = Math.max(0.25F, maxPitchStep * (0.30F + easedPitch * 0.70F));

        float moveYaw = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float movePitch = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);

        Rotation result = new Rotation(currentRotation.getYaw(), currentRotation.getPitch());
        result.setYaw(MathHelper.lerp(yawSpeed, currentRotation.getYaw(), currentRotation.getYaw() + moveYaw));
        result.setPitch(MathHelper.lerp(pitchSpeed, currentRotation.getPitch(), currentRotation.getPitch() + movePitch));

        return result;
    }

    private void updatePostAttackSnap(AttackManager attackManager, long now, boolean attackWindow) {
        int attackCount = attackManager.getCount();

        if (lastAttackCount == -1) {
            lastAttackCount = attackCount;
            return;
        }

        if (attackCount != lastAttackCount) {
            lastAttackCount = attackCount;

            if (!attackWindow && random.nextFloat() < randomRange(0.10F, 0.20F)) {
                float direction = random.nextBoolean() ? 1.0F : -1.0F;
                float verticalDirection = random.nextBoolean() ? 1.0F : -1.0F;

                snapYawOffset = direction * randomRange(5.5F, 13.5F);
                snapPitchOffset = verticalDirection * randomRange(1.5F, 5.5F);

                snapStartTime = now + random.nextInt(15, 55);
                snapEndTime = snapStartTime + random.nextInt(105, 210);
            }
        }

        if (attackWindow) {
            snapStartTime = 0L;
            snapEndTime = 0L;
            snapYawOffset = 0.0F;
            snapPitchOffset = 0.0F;
        }
    }

    private boolean isSnapActive(long now) {
        return snapStartTime > 0L && now >= snapStartTime && now < snapEndTime;
    }

    private void updateAimPoint(long now, boolean attackWindow) {
        if (pointEndTime == 0L) {
            pointStartTime = now;
            pointEndTime = now + random.nextInt(180, 390);
            pointFrom = new Vec3d(0.5, 0.68, 0.5);
            pointTo = randomPoint();
            return;
        }

        if (attackWindow) {
            Vec3d current = interpolatePoint(now);
            Vec3d attackPoint = new Vec3d(
                    MathHelper.clamp(current.x, 0.43, 0.57),
                    MathHelper.clamp(current.y, 0.60, 0.74),
                    MathHelper.clamp(current.z, 0.43, 0.57)
            );

            pointFrom = current;
            pointTo = attackPoint;
            pointStartTime = now;
            pointEndTime = now + random.nextInt(45, 90);
            return;
        }

        if (now >= pointEndTime) {
            pointFrom = interpolatePoint(now);
            pointTo = randomPoint();
            pointStartTime = now;
            pointEndTime = now + random.nextInt(180, 470);
        }
    }

    private Vec3d getAimPoint(Entity entity, long now, boolean attackWindow) {
        Box box = entity.getBoundingBox();
        Vec3d normalized = interpolatePoint(now);

        if (attackWindow) {
            Vec3d safe = new Vec3d(
                    MathHelper.clamp(normalized.x, 0.40, 0.60),
                    MathHelper.clamp(normalized.y, 0.58, 0.76),
                    MathHelper.clamp(normalized.z, 0.40, 0.60)
            );

            double x = MathHelper.lerp(safe.x, box.minX, box.maxX);
            double y = MathHelper.lerp(safe.y, box.minY, box.maxY);
            double z = MathHelper.lerp(safe.z, box.minZ, box.maxZ);

            return new Vec3d(x, y, z);
        }

        Vec3d center = box.getCenter();

        double yawOffset = (normalized.x - 0.5) * randomRange(1.45, 2.10);
        double pitchOffset = (normalized.y - 0.5) * randomRange(1.10, 1.75);
        double depthOffset = (normalized.z - 0.5) * randomRange(1.45, 2.10);

        double horizontalRadius = Math.max(entity.getWidth(), 0.6) * randomRange(0.95, 1.45);
        double verticalRadius = Math.max(entity.getHeight(), 1.0) * randomRange(0.28, 0.52);

        return center.add(
                yawOffset * horizontalRadius,
                pitchOffset * verticalRadius,
                depthOffset * horizontalRadius
        );
    }

    private Vec3d interpolatePoint(long now) {
        if (pointEndTime <= pointStartTime) {
            return pointTo;
        }

        double progress = MathHelper.clamp(
                (double) (now - pointStartTime) / (double) (pointEndTime - pointStartTime),
                0.0,
                1.0
        );

        double eased = progress * progress * (3.0 - 2.0 * progress);

        return new Vec3d(
                MathHelper.lerp(eased, pointFrom.x, pointTo.x),
                MathHelper.lerp(eased, pointFrom.y, pointTo.y),
                MathHelper.lerp(eased, pointFrom.z, pointTo.z)
        );
    }

    private Vec3d randomPoint() {
        double x;
        double y;
        double z;

        if (random.nextFloat() < 0.72F) {
            x = randomRange(-0.18, 1.18);
            y = randomRange(0.22, 0.92);
            z = randomRange(-0.18, 1.18);
        } else {
            x = randomRange(0.18, 0.82);
            y = randomRange(0.42, 0.84);
            z = randomRange(0.18, 0.82);
        }

        return new Vec3d(x, y, z);
    }

    private void updateSpeedProfile(long now, boolean attackWindow) {
        if (attackWindow) {
            targetYawSpeed = randomRange(0.90F, 0.99F);
            targetPitchSpeed = randomRange(0.86F, 0.97F);
            return;
        }

        if (now < speedRefreshTime) {
            return;
        }

        speedRefreshTime = now + random.nextInt(110, 310);

        targetTrackSpeed = randomRange(0.20F, 0.52F);

        float yawBias = randomRange(0.88F, 1.14F);
        float pitchBias = randomRange(0.72F, 1.02F);

        targetYawSpeed = MathHelper.clamp(targetTrackSpeed * yawBias, 0.14F, 0.62F);
        targetPitchSpeed = MathHelper.clamp(targetTrackSpeed * pitchBias, 0.10F, 0.48F);

        if (random.nextFloat() < 0.18F) {
            targetYawSpeed *= randomRange(0.55F, 0.78F);
            targetPitchSpeed *= randomRange(0.58F, 0.82F);
        }

        yawFrequency = randomRange(0.0022, 0.0061);
        pitchFrequency = randomRange(0.0016, 0.0048);
        yawNoiseAmplitude = randomRange(0.16F, 0.72F);
        pitchNoiseAmplitude = randomRange(0.07F, 0.34F);

        yawPhase += randomRange(-0.55, 0.55);
        pitchPhase += randomRange(-0.45, 0.45);
    }

    private float humanNoiseYaw(long now) {
        double first = Math.sin(now * yawFrequency + yawPhase);
        double second = Math.sin(now * yawFrequency * 0.43 + yawPhase * 1.73);
        double third = Math.cos(now * yawFrequency * 0.19 + pitchPhase * 0.61);

        return (float) ((first * 0.55 + second * 0.30 + third * 0.15) * yawNoiseAmplitude);
    }

    private float humanNoisePitch(long now) {
        double first = Math.cos(now * pitchFrequency + pitchPhase);
        double second = Math.sin(now * pitchFrequency * 0.37 + pitchPhase * 1.41);
        double third = Math.sin(now * pitchFrequency * 0.16 + yawPhase * 0.52);

        return (float) ((first * 0.58 + second * 0.27 + third * 0.15) * pitchNoiseAmplitude);
    }

    private Rotation rotateBack(Rotation currentRotation, Rotation targetRotation) {
        Rotation delta = RotationUtils.calculateDelta(currentRotation, targetRotation);

        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();
        float difference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        float factor = MathHelper.clamp(difference / 120.0F, 0.0F, 1.0F);
        float speed = MathHelper.lerp(factor, 0.16F, 0.44F);

        float yawStep = MathHelper.clamp(yawDelta, -32.0F, 32.0F);
        float pitchStep = MathHelper.clamp(pitchDelta, -20.0F, 20.0F);

        Rotation result = new Rotation(currentRotation.getYaw(), currentRotation.getPitch());
        result.setYaw(MathHelper.lerp(speed, currentRotation.getYaw(), currentRotation.getYaw() + yawStep));
        result.setPitch(MathHelper.lerp(speed, currentRotation.getPitch(), currentRotation.getPitch() + pitchStep));

        return result;
    }

    private void resetForEntity(Entity entity, long now, int attackCount) {
        lastEntity = entity;
        lastAttackCount = attackCount;

        pointFrom = new Vec3d(0.5, 0.68, 0.5);
        pointTo = randomPoint();
        pointStartTime = now;
        pointEndTime = now + random.nextInt(180, 360);

        speedRefreshTime = 0L;

        snapStartTime = 0L;
        snapEndTime = 0L;
        snapYawOffset = 0.0F;
        snapPitchOffset = 0.0F;

        currentYawSpeed = randomRange(0.26F, 0.42F);
        currentPitchSpeed = randomRange(0.20F, 0.36F);
    }

    private void resetTargetState(Entity entity) {
        if (entity == null) {
            lastEntity = null;
        }

        snapStartTime = 0L;
        snapEndTime = 0L;
        snapYawOffset = 0.0F;
        snapPitchOffset = 0.0F;
    }

    private float easeOutCubic(float value) {
        float x = 1.0F - value;
        return 1.0F - x * x * x;
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private double randomRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
