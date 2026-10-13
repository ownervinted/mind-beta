/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Hand;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.HandOffsetEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SliderSettings;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViewModel extends ModuleStructure {

    SliderSettings mainHandXSetting = new SliderSettings("Main Hand X", "Main-hand X offset")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandYSetting = new SliderSettings("Main Hand Y", "Main-hand Y offset")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandZSetting = new SliderSettings("Main Hand Z", "Main-hand Z offset")
            .setValue(0.0F).range(-2.5F, 2.5F);

    SliderSettings offHandXSetting = new SliderSettings("Offhand X", "Offhand X offset")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandYSetting = new SliderSettings("Offhand Y", "Offhand Y offset")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandZSetting = new SliderSettings("Offhand Z", "Offhand Z offset")
            .setValue(0.0F).range(-2.5F, 2.5F);

    public ViewModel() {
        super("ViewModel", "View Model", ModuleCategory.RENDER);
        settings(mainHandXSetting, mainHandYSetting, mainHandZSetting,
                offHandXSetting, offHandYSetting, offHandZSetting);
    }

    @EventHandler
    public void onHandOffset(HandOffsetEvent e) {
        Hand hand = e.getHand();
        if (hand.equals(Hand.MAIN_HAND) && e.getStack().getItem() instanceof CrossbowItem) return;

        MatrixStack matrix = e.getMatrices();

        if (hand.equals(Hand.MAIN_HAND)) {
            matrix.translate(mainHandXSetting.getValue(), mainHandYSetting.getValue(), mainHandZSetting.getValue());
        } else {
            matrix.translate(offHandXSetting.getValue(), offHandYSetting.getValue(), offHandZSetting.getValue());
        }
    }
}