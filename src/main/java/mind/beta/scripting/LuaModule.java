package mind.beta.scripting;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.AttackEvent;
import mind.beta.events.impl.ChatEvent;
import mind.beta.events.impl.DrawEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.scripting.api.LuaApiFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LuaModule extends ModuleStructure {
    private final LuaScript owner;
    private final Map<String, LuaValue> callbacks = new HashMap<>();

    public LuaModule(LuaScript owner, String name, String description, ModuleCategory category) {
        super(name, description, category);
        this.owner = owner;
    }

    public LuaScript getOwner() {
        return owner;
    }

    public void on(String eventName, LuaValue callback) {
        if (eventName == null || callback == null || !callback.isfunction()) {
            throw new LuaError("module:on(event, callback) requires a function");
        }
        callbacks.put(normalizeEventName(eventName), callback);
    }

    public boolean hasCallback(String eventName) {
        return callbacks.containsKey(normalizeEventName(eventName));
    }

    @Override
    public void activate() {
        invoke("enable");
    }

    @Override
    public void deactivate() {
        invoke("disable");
        LuaApiFactory.releaseModuleResources(this);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        invoke("tick");
    }

    @EventHandler
    public void onRender2D(DrawEvent event) {
        LuaValue callback = callbacks.get("render2d");
        if (callback == null) {
            return;
        }
        invokeFunction("render2d", callback, LuaApiFactory.renderContext(event));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        LuaValue callback = callbacks.get("render3d");
        if (callback == null) {
            return;
        }
        invokeFunction("render3d", callback, LuaApiFactory.worldRenderContext(event));
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        LuaValue callback = callbacks.get("attack");
        if (callback == null) {
            return;
        }
        invokeFunction("attack", callback, LuaApiFactory.entity(event.getTarget()));
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        LuaValue callback = callbacks.get("key");
        if (callback == null) {
            return;
        }
        invokeFunction("key", callback, LuaApiFactory.keyEvent(event));
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        LuaValue callback = callbacks.get("chat");
        if (callback == null) {
            return;
        }
        invokeFunction("chat", callback, LuaApiFactory.chatEvent(event));
    }

    public LuaValue invoke(String eventName, LuaValue... args) {
        LuaValue callback = callbacks.get(normalizeEventName(eventName));
        if (callback == null) {
            return LuaValue.NIL;
        }
        return invokeFunction(eventName, callback, args);
    }

    private LuaValue invokeFunction(String eventName, LuaValue callback, LuaValue... args) {
        LuaModule previous = LuaExecutionContext.push(this);
        try {
            Varargs result = callback.invoke(LuaValue.varargsOf(args));
            return result.arg1();
        } catch (LuaError error) {
            owner.reportRuntimeError(this, eventName, error);
            return LuaValue.NIL;
        } catch (Throwable throwable) {
            owner.reportRuntimeError(this, eventName, throwable);
            return LuaValue.NIL;
        } finally {
            LuaExecutionContext.restore(previous);
        }
    }

    private String normalizeEventName(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "render", "draw", "render_2d", "draw2d" -> "render2d";
            case "world_render", "render_3d", "worldrender" -> "render3d";
            case "on_enable", "activate" -> "enable";
            case "on_disable", "deactivate" -> "disable";
            default -> normalized;
        };
    }
}
