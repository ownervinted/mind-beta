/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.rotations;

import mind.beta.Initialization;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class PolarRotation extends RotationMode {

    private static final float ACQUIRE_START = 0.85F;

    private final Random random = new Random();

    private Entity lastEntity;

    private int lastAttackCount;
    private int tick;
    private int nextOffsetChangeTick;

    private float offsetYaw;
    private float offsetPitch;

    private float targetOffsetYaw;
    private float targetOffsetPitch;

    private float yawVelocity;
    private float pitchVelocity;

    private double phaseA;
    private double phaseB;
    private double phaseC;

    private double phaseSpeedA;
    private double phaseSpeedB;
    private double phaseSpeedC;

    private int escapeTicks;
    private int escapeDuration;

    private float escapeYaw;
    private float escapePitch;

    private float acquireCurve;
    private float acquireYawSpeed;
    private float acquirePitchSpeed;

    private float lastYawRadius;
    private float lastPitchRadius;

    public PolarRotation() {
        super("Reflex Flow");
    }

    public void reset() {
        lastEntity = null;

        tick = 0;
        lastAttackCount = 0;
        nextOffsetChangeTick = 0;

        offsetYaw = 0.0F;
        offsetPitch = 0.0F;

        targetOffsetYaw = 0.0F;
        targetOffsetPitch = 0.0F;

        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;

        phaseA = 0.0;
        phaseB = 0.0;
        phaseC = 0.0;

        phaseSpeedA = 0.0;
        phaseSpeedB = 0.0;
        phaseSpeedC = 0.0;

        escapeTicks = 0;
        escapeDuration = 0;

        escapeYaw = 0.0F;
        escapePitch = 0.0F;

        acquireCurve = 1.0F;
        acquireYawSpeed = 64.0F;
        acquirePitchSpeed = 38.0F;

        lastYawRadius = 0.0F;
        lastPitchRadius = 0.0F;
    }

    @Override
    public Rotation limitAngleChange(
            Rotation current,
            Rotation target,
            Vec3d vector,
            Entity entity
    ) {
        Aura aura = Aura.getInstance();

        if (mc.player == null || entity == null || !aura.isState()) {
            reset();
            return approachDirect(current, target);
        }

        AttackManager attacks = Initialization.getInstance()
                .getManager()
                .getAttackPerpetrator()
                .getAttackHandler();

        if (lastEntity != entity) {
            reset();

            lastEntity = entity;
            lastAttackCount = attacks.getCount();

            initializeNoise();
            updateHitboxRadius(entity);
            chooseOutsidePoint(true);
        }

        tick++;

        updateHitboxRadius(entity);

        int attackCount = attacks.getCount();
        boolean attacked = attackCount != lastAttackCount;

        if (attacked) {
            lastAttackCount = attackCount;
            beginEscape();
        }

        float cooldown = MathHelper.clamp(
                mc.player.getAttackCooldownProgress(0.0F),
                0.0F,
                1.0F
        );

        if (mc.player.isGliding()) {
            return approachDirect(current, target);
        }

        updateNoise();

        if (cooldown >= ACQUIRE_START) {
            return acquire(current, target, cooldown);
        }

        if (tick >= nextOffsetChangeTick) {
            chooseOutsidePoint(false);
        }

        updateOutsideOffset();

        float yaw = offsetYaw;
        float pitch = offsetPitch;

        if (escapeTicks < escapeDuration) {
            float progress = escapeDuration <= 0
                    ? 1.0F
                    : (float) escapeTicks / (float) escapeDuration;

            escapeTicks++;

            float fade = 1.0F - smoothStep(progress);

            yaw += escapeYaw * fade;
            pitch += escapePitch * fade;
        }

        float yawNoise =
                (float) Math.sin(phaseA) * 0.48F
                        + (float) Math.sin(phaseB) * 0.32F
                        + (float) Math.cos(phaseC) * 0.20F;

        float pitchNoise =
                (float) Math.cos(phaseA * 0.73) * 0.44F
                        + (float) Math.sin(phaseB * 1.19) * 0.34F
                        + (float) Math.sin(phaseC * 0.61) * 0.22F;

        float yawNoiseStrength = lastYawRadius * 0.32F;
        float pitchNoiseStrength = lastPitchRadius * 0.28F;

        yaw += yawNoise * yawNoiseStrength;
        pitch += pitchNoise * pitchNoiseStrength;

        yaw = keepOutsideYaw(yaw);

        if (Math.abs(pitch) < lastPitchRadius * 0.55F) {
            float direction = pitch >= 0.0F ? 1.0F : -1.0F;

            if (Math.abs(pitch) < 0.01F) {
                direction = random.nextBoolean() ? 1.0F : -1.0F;
            }

            pitch = direction * lastPitchRadius * 0.55F;
        }

        float maxYaw = lastYawRadius * 2.35F;
        float maxPitch = lastPitchRadius * 1.85F;

        yaw = MathHelper.clamp(yaw, -maxYaw, maxYaw);
        pitch = MathHelper.clamp(pitch, -maxPitch, maxPitch);

        Rotation floatingTarget = new Rotation(
                target.getYaw() + yaw,
                MathHelper.clamp(
                        target.getPitch() + pitch,
                        -89.0F,
                        89.0F
                )
        );

        return approachFloating(current, floatingTarget);
    }

    private void updateHitboxRadius(Entity entity) {
        Box box = entity.getBoundingBox();

        Vec3d eye = mc.player.getEyePos();
        Vec3d center = box.getCenter();

        double distance = Math.max(
                0.5,
                Math.hypot(
                        center.x - eye.x,
                        center.z - eye.z
                )
        );

        lastYawRadius = MathHelper.clamp(
                (float) Math.toDegrees(
                        Math.atan(
                                Math.max(
                                        box.getLengthX(),
                                        box.getLengthZ()
                                ) * 0.5 / distance
                        )
                ),
                2.0F,
                10.5F
        );

        lastPitchRadius = MathHelper.clamp(
                (float) Math.toDegrees(
                        Math.atan(
                                box.getLengthY() * 0.24 / distance
                        )
                ),
                1.5F,
                6.5F
        );
    }

    private void beginEscape() {
        float yawDirection = random.nextBoolean() ? 1.0F : -1.0F;

        escapeYaw =
                yawDirection
                        * lastYawRadius
                        * randomRange(1.20F, 1.75F);

        float pitchDirection = random.nextBoolean() ? 1.0F : -1.0F;

        escapePitch =
                pitchDirection
                        * lastPitchRadius
                        * randomRange(0.45F, 1.15F);

        escapeDuration = 3 + random.nextInt(4);
        escapeTicks = 0;

        targetOffsetYaw =
                yawDirection
                        * lastYawRadius
                        * randomRange(1.20F, 1.85F);

        targetOffsetPitch =
                pitchDirection
                        * lastPitchRadius
                        * randomRange(0.55F, 1.35F);

        nextOffsetChangeTick =
                tick + 3 + random.nextInt(6);

        acquireCurve = randomRange(0.72F, 1.30F);
        acquireYawSpeed = randomRange(58.0F, 78.0F);
        acquirePitchSpeed = randomRange(32.0F, 46.0F);

        yawVelocity *= 0.28F;
        pitchVelocity *= 0.28F;

        phaseA += randomRange(-0.55F, 0.55F);
        phaseB += randomRange(-0.75F, 0.75F);
        phaseC += randomRange(-0.45F, 0.45F);
    }

    private void chooseOutsidePoint(boolean initial) {
        float yawDirection;

        if (!initial && Math.abs(targetOffsetYaw) > 0.01F && random.nextFloat() < 0.68F) {
            yawDirection = Math.signum(targetOffsetYaw);
        } else {
            yawDirection = random.nextBoolean() ? 1.0F : -1.0F;
        }

        float yawDistance = randomRange(1.18F, 2.00F);

        targetOffsetYaw =
                yawDirection
                        * lastYawRadius
                        * yawDistance;

        float pitchDirection;

        if (!initial && Math.abs(targetOffsetPitch) > 0.01F && random.nextFloat() < 0.58F) {
            pitchDirection = Math.signum(targetOffsetPitch);
        } else {
            pitchDirection = random.nextBoolean() ? 1.0F : -1.0F;
        }

        targetOffsetPitch =
                pitchDirection
                        * lastPitchRadius
                        * randomRange(0.50F, 1.45F);

        if (initial) {
            offsetYaw =
                    yawDirection
                            * lastYawRadius
                            * randomRange(1.08F, 1.35F);

            offsetPitch =
                    pitchDirection
                            * lastPitchRadius
                            * randomRange(0.45F, 0.90F);
        }

        nextOffsetChangeTick =
                tick + 4 + random.nextInt(8);

        phaseSpeedA = randomRange(0.11F, 0.23F);
        phaseSpeedB = randomRange(0.05F, 0.13F);
        phaseSpeedC = randomRange(0.025F, 0.08F);
    }

    private void updateOutsideOffset() {
        float yawLerp = randomRange(0.11F, 0.17F);
        float pitchLerp = randomRange(0.08F, 0.14F);

        offsetYaw = MathHelper.lerp(
                yawLerp,
                offsetYaw,
                targetOffsetYaw
        );

        offsetPitch = MathHelper.lerp(
                pitchLerp,
                offsetPitch,
                targetOffsetPitch
        );

        offsetYaw = keepOutsideYaw(offsetYaw);
    }

    private float keepOutsideYaw(float yaw) {
        float minOutside = lastYawRadius * 1.08F;

        if (Math.abs(yaw) >= minOutside) {
            return yaw;
        }

        float direction;

        if (Math.abs(yaw) > 0.01F) {
            direction = Math.signum(yaw);
        } else if (Math.abs(targetOffsetYaw) > 0.01F) {
            direction = Math.signum(targetOffsetYaw);
        } else {
            direction = random.nextBoolean() ? 1.0F : -1.0F;
        }

        return direction * minOutside;
    }

    private void initializeNoise() {
        phaseA = random.nextDouble() * Math.PI * 2.0;
        phaseB = random.nextDouble() * Math.PI * 2.0;
        phaseC = random.nextDouble() * Math.PI * 2.0;

        phaseSpeedA = randomRange(0.11F, 0.23F);
        phaseSpeedB = randomRange(0.05F, 0.13F);
        phaseSpeedC = randomRange(0.025F, 0.08F);
    }

    private void updateNoise() {
        double modulation =
                Math.sin(tick * 0.071) * 0.011
                        + Math.cos(tick * 0.039) * 0.006;

        phaseA += phaseSpeedA + modulation;
        phaseB += phaseSpeedB - modulation * 0.43;
        phaseC += phaseSpeedC + modulation * 0.25;
    }

    private Rotation acquire(
            Rotation current,
            Rotation target,
            float cooldown
    ) {
        float progress = MathHelper.clamp(
                (cooldown - ACQUIRE_START)
                        / (1.0F - ACQUIRE_START),
                0.0F,
                1.0F
        );

        float smooth = smoothStep(progress);

        float curved = (float) Math.pow(
                smooth,
                acquireCurve
        );

        float release = 1.0F - curved;

        float residualYaw =
                offsetYaw * release;

        float residualPitch =
                offsetPitch * release;

        float wobbleFade =
                (1.0F - curved)
                        * MathHelper.clamp(
                        1.0F - progress * 0.65F,
                        0.0F,
                        1.0F
                );

        float wobbleYaw =
                (float) Math.sin(
                        phaseA * 1.21
                                + phaseB * 0.37
                )
                        * lastYawRadius
                        * 0.16F
                        * wobbleFade;

        float wobblePitch =
                (float) Math.cos(
                        phaseB * 0.94
                                - phaseC * 0.41
                )
                        * lastPitchRadius
                        * 0.14F
                        * wobbleFade;

        Rotation desired = new Rotation(
                target.getYaw()
                        + residualYaw
                        + wobbleYaw,
                MathHelper.clamp(
                        target.getPitch()
                                + residualPitch
                                + wobblePitch,
                        -89.0F,
                        89.0F
                )
        );

        Rotation delta =
                RotationUtils.calculateDelta(
                        current,
                        desired
                );

        float yawLimit =
                MathHelper.lerp(
                        curved,
                        16.0F,
                        acquireYawSpeed
                );

        float pitchLimit =
                MathHelper.lerp(
                        curved,
                        10.0F,
                        acquirePitchSpeed
                );

        float response =
                MathHelper.lerp(
                        curved,
                        0.42F,
                        0.93F
                );

        float desiredYawVelocity =
                MathHelper.clamp(
                        delta.getYaw() * response,
                        -yawLimit,
                        yawLimit
                );

        float desiredPitchVelocity =
                MathHelper.clamp(
                        delta.getPitch() * response,
                        -pitchLimit,
                        pitchLimit
                );

        float yawAcceleration =
                MathHelper.lerp(
                        curved,
                        6.0F,
                        27.0F
                );

        float pitchAcceleration =
                MathHelper.lerp(
                        curved,
                        4.0F,
                        18.0F
                );

        if (yawVelocity * delta.getYaw() < 0.0F) {
            yawVelocity *= 0.20F;
        }

        if (pitchVelocity * delta.getPitch() < 0.0F) {
            pitchVelocity *= 0.20F;
        }

        yawVelocity += MathHelper.clamp(
                desiredYawVelocity - yawVelocity,
                -yawAcceleration,
                yawAcceleration
        );

        pitchVelocity += MathHelper.clamp(
                desiredPitchVelocity - pitchVelocity,
                -pitchAcceleration,
                pitchAcceleration
        );

        if (progress > 0.84F) {
            float settle = MathHelper.clamp(
                    (progress - 0.84F) / 0.16F,
                    0.0F,
                    1.0F
            );

            yawVelocity =
                    MathHelper.lerp(
                            settle * 0.72F,
                            yawVelocity,
                            delta.getYaw()
                    );

            pitchVelocity =
                    MathHelper.lerp(
                            settle * 0.72F,
                            pitchVelocity,
                            delta.getPitch()
                    );
        }

        float yawStep =
                clampToDelta(
                        yawVelocity,
                        delta.getYaw()
                );

        float pitchStep =
                clampToDelta(
                        pitchVelocity,
                        delta.getPitch()
                );

        if (progress >= 0.97F) {
            Rotation finalDelta =
                    RotationUtils.calculateDelta(
                            current,
                            target
                    );

            yawStep = MathHelper.clamp(
                    finalDelta.getYaw(),
                    -acquireYawSpeed,
                    acquireYawSpeed
            );

            pitchStep = MathHelper.clamp(
                    finalDelta.getPitch(),
                    -acquirePitchSpeed,
                    acquirePitchSpeed
            );
        }

        return new Rotation(
                current.getYaw() + yawStep,
                MathHelper.clamp(
                        current.getPitch() + pitchStep,
                        -89.0F,
                        89.0F
                )
        );
    }

    private Rotation approachFloating(
            Rotation current,
            Rotation target
    ) {
        Rotation delta =
                RotationUtils.calculateDelta(
                        current,
                        target
                );

        float yawLimit =
                randomRange(12.0F, 21.0F);

        float pitchLimit =
                randomRange(7.0F, 13.0F);

        float desiredYaw =
                MathHelper.clamp(
                        delta.getYaw()
                                * randomRange(0.27F, 0.40F),
                        -yawLimit,
                        yawLimit
                );

        float desiredPitch =
                MathHelper.clamp(
                        delta.getPitch()
                                * randomRange(0.23F, 0.36F),
                        -pitchLimit,
                        pitchLimit
                );

        float yawAcceleration =
                randomRange(2.7F, 4.7F);

        float pitchAcceleration =
                randomRange(1.7F, 3.4F);

        yawVelocity += MathHelper.clamp(
                desiredYaw - yawVelocity,
                -yawAcceleration,
                yawAcceleration
        );

        pitchVelocity += MathHelper.clamp(
                desiredPitch - pitchVelocity,
                -pitchAcceleration,
                pitchAcceleration
        );

        if (Math.abs(delta.getYaw()) < 1.5F) {
            yawVelocity *= 0.80F;
        }

        if (Math.abs(delta.getPitch()) < 1.2F) {
            pitchVelocity *= 0.78F;
        }

        float yawStep =
                clampToDelta(
                        yawVelocity,
                        delta.getYaw()
                );

        float pitchStep =
                clampToDelta(
                        pitchVelocity,
                        delta.getPitch()
                );

        return new Rotation(
                current.getYaw() + yawStep,
                MathHelper.clamp(
                        current.getPitch() + pitchStep,
                        -89.0F,
                        89.0F
                )
        );
    }

    private Rotation approachDirect(
            Rotation current,
            Rotation target
    ) {
        Rotation delta =
                RotationUtils.calculateDelta(
                        current,
                        target
                );

        float yawStep =
                MathHelper.clamp(
                        delta.getYaw() * 0.82F,
                        -72.0F,
                        72.0F
                );

        float pitchStep =
                MathHelper.clamp(
                        delta.getPitch() * 0.82F,
                        -42.0F,
                        42.0F
                );

        return new Rotation(
                current.getYaw() + yawStep,
                MathHelper.clamp(
                        current.getPitch() + pitchStep,
                        -89.0F,
                        89.0F
                )
        );
    }

    private float clampToDelta(
            float velocity,
            float delta
    ) {
        if (delta > 0.0F) {
            return MathHelper.clamp(
                    velocity,
                    0.0F,
                    delta
            );
        }

        if (delta < 0.0F) {
            return MathHelper.clamp(
                    velocity,
                    delta,
                    0.0F
            );
        }

        return 0.0F;
    }

    private float smoothStep(float value) {
        value = MathHelper.clamp(
                value,
                0.0F,
                1.0F
        );

        return value
                * value
                * (3.0F - 2.0F * value);
    }

    private float randomRange(
            float min,
            float max
    ) {
        return min
                + random.nextFloat()
                * (max - min);
    }

    @Override
    public boolean bypassManagerSmoothing() {
        return true;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}