/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.MinecraftClient;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.AttackEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftTap extends ModuleStructure {

    @NonFinal
    long shiftTapEndTime = 0;
    @NonFinal
    boolean isModuleControllingSneak = false;
    @NonFinal
    int shiftTapDuration = 100;

    MinecraftClient mc = MinecraftClient.getInstance();

    public ShiftTap() {
        super("ShiftTap", "Shift Tap", ModuleCategory.COMBAT);
    }

    private void startShiftTap() {
        shiftTapEndTime = System.currentTimeMillis() + 25;
        if (!isModuleControllingSneak) {
            mc.options.sneakKey.setPressed(true);
            isModuleControllingSneak = true;
        }
    }

    private void stopShiftTap() {
        if (isModuleControllingSneak) {
            mc.options.sneakKey.setPressed(false);
            isModuleControllingSneak = false;
        }
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (mc.player == null) {
            return;
        }
        startShiftTap();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.player.isSpectator()) {
            stopShiftTap();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (isModuleControllingSneak && currentTime > shiftTapEndTime) {
            stopShiftTap();
        }
    }
}