/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import mind.beta.events.impl.InputEvent;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.util.Instance;
import mind.beta.util.math.TaskPriority;


public class Scaffold extends ModuleStructure {

    public static Scaffold getInstance() {
        return Instance.get(Scaffold.class);
    }

    private final BooleanSetting rotation = new BooleanSetting(
            "Rotation",
            "Turn away from the movement direction and look down"
    ).setValue(true);

    private final BooleanSetting keepY = new BooleanSetting(
            "Keep Y",
            "Keep the height at which Scaffold was enabled"
    ).setValue(false);

    private final RotationConfig scaffoldRotationConfig = new RotationConfig(
            180.0f,
            180.0f,
            120.0f,
            180.0f,
            90.0f,
            1.0f,
            0.85f,
            1.0f
    );

    private final RotationRequest scaffoldRotationRequest = new RotationRequest(
            new LinearRotation(),
            true,
            true
    );

    private int startY;
    private int previousSlot = -1;
    private int blockSlot = -1;


    private float lockedYaw;
    private boolean hasLockedYaw;
    private float bridgeYaw;
    private boolean hasBridgeYaw;
    private float previousMovementYaw;

    private Placement placement;
    private BlockPos lastPlacementTarget;

    public Scaffold() {
        super("Scaffold", ModuleCategory.MOVEMENT);
        settings(rotation, keepY);
    }

    @Override
    public void activate() {
        if (mc.player == null) {
            return;
        }

        startY = MathHelper.floor(mc.player.getY()) - 1;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        blockSlot = -1;
        placement = null;
        lastPlacementTarget = null;

        lockedYaw = MathHelper.wrapDegrees(getMovementYaw() + 180.0f);
        hasLockedYaw = true;
        hasBridgeYaw = false;
        previousMovementYaw = getMovementYaw();

        RotationManager.INSTANCE.cancelReturning();
    }

    @Override
    public void deactivate() {
        placement = null;
        lastPlacementTarget = null;
        blockSlot = -1;
        hasLockedYaw = false;
        hasBridgeYaw = false;

        if (mc.player != null
                && previousSlot >= 0
                && previousSlot < 9
                && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }

        previousSlot = -1;

        if (rotation.isValue()) {
            RotationManager.INSTANCE.startReturning();
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null) {
            return;
        }

        event.setSprinting(false);

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }


    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            updatePlacement();
            updateRotation();
            return;
        }

        if (event.getType() == EventType.POST) {
            place();
        }
    }

    public void prepareMovementRotation() {
        if (!isState() || !rotation.isValue()
                || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.hasVehicle()) {
            return;
        }

        RotationManager manager = RotationManager.INSTANCE;
        if (manager.getItemUseRotationLock() != null) {
            return;
        }

        updatePlacement();
        if (placement == null || !isFaceAccessible(placement.blockPos, placement.face)) {
            return;
        }

        float reference = MathHelper.wrapDegrees(getMovementYaw() + 180f);
        Vec3d point = createFacePoint(placement.blockPos, placement.face, reference);
        if (point == null) {
            return;
        }

        placement = new Placement(placement.targetPos, placement.blockPos, placement.face, point);
        Rotation target = rotationTo(point);
        Rotation sent = manager.getServerRotation();
        Rotation aim = new Rotation(
                sent.getYaw() + MathHelper.wrapDegrees(target.getYaw() - sent.getYaw()),
                target.getPitch()
        ).adjustSensitivity();
        if (!matchesPlacement(raycastRotation(aim))) {
            return;
        }
        bridgeYaw = aim.getYaw();
        hasBridgeYaw = true;
        manager.currentRotation = aim;
    }

    private void updateRotation() {
        if (!rotation.isValue()) {
            return;
        }

        RotationManager.INSTANCE.setRotationConfig(scaffoldRotationConfig);

        float movementYaw = getMovementYaw();
        if (hasMovementInput() && Math.abs(MathHelper.wrapDegrees(movementYaw - previousMovementYaw)) > 10f) {
            hasBridgeYaw = false;
        }
        if (hasMovementInput()) {
            previousMovementYaw = movementYaw;
        }
        lockedYaw = MathHelper.wrapDegrees(previousMovementYaw + 180f);
        hasLockedYaw = true;

        Rotation targetRotation;
        if (placement != null) {
            targetRotation = rotationTo(placement.hitVec);
            bridgeYaw = targetRotation.getYaw();
            hasBridgeYaw = true;
        } else {
            targetRotation = new Rotation(hasBridgeYaw ? bridgeYaw : lockedYaw, 82f);
        }

        RotationManager.INSTANCE.rotateTo(
                targetRotation,
                2,
                scaffoldRotationRequest,
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }

    private Rotation rotationTo(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = point.subtract(eyes);

        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        return new Rotation(
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0f, 90.0f)
        );
    }

    private void updatePlacement() {
        blockSlot = findBlockSlot();

        if (blockSlot == -1) {
            placement = null;
            lastPlacementTarget = null;
            return;
        }

        int targetY = keepY.isValue()
                ? startY
                : MathHelper.floor(mc.player.getY()) - 1;

        BlockPos target = BlockPos.ofFloored(
                mc.player.getX(),
                targetY,
                mc.player.getZ()
        );

        if (!canReplace(target)) {
            placement = null;
            lastPlacementTarget = null;
            return;
        }

        if (placement != null
                && lastPlacementTarget != null
                && lastPlacementTarget.equals(target)
                && canReplace(target)) {
            return;
        }

        placement = findPlacement(target);
        lastPlacementTarget = placement != null ? target : null;
    }

    private Placement findPlacement(BlockPos target) {
        Direction[] directions = {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.DOWN
        };

        Placement best = null;
        float bestDifference = Float.MAX_VALUE;
        float reference = MathHelper.wrapDegrees(getMovementYaw() + 180f);
        for (Direction direction : directions) {
            BlockPos neighbour = target.offset(direction);
            if (!isValidNeighbour(neighbour)) {
                continue;
            }
            Direction face = direction.getOpposite();
            if (!isFaceAccessible(neighbour, face)) {
                continue;
            }
            Vec3d hitVec = createFacePoint(neighbour, face, reference);
            if (hitVec == null) {
                continue;
            }
            float difference = Math.abs(MathHelper.wrapDegrees(rotationTo(hitVec).getYaw() - reference));
            if (difference < bestDifference) {
                bestDifference = difference;
                best = new Placement(target, neighbour, face, hitVec);
            }
        }
        return best;
    }

    private Vec3d createFacePoint(BlockPos blockPos, Direction face, float yaw) {
        Vec3d eyes = mc.player.getEyePos();
        double radians = Math.toRadians(yaw);
        double dx = -Math.sin(radians);
        double dz = Math.cos(radians);
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 0.7;
        double z = blockPos.getZ() + 0.5;
        switch (face) {
            case NORTH, SOUTH -> {
                z = blockPos.getZ() + (face == Direction.SOUTH ? 1.0 : 0.0);
                if (Math.abs(dz) > 1.0E-6) {
                    double t = (z - eyes.z) / dz;
                    if (t > 0.0) {
                        x = MathHelper.clamp(eyes.x + dx * t, blockPos.getX() + 0.08, blockPos.getX() + 0.92);
                    }
                }
            }
            case WEST, EAST -> {
                x = blockPos.getX() + (face == Direction.EAST ? 1.0 : 0.0);
                if (Math.abs(dx) > 1.0E-6) {
                    double t = (x - eyes.x) / dx;
                    if (t > 0.0) {
                        z = MathHelper.clamp(eyes.z + dz * t, blockPos.getZ() + 0.08, blockPos.getZ() + 0.92);
                    }
                }
            }
            case UP -> y = blockPos.getY() + 1.0;
            case DOWN -> y = blockPos.getY();
        }
        Vec3d point = new Vec3d(x, y, z);
        if (isRayValid(blockPos, face, point)) {
            return point;
        }
        point = new Vec3d(
                face == Direction.WEST || face == Direction.EAST ? x : blockPos.getX() + 0.5,
                y,
                face == Direction.NORTH || face == Direction.SOUTH ? z : blockPos.getZ() + 0.5
        );
        return isRayValid(blockPos, face, point) ? point : null;
    }

    private boolean isFaceAccessible(BlockPos pos, Direction face) {
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) {
            return false;
        }
        Box box = shape.getBoundingBox().offset(pos);
        Vec3d eyes = mc.player.getEyePos();
        return isOutsideFace(eyes, box, face);
    }

    private boolean isOutsideFace(Vec3d eyes, Box box, Direction face) {
        double margin = 0.04;
        return switch (face) {
            case NORTH -> eyes.z <= box.minZ - margin;
            case SOUTH -> eyes.z >= box.maxZ + margin;
            case WEST -> eyes.x <= box.minX - margin;
            case EAST -> eyes.x >= box.maxX + margin;
            case UP -> eyes.y >= box.maxY + margin;
            case DOWN -> eyes.y <= box.minY - margin;
        };
    }

    private boolean isRayValid(BlockPos blockPos, Direction face, Vec3d point) {
        BlockHitResult hit = raycastTo(point);

        return hit != null
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(blockPos)
                && hit.getSide() == face;
    }

    private BlockHitResult raycastTo(Vec3d point) {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = point.subtract(eyes);

        if (delta.lengthSquared() <= 1.0E-6) {
            return null;
        }

        Vec3d direction = delta.normalize();
        Vec3d end = eyes.add(direction.multiply(Math.min(4.5, delta.length() + 0.05)));

        return mc.world.raycast(
                new RaycastContext(
                        eyes,
                        end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );
    }

    private void place() {
        if (placement == null || blockSlot == -1) {
            return;
        }

        if (!canReplace(placement.targetPos) || !isFaceAccessible(placement.blockPos, placement.face)) {
            placement = null;
            lastPlacementTarget = null;
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != blockSlot) {
            mc.player.getInventory().setSelectedSlot(blockSlot);
            return;
        }

        RotationManager manager = RotationManager.INSTANCE;
        BlockHitResult ray = raycastRotation(manager.getServerRotation());

        if (!matchesPlacement(ray)) {
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                ray
        );

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            placement = null;
            lastPlacementTarget = null;
        }
    }

    private BlockHitResult raycastRotation(Rotation lookRotation) {
        if (lookRotation == null) {
            return null;
        }

        Vec3d eyes = mc.player.getEyePos();
        return mc.world.raycast(
                new RaycastContext(
                        eyes,
                        eyes.add(lookRotation.toVector().multiply(4.5)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );
    }

    private boolean matchesPlacement(BlockHitResult ray) {
        return ray != null
                && ray.getType() == HitResult.Type.BLOCK
                && ray.getBlockPos().equals(placement.blockPos)
                && ray.getSide() == placement.face;
    }

    private int findBlockSlot() {
        if (mc.player == null) {
            return -1;
        }

        int current = mc.player.getInventory().getSelectedSlot();

        if (current >= 0 && current < 9 && isPlaceableBlock(mc.player.getInventory().getStack(current))) {
            return current;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isPlaceableBlock(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isPlaceableBlock(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem;
    }

    private boolean canReplace(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isReplaceable();
    }

    private boolean isValidNeighbour(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);

        return !state.isAir()
                && !state.isReplaceable()
                && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean hasMovementInput() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    private float getMovementYaw() {
        float yaw = mc.player.getYaw();

        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();

        float forwardValue = 0.0f;

        if (forward) {
            forwardValue += 1.0f;
        }

        if (back) {
            forwardValue -= 1.0f;
        }

        float strafeValue = 0.0f;

        if (left) {
            strafeValue += 1.0f;
        }

        if (right) {
            strafeValue -= 1.0f;
        }

        if (forwardValue == 0.0f && strafeValue == 0.0f) {
            return yaw;
        }

        if (forwardValue < 0.0f) {
            yaw += 180.0f;
        }

        float factor = 1.0f;

        if (forwardValue < 0.0f) {
            factor = -0.5f;
        } else if (forwardValue > 0.0f) {
            factor = 0.5f;
        }

        if (strafeValue > 0.0f) {
            yaw -= 90.0f * factor;
        }

        if (strafeValue < 0.0f) {
            yaw += 90.0f * factor;
        }

        return MathHelper.wrapDegrees(yaw);
    }

    private record Placement(
            BlockPos targetPos,
            BlockPos blockPos,
            Direction face,
            Vec3d hitVec
    ) {
    }
}
