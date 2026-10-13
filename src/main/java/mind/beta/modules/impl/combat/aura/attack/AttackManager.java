/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.attack;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.UsingItemEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.AutoSpear;
import mind.beta.modules.impl.combat.TriggerBot;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.mace.MaceAttackController;
import mind.beta.modules.impl.combat.aura.target.RaycastHelper;
import mind.beta.modules.impl.movement.ElytraTarget;
import mind.beta.util.player.PlayerSimulation;
import mind.beta.util.string.PlayerInteractionHelper;
import mind.beta.util.timer.StopWatch;

import java.util.concurrent.ThreadLocalRandom;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttackManager implements IMinecraft {
    private final AttackCooldown clickScheduler = new AttackCooldown();
    private final StopWatch attackTimer = new StopWatch();
    private ClientPlayerEntity manualUsePlayer;
    private int lastManualUseAge = Integer.MIN_VALUE;

    private int count = 0;
    private int ticksOnBlock = 0;

    private static volatile int sprintResetHoldTicks = 0;
    private static volatile int sprintResetPreAttackTicks = 0;
    private static volatile boolean sprintResetAttackReady = false;
    private static volatile boolean sprintRestoreRequested = false;
    private boolean sprintResetCommittedAttack = false;

    private final MaceAttackController maceAttackController = new MaceAttackController();
    private int lastAttackPlayerAge = Integer.MIN_VALUE;
    private long nextLegacyAttackTime = 0L;

    private static final int MACE_DAMAGE_OBSERVE_TICKS = 8;
    private LivingEntity pendingMaceDamageTarget;
    private float pendingMaceDamageBefore;
    private int pendingMaceDamageStartAge = Integer.MIN_VALUE;
    private String lastMaceTargetName = "";
    private float lastMaceDamage = Float.NaN;
    private long lastMaceHitTime;

    void tick() {
        AutoSpear autoSpear = AutoSpear.getInstance();
        if (autoSpear != null && autoSpear.isSequenceActive()) {
            return;
        }

        updateMaceDamageTelemetry();
        if (shouldYieldToManualItemUse()) {
            return;
        }

        if (mc.player != null) {
            if (mc.player.isOnGround()) {
                ticksOnBlock++;
            } else {
                ticksOnBlock = 0;
            }
        } else {
            ticksOnBlock = 0;
        }

        maceAttackController.tick();
        processLegitSprintReset();
    }

    private void processLegitSprintReset() {
        if (mc.player == null) {
            sprintResetHoldTicks = 0;
            sprintResetPreAttackTicks = 0;
            sprintResetAttackReady = false;
            sprintRestoreRequested = false;
            sprintResetCommittedAttack = false;
            return;
        }

        if (isShieldMovementState()) {
            clearSprintResetForShield();
            return;
        }

        if (sprintResetPreAttackTicks > 0) {
            mc.player.setSprinting(false);
            sprintResetPreAttackTicks--;
            if (sprintResetPreAttackTicks == 0) {
                sprintResetAttackReady = true;
            }
            return;
        }

        if (sprintResetAttackReady) {
            mc.player.setSprinting(false);
            return;
        }

        if (sprintResetHoldTicks <= 0) {
            return;
        }

        mc.player.setSprinting(false);
        sprintResetHoldTicks--;

        if (sprintResetHoldTicks == 0) {
            sprintRestoreRequested = mc.options.forwardKey.isPressed() && !isSneakRequested();
        }
    }

    public static PlayerInput applySprintInput(PlayerInput input) {
        AttackManager manager = mind.beta.Initialization.getInstance()
                .getManager()
                .getAttackPerpetrator()
                .getAttackHandler();

        if (manager == null || mc.player == null) {
            return input;
        }

        boolean blocked = input.sneak()
                || mc.options.sneakKey.isPressed()
                || mc.player.isUsingItem()
                || manager.shouldYieldToManualItemUse();
        boolean sprint = input.sprint();

        if (blocked || isLegitSprintResetPending()) {
            sprint = false;
            if (blocked) {
                sprintRestoreRequested = false;
            }
        } else if (sprintRestoreRequested) {
            sprint = sprint || (input.forward() && !input.backward());
            sprintRestoreRequested = false;
        }

        if (sprint == input.sprint()) {
            return input;
        }

        return new PlayerInput(input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), input.sneak(), sprint);
    }

    public static boolean isShieldTransitionActive() {
        return false;
    }

    public static boolean isLegitSprintResetPending() {
        return sprintResetPreAttackTicks > 0 || sprintResetHoldTicks > 0 || sprintResetAttackReady;
    }

    private boolean prepareLegitSprintResetForAttack() {
        if (mc.player == null) {
            return true;
        }

        if (sprintResetAttackReady) {
            sprintResetAttackReady = false;
            sprintResetCommittedAttack = true;
            return true;
        }

        if (sprintResetPreAttackTicks > 0) {
            return false;
        }

        mc.player.setSprinting(false);
        sprintResetPreAttackTicks = 1;
        return false;
    }

    private void scheduleLegitSprintRestoreAfterAttack() {
        sprintResetHoldTicks = java.util.concurrent.ThreadLocalRandom.current().nextInt(2, 5);
    }

    public void resetPendingState() {
        sprintResetHoldTicks = 0;
        sprintResetPreAttackTicks = 0;
        sprintResetAttackReady = false;
        sprintRestoreRequested = false;
        sprintResetCommittedAttack = false;
        nextLegacyAttackTime = 0L;
        pendingMaceDamageTarget = null;
        pendingMaceDamageStartAge = Integer.MIN_VALUE;

        maceAttackController.resetMaceStateCompletely();
    }

    public void resetPendingStateForItemUse() {
        sprintResetHoldTicks = 0;
        sprintResetPreAttackTicks = 0;
        sprintResetAttackReady = false;
        sprintRestoreRequested = false;
        sprintResetCommittedAttack = false;

        nextLegacyAttackTime = 0L;
        pendingMaceDamageTarget = null;
        pendingMaceDamageStartAge = Integer.MIN_VALUE;

        maceAttackController.resetMaceStateCompletely();
    }

    void onUsingItem(UsingItemEvent e) {
        if (e.getType() == EventType.START && mc.player != null) {
            manualUsePlayer = mc.player;
            lastManualUseAge = mc.player.age;
            clearSprintResetForShield();
        }
    }

    void onPacket(PacketEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Packet<?> packet = e.getPacket();

        if (packet instanceof HandSwingC2SPacket || packet instanceof UpdateSelectedSlotC2SPacket) {
            clickScheduler.recalculate();
        }
    }

    private boolean hasAnyMovementInput() {
        if (mc.player == null)
            return false;
        return mc.player.input.playerInput.forward() ||
                mc.player.input.playerInput.backward() ||
                mc.player.input.playerInput.left() ||
                mc.player.input.playerInput.right();
    }

    private boolean isHoldingMace() {
        return clickScheduler.isHoldingMace();
    }

    private boolean isActuallyHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().isOf(Items.MACE);
    }

    private boolean isForcedFallMaceAttack() {
        return maceAttackController.isForcedFallMaceAttack();
    }

    private UseAction getActiveUseAction() {
        if (mc.player == null || !mc.player.isUsingItem() || mc.player.getActiveItem().isEmpty()) {
            return UseAction.NONE;
        }

        return mc.player.getActiveItem().getUseAction();
    }

    private boolean isUsingShield() {
        return getActiveUseAction() == UseAction.BLOCK;
    }

    private boolean isShieldHeldInHands() {
        if (mc.player == null) {
            return false;
        }

        return mc.player.getMainHandStack().getUseAction() == UseAction.BLOCK
                || mc.player.getOffHandStack().getUseAction() == UseAction.BLOCK;
    }

    private boolean hasShieldUseIntent() {
        return isShieldHeldInHands() && mc.options.useKey.isPressed();
    }

    private boolean isShieldMovementState() {
        return isUsingShield() || hasShieldUseIntent();
    }

    private void clearSprintResetForShield() {
        sprintResetHoldTicks = 0;
        sprintResetPreAttackTicks = 0;
        sprintResetAttackReady = false;
        sprintRestoreRequested = false;
        sprintResetCommittedAttack = false;
    }

    private boolean shouldYieldToManualItemUse() {
        if (manualUsePlayer != mc.player) {
            manualUsePlayer = mc.player;
            lastManualUseAge = Integer.MIN_VALUE;
        }

        if (mc.player == null) {
            return false;
        }

        if (mc.player.isUsingItem() || hasShieldUseIntent()) {
            lastManualUseAge = mc.player.age;
            clearSprintResetForShield();
            return true;
        }

        return lastManualUseAge != Integer.MIN_VALUE
                && mc.player.age <= lastManualUseAge + 1;
    }

    private boolean shouldYieldToManualBlockUse() {
        return mc.player != null
                && mc.options.useKey.isPressed()
                && !hasShieldUseIntent()
                && mc.crosshairTarget instanceof BlockHitResult;
    }

    private boolean shouldForceJumpCrit() {
        if (mc.player == null) {
            return false;
        }

        return mc.options.jumpKey.isPressed() || !mc.player.isOnGround();
    }

    private boolean isCurrentJumpCritPhase() {
        if (mc.player == null || mc.player.isOnGround()) {
            return false;
        }

        return mc.player.fallDistance > 0.0F
                && mc.player.getVelocity().y < -0.01
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying;
    }

    private boolean isInWater() {
        return mc.player != null
                && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isSwimming());
    }

    private boolean hasLowCeiling() {
        if (mc.player == null || mc.world == null)
            return false;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos above1 = playerPos.up(2);
        BlockPos above2 = playerPos.up(3);

        BlockState state1 = mc.world.getBlockState(above1);
        BlockState state2 = mc.world.getBlockState(above2);

        boolean blocked1 = !state1.isAir() && !state1.getCollisionShape(mc.world, above1).isEmpty();
        boolean blocked2 = !state2.isAir() && !state2.getCollisionShape(mc.world, above2).isEmpty();

        return blocked1 || blocked2;
    }

    private boolean isPerfectCrit() {
        if (mc.player == null)
            return false;

        return mc.player.fallDistance > 0.0F
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying;
    }

    private boolean isAscending() {
        if (mc.player == null)
            return false;
        return !mc.player.isOnGround() && mc.player.getVelocity().y > 0.0;
    }

    private boolean isDescending() {
        if (mc.player == null)
            return false;
        return !mc.player.isOnGround() && mc.player.getVelocity().y <= 0.0;
    }

    private boolean willBeCritInTicks(int ticks) {
        if (ticks == 0) {
            return isPerfectCrit();
        }

        PlayerSimulation sim = PlayerSimulation.simulateLocalPlayer(ticks);

        return sim.fallDistance > 0.0F
                && !sim.onGround
                && sim.velocity.y <= 0.0
                && !sim.isClimbing()
                && !sim.player.isTouchingWater()
                && !sim.hasStatusEffect(StatusEffects.BLINDNESS)
                && !sim.player.hasVehicle()
                && !sim.player.getAbilities().flying;
    }

    private boolean hasMovementRestrictions() {
        if (mc.player == null)
            return true;

        if (isInWater())
            return false;
        if (hasLowCeiling())
            return true;
        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS))
            return true;
        if (mc.player.hasStatusEffect(StatusEffects.LEVITATION))
            return true;
        if (PlayerInteractionHelper.isBoxInBlock(mc.player.getBoundingBox().expand(-1e-3), Blocks.COBWEB))
            return true;
        if (mc.player.isInLava())
            return true;
        if (mc.player.isClimbing())
            return true;
        if (!PlayerInteractionHelper.canChangeIntoPose(EntityPose.STANDING, mc.player.getEntityPos()))
            return true;
        if (mc.player.getAbilities().flying)
            return true;

        return false;
    }

    private boolean isSneakRequested() {
        return mc.player != null
                && (mc.options.sneakKey.isPressed()
                || mc.player.input.playerInput.sneak()
                || mc.player.isSneaking());
    }

    private boolean shouldResetSprintForCrit() {
        if (mc.player == null)
            return false;

        if (isShieldMovementState())
            return false;

        if (isInWater())
            return false;
        if (mc.player.isGliding())
            return false;

        return !isSneakRequested()
                && (mc.player.isSprinting() || sprintResetPreAttackTicks > 0 || sprintResetAttackReady);
    }

    private boolean resetSprintBeforeAttack() {
        if (Aura.getInstance().getResetSprintMode().isSelected("Packet")) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(
                            mc.player,
                            ClientCommandC2SPacket.Mode.STOP_SPRINTING
                    )
            );
            mc.player.setSprinting(false);
            sprintRestoreRequested = false;
            sprintResetCommittedAttack = true;
            return true;
        }

        sprintRestoreRequested = false;
        return prepareLegitSprintResetForAttack();
    }

    private boolean isLegacyAttackMode() {
        Aura aura = Aura.getInstance();
        return aura != null
                && aura.isState()
                && aura.getAttackMode().isSelected("1.8");
    }

    private boolean isLegacyAttackReady() {
        return System.currentTimeMillis() >= nextLegacyAttackTime;
    }

    private void scheduleNextLegacyAttack() {
        Aura aura = Aura.getInstance();
        if (aura == null) {
            nextLegacyAttackTime = 0L;
            return;
        }

        float min = Math.max(1.0F, aura.getMinCps().getValue());
        float max = Math.max(min, aura.getMaxCps().getValue());
        float cps = min;

        if (max > min) {
            cps = ThreadLocalRandom.current().nextFloat(min, max);
        }

        long baseDelay = Math.max(20L, (long) (1000.0F / cps));
        long jitter = ThreadLocalRandom.current().nextLong(-8L, 9L);
        nextLegacyAttackTime = System.currentTimeMillis() + Math.max(20L, baseDelay + jitter);
    }

    private boolean canCritNow() {
        return canAttack(null, 0);
    }

    void handleAttack(AttackExecutor.AttackPerpetratorConfigurable config) {
        if (config.getTarget() == null || !config.getTarget().isAlive()) {
            return;
        }

        if (shouldYieldToManualItemUse() || shouldYieldToManualBlockUse()) {
            return;
        }

        if (config.isShieldCheck() && ShieldAttackCheck.isProtected(config.getTarget(), mc.player)) {
            return;
        }

        if (maceAttackController.prepareMaceSwapForAttack()) {
            return;
        }

        if (isHoldingMace()) {
            handleMaceAttack(config);
            return;
        }

        boolean elytraMode = checkElytraMode(config);
        if (elytraMode && !checkElytraRaycast(config)) {
            return;
        }

        if (!RaycastHelper.rayTrace(config)) {
            return;
        }

        if (!isLookingAtTarget(config)) {
            return;
        }

        if (isLegacyAttackMode()) {
            if (!isLegacyAttackReady()) {
                return;
            }
        } else if (!clickScheduler.isCooldownComplete(0)) {
            return;
        }

        if (!canAttack(config, 2)) {
            return;
        }

        Aura aura = Aura.getInstance();
        boolean checkCritEnabled = aura.getCheckCrit().isValue();

        if (checkCritEnabled && shouldForceJumpCrit() && !isCurrentJumpCritPhase()) {
            return;
        }

        preAttackEntity(config);

        boolean shouldReset = shouldResetSprintForCrit();

        if (shouldReset && !resetSprintBeforeAttack()) {
            return;
        }

        executeAttack(config);
    }

    private void preAttackEntity(AttackExecutor.AttackPerpetratorConfigurable config) {

    }

    private void handleMaceAttack(AttackExecutor.AttackPerpetratorConfigurable config) {
        if (shouldYieldToManualItemUse())
            return;

        MaceAttackController.AttackState state = maceAttackController.resolve(config);
        if (state == MaceAttackController.AttackState.BLOCKED) {
            return;
        }
        boolean forcedFallMaceHit = state == MaceAttackController.AttackState.SMASH;

        if (!forcedFallMaceHit) {
            if (isLegacyAttackMode()) {
                if (!isLegacyAttackReady())
                    return;
            } else if (!clickScheduler.isCooldownComplete(0)) {
                return;
            }
        }

        preAttackEntity(config);

        boolean shouldReset = shouldResetSprintForCrit();

        if (shouldReset && !resetSprintBeforeAttack()) {
            return;
        }

        boolean attacked = executeAttack(config);

        if (attacked && forcedFallMaceHit) {
            maceAttackController.onSmash();
        }
    }

    private boolean checkElytraMode(AttackExecutor.AttackPerpetratorConfigurable config) {
        return Aura.target != null &&
                Aura.target.isGliding() &&
                mc.player.isGliding() &&
                ElytraTarget.getInstance() != null &&
                ElytraTarget.getInstance().isState();
    }

    private boolean checkElytraRaycast(AttackExecutor.AttackPerpetratorConfigurable config) {
        Vec3d targetVelocity = config.getTarget().getVelocity();
        float leadTicks = 0;
        if (ElytraTarget.shouldElytraTarget) {
            leadTicks = ElytraTarget.getInstance().elytraForward.getValue();
        }
        Vec3d predictedPos = config.getTarget().getEntityPos().add(targetVelocity.multiply(leadTicks));
        Box predictedBox = new Box(
                predictedPos.x - config.getTarget().getWidth() / 2,
                predictedPos.y,
                predictedPos.z - config.getTarget().getWidth() / 2,
                predictedPos.x + config.getTarget().getWidth() / 2,
                predictedPos.y + config.getTarget().getHeight(),
                predictedPos.z + config.getTarget().getWidth() / 2);
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = RotationManager.INSTANCE.getRotation().toVector();
        return predictedBox.raycast(eyePos, eyePos.add(lookVec.multiply(config.getMaximumRange()))).isPresent();
    }

    private boolean executeAttack(AttackExecutor.AttackPerpetratorConfigurable config) {
        if (mc.player == null || config == null || config.getTarget() == null) {
            return false;
        }

        if (shouldYieldToManualItemUse()) {
            return false;
        }

        if (config.isShieldCheck() && ShieldAttackCheck.isProtected(config.getTarget(), mc.player)) {
            return false;
        }

        int currentAge = mc.player.age;
        if (lastAttackPlayerAge == currentAge) {
            return false;
        }

        LivingEntity attackTarget = config.getTarget();
        boolean maceAttack = isActuallyHoldingMace();
        float maceHealthBefore = maceAttack ? getObservedHealth(attackTarget) : 0.0F;

        lastAttackPlayerAge = currentAge;

        mc.interactionManager.attackEntity(mc.player, attackTarget);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (maceAttack) {
            beginMaceDamageObservation(attackTarget, maceHealthBefore);
        }

        maceAttackController.onAttack();

        attackTimer.reset();
        count++;

        if (isLegacyAttackMode()) {
            scheduleNextLegacyAttack();
        }

        if (sprintResetCommittedAttack) {
            sprintResetCommittedAttack = false;
            scheduleLegitSprintRestoreAfterAttack();
        }

        return true;
    }

    public String getMaceDebugStatus() {
        boolean attackReady = isLegacyAttackMode() ? isLegacyAttackReady() : clickScheduler.isCooldownComplete(0);
        return maceAttackController.getMaceDebugStatus(shouldYieldToManualItemUse(), lastAttackPlayerAge,
                attackReady, sprintResetPreAttackTicks > 0 || sprintResetAttackReady);
    }

    public String getMaceLastHitDebugLine() {
        if (lastMaceHitTime == 0L || System.currentTimeMillis() - lastMaceHitTime > 3500L) {
            return "";
        }

        String damage = Float.isNaN(lastMaceDamage)
                ? "?"
                : String.format(java.util.Locale.US, "%.1f", lastMaceDamage);
        return "Last: " + lastMaceTargetName + " | dmg " + damage;
    }

    public boolean hasRecentMaceHitDebug() {
        return lastMaceHitTime != 0L && System.currentTimeMillis() - lastMaceHitTime <= 3500L;
    }

    private void beginMaceDamageObservation(LivingEntity target, float healthBefore) {
        pendingMaceDamageTarget = target;
        pendingMaceDamageBefore = healthBefore;
        pendingMaceDamageStartAge = mc.player != null ? mc.player.age : Integer.MIN_VALUE;
        lastMaceTargetName = target.getName().getString();
        lastMaceDamage = Float.NaN;
        lastMaceHitTime = System.currentTimeMillis();
    }

    private void updateMaceDamageTelemetry() {
        if (pendingMaceDamageTarget == null || mc.player == null) {
            return;
        }

        float currentHealth = getObservedHealth(pendingMaceDamageTarget);
        float damage = pendingMaceDamageBefore - currentHealth;

        if (damage > 0.01F) {
            lastMaceDamage = damage;
            pendingMaceDamageTarget = null;
            pendingMaceDamageStartAge = Integer.MIN_VALUE;
            return;
        }

        if (pendingMaceDamageStartAge != Integer.MIN_VALUE
                && mc.player.age - pendingMaceDamageStartAge > MACE_DAMAGE_OBSERVE_TICKS) {
            pendingMaceDamageTarget = null;
            pendingMaceDamageStartAge = Integer.MIN_VALUE;
        }
    }

    private float getObservedHealth(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }

        return Math.max(0.0F, entity.getHealth() + entity.getAbsorptionAmount());
    }

    void handleTriggerAttack(AttackExecutor.AttackPerpetratorConfigurable config, TriggerBot triggerBot) {
        if (shouldYieldToManualItemUse() || shouldYieldToManualBlockUse())
            return;
        if (!RaycastHelper.rayTrace(config))
            return;
        if (!isLookingAtTarget(config))
            return;
        if (!clickScheduler.isCooldownComplete(0))
            return;
        if (!canAttackTrigger(config, triggerBot))
            return;

        preAttackEntity(config);

        boolean shouldReset = shouldResetSprintForCrit();

        if (shouldReset && !resetSprintBeforeAttack()) {
            return;
        }

        executeAttack(config);

    }

    private boolean canAttackTrigger(AttackExecutor.AttackPerpetratorConfigurable config, TriggerBot triggerBot) {
        AutoSpear autoSpear = AutoSpear.getInstance();
        if (autoSpear != null && autoSpear.isSequenceActive()) {
            return false;
        }

        if (shouldYieldToManualItemUse())
            return false;

        if (!clickScheduler.isCooldownComplete(0))
            return false;

        boolean checkCritEnabled = triggerBot.isOnlyCrits();
        boolean smartCritsEnabled = triggerBot.getSmartCrits().isValue();

        if (!checkCritEnabled) {
            return true;
        }

        if (shouldForceJumpCrit()) {
            return isCurrentJumpCritPhase();
        }

        if (isInWater() || hasLowCeiling() || hasMovementRestrictions()) {
            return true;
        }

        if (mc.player.isOnGround()) {
            return smartCritsEnabled;
        }

        return isCurrentJumpCritPhase();
    }

    public boolean shouldResetSprinting(AttackExecutor.AttackPerpetratorConfigurable config) {
        if (Aura.target == null)
            return false;
        if (shouldYieldToManualItemUse())
            return false;
        if (isHoldingMace())
            return true;
        return shouldResetSprintForCrit();
    }

    public boolean shouldResetSprintingForTrigger(AttackExecutor.AttackPerpetratorConfigurable config,
                                                  TriggerBot triggerBot) {
        if (triggerBot.target == null)
            return false;
        if (shouldYieldToManualItemUse())
            return false;
        return shouldResetSprintForCrit();
    }

    public int getTicksUntilNextAttack() {
        if (mc.player == null) {
            return Integer.MAX_VALUE;
        }
        if (isForcedFallMaceAttack()) {
            return 0;
        }
        if (isLegacyAttackMode()) {
            long remainingMs = Math.max(0L, nextLegacyAttackTime - System.currentTimeMillis());
            return (int) Math.min(Integer.MAX_VALUE, (remainingMs + 49L) / 50L);
        }
        float progress = mc.player.getAttackCooldownProgress(0.0F);
        if (progress >= 0.95F) {
            return 0;
        }
        float perTick = mc.player.getAttackCooldownProgress(1.0F) - progress;
        if (perTick <= 0.00001F) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil((0.95F - progress) / perTick));
    }

    public boolean canAttack(AttackExecutor.AttackPerpetratorConfigurable config, int ticks) {
        AutoSpear autoSpear = AutoSpear.getInstance();
        if (autoSpear != null && autoSpear.isSequenceActive()) {
            return false;
        }

        if (shouldYieldToManualItemUse() || shouldYieldToManualBlockUse())
            return false;

        if (isForcedFallMaceAttack()) {
            return true;
        }

        if (isHoldingMace()) {
            if (isLegacyAttackMode()) {
                return isLegacyAttackReady();
            }

            return clickScheduler.isCooldownComplete(ticks);
        }

        if (isLegacyAttackMode()) {
            if (!isLegacyAttackReady()) {
                return false;
            }
        } else if (!clickScheduler.isCooldownComplete(0)) {
            return false;
        }

        Aura aura = Aura.getInstance();
        boolean checkCritEnabled = aura.getCheckCrit().isValue();
        boolean smartCritsEnabled = aura.getSmartCrits().isValue();

        if (!checkCritEnabled) {
            return true;
        }

        if (shouldForceJumpCrit()) {
            if (isCurrentJumpCritPhase()) {
                return true;
            }

            int predictionTicks = Math.max(3, ticks);

            for (int i = 1; i <= predictionTicks; i++) {
                PlayerSimulation sim = PlayerSimulation.simulateLocalPlayer(i);

                if (sim.onGround)
                    continue;
                if (sim.velocity.y > 0.03)
                    continue;
                if (sim.isClimbing())
                    continue;
                if (sim.player.isTouchingWater())
                    continue;
                if (sim.hasStatusEffect(StatusEffects.BLINDNESS))
                    continue;
                if (sim.player.hasVehicle())
                    continue;
                if (sim.player.getAbilities().flying)
                    continue;

                return true;
            }

            return false;
        }

        if (isInWater() || hasLowCeiling() || hasMovementRestrictions())
            return true;

        if (mc.player.isOnGround())
            return smartCritsEnabled;

        return isCurrentJumpCritPhase();
    }

    public boolean canCrit(AttackExecutor.AttackPerpetratorConfigurable config, int ticks) {
        if (isForcedFallMaceAttack())
            return true;

        if (isHoldingMace())
            return true;

        if (mc.player.isUsingItem()
                && !mc.player.getActiveItem().getItem().equals(Items.SHIELD)
                && config.isEatAndAttack()) {
            return false;
        }

        if (isInWater() || hasLowCeiling() || hasMovementRestrictions())
            return true;

        Aura aura = Aura.getInstance();
        boolean checkCritEnabled = aura.getCheckCrit().isValue();
        boolean smartCritsEnabled = aura.getSmartCrits().isValue();

        if (!checkCritEnabled)
            return true;

        if (ticks > 0) {
            for (int i = 0; i <= ticks; i++) {
                if (willBeCritInTicks(i))
                    return true;

                if (smartCritsEnabled) {
                    PlayerSimulation sim = PlayerSimulation.simulateLocalPlayer(i);
                    if (sim.onGround)
                        return true;
                }
            }

            return false;
        }

        return canCritNow();
    }

    private boolean isLookingAtTarget(AttackExecutor.AttackPerpetratorConfigurable config) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = RotationManager.INSTANCE.getRotation().toVector();
        Vec3d endVec = eyePos.add(lookVec.multiply(config.getMaximumRange()));
        return config.getBox().raycast(eyePos, endVec).isPresent();
    }
}
