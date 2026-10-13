/*
 * Copyright (c) Mind beta
 */

package mind.beta.mixin;

import mind.beta.modules.impl.combat.Macros;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MacrosInteractionMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void mind$observeMacroPlacement(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<ActionResult> callback) {
        Macros macros = Macros.getInstance();
        if (macros != null && macros.isState()) {
            macros.observeManualInteraction(hand, hit);
        }
    }
}
