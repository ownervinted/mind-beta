/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.AttackEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.sounds.SoundManager;

import java.util.concurrent.ThreadLocalRandom;

public class HitSound extends ModuleStructure {

    public static HitSound getInstance() {
        return Instance.get(HitSound.class);
    }

    private final SelectSetting soundType = new SelectSetting("Sound")
            .value("Moan", "Gamesense", "Crime", "Amethyst", "Soft")
            .selected("Soft");

    private final SliderSettings volume = new SliderSettings("Volume", "Adjust hit sound volume")
            .range(0.1f, 6.0f)
            .setValue(1.0f);

    public HitSound() {
        super("HitSound", ModuleCategory.COMBAT);
        settings(soundType, volume);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        playSelectedSound();
    }

    private void playSelectedSound() {
        float vol = volume.getValue();

        if (soundType.isSelected("Moan")) {
            playRandomMoan(vol, 1.0f);
        }

        if (soundType.isSelected("Metallic")) {
            SoundManager.playSound(SoundManager.METALLIC, vol, 1.0f);
        }

        if (soundType.isSelected("Crime")) {
            SoundManager.playSound(SoundManager.CRIME, vol, 1.0f);
        }

        if (soundType.isSelected("Amethyst")) {
            playRandomAmethyst(vol);
        }
        if(soundType.isSelected("Soft")) {
            SoundManager.playSound(SoundManager.SOFT, vol, 1.0f);
        }
    }

    private void playRandomMoan(float volume, float pitch) {
        int random = ThreadLocalRandom.current().nextInt(4);

        switch (random) {
            case 0 -> SoundManager.playSound(SoundManager.MOAN1, volume, pitch);
            case 1 -> SoundManager.playSound(SoundManager.MOAN2, volume, pitch);
            case 2 -> SoundManager.playSound(SoundManager.MOAN3, volume, pitch);
            case 3 -> SoundManager.playSound(SoundManager.MOAN4, volume, pitch);
        }
    }

    private void playRandomAmethyst(float volume) {
        float boostedVolume = volume * 2.5f;
        float pitch = randomPitch(0.95f, 1.12f);

        int random = ThreadLocalRandom.current().nextInt(3);

        switch (random) {
            case 0 -> mc.player.playSound(
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    boostedVolume,
                    pitch
            );

            case 1 -> mc.player.playSound(
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    boostedVolume,
                    pitch
            );

            case 2 -> mc.player.playSound(
                    SoundEvents.BLOCK_SMALL_AMETHYST_BUD_BREAK,
                    boostedVolume,
                    pitch
            );
        }

        mc.player.playSound(
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                boostedVolume * 0.65f,
                pitch + 0.08f
        );
    }

    private float randomPitch(float min, float max) {
        return ThreadLocalRandom.current().nextFloat(min, max);
    }
}
