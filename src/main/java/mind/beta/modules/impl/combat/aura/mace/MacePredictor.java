/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.mace;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;

public final class MacePredictor implements IMinecraft {
    private static final double PREDICTION_TICKS = 3.0D;
    private static final double PREDICTION_STEP = 0.20D;
    private static final double PREDICTION_TOLERANCE = 0.05D;
    private static final double GRAVITY_PER_TICK = 0.08D;
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double HORIZONTAL_DRAG = 0.91D;
    private final BooleanSetting maceOnFall;
    private final SliderSettings attackRange;
    private final SelectSetting predictionSetting;
    private LivingEntity cachedPredictionTarget;
    private Object cachedPredictionPlayer;
    private int cachedPredictionAge = Integer.MIN_VALUE;
    private ContactPrediction cachedPrediction;

    public MacePredictor(BooleanSetting maceOnFall, SliderSettings attackRange) {
        this.maceOnFall = maceOnFall;
        this.attackRange = attackRange;
        predictionSetting = new SelectSetting("Mace Prediction")
                .value("Disabled", "Relative")
                .selected("Disabled")
                .visible(() -> maceOnFall.isValue());
    }

    public SelectSetting getSetting() {
        return predictionSetting;
    }

    public float getSearchRangeBonus() {
        if (!isRelativePredictionActive()) {
            return 0.0F;
        }
        Vec3d velocity = mc.player.getVelocity();
        double travel = (Math.hypot(velocity.x, velocity.z) + Math.abs(velocity.y) * 0.35D) * PREDICTION_TICKS;
        return (float) Math.min(4.0D, Math.max(1.0D, travel + 0.5D));
    }

    public boolean willReach(LivingEntity target, double ticks) {
        ContactPrediction prediction = getPrediction(target);
        return prediction.reachable() && prediction.earliestContactTick() <= ticks;
    }

    public boolean isRelativePrediction() {
        return predictionSetting.isSelected("Relative");
    }

    public boolean isRelativePredictionActive() {
        return isRelativePrediction()
                && maceOnFall.isValue()
                && mc.player != null
                && !mc.player.isOnGround()
                && mc.player.getVelocity().y < 0.0D;
    }

    public ContactPrediction getPrediction(LivingEntity entity) {
        if (mc.player == null || entity == null) {
            return ContactPrediction.invalid();
        }

        int age = mc.player.age;

        if (cachedPredictionPlayer == mc.player
                && cachedPredictionTarget == entity
                && cachedPredictionAge == age
                && cachedPrediction != null) {
            return cachedPrediction;
        }

        ContactPrediction prediction = predictContact(entity);
        cachedPredictionPlayer = mc.player;
        cachedPredictionTarget = entity;
        cachedPredictionAge = age;
        cachedPrediction = prediction;
        return prediction;
    }

    private ContactPrediction predictContact(LivingEntity entity) {
        Vec3d playerEye = mc.player.getEyePos();
        Box targetBox = entity.getBoundingBox();

        Vec3d playerVelocity = mc.player.getVelocity();
        Vec3d targetVelocity = entity.getVelocity();

        double closingSpeed = calculateClosingSpeed(
                playerEye,
                targetBox.getCenter(),
                playerVelocity,
                targetVelocity
        );

        double range = getPredictionRange();
        double rangeSq = range * range;

        double currentDistanceSq = MaceTargeting.distanceSqToBox(playerEye, targetBox);
        double bestDistanceSq = currentDistanceSq;
        double earliestContactTick = currentDistanceSq <= rangeSq
                ? 0.0D
                : Double.POSITIVE_INFINITY;

        Vec3d playerOffset = Vec3d.ZERO;
        Vec3d targetOffset = Vec3d.ZERO;
        Vec3d currentPlayerVelocity = playerVelocity;
        Vec3d currentTargetVelocity = targetVelocity;

        Vec3d bestEye = playerEye;
        Box bestBox = targetBox;

        for (double tick = PREDICTION_STEP;
             tick <= PREDICTION_TICKS + 1.0E-6D;
             tick += PREDICTION_STEP) {

            MotionStep playerStep = simulateMotion(
                    playerOffset,
                    currentPlayerVelocity,
                    PREDICTION_STEP
            );
            playerOffset = playerStep.offset();
            currentPlayerVelocity = playerStep.velocity();

            MotionStep targetStep = simulateMotion(
                    targetOffset,
                    currentTargetVelocity,
                    PREDICTION_STEP
            );
            targetOffset = targetStep.offset();
            currentTargetVelocity = targetStep.velocity();

            Vec3d predictedEye = playerEye.add(playerOffset);
            Box predictedBox = offsetBox(targetBox, targetOffset);

            double distanceSq = MaceTargeting.distanceSqToBox(
                    predictedEye,
                    predictedBox
            );

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestEye = predictedEye;
                bestBox = predictedBox;
            }

            if (Double.isInfinite(earliestContactTick)
                    && distanceSq <= rangeSq) {
                earliestContactTick = tick;
            }
        }

        return new ContactPrediction(
                !Double.isInfinite(earliestContactTick),
                earliestContactTick,
                bestDistanceSq,
                closingSpeed,
                bestEye,
                bestBox
        );
    }

    private MotionStep simulateMotion(
            Vec3d offset,
            Vec3d velocity,
            double deltaTicks
    ) {
        Vec3d nextOffset = offset.add(
                velocity.multiply(deltaTicks)
        );

        double horizontalDrag =
                Math.pow(HORIZONTAL_DRAG, deltaTicks);
        double verticalDrag =
                Math.pow(VERTICAL_DRAG, deltaTicks);

        double nextX = velocity.x * horizontalDrag;
        double nextZ = velocity.z * horizontalDrag;
        double nextY = (
                velocity.y - GRAVITY_PER_TICK * deltaTicks
        ) * verticalDrag;

        return new MotionStep(
                nextOffset,
                new Vec3d(nextX, nextY, nextZ)
        );
    }

    private double calculateClosingSpeed(
            Vec3d playerEye,
            Vec3d targetCenter,
            Vec3d playerVelocity,
            Vec3d targetVelocity
    ) {
        Vec3d toTarget = targetCenter.subtract(playerEye);

        if (toTarget.lengthSquared() <= 1.0E-6D) {
            return 0.0D;
        }

        Vec3d relativeVelocity =
                playerVelocity.subtract(targetVelocity);

        return relativeVelocity.dotProduct(toTarget.normalize());
    }

    private double getPredictionRange() {
        return Math.min(
                MaceTargeting.VANILLA_ENTITY_REACH,
                attackRange.getValue()
        ) + PREDICTION_TOLERANCE;
    }

    private Box offsetBox(Box box, Vec3d offset) {
        return new Box(
                box.minX + offset.x,
                box.minY + offset.y,
                box.minZ + offset.z,
                box.maxX + offset.x,
                box.maxY + offset.y,
                box.maxZ + offset.z
        );
    }

    public boolean isPredictiveCandidate(LivingEntity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) {
            return false;
        }

        if (MaceTargeting.distanceSqToBox(mc.player.getEyePos(), entity.getBoundingBox()) <= Math.pow(Math.min(MaceTargeting.VANILLA_ENTITY_REACH, attackRange.getValue()), 2)) {
            return true;
        }

        ContactPrediction prediction = getPrediction(entity);

        if (!prediction.reachable()) {
            return false;
        }

        if (prediction.closingSpeed() < -0.10D) {
            return false;
        }

        return prediction.earliestContactTick() <= PREDICTION_TICKS;
    }

    private record MotionStep(
            Vec3d offset,
            Vec3d velocity
    ) {
    }

    public record ContactPrediction(
            boolean reachable,
            double earliestContactTick,
            double bestDistanceSq,
            double closingSpeed,
            Vec3d predictedPlayerEye,
            Box predictedTargetBox
    ) {
        private static ContactPrediction invalid() {
            return new ContactPrediction(
                    false,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    0.0D,
                    Vec3d.ZERO,
                    new Box(
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D
                    )
            );
        }
    }
}
