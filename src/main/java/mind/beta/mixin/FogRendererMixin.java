package mind.beta.mixin;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import mind.beta.modules.impl.render.Ambience;
import mind.beta.modules.impl.render.ambience.FogSettings;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @ModifyArgs(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void mind$applyCustomFog(
            Args args,
            Camera camera,
            int viewDistance,
            RenderTickCounter renderTickCounter,
            float skyDarkness,
            ClientWorld world
    ) {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isState()) return;

        FogSettings fog = ambience.fog;
        if (fog == null || !fog.isEnabled()) return;

        CameraSubmersionType submersion = camera.getSubmersionType();
        if (submersion != CameraSubmersionType.NONE && submersion != CameraSubmersionType.ATMOSPHERIC) {
            return;
        }

        float density = Math.max(0.10F, fog.getDensity());
        float start = Math.max(0.0F, fog.getStartDistance());
        float transition = Math.max(8.0F, fog.getDistance() / density);
        float end = start + transition;

        Vector4f color = new Vector4f((Vector4f) args.get(2));

        if (fog.colorMode.isSelected("Custom")) {
            int argb = fog.color.getColor();
            float r = ((argb >> 16) & 0xFF) / 255.0F;
            float g = ((argb >> 8) & 0xFF) / 255.0F;
            float b = (argb & 0xFF) / 255.0F;
            float mix = Math.max(0.0F, Math.min(1.0F, fog.getColorStrength()));
            color.lerp(new Vector4f(r, g, b, color.w), mix);
        }

        args.set(2, color);
        args.set(3, start);
        args.set(4, end);
        args.set(5, start * 0.85F);
        args.set(6, end);
        args.set(7, end * 1.12F);
        args.set(8, end * 1.06F);
    }
}
