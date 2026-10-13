/* Copyright Mind */
package mind.beta.modules.impl.combat.aura.rotations;

import mind.beta.Initialization;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class CustomRotation extends RotationMode {
    private Entity lastTarget;
    private float yawVelocity;
    private float pitchVelocity;
    private int countAtLastUpdate = -1;
    private RotationBuilder.Profile lastProfile;
    private int direction = 1;
    private int snapHoldUntil = -1;
    private long frames;
    private int activePointIndex;
    private long pointChangedAt;

    public CustomRotation() {
        super("Custom");
    }

    @Override
    public boolean bypassManagerSmoothing() {
        return true;
    }

    @Override
    public Rotation limitAngleChange(Rotation current, Rotation target, Vec3d aimPoint, Entity entity) {
        if (target == null || current == null) return target;

        RotationBuilder.Profile profile = RotationBuilder.INSTANCE.active();
        if (entity == null) {
            lastTarget = null;
            countAtLastUpdate = -1;
            return smooth(current, target, profile.returning, profile, false);
        }

        if (lastTarget != entity || lastProfile != profile) {
            lastTarget = entity;
            lastProfile = profile;
            yawVelocity = 0f;
            pitchVelocity = 0f;
            frames = 0L;
            activePointIndex = 0;
            pointChangedAt = 0L;
            direction = 1;
            countAtLastUpdate = -1;
            snapHoldUntil = -1;
        }

        frames++;
        AttackManager attacks = Initialization.getInstance().getManager().getAttackPerpetrator().getAttackHandler();
        int count = attacks.getCount();
        boolean justAttacked = countAtLastUpdate >= 0 && count != countAtLastUpdate;
        if (justAttacked) direction = -direction;
        countAtLastUpdate = count;

        int ticksToAttack = attacks.getTicksUntilNextAttack();
        boolean soon = ticksToAttack <= profile.leadTicks;

        if (!profile.aim.equals("Sticky")) {
            int size = Math.max(1, profile.multiPoints.size());
            if (frames - pointChangedAt >= Math.max(1, Math.round(profile.pointSwitchTicks))) {
                activePointIndex = (activePointIndex + 1) % size;
                pointChangedAt = frames;
            }
        }

        Rotation inside = RotationUtils.calculateAngle(selectedPoint(entity, profile));
        Rotation outside = outside(entity, inside, profile.outside);

        if (profile.aim.equals("Snap")) {
            if (justAttacked) {
                snapHoldUntil = (int) frames + Math.round(profile.snapHoldTicks);
                yawVelocity = 0f;
                pitchVelocity = 0f;
                return smooth(current, outside, profile.snapReturnSpeed, profile, false);
            }
            if (snapHoldUntil >= 0 && frames <= snapHoldUntil) {
                return inside;
            }
            if (ticksToAttack <= profile.snapTriggerTicks) {
                yawVelocity = 0f;
                pitchVelocity = 0f;
                return inside;
            }
            return smooth(current, outside, profile.snapOutsideSpeed, profile, false);
        }

        if (profile.aim.equals("Out of hitbox")) {
            if (soon) {
                return smooth(current, inside, Math.max(profile.acquire, 35f), profile, true);
            }
            return smooth(current, jitter(outside, profile), profile.track, profile, false);
        }

        return smooth(current, inside, frames < 9 ? profile.acquire : profile.track, profile, true);
    }

    private Vec3d selectedPoint(Entity entity, RotationBuilder.Profile profile) {
        if (profile.aim.equals("Sticky")) {
            return stickyPoint(entity, profile);
        }
        return multiPoint(entity, profile);
    }

    private Vec3d stickyPoint(Entity entity, RotationBuilder.Profile profile) {
        Box box = entity.getBoundingBox();
        return new Vec3d(box.minX + box.getLengthX() * profile.stickyX,
                box.minY + box.getLengthY() * profile.stickyY,
                box.getCenter().z);
    }

    private Vec3d multiPoint(Entity entity, RotationBuilder.Profile profile) {
        Box box = entity.getBoundingBox();
        List<RotationBuilder.Point> points = profile.multiPoints;
        if (points == null || points.isEmpty()) {
            return new Vec3d(box.getCenter().x, box.minY + box.getLengthY() * .65f, box.getCenter().z);
        }
        RotationBuilder.Point point = points.get(Math.floorMod(activePointIndex, points.size()));
        return new Vec3d(box.minX + box.getLengthX() * point.x,
                box.minY + box.getLengthY() * point.y,
                box.getCenter().z);
    }

    private Rotation outside(Entity entity, Rotation inside, float margin) {
        Box box = entity.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = box.getCenter();
        double horizontalDistance = Math.max(.15, Math.hypot(center.x - eye.x, center.z - eye.z));
        double radius = Math.hypot(box.getLengthX(), box.getLengthZ()) * .5;
        float halfAngle = (float) Math.toDegrees(Math.atan2(radius, horizontalDistance));
        float yawOffset = MathHelper.clamp(halfAngle + margin, margin, 78f);
        return new Rotation(inside.getYaw() + direction * yawOffset, inside.getPitch());
    }

    private Rotation jitter(Rotation rotation, RotationBuilder.Profile profile) {
        if (profile.jitter <= 0f) return rotation;
        return new Rotation(rotation.getYaw() + (float) Math.sin(frames * 1.75) * profile.jitter,
                MathHelper.clamp(rotation.getPitch() + (float) Math.cos(frames * 1.23) * profile.jitter * .35f, -90f, 90f));
    }

    private Rotation smooth(Rotation current, Rotation desired, float requestedSpeed,
                            RotationBuilder.Profile profile, boolean approaching) {
        float deltaYaw = MathHelper.wrapDegrees(desired.getYaw() - current.getYaw());
        float deltaPitch = desired.getPitch() - current.getPitch();
        float maxYaw = Math.min(profile.yaw, requestedSpeed);
        float maxPitch = Math.min(profile.pitch, requestedSpeed);
        float responsiveness = approaching ? Math.max(.54f, profile.acceleration) : profile.acceleration;
        float brake = approaching ? Math.max(.48f, profile.deceleration) : profile.deceleration;

        float yawStep = desiredStep(deltaYaw, maxYaw, profile);
        float pitchStep = desiredStep(deltaPitch, maxPitch, profile);
        yawVelocity += (yawStep - yawVelocity) * (Math.abs(yawStep) > Math.abs(yawVelocity) ? responsiveness : brake);
        pitchVelocity += (pitchStep - pitchVelocity) * (Math.abs(pitchStep) > Math.abs(pitchVelocity) ? responsiveness : brake);

        float smoothing = approaching ? Math.max(.86f, profile.smoothing) : profile.smoothing;
        float yawMove = MathHelper.clamp(yawVelocity * smoothing, -maxYaw, maxYaw);
        float pitchMove = MathHelper.clamp(pitchVelocity * smoothing, -maxPitch, maxPitch);
        if (Math.abs(deltaYaw) < .03f) yawVelocity *= .35f;
        if (Math.abs(deltaPitch) < .03f) pitchVelocity *= .35f;
        if (Math.signum(yawMove) != Math.signum(deltaYaw)) yawMove = 0f;
        if (Math.signum(pitchMove) != Math.signum(deltaPitch)) pitchMove = 0f;
        yawMove = Math.copySign(Math.min(Math.abs(yawMove), Math.abs(deltaYaw)), deltaYaw);
        pitchMove = Math.copySign(Math.min(Math.abs(pitchMove), Math.abs(deltaPitch)), deltaPitch);
        return new Rotation(current.getYaw() + yawMove,
                MathHelper.clamp(current.getPitch() + pitchMove, -90f, 90f));
    }

    private float desiredStep(float delta, float maximum, RotationBuilder.Profile profile) {
        float t = MathHelper.clamp(Math.abs(delta) / 42f, 0f, 1f);
        float curve = .14f + .86f * (profile.aim.equals("Out of hitbox") ? profile.curve(t) : t);
        return MathHelper.clamp(delta * curve, -maximum, maximum);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
