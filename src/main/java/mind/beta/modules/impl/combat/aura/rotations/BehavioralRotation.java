/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.rotations;

import net.minecraft.entity.Entity;
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
import mind.beta.modules.impl.combat.aura.target.Vector;
import mind.beta.util.move.MoveUtil;
import mind.beta.util.timer.StopWatch;

import java.security.SecureRandom;

public class BehavioralRotation extends RotationMode {

    private final SecureRandom random = new SecureRandom();

    private int lastTargetId = Integer.MIN_VALUE;

    private long nextProfileUpdate;
    private long nextDriftUpdate;

    private float profileSpeed = 1.0F;
    private float profileAccel = 1.0F;
    private float profileYawBias;
    private float profilePitchBias;

    private float driftYaw;
    private float driftPitch;
    private float targetDriftYaw;
    private float targetDriftPitch;

    private float yawVelocity;
    private float pitchVelocity;

    private boolean previousCanAttack;
    private boolean freshAttackWindow;
    private long postHitUntil;

    private float postHitYaw;
    private float postHitPitch;

    private float weaveYaw;
    private float weavePitch;
    private float targetWeaveYaw;
    private float targetWeavePitch;
    private long nextWeaveUpdate;

    public BehavioralRotation() {
        super("Reflex");
    }

    @Override
    public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d vec3d, Entity entity) {
        AttackManager attackHandler = Initialization.getInstance()
                .getManager()
                .getAttackPerpetrator()
                .getAttackHandler();

        Aura aura = Aura.getInstance();
        StopWatch attackTimer = attackHandler.getAttackTimer();

        long now = System.currentTimeMillis();
        boolean canAttack = entity != null && attackHandler.canAttack(aura.getConfig(), 0);

        if (entity != null) {
            int targetId = entity.getId();
            if (targetId != lastTargetId) {
                lastTargetId = targetId;
                resetMotionProfile(now);
            }
        } else {
            lastTargetId = Integer.MIN_VALUE;
        }

        boolean attackJustHappened = entity != null && !attackTimer.finished(55);

        if (attackJustHappened && !freshAttackWindow) {
            beginPostHitMotion(now);
        }

        freshAttackWindow = attackJustHappened;
        previousCanAttack = canAttack;

        updateProfile(now, canAttack);
        updateDrift(now, canAttack);
        updateWeave(now, canAttack);

        if (entity != null && canAttack) {
            float vertical = entity.isOnGround() ? 0.90F : 1.30F;
            Vec3d aimPoint = Vector.hitbox(entity, 1, vertical, 1, 2);
            targetRotation = RotationUtils.calculateAngle(aimPoint);
        }

        boolean postHit = entity != null && !canAttack && now < postHitUntil;

        Rotation delta = RotationUtils.calculateDelta(currentRotation, targetRotation);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();

        float distance = (float) Math.hypot(yawDelta, pitchDelta);

        boolean lookingAtHitbox = entity != null
                && RaycastHelper.rayTrace(
                RotationManager.INSTANCE.getRotation().toVector(),
                4.0,
                entity.getBoundingBox()
        );

        float intentionalYaw = 0.0F;
        float intentionalPitch = 0.0F;

        if (!canAttack && entity != null) {
            if (postHit) {
                float life = MathHelper.clamp((postHitUntil - now) / 185.0F, 0.0F, 1.0F);
                intentionalYaw += postHitYaw * easeOutSine(life);
                intentionalPitch += postHitPitch * easeOutSine(life);
            } else {
                intentionalYaw += profileYawBias;
                intentionalPitch += profilePitchBias;

                if (lookingAtHitbox) {
                    intentionalYaw *= 0.45F;
                    intentionalPitch *= 0.45F;
                }
            }

            intentionalYaw += driftYaw + weaveYaw;
            intentionalPitch += driftPitch + weavePitch;
        }

        float desiredYaw = yawDelta + intentionalYaw;
        float desiredPitch = pitchDelta + intentionalPitch;

        if (!aura.isState() || entity == null) {
            return returnToPlayer(
                    currentRotation,
                    desiredYaw,
                    desiredPitch,
                    distance,
                    attackTimer
            );
        }

        float phaseSpeed;
        float maxYawSpeed;
        float maxPitchSpeed;
        float maxYawAccel;
        float maxPitchAccel;

        if (canAttack) {
            float snap = MathHelper.clamp(distance / 36.0F, 0.0F, 1.0F);
            float eased = easeOutCubic(snap);

            phaseSpeed = MathHelper.lerp(eased, 0.72F, 0.98F);

            maxYawSpeed = MathHelper.lerp(eased, 24.0F, 64.0F);
            maxPitchSpeed = MathHelper.lerp(eased, 14.0F, 38.0F);

            maxYawAccel = 18.0F;
            maxPitchAccel = 12.0F;
        } else if (postHit) {
            phaseSpeed = randomStable(0.13F, 0.24F);
            maxYawSpeed = randomStable(5.0F, 11.0F);
            maxPitchSpeed = randomStable(3.0F, 7.0F);

            maxYawAccel = 3.5F * profileAccel;
            maxPitchAccel = 2.4F * profileAccel;
        } else {
            float approach = MathHelper.clamp(distance / 85.0F, 0.0F, 1.0F);
            float eased = easeInOutSine(approach);

            phaseSpeed = MathHelper.lerp(eased, 0.14F, 0.43F) * profileSpeed;
            maxYawSpeed = MathHelper.lerp(eased, 7.0F, 30.0F) * profileSpeed;
            maxPitchSpeed = MathHelper.lerp(eased, 4.0F, 17.0F) * profileSpeed;

            maxYawAccel = 5.5F * profileAccel;
            maxPitchAccel = 3.6F * profileAccel;
        }

        if (!MoveUtil.hasPlayerMovement() && !canAttack) {
            phaseSpeed *= 0.84F;
            maxYawSpeed *= 0.88F;
            maxPitchSpeed *= 0.88F;
        }

        float desiredYawVelocity = MathHelper.clamp(
                desiredYaw * phaseSpeed,
                -maxYawSpeed,
                maxYawSpeed
        );

        float desiredPitchVelocity = MathHelper.clamp(
                desiredPitch * phaseSpeed,
                -maxPitchSpeed,
                maxPitchSpeed
        );

        yawVelocity = approachVelocity(
                yawVelocity,
                desiredYawVelocity,
                maxYawAccel
        );

        pitchVelocity = approachVelocity(
                pitchVelocity,
                desiredPitchVelocity,
                maxPitchAccel
        );

        if (Math.abs(yawDelta) < 0.35F && !postHit) {
            yawVelocity *= 0.55F;
        }

        if (Math.abs(pitchDelta) < 0.28F && !postHit) {
            pitchVelocity *= 0.50F;
        }

        float nextYaw = currentRotation.getYaw() + yawVelocity;
        float nextPitch = currentRotation.getPitch() + pitchVelocity;

        nextPitch = MathHelper.clamp(nextPitch, -89.0F, 89.0F);

        return new Rotation(nextYaw, nextPitch);
    }

    private Rotation returnToPlayer(
            Rotation currentRotation,
            float yawDelta,
            float pitchDelta,
            float distance,
            StopWatch attackTimer
    ) {
        float normalized = MathHelper.clamp(distance / 120.0F, 0.0F, 1.0F);

        float speed = !attackTimer.finished(180)
                ? 0.10F
                : MathHelper.lerp(easeInOutSine(normalized), 0.18F, 0.52F);

        float yawLimit = MathHelper.lerp(normalized, 8.0F, 34.0F);
        float pitchLimit = MathHelper.lerp(normalized, 5.0F, 20.0F);

        float targetYawVelocity = MathHelper.clamp(yawDelta * speed, -yawLimit, yawLimit);
        float targetPitchVelocity = MathHelper.clamp(pitchDelta * speed, -pitchLimit, pitchLimit);

        yawVelocity = approachVelocity(yawVelocity, targetYawVelocity, 6.0F);
        pitchVelocity = approachVelocity(pitchVelocity, targetPitchVelocity, 4.0F);

        if (Math.abs(yawDelta) < 0.30F) {
            yawVelocity *= 0.45F;
        }

        if (Math.abs(pitchDelta) < 0.25F) {
            pitchVelocity *= 0.45F;
        }

        return new Rotation(
                currentRotation.getYaw() + yawVelocity,
                MathHelper.clamp(currentRotation.getPitch() + pitchVelocity, -89.0F, 89.0F)
        );
    }

    private void updateProfile(long now, boolean canAttack) {
        if (now < nextProfileUpdate) {
            return;
        }

        nextProfileUpdate = now + randomLong(
                canAttack ? 75L : 145L,
                canAttack ? 145L : 320L
        );

        profileSpeed = randomRange(0.88F, 1.12F);
        profileAccel = randomRange(0.82F, 1.18F);

        if (canAttack) {
            profileYawBias *= 0.25F;
            profilePitchBias *= 0.25F;
        } else {
            profileYawBias = randomSigned(0.15F, 1.25F);
            profilePitchBias = randomSigned(0.08F, 0.62F);
        }
    }

    private void updateDrift(long now, boolean canAttack) {
        if (canAttack) {
            targetDriftYaw = 0.0F;
            targetDriftPitch = 0.0F;
            driftYaw = MathHelper.lerp(0.65F, driftYaw, 0.0F);
            driftPitch = MathHelper.lerp(0.65F, driftPitch, 0.0F);
            return;
        }

        if (now >= nextDriftUpdate) {
            nextDriftUpdate = now + randomLong(105L, 245L);
            targetDriftYaw = randomSigned(0.02F, 0.85F);
            targetDriftPitch = randomSigned(0.01F, 0.42F);
        }

        driftYaw = MathHelper.lerp(0.18F, driftYaw, targetDriftYaw);
        driftPitch = MathHelper.lerp(0.18F, driftPitch, targetDriftPitch);
    }

    private void beginPostHitMotion(long now) {
        postHitUntil = now + randomLong(110L, 210L);

        float side = random.nextBoolean() ? 1.0F : -1.0F;

        postHitYaw = side * randomRange(2.6F, 7.4F);
        postHitPitch = randomSigned(0.8F, 3.2F);

        targetWeaveYaw = -side * randomRange(0.7F, 2.2F);
        targetWeavePitch = randomSigned(0.18F, 0.85F);

        nextWeaveUpdate = now + randomLong(70L, 145L);

        profileSpeed = randomRange(0.86F, 1.15F);
        profileAccel = randomRange(0.80F, 1.20F);
    }

    private void updateWeave(long now, boolean canAttack) {
        if (canAttack) {
            targetWeaveYaw = 0.0F;
            targetWeavePitch = 0.0F;
            weaveYaw = MathHelper.lerp(0.72F, weaveYaw, 0.0F);
            weavePitch = MathHelper.lerp(0.72F, weavePitch, 0.0F);
            return;
        }

        if (now >= nextWeaveUpdate) {
            nextWeaveUpdate = now + randomLong(95L, 220L);
            targetWeaveYaw = randomSigned(0.45F, 1.85F);
            targetWeavePitch = randomSigned(0.12F, 0.72F);
        }

        weaveYaw = MathHelper.lerp(0.16F, weaveYaw, targetWeaveYaw);
        weavePitch = MathHelper.lerp(0.16F, weavePitch, targetWeavePitch);
    }

    private void resetMotionProfile(long now) {
        nextProfileUpdate = 0L;
        nextDriftUpdate = 0L;
        nextWeaveUpdate = 0L;

        yawVelocity *= 0.35F;
        pitchVelocity *= 0.35F;

        driftYaw = 0.0F;
        driftPitch = 0.0F;
        targetDriftYaw = 0.0F;
        targetDriftPitch = 0.0F;

        weaveYaw = 0.0F;
        weavePitch = 0.0F;
        targetWeaveYaw = 0.0F;
        targetWeavePitch = 0.0F;

        postHitUntil = 0L;
        previousCanAttack = false;
        freshAttackWindow = false;

        updateProfile(now, false);
        updateDrift(now, false);
        updateWeave(now, false);
    }

    private float approachVelocity(float current, float target, float acceleration) {
        float delta = target - current;
        return current + MathHelper.clamp(delta, -acceleration, acceleration);
    }

    private float randomStable(float min, float max) {
        float center = (min + max) * 0.5F;
        float radius = (max - min) * 0.5F;
        return center + radius * profileSpeed * 0.18F;
    }

    private float randomRange(float min, float max) {
        return MathHelper.lerp(random.nextFloat(), min, max);
    }

    private float randomSigned(float minAbs, float maxAbs) {
        float value = randomRange(minAbs, maxAbs);
        return random.nextBoolean() ? value : -value;
    }

    private long randomLong(long min, long max) {
        if (max <= min) {
            return min;
        }

        return min + (long) (random.nextDouble() * (max - min));
    }

    private float easeOutCubic(float x) {
        float inv = 1.0F - x;
        return 1.0F - inv * inv * inv;
    }

    private float easeOutSine(float x) {
        return (float) Math.sin((x * Math.PI) * 0.5D);
    }

    private float easeInOutSine(float x) {
        return (float) (-(Math.cos(Math.PI * x) - 1.0D) * 0.5D);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
