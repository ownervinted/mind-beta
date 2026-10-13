/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.PacketEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.rotations.CrystalRotation;
import mind.beta.modules.impl.combat.crystal.CrystalDamage;
import mind.beta.modules.impl.combat.crystal.CrystalPrediction;
import mind.beta.modules.impl.combat.crystal.CrystalRayTrace;
import mind.beta.modules.impl.combat.crystal.CrystalTracker;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.repository.friend.FriendUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AutoCrystal extends ModuleStructure {

    public static AutoCrystal getInstance() {
        return Instance.get(AutoCrystal.class);
    }

    private final BooleanSetting await = new BooleanSetting("Await", "Wait for server crystal confirmation")
            .setValue(true);

    private final SelectSetting sequential = new SelectSetting("Sequential")
            .value("Off", "Strict", "Strong")
            .selected("Strong");

    private final SelectSetting interact = new SelectSetting("Interact")
            .value("Default", "Strict")
            .selected("Strict");

    private final SelectSetting autoSwitch = new SelectSetting("Switch")
            .value("None", "Normal", "Silent")
            .selected("Normal");

    private final SelectSetting antiWeakness = new SelectSetting("Anti Weakness")
            .value("None", "Normal", "Silent")
            .selected("Silent");

    private final SelectSetting targetLogic = new SelectSetting("Target Logic")
            .value("Distance", "HP", "FOV")
            .selected("Distance");

    private final SliderSettings targetRange = new SliderSettings("Target Range", "Target search range")
            .range(1.0F, 15.0F)
            .setValue(10.0F);

    private final SliderSettings extrapolation = new SliderSettings("Extrapolation", "Maximum adaptive target prediction ticks")
            .range(0.0F, 5.0F)
            .setValue(2.0F);

    private final SliderSettings placeDelay = new SliderSettings("Place Delay", "Normal place delay in ticks")
            .range(0.0F, 20.0F)
            .setValue(0.0F);

    private final SliderSettings lowPlaceDelay = new SliderSettings("Low Place Delay", "Face place delay in ticks")
            .range(0.0F, 20.0F)
            .setValue(0.0F);

    private final SliderSettings placeRange = new SliderSettings("Place Range", "Crystal place range")
            .range(1.0F, 6.0F)
            .setValue(5.0F);

    private final SliderSettings placeWallRange = new SliderSettings("Place Wall Range", "Crystal place range through walls")
            .range(0.0F, 6.0F)
            .setValue(3.5F);

    private final BooleanSetting inhibit = new BooleanSetting("Inhibit", "Do not attack locally dead crystals")
            .setValue(true);

    private final SliderSettings breakDelay = new SliderSettings("Break Delay", "Normal break delay in ticks")
            .range(0.0F, 20.0F)
            .setValue(0.0F);

    private final SliderSettings lowBreakDelay = new SliderSettings("Low Break Delay", "Face place break delay in ticks")
            .range(0.0F, 20.0F)
            .setValue(0.0F);

    private final SliderSettings breakRange = new SliderSettings("Break Range", "Crystal break range")
            .range(1.0F, 6.0F)
            .setValue(5.0F);

    private final SliderSettings breakWallRange = new SliderSettings("Break Wall Range", "Crystal break range through walls")
            .range(0.0F, 6.0F)
            .setValue(3.5F);

    private final SliderSettings crystalAge = new SliderSettings("Crystal Age", "Minimum crystal age in ticks")
            .range(0.0F, 20.0F)
            .setValue(0.0F);

    private final SliderSettings minDamage = new SliderSettings("Min Damage", "Minimum target damage")
            .range(0.0F, 20.0F)
            .setValue(6.0F);

    private final SliderSettings maxSelfDamage = new SliderSettings("Max Self Damage", "Maximum allowed self/friend damage")
            .range(0.0F, 20.0F)
            .setValue(10.0F);

    private final BooleanSetting efficiency = new BooleanSetting("Efficiency", "Require damage to self-damage ratio")
            .setValue(false);

    private final SliderSettings efficiencyFactor = new SliderSettings("Efficiency Factor", "Required damage/self ratio")
            .range(0.1F, 5.0F)
            .setValue(1.0F);

    private final BooleanSetting protectFriends = new BooleanSetting("Protect Friends", "Treat friend damage as self damage")
            .setValue(true);

    private final BooleanSetting overrideSelfDamage = new BooleanSetting("Override Self Damage", "Allow lethal/pop overrides")
            .setValue(true);

    private final BooleanSetting sacrificeTotem = new BooleanSetting("Sacrifice Totem", "Allow own totem pop for a kill")
            .setValue(false);

    private final BooleanSetting armorBreaker = new BooleanSetting("Armor Breaker", "Face place low durability armor")
            .setValue(true);

    private final SliderSettings armorScale = new SliderSettings("Armor %", "Armor durability threshold")
            .range(0.0F, 40.0F)
            .setValue(5.0F);

    private final SliderSettings facePlaceHp = new SliderSettings("Face Place HP", "Ignore min damage below this HP")
            .range(0.0F, 20.0F)
            .setValue(5.0F);

    private final BooleanSetting shiftFacePlace = new BooleanSetting("Shift Face Place", "Left Shift ignores min damage")
            .setValue(true);

    private final BooleanSetting placeFailsafe = new BooleanSetting("Place Failsafe", "Block repeatedly failed positions")
            .setValue(true);

    private final BooleanSetting breakFailsafe = new BooleanSetting("Break Failsafe", "Block repeatedly failed crystals")
            .setValue(true);

    private final SliderSettings attempts = new SliderSettings("Max Attempts", "Failsafe attempt limit")
            .range(1.0F, 30.0F)
            .setValue(5.0F);

    private final BooleanSetting pauseEating = new BooleanSetting("Pause Eating", "Pause while using an item")
            .setValue(true);

    private final BooleanSetting pauseMining = new BooleanSetting("Pause Mining", "Pause while mining without offhand crystals")
            .setValue(true);

    private final SliderSettings pauseHp = new SliderSettings("Pause HP", "Pause below this health")
            .range(0.0F, 20.0F)
            .setValue(8.0F);

    private final BooleanSetting autoObsidian = new BooleanSetting("Auto Obsidian", "Create a better crystal base when useful")
            .setValue(true);

    private final SliderSettings baseMinDamageDelta = new SliderSettings("Base Damage Delta", "Required damage gain for obsidian base")
            .range(0.0F, 20.0F)
            .setValue(5.0F);

    private final BooleanSetting predictiveBreak = new BooleanSetting("Predictive Break", "Wait briefly for a better damage window")
            .setValue(true);

    private final SliderSettings breakWait = new SliderSettings("Break Wait", "Maximum damage-window wait in ticks")
            .range(0.0F, 4.0F).setValue(2.0F);

    private final BooleanSetting popPressure = new BooleanSetting("Pop Pressure", "Face place briefly after a confirmed totem pop")
            .setValue(true);

    private final SliderSettings safetyMargin = new SliderSettings("Safety Margin", "Health reserved after an explosion")
            .range(0.0F, 5.0F).setValue(1.0F);

    private final BooleanSetting rotate = new BooleanSetting("Rotation", "Use RotationManager")
            .setValue(true);

    private final CrystalRotation crystalRotation = new CrystalRotation();
    private final RotationRequest rotationRequest = new RotationRequest(crystalRotation, true, false);
    private final RotationConfig rotationConfig = new RotationConfig(
            78.0F,
            66.0F,
            54.0F,
            92.0F,
            68.0F,
            0.72F,
            0.66F,
            0.96F
    );

    private final CrystalTracker crystalTracker = new CrystalTracker();

    private PlayerEntity target;
    private PlaceData currentData;
    private PlaceData bestPlaceData;
    private EndCrystalEntity bestCrystal;
    private ObsidianData bestObsidian;

    private Action action = Action.NONE;
    private Vec3d rotationPoint;

    private int placeDelayTicks;
    private int breakDelayTicks;
    private final CrystalPrediction prediction = new CrystalPrediction();
    private final Map<Integer, Long> popTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private int waitingCrystalId = -1;
    private int waitingSince;
    private Object activeWorld;
    private boolean rotationConfigured;
    private boolean facePlacing;

    private long confirmTime;
    private long calcTime;

    public float renderDamage;
    public float renderSelfDamage;

    private State currentState = State.NoTarget;

    public AutoCrystal() {
        super("AutoCrystal", "Auto Crystal", ModuleCategory.COMBAT);
        settings(
                await,
                sequential,
                interact,
                autoSwitch,
                antiWeakness,
                targetLogic,
                targetRange,
                extrapolation,
                placeDelay,
                lowPlaceDelay,
                placeRange,
                placeWallRange,
                inhibit,
                breakDelay,
                lowBreakDelay,
                breakRange,
                breakWallRange,
                crystalAge,
                minDamage,
                maxSelfDamage,
                efficiency,
                efficiencyFactor,
                protectFriends,
                overrideSelfDamage,
                sacrificeTotem,
                armorBreaker,
                armorScale,
                facePlaceHp,
                shiftFacePlace,
                placeFailsafe,
                breakFailsafe,
                attempts,
                pauseEating,
                pauseMining,
                pauseHp,
                autoObsidian,
                baseMinDamageDelta,
                predictiveBreak,
                breakWait,
                popPressure,
                safetyMargin,
                rotate
        );
    }

    @Override
    public void activate() {
        resetState();
    }

    @Override
    public void deactivate() {
        resetState();
        if (rotate.isValue()) {
            RotationManager.INSTANCE.startReturning(this);
        }
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            if (activeWorld != mc.world) {
                resetState();
            }
            CrystalDamage.clearCache();
            prediction.update();
            tickTimers();
            crystalTracker.update(getPing());
            popTimes.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 2000L);
            updateTarget();
            processSpawnConfirmations();
            calculateCombatData();
            resolveAction();

            if (rotate.isValue() && rotationPoint != null && target != null && !shouldPause()) {
                requestRotation(rotationPoint);
            }
            return;
        }

        if (event.getType() == EventType.POST) {
            CrystalDamage.clearCache();
            executeAction();
        }
    }

    private void resetState() {
        target = null;
        currentData = null;
        bestPlaceData = null;
        bestCrystal = null;
        bestObsidian = null;
        action = Action.NONE;
        rotationPoint = null;
        placeDelayTicks = 0;
        breakDelayTicks = 0;
        CrystalDamage.clearCache();
        rotationConfigured = false;
        facePlacing = false;
        confirmTime = 0L;
        calcTime = 0L;
        prediction.reset();
        popTimes.clear();
        waitingCrystalId = -1;
        activeWorld = mc.world;
        renderDamage = 0.0F;
        renderSelfDamage = 0.0F;
        currentState = State.NoTarget;
        crystalTracker.reset();
    }

    private void tickTimers() {
        if (placeDelayTicks > 0) {
            placeDelayTicks--;
        }
        if (breakDelayTicks > 0) {
            breakDelayTicks--;
        }
    }

    private void updateTarget() {
        PlayerEntity best = null;
        double bestValue = Double.MAX_VALUE;
        double maxDistanceSq = targetRange.getValue() * targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()
                    || player.getAbilities().creativeMode || FriendUtils.isFriend(player)) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(player);
            if (distance > maxDistanceSq) {
                continue;
            }

            double value;
            if (targetLogic.isSelected("HP")) {
                value = getTotalHealth(player);
            } else if (targetLogic.isSelected("FOV")) {
                value = getFovDifference(player);
            } else {
                value = distance;
            }

            if (value >= bestValue) {
                continue;
            }

            bestValue = value;
            best = player;
        }

        if (target != best) {
            bestPlaceData = null;
            bestCrystal = null;
            bestObsidian = null;
            waitingCrystalId = -1;
            currentData = null;
        }

        target = best;
        if (target == null) {
            currentState = State.NoTarget;
        }
    }

    private void processSpawnConfirmations() {
        Set<Integer> aliveIds = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) {
                continue;
            }

            aliveIds.add(crystal.getId());
            if (!crystalTracker.markSeen(crystal)) {
                continue;
            }

            BlockPos confirmedBase = null;
            CrystalTracker.Attempt confirmedAttempt = null;

            for (Map.Entry<BlockPos, CrystalTracker.Attempt> entry : crystalTracker.getAwaitingPositions().entrySet()) {
                Vec3d expected = crystalPosition(entry.getKey());
                if (crystal.getEntityPos().squaredDistanceTo(expected) <= 0.75) {
                    confirmedBase = entry.getKey();
                    confirmedAttempt = entry.getValue();
                    break;
                }
            }

            if (confirmedBase != null) {
                crystalTracker.confirmSpawn(confirmedBase);
                if (confirmedAttempt != null) {
                    confirmTime = System.currentTimeMillis() - confirmedAttempt.getTime();
                }
            }
        }

        crystalTracker.retainSeen(aliveIds);
    }

    private void calculateCombatData() {
        renderDamage = 0.0F;
        renderSelfDamage = 0.0F;
        bestPlaceData = null;
        bestCrystal = null;
        bestObsidian = null;
        currentData = null;
        facePlacing = false;
        if (target == null || !target.isAlive() || shouldPause()) {
            return;
        }
        long start = System.nanoTime();
        bestCrystal = scanBestCrystal();
        bestPlaceData = scanBestPlace();
        if (autoObsidian.isValue() && bestCrystal == null) {
            bestObsidian = scanBestObsidian();
        }
        calcTime = (System.nanoTime() - start) / 1_000_000L;
    }

    private PlaceData scanBestPlace() {
        Vec3d center = mc.player.getEntityPos();
        BlockPos playerPos = BlockPos.ofFloored(center);
        int radius = (int) Math.ceil(effectivePlaceRange());
        PlaceData best = null;

        Vec3d predictedSelf = predictPosition(mc.player, getSelfPredictTicks());
        Vec3d predictedTarget = predictPosition(target, (int) extrapolation.getValue());

        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int y = playerPos.getY() - radius; y <= playerPos.getY() + radius; y++) {
                for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                    PlaceData data = getPlaceData(new BlockPos(x, y, z), predictedSelf, predictedTarget);
                    if (data == null || !isSafe(data.damage(), data.selfDamage(), data.overrideDamage())) {
                        continue;
                    }
                    best = chooseBetterPlace(best, data);
                }
            }
        }

        if (best != null) {
            currentData = best;
            renderDamage = best.damage();
            renderSelfDamage = best.selfDamage();
            facePlacing = best.damage() < minDamage.getValue();
        } else {
            currentData = null;
            facePlacing = false;
        }

        return best;
    }

    private PlaceData getPlaceData(BlockPos base, Vec3d predictedSelf, Vec3d predictedTarget) {
        if ((await.isValue() && !sequential.isSelected("Off") && crystalTracker.isAwaiting(base))
                || (placeFailsafe.isValue() && crystalTracker.isPositionBlocked(base, (int) attempts.getValue(), getPing()))) {
            return null;
        }

        Block block = mc.world.getBlockState(base).getBlock();
        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
            return null;
        }

        if (!mc.world.isAir(base.up())) {
            return null;
        }

        if (isPositionBlockedByEntity(base)) {
            return null;
        }

        Vec3d crystalPos = crystalPosition(base);
        if (!isUsefulCrystalHeight(crystalPos)
                || target.getEntityPos().squaredDistanceTo(crystalPos) > 144.0) {
            return null;
        }

        if (!predictCrystalReach(crystalPos, predictedSelf)) {
            return null;
        }

        BlockHitResult hit = CrystalRayTrace.findCrystalPlaceHit(base, effectivePlaceRange(),
                placeWallRange.getValue(), interact.isSelected("Strict"));
        if (hit == null) {
            return null;
        }
        float damage = plannedDamage(crystalPos, predictedTarget, null);
        float selfDamage = protectedDamage(crystalPos, predictedSelf, null);
        boolean override = shouldOverrideMaxSelfDamage(CrystalDamage.calculate(crystalPos, target), selfDamage);
        if (damage < 1.5F || !isSafe(damage, selfDamage, override)
                || !friendsSafe(crystalPos, null)) {
            return null;
        }

        return new PlaceData(base, hit, damage, selfDamage, override);
    }

    private EndCrystalEntity scanBestCrystal() {
        CrystalData best = null;
        Vec3d predictedSelf = predictPosition(mc.player, getSelfPredictTicks());
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) {
                continue;
            }
            CrystalData data = getCrystalData(crystal, predictedSelf);
            if (data != null && isSafe(data.damage(), data.selfDamage(), data.overrideDamage())) {
                best = chooseBetterCrystal(best, data);
            }
        }
        return best == null ? null : best.crystal();
    }

    private CrystalData getCrystalData(EndCrystalEntity crystal, Vec3d predictedSelf) {
        if (!crystal.isAlive()) {
            return null;
        }

        if (crystal.age < (int) crystalAge.getValue()) {
            return null;
        }

        if (inhibit.isValue() && crystalTracker.isDead(crystal.getId())) {
            return null;
        }

        if (breakFailsafe.isValue() && crystalTracker.isCrystalBlocked(crystal.getId(), (int) attempts.getValue(), getPing())) {
            return null;
        }

        Vec3d center = crystal.getBoundingBox().getCenter();
        double distanceSq = distanceToBoxSquared(mc.player.getEyePos(), crystal.getBoundingBox());
        boolean visible = CrystalRayTrace.isVisible(center);
        double allowed = effectiveBreakRange(visible);

        if (distanceSq > allowed * allowed) {
            return null;
        }

        float damage = CrystalDamage.calculate(crystal.getEntityPos(), target);
        float selfDamage = protectedDamage(crystal.getEntityPos(), predictedSelf, null);
        boolean override = shouldOverrideMaxSelfDamage(damage, selfDamage);
        if (damage < 1.5F || !isSafe(damage, selfDamage, override)
                || !friendsSafe(crystal.getEntityPos(), null)) {
            return null;
        }

        return new CrystalData(crystal, damage, selfDamage, override);
    }

    private PlaceData chooseBetterPlace(PlaceData best, PlaceData candidate) {
        if (!passesMinDamage(candidate.damage())) {
            return best;
        }
        return best == null || score(candidate.damage(), candidate.selfDamage(), crystalPosition(candidate.base()))
                > score(best.damage(), best.selfDamage(), crystalPosition(best.base())) ? candidate : best;
    }

    private CrystalData chooseBetterCrystal(CrystalData best, CrystalData candidate) {
        if (!passesMinDamage(candidate.damage())) {
            return best;
        }
        return best == null || score(candidate.damage(), candidate.selfDamage(), candidate.crystal().getEntityPos())
                > score(best.damage(), best.selfDamage(), best.crystal().getEntityPos()) ? candidate : best;
    }

    private boolean passesMinDamage(float damage) {
        return damage >= minDamage.getValue() || shouldOverrideMinDamage(damage);
    }

    private boolean isSafe(float damage, float selfDamage, boolean override) {
        boolean lethalSelf = selfDamage + safetyMargin.getValue() >= getTotalHealth(mc.player);
        if (lethalSelf && !(override && sacrificeTotem.isValue() && isHoldingTotem(mc.player)
                && damage >= getTotalHealth(target) && !isHoldingTotem(target))) {
            return false;
        }
        if (selfDamage > maxSelfDamage.getValue() && !override) {
            return false;
        }
        return !efficiency.isValue() || override || selfDamage <= 0.01F
                || damage / selfDamage >= efficiencyFactor.getValue();
    }

    private boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
        if (!overrideSelfDamage.isValue() || target == null) {
            return false;
        }

        boolean targetSafe = isHoldingTotem(target);
        boolean playerSafe = isHoldingTotem(mc.player);

        float targetHp = getTotalHealth(target);
        float playerHp = getTotalHealth(mc.player);

        boolean canPop = damage >= targetHp && targetSafe;
        boolean canKill = damage >= targetHp && !targetSafe;
        boolean canPopSelf = selfDamage + safetyMargin.getValue() >= playerHp && playerSafe;
        boolean canKillSelf = selfDamage + safetyMargin.getValue() >= playerHp && !playerSafe;

        if (canPopSelf && canKill && sacrificeTotem.isValue()) {
            return true;
        }

        return selfDamage > maxSelfDamage.getValue()
                && (canPop || canKill)
                && !canKillSelf
                && !canPopSelf;
    }

    private boolean shouldOverrideMinDamage(float damage) {
        if (target == null) {
            return false;
        }

        if (shiftFacePlace.isValue()
                && GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            return true;
        }

        if (getTotalHealth(target) - damage <= 0.0F) {
            return true;
        }

        if (isPopWindow()) {
            return true;
        }

        if (armorBreaker.isValue()) {
            EquipmentSlot[] armorSlots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };

            for (EquipmentSlot slot : armorSlots) {
                ItemStack armor = target.getEquippedStack(slot);
                if (armor == null || armor.isEmpty() || !armor.isDamageable()) {
                    continue;
                }

                float durability = ((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage()) * 100.0F;
                if (durability < armorScale.getValue()) {
                    return true;
                }
            }
        }

        return getTotalHealth(target) <= facePlaceHp.getValue();
    }

    private boolean predictCrystalReach(Vec3d crystalPos, Vec3d predictedSelf) {
        Vec3d eyes = predictedSelf.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        Box crystalBox = new Box(crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0,
                crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0);
        double distanceSq = distanceToBoxSquared(eyes, crystalBox);
        boolean visible = CrystalRayTrace.isVisible(crystalPos);
        double range = effectiveBreakRange(visible);
        return distanceSq <= range * range;
    }

    private boolean canAttackCrystal(EndCrystalEntity crystal) {
        if (target == null || crystal == null) {
            return false;
        }

        Vec3d predictedSelf = predictPosition(mc.player, getSelfPredictTicks());
        CrystalData data = getCrystalData(crystal, predictedSelf);
        return data != null && isSafe(data.damage(), data.selfDamage(), data.overrideDamage()) && passesMinDamage(data.damage());
    }

    private boolean isPositionBlockedByEntity(BlockPos base) {
        Box box = new Box(base.getX(), base.getY() + 1.0, base.getZ(),
                base.getX() + 1.0, base.getY() + 3.0, base.getZ() + 1.0);
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive() && !entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPositionBlockedByCrystal(BlockPos base) {
        Box box = new Box(base.getX(), base.getY() + 1.0, base.getZ(),
                base.getX() + 1.0, base.getY() + 3.0, base.getZ() + 1.0);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal
                    && crystal.isAlive()
                    && crystal.getBoundingBox().intersects(box)
                    && !crystalTracker.isDead(crystal.getId())) {
                return true;
            }
        }

        return false;
    }

    private ObsidianData scanBestObsidian() {
        if (findHotbarSlot(Items.OBSIDIAN) == -1 && mc.player.getOffHandStack().getItem() != Items.OBSIDIAN) {
            return null;
        }
        BlockPos center = BlockPos.ofFloored(mc.player.getEntityPos());
        int radius = (int) Math.ceil(effectivePlaceRange());
        Vec3d predictedSelf = predictPosition(mc.player, getSelfPredictTicks());
        Vec3d predictedTarget = predictPosition(target, (int) extrapolation.getValue());
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Vec3d explosion = crystalPosition(pos);
                    if (!isUsefulCrystalHeight(explosion)
                            || explosion.squaredDistanceTo(predictedTarget) > 36.0
                            || mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > Math.pow(effectivePlaceRange() + 0.9, 2.0)
                            || !mc.world.getBlockState(pos).isReplaceable() || !mc.world.isAir(pos.up())) {
                        continue;
                    }
                    candidates.add(pos);
                }
            }
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> crystalPosition(pos).squaredDistanceTo(predictedTarget)
                + mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) * 0.15));
        ObsidianData best = null;
        int checked = 0;
        float currentDamage = bestPlaceData == null ? 0.0F : bestPlaceData.damage();
        for (BlockPos pos : candidates) {
            if (!canPlaceObsidianAt(pos) || !predictCrystalReach(crystalPosition(pos), predictedSelf)) {
                continue;
            }
            if (++checked > 32) {
                break;
            }
            BlockHitResult hit = CrystalRayTrace.findBlockPlacementHit(pos, effectivePlaceRange());
            if (hit == null) {
                continue;
            }
            Vec3d explosion = crystalPosition(pos);
            float damage = plannedDamage(explosion, predictedTarget, pos);
            if (damage < Math.max(1.5F, currentDamage + baseMinDamageDelta.getValue())) {
                continue;
            }
            float self = protectedDamage(explosion, predictedSelf, pos);
            boolean override = shouldOverrideMaxSelfDamage(
                    CrystalDamage.calculate(explosion, target, target.getEntityPos(), pos), self);
            if (isSafe(damage, self, override) && friendsSafe(explosion, pos)) {
                best = chooseBetterObsidian(best, new ObsidianData(pos, hit, damage, self, override));
            }
        }
        return best;
    }

    private ObsidianData chooseBetterObsidian(ObsidianData best, ObsidianData candidate) {
        if (!passesMinDamage(candidate.damage())) {
            return best;
        }
        return best == null || score(candidate.damage(), candidate.selfDamage(), crystalPosition(candidate.pos()))
                > score(best.damage(), best.selfDamage(), crystalPosition(best.pos())) ? candidate : best;
    }

    private void resolveAction() {
        action = Action.NONE;
        rotationPoint = null;
        if (target == null || shouldPause()) {
            return;
        }
        if (bestCrystal != null && breakDelayTicks <= 0) {
            if (waitForDamageWindow(bestCrystal)) {
                rotationPoint = getCrystalAimPoint(bestCrystal);
                return;
            }
            action = Action.BREAK;
            rotationPoint = getCrystalAimPoint(bestCrystal);
            renderDamage = CrystalDamage.calculate(bestCrystal.getEntityPos(), target);
            renderSelfDamage = protectedDamage(bestCrystal.getEntityPos(), predictPosition(mc.player, getSelfPredictTicks()), null);
            facePlacing = renderDamage < minDamage.getValue();
            return;
        }
        boolean waiting = await.isValue() && !sequential.isSelected("Off")
                && (sequential.isSelected("Strict") ? crystalTracker.hasAwaiting() : bestPlaceData != null
                && crystalTracker.isAwaiting(bestPlaceData.base()));
        if (bestPlaceData != null && placeDelayTicks <= 0 && !waiting) {
            action = Action.PLACE_CRYSTAL;
            rotationPoint = bestPlaceData.hit().getPos();
            return;
        }
        if (bestObsidian != null && placeDelayTicks <= 0 && !crystalTracker.hasAwaiting()) {
            action = Action.PLACE_OBSIDIAN;
            rotationPoint = bestObsidian.hit().getPos();
            renderDamage = bestObsidian.damage();
            renderSelfDamage = bestObsidian.selfDamage();
        }
    }

    private void executeAction() {
        if (target == null || action == Action.NONE || shouldPause()) {
            return;
        }

        switch (action) {
            case BREAK -> executeBreak();
            case PLACE_CRYSTAL -> executeCrystalPlace();
            case PLACE_OBSIDIAN -> executeObsidianPlace();
            default -> {
            }
        }
    }

    private void executeBreak() {
        EndCrystalEntity crystal = bestCrystal;
        if (crystal == null || !canAttackCrystal(crystal)) {
            return;
        }

        Vec3d center = crystal.getBoundingBox().getCenter();
        boolean visible = CrystalRayTrace.isVisible(center);
        double range = effectiveBreakRange(visible);

        if (distanceToBoxSquared(mc.player.getEyePos(), crystal.getBoundingBox()) > range * range) {
            return;
        }

        Rotation applied = rotate.isValue() ? RotationManager.INSTANCE.getRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());
        if (!CrystalRayTrace.canHitCrystalVisible(crystal, applied, range, visible)) {
            return;
        }

        int oldSlot = prepareAntiWeakness();
        if (oldSlot == -2) {
            return;
        }
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        crystalTracker.onAttack(crystal, breakFailsafe.isValue());

        if (oldSlot != -1 && antiWeakness.isSelected("Silent")) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }

        breakDelayTicks = facePlacing ? (int) lowBreakDelay.getValue() : (int) breakDelay.getValue();
        waitingCrystalId = -1;

        if (sequential.isSelected("Strong")) {
            CrystalDamage.clearCache();
            bestPlaceData = null;
        }
    }

    private void executeCrystalPlace() {
        PlaceData data = bestPlaceData;
        if (data == null) {
            return;
        }

        PlaceData fresh = getPlaceData(data.base(), predictPosition(mc.player, getSelfPredictTicks()),
                predictPosition(target, (int) extrapolation.getValue()));
        if (fresh == null || !passesMinDamage(fresh.damage())) {
            return;
        }
        data = fresh;
        if (!isCrystalBase(data.base())) {
            return;
        }

        if (isPositionBlockedByEntity(data.base())) {
            return;
        }

        SwitchResult switchResult = prepareItem(Items.END_CRYSTAL, autoSwitch);
        if (switchResult.hand() == null) {
            currentState = State.NoCrystals;
            return;
        }

        BlockHitResult hit = data.hit();
        if (!rotationReadyForBlock(hit)) {
            restoreSilentSwitch(switchResult, autoSwitch);
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, switchResult.hand(), hit);
        if (result.isAccepted()) {
            mc.player.swingHand(switchResult.hand());
            boolean physicallyBlocked = isPositionBlockedByCrystal(data.base());
            crystalTracker.addAwaiting(data.base(), physicallyBlocked, placeFailsafe.isValue());
            placeDelayTicks = facePlacing ? (int) lowPlaceDelay.getValue() : (int) placeDelay.getValue();
        }

        restoreSilentSwitch(switchResult, autoSwitch);
    }

    private void executeObsidianPlace() {
        ObsidianData data = bestObsidian;
        if (data == null || !canPlaceObsidianAt(data.pos())) {
            return;
        }

        Vec3d explosion = crystalPosition(data.pos());
        if (!isUsefulCrystalHeight(explosion)) {
            return;
        }
        float damage = plannedDamage(explosion, predictPosition(target, (int) extrapolation.getValue()), data.pos());
        float self = protectedDamage(explosion, predictPosition(mc.player, getSelfPredictTicks()), data.pos());
        if (!passesMinDamage(damage) || !isSafe(damage, self, shouldOverrideMaxSelfDamage(damage, self))
                || !friendsSafe(explosion, data.pos())) {
            return;
        }

        SwitchResult switchResult = prepareItem(Items.OBSIDIAN, autoSwitch);
        if (switchResult.hand() == null) {
            return;
        }

        BlockHitResult hit = data.hit();
        if (!rotationReadyForBlock(hit)) {
            restoreSilentSwitch(switchResult, autoSwitch);
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, switchResult.hand(), hit);
        if (result.isAccepted()) {
            mc.player.swingHand(switchResult.hand());
            placeDelayTicks = facePlacing ? (int) lowPlaceDelay.getValue() : (int) placeDelay.getValue();
            CrystalDamage.clearCache();
        }

        restoreSilentSwitch(switchResult, autoSwitch);
    }

    private boolean rotationReadyForBlock(BlockHitResult expected) {
        if (interact.isSelected("Strict") || CrystalRayTrace.isVisible(expected.getPos())) {
            BlockHitResult actual;
            if (rotate.isValue()) {
                actual = CrystalRayTrace.raycastCurrentRotation(effectivePlaceRange());
            } else {
                Vec3d eyes = mc.player.getEyePos();
                actual = mc.world.raycast(new net.minecraft.world.RaycastContext(eyes,
                        eyes.add(new Rotation(mc.player.getYaw(), mc.player.getPitch()).toVector().multiply(effectivePlaceRange())),
                        net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
            }
            return CrystalRayTrace.matches(actual, expected);
        }

        Vec3d eyes = mc.player.getEyePos();
        Vec3d wanted = expected.getPos().subtract(eyes).normalize();
        Vec3d current = (rotate.isValue() ? RotationManager.INSTANCE.getRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch())).toVector().normalize();
        return wanted.dotProduct(current) >= 0.995;
    }

    private void requestRotation(Vec3d point) {
        if (!rotationConfigured) {
            RotationManager.INSTANCE.setRotationConfig(rotationConfig);
            rotationConfigured = true;
        }

        Rotation rotation = RotationUtils.calculateAngle(point);
        RotationManager.INSTANCE.rotateTo(
                new Rotation.VecRotation(rotation, rotation.toVector()),
                target,
                2,
                rotationRequest,
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }

    private Vec3d getCrystalAimPoint(EndCrystalEntity crystal) {
        Vec3d center = crystal.getBoundingBox().getCenter();
        BlockPos base = crystal.getBlockPos().down();
        Vec3d baseTop = base.toCenterPos().add(0.0, 0.5, 0.0);
        Rotation rotation = RotationUtils.calculateAngle(baseTop);

        if (CrystalRayTrace.canHitCrystal(crystal, rotation, effectiveBreakRange(CrystalRayTrace.isVisible(baseTop)))) {
            return baseTop;
        }

        return center;
    }

    private boolean shouldPause() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            currentState = State.ExternalPause;
            return true;
        }

        boolean offhandCrystal = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);

        if (target == null) {
            currentState = State.NoTarget;
            return true;
        }

        if (pauseMining.isValue() && mc.interactionManager.isBreakingBlock() && !offhandCrystal) {
            currentState = State.Mining;
            return true;
        }

        if (pauseEating.isValue() && mc.player.isUsingItem()) {
            currentState = State.Eating;
            return true;
        }

        if (getTotalHealth(mc.player) < pauseHp.getValue()) {
            currentState = State.LowHP;
            return true;
        }

        currentState = State.Active;
        return false;
    }

    private int prepareAntiWeakness() {
        if (antiWeakness.isSelected("None")) {
            return -1;
        }

        StatusEffectInstance weaknessEffect = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
        StatusEffectInstance strengthEffect = mc.player.getStatusEffect(StatusEffects.STRENGTH);

        if (weaknessEffect == null
                || (strengthEffect != null && strengthEffect.getAmplifier() >= weaknessEffect.getAmplifier())) {
            return -1;
        }

        int slot = findAntiWeaknessSlot();
        if (slot == -1) {
            return -2;
        }

        int old = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        return old;
    }

    private int findAntiWeaknessSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isIn(ItemTags.SWORDS)
                    || stack.isIn(ItemTags.PICKAXES)
                    || stack.isIn(ItemTags.AXES)
                    || stack.isIn(ItemTags.SHOVELS)) {
                return slot;
            }
        }
        return -1;
    }

    private SwitchResult prepareItem(Item item, SelectSetting switchSetting) {
        if (mc.player.getOffHandStack().getItem() == item) {
            return new SwitchResult(Hand.OFF_HAND, -1);
        }

        if (mc.player.getMainHandStack().getItem() == item) {
            return new SwitchResult(Hand.MAIN_HAND, -1);
        }

        if (switchSetting.isSelected("None")) {
            return new SwitchResult(null, -1);
        }

        int slot = findHotbarSlot(item);
        if (slot == -1) {
            return new SwitchResult(null, -1);
        }

        int old = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        return new SwitchResult(Hand.MAIN_HAND, old);
    }

    private void restoreSilentSwitch(SwitchResult result, SelectSetting switchSetting) {
        if (result.oldSlot() != -1 && switchSetting.isSelected("Silent")) {
            mc.player.getInventory().setSelectedSlot(result.oldSlot());
        }
    }

    private int findHotbarSlot(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == item) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canPlaceObsidianAt(BlockPos pos) {
        if (!mc.world.isInHeightLimit(pos.getY()) || !mc.world.getWorldBorder().contains(pos)
                || !mc.world.getBlockState(pos).isReplaceable() || !mc.world.isAir(pos.up())
                || isPositionBlockedByEntity(pos)) {
            return false;
        }

        Box box = new Box(pos);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.getBoundingBox().intersects(box)) {
                continue;
            }
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isCrystalBase(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private boolean isHoldingTotem(PlayerEntity player) {
        return player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING
                || player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;
    }

    private float getTotalHealth(PlayerEntity player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }

    private Vec3d predictPosition(Entity entity, int ticks) {
        return prediction.predict(entity, Math.min(5, ticks));
    }


    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.isSend() || !(event.getPacket() instanceof EntityStatusS2CPacket packet)
                || packet.getStatus() != 35 || mc.world == null) {
            return;
        }
        Entity entity = packet.getEntity(mc.world);
        if (entity instanceof PlayerEntity player) {
            popTimes.put(player.getId(), System.currentTimeMillis());
        }
    }

    private boolean isPopWindow() {
        if (!popPressure.isValue() || target == null) {
            return false;
        }
        Long time = popTimes.get(target.getId());
        return time != null && System.currentTimeMillis() - time < 700L;
    }

    private boolean isUsefulCrystalHeight(Vec3d explosion) {
        if (target == null || mc.player == null) {
            return false;
        }
        double targetFeet = target.getBoundingBox().minY;
        double playerFeet = mc.player.getBoundingBox().minY;
        if (targetFeet - playerFeet >= 0.75) {
            return explosion.y <= Math.min(targetFeet - 0.5, playerFeet + 0.15);
        }
        return explosion.y <= targetFeet + 0.05;
    }

    private Vec3d crystalPosition(BlockPos base) {
        return new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
    }

    private float plannedDamage(Vec3d pos, Vec3d predicted, BlockPos virtualBase) {
        float current = CrystalDamage.calculate(pos, target, target.getEntityPos(), virtualBase);
        float future = CrystalDamage.calculate(pos, target, predicted, virtualBase);
        return current * 0.35F + future * 0.65F;
    }

    private float protectedDamage(Vec3d pos, Vec3d predicted, BlockPos virtualBase) {
        return Math.max(CrystalDamage.calculate(pos, mc.player, mc.player.getEntityPos(), virtualBase),
                CrystalDamage.calculate(pos, mc.player, predicted, virtualBase));
    }

    private boolean friendsSafe(Vec3d pos, BlockPos virtualBase) {
        if (!protectFriends.isValue()) {
            return true;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || !FriendUtils.isFriend(player)) {
                continue;
            }
            float damage = Math.max(CrystalDamage.calculate(pos, player, player.getEntityPos(), virtualBase),
                    CrystalDamage.calculate(pos, player, predictPosition(player, getSelfPredictTicks()), virtualBase));
            if (damage > maxSelfDamage.getValue() || damage + safetyMargin.getValue() >= getTotalHealth(player)) {
                return false;
            }
        }
        return true;
    }

    private double score(float damage, float selfDamage, Vec3d pos) {
        float health = getTotalHealth(target);
        boolean lethal = damage >= health;
        double safetyWeight = getTotalHealth(mc.player) <= 12.0F ? 3.5 : 2.5;
        double score = Math.min(damage, health + 4.0F) * 3.0 - selfDamage * safetyWeight
                - mc.player.getEyePos().distanceTo(pos) * 0.2;
        if (lethal) {
            score += isHoldingTotem(target) ? 18.0 : 30.0;
        }
        return score;
    }

    private boolean waitForDamageWindow(EndCrystalEntity crystal) {
        if (!predictiveBreak.isValue() || breakWait.getValue() <= 0.0F) {
            return false;
        }
        float current = CrystalDamage.calculate(crystal.getEntityPos(), target);
        if (current >= getTotalHealth(target) || isPopWindow() || shouldOverrideMinDamage(current)) {
            waitingCrystalId = -1;
            return false;
        }
        float future = CrystalDamage.calculate(crystal.getEntityPos(), target,
                predictPosition(target, Math.max(1, (int) extrapolation.getValue())));
        if (future < current + 2.0F) {
            waitingCrystalId = -1;
            return false;
        }
        if (waitingCrystalId != crystal.getId()) {
            waitingCrystalId = crystal.getId();
            waitingSince = mc.player.age;
        }
        return mc.player.age - waitingSince < (int) breakWait.getValue();
    }

    private double getFovDifference(PlayerEntity player) {
        Vec3d delta = player.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        return Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
    }

    private double effectivePlaceRange() {
        return Math.min(placeRange.getValue(), mc.player.getBlockInteractionRange());
    }

    private double effectiveBreakRange(boolean visible) {
        return Math.min(visible ? breakRange.getValue() : breakWallRange.getValue(), mc.player.getEntityInteractionRange());
    }

    private double distanceToBoxSquared(Vec3d point, Box box) {
        Vec3d nearest = new Vec3d(MathHelper.clamp(point.x, box.minX, box.maxX),
                MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ));
        return point.squaredDistanceTo(nearest);
    }

    private int getSelfPredictTicks() {
        return Math.min(3, (int) Math.ceil(getPing() / 100.0));
    }

    private long getPing() {
        try {
            if (mc.getNetworkHandler() == null || mc.player == null) {
                return 0L;
            }

            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            return entry == null ? 0L : Math.max(0, entry.getLatency());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public PlayerEntity getTarget() {
        return target;
    }

    public PlaceData getCurrentData() {
        return currentData;
    }

    public long getCalcTime() {
        return calcTime;
    }

    public long getConfirmTime() {
        return confirmTime;
    }

    public String getPauseState() {
        return currentState.name();
    }

    public record PlaceData(
            BlockPos base,
            BlockHitResult hit,
            float damage,
            float selfDamage,
            boolean overrideDamage
    ) {
    }

    private record CrystalData(
            EndCrystalEntity crystal,
            float damage,
            float selfDamage,
            boolean overrideDamage
    ) {
    }

    private record ObsidianData(
            BlockPos pos,
            BlockHitResult hit,
            float damage,
            float selfDamage,
            boolean overrideDamage
    ) {
    }

    private record SwitchResult(Hand hand, int oldSlot) {
    }

    private enum Action {
        NONE,
        BREAK,
        PLACE_CRYSTAL,
        PLACE_OBSIDIAN
    }

    public enum State {
        Active,
        Eating,
        LowHP,
        NoTarget,
        NoCrystals,
        ExternalPause,
        Mining
    }
}
