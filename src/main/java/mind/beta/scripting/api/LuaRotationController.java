package mind.beta.scripting.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.scripting.LuaModule;

import java.util.Locale;
import java.util.Random;

public final class LuaRotationController {
    private enum Phase {
        IDLE,
        ACQUIRE,
        TRACK,
        RETURN
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final LuaModule owner;
    private final Random random = new Random();

    private boolean moveFix = true;
    private boolean sensitivity = true;
    private long acquireMs = 180L;
    private long returnMs = 220L;
    private float maxYawSpeed = 42.0f;
    private float maxPitchSpeed = 28.0f;
    private float acceleration = 0.34f;
    private float deceleration = 0.48f;
    private float trackingGain = 0.72f;
    private float deadzone = 0.04f;
    private double predictionTicks = 1.25;
    private String aimMode = "dynamic";
    private double aimHeight = 0.72;
    private double wander = 0.22;
    private long wanderIntervalMs = 260L;
    private double jitterYaw = 0.18;
    private double jitterPitch = 0.10;
    private double jitterSpeed = 1.4;
    private LuaEasing.Curve acquireCurve = LuaEasing.cubicBezierCurve(0.16, 0.72, 0.22, 1.0);
    private LuaEasing.Curve returnCurve = LuaEasing.cubicBezierCurve(0.20, 0.76, 0.30, 1.0);
    private LuaEasing.Curve speedCurve = LuaEasing.named("out_cubic");

    private Phase phase = Phase.IDLE;
    private int targetId = Integer.MIN_VALUE;
    private Rotation acquireStart;
    private Rotation returnStart;
    private long phaseStartedAt;
    private float yawVelocity;
    private float pitchVelocity;
    private boolean overrideActive;
    private boolean returnTickerRegistered;

    private Vec3d previousWander = Vec3d.ZERO;
    private Vec3d nextWander = Vec3d.ZERO;
    private long wanderStartedAt;
    private long wanderNextAt;

    public LuaRotationController(LuaModule owner, LuaTable options) {
        this.owner = owner;
        configure(options);
        resetWander(System.currentTimeMillis());
    }

    public LuaModule getOwner() {
        return owner;
    }

    public void configure(LuaTable options) {
        if (options == null) {
            return;
        }

        moveFix = bool(options, "move_fix", moveFix);
        sensitivity = bool(options, "sensitivity", sensitivity);
        acquireMs = Math.max(0L, longValue(options, "acquire_ms", acquireMs));
        returnMs = Math.max(0L, longValue(options, "return_ms", returnMs));
        maxYawSpeed = positiveFloat(options, "max_yaw", maxYawSpeed);
        maxPitchSpeed = positiveFloat(options, "max_pitch", maxPitchSpeed);
        acceleration = unitFloat(options, "acceleration", acceleration);
        deceleration = unitFloat(options, "deceleration", deceleration);
        trackingGain = positiveFloat(options, "tracking_gain", trackingGain);
        deadzone = Math.max(0.0f, floatValue(options, "deadzone", deadzone));
        predictionTicks = Math.max(0.0, number(options, "prediction", predictionTicks));
        aimMode = string(options, "aim", aimMode).toLowerCase(Locale.ROOT);
        aimHeight = clamp(number(options, "aim_height", aimHeight), 0.0, 1.0);
        wander = clamp(number(options, "wander", wander), 0.0, 1.0);
        wanderIntervalMs = Math.max(20L, longValue(options, "wander_interval_ms", wanderIntervalMs));
        jitterYaw = Math.max(0.0, number(options, "jitter_yaw", jitterYaw));
        jitterPitch = Math.max(0.0, number(options, "jitter_pitch", jitterPitch));
        jitterSpeed = Math.max(0.0, number(options, "jitter_speed", jitterSpeed));

        acquireCurve = curve(options.get("acquire_curve"), acquireCurve);
        returnCurve = curve(options.get("return_curve"), returnCurve);
        speedCurve = curve(options.get("speed_curve"), speedCurve);

        if (overrideActive) {
            RotationManager.INSTANCE.setOverrideMoveCorrection(owner, moveFix);
        }
    }

    public boolean lookAt(Entity entity) {
        if (entity == null || !entity.isAlive() || mc.player == null || mc.world == null) {
            return false;
        }
        return updateTarget(entity.getId(), calculateAim(entity));
    }

    public boolean lookAtPosition(Vec3d position) {
        if (position == null || mc.player == null || mc.world == null) {
            return false;
        }
        return updateTarget(Integer.MIN_VALUE + 1, RotationUtils.calculateAngle(position));
    }

    public boolean lookAtAngles(float yaw, float pitch) {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        return updateTarget(Integer.MIN_VALUE + 2, new Rotation(yaw, MathHelper.clamp(pitch, -90.0f, 90.0f)));
    }

    public boolean returnToView() {
        if (mc.player == null) {
            closeImmediate();
            return true;
        }

        ensureOverride();
        RotationManager manager = RotationManager.INSTANCE;
        Rotation current = manager.getRotation();

        if (phase != Phase.RETURN) {
            phase = Phase.RETURN;
            phaseStartedAt = System.currentTimeMillis();
            returnStart = new Rotation(current.getYaw(), current.getPitch());
            targetId = Integer.MIN_VALUE;
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
        }

        Rotation camera = RotationUtils.cameraAngle();
        double progress = returnMs <= 0L ? 1.0 : clamp01((double) (System.currentTimeMillis() - phaseStartedAt) / returnMs);
        double eased = returnCurve.apply(progress);
        Rotation next = interpolateRotation(returnStart, camera, eased);
        apply(next);

        if (progress >= 1.0 || difference(next, camera) <= 0.25f) {
            manager.endRotationOverride(owner);
            overrideActive = false;
            phase = Phase.IDLE;
            returnStart = null;
            unregisterReturnTicker();
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
            return true;
        }
        return false;
    }

    public void releaseSmooth() {
        if (!overrideActive) {
            phase = Phase.IDLE;
            unregisterReturnTicker();
            return;
        }
        boolean done = returnToView();
        if (!done) {
            registerReturnTicker();
        }
    }

    public void closeImmediate() {
        if (overrideActive) {
            RotationManager.INSTANCE.endRotationOverride(owner);
        }
        overrideActive = false;
        phase = Phase.IDLE;
        targetId = Integer.MIN_VALUE;
        unregisterReturnTicker();
        yawVelocity = 0.0f;
        pitchVelocity = 0.0f;
    }

    @EventHandler
    public void onReturnTick(TickEvent event) {
        if (returnTickerRegistered && phase == Phase.RETURN) {
            returnToView();
        }
    }

    private void registerReturnTicker() {
        if (returnTickerRegistered) {
            return;
        }
        Initialization initialization = Initialization.getInstance();
        if (initialization == null || initialization.getManager() == null || initialization.getManager().getEventManager() == null) {
            return;
        }
        initialization.getManager().getEventManager().register(this);
        returnTickerRegistered = true;
    }

    private void unregisterReturnTicker() {
        if (!returnTickerRegistered) {
            return;
        }
        Initialization initialization = Initialization.getInstance();
        if (initialization != null && initialization.getManager() != null && initialization.getManager().getEventManager() != null) {
            initialization.getManager().getEventManager().unregister(this);
        }
        returnTickerRegistered = false;
    }

    public String getPhase() {
        return phase.name().toLowerCase(Locale.ROOT);
    }

    public float getYawVelocity() {
        return yawVelocity;
    }

    public float getPitchVelocity() {
        return pitchVelocity;
    }

    private boolean updateTarget(int nextTargetId, Rotation desired) {
        unregisterReturnTicker();
        ensureOverride();
        RotationManager manager = RotationManager.INSTANCE;
        Rotation current = manager.getRotation();
        long now = System.currentTimeMillis();

        if (targetId != nextTargetId || phase == Phase.IDLE || phase == Phase.RETURN) {
            targetId = nextTargetId;
            phase = Phase.ACQUIRE;
            phaseStartedAt = now;
            acquireStart = new Rotation(current.getYaw(), current.getPitch());
            yawVelocity = 0.0f;
            pitchVelocity = 0.0f;
        }

        Rotation next;
        if (phase == Phase.ACQUIRE) {
            double progress = acquireMs <= 0L ? 1.0 : clamp01((double) (now - phaseStartedAt) / acquireMs);
            double eased = acquireCurve.apply(progress);
            next = interpolateRotation(acquireStart, desired, eased);
            if (progress >= 1.0 || difference(next, desired) <= 0.35f) {
                phase = Phase.TRACK;
                yawVelocity = 0.0f;
                pitchVelocity = 0.0f;
            }
        } else {
            phase = Phase.TRACK;
            next = track(current, desired);
        }

        apply(next);
        return true;
    }

    private Rotation track(Rotation current, Rotation desired) {
        Rotation delta = RotationUtils.calculateDelta(current, desired);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();
        float distance = (float) Math.hypot(yawDelta, pitchDelta);

        if (distance <= deadzone) {
            yawVelocity *= 0.55f;
            pitchVelocity *= 0.55f;
            return desired;
        }

        double error = clamp01(distance / 90.0);
        float graph = (float) (0.16 + 0.84 * speedCurve.apply(error));

        float targetYawVelocity = MathHelper.clamp(yawDelta * trackingGain, -maxYawSpeed, maxYawSpeed) * graph;
        float targetPitchVelocity = MathHelper.clamp(pitchDelta * trackingGain, -maxPitchSpeed, maxPitchSpeed) * graph;

        yawVelocity = approachVelocity(yawVelocity, targetYawVelocity, acceleration, deceleration);
        pitchVelocity = approachVelocity(pitchVelocity, targetPitchVelocity, acceleration, deceleration);

        float yawStep = clampStep(yawVelocity, yawDelta);
        float pitchStep = clampStep(pitchVelocity, pitchDelta);

        return new Rotation(
                current.getYaw() + yawStep,
                MathHelper.clamp(current.getPitch() + pitchStep, -90.0f, 90.0f)
        );
    }

    private Rotation calculateAim(Entity entity) {
        Box box = entity.getBoundingBox();
        Vec3d velocity = entity.getVelocity().multiply(predictionTicks);
        Vec3d point;

        switch (aimMode) {
            case "eyes", "eye", "head" -> point = entity.getEyePos();
            case "feet", "legs" -> point = new Vec3d(entity.getX(), box.minY + 0.08, entity.getZ());
            case "center", "middle" -> point = box.getCenter();
            case "chest", "body" -> point = new Vec3d(
                    (box.minX + box.maxX) * 0.5,
                    box.minY + (box.maxY - box.minY) * 0.66,
                    (box.minZ + box.maxZ) * 0.5
            );
            case "fixed" -> point = new Vec3d(
                    (box.minX + box.maxX) * 0.5,
                    box.minY + (box.maxY - box.minY) * aimHeight,
                    (box.minZ + box.maxZ) * 0.5
            );
            default -> point = dynamicPoint(box);
        }

        point = point.add(velocity);
        Rotation rotation = RotationUtils.calculateAngle(point);

        if (jitterSpeed > 0.0 && (jitterYaw > 0.0 || jitterPitch > 0.0)) {
            double time = System.nanoTime() / 1_000_000_000.0 * jitterSpeed;
            float yawNoise = (float) ((Math.sin(time * 2.13) * 0.62 + Math.sin(time * 4.71 + 1.7) * 0.38) * jitterYaw);
            float pitchNoise = (float) ((Math.sin(time * 1.79 + 0.8) * 0.67 + Math.sin(time * 3.97 + 2.2) * 0.33) * jitterPitch);
            rotation = new Rotation(rotation.getYaw() + yawNoise, MathHelper.clamp(rotation.getPitch() + pitchNoise, -90.0f, 90.0f));
        }

        return rotation;
    }

    private Vec3d dynamicPoint(Box box) {
        long now = System.currentTimeMillis();
        if (now >= wanderNextAt) {
            previousWander = currentWander(now);
            nextWander = randomWander();
            wanderStartedAt = now;
            wanderNextAt = now + wanderIntervalMs;
        }

        Vec3d drift = currentWander(now);
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double height = box.maxY - box.minY;
        double halfWidth = (box.maxX - box.minX) * 0.5;
        double halfDepth = (box.maxZ - box.minZ) * 0.5;

        return new Vec3d(
                centerX + drift.x * halfWidth * wander,
                box.minY + height * clamp(aimHeight + drift.y * 0.22 * wander, 0.18, 0.95),
                centerZ + drift.z * halfDepth * wander
        );
    }

    private Vec3d currentWander(long now) {
        if (wanderIntervalMs <= 0L) {
            return nextWander;
        }
        double t = clamp01((double) (now - wanderStartedAt) / wanderIntervalMs);
        double eased = LuaEasing.apply("in_out_sine", t);
        return new Vec3d(
                previousWander.x + (nextWander.x - previousWander.x) * eased,
                previousWander.y + (nextWander.y - previousWander.y) * eased,
                previousWander.z + (nextWander.z - previousWander.z) * eased
        );
    }

    private Vec3d randomWander() {
        return new Vec3d(
                random.nextDouble() * 2.0 - 1.0,
                random.nextDouble() * 2.0 - 1.0,
                random.nextDouble() * 2.0 - 1.0
        );
    }

    private void resetWander(long now) {
        previousWander = randomWander();
        nextWander = randomWander();
        wanderStartedAt = now;
        wanderNextAt = now + wanderIntervalMs;
    }

    private void ensureOverride() {
        if (overrideActive) {
            return;
        }
        RotationManager.INSTANCE.beginRotationOverride(owner, moveFix);
        overrideActive = true;
    }

    private void apply(Rotation rotation) {
        Rotation next = sensitivity ? rotation.adjustSensitivity() : rotation;
        RotationManager.INSTANCE.setOverrideRotation(owner, next);
    }

    private static Rotation interpolateRotation(Rotation from, Rotation to, double progress) {
        float yawDelta = MathHelper.wrapDegrees(to.getYaw() - from.getYaw());
        float pitchDelta = to.getPitch() - from.getPitch();
        return new Rotation(
                from.getYaw() + yawDelta * (float) progress,
                MathHelper.clamp(from.getPitch() + pitchDelta * (float) progress, -90.0f, 90.0f)
        );
    }

    private static float difference(Rotation first, Rotation second) {
        Rotation delta = RotationUtils.calculateDelta(first, second);
        return (float) Math.hypot(delta.getYaw(), delta.getPitch());
    }

    private static float approachVelocity(float current, float target, float acceleration, float deceleration) {
        boolean braking = Math.signum(current) != Math.signum(target) || Math.abs(target) < Math.abs(current);
        float response = MathHelper.clamp(braking ? deceleration : acceleration, 0.001f, 1.0f);
        return current + (target - current) * response;
    }

    private static float clampStep(float velocity, float delta) {
        if (Math.abs(velocity) > Math.abs(delta)) {
            return delta;
        }
        return velocity;
    }

    private static LuaEasing.Curve curve(LuaValue value, LuaEasing.Curve fallback) {
        if (value == null || value.isnil()) {
            return fallback;
        }
        if (value.isstring()) {
            return LuaEasing.named(value.tojstring());
        }
        if (value.isfunction()) {
            return t -> {
                try {
                    return value.call(LuaValue.valueOf(t)).checkdouble();
                } catch (Throwable ignored) {
                    return t;
                }
            };
        }
        if (value.istable()) {
            LuaTable table = value.checktable();
            if (table.length() >= 4) {
                return LuaEasing.cubicBezierCurve(
                        table.get(1).checkdouble(),
                        table.get(2).checkdouble(),
                        table.get(3).checkdouble(),
                        table.get(4).checkdouble()
                );
            }
        }
        return fallback;
    }

    private static boolean bool(LuaTable table, String key, boolean fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.toboolean();
    }

    private static String string(LuaTable table, String key, String fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.checkjstring();
    }

    private static double number(LuaTable table, String key, double fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.checkdouble();
    }

    private static long longValue(LuaTable table, String key, long fallback) {
        return Math.round(number(table, key, fallback));
    }

    private static float floatValue(LuaTable table, String key, float fallback) {
        return (float) number(table, key, fallback);
    }

    private static float positiveFloat(LuaTable table, String key, float fallback) {
        return Math.max(0.01f, floatValue(table, key, fallback));
    }

    private static float unitFloat(LuaTable table, String key, float fallback) {
        return MathHelper.clamp(floatValue(table, key, fallback), 0.001f, 1.0f);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
