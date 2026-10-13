package mind.beta.mixin;

import mind.beta.screens.storage.StorageItemAlpha;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class StorageViewerGuiStateMixin {
    @Inject(method = "addItem", at = @At("HEAD"))
    private void mind$captureStorageItem(ItemGuiElementRenderState state, CallbackInfo ci) {
        StorageItemAlpha.capture(state);
    }
}
