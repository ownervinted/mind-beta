/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.math.TaskPriority;

import java.util.LinkedHashSet;
import java.util.Set;

public class WebTrap extends ModuleStructure {

    public static WebTrap getInstance() {
        return Instance.get(WebTrap.class);
    }

    private final BindSetting trapKey = new BindSetting(
            "Web Key",
            "Places cobwebs into the nearest target"
    );

    private final SelectSetting websPerPress = new SelectSetting(
            "Webs / Press"
    )
            .value("1", "2")
            .selected("1");

    private final SliderSettings targetRange = new SliderSettings(
            "Target Range",
            "Target search distance"
    )
            .range(2.0f, 6.0f)
            .setValue(4.5f);

    private final SliderSettings placeRange = new SliderSettings(
            "Place Range",
            "Maximum block interaction distance"
    )
            .range(2.0f, 5.0f)
            .setValue(4.5f);

    private final SliderSettings placeDelay = new SliderSettings(
            "Place Delay",
            "Ticks between the first and second web"
    )
            .range(0.0f, 4.0f)
            .setValue(1.0f);

    private final SliderSettings prediction = new SliderSettings(
            "Prediction",
            "Target movement prediction in ticks"
    )
            .range(0.0f, 2.0f)
            .setValue(0.5f);

    private final BooleanSetting rotate = new BooleanSetting(
            "Rotate",
            "Uses RotationManager for placement"
    )
            .setValue(true);

    private final BooleanSetting restoreSlot = new BooleanSetting(
            "Restore Slot",
            "Returns to the previous hotbar slot after the action"
    )
            .setValue(true);

    private final RotationConfig rotationConfig = new RotationConfig(
            110.0f,
            85.0f,
            70.0f,
            95.0f,
            75.0f,
            0.72f,
            0.68f,
            0.96f
    );

    private final RotationRequest rotationRequest = new RotationRequest(
            new LinearRotation(),
            false,
            true
    );

    private static final Direction[] PLACE_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP
    };

    private PlayerEntity target;
    private Placement placement;
    private Rotation desiredRotation;

    private boolean trapping;
    private int webSlot = -1;
    private int previousSlot = -1;
    private int delayTicks;
    private int sequenceTicks;
    private int placedThisPress;
    private int alignedTicks;

    public WebTrap() {
        super("WebTrap", "Places one or two cobwebs into a nearby target by bind", ModuleCategory.COMBAT);
        settings(
                trapKey,
                websPerPress,
                targetRange,
                placeRange,
                placeDelay,
                prediction,
                rotate,
                restoreSlot
        );
    }

    @Override
    public void deactivate() {
        finishTrap();
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (trapKey.getKey() == -1 || !event.isKeyDown(trapKey.getKey())) {
            return;
        }

        if (trapping) {
            finishTrap();
        }

        startTrap();
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!trapping || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            updateTrap();
            return;
        }

        if (event.getType() == EventType.POST) {
            placeCurrent();
        }
    }

    private void startTrap() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        webSlot = findWebSlot();
        if (webSlot == -1) {
            return;
        }

        target = findTarget();
        if (target == null) {
            return;
        }

        previousSlot = mc.player.getInventory().getSelectedSlot();
        placement = null;
        desiredRotation = null;
        delayTicks = 0;
        sequenceTicks = 0;
        placedThisPress = 0;
        alignedTicks = 0;
        trapping = true;
    }

    private void updateTrap() {
        sequenceTicks++;

        if (sequenceTicks > 24 || !isTargetValid(target)) {
            finishTrap();
            return;
        }

        if (placedThisPress >= getMaxPlacements()) {
            finishTrap();
            return;
        }

        webSlot = findWebSlot();
        if (webSlot == -1) {
            finishTrap();
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (placement == null || !canPlaceWeb(placement.targetPos())) {
            placement = findNextPlacement();
            desiredRotation = null;
            alignedTicks = 0;

            if (placement == null) {
                finishTrap();
                return;
            }
        }

        if (!rotate.isValue()) {
            return;
        }

        desiredRotation = RotationUtils.calculateAngle(placement.hitVec());

        RotationManager.INSTANCE.setRotationConfig(rotationConfig);
        RotationManager.INSTANCE.rotateTo(
                rotationRequest.createRotationPlan(
                        desiredRotation,
                        placement.hitVec(),
                        target,
                        true,
                        false
                ),
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }

    private void placeCurrent() {
        if (!trapping || placement == null || delayTicks > 0) {
            return;
        }

        if (!isTargetValid(target) || !canPlaceWeb(placement.targetPos())) {
            placement = null;
            desiredRotation = null;
            alignedTicks = 0;
            return;
        }

        if (webSlot < 0 || webSlot > 8) {
            finishTrap();
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != webSlot) {
            mc.player.getInventory().setSelectedSlot(webSlot);
            alignedTicks = 0;
            return;
        }

        BlockHitResult hitResult = placement.hitResult();

        if (rotate.isValue()) {
            if (desiredRotation == null) {
                alignedTicks = 0;
                return;
            }

            Rotation current = RotationManager.INSTANCE.getRotation();
            double difference = RotationManager.computeRotationDifference(current, desiredRotation);

            if (difference > 1.25) {
                alignedTicks = 0;
                return;
            }

            alignedTicks++;

            if (alignedTicks < 2) {
                return;
            }

            Vec3d eyes = mc.player.getEyePos();
            Vec3d end = eyes.add(current.toVector().multiply(placeRange.getValue()));

            BlockHitResult ray = mc.world.raycast(
                    new RaycastContext(
                            eyes,
                            end,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    )
            );

            if (ray.getType() != HitResult.Type.BLOCK
                    || !ray.getBlockPos().equals(placement.blockPos())
                    || ray.getSide() != placement.face()) {
                return;
            }

            hitResult = new BlockHitResult(
                    ray.getPos(),
                    ray.getSide(),
                    ray.getBlockPos(),
                    false
            );
        }

        ActionResult result = mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                hitResult
        );

        if (!result.isAccepted()) {
            return;
        }

        mc.player.swingHand(Hand.MAIN_HAND);

        placedThisPress++;
        placement = null;
        desiredRotation = null;
        alignedTicks = 0;

        if (placedThisPress >= getMaxPlacements()) {
            finishTrap();
            return;
        }

        delayTicks = Math.max(0, Math.round(placeDelay.getValue()));
    }

    private Placement findNextPlacement() {
        Set<BlockPos> positions = new LinkedHashSet<>();

        Vec3d predicted = target.getEntityPos().add(
                target.getVelocity().multiply(prediction.getValue())
        );

        BlockPos predictedFeet = BlockPos.ofFloored(predicted);
        BlockPos currentFeet = BlockPos.ofFloored(target.getEntityPos());

        if (placedThisPress == 0) {
            positions.add(predictedFeet);
            positions.add(currentFeet);
            positions.add(predictedFeet.up());
            positions.add(currentFeet.up());
        } else {
            positions.add(predictedFeet.up());
            positions.add(currentFeet.up());
            positions.add(predictedFeet);
            positions.add(currentFeet);
        }

        for (BlockPos pos : positions) {
            if (!canPlaceWeb(pos)) {
                continue;
            }

            Placement candidate = findPlacement(pos);

            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private Placement findPlacement(BlockPos targetPos) {
        Placement best = null;
        double bestDistance = Double.MAX_VALUE;
        double maxRange = placeRange.getValue();
        double maxRangeSq = maxRange * maxRange;

        for (Direction direction : PLACE_DIRECTIONS) {
            BlockPos neighbour = targetPos.offset(direction);

            if (!isValidNeighbour(neighbour)) {
                continue;
            }

            Direction face = direction.getOpposite();

            Vec3d hitVec = Vec3d.ofCenter(neighbour).add(
                    face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5,
                    face.getOffsetZ() * 0.5
            );

            double distance = mc.player.getEyePos().squaredDistanceTo(hitVec);

            if (distance > maxRangeSq || distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;

            best = new Placement(
                    targetPos,
                    neighbour,
                    face,
                    hitVec,
                    new BlockHitResult(
                            hitVec,
                            face,
                            neighbour,
                            false
                    )
            );
        }

        return best;
    }

    private boolean canPlaceWeb(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);

        if (state.isOf(Blocks.COBWEB)) {
            return false;
        }

        return state.isReplaceable();
    }

    private boolean isValidNeighbour(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);

        return !state.isAir() && !state.isReplaceable();
    }

    private int findWebSlot() {
        if (mc.player == null) {
            return -1;
        }

        int selected = mc.player.getInventory().getSelectedSlot();

        if (selected >= 0 && selected < 9 && isWeb(mc.player.getInventory().getStack(selected))) {
            return selected;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isWeb(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isWeb(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.COBWEB);
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = targetRange.getValue();
        double rangeSq = range * range;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(player);

            if (distance > rangeSq || distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;
            best = player;
        }

        return best;
    }

    private boolean isTargetValid(PlayerEntity player) {
        if (player == null || mc.player == null || mc.world == null) {
            return false;
        }

        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }

        double range = targetRange.getValue() + 0.75f;
        return mc.player.squaredDistanceTo(player) <= range * range;
    }

    private int getMaxPlacements() {
        return websPerPress.isSelected("2") ? 2 : 1;
    }

    private void finishTrap() {
        boolean hadRotation = trapping && rotate.isValue();

        trapping = false;
        target = null;
        placement = null;
        desiredRotation = null;
        webSlot = -1;
        delayTicks = 0;
        sequenceTicks = 0;
        placedThisPress = 0;
        alignedTicks = 0;

        if (mc.player != null
                && restoreSlot.isValue()
                && previousSlot >= 0
                && previousSlot < 9
                && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }

        previousSlot = -1;

        if (hadRotation) {
            RotationManager.INSTANCE.startReturning(this);
        }
    }

    private record Placement(
            BlockPos targetPos,
            BlockPos blockPos,
            Direction face,
            Vec3d hitVec,
            BlockHitResult hitResult
    ) {
    }
}
