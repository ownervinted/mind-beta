/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.impl.GameLeftEvent;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.screens.clickgui.impl.settingsrender.BindComponent;
import mind.beta.util.Instance;
import net.minecraft.util.Hand;
import mind.beta.util.string.chat.ChatMessage;

public class AutoSpear extends ModuleStructure {
    private final BindSetting lungeKey = new BindSetting(
            "Lunge Key",
            "Select a Lunge spear from the hotbar, jab, and restore the previous item"
    );

    private static final long ACTIVATION_COOLDOWN_NS = 350_000_000L;
    private long nextActivationAt;
    private boolean active;
    private boolean requested;
    private ClientPlayerEntity sequencePlayer;
    private int previousSlot = -1;
    private int spearSlot = -1;
    private int jabAge;

    public AutoSpear() {
        super("AutoSpear", "Lunge with a spear using a dedicated key", ModuleCategory.COMBAT);
        settings(lungeKey);
    }

    public static AutoSpear getInstance() {
        return Instance.get(AutoSpear.class);
    }

    public boolean isSequenceActive() {
        return isState() && (active || requested);
    }

    @Override
    public void activate() {
        active = false;
        requested = false;
    }

    @Override
    public void deactivate() {
        finish(true);
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (!isState() || event.action() != GLFW.GLFW_PRESS
                || mc.currentScreen != null || !matchesBind(event) || active || requested || System.nanoTime() < nextActivationAt) {
            return;
        }
        requested = true;
    }

    private boolean matchesBind(KeyEvent event) {
        int key = lungeKey.getKey();
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        if (lungeKey.getType() == 2) {
            return key == BindComponent.MIDDLE_MOUSE_BIND
                    && event.type() == InputUtil.Type.MOUSE
                    && event.key() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        }
        if (lungeKey.getType() == 0) {
            return event.type() == InputUtil.Type.MOUSE && event.key() == key;
        }
        return lungeKey.getType() == 1
                && event.type() == InputUtil.Type.KEYSYM && event.key() == key;
    }

    private void start() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || !canLunge(true)) {
            return;
        }
        MaceHelper helper = MaceHelper.getInstance();
        if (helper != null && helper.isThrowSequenceActive()) {
            ChatMessage.brandmessage("AutoSpear: wait for MaceHelper to finish.");
            return;
        }
        int spearSlot = findSpear();
        if (spearSlot < 0) {
            ChatMessage.brandmessage("AutoSpear: no spear with Lunge in the hotbar.");
            return;
        }
        Aura aura = Aura.getInstance();
        if (aura != null && aura.isState()) {
            Initialization.getInstance().getManager().getAttackPerpetrator()
                    .getAttackHandler().resetPendingStateForItemUse();
            RotationManager.INSTANCE.releaseControl(aura);
        }
        sequencePlayer = mc.player;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        this.spearSlot = spearSlot;
        nextActivationAt = System.nanoTime() + ACTIVATION_COOLDOWN_NS;
        active = true;
        try {
            mc.player.getInventory().setSelectedSlot(spearSlot);
            var weapon = mc.player.getMainHandStack().get(DataComponentTypes.PIERCING_WEAPON);
            if (weapon == null) {
                finish(true);
                return;
            }
            mc.interactionManager.attackWithPiercingWeapon(weapon);
            mc.player.swingHand(Hand.MAIN_HAND);
            jabAge = mc.player.age;
        } catch (RuntimeException exception) {
            finish(true);
            throw exception;
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (requested) {
            requested = false;
            if (isState() && mc.currentScreen == null && System.nanoTime() >= nextActivationAt) {
                start();
            }
            return;
        }
        if (!active) {
            return;
        }
        if (mc.player == null || mc.player != sequencePlayer || mc.world == null) {
            finish(false);
            return;
        }
        if (mc.player.getInventory().getSelectedSlot() != spearSlot) {
            finish(false);
            return;
        }
        if (mc.player.age > jabAge || mc.currentScreen != null || !mc.player.isAlive()) {
            finish(true);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        finish(false);
    }

    private void finish(boolean restore) {
        if (restore && mc.player != null && mc.player == sequencePlayer
                && previousSlot >= 0 && previousSlot < 9
                && mc.player.getInventory().getSelectedSlot() == spearSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        active = false;
        requested = false;
        sequencePlayer = null;
        previousSlot = -1;
        spearSlot = -1;
    }

    private boolean canLunge(boolean report) {
        String reason = null;
        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            reason = "player cannot attack.";
        } else if (mc.player.isUsingItem()) {
            reason = "finish using the current item first.";
        } else if (mc.player.isTouchingWater()) {
            reason = "Lunge does not work in water.";
        } else if (mc.player.isGliding()) {
            reason = "Lunge does not work while gliding.";
        } else if (!mc.player.getAbilities().creativeMode
                && mc.player.getHungerManager().getFoodLevel() < 6) {
            reason = "Lunge requires at least 6 hunger points.";
        }
        if (reason != null && report) {
            ChatMessage.brandmessage("AutoSpear: " + reason);
        }
        return reason == null;
    }

    private int findSpear() {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (lungeLevel(mc.player.getInventory().getStack(selected)) > 0) {
            return selected;
        }
        int bestSlot = -1;
        int bestLevel = 0;
        for (int slot = 0; slot < 9; slot++) {
            int level = lungeLevel(mc.player.getInventory().getStack(slot));
            if (level > bestLevel) {
                bestSlot = slot;
                bestLevel = level;
            }
        }
        return bestSlot;
    }

    private int lungeLevel(ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponentTypes.PIERCING_WEAPON) == null) {
            return 0;
        }
        var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null) {
            return 0;
        }
        for (var enchantment : enchantments.getEnchantments()) {
            if ("minecraft:lunge".equals(enchantment.getIdAsString())) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

}
