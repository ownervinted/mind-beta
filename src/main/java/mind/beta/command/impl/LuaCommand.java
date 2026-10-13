package mind.beta.command.impl;

import net.minecraft.util.Formatting;
import mind.beta.Initialization;
import mind.beta.command.Command;
import mind.beta.scripting.LuaScript;
import mind.beta.scripting.ScriptManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class LuaCommand extends Command {
    public LuaCommand() {
        super("lua", "Управление Lua-скриптами", "scripts", "script");
    }

    @Override
    public void execute(String label, String[] args) {
        ScriptManager manager = getManager();
        if (manager == null) {
            logDirect("Lua manager не инициализирован", Formatting.RED);
            return;
        }

        if (args.length == 0) {
            printStatus(manager);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload", "r" -> {
                int scripts = manager.reload();
                int modules = manager.getModuleCount();
                if (manager.getErrors().isEmpty()) {
                    logDirect("Lua перезагружен: " + scripts + " script(s), " + modules + " module(s)", Formatting.GREEN);
                } else {
                    logDirect("Lua перезагружен с ошибками: " + scripts + " script(s), " + modules + " module(s)", Formatting.RED);
                    printErrors(manager.getErrors());
                }
            }
            case "list", "ls" -> printList(manager);
            case "errors" -> printErrors(manager.getErrors());
            case "open", "folder" -> {
                if (manager.openScriptsDirectory()) {
                    logDirect("Открыл папку: " + manager.getScriptsDirectory(), Formatting.GREEN);
                } else {
                    logDirect("Не удалось открыть папку. Путь: " + manager.getScriptsDirectory(), Formatting.RED);
                }
            }
            case "example", "demo" -> {
                try {
                    Path path = manager.createExample();
                    logDirect("Example: " + path.getFileName() + ". Используй .lua reload", Formatting.GREEN);
                } catch (IOException e) {
                    logDirect("Не удалось создать example.lua: " + e.getMessage(), Formatting.RED);
                }
            }
            default -> logDirect("Использование: .lua <reload|list|errors|open|example>", Formatting.RED);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length != 1) {
            return Stream.empty();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("reload", "list", "errors", "open", "example")
                .filter(value -> value.startsWith(prefix));
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Загрузка и hot-reload Lua модулей из config/mind/scripts.",
                "Использование:",
                "> lua reload - перезагрузить все .lua файлы",
                "> lua list - список загруженных скриптов и модулей",
                "> lua errors - последние ошибки загрузки/runtime",
                "> lua open - открыть папку скриптов",
                "> lua example - создать example.lua"
        );
    }

    private void printStatus(ScriptManager manager) {
        logDirect("Lua: " + manager.getScripts().size() + " script(s), " + manager.getModuleCount() + " module(s)");
        logDirect("Папка: " + manager.getScriptsDirectory());
        if (!manager.getErrors().isEmpty()) {
            logDirect("Ошибок: " + manager.getErrors().size() + " (.lua errors)", Formatting.RED);
        }
    }

    private void printList(ScriptManager manager) {
        if (manager.getScripts().isEmpty()) {
            logDirect("Lua-скриптов нет. Используй .lua example или положи .lua в " + manager.getScriptsDirectory());
            return;
        }

        for (LuaScript script : manager.getScripts()) {
            String modules = script.getModules().isEmpty()
                    ? "без модулей"
                    : script.getModules().stream().map(module -> module.getName()).reduce((a, b) -> a + ", " + b).orElse("");
            logDirect(script.getName() + " -> " + modules);
        }
    }

    private void printErrors(List<String> errors) {
        if (errors.isEmpty()) {
            logDirect("Lua ошибок нет", Formatting.GREEN);
            return;
        }
        for (String error : errors) {
            logDirect(error, Formatting.RED);
        }
    }

    private ScriptManager getManager() {
        Initialization initialization = Initialization.getInstance();
        if (initialization == null || initialization.getManager() == null) {
            return null;
        }
        return initialization.getManager().getScriptManager();
    }
}
