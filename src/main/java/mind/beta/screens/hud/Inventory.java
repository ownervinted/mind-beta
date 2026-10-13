package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.util.animations.Direction;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.item.ItemRender;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Inventory extends AbstractHudElement {

    private static final int SLOT_SIZE = 12;
    private static final int SLOTS_PER_ROW = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final float ITEM_SCALE = 0.5f;
    private static final float RADIUS = 9.5f;
    private static final float HEADER_HEIGHT = 20f;
    private static final float PADDING = 6f;
    private static final float SLOT_GAP = 1f;

    private int filledSlots = 0;

    public Inventory() {
        super("Inventory", 20, 60, 200, 80, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            filledSlots = 0;
            stopAnimation();
            return;
        }

        filledSlots = 0;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                filledSlots++;
            }
        }

        if (filledSlots > 0 || isChat(mc.currentScreen)) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0 || mc.player == null) return;

        float alphaFactor = alpha / 255f;
        float x = getX();
        float y = getY();

        float slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + (SLOTS_PER_ROW - 1) * SLOT_GAP;
        float slotsHeight = INVENTORY_ROWS * SLOT_SIZE + (INVENTORY_ROWS - 1) * SLOT_GAP;
        float width = slotsWidth + PADDING * 2f;
        float height = HEADER_HEIGHT + slotsHeight + PADDING + 2f;

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(height));

        int panelAlpha = Math.max(0, Math.min(255, alpha));

        HudPanelStyle.panel(x, y, width, height, RADIUS, panelAlpha);
        HudPanelStyle.headerDivider(x, y, width, panelAlpha);

        Fonts.MEDIUM.draw(
                "Inventory",
                x + 8f,
                y + 6.5f,
                6f,
                HudPanelStyle.text(panelAlpha)
        );

        String count = String.valueOf(filledSlots);
        float countWidth = Fonts.MEDIUM.getWidth(count, 6f);

        Fonts.MEDIUM.draw(
                count,
                x + width - countWidth - 8f,
                y + 6.5f,
                6f,
                HudPanelStyle.accent(panelAlpha)
        );

        float slotsStartX = x + PADDING;
        float slotsStartY = y + HEADER_HEIGHT + 3f;

        List<CountLabel> countLabels = new ArrayList<>();

        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slotIndex = 9 + row * SLOTS_PER_ROW + col;

                float slotX = slotsStartX + col * (SLOT_SIZE + SLOT_GAP);
                float slotY = slotsStartY + row * (SLOT_SIZE + SLOT_GAP);

                ItemStack stack = mc.player.getInventory().getStack(slotIndex);

                Render2D.rect(
                        slotX,
                        slotY,
                        SLOT_SIZE,
                        SLOT_SIZE,
                        ThemeColors.color(7, 24, 41, Math.round(82f * alphaFactor)).getRGB(),
                        3f
                );

                if (!stack.isEmpty()) {
                    float itemSize = 16f * ITEM_SCALE;
                    float itemX = slotX + (SLOT_SIZE - itemSize) / 2f;
                    float itemY = slotY + (SLOT_SIZE - itemSize) / 2f;

                    if (ItemRender.needsContextRender(stack)) {
                        ItemRender.drawItemWithContext(context, stack, itemX, itemY, ITEM_SCALE, alphaFactor);
                    } else {
                        ItemRender.drawItem(stack, itemX, itemY, ITEM_SCALE, alphaFactor);
                    }

                    if (stack.getCount() > 1) {
                        countLabels.add(new CountLabel(slotX, slotY, stack.getCount()));
                    }
                }
            }
        }

        int textAlpha = Math.max(0, Math.min(255, Math.round(230f * alphaFactor)));
        int textColor = ThemeColors.color(228, 232, 238, textAlpha).getRGB();

        for (CountLabel label : countLabels) {
            String countText = String.valueOf(label.count);
            int textWidth = mc.textRenderer.getWidth(countText);
            int textX = (int) (label.slotX + SLOT_SIZE - textWidth);
            int textY = (int) (label.slotY + SLOT_SIZE - mc.textRenderer.fontHeight + 1);

            context.drawText(mc.textRenderer, countText, textX, textY, textColor, true);
        }
    }

    private record CountLabel(float slotX, float slotY, int count) {}
}
