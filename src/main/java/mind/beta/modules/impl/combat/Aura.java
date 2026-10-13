/* Copyright Mind */

package mind.beta.modules.impl.combat;

import lombok.Getter;
import lombok.experimental.NonFinal;
import mind.beta.modules.impl.combat.aura.*;
import mind.beta.modules.impl.combat.aura.rotations.MatrixRotation;
import mind.beta.modules.impl.combat.aura.rotations.ExtendedRotation;
import mind.beta.modules.impl.combat.aura.rotations.BehavioralRotation;
import mind.beta.modules.impl.combat.aura.rotations.PolarRotation;
import mind.beta.modules.impl.combat.aura.rotations.CustomRotation;
import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import mind.beta.modules.impl.combat.aura.custom.RotationBuilderSettings;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Pair;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.impl.combat.aura.mace.MaceResolver;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import mind.beta.modules.impl.combat.aura.target.MultiPoint;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.impl.misc.ClickPearl;
import mind.beta.modules.impl.movement.ElytraTarget;
import mind.beta.modules.impl.movement.Scaffold;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.scripting.extensions.LuaExtensionRegistry;
import mind.beta.util.Instance;
import mind.beta.util.render.Render3D;
import mind.beta.util.math.TaskPriority;

import java.util.Objects;

public class Aura extends ModuleStructure {

    private static boolean jumpBlockWaitingForGround;
    private static long jumpBlockUntilMs;

    public static boolean shouldBlockJumpAfterDeactivate() {
        if (mc.player == null) {
            jumpBlockWaitingForGround = false;
            jumpBlockUntilMs = 0L;
            return false;
        }

        long now = System.currentTimeMillis();

        if (jumpBlockWaitingForGround) {
            if (!mc.player.isOnGround()) {
                return false;
            }

            jumpBlockWaitingForGround = false;
            jumpBlockUntilMs = now + 150L;
        }

        if (jumpBlockUntilMs <= 0L) {
            return false;
        }

        if (now >= jumpBlockUntilMs) {
            jumpBlockUntilMs = 0L;
            return false;
        }

        return true;
    }

    public static Aura getInstance() {
        return Instance.get(Aura.class);
    }

    public boolean isFoodUseSuspended() {
        return foodUseSuspended;
    }

    private boolean isFoodResumeDelayActive() {
        return mc.player != null
                && foodResumeAfterAge != Integer.MIN_VALUE
                && mc.player.age <= foodResumeAfterAge;
    }

    private boolean restoreSprintAfterReturn;
    private boolean foodUseSuspended;
    private int foodResumeAfterAge = Integer.MIN_VALUE;

    private int foodUseArmAge = Integer.MIN_VALUE;
    private boolean foodUseInputPrepared;
    private int foodReturnSettledAge = Integer.MIN_VALUE;


    private final SelectSetting mode = new SelectSetting("Rotation")
            .value("Matrix", "Extended", "Behavioral", "Polar", "Custom")
            .selected("Matrix");

    public boolean isCustomRotationSelected() { return mode.isSelected("Custom"); }

    private final RotationBuilderSettings customSettings = new RotationBuilderSettings(this::isCustomRotationSelected);

    public void syncCustomRotationSettings() { customSettings.sync(); }

    public void refreshCustomRotationSettings() { customSettings.applyProfile(); }
    public void reloadCustomRotationSettings() { customSettings.applyProfile(); }

    private final BooleanSetting rotationDebug = new BooleanSetting("Rotation Debug", "Shows silent rotation point")
            .setValue(false);

    private final SelectSetting moveFix = new SelectSetting("Movement Correction")
            .value("Focused", "Free", "Follow", "Target", "Disabled")
            .selected("Focused");

    @Getter
    public final SliderSettings attackrange = new SliderSettings("Attack Range", "Set range value")
            .range(2.0f, 6.0f)
            .setValue(3.0f);

    private final SliderSettings lookrange = new SliderSettings("Search Range", "Set look range value")
            .range(0.0f, 10.0f)
            .setValue(1.5f);

    public final MultiSelectSetting options = new MultiSelectSetting("Options", "")
            .value("Ignore Walls", "Critical Randomization", "Do Not Attack While Eating", "Shield Check")
            .selected("Ignore Walls", "Critical Randomization");

    private final MultiSelectSetting targetType = new MultiSelectSetting("Target Types", "Select target settings")
            .value("Players", "Mobs", "Animals", "Friends", "Armor Stands")
            .selected("Players", "Mobs", "Animals");

    @Getter
    private final SelectSetting attackMode = new SelectSetting("Attack Mode")
            .value("1.9+", "1.8")
            .selected("1.9+");

    @Getter
    private final SliderSettings minCps = new SliderSettings("Min CPS", "Minimum CPS for 1.8 attacks")
            .range(1.0f, 20.0f)
            .setValue(9.0f)
            .visible(() -> attackMode.isSelected("1.8"));

    @Getter
    private final SliderSettings maxCps = new SliderSettings("Max CPS", "Maximum CPS for 1.8 attacks")
            .range(1.0f, 20.0f)
            .setValue(12.0f)
            .visible(() -> attackMode.isSelected("1.8"));

    @Getter
    private final SelectSetting resetSprintMode = new SelectSetting("Sprint Reset")
            .value("Legit", "Packet")
            .selected("Legit");

    @Getter
    private final BooleanSetting checkCrit = new BooleanSetting("Only Criticals", "Only critical hits")
            .setValue(true);

    @Getter
    private final BooleanSetting smartCrits = new BooleanSetting(
            "Smart Criticals",
            "Smart crits - attack on ground when possible"
    )
            .setValue(true)
            .visible(() -> checkCrit.isValue());

    @Getter
    private final BooleanSetting maceOnFall = new BooleanSetting(
            "Mace On Fall",
            "Swap to mace from hotbar when falling more than 5 blocks"
    )
            .setValue(false);

    private final MaceResolver maceResolver = new MaceResolver(maceOnFall, attackrange);

    public SelectSetting getMaceResolver() {
        return maceResolver.getSetting();
    }

    public MaceResolver getMaceResolverController() {
        return maceResolver;
    }

    public Aura() {
        super("Aura", ModuleCategory.COMBAT);
        LuaExtensionRegistry.getInstance().bindAuraModeSetting(mode, "Matrix");
        settings(
                mode,
                customSettings.topDivider,
                customSettings.section,
                customSettings.editorDivider,
                customSettings.aim,
                customSettings.stickyPoint,
                customSettings.outside,
                customSettings.leadTicks,
                customSettings.jitter,
                customSettings.acquire,
                customSettings.track,
                customSettings.returning,
                customSettings.yaw,
                customSettings.pitch,
                customSettings.acceleration,
                customSettings.deceleration,
                customSettings.smoothing,
                customSettings.snapOutsideSpeed,
                customSettings.snapHoldTicks,
                customSettings.snapReturnSpeed,
                customSettings.snapTriggerTicks,
                customSettings.curve,
                customSettings.multiPoints,
                customSettings.pointSwitchTicks,
                customSettings.bottomDivider,
                rotationDebug,
                attackrange,
                lookrange,
                options,
                targetType,
                moveFix,
                attackMode,
                minCps,
                maxCps,
                resetSprintMode,
                checkCrit,
                smartCrits,
                maceOnFall,
                maceResolver.getSetting(),
                maceResolver.getPredictionSetting()
        );
    }

    @NonFinal
    public static LivingEntity target;

    @NonFinal
    public LivingEntity lastTarget;

    @NonFinal
    private LivingEntity moveFixReturnTarget;

    TargetSelector targetSelector = new TargetSelector();
    MultiPoint pointFinder = new MultiPoint();

    private final RotationManager controller = RotationManager.INSTANCE;

    private final RotationMode matrixRotation = new MatrixRotation();
    private final RotationMode customRotation = new CustomRotation();
    private final RotationMode extendedRotation = new ExtendedRotation();
    private final RotationMode behavioralRotation = new BehavioralRotation();
    private final PolarRotation polarRotation = new PolarRotation();

    private final RotationConfig matrixConfig =
            new RotationConfig(
                    92.0f,
                    60.0f,
                    80.0f,
                    86.0f,
                    73.0f,
                    0.82f,
                    0.70f,
                    0.92f
            );

    private final RotationConfig extendedConfig =
            new RotationConfig(
                    90.0f,
                    58.0f,
                    60.0f,
                    82.0f,
                    62.0f,
                    0.72f,
                    0.66f,
                    0.94f
            );

    private final RotationConfig behavioralConfig =
            new RotationConfig(
                    90.0f,
                    120.0f,
                    76.0f,
                    88.0f,
                    98.0f,
                    1.4f,
                    1.66f,
                    1.5f
            );

    private final RotationConfig polarConfig =
            new RotationConfig(90.0f, 72.0f, 60.0f, 72.0f, 42.0f, 0.72f, 0.66f, 1.0f);

    private final RotationConfig luaFallbackConfig =
            new RotationConfig(
                    30.0f,
                    18.0f,
                    15.0f,
                    45.0f,
                    25.0f,
                    0.34f,
                    0.34f,
                    1.0f
            );

    @Override
    public void activate() {
        polarRotation.reset();
        jumpBlockWaitingForGround = false;
        jumpBlockUntilMs = 0L;
        moveFixReturnTarget = null;
        foodUseSuspended = false;
        foodResumeAfterAge = Integer.MIN_VALUE;
        foodUseArmAge = Integer.MIN_VALUE;
        foodUseInputPrepared = false;
        foodReturnSettledAge = Integer.MIN_VALUE;
    }

    boolean restoreSprint;

    @Override
    public void deactivate() {
        polarRotation.reset();
        foodUseSuspended = false;
        foodResumeAfterAge = Integer.MIN_VALUE;
        foodUseArmAge = Integer.MIN_VALUE;
        foodUseInputPrepared = false;
        foodReturnSettledAge = Integer.MIN_VALUE;
        jumpBlockWaitingForGround = true;
        jumpBlockUntilMs = 0L;
        moveFixReturnTarget = target != null ? target : lastTarget;

        target = null;
        lastTarget = null;

        RotationManager.INSTANCE.startReturning(this);

        Initialization.getInstance().getManager()
                .getAttackPerpetrator()
                .getAttackHandler()
                .resetPendingState();
    }

    @EventHandler
    private void tick(TickEvent event) {
        Initialization.getInstance()
                .getManager()
                .getAttackPerpetrator()
                .tick();

    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (!rotationDebug.isValue() || !isState() || mc.player == null || mc.world == null || target == null || !target.isAlive()) {
            return;
        }

        Rotation rotation = RotationManager.INSTANCE.getRotation();
        if (rotation == null) {
            return;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d direction = rotation.toVector().normalize();
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double distance = eyePos.distanceTo(targetCenter);
        Vec3d point = eyePos.add(direction.multiply(distance));
        double size = 0.045;

        Render3D.drawBox(
                new Box(
                        point.x - size, point.y - size, point.z - size,
                        point.x + size, point.y + size, point.z + size
                ),
                0xFF0080FF,
                1.5F,
                true,
                true,
                false
        );
    }


    private boolean isEatingOrStartingToEat() {
        if (mc.player == null) {
            return false;
        }

        if (mc.player.isUsingItem()) {
            var activeItem = mc.player.getActiveItem();

            if (!activeItem.isEmpty()) {
                UseAction action = activeItem.getUseAction();
                if (action == UseAction.EAT || action == UseAction.DRINK) {
                    return true;
                }
            }
        }

        if (!mc.options.useKey.isPressed()) {
            return false;
        }

        UseAction mainAction = mc.player.getMainHandStack().getUseAction();
        UseAction offAction = mc.player.getOffHandStack().getUseAction();

        return mainAction == UseAction.EAT
                || mainAction == UseAction.DRINK
                || offAction == UseAction.EAT
                || offAction == UseAction.DRINK;
    }

    private boolean isHoldingConsumable() {
        if (mc.player == null) {
            return false;
        }

        UseAction mainAction = mc.player.getMainHandStack().getUseAction();
        UseAction offAction = mc.player.getOffHandStack().getUseAction();

        return mainAction == UseAction.EAT
                || mainAction == UseAction.DRINK
                || offAction == UseAction.EAT
                || offAction == UseAction.DRINK;
    }

    public boolean shouldDelayFoodUseStart() {
        if (!isState() || mc.player == null || !isHoldingConsumable()) {
            return false;
        }

        if (!foodUseSuspended) {
            foodUseArmAge = mc.player.age;
            foodUseInputPrepared = false;
            foodReturnSettledAge = Integer.MIN_VALUE;
            suspendForFoodUse();
            return true;
        }


        if (!RotationManager.INSTANCE.isReturningSettled()) {
            foodReturnSettledAge = Integer.MIN_VALUE;
            return true;
        }


        if (foodReturnSettledAge == Integer.MIN_VALUE) {
            foodReturnSettledAge = mc.player.age;
            return true;
        }

        return mc.player.age <= foodReturnSettledAge;
    }

    public void suspendForFoodUse() {
        if (!isState() || mc.player == null) {
            return;
        }


        if (foodUseSuspended) {
            return;
        }

        LivingEntity previousTarget = target != null ? target : lastTarget;

        foodUseSuspended = true;
        foodResumeAfterAge = Integer.MIN_VALUE;

        if (foodUseArmAge == Integer.MIN_VALUE) {
            foodUseArmAge = mc.player.age;
            foodUseInputPrepared = false;
        }

        foodReturnSettledAge = Integer.MIN_VALUE;
        polarRotation.reset();

        if (previousTarget != null) {
            lastTarget = previousTarget;
            moveFixReturnTarget = previousTarget;
        }

        target = null;


        mc.player.setSprinting(false);

        RotationManager.INSTANCE.startReturning(this);

        Initialization.getInstance().getManager()
                .getAttackPerpetrator()
                .getAttackHandler()
                .resetPendingStateForItemUse();
    }


    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        TargetPearl targetPearl = TargetPearl.getInstance();

        if (targetPearl != null && targetPearl.isIntercepting()) {
            return;
        }

        ClickPearl clickPearl = ClickPearl.getInstance();

        if (clickPearl != null && clickPearl.isRotationOverrideActive()) {
            return;
        }

        AutoSpear autoSpear = AutoSpear.getInstance();
        if (autoSpear != null && autoSpear.isSequenceActive()) {
            return;
        }

        MaceHelper maceHelper = MaceHelper.getInstance();
        if (maceHelper != null && maceHelper.isThrowSequenceActive()) {
            return;
        }

        if (!isState()) {
            target = null;
            return;
        }

        switch (e.getType()) {
            case EventType.PRE -> {
                if (isEatingOrStartingToEat()) {
                    suspendForFoodUse();
                    return;
                }

                if (foodUseSuspended) {
                    foodUseSuspended = false;
                    foodUseArmAge = Integer.MIN_VALUE;
                    foodUseInputPrepared = false;
                    foodReturnSettledAge = Integer.MIN_VALUE;
                    foodResumeAfterAge = mc.player != null
                            ? mc.player.age + 1
                            : Integer.MIN_VALUE;
                    return;
                }

                if (isFoodResumeDelayActive()) {
                    return;
                }

                foodResumeAfterAge = Integer.MIN_VALUE;

                LivingEntity previousTarget = target;
                target = updateTarget();
                if (target == null) {
                    polarRotation.reset();
                }

                if (target != null) {
                    moveFixReturnTarget = null;
                }

                if (previousTarget != null && target == null) {
                    moveFixReturnTarget = previousTarget;
                    lastTarget = previousTarget;

                    RotationManager.INSTANCE.startReturning(this);

                    Initialization.getInstance().getManager()
                            .getAttackPerpetrator()
                            .getAttackHandler()
                            .resetPendingState();
                }

                if (target != null) {
                    rotateToTarget(getConfig());
                    lastTarget = target;
                }
            }

            case EventType.POST -> {
                if (target != null) {
                    Initialization.getInstance()
                            .getManager()
                            .getAttackPerpetrator()
                            .performAttack(getConfig());
                }
            }
        }
    }


    public AttackExecutor.AttackPerpetratorConfigurable getConfig() {
        float baseRange = attackrange.getValue();

        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target,
                baseRange,
                RotationManager.INSTANCE.getRotation(),
                getSmoothMode().randomValue(),
                options.isSelected("Ignore Walls")
        );

        Vec3d computedPoint = pointData.getLeft();
        Box hitbox = pointData.getRight();

        if (mc.player.isGliding() && target.isGliding()) {
            Vec3d targetVelocity = target.getVelocity();
            double targetSpeed = targetVelocity.horizontalLength();

            float leadTicks = 0;

            if (ElytraTarget.shouldElytraTarget
                    && ElytraTarget.getInstance() != null
                    && ElytraTarget.getInstance().isState()) {
                leadTicks = ElytraTarget.getInstance().elytraForward.getValue();
            }

            if (targetSpeed > 0.35) {
                Vec3d predictedPos = target.getEntityPos()
                        .add(targetVelocity.multiply(leadTicks));

                computedPoint = predictedPos.add(
                        0,
                        target.getHeight() / 2,
                        0
                );

                hitbox = new Box(
                        predictedPos.x - target.getWidth() / 2,
                        predictedPos.y,
                        predictedPos.z - target.getWidth() / 2,
                        predictedPos.x + target.getWidth() / 2,
                        predictedPos.y + target.getHeight(),
                        predictedPos.z + target.getWidth() / 2
                );
            }
        }

        computedPoint = maceResolver.resolveAimPoint(target, computedPoint);

        Rotation rotation = RotationUtils.fromVec3d(
                computedPoint.subtract(
                        Objects.requireNonNull(mc.player).getEyePos()
                )
        );

        return new AttackExecutor.AttackPerpetratorConfigurable(
                target,
                rotation,
                baseRange,
                options.getSelected(),
                mode,
                hitbox
        );
    }

    public RotationRequest getRotationConfig() {
        boolean visibleCorrection = !moveFix.isSelected("Disabled");
        boolean freeCorrection = moveFix.isSelected("Free");
        RotationSelection selection = resolveRotationSelection();

        return new RotationRequest(
                selection.mode(),
                selection.profile(),
                visibleCorrection,
                freeCorrection
        );
    }

    private void rotateToTarget(
            AttackExecutor.AttackPerpetratorConfigurable config
    ) {
        Rotation.VecRotation rotation = new Rotation.VecRotation(
                config.getRotation(),
                config.getRotation().toVector()
        );

        controller.rotateTo(
                rotation,
                target,
                1,
                getRotationConfig(),
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }


    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        PlayerInput input = event.getInput();

        if (input == null) {
            return;
        }

        if (foodUseSuspended || isFoodResumeDelayActive()) {
            boolean forwardInput = mc.options.forwardKey.isPressed();
            boolean backInput = mc.options.backKey.isPressed();
            boolean leftInput = mc.options.leftKey.isPressed();
            boolean rightInput = mc.options.rightKey.isPressed();


            if (shouldApplyReturnMovementCorrection()) {
                applyRotationReturnMoveFix(
                        event,
                        forwardInput,
                        backInput,
                        leftInput,
                        rightInput
                );
            } else {
                restoreOriginalInput(
                        event,
                        forwardInput,
                        backInput,
                        leftInput,
                        rightInput
                );
            }


            event.setSprinting(false);
            mc.player.setSprinting(false);

            foodUseInputPrepared = true;

            return;
        }

        MaceHelper maceHelper = MaceHelper.getInstance();
        if (maceHelper != null && maceHelper.isMovementLockedByThrow()) {
            event.setDirectionalLow(false, false, false, false);
            event.setSprinting(false);
            return;
        }

        Scaffold scaffold = Scaffold.getInstance();

        if (scaffold != null && scaffold.isState()) {
            if (AttackManager.isLegitSprintResetPending()) {
                event.setSprinting(false);
            }
            return;
        }


        if (AttackManager.isLegitSprintResetPending()) {
            event.setSprinting(false);
        }

        boolean forwardInput = mc.options.forwardKey.isPressed();
        boolean backInput = mc.options.backKey.isPressed();
        boolean leftInput = mc.options.leftKey.isPressed();
        boolean rightInput = mc.options.rightKey.isPressed();

        if (shouldApplyReturnMovementCorrection()) {
            applyRotationReturnMoveFix(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );
            return;
        }

        if (!isState()) {
            moveFixReturnTarget = null;

            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        if (target == null || !target.isAlive()) {
            moveFixReturnTarget = null;

            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        if (moveFix.isSelected("Target")) {
            applyTargetMoveFix(event, target);
            return;
        }

        if (moveFix.isSelected("Follow")) {
            applyFollowMoveFix(
                    event,
                    target,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        restoreOriginalInput(
                event,
                forwardInput,
                backInput,
                leftInput,
                rightInput
        );
    }


    private boolean shouldApplyReturnMovementCorrection() {
        return RotationManager.INSTANCE.isReturning()
                && RotationManager.INSTANCE.getRotation() != null
                && !moveFix.isSelected("Disabled");
    }

    private void applyRotationReturnMoveFix(
            InputEvent event,
            boolean forwardInput,
            boolean backInput,
            boolean leftInput,
            boolean rightInput
    ) {
        Rotation rotation = RotationManager.INSTANCE.getEffectiveRotation();

        if (rotation == null || mc.player == null) {
            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );
            return;
        }

        float deltaYaw = mc.player.getYaw() - rotation.getYaw();
        float radians = deltaYaw * 0.017453292F;

        float forward = (forwardInput ? 1.0F : 0.0F) - (backInput ? 1.0F : 0.0F);
        float sideways = (leftInput ? 1.0F : 0.0F) - (rightInput ? 1.0F : 0.0F);

        float correctedSideways = sideways * MathHelper.cos(radians)
                - forward * MathHelper.sin(radians);
        float correctedForward = forward * MathHelper.cos(radians)
                + sideways * MathHelper.sin(radians);

        int movementSideways = Math.round(correctedSideways);
        int movementForward = Math.round(correctedForward);

        event.setDirectionalLow(
                movementForward > 0,
                movementForward < 0,
                movementSideways > 0,
                movementSideways < 0
        );
    }

    private boolean isRotationReturning() {
        if (!RotationManager.INSTANCE.isReturning()) {
            return false;
        }

        Rotation current = RotationManager.INSTANCE.getRotation();
        Rotation camera = RotationUtils.cameraAngle();

        float difference = Math.abs(
                MathHelper.wrapDegrees(current.getYaw() - camera.getYaw())
        );

        return difference > 1.0F;
    }

    private void applyReturnMoveFix(
            InputEvent event,
            boolean forwardInput,
            boolean backInput,
            boolean leftInput,
            boolean rightInput
    ) {
        if (moveFixReturnTarget == null
                || !moveFixReturnTarget.isAlive()) {

            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        if (moveFix.isSelected("Follow")) {
            applyFollowMoveFix(
                    event,
                    moveFixReturnTarget,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        if (moveFix.isSelected("Target")) {
            applyTargetMoveFix(
                    event,
                    moveFixReturnTarget
            );

            return;
        }

        restoreOriginalInput(
                event,
                forwardInput,
                backInput,
                leftInput,
                rightInput
        );
    }

    private void restoreOriginalInput(
            InputEvent event,
            boolean forward,
            boolean back,
            boolean left,
            boolean right
    ) {
        event.setDirectionalLow(
                forward,
                back,
                left,
                right
        );
    }

    private void applyTargetMoveFix(
            InputEvent event,
            LivingEntity targetEntity
    ) {
        if (targetEntity == null || !targetEntity.isAlive()) {
            return;
        }

        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d targetPos = targetEntity.getEntityPos();

        Vec3d direction = new Vec3d(
                targetPos.x - playerPos.x,
                0.0,
                targetPos.z - playerPos.z
        );

        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }

        direction = direction.normalize();

        float yaw = getMoveFixYaw();

        float moveAngle = (float) Math.toDegrees(
                Math.atan2(
                        direction.z,
                        direction.x
                )
        ) - 90.0F;

        float angleDiff = MathHelper.wrapDegrees(
                moveAngle - yaw
        );

        applyDirectionalMovement(
                event,
                angleDiff
        );
    }

    private void applyFollowMoveFix(
            InputEvent event,
            LivingEntity targetEntity,
            boolean forwardInput,
            boolean backInput,
            boolean leftInput,
            boolean rightInput
    ) {
        if (targetEntity == null || !targetEntity.isAlive()) {
            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        if (!forwardInput
                && !backInput
                && !leftInput
                && !rightInput) {

            event.setDirectionalLow(
                    false,
                    false,
                    false,
                    false
            );

            return;
        }

        Vec3d playerPos = mc.player.getEntityPos();

        Box targetBox = targetEntity.getBoundingBox();
        Vec3d center = targetBox.getCenter();

        float targetYaw = targetEntity.getYaw();
        double radians = Math.toRadians(targetYaw);

        Vec3d forwardDirection = new Vec3d(
                -Math.sin(radians),
                0.0,
                Math.cos(radians)
        ).normalize();

        Vec3d rightDirection = new Vec3d(
                -forwardDirection.z,
                0.0,
                forwardDirection.x
        ).normalize();

        Vec3d leftDirection = rightDirection.multiply(-1.0);

        double offset = targetEntity.getWidth() / 2.0 + 0.1;

        Vec3d offsetDirection = Vec3d.ZERO;

        if (forwardInput) {
            offsetDirection = offsetDirection.add(
                    forwardDirection
            );
        }

        if (backInput) {
            offsetDirection = offsetDirection.add(
                    forwardDirection.multiply(-1.0)
            );
        }

        if (leftInput) {
            offsetDirection = offsetDirection.add(
                    leftDirection
            );
        }

        if (rightInput) {
            offsetDirection = offsetDirection.add(
                    rightDirection
            );
        }

        Vec3d moveTarget = center;

        if (offsetDirection.lengthSquared() > 1.0E-6) {
            offsetDirection = offsetDirection
                    .normalize()
                    .multiply(offset);

            moveTarget = center.add(offsetDirection);
        }

        Vec3d direction = new Vec3d(
                moveTarget.x - playerPos.x,
                0.0,
                moveTarget.z - playerPos.z
        );

        if (direction.lengthSquared() < 1.0E-6) {
            restoreOriginalInput(
                    event,
                    forwardInput,
                    backInput,
                    leftInput,
                    rightInput
            );

            return;
        }

        direction = direction.normalize();

        float yaw = getMoveFixYaw();

        float moveAngle = (float) Math.toDegrees(
                Math.atan2(
                        direction.z,
                        direction.x
                )
        ) - 90.0F;

        float angleDiff = MathHelper.wrapDegrees(
                moveAngle - yaw
        );

        applyDirectionalMovement(
                event,
                angleDiff
        );
    }

    private float getMoveFixYaw() {
        Rotation rotation = RotationManager.INSTANCE.getEffectiveRotation();
        return rotation != null ? rotation.getYaw() : mc.player.getYaw();
    }

    private void applyDirectionalMovement(
            InputEvent event,
            float angleDiff
    ) {
        boolean forward = false;
        boolean back = false;
        boolean left = false;
        boolean right = false;

        if (angleDiff >= -22.5F && angleDiff < 22.5F) {
            forward = true;
        } else if (angleDiff >= 22.5F && angleDiff < 67.5F) {
            forward = true;
            right = true;
        } else if (angleDiff >= 67.5F && angleDiff < 112.5F) {
            right = true;
        } else if (angleDiff >= 112.5F && angleDiff < 157.5F) {
            back = true;
            right = true;
        } else if (angleDiff >= -67.5F && angleDiff < -22.5F) {
            forward = true;
            left = true;
        } else if (angleDiff >= -112.5F && angleDiff < -67.5F) {
            left = true;
        } else if (angleDiff >= -157.5F && angleDiff < -112.5F) {
            back = true;
            left = true;
        } else {
            back = true;
        }

        event.setDirectionalLow(
                forward,
                back,
                left,
                right
        );
    }

    private LivingEntity updateTarget() {
        TargetSelector.EntityFilter filter =
                new TargetSelector.EntityFilter(
                        targetType.getSelected()
                );

        float range = attackrange.getValue()
                + 0.25F
                + maceResolver.getSearchRangeBonus()
                + (
                mc.player.isGliding()
                        && ElytraTarget.getInstance() != null
                        && ElytraTarget.getInstance().isState()
                        ? ElytraTarget.getInstance()
                        .elytraFindRange
                        .getValue()
                        : lookrange.getValue()
        );

        float dynamicFov = 360;

        targetSelector.searchTargets(
                mc.world.getEntities(),
                range,
                dynamicFov,
                options.isSelected("Ignore Walls")
        );

        maceResolver.resolve(targetSelector, filter);

        return targetSelector.getCurrentTarget();
    }

    public RotationMode getSmoothMode() {
        if (isCustomRotationSelected()) customSettings.sync();
        if (!mode.isSelected("Polar")) {
            polarRotation.reset();
        }
        RotationMode luaRotation = LuaExtensionRegistry.getInstance()
                .getAuraRotationMode(mode.getSelected());
        if (luaRotation != null) {
            return luaRotation;
        }

        return switch (mode.getSelected()) {
            case "Matrix" -> matrixRotation;
            case "Extended" -> extendedRotation;
            case "Behavioral" -> behavioralRotation;
            case "Polar" -> polarRotation;
            case "Custom" -> customRotation;
            default -> matrixRotation;
        };
    }

    private RotationSelection resolveRotationSelection() {
        if (isCustomRotationSelected()) customSettings.sync();
        LuaExtensionRegistry registry = LuaExtensionRegistry.getInstance();
        RotationMode luaRotation = registry.getAuraRotationMode(mode.getSelected());

        if (luaRotation != null) {
            RotationConfig profile = registry.getAuraRotationConfig(
                    mode.getSelected(),
                    luaFallbackConfig,
                    target
            );
            profile = maceResolver.resolveRotationConfig(profile, target);
            return new RotationSelection(luaRotation, profile);
        }

        RotationMode selectedMode;
        RotationConfig profile;

        switch (mode.getSelected()) {
            case "Matrix" -> {
                selectedMode = matrixRotation;
                profile = matrixConfig;
            }
            case "Extended" -> {
                selectedMode = extendedRotation;
                profile = extendedConfig;
            }
            case "Custom" -> {
                selectedMode = customRotation;
                profile = RotationBuilder.INSTANCE.active().config();
            }
            case "Polar" -> {
                selectedMode = polarRotation;
                profile = polarConfig;
            }
            case "Behavioral" -> {
                selectedMode = behavioralRotation;
                profile = behavioralConfig;
            }
            default -> {
                selectedMode = matrixRotation;
                profile = matrixConfig;
            }
        }

        profile = maceResolver.resolveRotationConfig(profile, target);
        return new RotationSelection(selectedMode, profile);
    }

    private record RotationSelection(RotationMode mode, RotationConfig profile) {
    }
}
