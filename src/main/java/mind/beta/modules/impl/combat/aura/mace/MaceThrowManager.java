/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.mace;

import lombok.Getter;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import mind.beta.IMinecraft;
import mind.beta.events.impl.HotBarScrollEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.mixin.ClientWorldAccessor;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.screens.clickgui.impl.settingsrender.BindComponent;
import mind.beta.util.inventory.InventoryUtils;
import mind.beta.util.inventory.MovementController;
import mind.beta.util.inventory.SwapSettings;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.string.chat.ChatMessage;

public final class MaceThrowManager implements IMinecraft {

    private static final float PEARL_THROW_PITCH = -90.0F;
    private static final float WIND_ONLY_THROW_PITCH = 90.0F;
    private static final long WIND_CHARGE_DELAY_MS = 50L;
    private static final long WIND_ONLY_JUMP_DELAY_MS = 50L;
    private static final int MOVEMENT_RELEASE_DELAY_TICKS = 2;
    private static final double MOVEMENT_STOP_SPEED = 0.035D;
    private static final int MOVEMENT_STOP_STABLE_TICKS = 1;

    private final ModuleStructure owner;
    private final RotationManager controller = RotationManager.INSTANCE;

    @Getter
    private final SelectSetting throwSwapMode;

    @Getter
    private final SelectSetting windChargeMode;

    @Getter
    private final BindSetting keySetting;

    @Getter
    private final BindSetting windChargeKeySetting;


    private static final long SEQUENCE_TIMEOUT_MS = 4000L;
    private long pearlSequenceStartTime;
    private Object pearlSequencePlayer;
    private Object pearlSequenceWorld;
    private long windSequenceStartTime;
    private Object windSequencePlayer;
    private Object windSequenceWorld;
    private boolean windInventorySwapApplied;
    private int windInventorySwapHotbarSlot = -1;
    private State state = State.IDLE;
    private ThrowAction action = ThrowAction.NONE;
    private long pearlThrowTime;
    private int movementReleaseTicks;
    private int movementStopStableTicks;
    private int restoreSlot = -1;


    private final MovementController windMovement = new MovementController();
    private WindPhase windPhase = WindPhase.IDLE;
    private int windSavedSlot = -1;
    private int windChargeSlot = -1;
    private boolean windFromInventory;
    private long windPhaseStartTime;
    private int windCurrentDelay;
    private long windLastThrowTime;
    private int windRotationTicks;
    private boolean pendingWindThrow;


    private long windOnlyJumpAt;
    private int windOnlyJumpPlayerAge = -1;

    public MaceThrowManager(ModuleStructure owner) {
        this.owner = owner;

        this.throwSwapMode = new SelectSetting(
                "Throw Mode"
        )
                .value("Normal", "Physical")
                .selected("Normal");

        this.windChargeMode = new SelectSetting(
                "Wind Charge Mode"
        )
                .value("Instant", "Legit")
                .selected("Legit");

        this.keySetting = new BindSetting(
                "Pearl + Wind Key",
                "Throws a pearl upward and then a wind charge"
        );

        this.windChargeKeySetting = new BindSetting(
                "Wind Charge Key",
                "Wind Jump under the player"
        );
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.world == null || !owner.isState()
                || (isActive() && mc.currentScreen != null)
                || (state != State.IDLE && (mc.player != pearlSequencePlayer
                || mc.world != pearlSequenceWorld || now - pearlSequenceStartTime > SEQUENCE_TIMEOUT_MS))
                || (windPhase != WindPhase.IDLE && (mc.player != windSequencePlayer
                || mc.world != windSequenceWorld || now - windSequenceStartTime > SEQUENCE_TIMEOUT_MS))) {
            cancel();
            return;
        }

        processPearlWindTick();
        processWindJumpTick();
    }

    private void processPearlWindTick() {
        if (isWindJumpRunning()) {
            return;
        }

        if (shouldBlockPearlMovement()) {
            mc.player.setSprinting(false);
        }

        if (state != State.WAIT_MOVEMENT_STOP) {
            return;
        }

        if (getHorizontalSpeed() <= MOVEMENT_STOP_SPEED) {
            movementStopStableTicks++;
        } else {
            movementStopStableTicks = 0;
        }

        if (movementStopStableTicks >= MOVEMENT_STOP_STABLE_TICKS) {
            movementStopStableTicks = 0;
            controller.beginRotationOverride(owner);
            state = State.WAIT_THROW_ROTATION;
        }
    }

    private void processWindJumpTick() {
        if (windPhase == WindPhase.IDLE) {
            return;
        }

        boolean continueProcessing = true;
        int iterations = 0;

        while (continueProcessing && iterations < 10) {
            iterations++;
            continueProcessing = processWindTick();
        }
    }

    public void onKey(KeyEvent event) {
        if (!owner.isState() || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        if (isActive() || event.action() != 1) {
            return;
        }

        if (matchesBind(event, keySetting)) {
            startPearlWind();
        }
    }

    public void onScroll(HotBarScrollEvent event) {

    }

    private boolean matchesBind(KeyEvent event, BindSetting setting) {
        int bindKey = setting.getKey();
        int bindType = setting.getType();

        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            return false;
        }

        if (bindType == 2) {
            return bindKey == BindComponent.MIDDLE_MOUSE_BIND
                    && event.type() == InputUtil.Type.MOUSE
                    && event.key() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        }

        if (bindType == 0 && event.type() == InputUtil.Type.MOUSE) {
            return event.key() == bindKey;
        }

        return bindType == 1
                && event.type() == InputUtil.Type.KEYSYM
                && event.key() == bindKey;
    }

    public boolean isActive() {
        return state != State.IDLE || windPhase != WindPhase.IDLE;
    }

    public boolean isWindJumpRunning() {
        return windPhase != WindPhase.IDLE;
    }

    public String getDebugAction() {
        if (windPhase != WindPhase.IDLE || windOnlyJumpPlayerAge >= 0) {
            return "Wind Only";
        }

        return switch (action) {
            case PEARL_WIND -> "Pearl + Wind";
            case WIND_ONLY -> "Wind Only";
            default -> "Idle";
        };
    }

    public String getDebugState() {
        if (windPhase != WindPhase.IDLE) {
            return switch (windPhase) {
                case PRE_STOP -> "Pre stop";
                case STOPPING -> "Stopping movement";
                case WAIT_STOP -> "Waiting velocity";
                case PRE_SWAP -> "Pre swap";
                case SWAP_TO_HAND -> "Swapping charge";
                case AWAIT_ITEM -> "Awaiting charge";
                case ROTATE_DOWN -> "Rotating down " + windRotationTicks;
                case THROW -> "Throwing wind charge";
                case POST_THROW -> "Post throw";
                case SWAP_BACK -> "Swapping back";
                case RESUMING -> "Resuming movement";
                default -> "Ready";
            };
        }

        if (state == State.IDLE) {
            long jumpDelay = getWindOnlyJumpRemainingMs();
            if (jumpDelay >= 0L) {
                return "Jump in " + jumpDelay + "ms";
            }
            return "Ready";
        }

        return switch (state) {
            case WAIT_MOVEMENT_STOP -> "Stopping movement "
                    + String.format(java.util.Locale.US, "%.3f", getHorizontalSpeed());
            case WAIT_THROW_ROTATION -> "Applying rotation";
            case WAIT_PEARL_SWAP -> "Waiting pearl swap";
            case THROW_PEARL -> "Throwing pearl";
            case WAIT_WIND_SWAP -> "Waiting wind swap";
            case WAIT_WIND_CHARGE -> "Waiting wind delay";
            case THROW_WIND_ONLY -> "Throwing wind charge";
            default -> "Ready";
        };
    }

    public float getDebugRequiredPitch() {
        if (windPhase != WindPhase.IDLE) {
            return WIND_ONLY_THROW_PITCH;
        }

        return action == ThrowAction.NONE ? Float.NaN : requiredPitch();
    }

    public long getWindOnlyJumpRemainingMs() {
        if (windOnlyJumpPlayerAge < 0 || mc.player == null) {
            return -1L;
        }

        int ticks = Math.max(0, windOnlyJumpPlayerAge - mc.player.age);
        return ticks * WIND_ONLY_JUMP_DELAY_MS;
    }

    public boolean shouldBlockMovement() {
        if (mc.player == null) {
            return false;
        }

        return windMovement.isBlocked()
                || shouldBlockPearlMovement();
    }

    private boolean shouldBlockPearlMovement() {
        return movementReleaseTicks > 0 || state != State.IDLE;
    }

    public boolean shouldSuppressSprint() {
        return shouldBlockMovement();
    }

    public boolean shouldBlockGroundMovement() {
        return shouldBlockMovement();
    }

    public double getHorizontalSpeed() {
        if (mc.player == null) {
            return 0.0D;
        }

        double x = mc.player.getVelocity().x;
        double z = mc.player.getVelocity().z;
        return Math.sqrt(x * x + z * z);
    }

    public void onMovementInputBlocked() {
        if (state == State.IDLE && movementReleaseTicks > 0 && !windMovement.isBlocked()) {
            movementReleaseTicks--;
        }
    }

    public boolean consumeWindOnlyJump() {
        if (windOnlyJumpPlayerAge < 0
                || mc.player == null
                || mc.player.age < windOnlyJumpPlayerAge) {
            return false;
        }

        windOnlyJumpPlayerAge = -1;
        windOnlyJumpAt = 0L;
        return mc.player.isOnGround();
    }

    public void updateRotation() {
        if (mc.player == null) {
            return;
        }

        if (windPhase == WindPhase.ROTATE_DOWN || windPhase == WindPhase.THROW) {
            Rotation throwRotation = new Rotation(
                    mc.player.getYaw(),
                    WIND_ONLY_THROW_PITCH
            );

            RotationRequest config = new RotationRequest(
                    new LinearRotation(),
                    true,
                    true
            );

            controller.rotateTo(
                    throwRotation,
                    3,
                    config,
                    TaskPriority.HIGH_IMPORTANCE_1,
                    owner
            );
            return;
        }

        if (state == State.IDLE || state == State.WAIT_MOVEMENT_STOP) {
            return;
        }

        Rotation base = controller.getRotation();
        Rotation rotation = new Rotation(
                base.getYaw(),
                requiredPitch()
        ).adjustSensitivity();

        controller.setOverrideRotation(owner, rotation);
    }

    public void process() {
        if (pendingWindThrow) {
            performWindThrow();
            pendingWindThrow = false;
            return;
        }

        if (state == State.IDLE) {
            return;
        }

        switch (state) {
            case WAIT_MOVEMENT_STOP -> {
            }
            case WAIT_THROW_ROTATION -> processInitialRotation();
            case WAIT_PEARL_SWAP -> processPearlSwap();
            case THROW_PEARL -> processPearlThrow();
            case WAIT_WIND_SWAP -> processWindSwap();
            case WAIT_WIND_CHARGE -> processWindAfterPearl();
            case THROW_WIND_ONLY -> processWindOnlyThrow();
            default -> {
            }
        }
    }

    public void cancel() {
        if (mc.player == pearlSequencePlayer && mc.world == pearlSequenceWorld) {
            restoreSlot();
        } else {
            restoreSlot = -1;
        }

        state = State.IDLE;
        action = ThrowAction.NONE;
        pearlThrowTime = 0L;
        movementReleaseTicks = 0;
        movementStopStableTicks = 0;

        resetWindState();

        windOnlyJumpAt = 0L;
        windOnlyJumpPlayerAge = -1;

        releaseRotationControl();
    }





    private void processInitialRotation() {
        if (!isRotationApplied()) {
            return;
        }

        if (action == ThrowAction.PEARL_WIND) {
            if (!selectSlot(Items.ENDER_PEARL)) {
                cancel();
                return;
            }
            state = State.WAIT_PEARL_SWAP;
            return;
        }

        if (action == ThrowAction.WIND_ONLY) {
            if (isPhysicalSwap()) {
                if (!selectSlot(Items.WIND_CHARGE)) {
                    cancel();
                    return;
                }
                state = State.WAIT_WIND_SWAP;
            } else {
                state = State.THROW_WIND_ONLY;
            }
        }
    }

    private void processPearlSwap() {
        if (!isHolding(Items.ENDER_PEARL)) {
            cancel();
            return;
        }
        state = State.THROW_PEARL;
    }

    private void processPearlThrow() {
        if (!isRotationApplied()) {
            return;
        }
        if (!useSelected(Items.ENDER_PEARL)) {
            cancel();
            return;
        }

        pearlThrowTime = System.currentTimeMillis();
        if (!selectSlot(Items.WIND_CHARGE)) {
            cancel();
            return;
        }
        state = State.WAIT_WIND_SWAP;
    }

    private void processWindSwap() {
        if (!isHolding(Items.WIND_CHARGE)) {
            cancel();
            return;
        }
        state = action == ThrowAction.WIND_ONLY
                ? State.THROW_WIND_ONLY
                : State.WAIT_WIND_CHARGE;
    }

    private void processWindAfterPearl() {
        if (!isRotationApplied()) {
            return;
        }
        if (System.currentTimeMillis() - pearlThrowTime < WIND_CHARGE_DELAY_MS) {
            return;
        }
        if (!useSelected(Items.WIND_CHARGE)) {
            cancel();
            return;
        }
        finishPearlWind();
    }

    private void processWindOnlyThrow() {


        if (!isRotationApplied()) {
            return;
        }

        boolean thrown = isPhysicalSwap()
                ? useSelected(Items.WIND_CHARGE)
                : useTemporarySlot(Items.WIND_CHARGE);

        if (!thrown) {
            cancel();
            return;
        }

        scheduleWindJump();
        finishPearlWind();
    }

    private void startPearlWind() {
        if (findSlot(Items.ENDER_PEARL) == -1
                || findSlot(Items.WIND_CHARGE) == -1) {
            return;
        }

        pearlSequenceStartTime = System.currentTimeMillis();
        pearlSequencePlayer = mc.player;
        pearlSequenceWorld = mc.world;
        restoreSlot = mc.player.getInventory().getSelectedSlot();

        action = ThrowAction.PEARL_WIND;
        state = State.WAIT_MOVEMENT_STOP;
        pearlThrowTime = 0L;
        movementReleaseTicks = 0;
        movementStopStableTicks = 0;
        mc.player.setSprinting(false);
    }

    private void finishPearlWind() {
        ThrowAction completedAction = action;

        restoreSlot();

        state = State.IDLE;
        action = ThrowAction.NONE;
        pearlThrowTime = 0L;
        movementReleaseTicks = completedAction != ThrowAction.NONE
                ? MOVEMENT_RELEASE_DELAY_TICKS
                : 0;

        releaseRotationControl();
    }





    private void tryWindJump() {
        if (System.currentTimeMillis() - windLastThrowTime < 100L) {
            return;
        }

        windLastThrowTime = System.currentTimeMillis();
        startWindChargeProcess();
    }

    private void startWindChargeProcess() {
        windSequenceStartTime = System.currentTimeMillis();
        windSequencePlayer = mc.player;
        windSequenceWorld = mc.world;
        if (mc.player == null) {
            return;
        }

        windSavedSlot = mc.player.getInventory().getSelectedSlot();

        int hotbarSlot = InventoryUtils.findItemInHotbar(Items.WIND_CHARGE);
        if (hotbarSlot != -1) {
            windChargeSlot = hotbarSlot;
            windFromInventory = false;
            InventoryUtils.selectSlot(windChargeSlot);
            startWindPhase(WindPhase.AWAIT_ITEM, 0);
            return;
        }

        int inventorySlot = InventoryUtils.findItemInInventory(Items.WIND_CHARGE);
        if (inventorySlot != -1) {
            windChargeSlot = inventorySlot;
            windFromInventory = true;

            SwapSettings settings = buildWindSettings();

            if (settings.shouldStopMovement()) {
                startWindPhase(
                        WindPhase.PRE_STOP,
                        settings.randomPreStopDelay()
                );
            } else {
                startWindPhase(WindPhase.SWAP_TO_HAND, 0);
            }
            return;
        }

        ChatMessage.brandmessage("No wind charges");
        resetWindState();
    }

    private boolean processWindTick() {
        if (mc.player == null || mc.currentScreen != null) {
            if (windMovement.isBlocked()) {
                windMovement.restoreFromCurrent();
            }
            resetWindState();
            return false;
        }

        long elapsed = System.currentTimeMillis() - windPhaseStartTime;
        SwapSettings settings = buildWindSettings();

        switch (windPhase) {
            case PRE_STOP -> {
                if (elapsed >= windCurrentDelay) {
                    windMovement.saveState();
                    windMovement.block();

                    if (settings.shouldStopSprint()) {
                        windMovement.stopSprint();
                    }

                    startWindPhase(WindPhase.STOPPING, 0);
                    return true;
                }
            }

            case STOPPING -> {
                windMovement.block();

                if (settings.shouldStopSprint()) {
                    windMovement.stopSprint();
                }

                startWindPhase(
                        WindPhase.WAIT_STOP,
                        settings.randomWaitStopDelay()
                );
                return windCurrentDelay == 0;
            }

            case WAIT_STOP -> {
                windMovement.block();

                boolean stopped = windMovement.isPlayerStopped(
                        settings.getVelocityThreshold()
                );

                boolean timeout = elapsed >= windCurrentDelay;

                if (stopped || timeout) {
                    startWindPhase(
                            WindPhase.PRE_SWAP,
                            settings.randomPreSwapDelay()
                    );
                    return windCurrentDelay == 0;
                }
            }

            case PRE_SWAP -> {
                windMovement.block();

                if (elapsed >= windCurrentDelay) {
                    startWindPhase(WindPhase.SWAP_TO_HAND, 0);
                    return true;
                }
            }

            case SWAP_TO_HAND -> {
                int hotbarSlot = mc.player.getInventory().getSelectedSlot();
                InventoryUtils.click(
                        windChargeSlot,
                        hotbarSlot,
                        SlotActionType.SWAP
                );

                windInventorySwapApplied = true;
                windInventorySwapHotbarSlot = hotbarSlot;
                startWindPhase(WindPhase.AWAIT_ITEM, 0);
                return true;
            }

            case AWAIT_ITEM -> {
                if (mc.player.getMainHandStack().getItem() == Items.WIND_CHARGE) {
                    windRotationTicks = 0;
                    startWindPhase(WindPhase.ROTATE_DOWN, 0);
                    return true;
                }
            }

            case ROTATE_DOWN -> {
                windRotationTicks++;

                Rotation currentRotation = controller.getRotation();
                boolean rotationReady = currentRotation != null
                        && currentRotation.getPitch() >= 80.0F;

                boolean waitedEnough = windRotationTicks >= 2;

                if (rotationReady && waitedEnough) {
                    startWindPhase(WindPhase.THROW, 0);
                    return true;
                }

                if (windRotationTicks > 10) {
                    if (windMovement.isBlocked()) {
                        windMovement.restoreFromCurrent();
                    }
                    resetWindState();
                    return false;
                }
            }

            case THROW -> {
                pendingWindThrow = true;
                return false;
            }

            case POST_THROW -> {
                if (elapsed >= windCurrentDelay) {
                    if (windFromInventory) {
                        startWindPhase(WindPhase.SWAP_BACK, 0);
                        return true;
                    }

                    InventoryUtils.selectSlot(windSavedSlot);

                    startWindPhase(
                            WindPhase.RESUMING,
                            settings.randomResumeDelay()
                    );

                    return windCurrentDelay == 0;
                }
            }

            case SWAP_BACK -> {

                InventoryUtils.click(
                        windChargeSlot,
                        windInventorySwapHotbarSlot,
                        SlotActionType.SWAP
                );

                windInventorySwapApplied = false;
                InventoryUtils.selectSlot(windSavedSlot);

                if (settings.shouldCloseInventory()) {
                    InventoryUtils.closeScreen();
                }

                startWindPhase(
                        WindPhase.RESUMING,
                        settings.randomResumeDelay()
                );

                return windCurrentDelay == 0;
            }

            case RESUMING -> {
                if (elapsed >= windCurrentDelay) {
                    if (buildWindSettings().shouldStopMovement()
                            && windMovement.isBlocked()) {
                        windMovement.restoreFromCurrent();
                    }

                    releaseRotationControl();
                    resetWindState();
                    return false;
                }
            }

            default -> {
            }
        }

        return false;
    }

    private void performWindThrow() {
        if (mc.player == null) {
            return;
        }

        Rotation rotation = controller.getRotation();
        if (rotation == null) {
            rotation = RotationUtils.cameraAngle();
        }

        final Rotation finalRotation = rotation;

        sendSequencedPacket(sequence ->
                new PlayerInteractItemC2SPacket(
                        Hand.MAIN_HAND,
                        sequence,
                        finalRotation.getYaw(),
                        finalRotation.getPitch()
                )
        );

        mc.player.swingHand(Hand.MAIN_HAND);

        scheduleWindJump();

        SwapSettings settings = buildWindSettings();
        startWindPhase(
                WindPhase.POST_THROW,
                settings.randomPostSwapDelay()
        );
    }

    private void scheduleWindJump() {
        if (mc.player == null) {
            return;
        }

        if (windChargeMode.isSelected("Instant")) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
            windOnlyJumpAt = 0L;
            windOnlyJumpPlayerAge = -1;
            return;
        }

        windOnlyJumpAt = System.currentTimeMillis()
                + WIND_ONLY_JUMP_DELAY_MS;

        windOnlyJumpPlayerAge = mc.player.age
                + Math.max(
                1,
                (int) Math.ceil(
                        WIND_ONLY_JUMP_DELAY_MS / 50.0D
                )
        );
    }

    private void sendSequencedPacket(
            java.util.function.IntFunction<net.minecraft.network.packet.Packet<?>> packetCreator
    ) {
        if (mc.player == null
                || mc.getNetworkHandler() == null
                || mc.world == null) {
            return;
        }

        try {
            ClientWorldAccessor worldAccessor =
                    (ClientWorldAccessor) mc.world;

            PendingUpdateManager pendingUpdateManager =
                    worldAccessor
                            .getPendingUpdateManager()
                            .incrementSequence();

            int sequence = pendingUpdateManager.getSequence();

            mc.getNetworkHandler().sendPacket(
                    packetCreator.apply(sequence)
            );

            pendingUpdateManager.close();
        } catch (Exception ignored) {
            mc.getNetworkHandler().sendPacket(
                    packetCreator.apply(0)
            );
        }
    }

    private SwapSettings buildWindSettings() {
        return windChargeMode.isSelected("Instant")
                ? SwapSettings.instant()
                : SwapSettings.legit();
    }

    private void startWindPhase(
            WindPhase newPhase,
            int delay
    ) {
        this.windPhase = newPhase;
        this.windPhaseStartTime = System.currentTimeMillis();
        this.windCurrentDelay = delay;
    }

    private void resetWindState() {
        if (mc.player != null && mc.world != null
                && mc.player == windSequencePlayer && mc.world == windSequenceWorld) {
            if (windInventorySwapApplied && windChargeSlot >= 0 && windInventorySwapHotbarSlot >= 0) {
                InventoryUtils.click(windChargeSlot, windInventorySwapHotbarSlot, SlotActionType.SWAP);
            }
            if (windSavedSlot >= 0 && windSavedSlot < 9) {
                InventoryUtils.selectSlot(windSavedSlot);
            }
            if (windMovement.isBlocked()) {
                windMovement.restoreFromCurrent();
            }
        }
        if (windPhase != WindPhase.IDLE) {
            releaseRotationControl();
        }
        windInventorySwapApplied = false;
        windInventorySwapHotbarSlot = -1;
        windSequenceStartTime = 0L;
        windSequencePlayer = null;
        windSequenceWorld = null;
        windMovement.reset();
        windPhase = WindPhase.IDLE;
        windSavedSlot = -1;
        windChargeSlot = -1;
        windFromInventory = false;
        windPhaseStartTime = 0L;
        windCurrentDelay = 0;
        windRotationTicks = 0;
        pendingWindThrow = false;
    }





    private void releaseRotationControl() {
        controller.releaseControl(owner);
    }

    private float requiredPitch() {
        return action == ThrowAction.WIND_ONLY
                ? WIND_ONLY_THROW_PITCH
                : PEARL_THROW_PITCH;
    }

    private boolean isPhysicalSwap() {
        return throwSwapMode.isSelected("Physical");
    }

    private boolean isRotationApplied() {
        Rotation serverRotation = controller.getServerRotation();

        return serverRotation != null
                && Math.abs(
                serverRotation.getPitch() - requiredPitch()
        ) <= 1.0F;
    }

    private boolean selectSlot(Item item) {
        if (mc.player == null) {
            return false;
        }

        int slot = findSlot(item);

        if (slot == -1) {
            return false;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    private boolean isHolding(Item item) {
        if (mc.player == null) {
            return false;
        }

        int selectedSlot =
                mc.player.getInventory().getSelectedSlot();

        return selectedSlot >= 0
                && selectedSlot < 9
                && mc.player
                .getInventory()
                .getStack(selectedSlot)
                .isOf(item);
    }

    private boolean useSelected(Item item) {
        if (mc.player == null
                || mc.interactionManager == null
                || !isHolding(item)) {
            return false;
        }

        mc.interactionManager.interactItem(
                mc.player,
                Hand.MAIN_HAND
        );

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private boolean useTemporarySlot(Item item) {
        if (mc.player == null
                || mc.interactionManager == null) {
            return false;
        }

        int slot = findSlot(item);

        if (slot == -1) {
            return false;
        }

        int previousSlot =
                mc.player.getInventory().getSelectedSlot();

        mc.player.getInventory().setSelectedSlot(slot);

        mc.interactionManager.interactItem(
                mc.player,
                Hand.MAIN_HAND
        );

        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previousSlot);

        return true;
    }

    private int findSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player
                    .getInventory()
                    .getStack(slot)
                    .isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    private void restoreSlot() {
        if (mc.player != null
                && restoreSlot >= 0
                && restoreSlot < 9
                && mc.player
                .getInventory()
                .getSelectedSlot() != restoreSlot) {
            mc.player
                    .getInventory()
                    .setSelectedSlot(restoreSlot);
        }

        restoreSlot = -1;
    }

    private enum ThrowAction {
        NONE,
        PEARL_WIND,
        WIND_ONLY
    }

    private enum State {
        IDLE,
        WAIT_MOVEMENT_STOP,
        WAIT_THROW_ROTATION,
        WAIT_PEARL_SWAP,
        THROW_PEARL,
        WAIT_WIND_SWAP,
        WAIT_WIND_CHARGE,
        THROW_WIND_ONLY
    }

    private enum WindPhase {
        IDLE,
        PRE_STOP,
        STOPPING,
        WAIT_STOP,
        PRE_SWAP,
        SWAP_TO_HAND,
        AWAIT_ITEM,
        ROTATE_DOWN,
        THROW,
        POST_THROW,
        SWAP_BACK,
        RESUMING
    }
}
