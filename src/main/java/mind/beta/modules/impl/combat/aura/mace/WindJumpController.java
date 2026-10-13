/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.mace;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import mind.beta.IMinecraft;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.HotBarScrollEvent;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.mixin.ClientWorldAccessor;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationManager;
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

public final class WindJumpController implements IMinecraft {

    private final ModuleStructure owner;

    private final SelectSetting modeSetting = new SelectSetting("Wind Charge Mode")
            .value("Instant", "Legit")
            .selected("Legit");

    private final BindSetting keySetting = new BindSetting("Wind Charge Key", "Wind charge key");

    private enum Phase {
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

    private final MovementController movement = new MovementController();
    private static final float THROW_PITCH = 90f;

    private static final long SEQUENCE_TIMEOUT_MS = 4000L;
    private long sequenceStartTime;
    private Object sequencePlayer;
    private Object sequenceWorld;
    private boolean inventorySwapApplied;
    private int inventorySwapHotbarSlot = -1;
    private Phase phase = Phase.IDLE;
    private int savedSlot = -1;
    private int chargeSlot = -1;
    private boolean fromInventory = false;
    private long phaseStartTime = 0;
    private int currentDelay = 0;
    private long lastThrowTime = 0;
    private int rotationTicks = 0;
    private boolean pendingThrow = false;


    private int pendingJumpPlayerAge = -1;

    public WindJumpController(ModuleStructure owner) {
        this.owner = owner;
    }

    public SelectSetting getModeSetting() {
        return modeSetting;
    }

    public BindSetting getKeySetting() {
        return keySetting;
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public boolean isMovementBlocked() {
        return movement.isBlocked();
    }

    public String getDebugState() {
        return switch (phase) {
            case PRE_STOP -> "Pre stop";
            case STOPPING -> "Stopping";
            case WAIT_STOP -> "Wait stop";
            case PRE_SWAP -> "Pre swap";
            case SWAP_TO_HAND -> "Swap to hand";
            case AWAIT_ITEM -> "Await item";
            case ROTATE_DOWN -> "Rotate down";
            case THROW -> "Throw";
            case POST_THROW -> "Post throw";
            case SWAP_BACK -> "Swap back";
            case RESUMING -> "Resuming";
            default -> "Ready";
        };
    }

    public float getDebugRequiredPitch() {
        return isRunning() ? THROW_PITCH : Float.NaN;
    }

    public long getJumpRemainingMs() {
        if (pendingJumpPlayerAge < 0 || mc.player == null) {
            return -1L;
        }
        return Math.max(0, pendingJumpPlayerAge - mc.player.age) * 50L;
    }

    public void onKey(KeyEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (phase != Phase.IDLE) return;
        if (e.action() != 1) return;

        int bindKey = keySetting.getKey();
        int bindType = keySetting.getType();

        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) return;

        boolean matches = false;

        if (bindType == 2) {
            if (bindKey == BindComponent.MIDDLE_MOUSE_BIND
                    && e.type() == InputUtil.Type.MOUSE
                    && e.key() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                matches = true;
            }
        } else if (bindType == 0 && e.type() == InputUtil.Type.MOUSE) {
            matches = e.key() == bindKey;
        } else if (bindType == 1 && e.type() == InputUtil.Type.KEYSYM) {
            matches = e.key() == bindKey;
        }

        if (matches) {
            tryThrowCharge();
        }
    }

    public void onScroll(HotBarScrollEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (phase != Phase.IDLE) return;

        int bindKey = keySetting.getKey();
        int bindType = keySetting.getType();

        if (bindType != 2) return;

        boolean matches = false;
        if (bindKey == BindComponent.SCROLL_UP_BIND && e.getVertical() > 0) {
            matches = true;
        } else if (bindKey == BindComponent.SCROLL_DOWN_BIND && e.getVertical() < 0) {
            matches = true;
        }

        if (matches) {
            e.setCancelled(true);
            tryThrowCharge();
        }
    }

    private void tryThrowCharge() {
        if (isRunning() || !owner.isState() || mc.player == null || mc.world == null
                || mc.currentScreen != null || System.currentTimeMillis() - lastThrowTime < 100) return;
        lastThrowTime = System.currentTimeMillis();
        startChargeProcess();
    }

    public void onRotationUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == EventType.PRE) {
            if (phase == Phase.ROTATE_DOWN || phase == Phase.THROW) {
                Rotation throwRotation = new Rotation(mc.player.getYaw(), THROW_PITCH);
                RotationRequest config = new RotationRequest(new LinearRotation(), true, true);

                RotationManager.INSTANCE.rotateTo(
                        throwRotation,
                        3,
                        config,
                        TaskPriority.HIGH_IMPORTANCE_1,
                        owner
                );
            }
        }

        if (event.getType() == EventType.POST) {
            if (pendingThrow) {
                performThrow();
                pendingThrow = false;
            }
        }
    }

    private void performThrow() {
        if (mc.player == null) return;

        Rotation rotation = RotationManager.INSTANCE.getOrLockItemUseRotation();
        if (rotation == null || rotation.getPitch() < 80.0F) {
            return;
        }

        final Rotation finalRotation = rotation;
        sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND,
                sequence,
                finalRotation.getYaw(),
                finalRotation.getPitch()
        ));

        mc.player.swingHand(Hand.MAIN_HAND);


        if (modeSetting.isSelected("Instant")) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
            pendingJumpPlayerAge = -1;
        } else {
            pendingJumpPlayerAge = mc.player.age + 1;
        }

        SwapSettings settings = buildSettings();
        startPhase(Phase.POST_THROW, settings.randomPostSwapDelay());
    }

    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || !owner.isState()
                || (isRunning() && (mc.player != sequencePlayer || mc.world != sequenceWorld
                || mc.currentScreen != null
                || System.currentTimeMillis() - sequenceStartTime > SEQUENCE_TIMEOUT_MS))) {
            deactivate();
            return;
        }

        if (phase != Phase.IDLE) {
            boolean continueProcessing = true;
            int iterations = 0;

            while (continueProcessing && iterations < 10) {
                iterations++;
                continueProcessing = processTick();
            }
        }
    }

    public void onInput(InputEvent e) {
        if (mc.player == null) return;

        if (movement.isBlocked()) {
            e.setDirectionalLow(false, false, false, false);
            e.setJumping(false);
        }


        if (pendingJumpPlayerAge >= 0 && mc.player.age >= pendingJumpPlayerAge) {
            pendingJumpPlayerAge = -1;
            if (mc.player.isOnGround()) {
                e.setJumping(true);
            }
        }
    }

    private void startChargeProcess() {
        sequenceStartTime = System.currentTimeMillis();
        sequencePlayer = mc.player;
        sequenceWorld = mc.world;
        savedSlot = mc.player.getInventory().getSelectedSlot();

        int hotbarSlot = InventoryUtils.findItemInHotbar(Items.WIND_CHARGE);
        if (hotbarSlot != -1) {
            chargeSlot = hotbarSlot;
            fromInventory = false;
            InventoryUtils.selectSlot(chargeSlot);
            startPhase(Phase.AWAIT_ITEM, 0);
            return;
        }

        int invSlot = InventoryUtils.findItemInInventory(Items.WIND_CHARGE);
        if (invSlot != -1) {
            chargeSlot = invSlot;
            fromInventory = true;

            SwapSettings settings = buildSettings();
            if (settings.shouldStopMovement()) {
                startPhase(Phase.PRE_STOP, settings.randomPreStopDelay());
            } else {
                startPhase(Phase.SWAP_TO_HAND, 0);
            }
        } else {
            ChatMessage.brandmessage("No wind charges");
            resetState();
        }
    }

    private boolean processTick() {
        if (mc.player == null || mc.currentScreen != null) {
            resetState();
            return false;
        }

        long elapsed = System.currentTimeMillis() - phaseStartTime;
        SwapSettings settings = buildSettings();

        switch (phase) {
            case PRE_STOP -> {
                if (elapsed >= currentDelay) {
                    movement.saveState();
                    movement.block();
                    if (settings.shouldStopSprint()) {
                        movement.stopSprint();
                    }
                    startPhase(Phase.STOPPING, 0);
                    return true;
                }
            }
            case STOPPING -> {
                movement.block();
                if (settings.shouldStopSprint()) {
                    movement.stopSprint();
                }
                startPhase(Phase.WAIT_STOP, settings.randomWaitStopDelay());
                return currentDelay == 0;
            }
            case WAIT_STOP -> {
                movement.block();
                boolean stopped = movement.isPlayerStopped(settings.getVelocityThreshold());
                boolean timeout = elapsed >= currentDelay;

                if (stopped || timeout) {
                    startPhase(Phase.PRE_SWAP, settings.randomPreSwapDelay());
                    return currentDelay == 0;
                }
            }
            case PRE_SWAP -> {
                movement.block();
                if (elapsed >= currentDelay) {
                    startPhase(Phase.SWAP_TO_HAND, 0);
                    return true;
                }
            }
            case SWAP_TO_HAND -> {
                int hotbarSlot = mc.player.getInventory().getSelectedSlot();
                InventoryUtils.click(chargeSlot, hotbarSlot, SlotActionType.SWAP);
                inventorySwapApplied = true;
                inventorySwapHotbarSlot = hotbarSlot;
                startPhase(Phase.AWAIT_ITEM, 0);
                return true;
            }
            case AWAIT_ITEM -> {
                if (mc.player.getMainHandStack().getItem() == Items.WIND_CHARGE) {
                    rotationTicks = 0;
                    startPhase(Phase.ROTATE_DOWN, 0);
                    return true;
                }
            }
            case ROTATE_DOWN -> {
                rotationTicks++;

                RotationManager manager = RotationManager.INSTANCE;
                Rotation currentRotation = manager.getRotation();
                Rotation lockedRotation = manager.getItemUseRotationLock();

                boolean rotationReady = currentRotation != null
                        && currentRotation.getPitch() >= 80f;




                boolean usePacketRotationReady = lockedRotation == null
                        || lockedRotation.getPitch() >= 80f;

                boolean waitedEnough = rotationTicks >= 2;

                if (rotationReady && usePacketRotationReady && waitedEnough) {
                    startPhase(Phase.THROW, 0);
                    return true;
                }

                if (rotationTicks > 10) {
                    resetState();
                    return false;
                }
            }
            case THROW -> {
                pendingThrow = true;
                return false;
            }
            case POST_THROW -> {
                if (elapsed >= currentDelay) {
                    if (fromInventory) {
                        startPhase(Phase.SWAP_BACK, 0);
                        return true;
                    } else {
                        InventoryUtils.selectSlot(savedSlot);
                        startPhase(Phase.RESUMING, settings.randomResumeDelay());
                        return currentDelay == 0;
                    }
                }
            }
            case SWAP_BACK -> {
                InventoryUtils.click(chargeSlot, inventorySwapHotbarSlot, SlotActionType.SWAP);
                inventorySwapApplied = false;
                InventoryUtils.selectSlot(savedSlot);
                if (settings.shouldCloseInventory()) {
                    InventoryUtils.closeScreen();
                }
                startPhase(Phase.RESUMING, settings.randomResumeDelay());
                return currentDelay == 0;
            }
            case RESUMING -> {
                if (elapsed >= currentDelay) {
                    if (buildSettings().shouldStopMovement()) {
                        movement.restoreFromCurrent();
                    }
                    int jumpAge = pendingJumpPlayerAge;
                    resetState();
                    pendingJumpPlayerAge = jumpAge;
                    return false;
                }
            }
        }

        return false;
    }

    private void sendSequencedPacket(
            java.util.function.IntFunction<net.minecraft.network.packet.Packet<?>> packetCreator
    ) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.world == null) return;

        try {
            ClientWorldAccessor worldAccessor = (ClientWorldAccessor) mc.world;
            PendingUpdateManager pendingUpdateManager =
                    worldAccessor.getPendingUpdateManager().incrementSequence();

            int sequence = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.apply(sequence));

            pendingUpdateManager.close();
        } catch (Exception e) {
            mc.getNetworkHandler().sendPacket(packetCreator.apply(0));
        }
    }

    private SwapSettings buildSettings() {
        String mode = modeSetting.getSelected();
        return switch (mode) {
            case "Instant" -> SwapSettings.instant();
            default -> SwapSettings.legit();
        };
    }

    private void startPhase(Phase newPhase, int delay) {
        this.phase = newPhase;
        this.phaseStartTime = System.currentTimeMillis();
        this.currentDelay = delay;
    }

    private void resetState() {
        if (mc.player != null && mc.world != null
                && mc.player == sequencePlayer && mc.world == sequenceWorld) {
            if (inventorySwapApplied && chargeSlot >= 0 && inventorySwapHotbarSlot >= 0) {
                InventoryUtils.click(chargeSlot, inventorySwapHotbarSlot, SlotActionType.SWAP);
            }
            if (savedSlot >= 0 && savedSlot < 9) {
                InventoryUtils.selectSlot(savedSlot);
            }
            if (movement.isBlocked()) {
                movement.restoreFromCurrent();
            }
        }
        if (phase != Phase.IDLE) {
            RotationManager.INSTANCE.releaseControl(owner);
        }
        inventorySwapApplied = false;
        inventorySwapHotbarSlot = -1;
        sequenceStartTime = 0L;
        sequencePlayer = null;
        sequenceWorld = null;
        pendingJumpPlayerAge = -1;
        movement.reset();
        phase = Phase.IDLE;
        savedSlot = -1;
        chargeSlot = -1;
        fromInventory = false;
        phaseStartTime = 0;
        currentDelay = 0;
        rotationTicks = 0;
        pendingThrow = false;
    }

    public void deactivate() {
        resetState();
    }
}
