/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.KeyEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.sounds.SoundManager;

public class ElytraTarget extends ModuleStructure {

    public static ElytraTarget getInstance() {
        return Instance.get(ElytraTarget.class);
    }

    public SliderSettings elytraFindRange = new SliderSettings("Aim Range", "Target search range while flying with an elytra")
            .setValue(32).range(6F, 64F);

    public SliderSettings elytraForward = new SliderSettings("Forward Lead", "Forward distance used to lead the target")
            .setValue(3).range(0F, 6F);

    final BindSetting forward = new BindSetting("Toggle Forward Lead Key", "");

    public static boolean shouldElytraTarget = false;

    public ElytraTarget() {
        super("ElytraTarget", "Elytra Target", ModuleCategory.MOVEMENT);
        settings(elytraFindRange, elytraForward, forward);
    }

    @EventHandler
    private void onEventKey(KeyEvent e) {
        if (e.isKeyDown(forward.getKey())) {
            shouldElytraTarget = !shouldElytraTarget;
            SoundManager.playSound(shouldElytraTarget ? SoundManager.MODULE_ENABLE : SoundManager.MODULE_DISABLE, 1, 1.0f);
        }
    }
}