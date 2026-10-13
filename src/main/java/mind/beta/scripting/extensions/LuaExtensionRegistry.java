package mind.beta.scripting.extensions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationConfig;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.scripting.LuaScript;
import mind.beta.scripting.api.LuaApiFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LuaExtensionRegistry {
    private static final LuaExtensionRegistry INSTANCE = new LuaExtensionRegistry();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<String, AuraRotationExtension> auraRotations = new LinkedHashMap<>();
    private final Map<String, MaceResolverExtension> maceResolvers = new LinkedHashMap<>();

    private SelectSetting auraModeSetting;
    private SelectSetting maceResolverSetting;
    private List<String> auraBuiltins = List.of();
    private List<String> maceBuiltins = List.of();
    private String auraFallback = "Matrix";
    private String maceFallback = "Disabled";

    private LuaExtensionRegistry() {
    }

    public static LuaExtensionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void bindAuraModeSetting(SelectSetting setting, String fallback) {
        if (setting == null) return;
        auraModeSetting = setting;
        auraBuiltins = List.copyOf(setting.getList());
        auraFallback = fallback == null ? "Matrix" : fallback;
        refreshAuraSetting();
    }

    public synchronized void bindMaceResolverSetting(SelectSetting setting, String fallback) {
        if (setting == null) return;
        maceResolverSetting = setting;
        maceBuiltins = List.copyOf(setting.getList());
        maceFallback = fallback == null ? "Disabled" : fallback;
        refreshMaceSetting();
    }

    public LuaTable createAuraApi(LuaScript owner) {
        LuaTable api = new LuaTable();
        api.set("register_rotation", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(1).checkjstring().trim();
                LuaTable definition = args.arg(2).checktable();
                registerAuraRotation(owner, name, definition);
                return LuaValue.valueOf(name);
            }
        });
        api.set("unregister_rotation", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(unregisterAuraRotation(owner, args.arg(1).checkjstring()));
            }
        });
        return api;
    }

    public LuaTable createMaceApi(LuaScript owner) {
        LuaTable api = new LuaTable();
        api.set("register_resolver", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(1).checkjstring().trim();
                LuaTable definition = args.arg(2).checktable();
                registerMaceResolver(owner, name, definition);
                return LuaValue.valueOf(name);
            }
        });
        api.set("unregister_resolver", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(unregisterMaceResolver(owner, args.arg(1).checkjstring()));
            }
        });
        return api;
    }

    public synchronized void registerAuraRotation(LuaScript owner, String name, LuaTable definition) {
        validateRegistration(owner, name, definition, true);
        String key = key(name);
        auraRotations.put(key, new AuraRotationExtension(owner, name, definition));
        refreshAuraSetting();
    }

    public synchronized void registerMaceResolver(LuaScript owner, String name, LuaTable definition) {
        validateRegistration(owner, name, definition, false);
        String key = key(name);
        maceResolvers.put(key, new MaceResolverExtension(owner, name, definition));
        refreshMaceSetting();
    }

    public synchronized boolean unregisterAuraRotation(LuaScript owner, String name) {
        AuraRotationExtension extension = auraRotations.get(key(name));
        if (extension == null || extension.owner != owner) return false;
        auraRotations.remove(key(name));
        refreshAuraSetting();
        return true;
    }

    public synchronized boolean unregisterMaceResolver(LuaScript owner, String name) {
        MaceResolverExtension extension = maceResolvers.get(key(name));
        if (extension == null || extension.owner != owner) return false;
        maceResolvers.remove(key(name));
        refreshMaceSetting();
        return true;
    }

    public synchronized void unregisterOwner(LuaScript owner) {
        if (owner == null) return;
        auraRotations.values().removeIf(extension -> extension.owner == owner);
        maceResolvers.values().removeIf(extension -> extension.owner == owner);
        refreshAuraSetting();
        refreshMaceSetting();
    }

    public synchronized void onOwnerStateChanged(LuaScript owner) {
        refreshAuraSetting();
        refreshMaceSetting();
    }

    public synchronized RotationMode getAuraRotationMode(String name) {
        AuraRotationExtension extension = auraRotations.get(key(name));
        return isActive(extension == null ? null : extension.owner) ? extension.mode : null;
    }

    public synchronized RotationConfig getAuraRotationConfig(String name, RotationConfig fallback) {
        return getAuraRotationConfig(name, fallback, null);
    }

    public synchronized RotationConfig getAuraRotationConfig(String name, RotationConfig fallback, LivingEntity target) {
        AuraRotationExtension extension = auraRotations.get(key(name));
        if (extension == null || !isActive(extension.owner)) return fallback;
        return extension.rotationConfig(fallback, target);
    }

    public synchronized boolean isCustomMaceResolver(String name) {
        MaceResolverExtension extension = maceResolvers.get(key(name));
        return extension != null && isActive(extension.owner);
    }

    public boolean maceTargetingPhase(String name, boolean fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(null, null);
        return extension.booleanCallback("active", ctx, fallback);
    }

    public boolean maceForcedSmashReady(String name, LivingEntity target, boolean fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(target, null);
        return extension.booleanCallback("can_smash", ctx, fallback);
    }

    public boolean maceCandidate(String name, LivingEntity target, boolean fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(target, null);
        return extension.booleanCallback("candidate", ctx, fallback);
    }

    public double maceScore(String name, LivingEntity target, double fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(target, null);
        return extension.numberCallback("score", ctx, fallback);
    }

    public float maceSearchBonus(String name, float fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(null, null);
        return Math.max(0.0f, (float) extension.numberCallback("search_bonus", ctx, fallback));
    }

    public boolean macePreSwap(String name, LivingEntity target, boolean fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(target, null);
        return extension.booleanCallback("pre_swap", ctx, fallback);
    }

    public boolean maceBoostRotation(String name, LivingEntity target, boolean fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null) return fallback;
        LuaTable ctx = maceContext(target, null);
        return extension.booleanCallback("boost_rotation", ctx, fallback);
    }

    public Vec3d maceResolvePoint(String name, LivingEntity target, Vec3d fallback) {
        MaceResolverExtension extension = activeMace(name);
        if (extension == null || target == null) return fallback;
        LuaValue callback = extension.definition.get("resolve");
        if (!callback.isfunction()) return fallback;

        LuaTable ctx = maceContext(target, fallback);
        try {
            LuaValue result = callback.call(ctx);
            Vec3d vector = vectorFromLua(result);
            return vector == null ? fallback : vector;
        } catch (Throwable throwable) {
            extension.error("mace_resolver:" + extension.name + ":resolve", throwable);
            return fallback;
        }
    }

    public synchronized List<String> activeAuraRotationNames() {
        List<String> names = new ArrayList<>();
        for (AuraRotationExtension extension : auraRotations.values()) {
            if (isActive(extension.owner)) names.add(extension.name);
        }
        return names;
    }

    public synchronized List<String> activeMaceResolverNames() {
        List<String> names = new ArrayList<>();
        for (MaceResolverExtension extension : maceResolvers.values()) {
            if (isActive(extension.owner)) names.add(extension.name);
        }
        return names;
    }

    public synchronized List<String> auraRotationNamesFor(LuaScript owner) {
        List<String> names = new ArrayList<>();
        for (AuraRotationExtension extension : auraRotations.values()) {
            if (extension.owner == owner) names.add(extension.name);
        }
        return names;
    }

    public synchronized List<String> maceResolverNamesFor(LuaScript owner) {
        List<String> names = new ArrayList<>();
        for (MaceResolverExtension extension : maceResolvers.values()) {
            if (extension.owner == owner) names.add(extension.name);
        }
        return names;
    }

    private synchronized MaceResolverExtension activeMace(String name) {
        MaceResolverExtension extension = maceResolvers.get(key(name));
        return extension != null && isActive(extension.owner) ? extension : null;
    }

    private void validateRegistration(LuaScript owner, String name, LuaTable definition, boolean aura) {
        if (owner == null) throw new LuaError("extension owner is missing");
        if (name == null || name.isBlank()) throw new LuaError("extension name cannot be empty");
        if (name.length() > 48) throw new LuaError("extension name is too long (max 48)");

        List<String> builtins = aura ? auraBuiltins : maceBuiltins;
        for (String builtin : builtins) {
            if (builtin.equalsIgnoreCase(name)) {
                throw new LuaError("extension name conflicts with built-in mode: " + name);
            }
        }

        if (aura) {
            AuraRotationExtension existing = auraRotations.get(key(name));
            if (existing != null && existing.owner != owner) {
                throw new LuaError("Aura rotation already registered: " + name);
            }
            if (!definition.get("rotate").isfunction() && !definition.get("update").isfunction()) {
                throw new LuaError("aura.register_rotation requires rotate(ctx) or update(ctx)");
            }
        } else {
            MaceResolverExtension existing = maceResolvers.get(key(name));
            if (existing != null && existing.owner != owner) {
                throw new LuaError("Mace resolver already registered: " + name);
            }
        }
    }

    private synchronized void refreshAuraSetting() {
        if (auraModeSetting == null) return;
        List<String> values = new ArrayList<>(auraBuiltins);
        values.addAll(activeAuraRotationNames());
        auraModeSetting.setValues(unique(values), auraFallback);
    }

    private synchronized void refreshMaceSetting() {
        if (maceResolverSetting == null) return;
        List<String> values = new ArrayList<>(maceBuiltins);
        values.addAll(activeMaceResolverNames());
        maceResolverSetting.setValues(unique(values), maceFallback);
    }

    private List<String> unique(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String key = key(value);
            if (seen.add(key)) result.add(value);
        }
        return result;
    }

    private boolean isActive(LuaScript script) {
        return script != null && script.isEnabled();
    }

    private String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private LuaTable rotationProfileContext(LivingEntity target, RotationConfig base) {
        LuaTable ctx = new LuaTable();
        ctx.set("player", mc.player == null ? LuaValue.NIL : LuaApiFactory.entity(mc.player));
        ctx.set("target", target == null ? LuaValue.NIL : LuaApiFactory.entity(target));
        ctx.set("distance", LuaValue.valueOf(
                mc.player == null || target == null
                        ? Double.POSITIVE_INFINITY
                        : mc.player.distanceTo(target)
        ));
        ctx.set("target_velocity", target == null ? LuaValue.NIL : vectorTable(target.getVelocity()));
        ctx.set("base_profile", rotationConfigTable(base));
        ctx.set("phase", LuaValue.valueOf(RotationManager.INSTANCE.getRotationStateName()));
        ctx.set("time_ms", LuaValue.valueOf((double) System.currentTimeMillis()));
        return ctx;
    }

    private static LuaTable rotationConfigTable(RotationConfig config) {
        LuaTable table = new LuaTable();
        RotationConfig value = config == null ? new RotationConfig() : config;
        table.set("acquire_speed", LuaValue.valueOf(value.getAcquireSpeed()));
        table.set("track_speed", LuaValue.valueOf(value.getTrackSpeed()));
        table.set("return_speed", LuaValue.valueOf(value.getReturnSpeed()));
        table.set("max_yaw", LuaValue.valueOf(value.getMaxYawSpeed()));
        table.set("max_pitch", LuaValue.valueOf(value.getMaxPitchSpeed()));
        table.set("acceleration", LuaValue.valueOf(value.getAcceleration()));
        table.set("deceleration", LuaValue.valueOf(value.getDeceleration()));
        table.set("aim_smoothing", LuaValue.valueOf(value.getAimSmoothing()));
        return table;
    }

    private LuaTable maceContext(LivingEntity target, Vec3d fallbackPoint) {
        LuaTable ctx = new LuaTable();
        if (mc.player != null) {
            ctx.set("player", LuaApiFactory.entity(mc.player));
            ctx.set("fall_distance", LuaValue.valueOf(mc.player.fallDistance));
            ctx.set("velocity_y", LuaValue.valueOf(mc.player.getVelocity().y));
            ctx.set("on_ground", LuaValue.valueOf(mc.player.isOnGround()));
            ctx.set("vanilla_smash", LuaValue.valueOf(MaceItem.shouldDealAdditionalDamage(mc.player)));
        } else {
            ctx.set("player", LuaValue.NIL);
            ctx.set("fall_distance", LuaValue.valueOf(0));
            ctx.set("velocity_y", LuaValue.valueOf(0));
            ctx.set("on_ground", LuaValue.FALSE);
            ctx.set("vanilla_smash", LuaValue.FALSE);
        }

        if (target != null) {
            ctx.set("target", LuaApiFactory.entity(target));
            ctx.set("distance", LuaValue.valueOf(mc.player == null ? Double.POSITIVE_INFINITY : mc.player.distanceTo(target)));
            ctx.set("target_velocity", vectorTable(target.getVelocity()));
            ctx.set("target_pos", vectorTable(target.getEntityPos()));
        } else {
            ctx.set("target", LuaValue.NIL);
            ctx.set("distance", LuaValue.valueOf(Double.POSITIVE_INFINITY));
        }

        ctx.set("fallback_point", fallbackPoint == null ? LuaValue.NIL : vectorTable(fallbackPoint));
        ctx.set("time_ms", LuaValue.valueOf((double) System.currentTimeMillis()));
        return ctx;
    }

    private static LuaTable rotationTable(Rotation rotation) {
        LuaTable table = new LuaTable();
        if (rotation != null) {
            table.set("yaw", LuaValue.valueOf(rotation.getYaw()));
            table.set("pitch", LuaValue.valueOf(rotation.getPitch()));
        }
        return table;
    }

    private static LuaTable vectorTable(Vec3d vector) {
        LuaTable table = new LuaTable();
        if (vector != null) {
            table.set("x", LuaValue.valueOf(vector.x));
            table.set("y", LuaValue.valueOf(vector.y));
            table.set("z", LuaValue.valueOf(vector.z));
        }
        return table;
    }

    private static Vec3d vectorFromLua(LuaValue value) {
        if (value == null || !value.istable()) return null;
        LuaTable table = value.checktable();
        LuaValue x = table.get("x");
        LuaValue y = table.get("y");
        LuaValue z = table.get("z");
        if (!x.isnumber() || !y.isnumber() || !z.isnumber()) return null;
        return new Vec3d(x.todouble(), y.todouble(), z.todouble());
    }

    private final class LuaAuraRotationMode extends RotationMode {
        private final AuraRotationExtension extension;

        private LuaAuraRotationMode(AuraRotationExtension extension) {
            super(extension.name);
            this.extension = extension;
        }

        @Override
        public Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, Vec3d point, Entity entity) {
            if (!isActive(extension.owner)) return targetRotation;

            LuaValue callback = extension.definition.get("rotate");
            if (!callback.isfunction()) callback = extension.definition.get("update");
            if (!callback.isfunction()) return targetRotation;

            LuaTable ctx = new LuaTable();
            ctx.set("current", rotationTable(currentRotation));
            ctx.set("target_rotation", rotationTable(targetRotation));
            ctx.set("point", point == null ? LuaValue.NIL : vectorTable(point));
            ctx.set("entity", entity == null ? LuaValue.NIL : LuaApiFactory.entity(entity));
            ctx.set("time_ms", LuaValue.valueOf((double) System.currentTimeMillis()));

            Rotation delta = RotationUtils.calculateDelta(currentRotation, targetRotation);
            ctx.set("yaw_delta", LuaValue.valueOf(delta.getYaw()));
            ctx.set("pitch_delta", LuaValue.valueOf(delta.getPitch()));
            ctx.set("distance", LuaValue.valueOf(Math.hypot(delta.getYaw(), delta.getPitch())));
            ctx.set("phase", LuaValue.valueOf(RotationManager.INSTANCE.getRotationStateName()));
            ctx.set("profile", rotationConfigTable(extension.currentProfile()));

            try {
                Varargs result = callback.invoke(ctx);
                LuaValue first = result.arg1();
                if (first.istable()) {
                    LuaTable table = first.checktable();
                    LuaValue yaw = table.get("yaw");
                    LuaValue pitch = table.get("pitch");
                    if (yaw.isnumber() && pitch.isnumber()) {
                        return sanitizeRotation(currentRotation, (float) yaw.todouble(), (float) pitch.todouble());
                    }
                }
                if (first.isnumber() && result.arg(2).isnumber()) {
                    return sanitizeRotation(currentRotation, (float) first.todouble(), (float) result.arg(2).todouble());
                }
            } catch (Throwable throwable) {
                extension.error("aura_rotation:" + extension.name, throwable);
            }
            return targetRotation;
        }

        @Override
        public Vec3d randomValue() {
            if (!isActive(extension.owner)) return Vec3d.ZERO;
            LuaValue callback = extension.definition.get("random");
            if (!callback.isfunction()) return Vec3d.ZERO;
            try {
                Vec3d result = vectorFromLua(callback.call());
                return result == null ? Vec3d.ZERO : result;
            } catch (Throwable throwable) {
                extension.error("aura_rotation:" + extension.name + ":random", throwable);
                return Vec3d.ZERO;
            }
        }

        @Override
        public boolean bypassManagerSmoothing() {
            LuaValue direct = extension.definition.get("direct");
            if (!direct.isnil()) {
                return direct.optboolean(false);
            }
            return !extension.hasProfileDefinition();
        }

        private Rotation sanitizeRotation(Rotation current, float yaw, float pitch) {
            float continuousYaw = current.getYaw() + MathHelper.wrapDegrees(yaw - current.getYaw());
            return new Rotation(continuousYaw, MathHelper.clamp(pitch, -90.0f, 90.0f));
        }
    }

    private abstract static class ExtensionBase {
        final LuaScript owner;
        final String name;
        final LuaTable definition;

        ExtensionBase(LuaScript owner, String name, LuaTable definition) {
            this.owner = owner;
            this.name = name;
            this.definition = definition;
        }

        void error(String event, Throwable throwable) {
            owner.reportRuntimeError(null, event, throwable);
        }

        boolean booleanCallback(String key, LuaTable ctx, boolean fallback) {
            LuaValue callback = definition.get(key);
            if (!callback.isfunction()) return fallback;
            try {
                return callback.call(ctx).optboolean(fallback);
            } catch (Throwable throwable) {
                error(key, throwable);
                return fallback;
            }
        }

        double numberCallback(String key, LuaTable ctx, double fallback) {
            LuaValue callback = definition.get(key);
            if (!callback.isfunction()) return fallback;
            try {
                return callback.call(ctx).optdouble(fallback);
            } catch (Throwable throwable) {
                error(key, throwable);
                return fallback;
            }
        }
    }

    private final class AuraRotationExtension extends ExtensionBase {
        final LuaAuraRotationMode mode;
        RotationConfig resolvedProfile = new RotationConfig();

        AuraRotationExtension(LuaScript owner, String name, LuaTable definition) {
            super(owner, name, definition);
            this.mode = new LuaAuraRotationMode(this);
        }

        RotationConfig rotationConfig(RotationConfig fallback, LivingEntity target) {
            RotationConfig base = fallback == null ? new RotationConfig() : fallback;
            LuaValue profileValue = definition.get("profile");
            if (profileValue.isnil()) {
                profileValue = definition.get("config");
            }

            if (profileValue.isfunction()) {
                try {
                    profileValue = profileValue.call(rotationProfileContext(target, base));
                } catch (Throwable throwable) {
                    error("aura_rotation:" + name + ":profile", throwable);
                    resolvedProfile = copyProfile(base);
                    return resolvedProfile;
                }
            }

            resolvedProfile = parseProfile(profileValue, base);
            return resolvedProfile;
        }

        RotationConfig currentProfile() {
            return resolvedProfile == null ? new RotationConfig() : resolvedProfile;
        }

        boolean hasProfileDefinition() {
            return !definition.get("profile").isnil() || !definition.get("config").isnil();
        }

        private RotationConfig parseProfile(LuaValue value, RotationConfig fallback) {
            if (value == null || value.isnil()) {
                return copyProfile(fallback);
            }

            if (value.isstring()) {
                return switch (value.tojstring().trim().toLowerCase(Locale.ROOT)) {
                    case "fast" -> RotationConfig.fast();
                    case "smooth" -> RotationConfig.smooth();
                    case "legit" -> RotationConfig.legit();
                    case "default" -> new RotationConfig();
                    default -> copyProfile(fallback);
                };
            }

            if (!value.istable()) {
                return copyProfile(fallback);
            }

            LuaTable profile = value.checktable();
            return new RotationConfig(
                    positive(profile, fallback.getAcquireSpeed(), "acquire_speed", "acquireSpeed"),
                    positive(profile, fallback.getTrackSpeed(), "track_speed", "trackSpeed"),
                    positive(profile, fallback.getReturnSpeed(), "return_speed", "returnSpeed"),
                    positive(profile, fallback.getMaxYawSpeed(), "max_yaw", "max_yaw_speed", "maxYaw", "maxYawSpeed"),
                    positive(profile, fallback.getMaxPitchSpeed(), "max_pitch", "max_pitch_speed", "maxPitch", "maxPitchSpeed"),
                    ranged(profile, fallback.getAcceleration(), 0.01f, 2.0f, "acceleration", "accel"),
                    ranged(profile, fallback.getDeceleration(), 0.01f, 2.0f, "deceleration", "decel"),
                    ranged(profile, fallback.getAimSmoothing(), 0.01f, 1.5f, "aim_smoothing", "smoothing", "aimSmoothing")
            );
        }

        private RotationConfig copyProfile(RotationConfig source) {
            RotationConfig value = source == null ? new RotationConfig() : source;
            return new RotationConfig(
                    value.getAcquireSpeed(),
                    value.getTrackSpeed(),
                    value.getReturnSpeed(),
                    value.getMaxYawSpeed(),
                    value.getMaxPitchSpeed(),
                    value.getAcceleration(),
                    value.getDeceleration(),
                    value.getAimSmoothing()
            );
        }

        private float positive(LuaTable table, float fallback, String... keys) {
            return Math.max(0.01f, readFloat(table, fallback, keys));
        }

        private float ranged(LuaTable table, float fallback, float min, float max, String... keys) {
            return MathHelper.clamp(readFloat(table, fallback, keys), min, max);
        }

        private float readFloat(LuaTable table, float fallback, String... keys) {
            for (String key : keys) {
                LuaValue value = table.get(key);
                if (value.isnumber()) {
                    return (float) value.todouble();
                }
            }
            return fallback;
        }
    }

    private static final class MaceResolverExtension extends ExtensionBase {
        MaceResolverExtension(LuaScript owner, String name, LuaTable definition) {
            super(owner, name, definition);
        }
    }
}
