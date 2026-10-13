/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.attack;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.ItemStack;
import mind.beta.IMinecraft;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttackCooldown implements IMinecraft {

    long lastClickTime = System.currentTimeMillis();

    public boolean isCooldownComplete(int ticks) {
        if (mc.player == null)
            return false;

        float cooldownProgress = mc.player.getAttackCooldownProgress(ticks);
        return cooldownProgress >= 0.95F;
    }

    public boolean isMaceFastAttack() {
        return isHoldingMace() && isCooldownComplete(0);
    }

    public long lastClickPassed() {
        return System.currentTimeMillis() - lastClickTime;
    }

    public void recalculate() {
        lastClickTime = System.currentTimeMillis();
    }

    public boolean isHoldingMace() {
        if (mc.player == null)
            return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        return mainHand.getItem().getTranslationKey().toLowerCase().contains("mace");
    }

    public boolean isWeapon() {
        if (mc.player == null)
            return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty())
            return false;
        String itemName = mainHand.getItem().getTranslationKey().toLowerCase();
        return itemName.contains("sword") || itemName.contains("axe") || itemName.contains("trident");
    }
}