/**
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.misc.ClickPearl;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.screens.clickgui.impl.settingsrender.BindComponent;
import mind.beta.util.Instance;
import mind.beta.util.inventory.InventoryUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class TargetPearl extends ModuleStructure {

    private static final int MAX_TICKS = 300;
    private static final int SUB_STEPS = 3;

    private static final double PEARL_SPEED = 1.5;
    private static final double PEARL_GRAVITY = 0.03;
    private static final double PEARL_AIR_DRAG = 0.99;
    private static final double PEARL_WATER_DRAG = 0.80;

    private static final double MAX_HORIZONTAL_ERROR = 4.5;
    private static final float ROTATION_EPSILON = 1.35f;
    private static final long AUTO_REMEMBER_TARGET_MS = 1500L;
    private static final long OVERRIDE_REMEMBER_TARGET_MS = 3000L;

    private enum Phase {
        IDLE,
        ROTATING,
        WAIT_SLOT,
        THROW
    }

    private final RotationManager controller = RotationManager.INSTANCE;
    private final Set<Integer> processedPearls = new HashSet<>();

    private final SelectSetting mode = new SelectSetting(
            "Mode"
    )
            .value("Auto", "Override")
            .selected("Auto");

    private final BindSetting overrideKey = new BindSetting(
            "Override Key",
            "Throw calculated pearl in Override mode"
    );

    private Phase phase = Phase.IDLE;

    private LivingEntity trackedTarget;
    private EnderPearlEntity trackedPearl;

    private LivingEntity rememberedTarget;
    private long rememberedTargetUntil;

    private EnderPearlEntity pendingPearl;
    private Vec3d pendingImpact;
    private Solution pendingSolution;
    private long pendingUntil;

    private Vec3d targetImpact;
    private Rotation solvedRotation;
    private double solvedError = Double.MAX_VALUE;

    private int savedSlot = -1;
    private int pearlSlot = -1;
    private boolean fromInventory;
    private int slotReadyAge = Integer.MIN_VALUE;

    public TargetPearl() {
        super(
                "TargetPearl",
                "Follows the Aura target's ender pearl",
                ModuleCategory.COMBAT
        );

        settings(
                mode,
                overrideKey
        );
    }

    public static TargetPearl getInstance() {
        return Instance.get(TargetPearl.class);
    }

    public boolean isIntercepting() {
        return isState() && phase != Phase.IDLE;
    }

    @Override
    public void activate() {
        reset(false);
        rememberedTarget = null;
        rememberedTargetUntil = 0L;
        clearPending();
    }

    @Override
    public void deactivate() {
        cancel();
        processedPearls.clear();
        rememberedTarget = null;
        rememberedTargetUntil = 0L;
        clearPending();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            reset(false);
            rememberedTarget = null;
            rememberedTargetUntil = 0L;
            clearPending();
            return;
        }

        updateRememberedTarget();

        if (phase != Phase.IDLE) {
            return;
        }

        ClickPearl clickPearl = ClickPearl.getInstance();

        if (clickPearl != null
                && (clickPearl.isRunning()
                || clickPearl.isRotationOverrideActive())) {
            return;
        }

        MaceHelper maceHelper = MaceHelper.getInstance();

        if (maceHelper != null && maceHelper.isThrowSequenceActive()) {
            return;
        }

        LivingEntity target = getTrackingTarget();

        if (target == null || !target.isAlive()) {
            return;
        }

        if (!hasPearl()) {
            return;
        }

        EnderPearlEntity pearl = findNewTargetPearl(target);

        if (pearl == null) {
            return;
        }

        processedPearls.add(pearl.getId());

        Vec3d impact = simulateExistingPearl(pearl);

        if (impact == null) {
            return;
        }

        Solution solution = solveThrow(impact);

        if (solution == null
                || solution.horizontalError() > MAX_HORIZONTAL_ERROR) {
            return;
        }

        if (mode.isSelected("Override")) {
            pendingPearl = pearl;
            pendingImpact = impact;
            pendingSolution = solution;
            pendingUntil = System.currentTimeMillis() + OVERRIDE_REMEMBER_TARGET_MS;
            return;
        }

        beginResponse(
                target,
                pearl,
                impact,
                solution
        );
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (!mode.isSelected("Override")
                || phase != Phase.IDLE
                || mc.player == null
                || mc.world == null) {
            return;
        }

        if (!matchesOverrideBind(event)) {
            return;
        }

        if (event.action() != 1) {
            return;
        }

        if (pendingSolution == null
                || pendingImpact == null
                || pendingPearl == null
                || System.currentTimeMillis() > pendingUntil
                || !hasPearl()) {
            clearPending();
            return;
        }

        LivingEntity target = getTrackingTarget();

        if (target == null || !target.isAlive()) {
            clearPending();
            return;
        }

        beginResponse(
                target,
                pendingPearl,
                pendingImpact,
                pendingSolution
        );

        clearPending();
    }

    private void updateRememberedTarget() {
        Aura aura = Aura.getInstance();
        LivingEntity auraTarget = aura != null && aura.isState()
                ? Aura.target
                : null;

        long now = System.currentTimeMillis();

        if (auraTarget != null && auraTarget.isAlive()) {
            rememberedTarget = auraTarget;
            rememberedTargetUntil = now + getRememberDurationMs();
            return;
        }

        if (rememberedTarget != null
                && (now > rememberedTargetUntil
                || !rememberedTarget.isAlive())) {
            rememberedTarget = null;
            rememberedTargetUntil = 0L;
            clearPending();
        }

        if (!mode.isSelected("Override") && pendingSolution != null) {
            clearPending();
        } else if (pendingSolution != null && now > pendingUntil) {
            clearPending();
        }
    }

    private LivingEntity getTrackingTarget() {
        Aura aura = Aura.getInstance();

        if (aura != null
                && aura.isState()
                && Aura.target != null
                && Aura.target.isAlive()) {
            return Aura.target;
        }

        if (rememberedTarget != null
                && rememberedTarget.isAlive()
                && System.currentTimeMillis() <= rememberedTargetUntil) {
            return rememberedTarget;
        }

        return null;
    }

    private long getRememberDurationMs() {
        return mode.isSelected("Override")
                ? OVERRIDE_REMEMBER_TARGET_MS
                : AUTO_REMEMBER_TARGET_MS;
    }

    private boolean matchesOverrideBind(KeyEvent event) {
        int bindKey = overrideKey.getKey();
        int bindType = overrideKey.getType();

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

    private void beginResponse(
            LivingEntity target,
            EnderPearlEntity pearl,
            Vec3d impact,
            Solution solution
    ) {
        trackedTarget = target;
        trackedPearl = pearl;
        targetImpact = impact;
        solvedRotation = solution.rotation().adjustSensitivity();
        solvedError = solution.horizontalError();

        savedSlot = mc.player.getInventory().getSelectedSlot();

        controller.beginRotationOverride(this, false);
        controller.setOverrideRotation(this, solvedRotation);

        phase = Phase.ROTATING;
    }

    private void clearPending() {
        pendingPearl = null;
        pendingImpact = null;
        pendingSolution = null;
        pendingUntil = 0L;
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isState()
                || phase == Phase.IDLE
                || mc.player == null
                || mc.world == null) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            if (solvedRotation != null) {
                controller.setOverrideRotation(
                        this,
                        solvedRotation
                );
            }
            return;
        }

        if (event.getType() != EventType.POST) {
            return;
        }

        switch (phase) {
            case ROTATING -> {
                if (!isRotationApplied()) {
                    return;
                }

                if (!preparePearl()) {
                    cancel();
                    return;
                }

                phase = Phase.WAIT_SLOT;
            }

            case WAIT_SLOT -> {
                if (mc.player.age < slotReadyAge) {
                    return;
                }

                if (!mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                    if (!preparePearl()) {
                        cancel();
                    }
                    return;
                }

                if (!isRotationApplied()) {
                    phase = Phase.ROTATING;
                    return;
                }

                phase = Phase.THROW;
            }

            case THROW -> {
                if (!isRotationApplied()
                        || mc.interactionManager == null
                        || !mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
                    return;
                }

                mc.interactionManager.interactItem(
                        mc.player,
                        Hand.MAIN_HAND
                );

                mc.player.swingHand(Hand.MAIN_HAND);

                finish();
            }

            default -> {
            }
        }
    }

    private EnderPearlEntity findNewTargetPearl(
            LivingEntity target
    ) {
        EnderPearlEntity newest = null;
        int newestId = Integer.MIN_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) {
                continue;
            }

            if (processedPearls.contains(pearl.getId())) {
                continue;
            }

            if (pearl.getOwner() != target) {
                continue;
            }

            if (newest == null || pearl.getId() > newestId) {
                newest = pearl;
                newestId = pearl.getId();
            }
        }

        if (processedPearls.size() > 256) {
            processedPearls.clear();

            if (newest != null) {
                processedPearls.add(newest.getId());
            }
        }

        return newest;
    }

    private Vec3d simulateExistingPearl(
            EnderPearlEntity pearl
    ) {
        Vec3d position = pearl.getEntityPos();
        Vec3d motion = pearl.getVelocity();

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            for (int step = 0; step < SUB_STEPS; step++) {
                Vec3d previous = position;
                Vec3d next = position.add(
                        motion.multiply(1.0 / SUB_STEPS)
                );

                BlockHitResult hit = raycast(
                        previous,
                        next,
                        pearl
                );

                if (hit.getType() != HitResult.Type.MISS) {
                    return hit.getPos();
                }

                position = next;

                if (position.y < mc.world.getBottomY() - 8) {
                    return null;
                }
            }

            double drag = isWater(position)
                    ? PEARL_WATER_DRAG
                    : PEARL_AIR_DRAG;

            motion = motion
                    .multiply(drag)
                    .add(0.0, -PEARL_GRAVITY, 0.0);
        }

        return null;
    }

    private Solution solveThrow(
            Vec3d desiredImpact
    ) {
        Vec3d eye = mc.player.getEyePos();

        Vec3d delta = desiredImpact.subtract(eye);

        float directYaw = (float) Math.toDegrees(
                Math.atan2(delta.z, delta.x)
        ) - 90.0f;

        Solution best = null;

        float[] yawOffsets = {
                -8.0f,
                -5.0f,
                -2.5f,
                0.0f,
                2.5f,
                5.0f,
                8.0f
        };

        for (float yawOffset : yawOffsets) {
            float yaw = directYaw + yawOffset;

            for (float pitch = 48.0f; pitch >= -72.0f; pitch -= 3.0f) {
                Solution candidate = evaluate(
                        yaw,
                        pitch,
                        desiredImpact
                );

                if (isBetterSolution(candidate, best)) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            return null;
        }

        float coarseYaw = best.rotation().getYaw();
        float coarsePitch = best.rotation().getPitch();

        for (float yaw = coarseYaw - 3.0f;
             yaw <= coarseYaw + 3.01f;
             yaw += 0.75f) {

            for (float pitch = coarsePitch - 3.0f;
                 pitch <= coarsePitch + 3.01f;
                 pitch += 0.75f) {

                Solution candidate = evaluate(
                        yaw,
                        pitch,
                        desiredImpact
                );

                if (isBetterSolution(candidate, best)) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private boolean isBetterSolution(
            Solution candidate,
            Solution current
    ) {
        if (candidate == null) {
            return false;
        }

        if (current == null) {
            return true;
        }

        boolean candidateValid =
                candidate.horizontalError() <= MAX_HORIZONTAL_ERROR;

        boolean currentValid =
                current.horizontalError() <= MAX_HORIZONTAL_ERROR;

        if (candidateValid != currentValid) {
            return candidateValid;
        }

        if (candidateValid) {
            if (Math.abs(
                    candidate.flightTicks()
                            - current.flightTicks()
            ) > 0.05) {
                return candidate.flightTicks()
                        < current.flightTicks();
            }

            if (Math.abs(
                    candidate.horizontalError()
                            - current.horizontalError()
            ) > 0.05) {
                return candidate.horizontalError()
                        < current.horizontalError();
            }

            return candidate.score() < current.score();
        }

        return candidate.score() < current.score();
    }

    private Solution evaluate(
            float yaw,
            float pitch,
            Vec3d desiredImpact
    ) {
        ThrowResult result = simulateOurThrow(
                yaw,
                pitch
        );

        if (result == null) {
            return null;
        }

        Vec3d landing = result.landing();

        double dx = landing.x - desiredImpact.x;
        double dz = landing.z - desiredImpact.z;
        double dy = landing.y - desiredImpact.y;

        double horizontal = Math.sqrt(
                dx * dx + dz * dz
        );

        double score = horizontal
                + Math.abs(dy) * 0.18
                + result.flightTicks() * 0.025;

        return new Solution(
                new Rotation(
                        MathHelper.wrapDegrees(yaw),
                        MathHelper.clamp(pitch, -90.0f, 90.0f)
                ),
                landing,
                horizontal,
                score,
                result.flightTicks()
        );
    }

    private ThrowResult simulateOurThrow(
            float yaw,
            float pitch
    ) {
        Vec3d position = mc.player.getEyePos();

        Vec3d direction = mc.player
                .getRotationVector(pitch, yaw)
                .normalize();

        Vec3d velocity = mc.player.getVelocity();

        Vec3d inherited = new Vec3d(
                velocity.x,
                mc.player.isOnGround() ? 0.0 : velocity.y,
                velocity.z
        );

        Vec3d motion = direction
                .multiply(PEARL_SPEED)
                .add(inherited);

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            for (int step = 0; step < SUB_STEPS; step++) {
                Vec3d previous = position;
                Vec3d next = position.add(
                        motion.multiply(1.0 / SUB_STEPS)
                );

                BlockHitResult hit = raycast(
                        previous,
                        next,
                        mc.player
                );

                if (hit.getType() != HitResult.Type.MISS) {
                    double partialTick =
                            (step + 1.0) / SUB_STEPS;

                    return new ThrowResult(
                            hit.getPos(),
                            tick + partialTick
                    );
                }

                position = next;

                if (position.y < mc.world.getBottomY() - 8) {
                    return null;
                }
            }

            double drag = isWater(position)
                    ? PEARL_WATER_DRAG
                    : PEARL_AIR_DRAG;

            motion = motion
                    .multiply(drag)
                    .add(0.0, -PEARL_GRAVITY, 0.0);
        }

        return null;
    }

    private boolean preparePearl() {
        if (mc.player == null) {
            return false;
        }

        if (mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            slotReadyAge = mc.player.age;
            return true;
        }

        int hotbar = InventoryUtils.findItemInHotbar(
                Items.ENDER_PEARL
        );

        if (hotbar != -1) {
            pearlSlot = hotbar;
            fromInventory = false;

            mc.player.getInventory().setSelectedSlot(
                    hotbar
            );

            slotReadyAge = mc.player.age + 1;
            return true;
        }

        int inventory = InventoryUtils.findItemInInventory(
                Items.ENDER_PEARL
        );

        if (inventory == -1) {
            return false;
        }

        int selected = mc.player
                .getInventory()
                .getSelectedSlot();

        pearlSlot = inventory;
        fromInventory = true;

        InventoryUtils.click(
                pearlSlot,
                selected,
                SlotActionType.SWAP
        );

        slotReadyAge = mc.player.age + 1;

        return true;
    }

    private boolean hasPearl() {
        return InventoryUtils.findItemInHotbar(
                Items.ENDER_PEARL
        ) != -1
                || InventoryUtils.findItemInInventory(
                Items.ENDER_PEARL
        ) != -1;
    }

    private boolean isRotationApplied() {
        if (solvedRotation == null) {
            return false;
        }

        Rotation server = controller.getServerRotation();

        if (server == null) {
            return false;
        }

        float yaw = Math.abs(
                MathHelper.wrapDegrees(
                        server.getYaw()
                                - solvedRotation.getYaw()
                )
        );

        float pitch = Math.abs(
                server.getPitch()
                        - solvedRotation.getPitch()
        );

        return yaw <= ROTATION_EPSILON
                && pitch <= ROTATION_EPSILON;
    }

    private void finish() {
        restoreSlot();

        controller.endRotationOverride(this);

        Aura aura = Aura.getInstance();

        if (aura == null
                || !aura.isState()
                || Aura.target == null
                || !Aura.target.isAlive()) {
            controller.startReturning(this);
        }

        reset(false);
    }

    private void cancel() {
        restoreSlot();

        if (phase != Phase.IDLE) {
            controller.endRotationOverride(this);

            Aura aura = Aura.getInstance();

            if (aura == null
                    || !aura.isState()
                    || Aura.target == null
                    || !Aura.target.isAlive()) {
                controller.startReturning(this);
            }
        }

        reset(false);
    }

    private void restoreSlot() {
        if (mc.player == null) {
            return;
        }

        if (fromInventory
                && pearlSlot >= 0
                && pearlSlot < 36) {

            int selected = mc.player
                    .getInventory()
                    .getSelectedSlot();

            InventoryUtils.click(
                    pearlSlot,
                    selected,
                    SlotActionType.SWAP
            );
        }

        if (savedSlot >= 0
                && savedSlot < 9
                && mc.player.getInventory().getSelectedSlot()
                != savedSlot) {

            mc.player.getInventory().setSelectedSlot(
                    savedSlot
            );
        }
    }

    private void reset(boolean clearProcessed) {
        phase = Phase.IDLE;

        trackedTarget = null;
        trackedPearl = null;

        targetImpact = null;
        solvedRotation = null;
        solvedError = Double.MAX_VALUE;

        savedSlot = -1;
        pearlSlot = -1;
        fromInventory = false;
        slotReadyAge = Integer.MIN_VALUE;

        if (clearProcessed) {
            processedPearls.clear();
        }
    }

    private BlockHitResult raycast(
            Vec3d start,
            Vec3d end,
            Entity source
    ) {
        return mc.world.raycast(
                new RaycastContext(
                        start,
                        end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        source
                )
        );
    }

    private boolean isWater(
            Vec3d position
    ) {
        return !mc.world
                .getFluidState(
                        net.minecraft.util.math.BlockPos.ofFloored(position)
                )
                .isEmpty();
    }

    public Vec3d getTargetImpact() {
        return targetImpact;
    }

    public double getSolvedError() {
        return solvedError;
    }

    private record ThrowResult(
            Vec3d landing,
            double flightTicks
    ) {
    }

    private record Solution(
            Rotation rotation,
            Vec3d landing,
            double horizontalError,
            double score,
            double flightTicks
    ) {
    }
}
