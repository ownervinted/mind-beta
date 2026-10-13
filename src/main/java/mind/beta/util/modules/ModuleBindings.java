package mind.beta.util.modules;

import mind.beta.Initialization;
import mind.beta.modules.module.ModuleRepository;
import mind.beta.modules.module.ModuleStructure;
import org.lwjgl.glfw.GLFW;

public final class ModuleBindings {
    private ModuleBindings() {
    }

    public static void assignUnique(ModuleStructure target, int key) {
        if (target == null) {
            return;
        }
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            Initialization client = Initialization.getInstance();
            if (client != null && client.getManager() != null) {
                ModuleRepository repository = client.getManager().getModuleRepository();
                if (repository != null) {
                    for (ModuleStructure module : repository.modules()) {
                        if (module != target && module.getKey() == key) {
                            module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                        }
                    }
                }
            }
        }
        target.setKey(key);
    }
}
