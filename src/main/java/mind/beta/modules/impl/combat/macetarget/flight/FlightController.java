/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.macetarget.flight;

import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.macetarget.prediction.TargetPredictor;
import mind.beta.modules.impl.combat.macetarget.state.MaceState.Stage;

@Getter
public class FlightController {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final TargetPredictor predictor;
    private boolean predictionEnabled = false;
    private float height = 30.0f;

    public FlightController(TargetPredictor predictor) {
        this.predictor = predictor;
    }

    public void setPredictionEnabled(boolean enabled) {
        this.predictionEnabled = enabled;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public Vec3d getTargetPosition(LivingEntity target, Stage stage) {
        if (target == null) {
            return Vec3d.ZERO;
        }

        if (predictionEnabled && predictor.isMoving()) {
            return predictor.getPredictedPosition(target, stage);
        }

        return target.getEyePos();
    }

    public Rotation calculateAngle(LivingEntity target, Stage stage) {
        if (target == null || mc.player == null) {
            return RotationUtils.cameraAngle();
        }

        Vec3d targetPos = getTargetPosition(target, stage);

        switch (stage) {
            case FLYING_UP -> {
                Vec3d flyTarget = targetPos.add(0, height, 0);
                return RotationUtils.fromVec3d(flyTarget.subtract(mc.player.getEyePos()));
            }
            case TARGETTING, ATTACKING -> {
                return RotationUtils.fromVec3d(targetPos.subtract(mc.player.getEyePos()));
            }
            default -> {
                return RotationUtils.cameraAngle();
            }
        }
    }
}