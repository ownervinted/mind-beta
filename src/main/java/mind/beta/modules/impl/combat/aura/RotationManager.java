/* Copyright Mind */

package mind.beta.modules.impl.combat.aura;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.EventManager;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.PlayerVelocityStrafeEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.movement.Scaffold;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.math.TaskProcessor;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RotationManager implements IMinecraft {
    public static RotationManager INSTANCE = new RotationManager();

    RotationPlan lastRotationPlan;
    final TaskProcessor<RotationPlan> rotationPlanTaskProcessor = new TaskProcessor<>();
    ModuleStructure rotationOwner;

    public Rotation currentRotation;
    Rotation previousRotation;
    Rotation serverRotation = Rotation.DEFAULT;
    Rotation fakeRotation;




    Rotation itemUseRotationLock;

    boolean returning;
    RotationState rotationState = RotationState.IDLE;
    LivingEntity trackedEntity;
    float turnSpeed;
    RotationConfig rotationConfig = new RotationConfig();
    RotationConfig returnRotationConfig = new RotationConfig();
    ModuleStructure rotationOverrideOwner;
    Rotation overrideRotation;
    boolean rotationOverrideMoveCorrection;

    public RotationManager() {
        Initialization.getInstance().getManager().getEventManager().register(this);
    }

    public void setRotation(Rotation value) {
        if (value == null) {
            this.previousRotation = this.currentRotation != null ? this.currentRotation : RotationUtils.cameraAngle();
            this.currentRotation = null;
            return;
        }

        Rotation reference = this.currentRotation != null ? this.currentRotation : this.serverRotation;
        float continuousYaw = unwrapYaw(reference.getYaw(), value.getYaw());
        continuousYaw = unwrapYaw(this.serverRotation.getYaw(), continuousYaw);
        Rotation continuousValue = new Rotation(
                continuousYaw,
                MathHelper.clamp(value.getPitch(), -90.0f, 90.0f)
        );

        this.previousRotation = this.currentRotation;
        this.currentRotation = continuousValue;
    }

    private float unwrapYaw(float referenceYaw, float yaw) {
        return referenceYaw + MathHelper.wrapDegrees(yaw - referenceYaw);
    }

    public RotationConfig getRotationConfig() {
        return rotationConfig;
    }

    public String getRotationStateName() {
        return rotationState.name().toLowerCase(java.util.Locale.ROOT);
    }

    public void setRotationConfig(RotationConfig config) {
        this.rotationConfig = config != null ? config : new RotationConfig();
        this.turnSpeed = 0.0f;
    }

    public Rotation getRotation() {
        return currentRotation != null ? currentRotation : RotationUtils.cameraAngle();
    }

    public Rotation getFakeRotation() {
        if (fakeRotation != null) {
            return fakeRotation;
        }

        return currentRotation != null
                ? currentRotation
                : previousRotation != null
                ? previousRotation
                : RotationUtils.cameraAngle();
    }

    public void setFakeRotation(Rotation rotation) {
        this.fakeRotation = rotation;
    }

    public Rotation getPreviousRotation() {
        return currentRotation != null && previousRotation != null
                ? previousRotation
                : new Rotation(mc.player.lastYaw, mc.player.lastPitch);
    }

    public Rotation getMoveRotation() {
        RotationPlan rotationPlan = getCurrentRotationPlan();
        return currentRotation != null && rotationPlan != null && rotationPlan.isMoveCorrection()
                ? currentRotation
                : RotationUtils.cameraAngle();
    }

    public RotationPlan getCurrentRotationPlan() {
        RotationPlan active = rotationPlanTaskProcessor.fetchActiveTaskValue();
        return active != null ? active : lastRotationPlan;
    }

    public boolean hasActiveRotation() {
        return getCurrentRotationPlan() != null;
    }

    public boolean shouldUseSilentRotationForItems() {
        return currentRotation != null
                && (rotationOverrideOwner != null || getCurrentRotationPlan() != null || returning);
    }

    public Rotation getItemUseRotationLock() {
        return itemUseRotationLock;
    }

    public Rotation getOrLockItemUseRotation() {
        if (itemUseRotationLock != null) {
            return itemUseRotationLock;
        }

        Rotation source = currentRotation != null
                ? currentRotation
                : serverRotation;

        itemUseRotationLock = new Rotation(
                source.getYaw(),
                source.getPitch()
        );
        return itemUseRotationLock;
    }

    public void clearItemUseRotationLock() {
        itemUseRotationLock = null;
    }

    public void syncManualBlockUseToCamera() {
        if (mc.player == null) {
            return;
        }

        Rotation camera = RotationUtils.cameraAngle();




        itemUseRotationLock = new Rotation(camera.getYaw(), camera.getPitch());
    }

    public boolean isReturningSettled() {
        if (!returning) {
            return true;
        }

        if (currentRotation == null) {
            return true;
        }

        return computeRotationDifference(currentRotation, RotationUtils.cameraAngle()) < 1.0;
    }

    public void rotateTo(
            Rotation.VecRotation vecRotation,
            LivingEntity entity,
            int reset,
            RotationRequest configurable,
            TaskPriority taskPriority,
            ModuleStructure provider
    ) {
        rotateTo(
                configurable.createRotationPlan(
                        vecRotation.getRotation(),
                        vecRotation.getVec(),
                        entity,
                        reset
                ),
                taskPriority,
                provider
        );
    }

    public void rotateTo(
            Rotation rotation,
            int reset,
            RotationRequest configurable,
            TaskPriority taskPriority,
            ModuleStructure provider
    ) {
        rotateTo(
                configurable.createRotationPlan(
                        rotation,
                        rotation.toVector(),
                        null,
                        reset
                ),
                taskPriority,
                provider
        );
    }

    public void rotateTo(
            Rotation rotation,
            RotationRequest configurable,
            TaskPriority taskPriority,
            ModuleStructure provider
    ) {
        rotateTo(
                configurable.createRotationPlan(
                        rotation,
                        rotation.toVector(),
                        null,
                        1
                ),
                taskPriority,
                provider
        );
    }

    public void rotateTo(
            RotationPlan plan,
            TaskPriority taskPriority,
            ModuleStructure provider
    ) {
        if (plan == null || (rotationOverrideOwner != null && rotationOverrideOwner != provider)) {
            return;
        }

        LivingEntity nextEntity = plan.getEntity() instanceof LivingEntity living ? living : null;
        boolean ownerChanged = provider != rotationOwner;
        boolean modeChanged = lastRotationPlan != null
                && lastRotationPlan.getAngleSmooth() != plan.getAngleSmooth();

        if (returning || nextEntity != trackedEntity || ownerChanged || modeChanged) {
            rotationState = RotationState.ACQUIRING;
            turnSpeed = 0.0f;
        } else if (rotationState == RotationState.IDLE) {
            rotationState = RotationState.ACQUIRING;
            turnSpeed = 0.0f;
        } else {
            rotationState = RotationState.TRACKING;
        }

        trackedEntity = nextEntity;
        returning = false;
        rotationOwner = provider;

        if (plan.getRotationConfig() != null) {
            rotationConfig = plan.getRotationConfig();
        }

        rotationPlanTaskProcessor.addTask(
                new TaskProcessor.Task<>(
                        1,
                        taskPriority.getPriority(),
                        provider,
                        plan
                )
        );
    }

    public void update() {
        if (rotationOverrideOwner != null) {
            if (overrideRotation != null) {
                setRotation(overrideRotation);
            }
            return;
        }

        RotationPlan activePlan = getCurrentRotationPlan();

        if (activePlan == null) {
            if (returning && currentRotation != null) {
                updateReturning();
            }
            return;
        }

        Rotation clientRotation = RotationUtils.cameraAngle();
        Rotation current = currentRotation != null ? currentRotation : clientRotation;

        Rotation desired = activePlan.nextRotation(
                current,
                rotationPlanTaskProcessor.fetchActiveTaskValue() == null
        );

        boolean directMode = activePlan.getAngleSmooth() != null
                && activePlan.getAngleSmooth().bypassManagerSmoothing();

        RotationConfig activeConfig = activePlan.getRotationConfig() != null
                ? activePlan.getRotationConfig()
                : rotationConfig;

        Rotation limited = directMode
                ? desired
                : limitRotationStep(current, desired, rotationState, activeConfig);

        setRotation(limited.adjustSensitivity());

        lastRotationPlan = activePlan;
        rotationPlanTaskProcessor.tick(1);
    }

    private Rotation limitRotationStep(Rotation current, Rotation desired, RotationState state, RotationConfig profile) {
        if (desired == null) {
            return current;
        }

        Rotation delta = RotationUtils.calculateDelta(current, desired);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();
        float distance = (float) Math.hypot(yawDelta, pitchDelta);

        if (distance < 0.001f) {
            turnSpeed *= 0.75f;
            return current;
        }

        RotationConfig config = profile != null ? profile : (rotationConfig != null ? rotationConfig : new RotationConfig());

        float targetSpeed = switch (state) {
            case ACQUIRING -> {
                float error = MathHelper.clamp(distance / 120.0f, 0.0f, 1.0f);
                yield Math.max(0.1f, config.getAcquireSpeed()) * (0.35f + 0.65f * smoothStep(error));
            }
            case TRACKING -> {
                float error = MathHelper.clamp(distance / 45.0f, 0.0f, 1.0f);
                yield Math.max(0.1f, config.getTrackSpeed()) * (0.35f + 0.65f * smoothStep(error));
            }
            case RETURNING -> {
                float error = MathHelper.clamp(distance / 60.0f, 0.0f, 1.0f);
                yield Math.max(0.1f, config.getReturnSpeed()) * (0.35f + 0.65f * smoothStep(error));
            }
            default -> 2.0f;
        };

        float response = state == RotationState.RETURNING
                ? config.getDeceleration()
                : config.getAcceleration();

        response = MathHelper.clamp(response, 0.01f, 1.0f);
        turnSpeed += (targetSpeed - turnSpeed) * response;

        float maxYaw = Math.max(0.1f, config.getMaxYawSpeed());
        float maxPitch = Math.max(0.1f, config.getMaxPitchSpeed());

        float yawStep = MathHelper.clamp(yawDelta, -maxYaw, maxYaw);
        float pitchStep = MathHelper.clamp(pitchDelta, -maxPitch, maxPitch);

        float speedFactor = Math.min(
                1.0f,
                Math.max(1.0f, turnSpeed) / Math.max(1.0f, distance)
        );

        float newYaw = current.getYaw() + yawStep * speedFactor;
        float newPitch = current.getPitch() + pitchStep * speedFactor;

        float smoothing = MathHelper.clamp(config.getAimSmoothing(), 0.01f, 1.0f);

        newYaw = current.getYaw() + (newYaw - current.getYaw()) * smoothing;
        newPitch = current.getPitch() + (newPitch - current.getPitch()) * smoothing;

        return new Rotation(
                newYaw,
                MathHelper.clamp(newPitch, -90.0f, 90.0f)
        );
    }

    private float smoothStep(float value) {
        value = MathHelper.clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private void updateReturning() {
        Rotation camera = RotationUtils.cameraAngle();
        Rotation current = currentRotation;

        Rotation delta = RotationUtils.calculateDelta(current, camera);
        float distance = (float) Math.hypot(delta.getYaw(), delta.getPitch());

        if (distance <= 0.35f) {
            handoffContinuousYawToVanilla(current);
            setRotation(null);
            lastRotationPlan = null;
            trackedEntity = null;
            rotationOwner = null;
            returning = false;
            rotationState = RotationState.IDLE;
            turnSpeed = 0.0f;
            return;
        }

        rotationState = RotationState.RETURNING;

        Rotation next = limitRotationStep(
                current,
                camera,
                RotationState.RETURNING,
                returnRotationConfig
        );

        setRotation(next.adjustSensitivity());
    }


    private void handoffContinuousYawToVanilla(Rotation reference) {
        if (mc.player == null || reference == null) {
            return;
        }

        float oldYaw = mc.player.getYaw();
        float continuousYaw = unwrapYaw(reference.getYaw(), oldYaw);
        float offset = continuousYaw - oldYaw;

        if (Math.abs(offset) < 0.001f) {
            return;
        }

        mc.player.setYaw(continuousYaw);
        mc.player.lastYaw += offset;
    }

    public static double computeRotationDifference(Rotation a, Rotation b) {
        return Math.hypot(
                Math.abs(computeAngleDifference(a.getYaw(), b.getYaw())),
                Math.abs(a.getPitch() - b.getPitch())
        );
    }

    public static float computeAngleDifference(float a, float b) {
        return MathHelper.wrapDegrees(a - b);
    }

    public Rotation getEffectiveRotation() {
        return itemUseRotationLock != null ? itemUseRotationLock : currentRotation;
    }

    private Vec3d fixVelocity(Vec3d currVelocity, Vec3d movementInput, float speed) {
        Rotation effectiveRotation = getEffectiveRotation();
        if (effectiveRotation != null) {
            float yaw = effectiveRotation.getYaw();
            double d = movementInput.lengthSquared();

            if (d < 1.0E-7) {
                return Vec3d.ZERO;
            }

            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);

            float f = MathHelper.sin(yaw * 0.017453292f);
            float g = MathHelper.cos(yaw * 0.017453292f);

            return new Vec3d(
                    vec3d.getX() * g - vec3d.getZ() * f,
                    vec3d.getY(),
                    vec3d.getZ() * g + vec3d.getX() * f
            );
        }

        return currVelocity;
    }

    public void beginRotationOverride(ModuleStructure provider) {
        beginRotationOverride(provider, false);
    }

    public void beginRotationOverride(ModuleStructure provider, boolean moveCorrection) {
        if (provider == null) {
            return;
        }

        rotationOverrideOwner = provider;
        rotationOverrideMoveCorrection = moveCorrection;
        Rotation source = currentRotation != null ? currentRotation : RotationUtils.cameraAngle();
        overrideRotation = new Rotation(
                unwrapYaw(serverRotation.getYaw(), source.getYaw()),
                source.getPitch()
        );
        rotationPlanTaskProcessor.activeTasks.clear();
        lastRotationPlan = null;
        trackedEntity = null;
        returning = false;
        rotationState = RotationState.IDLE;
        turnSpeed = 0.0f;
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    public void setOverrideRotation(ModuleStructure provider, Rotation rotation) {
        if (provider == null || provider != rotationOverrideOwner || rotation == null) {
            return;
        }

        setRotation(rotation);
        overrideRotation = currentRotation;
    }

    public void setOverrideMoveCorrection(ModuleStructure provider, boolean moveCorrection) {
        if (provider != null && provider == rotationOverrideOwner) {
            rotationOverrideMoveCorrection = moveCorrection;
        }
    }

    public void endRotationOverride(ModuleStructure provider) {
        if (provider == null || provider != rotationOverrideOwner) {
            return;
        }

        rotationOverrideOwner = null;
        rotationOverrideMoveCorrection = false;
        overrideRotation = null;
        lastRotationPlan = null;
        trackedEntity = null;
        rotationOwner = null;
        returning = false;
        rotationState = RotationState.IDLE;
        turnSpeed = 0.0f;
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    public void clear() {
        rotationPlanTaskProcessor.activeTasks.clear();
    }

    public void releaseControl(ModuleStructure provider) {
        if (provider != null) {
            boolean ownsRotation = rotationOwner == provider;
            boolean ownsOverride = rotationOverrideOwner == provider;

            if ((!ownsRotation && !ownsOverride)
                    || (rotationOverrideOwner != null && !ownsOverride)) {
                return;
            }
        }

        rotationPlanTaskProcessor.activeTasks.clear();
        lastRotationPlan = null;
        trackedEntity = null;

        if (provider == null || rotationOwner == provider) {
            rotationOwner = null;
        }

        if (provider == null || rotationOverrideOwner == provider) {
            rotationOverrideOwner = null;
            rotationOverrideMoveCorrection = false;
            overrideRotation = null;
        }

        itemUseRotationLock = null;
        if (mc.player != null) {
            handoffContinuousYawToVanilla(serverRotation);
        }
        currentRotation = null;
        previousRotation = mc.player != null ? RotationUtils.cameraAngle() : null;
        fakeRotation = null;
        returning = false;
        rotationState = RotationState.IDLE;
        turnSpeed = 0.0f;
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    public void startReturning() {
        startReturning(null);
    }

    public void startReturning(ModuleStructure provider) {
        if (provider != null && rotationOwner != null && rotationOwner != provider) {
            return;
        }

        RotationPlan returnPlan = getCurrentRotationPlan();
        RotationConfig sourceProfile = returnPlan != null && returnPlan.getRotationConfig() != null
                ? returnPlan.getRotationConfig()
                : rotationConfig;

        returnRotationConfig = copyRotationConfig(sourceProfile);

        rotationPlanTaskProcessor.activeTasks.clear();
        lastRotationPlan = null;
        trackedEntity = null;

        if (provider != null) {
            rotationOwner = provider;
        }

        if (mc.player == null || currentRotation == null) {
            rotationOwner = null;
            returning = false;
            rotationState = RotationState.IDLE;
            turnSpeed = 0.0f;
            return;
        }

        returning = true;
        rotationState = RotationState.RETURNING;

        turnSpeed = 0.0f;
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    private RotationConfig copyRotationConfig(RotationConfig config) {
        RotationConfig source = config != null ? config : new RotationConfig();
        return new RotationConfig(
                source.getAcquireSpeed(),
                source.getTrackSpeed(),
                source.getReturnSpeed(),
                source.getMaxYawSpeed(),
                source.getMaxPitchSpeed(),
                source.getAcceleration(),
                source.getDeceleration(),
                source.getAimSmoothing()
        );
    }

    public void cancelReturning() {
        if (!returning) {
            return;
        }

        rotationPlanTaskProcessor.activeTasks.clear();
        lastRotationPlan = null;
        trackedEntity = null;
        rotationOwner = null;
        returning = false;
        rotationState = RotationState.IDLE;
        turnSpeed = 0.0f;
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    public void reset() {
        rotationPlanTaskProcessor.activeTasks.clear();
        currentRotation = null;
        previousRotation = null;
        fakeRotation = null;
        itemUseRotationLock = null;
        rotationOverrideOwner = null;
        rotationOverrideMoveCorrection = false;
        overrideRotation = null;
        lastRotationPlan = null;
        trackedEntity = null;
        rotationOwner = null;
        returning = false;
        rotationState = RotationState.IDLE;
        turnSpeed = 0.0f;
        returnRotationConfig = new RotationConfig();
        rotationPlanTaskProcessor.tickCounter = 0;
    }

    @EventHandler
    public void onInput(InputEvent e) {
        if (returning) {
            e.setSprinting(false);
        }
    }

    @EventHandler
    public void onPlayerVelocityStrafe(PlayerVelocityStrafeEvent e) {
        RotationPlan currentRotationPlan = getCurrentRotationPlan();

        boolean activeCorrection =
                currentRotationPlan != null && currentRotationPlan.isMoveCorrection();

        boolean returningCorrection =
                returning && currentRotation != null;

        boolean overrideCorrection =
                rotationOverrideOwner != null && rotationOverrideMoveCorrection && currentRotation != null;

        if (activeCorrection || returningCorrection || overrideCorrection) {
            e.setVelocity(
                    fixVelocity(
                            e.getVelocity(),
                            e.getMovementInput(),
                            e.getSpeed()
                    )
            );
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        EventManager.callEvent(
                new RotationUpdateEvent(EventType.PRE)
        );

        update();

        EventManager.callEvent(
                new RotationUpdateEvent(EventType.POST)
        );
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        switch (event.getPacket()) {
            case PlayerMoveC2SPacket player when player.changesLook() -> {
                float packetYaw = player.getYaw(1);
                serverRotation = new Rotation(
                        unwrapYaw(serverRotation.getYaw(), packetYaw),
                        player.getPitch(1)
                );
            }

            case PlayerPositionLookS2CPacket player -> {
                itemUseRotationLock = null;
                float packetYaw = player.change().yaw();
                serverRotation = new Rotation(
                        unwrapYaw(serverRotation.getYaw(), packetYaw),
                        player.change().pitch()
                );
            }

            default -> {
            }
        }
    }

    public enum RotationState {
        IDLE,
        ACQUIRING,
        TRACKING,
        RETURNING
    }
}
