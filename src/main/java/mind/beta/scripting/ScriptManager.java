package mind.beta.scripting;

import net.fabricmc.loader.api.FabricLoader;
import mind.beta.modules.module.ModuleRepository;
import mind.beta.scripting.extensions.LuaExtensionRegistry;
import mind.beta.util.config.impl.consolelogger.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class ScriptManager {
    private final ModuleRepository moduleRepository;
    private final Path scriptsDirectory;
    private final List<LuaScript> scripts = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> discoveredScripts = new ArrayList<>();
    private final Map<String, Map<String, ModuleState>> suspendedModuleStates = new HashMap<>();

    public ScriptManager(ModuleRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
        this.scriptsDirectory = FabricLoader.getInstance().getConfigDir().resolve("mind").resolve("scripts");
    }

    public void init() {
        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException e) {
            reportError("Cannot create scripts directory: " + e.getMessage());
            return;
        }
        reload();
    }

    public synchronized int reload() {
        Map<String, ScriptState> previousStates = captureScriptStates();
        unloadAll();
        errors.clear();
        discoveredScripts.clear();

        try {
            Files.createDirectories(scriptsDirectory);
            List<Path> files = scanScriptFiles();
            for (Path file : files) {
                discoveredScripts.add(file.getFileName().toString());
                load(file);
            }
        } catch (IOException e) {
            reportError("Cannot scan scripts directory: " + e.getMessage());
        }

        restoreScriptStates(previousStates);
        Logger.info("Lua: loaded " + scripts.size() + " script(s), " + getModuleCount() + " module(s)");
        return scripts.size();
    }

    public synchronized boolean reloadScript(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        Path path = scriptsDirectory.resolve(fileName).normalize();
        if (!path.getParent().equals(scriptsDirectory.normalize()) || !fileName.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            return false;
        }

        LuaScript existing = getScript(fileName);
        ScriptState previousState = existing == null ? null : captureScriptState(existing);
        if (existing != null) {
            rollbackScript(existing);
            scripts.remove(existing);
        }

        errors.removeIf(error -> error.toLowerCase(Locale.ROOT).startsWith(fileName.toLowerCase(Locale.ROOT) + ":"));

        if (!Files.isRegularFile(path)) {
            refreshDiscoveredScripts();
            reportError(fileName + ": file not found");
            return false;
        }

        load(path);
        refreshDiscoveredScripts();

        LuaScript reloaded = getScript(fileName);
        if (reloaded == null) {
            return false;
        }

        if (previousState != null) {
            restoreScriptState(reloaded, previousState);
        }
        return true;
    }

    private List<Path> scanScriptFiles() throws IOException {
        try (Stream<Path> stream = Files.list(scriptsDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private void refreshDiscoveredScripts() {
        discoveredScripts.clear();
        try {
            Files.createDirectories(scriptsDirectory);
            for (Path file : scanScriptFiles()) {
                discoveredScripts.add(file.getFileName().toString());
            }
        } catch (IOException e) {
            reportError("Cannot scan scripts directory: " + e.getMessage());
        }
    }

    private void load(Path path) {
        LuaScript script = new LuaScript(this, path);
        try {
            script.load();
            scripts.add(script);
            Logger.success("Lua loaded: " + script.getName());
        } catch (Throwable throwable) {
            rollbackScript(script);
            String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            reportError(path.getFileName() + ": " + message);
        }
    }

    public synchronized void registerModule(LuaScript owner, LuaModule module) {
        owner.addModule(module);
        if (owner.isEnabled()) {
            moduleRepository.registerDynamicModule(module);
        }
    }

    private void rollbackScript(LuaScript script) {
        LuaExtensionRegistry.getInstance().unregisterOwner(script);
        List<LuaModule> owned = new ArrayList<>(script.getModules());
        for (LuaModule module : owned) {
            moduleRepository.unregisterDynamicModule(module);
            script.removeModule(module);
        }
        suspendedModuleStates.remove(scriptKey(script));
    }

    public synchronized void unloadAll() {
        List<LuaScript> current = new ArrayList<>(scripts);
        for (LuaScript script : current) {
            rollbackScript(script);
        }
        scripts.clear();
    }

    public synchronized void setScriptEnabled(LuaScript script, boolean enabled) {
        if (script == null || script.isEnabled() == enabled) {
            return;
        }

        String key = scriptKey(script);

        if (!enabled) {
            Map<String, ModuleState> states = new HashMap<>();
            for (LuaModule module : script.getModules()) {
                states.put(moduleKey(module), ModuleState.of(module));
            }
            suspendedModuleStates.put(key, states);

            script.setEnabled(false);
            LuaExtensionRegistry.getInstance().onOwnerStateChanged(script);

            for (LuaModule module : new ArrayList<>(script.getModules())) {
                moduleRepository.unregisterDynamicModule(module);
            }
            return;
        }

        script.setEnabled(true);
        for (LuaModule module : script.getModules()) {
            try {
                moduleRepository.registerDynamicModule(module);
            } catch (IllegalArgumentException exception) {
                reportError(script.getName() + ": cannot enable module '" + module.getName() + "': " + exception.getMessage());
            }
        }

        LuaExtensionRegistry.getInstance().onOwnerStateChanged(script);

        Map<String, ModuleState> states = suspendedModuleStates.remove(key);
        if (states != null) {
            restoreModuleStates(script, states);
        }
    }

    public synchronized void toggleScript(LuaScript script) {
        if (script != null) {
            setScriptEnabled(script, !script.isEnabled());
        }
    }

    public void reportError(String message) {
        if (errors.isEmpty() || !errors.get(errors.size() - 1).equals(message)) {
            errors.add(message);
            if (errors.size() > 100) {
                errors.remove(0);
            }
        }
        Logger.error("[Lua] " + message);
    }

    public List<LuaScript> getScripts() {
        return List.copyOf(scripts);
    }

    public List<String> getDiscoveredScripts() {
        return List.copyOf(discoveredScripts);
    }

    public LuaScript getScript(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (LuaScript script : scripts) {
            if (script.getName().equalsIgnoreCase(fileName)) {
                return script;
            }
        }
        return null;
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public int getModuleCount() {
        return scripts.stream().mapToInt(script -> script.getModules().size()).sum();
    }

    public int getActiveScriptCount() {
        return (int) scripts.stream().filter(LuaScript::isEnabled).count();
    }

    public Path getScriptsDirectory() {
        return scriptsDirectory;
    }

    public boolean openScriptsDirectory() {
        try {
            Files.createDirectories(scriptsDirectory);
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop.getDesktop().open(scriptsDirectory.toFile());
            return true;
        } catch (Exception e) {
            reportError("Cannot open scripts directory: " + e.getMessage());
            return false;
        }
    }

    public Path createExample() throws IOException {
        Files.createDirectories(scriptsDirectory);
        Path target = scriptsDirectory.resolve("example.lua");
        if (Files.exists(target)) {
            return target;
        }
        Files.writeString(target, exampleScript(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        refreshDiscoveredScripts();
        return target;
    }

    private Map<String, ScriptState> captureScriptStates() {
        Map<String, ScriptState> states = new HashMap<>();
        for (LuaScript script : scripts) {
            states.put(scriptKey(script), captureScriptState(script));
        }
        return states;
    }

    private ScriptState captureScriptState(LuaScript script) {
        Map<String, ModuleState> modules = new HashMap<>();
        Map<String, ModuleState> suspended = suspendedModuleStates.get(scriptKey(script));

        for (LuaModule module : script.getModules()) {
            ModuleState state = suspended == null ? null : suspended.get(moduleKey(module));
            modules.put(moduleKey(module), state != null ? state : ModuleState.of(module));
        }

        return new ScriptState(script.isEnabled(), modules);
    }

    private void restoreScriptStates(Map<String, ScriptState> states) {
        for (LuaScript script : scripts) {
            ScriptState state = states.get(scriptKey(script));
            if (state != null) {
                restoreScriptState(script, state);
            }
        }
    }

    private void restoreScriptState(LuaScript script, ScriptState state) {
        if (state.enabled()) {
            restoreModuleStates(script, state.modules());
            return;
        }

        restoreModuleMetadata(script, state.modules());
        setScriptEnabled(script, false);
        suspendedModuleStates.put(scriptKey(script), new HashMap<>(state.modules()));
    }

    private void restoreModuleMetadata(LuaScript script, Map<String, ModuleState> states) {
        for (LuaModule module : script.getModules()) {
            ModuleState state = states.get(moduleKey(module));
            if (state == null) continue;
            module.setKey(state.key());
            module.setFavorite(state.favorite());
        }
    }

    private void restoreModuleStates(LuaScript script, Map<String, ModuleState> states) {
        for (LuaModule module : script.getModules()) {
            ModuleState state = states.get(moduleKey(module));
            if (state == null) {
                continue;
            }
            module.setKey(state.key());
            module.setFavorite(state.favorite());
            module.setState(state.enabled());
        }
    }

    private String scriptKey(LuaScript script) {
        return script.getName().toLowerCase(Locale.ROOT);
    }

    private String moduleKey(LuaModule module) {
        return module.getName().toLowerCase(Locale.ROOT);
    }

    private String exampleScript() {
        return """
                local demo = module.create("Lua Demo", "Misc", "Example module loaded from Lua")

                local range = demo:number("Range", 6.0, 1.0, 32.0)
                local showHud = demo:boolean("Show HUD", true)
                local mode = demo:select("Mode", "Nearest", {"Nearest", "Off"})

                demo:on("enable", function()
                    client.message("Lua Demo enabled")
                end)

                demo:on("tick", function()
                    if mode:get() == "Off" then
                        return
                    end

                    local target = entities.closest_player(range:get())
                    if target ~= nil then
                        rotation.look_at(target, 2, true)
                    end
                end)

                demo:on("render2d", function(ctx)
                    if showHud:get() then
                        ctx:text("Mind Lua API", 10, 10, 8, color.rgb(0, 128, 255))
                    end
                end)

                demo:on("disable", function()
                    rotation.release()
                end)
                """;
    }

    private record ScriptState(boolean enabled, Map<String, ModuleState> modules) {
    }

    private record ModuleState(boolean enabled, int key, boolean favorite) {
        static ModuleState of(LuaModule module) {
            return new ModuleState(module.isState(), module.getKey(), module.isFavorite());
        }
    }
}
