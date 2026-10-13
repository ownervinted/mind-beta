/* Copyright Mind */
package mind.beta.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import mind.beta.events.api.EventManager;
import mind.beta.events.impl.InputEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.movement.Scaffold;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationPlan;

import static mind.beta.IMinecraft.mc;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput tickHook(PlayerInput original) {
        InputEvent event = new InputEvent(original);
        EventManager.callEvent(event);
        PlayerInput input = event.getInput();

        if (Aura.shouldBlockJumpAfterDeactivate()) {
            input = new PlayerInput(
                    input.forward(), input.backward(), input.left(), input.right(),
                    false, input.sneak(), input.sprint()
            );
        }

        return AttackManager.applySprintInput(transformInput(input));
    }

    @Unique
    private PlayerInput transformInput(PlayerInput input) {
        Aura aura = Aura.getInstance();
        Scaffold scaffold = Scaffold.getInstance();
        boolean scaffoldActive = scaffold != null && scaffold.isState();
        if (!scaffoldActive && aura != null && aura.isState() && aura.isFoodUseSuspended()) {
            return input;
        }

        RotationManager rotationController = RotationManager.INSTANCE;
        Rotation rotation = rotationController.getEffectiveRotation();
        RotationPlan configurable = rotationController.getCurrentRotationPlan();

        if (mc.player == null || rotation == null || configurable == null ||
                !(configurable.isMoveCorrection() && configurable.isFreeCorrection())) {
            return input;
        }

        float deltaYaw = mc.player.getYaw() - rotation.getYaw();
        float z = KeyboardInput.getMovementMultiplier(input.forward(), input.backward());
        float x = KeyboardInput.getMovementMultiplier(input.left(), input.right());
        float newX = x * MathHelper.cos(deltaYaw * 0.017453292f) - z * MathHelper.sin(deltaYaw * 0.017453292f);
        float newZ = z * MathHelper.cos(deltaYaw * 0.017453292f) + x * MathHelper.sin(deltaYaw * 0.017453292f);
        int movementSideways = Math.round(newX);
        int movementForward = Math.round(newZ);

        return new PlayerInput(
                movementForward > 0F, movementForward < 0F,
                movementSideways > 0F, movementSideways < 0F,
                input.jump(), input.sneak(), input.sprint()
        );
    }
}
