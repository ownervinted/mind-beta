/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PlayerTravelEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.move.MoveUtil;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Speed extends ModuleStructure {

    SelectSetting mode = new SelectSetting("Mode")
            .value("Vanilla", "Grim", "Polar", "Intave")
            .selected("Grim");

    SliderSettings speed = new SliderSettings("Speed", "Movement speed")
            .range(1.0f, 5.0f)
            .setValue(1.5f)
            .visible(() -> mode.isSelected("Vanilla"));

    public Speed() {
        super("Speed", "Speed", ModuleCategory.MOVEMENT);
        settings(mode, speed);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mode.isSelected("Vanilla")) {
            MoveUtil.setVelocity(speed.getValue() / 3);
        }
    }

    @EventHandler
    public void onMotion(PlayerTravelEvent e) {
        if (mode.isSelected("Polar")) {
            handlePolarMode();
        }
        if (mode.isSelected("Grim") && e.isPre() && MoveUtil.hasPlayerMovement()) {
            handleGrimMode();
        }

        if (mode.isSelected("Intave") && e.isPre() && MoveUtil.hasPlayerMovement()) {
            handleIntaveMode();
        }
    }

    private void handlePolarMode() {
        if (!mc.player.isSwimming() && !mc.player.isGliding() && !mc.player.isSneaking()) {
            if (mc.player.getBoundingBox().maxY - mc.player.getBoundingBox().minY < 1.5f) {
                float motion = mc.player.hasStatusEffect(StatusEffects.SPEED) ? 0.32f : 0.28f;
                MoveUtil.setVelocity(motion);
            }
        }
    }

    private void handleGrimMode() {
        int collisions = 0;

        for (Entity ent : mc.world.getEntities())
            if (ent != mc.player && (!(ent instanceof ArmorStandEntity)) && (ent instanceof LivingEntity || ent instanceof BoatEntity) && mc.player.getBoundingBox().expand(0.5f).intersects(ent.getBoundingBox()))
                collisions++;
        double[] motion = MoveUtil.forward(0.07 * collisions);
        mc.player.addVelocity(motion[0], 0, motion[1]);
    }

    private void handleIntaveMode() {
        int collisions = 0;

        for (Entity ent : mc.world.getEntities())
            if (ent != mc.player && (!(ent instanceof ArmorStandEntity)) && (ent instanceof LivingEntity || ent instanceof BoatEntity) && mc.player.getBoundingBox().expand(0.35f).intersects(ent.getBoundingBox()))
                collisions++;
        double[] motion = MoveUtil.forward(0.0205 * collisions);
        mc.player.addVelocity(motion[0], 0, motion[1]);
    }

    private boolean hasSprintingTarget() {
        return false;
    }
}