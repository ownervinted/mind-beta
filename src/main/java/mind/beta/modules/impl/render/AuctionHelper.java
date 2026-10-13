/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.Slot;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.HandledScreenEvent;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuctionHelper extends ModuleStructure {

    final BooleanSetting filterThorns = new BooleanSetting("Thorns Filter", "Hide armor enchanted with Thorns")
            .setValue(true);

    final BooleanSetting showPricePerItem = new BooleanSetting("Price Per Item", "Use the price per item")
            .setValue(false);

    Slot firstSlot = null;
    Slot secondSlot = null;
    Slot thirdSlot = null;

    boolean needUpdate = false;
    int updateDelay = 0;

    static final int GREEN_COLOR = 0xFF00FF00;
    static final int ORANGE_COLOR = 0xFFFF8C00;
    static final int RED_COLOR = 0xFFFF3333;

    public AuctionHelper() {
        super("AuctionHelper", "Auction Helper", ModuleCategory.RENDER);
        settings(filterThorns, showPricePerItem);
    }

    @Override
    public void activate() {
        firstSlot = null;
        secondSlot = null;
        thirdSlot = null;
        needUpdate = false;
        updateDelay = 0;
    }

    @Override
    public void deactivate() {
        firstSlot = null;
        secondSlot = null;
        thirdSlot = null;
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket) {
            needUpdate = true;
            updateDelay = 2;
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (updateDelay > 0) {
            updateDelay--;
            return;
        }

        if (needUpdate && mc.currentScreen instanceof GenericContainerScreen screen) {
            updateSlots(screen);
            needUpdate = false;
        }
    }

    private void updateSlots(GenericContainerScreen screen) {
        List<SlotData> validSlots = new ArrayList<>();

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            int price = getPrice(stack);
            if (price <= 0) continue;

            if (filterThorns.isValue() && isArmorItem(stack) && hasThornsEnchantment(stack)) {
                continue;
            }

            int effectivePrice = showPricePerItem.isValue() && stack.getCount() > 1
                    ? price / stack.getCount()
                    : price;

            validSlots.add(new SlotData(slot, effectivePrice));
        }

        validSlots.sort(Comparator.comparingInt(data -> data.price));

        firstSlot = validSlots.size() > 0 ? validSlots.get(0).slot : null;
        secondSlot = validSlots.size() > 1 ? validSlots.get(1).slot : null;
        thirdSlot = validSlots.size() > 2 ? validSlots.get(2).slot : null;
    }

    @EventHandler
    public void onHandledScreen(HandledScreenEvent e) {
        if (mc.player == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        DrawContext context = e.getDrawContext();

        int offsetX = (screen.width - e.getBackgroundWidth()) / 2;
        int offsetY = (screen.height - e.getBackgroundHeight()) / 2;

        highlightSlot(context, firstSlot, offsetX, offsetY, getBlinkingColor(GREEN_COLOR));
        highlightSlot(context, secondSlot, offsetX, offsetY, getBlinkingColor(ORANGE_COLOR));
        highlightSlot(context, thirdSlot, offsetX, offsetY, getBlinkingColor(RED_COLOR));
    }

    private int getBlinkingColor(int color) {
        float alpha = (float) Math.abs(Math.sin(System.currentTimeMillis() / 800.0 * Math.PI));
        alpha = 0.1f + alpha * 0.4f;

        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a < 50) a = 50;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void highlightSlot(DrawContext context, Slot slot, int offsetX, int offsetY, int color) {
        if (slot == null) return;

        int x1 = offsetX + slot.x;
        int y1 = offsetY + slot.y;
        int x2 = x1 + 16;
        int y2 = y1 + 16;

        context.fill(x1, y1, x2, y2, color);
    }

    private record SlotData(Slot slot, int price) {}
    private static final Pattern funTimePricePattern = Pattern.compile("\\$([\\d]+(?:[\\s,][\\d]{3})*(?:\\.[\\d]{2})?)");
    private static final Pattern digitPattern = Pattern.compile("([\\d][\\d\\s,.]*)");

    public static int getPrice(ItemStack stack) {
        String priceStr = null;
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            for (Text line : lore.lines()) {
                String lineStr = line.getString();
                if (lineStr.contains("$") || lineStr.toLowerCase().contains("цена")) {
                    Matcher matcher = funTimePricePattern.matcher(lineStr);
                    if (matcher.find()) {
                        priceStr = matcher.group(1);
                        break;
                    }
                    matcher = digitPattern.matcher(lineStr);
                    if (matcher.find()) {
                        priceStr = matcher.group(1);
                        break;
                    }
                }
            }
        }
        if (priceStr == null || priceStr.isEmpty()) {
            String itemName = stack.getName().getString();
            if (itemName != null) {
                Matcher matcher = funTimePricePattern.matcher(itemName);
                if (matcher.find()) {
                    priceStr = matcher.group(1);
                }
            }
        }
        if (priceStr == null || priceStr.isEmpty()) {
            var components = stack.getComponents();
            if (components != null) {
                String componentString = components.toString();
                if (componentString.contains("$")) {
                    Matcher matcher = funTimePricePattern.matcher(componentString);
                    if (matcher.find()) {
                        priceStr = matcher.group(1);
                    }
                }
            }
        }
        if (priceStr == null || priceStr.isEmpty()) return -1;
        try {
            priceStr = priceStr.replaceAll("[\\s,.$]", "").trim();
            if (priceStr.isEmpty()) return -1;
            return Integer.parseInt(priceStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isArmorItem(ItemStack stack) {
        return stack.getItem() == Items.NETHERITE_HELMET ||
                stack.getItem() == Items.NETHERITE_CHESTPLATE ||
                stack.getItem() == Items.NETHERITE_LEGGINGS ||
                stack.getItem() == Items.NETHERITE_BOOTS ||
                stack.getItem() == Items.DIAMOND_HELMET ||
                stack.getItem() == Items.DIAMOND_CHESTPLATE ||
                stack.getItem() == Items.DIAMOND_LEGGINGS ||
                stack.getItem() == Items.DIAMOND_BOOTS ||
                stack.getItem() == Items.IRON_HELMET ||
                stack.getItem() == Items.IRON_CHESTPLATE ||
                stack.getItem() == Items.IRON_LEGGINGS ||
                stack.getItem() == Items.IRON_BOOTS ||
                stack.getItem() == Items.GOLDEN_HELMET ||
                stack.getItem() == Items.GOLDEN_CHESTPLATE ||
                stack.getItem() == Items.GOLDEN_LEGGINGS ||
                stack.getItem() == Items.GOLDEN_BOOTS ||
                stack.getItem() == Items.CHAINMAIL_HELMET ||
                stack.getItem() == Items.CHAINMAIL_CHESTPLATE ||
                stack.getItem() == Items.CHAINMAIL_LEGGINGS ||
                stack.getItem() == Items.CHAINMAIL_BOOTS ||
                stack.getItem() == Items.LEATHER_HELMET ||
                stack.getItem() == Items.LEATHER_CHESTPLATE ||
                stack.getItem() == Items.LEATHER_LEGGINGS ||
                stack.getItem() == Items.LEATHER_BOOTS ||
                stack.getItem() == Items.TURTLE_HELMET;
    }

    public static boolean hasThornsEnchantment(ItemStack stack) {
        var enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) {
            return false;
        }
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            String enchantId = entry.getIdAsString();
            if (enchantId != null) {
                String lowerEnchantId = enchantId.toLowerCase();
                if (lowerEnchantId.contains("thorns") || lowerEnchantId.contains("шип")) {
                    return true;
                }
            }
        }
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String loreStr = line.getString().toLowerCase();
                if (loreStr.contains("thorns") || loreStr.contains("шип")) {
                    return true;
                }
            }
        }
        return false;
    }
}
