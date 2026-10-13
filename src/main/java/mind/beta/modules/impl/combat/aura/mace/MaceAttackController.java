/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.mace;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.impl.combat.aura.target.RaycastHelper;

public final class MaceAttackController implements IMinecraft {
    private boolean maceSwapPending = false;
    private int maceSwapRestoreSlot = -1;
    private int maceSwapWaitTicks = 0;
    private boolean maceFallAttackUsed = false;

    private enum MaceRearmState {
        READY,
        WAITING_OFF_MACE,
        WAITING_NEW_FALL,
        WAITING_MACE
    }

    private MaceRearmState maceRearmState = MaceRearmState.READY;
    private int maceRearmWaitTicks = 0;
    private boolean maceRearmSawAscent = false;
    private double maceRearmStartY = Double.NaN;
    private double maceRearmPeakY = Double.NaN;
    private static final int MACE_REARM_SWAP_WAIT_TICKS = 1;
    private static final double MACE_REARM_REQUIRED_DROP = 1.5D;

    public enum AttackState { BLOCKED, NORMAL, SMASH }

    public void tick() {
        if (mc.player != null && mc.player.isOnGround()) {
            resetMaceStateCompletely();
        }
        processMaceRearmState();
        Aura aura = Aura.getInstance();
        if (aura != null && (aura.getMaceResolverController().isEnabled()
                || aura.getMaceResolverController().isRelativePrediction())) {
            prepareMacePreSwap();
        }
        processMaceSwapState();
    }

    public AttackState resolve(AttackExecutor.AttackPerpetratorConfigurable config) {
        if (mc.player == null || config == null || config.getTarget() == null || !config.getTarget().isAlive()) {
            return AttackState.BLOCKED;
        }
        boolean smash = isForcedFallMaceAttack();
        Aura aura = Aura.getInstance();
        MaceResolver resolver = aura != null ? aura.getMaceResolverController() : null;
        if (resolver != null && resolver.isTargetingPhase()) {
            if (!smash || !resolver.getTargeting().canReachForcedMaceTarget(config)) {
                return AttackState.BLOCKED;
            }
        } else {
            if (aura == null || mc.player.distanceTo(config.getTarget()) > aura.getAttackrange().getValue()
                    || !RaycastHelper.rayTrace(config)) {
                return AttackState.BLOCKED;
            }
            var eye = mc.player.getEyePos();
            var rotation = mind.beta.modules.impl.combat.aura.RotationManager.INSTANCE.getRotation();
            if (rotation == null || !config.getBox().raycast(eye,
                    eye.add(rotation.toVector().multiply(config.getMaximumRange()))).isPresent()) {
                return AttackState.BLOCKED;
            }
        }
        return smash ? AttackState.SMASH : AttackState.NORMAL;
    }

    public void onSmash() {
        beginMaceRearm();
    }

    public void onAttack() {
        if (maceSwapPending) {
            restoreMaceSlot();
        }
    }

    private boolean isHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().getItem()
                .getTranslationKey().toLowerCase().contains("mace");
    }

    public void resetMaceStateCompletely() {
        maceFallAttackUsed = false;
        maceRearmState = MaceRearmState.READY;
        maceRearmWaitTicks = 0;
        maceRearmSawAscent = false;
        maceRearmStartY = Double.NaN;
        maceRearmPeakY = Double.NaN;
        restoreMaceSlot();
    }

    private void beginMaceRearm() {
        if (mc.player == null) {
            return;
        }

        maceFallAttackUsed = true;
        maceRearmWaitTicks = MACE_REARM_SWAP_WAIT_TICKS;
        maceRearmSawAscent = false;
        maceRearmStartY = mc.player.getY();
        maceRearmPeakY = maceRearmStartY;

        if (isActuallyHoldingMace()) {
            int offSlot = findMaceRearmOffSlot();
            if (offSlot == -1) {
                maceRearmState = MaceRearmState.WAITING_OFF_MACE;
                return;
            }

            mc.player.getInventory().setSelectedSlot(offSlot);
        }

        maceRearmState = MaceRearmState.WAITING_OFF_MACE;
    }

    private void processMaceRearmState() {
        if (maceRearmState == MaceRearmState.READY) {
            return;
        }

        if (mc.player == null) {
            maceRearmState = MaceRearmState.READY;
            maceRearmWaitTicks = 0;
            maceRearmSawAscent = false;
            maceRearmStartY = Double.NaN;
            maceRearmPeakY = Double.NaN;
            return;
        }

        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState() || !aura.getMaceOnFall().isValue()) {
            resetMaceStateCompletely();
            return;
        }

        if (mc.player.isOnGround()) {
            resetMaceStateCompletely();
            return;
        }

        MaceResolver resolver = aura.getMaceResolverController();
        if (resolver == null) {
            return;
        }

        double currentY = mc.player.getY();
        if (Double.isNaN(maceRearmPeakY) || currentY > maceRearmPeakY) {
            maceRearmPeakY = currentY;
        }

        if (!Double.isNaN(maceRearmStartY)
                && (mc.player.getVelocity().y > 0.03D || currentY > maceRearmStartY + 0.05D)) {
            maceRearmSawAscent = true;
        }

        if (maceRearmState == MaceRearmState.WAITING_OFF_MACE) {
            if (isActuallyHoldingMace()) {
                int offSlot = findMaceRearmOffSlot();
                if (offSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(offSlot);
                    maceRearmWaitTicks = MACE_REARM_SWAP_WAIT_TICKS;
                }
                return;
            }

            if (maceRearmWaitTicks > 0) {
                maceRearmWaitTicks--;
                return;
            }

            maceRearmState = MaceRearmState.WAITING_NEW_FALL;
            return;
        }

        if (maceRearmState == MaceRearmState.WAITING_NEW_FALL) {
            if (!maceRearmSawAscent) {
                return;
            }

            if (mc.player.getVelocity().y >= -0.01D) {
                return;
            }

            double dropFromPeak = maceRearmPeakY - currentY;
            if (dropFromPeak < MACE_REARM_REQUIRED_DROP) {
                return;
            }

            if (!resolver.isForcedSmashReady()) {
                return;
            }

            if (Aura.target == null || !Aura.target.isAlive()) {
                return;
            }

            if (!resolver.canPreSwapForTarget(Aura.target)) {
                return;
            }

            int maceSlot = findMaceInHotbar();
            if (maceSlot == -1) {
                return;
            }

            int selectedSlot = mc.player.getInventory().getSelectedSlot();

            if (selectedSlot != maceSlot) {
                maceSwapRestoreSlot = selectedSlot;
                maceSwapPending = true;
                maceSwapWaitTicks = MACE_REARM_SWAP_WAIT_TICKS;
                mc.player.getInventory().setSelectedSlot(maceSlot);
            }

            maceRearmState = MaceRearmState.WAITING_MACE;
            maceRearmWaitTicks = MACE_REARM_SWAP_WAIT_TICKS;
            return;
        }

        if (maceRearmState == MaceRearmState.WAITING_MACE) {
            if (!isActuallyHoldingMace()) {
                maceRearmState = MaceRearmState.WAITING_NEW_FALL;
                maceRearmWaitTicks = 0;
                return;
            }

            if (maceRearmWaitTicks > 0) {
                maceRearmWaitTicks--;
                return;
            }

            if (maceSwapWaitTicks > 0) {
                return;
            }

            if (!resolver.isForcedSmashReady()) {
                maceRearmState = MaceRearmState.WAITING_NEW_FALL;
                return;
            }

            maceFallAttackUsed = false;
            maceRearmState = MaceRearmState.READY;
        }
    }

    private void processMaceSwapState() {
        if (mc.player == null) {
            maceSwapPending = false;
            maceSwapRestoreSlot = -1;
            maceSwapWaitTicks = 0;
            return;
        }

        if (!maceSwapPending) {
            return;
        }

        Aura aura = Aura.getInstance();

        if (aura == null || !aura.isState() || !aura.getMaceOnFall().isValue()) {
            restoreMaceSlot();
            return;
        }

        if (maceSwapWaitTicks > 0) {
            maceSwapWaitTicks--;
        }
    }

    private boolean isActuallyHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().isOf(Items.MACE);
    }

    public boolean isForcedFallMaceAttack() {
        if (mc.player == null || maceFallAttackUsed || maceRearmState != MaceRearmState.READY) {
            return false;
        }

        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState() || !aura.getMaceOnFall().isValue()) {
            return false;
        }

        boolean maceReady = (maceSwapPending && maceSwapWaitTicks <= 0 && isActuallyHoldingMace())
                || (!maceSwapPending && isActuallyHoldingMace());
        MaceResolver maceResolver = aura.getMaceResolverController();

        return maceReady
                && maceResolver != null
                && maceResolver.isForcedSmashReady();
    }

    public boolean prepareMaceSwapForAttack() {
        if (mc.player == null) {
            return false;
        }

        if (maceRearmState != MaceRearmState.READY) {
            return true;
        }

        Aura aura = Aura.getInstance();

        if (maceSwapPending) {
            if (aura == null || !aura.isState() || !aura.getMaceOnFall().isValue()) {
                restoreMaceSlot();
                return false;
            }

            return maceSwapWaitTicks > 0;
        }

        if (maceFallAttackUsed) {
            return false;
        }

        if (!shouldSwapToMaceOnFall()) {
            return false;
        }

        if (isHoldingMace()) {
            return false;
        }

        int maceSlot = findMaceInHotbar();

        if (maceSlot == -1) {
            return false;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();

        if (selectedSlot == maceSlot) {
            return false;
        }

        maceSwapRestoreSlot = selectedSlot;
        maceSwapPending = true;
        maceSwapWaitTicks = 1;

        mc.player.getInventory().setSelectedSlot(maceSlot);

        return true;
    }

    private boolean shouldSwapToMaceOnFall() {
        if (mc.player == null) {
            return false;
        }

        Aura aura = Aura.getInstance();

        if (aura == null || !aura.isState() || !aura.getMaceOnFall().isValue()) {
            return false;
        }

        return aura.getMaceResolverController().canPreSwapForTarget(Aura.target);
    }

    private void prepareMacePreSwap() {
        if (mc.player == null
                || maceRearmState != MaceRearmState.READY
                || maceSwapPending
                || maceFallAttackUsed
                || isHoldingMace()) {
            return;
        }

        Aura aura = Aura.getInstance();
        if (aura == null
                || !aura.isState()
                || !aura.getMaceOnFall().isValue()
                || (!aura.getMaceResolverController().isEnabled()
                && !aura.getMaceResolverController().isRelativePrediction())) {
            return;
        }

        if (Aura.target == null || !Aura.target.isAlive()) {
            return;
        }

        if (!aura.getMaceResolverController().canPreSwapForTarget(Aura.target)) {
            return;
        }

        int maceSlot = findMaceInHotbar();
        if (maceSlot == -1) {
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (selectedSlot == maceSlot) {
            return;
        }

        maceSwapRestoreSlot = selectedSlot;
        maceSwapPending = true;
        maceSwapWaitTicks = 1;
        mc.player.getInventory().setSelectedSlot(maceSlot);
    }

    private int findMaceInHotbar() {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(Items.MACE)) {
                return slot;
            }
        }

        return -1;
    }

    private int findMaceRearmOffSlot() {
        if (mc.player == null) {
            return -1;
        }

        int selected = mc.player.getInventory().getSelectedSlot();

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) {
                continue;
            }

            var stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                    && !stack.isOf(Items.MACE)
                    && stack.getItem().getTranslationKey().toLowerCase().contains("sword")) {
                return slot;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) {
                continue;
            }

            var stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && !stack.isOf(Items.MACE)) {
                return slot;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && !mc.player.getInventory().getStack(slot).isOf(Items.MACE)) {
                return slot;
            }
        }

        return -1;
    }

    private void restoreMaceSlot() {
        if (mc.player != null
                && maceSwapRestoreSlot >= 0
                && maceSwapRestoreSlot < 9
                && mc.player.getInventory().getSelectedSlot() != maceSwapRestoreSlot) {
            mc.player.getInventory().setSelectedSlot(maceSwapRestoreSlot);
        }

        maceSwapPending = false;
        maceSwapRestoreSlot = -1;
        maceSwapWaitTicks = 0;
    }

    public String getMaceDebugStatus(boolean itemUse, int lastAttackPlayerAge, boolean attackReady, boolean sprintSync) {
        if (mc.player == null) {
            return "No player";
        }

        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState()) {
            return "Aura disabled";
        }

        if (!aura.getMaceOnFall().isValue()) {
            return "Mace On Fall disabled";
        }

        LivingEntity target = Aura.target;
        if (target == null || !target.isAlive()) {
            return "No target";
        }

        if (findMaceInHotbar() == -1 && !isActuallyHoldingMace()) {
            return "No mace in hotbar";
        }

        if (maceRearmState != MaceRearmState.READY) {
            return switch (maceRearmState) {
                case WAITING_OFF_MACE -> "Re-arm: swap off mace";
                case WAITING_NEW_FALL -> {
                    if (!maceRearmSawAscent) {
                        yield "Re-arm: waiting ascent";
                    }

                    double drop = Double.isNaN(maceRearmPeakY) || mc.player == null
                            ? 0.0D
                            : Math.max(0.0D, maceRearmPeakY - mc.player.getY());
                    yield String.format("Re-arm: fall %.2f/%.2f", drop, MACE_REARM_REQUIRED_DROP);
                }
                case WAITING_MACE -> "Re-arm: swap back to mace";
                default -> "Re-arm";
            };
        }

        if (maceFallAttackUsed) {
            return "Smash used: waiting reset";
        }

        MaceResolver resolver = aura.getMaceResolverController();
        if (resolver == null) {
            return "Resolver unavailable";
        }

        if (maceSwapPending && maceSwapWaitTicks > 0) {
            return "Mace swap sync: " + maceSwapWaitTicks + "t";
        }

        if (!isActuallyHoldingMace()) {
            if (!resolver.canPreSwapForTarget(target)) {
                return resolver.isForcedSmashReady()
                        ? "Waiting pre-swap window"
                        : "Smash not ready";
            }
            return "Mace swap ready";
        }

        if (!resolver.isForcedSmashReady()) {
            return "Holding mace: smash not ready";
        }

        double distance = resolver.getDebugTargetDistance(target);
        double safeRange = resolver.getDebugSafeRange(target);
        if (!resolver.isDebugTargetInSafeReach(target)) {
            return String.format(java.util.Locale.US, "Out of reach %.2f / %.2f", distance, safeRange);
        }

        if (!resolver.isDebugRotationAligned(target)) {
            return "Rotation not aligned";
        }

        if (itemUse) {
            return "Blocked: item use";
        }

        if (lastAttackPlayerAge == mc.player.age) {
            return "Already attacked this tick";
        }

        if (!isForcedFallMaceAttack() && !attackReady) {
            return "Waiting attack timer/cooldown";
        }
        if (sprintSync) {
            return "Sprint reset sync";
        }

        return "Ready to smash";
    }
}
