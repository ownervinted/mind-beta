package mind.beta.modules.impl.combat;

import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.util.Instance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoAnchor extends ModuleStructure {
    private final SliderSettings targetRange = new SliderSettings("Target Range", "Maximum target distance")
            .range(2.0F, 10.0F).setValue(6.0F);
    private final SliderSettings anchorRange = new SliderSettings("Anchor Range", "Maximum anchor interaction distance")
            .range(2.0F, 6.0F).setValue(4.5F);
    private final SliderSettings targetRadius = new SliderSettings("Target Radius", "Anchor search radius around target")
            .range(1.0F, 3.0F).setValue(2.0F);
    private final SliderSettings actionDelay = new SliderSettings("Action Delay", "Ticks between macro stages")
            .range(1.0F, 5.0F).setValue(1.0F);
    private final BooleanSetting autoCover = new BooleanSetting("Auto Cover", "Place a solid block in front when exposed")
            .setValue(true);
    private final SliderSettings coverDistance = new SliderSettings("Cover Distance", "Cover when enemy is this close")
            .range(2.0F, 8.0F).setValue(4.0F);
    private final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", "Restore selected slot after sequence")
            .setValue(true);

    private Stage stage = Stage.IDLE;
    private PlayerEntity target;
    private BlockPos anchorPos;
    private BlockHitResult anchorPlaceHit;
    private BlockPos coverPos;
    private BlockHitResult coverPlaceHit;
    private int oldSlot = -1;
    private int selectedActionSlot = -1;
    private int nextActionTick;
    private int waitUntilTick;
    private int retries;
    private Rotation requestedRotation;
    private int rotationRequestedTick = -1;
    private boolean rotationOverrideActive;

    public AutoAnchor() {
        super("AutoAnchor", "Places, charges and explodes anchors near enemies", ModuleCategory.COMBAT);
        settings(targetRange, anchorRange, targetRadius, actionDelay, autoCover, coverDistance, restoreSlot);
    }

    public static AutoAnchor getInstance() {
        return Instance.get(AutoAnchor.class);
    }

    @Override
    public void activate() {
        reset(true);
    }

    @Override
    public void deactivate() {
        reset(true);
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != EventType.PRE || !isState()) {
            return;
        }

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset(false);
            return;
        }

        if (mc.world.getRegistryKey().equals(World.NETHER) || mc.currentScreen != null || mc.player.isUsingItem()
                || !mc.player.isAlive() || mc.player.isSpectator()) {
            reset(true);
            return;
        }

        if (mc.player.age < nextActionTick) {
            return;
        }

        if (stage != Stage.IDLE && !validTarget(target)) {
            reset(true);
        }

        switch (stage) {
            case IDLE -> beginSequence();
            case COVER_SELECT -> selectCoverBlock();
            case COVER_PLACE -> placeCover();
            case ANCHOR_SELECT -> selectAnchor();
            case ANCHOR_PLACE -> placeAnchor();
            case WAIT_ANCHOR -> waitForAnchor();
            case GLOWSTONE_SELECT -> selectGlowstone();
            case CHARGE -> chargeAnchor();
            case WAIT_CHARGE -> waitForCharge();
            case NEUTRAL_SELECT -> selectNeutral();
            case ACTIVATE -> activateAnchor();
            case WAIT_EXPLOSION -> waitForExplosion();
        }
    }

    private void beginSequence() {
        target = findTarget();
        if (target == null) {
            return;
        }

        Placement anchor = findAnchorPlacement(target);
        if (anchor == null) {
            target = null;
            return;
        }

        anchorPos = anchor.pos();
        anchorPlaceHit = anchor.hit();
        oldSlot = mc.player.getInventory().getSelectedSlot();
        retries = 0;

        if (autoCover.isValue()) {
            Placement cover = findCoverPlacement(target);
            if (cover != null) {
                coverPos = cover.pos();
                coverPlaceHit = cover.hit();
                stage = Stage.COVER_SELECT;
                return;
            }
        }

        stage = Stage.ANCHOR_SELECT;
    }

    private PlayerEntity findTarget() {
        double max = targetRange.getValue();
        double maxSq = max * max;
        PlayerEntity best = null;
        double bestSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!validTarget(player)) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(player);
            if (distance <= maxSq && distance < bestSq) {
                bestSq = distance;
                best = player;
            }
        }

        return best;
    }

    private boolean validTarget(PlayerEntity player) {
        return player != null && player != mc.player && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && mc.player != null && mc.world != null
                && mc.player.squaredDistanceTo(player) <= targetRange.getValue() * targetRange.getValue();
    }

    private Placement findAnchorPlacement(PlayerEntity player) {
        int radius = Math.max(1, Math.min(3, Math.round(targetRadius.getValue())));
        BlockPos feet = player.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        for (int y = -1; y <= 1; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(z) == 0 || Math.abs(x) + Math.abs(z) > radius + 1) {
                        continue;
                    }
                    candidates.add(feet.add(x, y, z).toImmutable());
                }
            }
        }

        candidates.sort(Comparator
                .comparingDouble((BlockPos pos) -> Vec3d.ofCenter(pos).squaredDistanceTo(player.getEntityPos()))
                .thenComparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(mc.player.getEntityPos())));

        for (BlockPos pos : candidates) {
            if (!canOccupy(pos)) {
                continue;
            }

            BlockHitResult hit = placementHit(pos, anchorRange.getValue());
            if (hit == null) {
                continue;
            }

            return new Placement(pos, hit);
        }

        return null;
    }

    private Placement findCoverPlacement(PlayerEntity player) {
        if (mc.player.squaredDistanceTo(player) > coverDistance.getValue() * coverDistance.getValue()) {
            return null;
        }

        Vec3d delta = player.getEntityPos().subtract(mc.player.getEntityPos());
        if (delta.horizontalLengthSquared() < 1.0E-4) {
            return null;
        }

        Direction front = horizontalDirection(delta.x, delta.z);
        BlockPos feet = mc.player.getBlockPos();
        BlockPos[] positions = {
                feet.offset(front),
                feet.offset(front).up()
        };

        for (BlockPos pos : positions) {
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                continue;
            }
            if (!canOccupy(pos)) {
                continue;
            }

            BlockHitResult hit = placementHit(pos, mc.player.getBlockInteractionRange());
            if (hit != null) {
                return new Placement(pos.toImmutable(), hit);
            }
        }

        return null;
    }

    private Direction horizontalDirection(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0.0 ? Direction.EAST : Direction.WEST;
        }
        return z > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean canOccupy(BlockPos pos) {
        if (!mc.world.isInHeightLimit(pos.getY()) || !mc.world.getWorldBorder().contains(pos)
                || !mc.world.getBlockState(pos).isReplaceable()) {
            return false;
        }

        Box box = new Box(pos);
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive() && !entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }

        return true;
    }

    private BlockHitResult placementHit(BlockPos desired, double range) {
        Vec3d eyes = mc.player.getEyePos();
        double maxSq = range * range;
        double[] offsets = {0.0, -0.22, 0.22};

        for (Direction side : Direction.values()) {
            BlockPos support = desired.offset(side.getOpposite());
            BlockState supportState = mc.world.getBlockState(support);

            if (supportState.isAir()
                    || supportState.isReplaceable()
                    || supportState.getCollisionShape(mc.world, support).isEmpty()) {
                continue;
            }

            for (double first : offsets) {
                for (double second : offsets) {
                    Vec3d point = facePoint(support, side, first, second, 0.499);

                    if (eyes.squaredDistanceTo(point) > maxSq) {
                        continue;
                    }

                    BlockHitResult ray = mc.world.raycast(new RaycastContext(
                            eyes,
                            point,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    ));

                    if (ray.getType() != HitResult.Type.BLOCK
                            || !ray.getBlockPos().equals(support)
                            || ray.getSide() != side) {
                        continue;
                    }

                    return new BlockHitResult(
                            ray.getPos(),
                            side,
                            support,
                            false
                    );
                }
            }
        }

        return null;
    }

    private Vec3d facePoint(BlockPos pos, Direction side, double first, double second, double depth) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        x += side.getOffsetX() * depth;
        y += side.getOffsetY() * depth;
        z += side.getOffsetZ() * depth;

        if (side.getAxis() == Direction.Axis.Y) {
            x += first;
            z += second;
        } else if (side.getAxis() == Direction.Axis.X) {
            y += first;
            z += second;
        } else {
            x += first;
            y += second;
        }

        return new Vec3d(x, y, z);
    }

    private BlockHitResult existingBlockHit(BlockPos pos, double range) {
        Vec3d eyes = mc.player.getEyePos();
        double maxSq = range * range;
        double[] offsets = {0.0, -0.22, 0.22};

        for (Direction side : Direction.values()) {
            for (double first : offsets) {
                for (double second : offsets) {
                    Vec3d point = facePoint(pos, side, first, second, 0.499);

                    if (eyes.squaredDistanceTo(point) > maxSq) {
                        continue;
                    }

                    BlockHitResult ray = mc.world.raycast(new RaycastContext(
                            eyes,
                            point,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    ));

                    if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(pos)) {
                        continue;
                    }

                    return new BlockHitResult(
                            ray.getPos(),
                            ray.getSide(),
                            ray.getBlockPos(),
                            false
                    );
                }
            }
        }

        return null;
    }

    private void selectCoverBlock() {
        if (coverPos == null || coverPlaceHit == null || !mc.world.getBlockState(coverPos).isReplaceable()) {
            stage = Stage.ANCHOR_SELECT;
            return;
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            Block block = blockItem.getBlock();
            if (block == Blocks.RESPAWN_ANCHOR || block == Blocks.GLOWSTONE) {
                continue;
            }
            BlockState state = block.getDefaultState();
            if (!state.isFullCube(mc.world, coverPos)) {
                continue;
            }
            slots.add(slot);
        }

        if (slots.isEmpty()) {
            stage = Stage.ANCHOR_SELECT;
            return;
        }

        int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
        if (selectSlot(slot)) {
            return;
        }

        selectedActionSlot = slot;
        stage = Stage.COVER_PLACE;
    }

    private void placeCover() {
        if (coverPos == null || !mc.world.getBlockState(coverPos).isReplaceable()) {
            stage = Stage.ANCHOR_SELECT;
            return;
        }

        BlockHitResult hit = placementHit(coverPos, mc.player.getBlockInteractionRange());
        if (hit == null) {
            stage = Stage.ANCHOR_SELECT;
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() == Blocks.RESPAWN_ANCHOR
                || blockItem.getBlock() == Blocks.GLOWSTONE) {
            stage = Stage.COVER_SELECT;
            return;
        }

        if (!rotateAndReady(hit)) {
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        schedule();
        stage = Stage.ANCHOR_SELECT;
    }

    private void selectAnchor() {
        if (anchorPos == null) {
            reset(true);
            return;
        }

        if (mc.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
            stage = Stage.GLOWSTONE_SELECT;
            return;
        }

        if (!canOccupy(anchorPos)) {
            reset(true);
            return;
        }

        int slot = findSlot(Items.RESPAWN_ANCHOR);
        if (slot < 0) {
            reset(true);
            return;
        }

        if (selectSlot(slot)) {
            return;
        }

        selectedActionSlot = slot;
        stage = Stage.ANCHOR_PLACE;
    }

    private void placeAnchor() {
        if (anchorPos == null || !canOccupy(anchorPos)) {
            reset(true);
            return;
        }

        BlockHitResult hit = placementHit(anchorPos, anchorRange.getValue());
        if (hit == null) {
            reset(true);
            return;
        }

        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
            stage = Stage.ANCHOR_SELECT;
            return;
        }

        if (!rotateAndReady(hit)) {
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        retries++;
        waitUntilTick = mc.player.age + 8;
        schedule();
        stage = Stage.WAIT_ANCHOR;
    }

    private void waitForAnchor() {
        if (mc.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
            retries = 0;
            stage = Stage.GLOWSTONE_SELECT;
            return;
        }

        if (mc.player.age >= waitUntilTick) {
            if (retries >= 3) {
                reset(true);
            } else {
                stage = Stage.ANCHOR_SELECT;
            }
        }
    }

    private void selectGlowstone() {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (state.get(RespawnAnchorBlock.CHARGES) > 0) {
            stage = Stage.NEUTRAL_SELECT;
            return;
        }

        int slot = findSlot(Items.GLOWSTONE);
        if (slot < 0) {
            reset(true);
            return;
        }

        if (selectSlot(slot)) {
            return;
        }

        selectedActionSlot = slot;
        stage = Stage.CHARGE;
    }

    private void chargeAnchor() {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (state.get(RespawnAnchorBlock.CHARGES) > 0) {
            stage = Stage.NEUTRAL_SELECT;
            return;
        }

        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            stage = Stage.GLOWSTONE_SELECT;
            return;
        }

        BlockHitResult hit = existingBlockHit(anchorPos, anchorRange.getValue());
        if (hit == null) {
            reset(true);
            return;
        }

        if (!rotateAndReady(hit)) {
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        retries++;
        waitUntilTick = mc.player.age + 8;
        schedule();
        stage = Stage.WAIT_CHARGE;
    }

    private void waitForCharge() {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (state.get(RespawnAnchorBlock.CHARGES) > 0) {
            retries = 0;
            stage = Stage.NEUTRAL_SELECT;
            return;
        }

        if (mc.player.age >= waitUntilTick) {
            if (retries >= 3) {
                reset(true);
            } else {
                stage = Stage.GLOWSTONE_SELECT;
            }
        }
    }

    private void selectNeutral() {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (state.get(RespawnAnchorBlock.CHARGES) <= 0) {
            stage = Stage.GLOWSTONE_SELECT;
            return;
        }

        int current = mc.player.getInventory().getSelectedSlot();
        if (neutral(mc.player.getInventory().getStack(current))) {
            stage = Stage.ACTIVATE;
            schedule();
            return;
        }

        int slot = findNeutralSlot();
        if (slot < 0) {
            reset(true);
            return;
        }

        if (selectSlot(slot)) {
            return;
        }

        selectedActionSlot = slot;
        stage = Stage.ACTIVATE;
    }

    private void activateAnchor() {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (state.get(RespawnAnchorBlock.CHARGES) <= 0) {
            stage = Stage.GLOWSTONE_SELECT;
            return;
        }

        if (!neutral(mc.player.getMainHandStack())) {
            stage = Stage.NEUTRAL_SELECT;
            return;
        }

        BlockHitResult hit = existingBlockHit(anchorPos, anchorRange.getValue());
        if (hit == null) {
            reset(true);
            return;
        }

        if (!rotateAndReady(hit)) {
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        retries++;
        waitUntilTick = mc.player.age + 8;
        schedule();
        stage = Stage.WAIT_EXPLOSION;
    }

    private void waitForExplosion() {
        if (!mc.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(true);
            return;
        }

        if (mc.player.age >= waitUntilTick) {
            if (retries >= 3) {
                reset(true);
            } else {
                stage = Stage.NEUTRAL_SELECT;
            }
        }
    }


    private boolean rotateAndReady(BlockHitResult hit) {
        Rotation targetRotation = rotationTo(hit.getPos()).adjustSensitivity();

        if (!rotationOverrideActive) {
            RotationManager.INSTANCE.beginRotationOverride(this, true);
            rotationOverrideActive = true;
            requestedRotation = null;
            rotationRequestedTick = -1;
        }

        if (requestedRotation == null
                || Math.abs(MathHelper.wrapDegrees(targetRotation.getYaw() - requestedRotation.getYaw())) > 0.5F
                || Math.abs(targetRotation.getPitch() - requestedRotation.getPitch()) > 0.5F) {
            requestedRotation = targetRotation;
            rotationRequestedTick = mc.player.age;
        }

        RotationManager.INSTANCE.setOverrideMoveCorrection(this, true);
        RotationManager.INSTANCE.setOverrideRotation(this, targetRotation);

        if (rotationRequestedTick < 0) {
            rotationRequestedTick = mc.player.age;
            return false;
        }

        if (mc.player.age <= rotationRequestedTick) {
            return false;
        }

        Rotation server = RotationManager.INSTANCE.getServerRotation();
        if (server == null) {
            return false;
        }

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotation.getYaw() - server.getYaw()));
        float pitchDiff = Math.abs(targetRotation.getPitch() - server.getPitch());

        if (yawDiff > 2.5F || pitchDiff > 2.5F) {
            return false;
        }

        Vec3d eyes = mc.player.getEyePos();
        double range = mc.player.getBlockInteractionRange();
        Vec3d end = eyes.add(direction(server).multiply(range));

        BlockHitResult ray = mc.world.raycast(new RaycastContext(
                eyes,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(hit.getBlockPos())) {
            return false;
        }

        if (ray.getSide() != hit.getSide()) {
            return false;
        }

        requestedRotation = targetRotation;
        rotationRequestedTick = mc.player.age;
        return true;
    }

    private Rotation rotationTo(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        return new Rotation(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    private Vec3d direction(Rotation rotation) {
        float yaw = rotation.getYaw() * 0.017453292F;
        float pitch = rotation.getPitch() * 0.017453292F;
        float cosPitch = MathHelper.cos(pitch);

        return new Vec3d(
                -MathHelper.sin(yaw) * cosPitch,
                -MathHelper.sin(pitch),
                MathHelper.cos(yaw) * cosPitch
        );
    }

    private void clearRotationRequest() {
        requestedRotation = null;
        rotationRequestedTick = -1;

        if (rotationOverrideActive) {
            RotationManager.INSTANCE.endRotationOverride(this);
            rotationOverrideActive = false;
        }
    }

    private boolean selectSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            return false;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        selectedActionSlot = slot;
        schedule();
        return true;
    }

    private int findSlot(net.minecraft.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private int findNeutralSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (neutral(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean neutral(ItemStack stack) {
        return !stack.isOf(Items.GLOWSTONE) && !stack.isOf(Items.RESPAWN_ANCHOR);
    }

    private void schedule() {
        nextActionTick = mc.player.age + Math.max(1, Math.round(actionDelay.getValue()));
    }

    private void reset(boolean restore) {
        if (restore && restoreSlot.isValue() && mc.player != null && oldSlot >= 0 && oldSlot < 9) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }

        if (RotationManager.INSTANCE != null && rotationOverrideActive) {
            RotationManager.INSTANCE.endRotationOverride(this);
            rotationOverrideActive = false;
        }

        requestedRotation = null;
        rotationRequestedTick = -1;
        stage = Stage.IDLE;
        target = null;
        anchorPos = null;
        anchorPlaceHit = null;
        coverPos = null;
        coverPlaceHit = null;
        oldSlot = -1;
        selectedActionSlot = -1;
        nextActionTick = 0;
        waitUntilTick = 0;
        retries = 0;
    }

    private enum Stage {
        IDLE,
        COVER_SELECT,
        COVER_PLACE,
        ANCHOR_SELECT,
        ANCHOR_PLACE,
        WAIT_ANCHOR,
        GLOWSTONE_SELECT,
        CHARGE,
        WAIT_CHARGE,
        NEUTRAL_SELECT,
        ACTIVATE,
        WAIT_EXPLOSION
    }

    private record Placement(BlockPos pos, BlockHitResult hit) {
    }
}
