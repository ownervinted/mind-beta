package mind.beta.mixin;

import mind.beta.screens.storage.WorldStorageProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class StorageViewerSilentScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mind$hideScannedStorageScreen(Screen screen, CallbackInfo ci) {
        if (WorldStorageProbe.suppressScreen(screen)) ci.cancel();
    }
}
