package mind.beta.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import mind.beta.screens.storage.StorageItemAlpha;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class StorageViewerGuiRendererMixin {
    @Unique
    private ItemGuiElementRenderState mind$storageCurrentItem;

    @Inject(method = "prepareItem", at = @At("HEAD"), require = 1)
    private void mind$storagePrepareStart(ItemGuiElementRenderState state, float u, float v, int pixelsPerItem, int atlasSide, CallbackInfo ci) {
        mind$storageCurrentItem = state;
    }

    @ModifyArg(
            method = "prepareItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addSimpleElementToCurrentLayer(Lnet/minecraft/client/gui/render/state/TexturedQuadGuiElementRenderState;)V"
            ),
            index = 0,
            require = 1
    )
    private TexturedQuadGuiElementRenderState mind$storageFadeItem(TexturedQuadGuiElementRenderState original) {
        if (mind$storageCurrentItem == null) {
            return original;
        }

        int color = StorageItemAlpha.modulate(mind$storageCurrentItem, original.color());
        if (color == original.color()) {
            return original;
        }

        return new TexturedQuadGuiElementRenderState(
                original.pipeline(),
                original.textureSetup(),
                original.pose(),
                original.x1(),
                original.y1(),
                original.x2(),
                original.y2(),
                original.u1(),
                original.u2(),
                original.v1(),
                original.v2(),
                color,
                original.scissorArea(),
                original.bounds()
        );
    }

    @Inject(method = "prepareItem", at = @At("RETURN"), require = 1)
    private void mind$storagePrepareEnd(ItemGuiElementRenderState state, float u, float v, int pixelsPerItem, int atlasSide, CallbackInfo ci) {
        mind$storageCurrentItem = null;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void mind$storageClear(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        mind$storageCurrentItem = null;
        StorageItemAlpha.clear();
    }
}
