package mind.beta.mixin;

import mind.beta.screens.storage.WorldStorageCache;
import mind.beta.screens.storage.WorldStorageProbe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class StorageViewerClientMixin {
    @Inject(method = "handleInputEvents", at = @At("TAIL"))
    private void mind$scanUsingVanillaInput(CallbackInfo ci) {
        if (WorldStorageProbe.beginVanillaScan()) {
            ((StorageViewerVanillaUseInvoker) (Object) this).mind$useItem();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mind$updateStorage(CallbackInfo ci) {
        WorldStorageCache.tick();
        WorldStorageProbe.tick();
    }
}
