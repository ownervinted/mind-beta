/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.HotBarScrollEvent;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.screens.clickgui.impl.settingsrender.BindComponent;
import mind.beta.util.Instance;
import mind.beta.util.inventory.InventoryUtils;
import mind.beta.util.inventory.MovementController;
import mind.beta.util.inventory.SwapSettings;
import mind.beta.util.string.chat.ChatMessage;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClickPearl extends ModuleStructure {

    public static ClickPearl getInstance() {
        return Instance.get(ClickPearl.class);
    }

    final SelectSetting modeSetting = new SelectSetting("Mode")
            .value("Instant", "Legit")
            .selected("Legit");

    final BindSetting keySetting = new BindSetting("Key", "Throw key");

    enum Phase {
        IDLE, PRE_STOP, STOPPING, WAIT_STOP, PRE_SWAP, SWAP_TO_HAND, AWAIT_ITEM, THROW, POST_THROW, SWAP_BACK, RESUMING
    }

    final MovementController movement = new MovementController();

    Phase phase = Phase.IDLE;
    int savedSlot = -1;
    int pearlSlot = -1;
    boolean fromInventory = false;
    long phaseStartTime = 0;
    int currentDelay = 0;
    long lastThrowTime = 0;

    boolean bindHeld = false;
    boolean releaseRequested = false;
    boolean rotationOverrideActive = false;
    int slotReadyAge = Integer.MIN_VALUE;

    public ClickPearl() {
        super("ClickPearl", "Click Pearl", ModuleCategory.MISC);
        settings(modeSetting, keySetting);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

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

        if (!matches) return;

        if (e.action() == 1) {
            if (phase != Phase.IDLE || bindHeld || releaseRequested) return;
            beginManualAim();
            return;
        }

        if (e.action() == 0 && bindHeld) {
            bindHeld = false;
            releaseRequested = true;
        }
    }

    @EventHandler
    public void onScroll(HotBarScrollEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (phase != Phase.IDLE || bindHeld || releaseRequested || rotationOverrideActive) return;

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
            tryThrowPearl();
        }
    }

    private void beginManualAim() {
        bindHeld = true;
        releaseRequested = false;
        rotationOverrideActive = true;

        Aura aura = Aura.getInstance();

        if (aura != null && aura.isState()) {
            RotationManager.INSTANCE.startReturning(aura);
        }
    }

    private boolean isCameraRotationReady() {
        Aura aura = Aura.getInstance();

        if (aura == null || !aura.isState()) {
            return true;
        }

        return RotationManager.INSTANCE.isReturningSettled();
    }

    private void tryThrowPearl() {
        if (System.currentTimeMillis() - lastThrowTime < 100) return;
        lastThrowTime = System.currentTimeMillis();
        startPearlProcess();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        if (bindHeld) {
            return;
        }

        if (releaseRequested && phase == Phase.IDLE) {
            if (!isCameraRotationReady()) {
                return;
            }

            releaseRequested = false;
            tryThrowPearl();
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

    @EventHandler
    public void onInput(InputEvent e) {
        if (mc.player == null) return;
        if (movement.isBlocked()) {
            e.setDirectionalLow(false, false, false, false);
            e.setJumping(false);
        }
    }

    private void startPearlProcess() {
        savedSlot = mc.player.getInventory().getSelectedSlot();

        int hotbarSlot = InventoryUtils.findItemInHotbar(Items.ENDER_PEARL);
        if (hotbarSlot != -1) {
            pearlSlot = hotbarSlot;
            fromInventory = false;

            mc.player.getInventory().setSelectedSlot(pearlSlot);
            slotReadyAge = mc.player.age + 1;

            startPhase(Phase.AWAIT_ITEM, 0);
            return;
        }

        int invSlot = InventoryUtils.findItemInInventory(Items.ENDER_PEARL);
        if (invSlot != -1) {
            pearlSlot = invSlot;
            fromInventory = true;

            SwapSettings settings = buildSettings();
            if (settings.shouldStopMovement()) {
                startPhase(Phase.PRE_STOP, settings.randomPreStopDelay());
            } else {
                startPhase(Phase.SWAP_TO_HAND, 0);
            }
        } else {
            ChatMessage.brandmessage("No ender pearls");
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
                InventoryUtils.click(pearlSlot, hotbarSlot, SlotActionType.SWAP);

                slotReadyAge = mc.player.age + 1;

                startPhase(Phase.AWAIT_ITEM, 0);
                return false;
            }
            case AWAIT_ITEM -> {
                if (mc.player.age < slotReadyAge) {
                    return false;
                }

                if (mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                    startPhase(Phase.THROW, 0);
                    return true;
                }
            }
            case THROW -> {
                if (mc.interactionManager == null
                        || !mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                    resetState();
                    return false;
                }

                mc.interactionManager.interactItem(
                        mc.player,
                        Hand.MAIN_HAND
                );

                mc.player.swingHand(Hand.MAIN_HAND);

                startPhase(
                        Phase.POST_THROW,
                        settings.randomPostSwapDelay()
                );

                return currentDelay == 0;
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
                int hotbarSlot = mc.player.getInventory().getSelectedSlot();
                InventoryUtils.click(pearlSlot, hotbarSlot, SlotActionType.SWAP);
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
                    resetState();
                    return false;
                }
            }
        }
        return false;
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
        movement.reset();
        phase = Phase.IDLE;
        savedSlot = -1;
        pearlSlot = -1;
        fromInventory = false;
        phaseStartTime = 0;
        currentDelay = 0;
        bindHeld = false;
        releaseRequested = false;
        rotationOverrideActive = false;
        slotReadyAge = Integer.MIN_VALUE;
    }

    public boolean isRotationOverrideActive() {
        return rotationOverrideActive;
    }

    public boolean isBindHeld() {
        return bindHeld;
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    @Override
    public void deactivate() {
        if (movement.isBlocked()) {
            movement.restoreFromCurrent();
        }
        resetState();
    }
}