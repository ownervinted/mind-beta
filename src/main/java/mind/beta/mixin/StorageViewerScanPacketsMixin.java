package mind.beta.mixin;

import mind.beta.screens.storage.WorldStorageProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class StorageViewerScanPacketsMixin {
    @Inject(method = "onOpenScreen", at = @At("TAIL"))
    private void mind$acceptSilentStorage(OpenScreenS2CPacket packet, CallbackInfo ci) {
        WorldStorageProbe.onOpenScreen(packet);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void mind$captureSilentStorage(InventoryS2CPacket packet, CallbackInfo ci) {
        WorldStorageProbe.onInventory(packet);
    }
}
