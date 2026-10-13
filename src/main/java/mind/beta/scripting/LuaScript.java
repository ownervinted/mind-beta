package mind.beta.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import mind.beta.scripting.api.LuaApiFactory;
import mind.beta.scripting.extensions.LuaExtensionRegistry;
import mind.beta.util.config.impl.consolelogger.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LuaScript {
    private final ScriptManager manager;
    private final Path path;
    private final List<LuaModule> modules = new ArrayList<>();
    private Globals globals;
    private boolean enabled = true;

    public LuaScript(ScriptManager manager, Path path) {
        this.manager = manager;
        this.path = path;
    }

    public void load() throws IOException {
        globals = createSandbox();
        LuaApiFactory.install(globals, this, manager);
        installExtensionApis();

        String source = Files.readString(path, StandardCharsets.UTF_8);
        globals.load(new StringReader(source), "@" + path.getFileName()).call();
    }

    private void installExtensionApis() {
        LuaValue auraApi = globals.get("aura");
        if (auraApi.isnil() || !auraApi.istable()) {
            auraApi = LuaExtensionRegistry.getInstance().createAuraApi(this);
            globals.set("aura", auraApi);
        }

        LuaValue maceApi = globals.get("mace");
        if (maceApi.isnil() || !maceApi.istable()) {
            maceApi = LuaExtensionRegistry.getInstance().createMaceApi(this);
            globals.set("mace", maceApi);
        }

        LuaValue mindValue = globals.get("mind");
        if (mindValue.istable()) {
            mindValue.set("aura", auraApi);
            mindValue.set("mace", maceApi);
        }
    }

    private Globals createSandbox() {
        Globals sandbox = JsePlatform.standardGlobals();

        sandbox.set("luajava", LuaValue.NIL);
        sandbox.set("io", LuaValue.NIL);
        sandbox.set("os", LuaValue.NIL);
        sandbox.set("package", LuaValue.NIL);
        sandbox.set("require", LuaValue.NIL);
        sandbox.set("dofile", LuaValue.NIL);
        sandbox.set("loadfile", LuaValue.NIL);
        sandbox.set("debug", LuaValue.NIL);

        sandbox.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) {
                        builder.append(' ');
                    }
                    builder.append(args.arg(i).tojstring());
                }
                Logger.info("[Lua/" + getName() + "] " + builder);
                return LuaValue.NONE;
            }
        });

        return sandbox;
    }

    public void addModule(LuaModule module) {
        modules.add(module);
    }

    public void removeModule(LuaModule module) {
        modules.remove(module);
    }

    public List<LuaModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return path.getFileName().toString();
    }

    public Globals getGlobals() {
        return globals;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    public void reportRuntimeError(LuaModule module, String event, Throwable throwable) {
        String moduleName = module == null ? "<script>" : module.getName();
        String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
        manager.reportError(getName() + " -> " + moduleName + " [" + event + "]: " + message);
    }
}
