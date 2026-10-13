
/**
 * Copyright (c) Mind beta
 */
package mind.beta.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.HandAnimationEvent;
import mind.beta.events.impl.SwingDurationEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;

public class SwingAnimation extends ModuleStructure {

    private final SliderSettings hitStrengthSetting =
            new SliderSettings("Swing Strength", "Swing animation intensity")
                    .range(0.5F, 3.0F)
                    .setValue(1.0F);

    private final SliderSettings swingSpeedSetting =
            new SliderSettings("Swing Duration", "Attack animation duration")
                    .range(0.5F, 4.0F)
                    .setValue(1.0F);

    private final BooleanSetting onlySwing =
            new BooleanSetting("Only While Swinging", "Show the animation only while swinging")
                    .setValue(false);

    private final BooleanSetting onlyAura =
            new BooleanSetting("Only While Aura Is Enabled", "Show the animation only while Aura is enabled")
                    .setValue(false);

    public SwingAnimation() {
        super("SwingAnimation", "Swing Animation", ModuleCategory.RENDER);
        settings(hitStrengthSetting, swingSpeedSetting, onlySwing, onlyAura);
    }

    private boolean canAnimate() {
        if (!onlyAura.isValue()) {
            return true;
        }

        Aura aura = Aura.getInstance();
        return aura != null && aura.isState() && aura.target != null;
    }

    @EventHandler
    public void onSwingDuration(SwingDurationEvent e) {
        if (!canAnimate()) {
            return;
        }

        e.setAnimation(swingSpeedSetting.getValue());
        e.cancel();
    }

    @EventHandler
    public void onHandAnimation(HandAnimationEvent e) {
        if (mc.player == null || e.getHand() != Hand.MAIN_HAND) {
            return;
        }

        if (!canAnimate()) {
            return;
        }

        MatrixStack matrix = e.getMatrices();

        float progress = MathHelper.clamp(e.getSwingProgress(), 0.0F, 1.0F);
        float strength = hitStrengthSetting.getValue();

        int side = mc.player.getMainArm() == Arm.RIGHT ? 1 : -1;

        if (onlySwing.isValue() && mc.player.handSwingTicks == 0) {
            matrix.translate(side * 0.56F, -0.52F, -0.72F);
            e.cancel();
            return;
        }

        float swing = MathHelper.sin(
                MathHelper.sqrt(progress) * (float) Math.PI
        );

        float swingSquared = MathHelper.sin(
                progress * progress * (float) Math.PI
        );

        matrix.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        matrix.multiply(
                RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * side)
        );

        matrix.multiply(
                RotationAxis.POSITIVE_Y.rotationDegrees(
                        swingSquared * -20.0F * side * strength
                )
        );

        matrix.multiply(
                RotationAxis.POSITIVE_Z.rotationDegrees(
                        swing * -20.0F * side * strength
                )
        );

        matrix.multiply(
                RotationAxis.POSITIVE_X.rotationDegrees(
                        swing * -80.0F * strength
                )
        );

        matrix.multiply(
                RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F * side)
        );

        e.cancel();
    }
}
