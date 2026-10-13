/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.inventory.InventoryUtils;
import mind.beta.util.timer.StopWatch;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChestStealer extends ModuleStructure {
    StopWatch stopWatch = new StopWatch();

    SelectSetting modeSetting = new SelectSetting("Type")
            .value("Polar", "WhiteList", "Default").selected("Polar");

    SliderSettings delaySetting = new SliderSettings("Delay", "Delay between slot clicks")
            .setValue(100).range(0, 1000)
            .visible(() -> modeSetting.isSelected("WhiteList") || modeSetting.isSelected("Default"));

    MultiSelectSetting itemSettings = new MultiSelectSetting("Items", "Select items to take from containers")
            .value("Player Head", "Totem Of Undying", "Elytra", "Netherite Sword",
                    "Netherite Helmet", "Netherite ChestPlate", "Netherite Leggings",
                    "Netherite Boots", "Netherite Ingot", "Netherite Scrap")
            .visible(() -> modeSetting.isSelected("WhiteList"));

    public ChestStealer() {
        super("ChestStealer", "Chest Stealer", ModuleCategory.PLAYER);
        settings(modeSetting, delaySetting, itemSettings);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;

        switch (modeSetting.getSelected()) {
            case "Polar" -> handlePolarMode();
            case "WhiteList", "Default" -> handleDefaultMode();
        }
    }

    private void handlePolarMode() {
        if (mc.currentScreen instanceof GenericContainerScreen sh
                && sh.getTitle().getString().toLowerCase().contains("мистический")
                && !mc.player.getItemCooldownManager().isCoolingDown(Items.GUNPOWDER.getDefaultStack())) {

            sh.getScreenHandler().slots.stream()
                    .filter(s -> s.hasStack()
                            && !s.inventory.equals(mc.player.getInventory())
                            && stopWatch.every(150))
                    .forEach(s -> InventoryUtils.click(s.id, 0, SlotActionType.QUICK_MOVE));
        }
    }

    private void handleDefaultMode() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler sh) {
            sh.slots.forEach(s -> {
                if (s.hasStack()
                        && !s.inventory.equals(mc.player.getInventory())
                        && (modeSetting.isSelected("Default") || whiteList(s.getStack().getItem()))
                        && stopWatch.every(delaySetting.getValue())) {
                    InventoryUtils.click(s.id, 0, SlotActionType.QUICK_MOVE);
                }
            });
        }
    }

    private boolean whiteList(Item item) {
        return itemSettings.getSelected().toString().toLowerCase()
                .contains(item.toString().toLowerCase().replace("_", ""));
    }
}