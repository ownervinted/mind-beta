/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.mace;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.scripting.extensions.LuaExtensionRegistry;

public final class MaceResolver implements IMinecraft {
    private static final float DEFAULT_PRE_SWAP_DISTANCE = 3.0F;
    private static final float SMASH_DISTANCE = 5.0F;
    private final BooleanSetting maceOnFall;
    private final SelectSetting setting;
    private final MacePredictor predictor;
    private final MaceTargeting targeting;

    public MaceResolver(BooleanSetting maceOnFall, SliderSettings attackRange) {
        this.maceOnFall = maceOnFall;
        setting = new SelectSetting("Mace resolver")
                .value("Disabled", "Default", "Improved", "Vanilla")
                .selected("Vanilla")
                .visible(() -> maceOnFall.isValue());
        LuaExtensionRegistry.getInstance().bindMaceResolverSetting(setting, "Vanilla");
        predictor = new MacePredictor(maceOnFall, attackRange);
        targeting = new MaceTargeting(this, predictor, attackRange);
    }

    public SelectSetting getSetting() { return setting; }
    public SelectSetting getPredictionSetting() { return predictor.getSetting(); }
    public MacePredictor getPredictor() { return predictor; }
    public MaceTargeting getTargeting() { return targeting; }

    public boolean canSmash() {
        if (mc.player == null) {
            return false;
        }
        if (isVanilla()) {
            return isVanillaSmashReady();
        }
        if (isImproved()) {
            return isVanillaSmashReady()
                    || (!mc.player.isOnGround()
                    && mc.player.getVelocity().y < 0.0D
                    && mc.player.fallDistance >= 1.5F);
        }
        return !mc.player.isOnGround()
                && mc.player.getVelocity().y < 0.0D
                && mc.player.fallDistance > SMASH_DISTANCE;
    }

    public boolean isEnabled() {
        return !setting.isSelected("Disabled");
    }

    public boolean isDefault() {
        return setting.isSelected("Default");
    }

    public boolean isImproved() {
        return setting.isSelected("Improved");
    }

    public boolean isVanilla() {
        return setting.isSelected("Vanilla");
    }

    public boolean isCustom() {
        return LuaExtensionRegistry.getInstance().isCustomMaceResolver(setting.getSelected());
    }

    public boolean isVanillaSmashReady() {
        return mc.player != null && MaceItem.shouldDealAdditionalDamage(mc.player);
    }

    public boolean isForcedSmashReady() {
        if (mc.player == null) {
            return false;
        }

        boolean fallback = canSmash();

        return isCustom()
                ? LuaExtensionRegistry.getInstance()
                .maceForcedSmashReady(setting.getSelected(), Aura.target, fallback)
                : fallback;
    }

    public boolean isTargetingPhase() {
        if (!isEnabled() || !maceOnFall.isValue() || mc.player == null) {
            return false;
        }

        boolean fallback = canSmash();

        return isCustom()
                ? LuaExtensionRegistry.getInstance()
                .maceTargetingPhase(setting.getSelected(), fallback)
                : fallback;
    }

    public float getPreSwapDistance() {
        return isEnabled() ? DEFAULT_PRE_SWAP_DISTANCE : SMASH_DISTANCE;
    }

    public boolean canPreSwapNow() {
        if (mc.player == null) {
            return false;
        }

        boolean fallback;
        if (isVanilla()) {
            fallback = isVanillaSmashReady();
        } else {
            float requiredFallDistance = getPreSwapDistance();
            fallback = !mc.player.isOnGround()
                    && mc.player.getVelocity().y < 0.0D
                    && mc.player.fallDistance > requiredFallDistance;
        }

        return isCustom()
                ? LuaExtensionRegistry.getInstance()
                .macePreSwap(setting.getSelected(), Aura.target, fallback)
                : fallback;
    }

    public boolean isRelativePrediction() { return predictor.isRelativePrediction(); }

    public float getSearchRangeBonus() {
        return targeting.getSearchRangeBonus();
    }

    public void resolve(TargetSelector selector, TargetSelector.EntityFilter filter) {
        targeting.resolve(selector, filter);
    }

    public boolean isSmashCandidate(LivingEntity entity) {
        return targeting.isSmashCandidate(entity);
    }

    public boolean canReachForcedMaceTarget(
            AttackExecutor.AttackPerpetratorConfigurable config
    ) {
        return targeting.canReachForcedMaceTarget(config);
    }

    public RotationConfig resolveRotationConfig(
            RotationConfig normal,
            LivingEntity target
    ) {
        return targeting.resolveRotationConfig(normal, target);
    }

    public boolean shouldBoostRotation(LivingEntity target) {
        return targeting.shouldBoostRotation(target);
    }

    public boolean canPreSwapForTarget(LivingEntity target) {
        return targeting.canPreSwapForTarget(target);
    }

    public Vec3d resolveAimPoint(LivingEntity target, Vec3d fallback) {
        return targeting.resolveAimPoint(target, fallback);
    }

    public double targetDistanceSq(LivingEntity entity) {
        return targeting.targetDistanceSq(entity);
    }

    public double getDebugSafeRange(LivingEntity target) {
        return targeting.getDebugSafeRange(target);
    }

    public double getDebugTargetDistance(LivingEntity target) {
        return targeting.getDebugTargetDistance(target);
    }

    public boolean isDebugTargetInSafeReach(LivingEntity target) {
        return targeting.isDebugTargetInSafeReach(target);
    }

    public boolean isDebugRotationAligned(LivingEntity target) {
        return targeting.isDebugRotationAligned(target);
    }

    public double getDebugClosingSpeed(LivingEntity target) {
        return targeting.getDebugClosingSpeed(target);
    }

    public double getDebugContactTick(LivingEntity target) {
        return targeting.getDebugContactTick(target);
    }
}
