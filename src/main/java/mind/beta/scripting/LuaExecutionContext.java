package mind.beta.scripting;

public final class LuaExecutionContext {
    private static final ThreadLocal<LuaModule> CURRENT_MODULE = new ThreadLocal<>();

    private LuaExecutionContext() {
    }

    public static LuaModule currentModule() {
        return CURRENT_MODULE.get();
    }

    static LuaModule push(LuaModule module) {
        LuaModule previous = CURRENT_MODULE.get();
        CURRENT_MODULE.set(module);
        return previous;
    }

    static void restore(LuaModule previous) {
        if (previous == null) {
            CURRENT_MODULE.remove();
        } else {
            CURRENT_MODULE.set(previous);
        }
    }
}
