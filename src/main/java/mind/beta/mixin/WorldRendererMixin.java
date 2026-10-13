package mind.beta.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.render.Ambience;
import mind.beta.modules.impl.render.NoRender;
import mind.beta.util.render.sky.SkyFrameState;
import mind.beta.util.render.sky.SkyRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin implements IMinecraft {

    @Inject(method = "render", at = @At("HEAD"))
    private void mind$captureSkyProjection(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        SkyFrameState.capture(positionMatrix, projectionMatrix);
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void mind$renderCustomSky(
            FrameGraphBuilder frameGraphBuilder,
            Camera camera,
            GpuBufferSlice fogBuffer,
            CallbackInfo ci
    ) {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isState() || !ambience.sky.isCustom()) {
            return;
        }

        SkyRenderer.schedule(
                frameGraphBuilder,
                camera,
                ambience.sky,
                ambience.getCustomTime(),
                ambience.getActiveWeatherIntensity()
        );
        ci.cancel();
    }

    @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    private void onHasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.isState()) return;

        Entity entity = camera.getFocusedEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        boolean hasBlindness = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean hasDarkness = livingEntity.hasStatusEffect(StatusEffects.DARKNESS);

        if (noRender.modeSetting.isSelected("Bad Effects") && hasBlindness && !hasDarkness) {
            cir.setReturnValue(false);
        }

        if (noRender.modeSetting.isSelected("Darkness") && hasDarkness && !hasBlindness) {
            cir.setReturnValue(false);
        }

        if (noRender.modeSetting.isSelected("Bad Effects")
                && noRender.modeSetting.isSelected("Darkness")) {
            cir.setReturnValue(false);
        }
    }
}
