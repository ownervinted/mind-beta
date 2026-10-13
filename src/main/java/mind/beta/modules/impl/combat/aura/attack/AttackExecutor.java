/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.attack;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import mind.beta.IMinecraft;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.UsingItemEvent;
import mind.beta.modules.impl.combat.TriggerBot;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.module.setting.implement.SelectSetting;

import java.util.List;

@Getter
public class AttackExecutor implements IMinecraft {
    AttackManager attackHandler = new AttackManager();

    public void tick() {
        attackHandler.tick();
    }

    public void onPacket(PacketEvent e) {
        attackHandler.onPacket(e);
    }

    public void performAttack(AttackPerpetratorConfigurable configurable) {
        attackHandler.handleAttack(configurable);
    }

    public void performTriggerAttack(AttackPerpetratorConfigurable configurable, TriggerBot triggerBot) {
        attackHandler.handleTriggerAttack(configurable, triggerBot);
    }

    public void onUsingItem(UsingItemEvent e) {
        attackHandler.onUsingItem(e);
    }
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class AttackPerpetratorConfigurable {
        LivingEntity target;
        Rotation rotation;
        float maximumRange;
        boolean onlyCritical, shouldBreakShield, shouldUnPressShield, eatAndAttack, multiPoints, ignoreWalls, shieldCheck;
        Box box;
        SelectSetting aimMode;

        public AttackPerpetratorConfigurable(LivingEntity target, Rotation rotation, float maximumRange, List<String> options, SelectSetting aimMode, Box box) {
            this.target = target;
            this.rotation = rotation;
            this.maximumRange = maximumRange;
            this.onlyCritical = options.contains("Only Criticals") || options.contains("Only Critical") || options.contains("Crits with space");
            this.shouldBreakShield = options.contains("Break Shield");
            this.shouldUnPressShield = options.contains("UnPress Shield");
            this.multiPoints = options.contains("Multi Points");
            this.eatAndAttack = options.contains("No Attack When Eat");
            this.ignoreWalls = options.contains("Ignore Walls") || options.contains("Ignore The Walls");
            this.shieldCheck = options.contains("Shield Check");
            this.box = box;
            this.aimMode = aimMode;
        }
    }
}