package mind.beta.util.render.sky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import mind.beta.Initialization;
import mind.beta.modules.impl.render.ambience.SkySettings;

public final class SkyRenderer {

    private SkyRenderer() {
    }

    public static void schedule(
            FrameGraphBuilder frameGraphBuilder,
            Camera camera,
            SkySettings settings,
            long worldTime,
            float rainStrength
    ) {
        if (frameGraphBuilder == null || camera == null || settings == null || !settings.isCustom()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        SkyFrameState.Snapshot matrices = SkyFrameState.snapshot();
        if (matrices == null) return;

        Vec3d pos = camera.getCameraPos();
        SkyRenderState state = new SkyRenderState(
                new Matrix4f(matrices.inverseView()),
                new Matrix4f(matrices.inverseProjection()),
                (float) pos.x,
                (float) pos.y,
                (float) pos.z,
                Math.floorMod(worldTime, 24000L),
                Math.max(0.0F, Math.min(1.0F, rainStrength)),
                settings.getPresetId(),
                settings.getBrightness(),
                settings.getAnimationSpeed(),
                settings.getStarIntensity(),
                settings.getEffectIntensity(),
                settings.getCloudAmount(),
                settings.getCloudQualityId(),
                settings.getShootingStarRate(),
                settings.getShootingStarBrightness()
        );

        FramePass pass = frameGraphBuilder.createPass("mind_custom_sky");
        pass.setRenderer(() -> Initialization.getInstance()
                .getManager()
                .getRenderCore()
                .getSkyPipeline()
                .render(state));
        pass.markToBeVisited();
    }

    public record SkyRenderState(
            Matrix4f inverseView,
            Matrix4f inverseProjection,
            float cameraX,
            float cameraY,
            float cameraZ,
            float worldTime,
            float rainStrength,
            int preset,
            float brightness,
            float animationSpeed,
            float starIntensity,
            float effectIntensity,
            float cloudAmount,
            int cloudQuality,
            float shootingStarRate,
            float shootingStarBrightness
    ) {
    }
}
