package mind.beta.mixin;

import mind.beta.screens.storage.WorldStorageCache;
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
public abstract class StorageViewerInteractionMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void mind$recordStorageClick(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<ActionResult> cir) {
        WorldStorageCache.recordInteraction(hit.getBlockPos());
    }
}
