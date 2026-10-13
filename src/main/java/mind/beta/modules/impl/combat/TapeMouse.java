/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Hand;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TapeMouse extends ModuleStructure {

    final SelectSetting modeClick = new SelectSetting("Type")
            .value("Left Button", "Right Button")
            .selected("Left Button");

    final SliderSettings delayForClick = new SliderSettings("Delay", "Delay between clicks")
            .range(1.0f, 15.0f).setValue(1.0f);

    final StopWatch delay = new StopWatch();

    public TapeMouse() {
        super("TapeMouse", "Tape Mouse", ModuleCategory.COMBAT);
        settings(modeClick, delayForClick);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        long delayMs = (long) (delayForClick.getValue() * 300.0f);

        if (!delay.finished(delayMs)) return;

        performClick();
        delay.reset();
    }

    private void performClick() {
        if (modeClick.isSelected("Left Button")) {
            leftClick();
        } else {
            rightClick();
        }
    }

    private void leftClick() {
        if (mc.interactionManager == null) return;

        if (mc.targetedEntity != null) {
            mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
            mc.player.swingHand(Hand.MAIN_HAND);
        } else if (mc.crosshairTarget != null) {
            mc.doAttack();
        }
    }

    private void rightClick() {
        if (mc.interactionManager == null) return;
        mc.doItemUse();
    }
}