package mind.beta.scripting.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import mind.beta.Initialization;
import mind.beta.events.impl.ChatEvent;
import mind.beta.events.impl.DrawEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.Setting;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.modules.module.setting.implement.TextSetting;
import mind.beta.scripting.LuaExecutionContext;
import mind.beta.scripting.LuaModule;
import mind.beta.scripting.LuaScript;
import mind.beta.scripting.ScriptManager;
import mind.beta.util.ColorUtil;
import mind.beta.util.config.impl.consolelogger.Logger;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.Render3D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class LuaApiFactory {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<LuaModule, List<LuaRotationController>> ROTATION_CONTROLLERS = Collections.synchronizedMap(new WeakHashMap<>());

    private LuaApiFactory() {
    }

    public static void install(Globals globals, LuaScript script, ScriptManager manager) {
        globals.set("module", moduleApi(script, manager));
        globals.set("client", clientApi());
        globals.set("entities", entitiesApi());
        globals.set("combat", combatApi());
        globals.set("rotation", rotationApi());
        globals.set("animation", animationApi());
        globals.set("easing", easingApi());
        globals.set("mathx", mathApi());
        globals.set("vec3", vec3Api());
        globals.set("color", colorApi());

        LuaTable api = new LuaTable();
        api.set("version", LuaValue.valueOf(3));
        api.set("name", LuaValue.valueOf("Mind Lua API"));
        globals.set("mind", api);
    }

    private static LuaTable moduleApi(LuaScript script, ScriptManager manager) {
        LuaTable table = new LuaTable();

        table.set("create", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(1).checkjstring().trim();
                String categoryName = args.arg(2).optjstring("Misc");
                String description = args.arg(3).optjstring("");

                if (name.isEmpty()) {
                    throw new LuaError("Module name cannot be empty");
                }
                if (name.length() > 64) {
                    throw new LuaError("Module name is too long (max 64 characters)");
                }

                ModuleCategory category = parseCategory(categoryName);
                LuaModule module = new LuaModule(script, name, description, category);
                manager.registerModule(script, module);
                return moduleTable(module);
            }
        });

        return table;
    }

    private static LuaTable moduleTable(LuaModule module) {
        LuaTable table = new LuaTable();

        table.set("name", LuaValue.valueOf(module.getName()));
        table.set("category", LuaValue.valueOf(module.getCategory().getReadableName()));

        table.set("on", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String event = args.arg(2).checkjstring();
                LuaValue callback = args.arg(3);
                if (!callback.isfunction()) {
                    throw new LuaError("module:on requires a function as the second argument");
                }
                module.on(event, callback);
                return table;
            }
        });

        table.set("set_enabled", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                module.setState(args.arg(2).toboolean());
                return LuaValue.NONE;
            }
        });

        table.set("is_enabled", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(module.isState());
            }
        });

        table.set("set_key", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                module.setKey(args.arg(2).checkint());
                return LuaValue.NONE;
            }
        });

        table.set("get_key", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(module.getKey());
            }
        });

        table.set("boolean", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                boolean value = args.arg(3).optboolean(false);
                String description = args.arg(4).optjstring("");
                BooleanSetting setting = new BooleanSetting(name, description).setValue(value);
                module.settings(setting);
                return booleanSetting(module, setting);
            }
        });

        table.set("number", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                float value = (float) args.arg(3).checkdouble();
                float min = (float) args.arg(4).checkdouble();
                float max = (float) args.arg(5).checkdouble();
                boolean integer = args.arg(6).optboolean(false);
                String description = args.arg(7).optjstring("");

                if (max < min) {
                    float swap = min;
                    min = max;
                    max = swap;
                }

                SliderSettings setting = new SliderSettings(name, description);
                if (integer) {
                    setting.range(Math.round(min), Math.round(max));
                } else {
                    setting.range(min, max);
                }
                setting.setValue(MathHelper.clamp(value, min, max));
                module.settings(setting);
                return numberSetting(module, setting);
            }
        });

        table.set("select", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                String defaultValue = args.arg(3).checkjstring();
                LuaTable optionsTable = args.arg(4).checktable();
                String description = args.arg(5).optjstring("");
                String[] options = stringArray(optionsTable);
                if (options.length == 0) {
                    throw new LuaError("select requires at least one option");
                }
                SelectSetting setting = new SelectSetting(name).value(options);
                setting.selected(defaultValue);
                module.settings(setting);
                return selectSetting(module, setting);
            }
        });

        table.set("multiselect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                LuaTable optionsTable = args.arg(3).checktable();
                LuaTable selectedTable = args.arg(4).istable() ? args.arg(4).checktable() : new LuaTable();
                String description = args.arg(5).optjstring("");
                String[] options = stringArray(optionsTable);
                String[] selected = stringArray(selectedTable);
                MultiSelectSetting setting = new MultiSelectSetting(name, description).value(options).selected(selected);
                module.settings(setting);
                return multiSelectSetting(module, setting);
            }
        });

        table.set("text", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                String value = args.arg(3).optjstring("");
                String description = args.arg(4).optjstring("");
                TextSetting setting = new TextSetting(name, description).setText(value);
                module.settings(setting);
                return textSetting(module, setting);
            }
        });

        table.set("color", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                int value = args.arg(3).optint(0xFFFFFFFF);
                String description = args.arg(4).optjstring("");
                ColorSetting setting = new ColorSetting(name, description).value(value);
                module.settings(setting);
                return colorSetting(module, setting);
            }
        });

        table.set("bind", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).checkjstring();
                int value = args.arg(3).optint(-1);
                String description = args.arg(4).optjstring("");
                BindSetting setting = new BindSetting(name, description).setKey(value);
                module.settings(setting);
                return bindSetting(module, setting);
            }
        });

        table.set("rotation_controller", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable options = args.arg(2).istable() ? args.arg(2).checktable() : new LuaTable();
                return rotationControllerTable(module, options);
            }
        });

        return table;
    }

    private static LuaTable booleanSetting(LuaModule module, BooleanSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.isValue());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                setting.setValue(args.arg(2).toboolean());
                return table;
            }
        });
        return table;
    }

    private static LuaTable numberSetting(LuaModule module, SliderSettings setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.getValue());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float value = (float) args.arg(2).checkdouble();
                setting.setValue(MathHelper.clamp(value, setting.getMin(), setting.getMax()));
                return table;
            }
        });
        return table;
    }

    private static LuaTable selectSetting(LuaModule module, SelectSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.getSelected());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String value = args.arg(2).checkjstring();
                setting.selected(value);
                return table;
            }
        });
        table.set("is", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.isSelected(args.arg(2).checkjstring()));
            }
        });
        return table;
    }

    private static LuaTable multiSelectSetting(LuaModule module, MultiSelectSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return stringTable(setting.getSelected());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable values = args.arg(2).checktable();
                setting.setSelected(new ArrayList<>(java.util.Arrays.asList(stringArray(values))));
                return table;
            }
        });
        table.set("is", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.isSelected(args.arg(2).checkjstring()));
            }
        });
        return table;
    }

    private static LuaTable textSetting(LuaModule module, TextSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.getText() == null ? "" : setting.getText());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                setting.setText(args.arg(2).checkjstring());
                return table;
            }
        });
        return table;
    }

    private static LuaTable colorSetting(LuaModule module, ColorSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.getColor());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                setting.setColor(args.arg(2).checkint());
                return table;
            }
        });
        return table;
    }

    private static LuaTable bindSetting(LuaModule module, BindSetting setting) {
        LuaTable table = baseSettingTable(module, setting);
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(setting.getKey());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                setting.setKey(args.arg(2).checkint());
                return table;
            }
        });
        return table;
    }

    private static LuaTable baseSettingTable(LuaModule module, Setting setting) {
        LuaTable table = new LuaTable();
        table.set("name", LuaValue.valueOf(setting.getName()));
        table.set("visible", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue condition = args.arg(2);
                if (condition.isboolean()) {
                    boolean visible = condition.toboolean();
                    setting.setVisible(() -> visible);
                    return table;
                }
                if (!condition.isfunction()) {
                    throw new LuaError("setting:visible expects a boolean or function");
                }
                setting.setVisible(() -> {
                    try {
                        return condition.call().toboolean();
                    } catch (Throwable throwable) {
                        module.getOwner().reportRuntimeError(module, "setting-visible:" + setting.getName(), throwable);
                        return true;
                    }
                });
                return table;
            }
        });
        return table;
    }

    private static LuaTable clientApi() {
        LuaTable table = new LuaTable();

        table.set("player", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return mc.player == null ? LuaValue.NIL : entity(mc.player);
            }
        });

        table.set("in_game", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(mc.player != null && mc.world != null);
            }
        });

        table.set("message", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                String text = value.tojstring();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("[Lua] " + text), false);
                } else {
                    Logger.info("[Lua] " + text);
                }
                return LuaValue.NIL;
            }
        });

        table.set("module_enabled", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                Initialization initialization = Initialization.getInstance();
                if (initialization == null || initialization.getManager() == null || initialization.getManager().getModuleProvider() == null) {
                    return LuaValue.FALSE;
                }
                ModuleStructure module = initialization.getManager().getModuleProvider().get(value.checkjstring());
                return LuaValue.valueOf(module != null && module.isState());
            }
        });

        table.set("width", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(Render2D.getFixedScaledWidth());
            }
        });

        table.set("height", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(Render2D.getFixedScaledHeight());
            }
        });

        table.set("time_ms", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf((double) System.currentTimeMillis());
            }
        });

        return table;
    }

    private static LuaTable entitiesApi() {
        LuaTable table = new LuaTable();

        table.set("closest_player", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (mc.player == null || mc.world == null) {
                    return LuaValue.NIL;
                }
                double range = args.arg(1).optdouble(64.0);
                return mc.world.getPlayers().stream()
                        .filter(player -> player != mc.player)
                        .filter(Entity::isAlive)
                        .filter(player -> mc.player.distanceTo(player) <= range)
                        .min(Comparator.comparingDouble(player -> mc.player.distanceTo(player)))
                        .<LuaValue>map(LuaApiFactory::entity)
                        .orElse(LuaValue.NIL);
            }
        });

        table.set("players", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable result = new LuaTable();
                if (mc.player == null || mc.world == null) {
                    return result;
                }
                double range = args.arg(1).optdouble(Double.MAX_VALUE);
                int index = 1;
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == mc.player || !player.isAlive()) {
                        continue;
                    }
                    if (mc.player.distanceTo(player) > range) {
                        continue;
                    }
                    result.set(index++, entity(player));
                }
                return result;
            }
        });

        table.set("by_id", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                if (mc.world == null) {
                    return LuaValue.NIL;
                }
                Entity entity = mc.world.getEntityById(value.checkint());
                return entity == null ? LuaValue.NIL : entity(entity);
            }
        });

        table.set("aura_target", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (Aura.getInstance() == null || !Aura.getInstance().isState()) {
                    return LuaValue.NIL;
                }
                LivingEntity target = Aura.target;
                if (target == null || !target.isAlive()) {
                    return LuaValue.NIL;
                }
                return entity(target);
            }
        });

        return table;
    }

    private static LuaTable combatApi() {
        LuaTable table = new LuaTable();

        table.set("attack", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireActiveModule();
                Entity target = entityFromLua(args.arg(1));
                boolean swing = args.arg(2).optboolean(true);
                if (mc.player == null || mc.interactionManager == null || target == null || !target.isAlive()) {
                    return LuaValue.FALSE;
                }
                mc.interactionManager.attackEntity(mc.player, target);
                if (swing) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                return LuaValue.TRUE;
            }
        });

        table.set("swing", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                requireActiveModule();
                if (mc.player == null) {
                    return LuaValue.FALSE;
                }
                mc.player.swingHand(Hand.MAIN_HAND);
                return LuaValue.TRUE;
            }
        });

        table.set("cooldown", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (mc.player == null) {
                    return LuaValue.valueOf(0.0);
                }
                float ticks = (float) args.arg(1).optdouble(0.0);
                return LuaValue.valueOf(mc.player.getAttackCooldownProgress(ticks));
            }
        });

        table.set("ready", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (mc.player == null) {
                    return LuaValue.FALSE;
                }
                float threshold = MathHelper.clamp((float) args.arg(1).optdouble(0.92), 0.0f, 1.0f);
                float ticks = (float) args.arg(2).optdouble(0.0);
                return LuaValue.valueOf(mc.player.getAttackCooldownProgress(ticks) >= threshold);
            }
        });

        table.set("can_see", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                Entity target = entityFromLua(value);
                return LuaValue.valueOf(mc.player != null && target != null && mc.player.canSee(target));
            }
        });

        table.set("valid", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Entity target = entityFromLua(args.arg(1));
                double range = Math.max(0.0, args.arg(2).optdouble(Double.MAX_VALUE));
                boolean requireVisible = args.arg(3).optboolean(false);
                if (mc.player == null || target == null || target == mc.player || !target.isAlive()) {
                    return LuaValue.FALSE;
                }
                if (mc.player.distanceTo(target) > range) {
                    return LuaValue.FALSE;
                }
                return LuaValue.valueOf(!requireVisible || mc.player.canSee(target));
            }
        });

        return table;
    }

    private static LuaTable rotationApi() {
        LuaTable table = new LuaTable();

        table.set("look_at", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaModule owner = requireActiveModule();
                Entity target = entityFromLua(args.arg(1));
                if (target == null || mc.player == null) {
                    return LuaValue.FALSE;
                }

                int reset = Math.max(1, args.arg(2).optint(1));
                boolean moveFix = args.arg(3).optboolean(true);
                Rotation desired = RotationUtils.calculateAngle(target.getEyePos());
                RotationManager.INSTANCE.rotateTo(
                        desired,
                        reset,
                        new RotationRequest(moveFix, true),
                        TaskPriority.STANDARD,
                        owner
                );
                return LuaValue.TRUE;
            }
        });

        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaModule owner = requireActiveModule();
                float yaw = (float) args.arg(1).checkdouble();
                float pitch = MathHelper.clamp((float) args.arg(2).checkdouble(), -90.0f, 90.0f);
                int reset = Math.max(1, args.arg(3).optint(1));
                boolean moveFix = args.arg(4).optboolean(true);
                RotationManager.INSTANCE.rotateTo(
                        new Rotation(yaw, pitch),
                        reset,
                        new RotationRequest(moveFix, true),
                        TaskPriority.STANDARD,
                        owner
                );
                return LuaValue.TRUE;
            }
        });

        table.set("release", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaModule owner = requireActiveModule();
                List<LuaRotationController> controllers = ROTATION_CONTROLLERS.get(owner);
                if (controllers != null && !controllers.isEmpty()) {
                    for (LuaRotationController controller : new ArrayList<>(controllers)) {
                        controller.releaseSmooth();
                    }
                } else {
                    RotationManager.INSTANCE.startReturning(owner);
                }
                return LuaValue.NIL;
            }
        });

        table.set("yaw", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (mc.player == null) {
                    return LuaValue.valueOf(0);
                }
                return LuaValue.valueOf(RotationManager.INSTANCE.getRotation().getYaw());
            }
        });

        table.set("pitch", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (mc.player == null) {
                    return LuaValue.valueOf(0);
                }
                return LuaValue.valueOf(RotationManager.INSTANCE.getRotation().getPitch());
            }
        });

        table.set("current", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return rotationTable(RotationManager.INSTANCE.getRotation());
            }
        });

        table.set("camera", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return mc.player == null ? LuaValue.NIL : rotationTable(RotationUtils.cameraAngle());
            }
        });

        table.set("angle_to", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                Entity target = entityFromLua(value);
                if (target == null || mc.player == null) {
                    return LuaValue.NIL;
                }
                return rotationTable(RotationUtils.calculateAngle(target.getEyePos()));
            }
        });

        table.set("angle_to_pos", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (mc.player == null) {
                    return LuaValue.NIL;
                }
                Vec3d pos = new Vec3d(args.arg(1).checkdouble(), args.arg(2).checkdouble(), args.arg(3).checkdouble());
                return rotationTable(RotationUtils.calculateAngle(pos));
            }
        });

        table.set("delta", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Rotation from = new Rotation((float) args.arg(1).checkdouble(), (float) args.arg(2).checkdouble());
                Rotation to = new Rotation((float) args.arg(3).checkdouble(), (float) args.arg(4).checkdouble());
                return rotationTable(RotationUtils.calculateDelta(from, to));
            }
        });

        table.set("controller", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaModule owner = requireActiveModule();
                LuaTable options = args.arg(1).istable() ? args.arg(1).checktable() : new LuaTable();
                return rotationControllerTable(owner, options);
            }
        });

        return table;
    }

    private static LuaTable animationApi() {
        LuaTable table = new LuaTable();
        table.set("new", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return animationTable(new LuaAnimationValue(args.arg(1).optdouble(0.0)));
            }
        });
        return table;
    }

    private static LuaTable easingApi() {
        LuaTable table = new LuaTable();
        table.set("apply", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(LuaEasing.apply(args.arg(1).optjstring("smoothstep"), args.arg(2).checkdouble()));
            }
        });
        table.set("bezier", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(LuaEasing.cubicBezier(
                        args.arg(1).checkdouble(),
                        args.arg(2).checkdouble(),
                        args.arg(3).checkdouble(),
                        args.arg(4).checkdouble(),
                        args.arg(5).checkdouble()
                ));
            }
        });
        return table;
    }

    private static LuaTable mathApi() {
        LuaTable table = new LuaTable();
        table.set("clamp", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double value = args.arg(1).checkdouble();
                double min = args.arg(2).checkdouble();
                double max = args.arg(3).checkdouble();
                return LuaValue.valueOf(Math.max(min, Math.min(max, value)));
            }
        });
        table.set("lerp", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double from = args.arg(1).checkdouble();
                double to = args.arg(2).checkdouble();
                double t = args.arg(3).checkdouble();
                return LuaValue.valueOf(from + (to - from) * t);
            }
        });
        table.set("lerp_angle", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float from = (float) args.arg(1).checkdouble();
                float to = (float) args.arg(2).checkdouble();
                float t = (float) args.arg(3).checkdouble();
                return LuaValue.valueOf(from + MathHelper.wrapDegrees(to - from) * t);
            }
        });
        table.set("wrap_degrees", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                return LuaValue.valueOf(MathHelper.wrapDegrees((float) value.checkdouble()));
            }
        });
        table.set("smoothstep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double edge0 = args.arg(1).checkdouble();
                double edge1 = args.arg(2).checkdouble();
                double x = args.arg(3).checkdouble();
                double t = edge0 == edge1 ? 0.0 : Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
                return LuaValue.valueOf(t * t * (3.0 - 2.0 * t));
            }
        });
        return table;
    }

    private static LuaTable vec3Api() {
        LuaTable table = new LuaTable();
        table.set("new", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return vectorTable(new Vec3d(args.arg(1).optdouble(0.0), args.arg(2).optdouble(0.0), args.arg(3).optdouble(0.0)));
            }
        });
        table.set("lerp", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d a = vectorFromLua(args.arg(1));
                Vec3d b = vectorFromLua(args.arg(2));
                double t = args.arg(3).checkdouble();
                if (a == null || b == null) return LuaValue.NIL;
                return vectorTable(new Vec3d(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t
                ));
            }
        });
        return table;
    }

    private static LuaTable colorApi() {
        LuaTable table = new LuaTable();

        table.set("rgb", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int r = clampByte(args.arg(1).checkint());
                int g = clampByte(args.arg(2).checkint());
                int b = clampByte(args.arg(3).checkint());
                return LuaValue.valueOf((255 << 24) | (r << 16) | (g << 8) | b);
            }
        });

        table.set("rgba", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int r = clampByte(args.arg(1).checkint());
                int g = clampByte(args.arg(2).checkint());
                int b = clampByte(args.arg(3).checkint());
                int a = clampByte(args.arg(4).checkint());
                return LuaValue.valueOf((a << 24) | (r << 16) | (g << 8) | b);
            }
        });

        table.set("with_alpha", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int color = args.arg(1).checkint();
                int alpha = clampByte(args.arg(2).checkint());
                return LuaValue.valueOf((alpha << 24) | (color & 0x00FFFFFF));
            }
        });

        table.set("multiply_alpha", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int color = args.arg(1).checkint();
                float alpha = (float) Math.max(0.0, Math.min(1.0, args.arg(2).checkdouble()));
                return LuaValue.valueOf(ColorUtil.multAlpha(color, alpha));
            }
        });

        table.set("lerp", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int from = args.arg(1).checkint();
                int to = args.arg(2).checkint();
                float t = MathHelper.clamp((float) args.arg(3).checkdouble(), 0.0f, 1.0f);
                return LuaValue.valueOf(ColorUtil.interpolateColor(from, to, t));
            }
        });

        table.set("hsb", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float h = (float) args.arg(1).checkdouble();
                float saturation = MathHelper.clamp((float) args.arg(2).optdouble(1.0), 0.0f, 1.0f);
                float brightness = MathHelper.clamp((float) args.arg(3).optdouble(1.0), 0.0f, 1.0f);
                int alpha = clampByte(args.arg(4).optint(255));
                float hue = h - (float) Math.floor(h);
                int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
                return LuaValue.valueOf((alpha << 24) | (rgb & 0x00FFFFFF));
            }
        });

        table.set("rainbow", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double speed = Math.max(1.0, args.arg(1).optdouble(4000.0));
                double offset = args.arg(2).optdouble(0.0);
                float saturation = MathHelper.clamp((float) args.arg(3).optdouble(0.8), 0.0f, 1.0f);
                float brightness = MathHelper.clamp((float) args.arg(4).optdouble(1.0), 0.0f, 1.0f);
                float hue = (float) (((System.currentTimeMillis() + offset) % speed) / speed);
                java.awt.Color c = java.awt.Color.getHSBColor(hue, saturation, brightness);
                return LuaValue.valueOf((255 << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());
            }
        });

        return table;
    }

    public static LuaTable renderContext(DrawEvent event) {
        LuaTable table = new LuaTable();
        table.set("partial_ticks", LuaValue.valueOf(event.getPartialTicks()));
        table.set("width", LuaValue.valueOf(Render2D.getFixedScaledWidth()));
        table.set("height", LuaValue.valueOf(Render2D.getFixedScaledHeight()));

        table.set("text", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String text = args.arg(2).checkjstring();
                float x = (float) args.arg(3).checkdouble();
                float y = (float) args.arg(4).checkdouble();
                float size = (float) args.arg(5).optdouble(8.0);
                int color = args.arg(6).optint(0xFFFFFFFF);
                Fonts.MEDIUM.draw(text, x, y, size, color);
                return LuaValue.NONE;
            }
        });

        table.set("centered_text", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String text = args.arg(2).checkjstring();
                float x = (float) args.arg(3).checkdouble();
                float y = (float) args.arg(4).checkdouble();
                float size = (float) args.arg(5).optdouble(8.0);
                int color = args.arg(6).optint(0xFFFFFFFF);
                Fonts.MEDIUM.drawCentered(text, x, y, size, color);
                return LuaValue.NONE;
            }
        });

        table.set("rect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                int color = args.arg(6).optint(0xFFFFFFFF);
                float radius = (float) args.arg(7).optdouble(0.0);
                Render2D.rect(x, y, width, height, color, radius);
                return LuaValue.NONE;
            }
        });

        table.set("text_width", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String text = args.arg(2).checkjstring();
                float size = (float) args.arg(3).optdouble(8.0);
                return LuaValue.valueOf(Fonts.MEDIUM.getWidth(text, size));
            }
        });

        table.set("gradient", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                int c1 = args.arg(6).checkint();
                int c2 = args.arg(7).checkint();
                int c3 = args.arg(8).optint(c2);
                int c4 = args.arg(9).optint(c1);
                float radius = (float) args.arg(10).optdouble(0.0);
                Render2D.gradientRect(x, y, width, height, new int[]{c1, c2, c3, c4}, radius);
                return LuaValue.NONE;
            }
        });

        table.set("outline", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                float thickness = (float) Math.max(0.1, args.arg(6).optdouble(1.0));
                int color = args.arg(7).optint(0xFFFFFFFF);
                float radius = (float) args.arg(8).optdouble(0.0);
                Render2D.outline(x, y, width, height, thickness, color, radius);
                return LuaValue.NONE;
            }
        });

        table.set("gradient_outline", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                float thickness = (float) Math.max(0.1, args.arg(6).optdouble(1.0));
                int c1 = args.arg(7).checkint();
                int c2 = args.arg(8).checkint();
                int c3 = args.arg(9).optint(c2);
                int c4 = args.arg(10).optint(c1);
                float radius = (float) args.arg(11).optdouble(0.0);
                Render2D.gradientOutline(x, y, width, height, thickness, new int[]{c1, c2, c3, c4, c1, c2, c3, c4}, radius);
                return LuaValue.NONE;
            }
        });

        table.set("blur", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                float blurRadius = (float) Math.max(0.0, args.arg(6).optdouble(12.0));
                float radius = (float) Math.max(0.0, args.arg(7).optdouble(6.0));
                int tint = args.arg(8).optint(0x20000000);
                Render2D.blur(x, y, width, height, blurRadius, radius, tint);
                return LuaValue.NONE;
            }
        });

        table.set("glow", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float width = (float) args.arg(4).checkdouble();
                float height = (float) args.arg(5).checkdouble();
                float thickness = (float) Math.max(0.1, args.arg(6).optdouble(2.0));
                int color = args.arg(7).optint(0xFFFFFFFF);
                float radius = (float) Math.max(0.0, args.arg(8).optdouble(6.0));
                float progress = MathHelper.clamp((float) args.arg(9).optdouble(1.0), 0.0f, 1.0f);
                float alpha = MathHelper.clamp((float) args.arg(10).optdouble(1.0), 0.0f, 1.0f);
                Render2D.glowOutline(x, y, width, height, thickness, color, radius, progress, alpha);
                return LuaValue.NONE;
            }
        });

        table.set("arc", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float size = (float) args.arg(4).checkdouble();
                float thickness = (float) Math.max(0.1, args.arg(5).optdouble(1.0));
                float degrees = (float) args.arg(6).optdouble(360.0);
                float rotation = (float) args.arg(7).optdouble(0.0);
                int color = args.arg(8).optint(0xFFFFFFFF);
                Render2D.arc(x, y, size, thickness, degrees, rotation, color);
                return LuaValue.NONE;
            }
        });

        table.set("arc_outline", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.arg(2).checkdouble();
                float y = (float) args.arg(3).checkdouble();
                float size = (float) args.arg(4).checkdouble();
                float thickness = (float) Math.max(0.1, args.arg(5).optdouble(2.0));
                float degrees = (float) args.arg(6).optdouble(360.0);
                float rotation = (float) args.arg(7).optdouble(0.0);
                float outlineThickness = (float) Math.max(0.1, args.arg(8).optdouble(1.0));
                int fill = args.arg(9).optint(0xFFFFFFFF);
                int outline = args.arg(10).optint(0xFF000000);
                Render2D.arcOutline(x, y, size, thickness, degrees, rotation, outlineThickness, fill, outline);
                return LuaValue.NONE;
            }
        });

        table.set("scissor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Scissor.enable(
                        (float) args.arg(2).checkdouble(),
                        (float) args.arg(3).checkdouble(),
                        (float) args.arg(4).checkdouble(),
                        (float) args.arg(5).checkdouble(),
                        Render2D.getFixedGuiScale()
                );
                return LuaValue.NONE;
            }
        });

        table.set("end_scissor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Scissor.disable();
                return LuaValue.NONE;
            }
        });

        return table;
    }

    public static LuaTable worldRenderContext(WorldRenderEvent event) {
        LuaTable table = new LuaTable();
        table.set("partial_ticks", LuaValue.valueOf(event.getPartialTicks()));

        table.set("entity_pos", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Entity target = entityFromLua(args.arg(2));
                if (target == null) return LuaValue.NIL;
                return vectorTable(interpolatedPosition(target, event.getPartialTicks()));
            }
        });

        table.set("box", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Entity target = entityFromLua(args.arg(2));
                if (target == null || !target.isAlive()) {
                    return LuaValue.FALSE;
                }
                int color = args.arg(3).optint(0xFFFFFFFF);
                float width = (float) Math.max(0.1, args.arg(4).optdouble(2.0));
                boolean fill = args.arg(5).optboolean(true);
                boolean depth = args.arg(6).optboolean(false);
                double expand = Math.max(0.0, args.arg(7).optdouble(0.0));
                Box box = interpolatedBox(target, event.getPartialTicks()).expand(expand);
                Render3D.drawBox(box, color, width, true, fill, depth);
                return LuaValue.TRUE;
            }
        });

        table.set("styled_box", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Entity target = entityFromLua(args.arg(2));
                if (target == null || !target.isAlive()) {
                    return LuaValue.FALSE;
                }
                int lineColor = args.arg(3).optint(0xFFFFFFFF);
                int fillColor = args.arg(4).optint(0x30000000);
                float width = (float) Math.max(0.1, args.arg(5).optdouble(2.0));
                boolean depth = args.arg(6).optboolean(false);
                double expand = Math.max(0.0, args.arg(7).optdouble(0.0));
                Box box = interpolatedBox(target, event.getPartialTicks()).expand(expand);
                Render3D.drawBox(box, fillColor, width, false, true, depth);
                Render3D.drawBox(box, lineColor, width, true, false, depth);
                return LuaValue.TRUE;
            }
        });

        table.set("box_at", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Box box = new Box(
                        args.arg(2).checkdouble(), args.arg(3).checkdouble(), args.arg(4).checkdouble(),
                        args.arg(5).checkdouble(), args.arg(6).checkdouble(), args.arg(7).checkdouble()
                );
                int lineColor = args.arg(8).optint(0xFFFFFFFF);
                int fillColor = args.arg(9).optint(0x30000000);
                float width = (float) Math.max(0.1, args.arg(10).optdouble(2.0));
                boolean depth = args.arg(11).optboolean(false);
                Render3D.drawBox(box, fillColor, width, false, true, depth);
                Render3D.drawBox(box, lineColor, width, true, false, depth);
                return LuaValue.NONE;
            }
        });

        table.set("line", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d from = new Vec3d(args.arg(2).checkdouble(), args.arg(3).checkdouble(), args.arg(4).checkdouble());
                Vec3d to = new Vec3d(args.arg(5).checkdouble(), args.arg(6).checkdouble(), args.arg(7).checkdouble());
                int fromColor = args.arg(8).optint(0xFFFFFFFF);
                int toColor = args.arg(9).optint(fromColor);
                float width = (float) Math.max(0.1, args.arg(10).optdouble(1.5));
                boolean depth = args.arg(11).optboolean(false);
                Render3D.drawLineGradient(from, to, fromColor, toColor, width, depth);
                return LuaValue.NONE;
            }
        });

        table.set("tracer", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Entity target = entityFromLua(args.arg(2));
                if (target == null || mc.player == null) {
                    return LuaValue.FALSE;
                }
                int color = args.arg(3).optint(0xFFFFFFFF);
                float width = (float) Math.max(0.1, args.arg(4).optdouble(1.5));
                boolean depth = args.arg(5).optboolean(false);
                Vec3d to = interpolatedPosition(target, event.getPartialTicks()).add(0.0, target.getHeight() * 0.5, 0.0);
                Render3D.drawLine(mc.player.getEyePos(), to, color, width, depth);
                return LuaValue.TRUE;
            }
        });

        table.set("ring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double x = args.arg(2).checkdouble();
                double y = args.arg(3).checkdouble();
                double z = args.arg(4).checkdouble();
                double radius = Math.max(0.001, args.arg(5).checkdouble());
                int segments = Math.max(8, Math.min(128, args.arg(6).optint(40)));
                int color = args.arg(7).optint(0xFFFFFFFF);
                float width = (float) Math.max(0.1, args.arg(8).optdouble(1.5));
                boolean depth = args.arg(9).optboolean(false);
                Vec3d previous = null;
                Vec3d first = null;
                for (int i = 0; i <= segments; i++) {
                    double angle = Math.PI * 2.0 * i / segments;
                    Vec3d point = new Vec3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
                    if (first == null) first = point;
                    if (previous != null) Render3D.drawLine(previous, point, color, width, depth);
                    previous = point;
                }
                return LuaValue.NONE;
            }
        });

        table.set("path", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable points = args.arg(2).checktable();
                int color = args.arg(3).optint(0xFFFFFFFF);
                float width = (float) Math.max(0.1, args.arg(4).optdouble(1.5));
                boolean depth = args.arg(5).optboolean(false);
                Vec3d previous = null;
                for (int i = 1; i <= points.length(); i++) {
                    Vec3d point = vectorFromLua(points.get(i));
                    if (point == null) continue;
                    if (previous != null) Render3D.drawLine(previous, point, color, width, depth);
                    previous = point;
                }
                return LuaValue.NONE;
            }
        });

        table.set("quad", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d p1 = vectorFromLua(args.arg(2));
                Vec3d p2 = vectorFromLua(args.arg(3));
                Vec3d p3 = vectorFromLua(args.arg(4));
                Vec3d p4 = vectorFromLua(args.arg(5));
                if (p1 == null || p2 == null || p3 == null || p4 == null) return LuaValue.FALSE;
                int color = args.arg(6).optint(0xFFFFFFFF);
                boolean depth = args.arg(7).optboolean(false);
                Render3D.drawQuad(p1, p2, p3, p4, color, depth);
                return LuaValue.TRUE;
            }
        });

        return table;
    }

    public static LuaTable keyEvent(KeyEvent event) {
        LuaTable table = new LuaTable();
        table.set("key", LuaValue.valueOf(event.key()));
        table.set("action", LuaValue.valueOf(event.action()));
        table.set("pressed", LuaValue.valueOf(event.action() == 1));
        table.set("released", LuaValue.valueOf(event.action() == 0));
        return table;
    }

    public static LuaTable chatEvent(ChatEvent event) {
        LuaTable table = new LuaTable();
        table.set("message", LuaValue.valueOf(event.getMessage()));
        table.set("cancel", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                event.cancel();
                return LuaValue.NIL;
            }
        });
        return table;
    }

    public static LuaTable entity(Entity entity) {
        LuaTable table = new LuaTable();
        if (entity == null) {
            return table;
        }

        table.set("__entity_id", LuaValue.valueOf(entity.getId()));
        table.set("is_player", LuaValue.valueOf(entity instanceof PlayerEntity));

        table.set("id", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getId());
            }
        });

        table.set("name", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getName().getString());
            }
        });

        table.set("alive", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.isAlive());
            }
        });

        table.set("distance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(mc.player == null ? Double.POSITIVE_INFINITY : mc.player.distanceTo(entity));
            }
        });

        table.set("health", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity instanceof LivingEntity living ? living.getHealth() : 0.0);
            }
        });

        table.set("pos", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d pos = entity.getEntityPos();
                LuaTable position = new LuaTable();
                position.set("x", LuaValue.valueOf(pos.x));
                position.set("y", LuaValue.valueOf(pos.y));
                position.set("z", LuaValue.valueOf(pos.z));
                return position;
            }
        });

        table.set("yaw", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getYaw());
            }
        });

        table.set("pitch", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getPitch());
            }
        });

        table.set("eye_pos", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return vectorTable(entity.getEyePos());
            }
        });

        table.set("velocity", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return vectorTable(entity.getVelocity());
            }
        });

        table.set("bbox", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Box box = entity.getBoundingBox();
                LuaTable result = new LuaTable();
                result.set("min_x", LuaValue.valueOf(box.minX));
                result.set("min_y", LuaValue.valueOf(box.minY));
                result.set("min_z", LuaValue.valueOf(box.minZ));
                result.set("max_x", LuaValue.valueOf(box.maxX));
                result.set("max_y", LuaValue.valueOf(box.maxY));
                result.set("max_z", LuaValue.valueOf(box.maxZ));
                result.set("center", vectorTable(box.getCenter()));
                return result;
            }
        });

        table.set("width", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getWidth());
            }
        });

        table.set("height", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.getHeight());
            }
        });

        table.set("hurt_time", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity instanceof LivingEntity living ? living.hurtTime : 0);
            }
        });

        table.set("on_ground", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.isOnGround());
            }
        });

        return table;
    }

    private static LuaTable rotationControllerTable(LuaModule owner, LuaTable options) {
        LuaRotationController controller = new LuaRotationController(owner, options);
        ROTATION_CONTROLLERS.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(controller);

        LuaTable table = new LuaTable();
        table.set("configure", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.arg(2).istable()) {
                    controller.configure(args.arg(2).checktable());
                }
                return table;
            }
        });
        table.set("look_at", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.lookAt(entityFromLua(args.arg(2))));
            }
        });
        table.set("look_at_pos", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.lookAtPosition(new Vec3d(
                        args.arg(2).checkdouble(),
                        args.arg(3).checkdouble(),
                        args.arg(4).checkdouble()
                )));
            }
        });
        table.set("look_at_angles", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.lookAtAngles(
                        (float) args.arg(2).checkdouble(),
                        (float) args.arg(3).checkdouble()
                ));
            }
        });
        table.set("return_to_view", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.returnToView());
            }
        });
        table.set("release", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                controller.releaseSmooth();
                return LuaValue.NONE;
            }
        });
        table.set("close", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                controller.closeImmediate();
                List<LuaRotationController> controllers = ROTATION_CONTROLLERS.get(owner);
                if (controllers != null) controllers.remove(controller);
                return LuaValue.NONE;
            }
        });
        table.set("phase", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.getPhase());
            }
        });
        table.set("yaw_velocity", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.getYawVelocity());
            }
        });
        table.set("pitch_velocity", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(controller.getPitchVelocity());
            }
        });
        return table;
    }

    private static LuaTable animationTable(LuaAnimationValue animation) {
        LuaTable table = new LuaTable();
        table.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.get());
            }
        });
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                animation.snap(args.arg(2).checkdouble());
                return table;
            }
        });
        table.set("to", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                double target = args.arg(2).checkdouble();
                long duration = Math.max(1L, Math.round(args.arg(3).optdouble(200.0)));
                animation.animateTo(target, duration, curveFromLua(args.arg(4), LuaEasing.named("smoothstep")));
                return table;
            }
        });
        table.set("spring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                animation.springTo(
                        args.arg(2).checkdouble(),
                        args.arg(3).optdouble(180.0),
                        args.arg(4).optdouble(22.0)
                );
                return table;
            }
        });
        table.set("stop", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                animation.stop();
                return table;
            }
        });
        table.set("done", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.isDone());
            }
        });
        table.set("progress", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.progress());
            }
        });
        table.set("velocity", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.getVelocity());
            }
        });
        table.set("target", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.getTarget());
            }
        });
        return table;
    }

    private static LuaEasing.Curve curveFromLua(LuaValue value, LuaEasing.Curve fallback) {
        if (value == null || value.isnil()) return fallback;
        if (value.isstring()) return LuaEasing.named(value.tojstring());
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

    private static LuaTable rotationTable(Rotation rotation) {
        LuaTable table = new LuaTable();
        if (rotation == null) return table;
        table.set("yaw", LuaValue.valueOf(rotation.getYaw()));
        table.set("pitch", LuaValue.valueOf(rotation.getPitch()));
        return table;
    }

    private static LuaTable vectorTable(Vec3d vector) {
        LuaTable table = new LuaTable();
        if (vector == null) return table;
        table.set("x", LuaValue.valueOf(vector.x));
        table.set("y", LuaValue.valueOf(vector.y));
        table.set("z", LuaValue.valueOf(vector.z));
        table.set("length", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(vector.length());
            }
        });
        table.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d other = vectorFromLua(args.arg(2));
                return other == null ? LuaValue.NIL : vectorTable(vector.add(other));
            }
        });
        table.set("sub", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d other = vectorFromLua(args.arg(2));
                return other == null ? LuaValue.NIL : vectorTable(vector.subtract(other));
            }
        });
        table.set("mul", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return vectorTable(vector.multiply(args.arg(2).checkdouble()));
            }
        });
        table.set("normalize", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return vectorTable(vector.lengthSquared() < 1.0e-12 ? Vec3d.ZERO : vector.normalize());
            }
        });
        table.set("distance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Vec3d other = vectorFromLua(args.arg(2));
                return LuaValue.valueOf(other == null ? Double.POSITIVE_INFINITY : vector.distanceTo(other));
            }
        });
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

    private static Vec3d interpolatedPosition(Entity entity, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.lastX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ())
        );
    }

    private static Box interpolatedBox(Entity entity, float tickDelta) {
        Vec3d position = interpolatedPosition(entity, tickDelta);
        return entity.getBoundingBox().offset(
                position.x - entity.getX(),
                position.y - entity.getY(),
                position.z - entity.getZ()
        );
    }

    public static void releaseModuleResources(LuaModule module) {
        if (module == null) return;
        List<LuaRotationController> controllers = ROTATION_CONTROLLERS.remove(module);
        if (controllers == null) return;
        for (LuaRotationController controller : new ArrayList<>(controllers)) {
            try {
                controller.releaseSmooth();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Entity entityFromLua(LuaValue value) {
        if (mc.world == null || !value.istable()) {
            return null;
        }
        LuaValue id = value.get("__entity_id");
        if (!id.isnumber()) {
            return null;
        }
        return mc.world.getEntityById(id.toint());
    }

    private static LuaModule requireActiveModule() {
        LuaModule module = LuaExecutionContext.currentModule();
        if (module == null) {
            throw new LuaError("rotation API can only be used inside a module callback");
        }
        return module;
    }

    private static ModuleCategory parseCategory(String name) {
        String normalized = name == null ? "misc" : name.trim().toLowerCase(Locale.ROOT);
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category == ModuleCategory.LUA || category == ModuleCategory.CONFIG || category == ModuleCategory.THEME) {
                continue;
            }
            if (category.name().equalsIgnoreCase(normalized) || category.getReadableName().equalsIgnoreCase(normalized)) {
                return category;
            }
        }
        throw new LuaError("Unknown module category: " + name);
    }

    private static String[] stringArray(LuaTable table) {
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            LuaValue value = table.get(i);
            if (!value.isnil()) {
                values.add(value.checkjstring());
            }
        }
        return values.toArray(String[]::new);
    }

    private static LuaTable stringTable(List<String> values) {
        LuaTable table = new LuaTable();
        int index = 1;
        for (String value : values) {
            table.set(index++, LuaValue.valueOf(value));
        }
        return table;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
