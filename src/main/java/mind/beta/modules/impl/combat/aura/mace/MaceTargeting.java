/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.mace;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.scripting.extensions.LuaExtensionRegistry;

import java.util.Comparator;

public final class MaceTargeting implements IMinecraft {
    public static final double VANILLA_ENTITY_REACH = 3.0D;
    private static final double PRE_SWAP_CONTACT_TICKS = 2.50D;
    private static final double ROTATION_BOOST_TICKS = 2.00D;
    private final MaceResolver resolver;
    private final MacePredictor predictor;
    private final SliderSettings attackRange;
    private final RotationConfig improvedRotationConfig = new RotationConfig(
            210.0F,
            180.0F,
            120.0F,
            180.0F,
            140.0F,
            1.0F,
            0.78F,
            1.0F
    );


    public MaceTargeting(MaceResolver resolver, MacePredictor predictor, SliderSettings attackRange) {
        this.resolver = resolver;
        this.predictor = predictor;
        this.attackRange = attackRange;
    }

    public float getSearchRangeBonus() {
        if (mc.player == null) {
            return 0.0F;
        }
        return resolver.isCustom()
                ? LuaExtensionRegistry.getInstance().maceSearchBonus(resolver.getSetting().getSelected(), 0.0F)
                : predictor.getSearchRangeBonus();
    }

    public void resolve(TargetSelector selector, TargetSelector.EntityFilter filter) {
        if (!resolver.isTargetingPhase() && !predictor.isRelativePredictionActive()) {
            selector.validateTarget(filter::isValid);
            return;
        }

        if (resolver.isCustom()) {
            LuaExtensionRegistry registry = LuaExtensionRegistry.getInstance();
            selector.validateTargetSorted(
                    entity -> filter.isValid(entity)
                            && registry.maceCandidate(
                            resolver.getSetting().getSelected(),
                            entity,
                            isStrictReachCandidate(entity)
                    ),
                    Comparator.comparingDouble(entity ->
                            registry.maceScore(
                                    resolver.getSetting().getSelected(),
                                    entity,
                                    targetDistanceSq(entity)
                            )
                    )
            );
            return;
        }

        Comparator<LivingEntity> strictComparator =
                Comparator.comparingDouble(this::targetDistanceSq);

        if (!predictor.isRelativePredictionActive()) {
            selector.validateTargetSorted(
                    filter::isValid,
                    strictComparator
            );
            return;
        }

        selector.validateTargetPreferred(
                entity -> filter.isValid(entity)
                        && isStrictReachCandidate(entity),
                entity -> filter.isValid(entity)
                        && isStrictReachCandidate(entity),
                strictComparator,
                entity -> filter.isValid(entity)
                        && predictor.isPredictiveCandidate(entity),
                Comparator
                        .comparingDouble((LivingEntity entity) ->
                                predictor.getPrediction(entity).earliestContactTick())
                        .thenComparingDouble(entity ->
                                predictor.getPrediction(entity).bestDistanceSq())
        );
    }

    public boolean isSmashCandidate(LivingEntity entity) {
        boolean fallback = isStrictReachCandidate(entity);

        return resolver.isCustom()
                ? LuaExtensionRegistry.getInstance()
                .maceCandidate(resolver.getSetting().getSelected(), entity, fallback)
                : fallback;
    }

    public boolean canReachForcedMaceTarget(
            AttackExecutor.AttackPerpetratorConfigurable config
    ) {
        if (mc.player == null || config == null || config.getTarget() == null) {
            return false;
        }

        LivingEntity target = config.getTarget();
        Box box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        double safeRange = getCurrentAttackRange(config.getMaximumRange());

        if (safeRange <= 0.0D
                || distanceSqToBox(eye, box) > safeRange * safeRange) {
            return false;
        }

        Rotation currentRotation = RotationManager.INSTANCE.getRotation();
        return rayHitsBox(currentRotation, eye, box, safeRange);
    }

    public RotationConfig resolveRotationConfig(
            RotationConfig normal,
            LivingEntity target
    ) {
        return shouldBoostRotation(target)
                ? improvedRotationConfig
                : normal;
    }

    public boolean shouldBoostRotation(LivingEntity target) {
        if (target == null || !target.isAlive() || mc.player == null) {
            return false;
        }

        if (resolver.isCustom()) {
            return LuaExtensionRegistry.getInstance()
                    .maceBoostRotation(resolver.getSetting().getSelected(), target, false);
        }

        if (isCurrentReachValid(target)) {
            if (resolver.isVanilla() && resolver.isVanillaSmashReady()) {
                return true;
            }
            return resolver.isImproved() || predictor.isRelativePredictionActive();
        }

        if (!predictor.isRelativePredictionActive()) {
            return resolver.isImproved()
                    && resolver.isTargetingPhase()
                    && predictor.isPredictiveCandidate(target);
        }

        MacePredictor.ContactPrediction prediction = predictor.getPrediction(target);
        return prediction.reachable()
                && prediction.earliestContactTick() <= ROTATION_BOOST_TICKS;
    }

    public boolean canPreSwapForTarget(LivingEntity target) {
        if (mc.player == null || target == null || !target.isAlive()) {
            return false;
        }

        if (resolver.isCustom()) {
            return LuaExtensionRegistry.getInstance()
                    .macePreSwap(
                            resolver.getSetting().getSelected(),
                            target,
                            resolver.canPreSwapNow()
                    );
        }

        if (!resolver.canPreSwapNow()) {
            return false;
        }

        if (isCurrentReachValid(target)) {
            return true;
        }

        if (!predictor.isRelativePredictionActive()) {
            return true;
        }

        MacePredictor.ContactPrediction prediction = predictor.getPrediction(target);
        return prediction.reachable()
                && prediction.earliestContactTick() <= PRE_SWAP_CONTACT_TICKS
                && prediction.closingSpeed() > -0.05D;
    }

    public Vec3d resolveAimPoint(LivingEntity target, Vec3d fallback) {
        if (target == null || fallback == null) {
            return fallback;
        }

        if (resolver.isCustom()) {
            return LuaExtensionRegistry.getInstance()
                    .maceResolvePoint(resolver.getSetting().getSelected(), target, fallback);
        }

        if (resolver.isVanilla()
                && resolver.isVanillaSmashReady()
                && isCurrentReachValid(target)) {
            return stablePointInsideBox(target.getBoundingBox(), mc.player.getEyePos());
        }

        return fallback;
    }

    public double targetDistanceSq(LivingEntity entity) {
        if (mc.player == null || entity == null) {
            return Double.MAX_VALUE;
        }

        return distanceSqToBox(
                mc.player.getEyePos(),
                entity.getBoundingBox()
        );
    }

    public double getDebugSafeRange(LivingEntity target) {
        if (target == null) {
            return 0.0D;
        }

        return getCurrentAttackRange(attackRange.getValue());
    }

    public double getDebugTargetDistance(LivingEntity target) {
        if (target == null) {
            return Double.MAX_VALUE;
        }

        return Math.sqrt(targetDistanceSq(target));
    }

    public boolean isDebugTargetInSafeReach(LivingEntity target) {
        if (target == null) {
            return false;
        }

        double safeRange = getDebugSafeRange(target);
        return safeRange > 0.0D
                && targetDistanceSq(target) <= safeRange * safeRange;
    }

    public boolean isDebugRotationAligned(LivingEntity target) {
        if (mc.player == null || target == null) {
            return false;
        }

        double safeRange = getDebugSafeRange(target);
        if (safeRange <= 0.0D) {
            return false;
        }

        return rayHitsBox(
                RotationManager.INSTANCE.getRotation(),
                mc.player.getEyePos(),
                target.getBoundingBox(),
                safeRange
        );
    }

    public double getDebugClosingSpeed(LivingEntity target) {
        if (target == null) {
            return 0.0D;
        }

        return predictor.getPrediction(target).closingSpeed();
    }

    public double getDebugContactTick(LivingEntity target) {
        if (target == null) {
            return Double.POSITIVE_INFINITY;
        }

        return predictor.getPrediction(target).earliestContactTick();
    }

    private boolean isStrictReachCandidate(LivingEntity entity) {
        return isCurrentReachValid(entity);
    }

    private boolean isCurrentReachValid(LivingEntity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) {
            return false;
        }

        double currentRange = getCurrentAttackRange(attackRange.getValue());
        return currentRange > 0.0D
                && targetDistanceSq(entity) <= currentRange * currentRange;
    }

    private double getCurrentAttackRange(double configuredRange) {
        return Math.min(
                VANILLA_ENTITY_REACH,
                Math.min(attackRange.getValue(), configuredRange)
        );
    }

    private Vec3d stablePointInsideBox(Box box, Vec3d eye) {
        double inset = 1.0E-4D;

        double minX = box.minX + inset;
        double minY = box.minY + inset;
        double minZ = box.minZ + inset;
        double maxX = box.maxX - inset;
        double maxY = box.maxY - inset;
        double maxZ = box.maxZ - inset;

        double x = Math.max(minX, Math.min(eye.x, maxX));
        double y = Math.max(minY, Math.min(eye.y, maxY));
        double z = Math.max(minZ, Math.min(eye.z, maxZ));

        return new Vec3d(x, y, z);
    }

    private boolean rayHitsBox(
            Rotation rotation,
            Vec3d eye,
            Box box,
            double range
    ) {
        if (rotation == null) {
            return false;
        }

        if (box.contains(eye)) {
            return true;
        }

        Vec3d end = eye.add(
                rotation.toVector().multiply(range)
        );

        return box.raycast(eye, end).isPresent();
    }

    public static double distanceSqToBox(Vec3d point, Box box) {
        double x = Math.max(
                box.minX,
                Math.min(point.x, box.maxX)
        );
        double y = Math.max(
                box.minY,
                Math.min(point.y, box.maxY)
        );
        double z = Math.max(
                box.minZ,
                Math.min(point.z, box.maxZ)
        );

        double dx = x - point.x;
        double dy = y - point.y;
        double dz = z - point.z;

        return dx * dx + dy * dy + dz * dz;
    }
}
