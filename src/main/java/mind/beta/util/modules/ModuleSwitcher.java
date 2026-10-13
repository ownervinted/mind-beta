package mind.beta.util.modules;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.IMinecraft;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.EventManager;
import mind.beta.events.impl.KeyEvent;
import mind.beta.modules.module.ModuleStructure;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleSwitcher implements IMinecraft {
    List<ModuleStructure> moduleStructures;

    public ModuleSwitcher(List<ModuleStructure> moduleStructures, EventManager eventManager) {
        this.moduleStructures = moduleStructures;
        eventManager.register(this);
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        for (ModuleStructure moduleStructure : moduleStructures) {
            if (event.key() == moduleStructure.getKey() && mc.currentScreen == null) {
                try {
                    handleModuleState(moduleStructure, event.action());
                } catch (Exception e) {

                }
            }
        }
    }

    private void handleModuleState(ModuleStructure moduleStructure, int action) {
        if (moduleStructure.getType() == 1 && action == 1) {
            moduleStructure.switchState();
        }
    }










}
