/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.misc;

import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.ModuleToggleEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.sounds.SoundManager;

public class ClientSounds extends ModuleStructure {

    public static ClientSounds getInstance() {
        return Instance.get(ClientSounds.class);
    }

    private final SelectSetting soundType = new SelectSetting("Sound Type")
            .value("New", "Old")
            .selected("New");

    private final SliderSettings volume = new SliderSettings("Volume", "Set volume")
            .range(0.1f, 2.0f)
            .setValue(1.0f);

    public ClientSounds() {
        super("ClientSounds", ModuleCategory.MISC);
        settings(soundType, volume);
    }

    @EventHandler
    public void onModuleToggle(ModuleToggleEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getModule() == this) return;

        playToggleSound(event.isEnabled());
    }

    private void playToggleSound(boolean enabled) {
        float vol = volume.getValue();

        if (enabled) {
            if (soundType.isSelected("New")) {
                SoundManager.playSound(SoundManager.ENABLE, vol, 1);
            } else {
                SoundManager.playSound(SoundManager.ON, vol, 1);
            }
        } else {
            if (soundType.isSelected("New")) {
                SoundManager.playSound(SoundManager.DISABLE, vol, 1);
            } else {
                SoundManager.playSound(SoundManager.OFF, vol, 1);
            }
        }
    }
}