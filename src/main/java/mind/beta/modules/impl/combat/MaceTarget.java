/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.impl.combat.macetarget.armor.ArmorSwapHandler;
import mind.beta.modules.impl.combat.macetarget.armor.FireworkHandler;
import mind.beta.modules.impl.combat.macetarget.attack.AttackHandler;
import mind.beta.modules.impl.combat.macetarget.flight.FlightController;
import mind.beta.modules.impl.combat.macetarget.prediction.TargetPredictor;
import mind.beta.modules.impl.combat.macetarget.stage.StageHandler;
import mind.beta.modules.impl.combat.macetarget.state.MaceState.Stage;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.inventory.InventoryUtils;
import mind.beta.util.inventory.SwapSettings;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaceTarget extends ModuleStructure {

    public static MaceTarget getInstance() {
        return Instance.get(MaceTarget.class);
    }

    final SelectSetting serverMode = new SelectSetting("Server")
            .value("Default", "Matrix")
            .selected("Default");

    final SelectSetting modeSetting = new SelectSetting("Mode")
            .value("Silent", "Legit")
            .selected("Silent");

    final SliderSettings height = new SliderSettings("Height", "Flight altitude above the target")
            .range(20.0f, 60.0f)
            .setValue(30.0f);

    final MultiSelectSetting targetType = new MultiSelectSetting("Targets", "Target types")
            .value("Players", "Mobs", "Animals")
            .selected("Players");

    final BooleanSetting autoEquipChest = new BooleanSetting("Auto Chestplate", "Equip a chestplate when disabled")
            .setValue(true);

    final BooleanSetting predictMovement = new BooleanSetting("Prediction", "Predict the position of a fleeing target")
            .setValue(true);

    final TargetPredictor predictor = new TargetPredictor();
    final FlightController flightController;
    final ArmorSwapHandler armorSwapHandler;
    final FireworkHandler fireworkHandler;
    final AttackHandler attackHandler = new AttackHandler();
    final StageHandler stageHandler;
    final TargetSelector targetSelector = new TargetSelector();
    final StopWatch fireworkTimer = new StopWatch();

    LivingEntity target;

    public MaceTarget() {
        super("MaceTarget", "Mace Target", ModuleCategory.COMBAT);
        settings(serverMode, modeSetting, height, targetType, autoEquipChest, predictMovement);

        flightController = new FlightController(predictor);
        armorSwapHandler = new ArmorSwapHandler(this::buildSettings);
        fireworkHandler = new FireworkHandler(this::buildSettings);
        stageHandler = new StageHandler(armorSwapHandler, fireworkHandler, attackHandler, fireworkTimer);
    }

    private boolean isSilentMode() {
        return modeSetting.getSelected().equals("Silent");
    }

    private boolean isMatrixMode() {
        return serverMode.getSelected().equals("Matrix");
    }

    private SwapSettings buildSettings() {
        return isSilentMode() ? SwapSettings.instant() : SwapSettings.legit();
    }

    private void updateHandlers() {
        stageHandler.setSilentMode(isSilentMode());
        stageHandler.setMatrixMode(isMatrixMode());
        stageHandler.setHeight(height.getValue());
        flightController.setPredictionEnabled(predictMovement.isValue());
        flightController.setHeight(height.getValue());
    }

    @Override
    public void activate() {
        stageHandler.reset();
        target = null;
        attackHandler.reset();
        armorSwapHandler.reset();
        fireworkHandler.reset();
        predictor.reset();
        fireworkTimer.reset();
    }

    @Override
    public void deactivate() {
        if (autoEquipChest.isValue() && mc.player != null) {
            equipChestplateOnDisable();
        }

        armorSwapHandler.forceRestore();
        fireworkHandler.forceRestore();
        target = null;
        targetSelector.releaseTarget();
        armorSwapHandler.reset();
        fireworkHandler.reset();
        predictor.reset();
        RotationManager.INSTANCE.startReturning();
    }

    private void equipChestplateOnDisable() {
        if (InventoryUtils.hasElytra()) {
            int slot = InventoryUtils.findChestArmorSlot();
            if (slot != -1) {
                int wrappedSlot = InventoryUtils.wrapSlot(slot);
                InventoryUtils.swap(wrappedSlot, 6);
                InventoryUtils.closeScreen();
            }
        }
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == EventType.PRE) {
            updateHandlers();

            if (target == null || !target.isAlive()) {
                findTarget();
            }

            if (target == null) return;

            predictor.update(target);

            Stage currentStage = stageHandler.getStage();

            switch (currentStage) {
                case FLYING_UP -> {
                    if (InventoryUtils.hasElytra() && mc.player.isGliding()) {
                        Rotation targetRotation = flightController.calculateAngle(target, currentStage);
                        rotateTo(targetRotation);
                    }
                }
                case TARGETTING, ATTACKING -> {
                    Rotation targetRotation = flightController.calculateAngle(target, currentStage);
                    rotateTo(targetRotation);
                }
            }
        }

        if (event.getType() == EventType.POST) {
            handlePostRotation();
        }
    }

    private void handlePostRotation() {
        if (attackHandler.isPendingAttack()) {
            attackHandler.performAttack(target);
            attackHandler.setPendingAttack(false);

            if (attackHandler.isShouldDisableAfterAttack()) {
                attackHandler.setShouldDisableAfterAttack(false);
                setState(false);
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            resetAllStates();
            return;
        }

        if (!isSilentMode()) {
            armorSwapHandler.processLoop();
            fireworkHandler.processLoop();
        }

        if (armorSwapHandler.isActive() || fireworkHandler.isActive()) {
            return;
        }

        if (target == null || !target.isAlive()) {
            return;
        }

        processStage();
    }

    private void processStage() {
        boolean hasElytra = InventoryUtils.hasElytra();
        Stage currentStage = stageHandler.getStage();

        switch (currentStage) {
            case PREPARE -> stageHandler.handlePrepare(hasElytra);
            case FLYING_UP -> stageHandler.handleFlyingUp(target, hasElytra);
            case TARGETTING -> stageHandler.handleTargetting(target);
            case ATTACKING -> stageHandler.handleAttacking(target, hasElytra);
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null) return;

        if (armorSwapHandler.getMovement().isBlocked() || fireworkHandler.getMovement().isBlocked()) {
            event.setDirectionalLow(false, false, false, false);
            event.setJumping(false);
        }

        if (target != null && InventoryUtils.hasElytra() && stageHandler.getStage() == Stage.FLYING_UP) {
            if (mc.player.isOnGround()) {
                event.setJumping(true);
            } else if (!mc.player.isGliding() && !mc.player.getAbilities().flying) {
                event.setJumping(mc.player.age % 2 == 0);
            }
        }
    }

    private void findTarget() {
        TargetSelector.EntityFilter filter = new TargetSelector.EntityFilter(targetType.getSelected());
        targetSelector.searchTargets(mc.world.getEntities(), 128.0f, 360, true);
        targetSelector.validateTarget(filter::isValid);
        target = targetSelector.getCurrentTarget();
    }

    private void rotateTo(Rotation angle) {
        RotationRequest config = new RotationRequest(new LinearRotation(), true, false);
        Rotation.VecRotation rotation = new Rotation.VecRotation(angle, angle.toVector());
        RotationManager.INSTANCE.rotateTo(rotation, target, 1, config, TaskPriority.HIGH_IMPORTANCE_1, this);
    }

    private void resetAllStates() {
        armorSwapHandler.reset();
        fireworkHandler.reset();
        attackHandler.reset();
        predictor.reset();
        target = null;
        stageHandler.reset();
    }
}