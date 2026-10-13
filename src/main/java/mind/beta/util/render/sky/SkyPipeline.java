package mind.beta.util.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class SkyPipeline implements AutoCloseable {

    private static final Identifier PIPELINE_ID = Identifier.of("mind", "pipeline/sky");
    private static final Identifier SKY_SHADER = Identifier.of("mind", "core/sky");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SKY_SHADER)
                    .withFragmentShader(SKY_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 256;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    public void render(SkyRenderer.SkyRenderState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state == null || client.getFramebuffer() == null) return;

        ensureInitialized();
        prepareUniformData(client, state);
        uploadAndDraw(client);
    }

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0).flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "mind:sky_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);
        initialized = true;
    }

    private void prepareUniformData(MinecraftClient client, SkyRenderer.SkyRenderState state) {
        float width = client.getWindow().getFramebufferWidth();
        float height = client.getWindow().getFramebufferHeight();
        float time = (System.nanoTime() / 1_000_000_000.0F) * state.animationSpeed();

        dataBuffer.clear();

        putVec4(width, height, time, state.preset());
        putVec4(state.worldTime(), state.rainStrength(), state.brightness(), state.starIntensity());
        putVec4(state.effectIntensity(), state.cloudAmount(), state.cloudQuality(), state.shootingStarRate());
        putVec4(state.shootingStarBrightness(), state.cameraX(), state.cameraY(), state.cameraZ());
        putMatrix(state.inverseView());
        putMatrix(state.inverseProjection());

        dataBuffer.flip();
    }

    private void putVec4(float x, float y, float z, float w) {
        dataBuffer.putFloat(x);
        dataBuffer.putFloat(y);
        dataBuffer.putFloat(z);
        dataBuffer.putFloat(w);
    }

    private void putMatrix(Matrix4f matrix) {
        dataBuffer.putFloat(matrix.m00());
        dataBuffer.putFloat(matrix.m01());
        dataBuffer.putFloat(matrix.m02());
        dataBuffer.putFloat(matrix.m03());
        dataBuffer.putFloat(matrix.m10());
        dataBuffer.putFloat(matrix.m11());
        dataBuffer.putFloat(matrix.m12());
        dataBuffer.putFloat(matrix.m13());
        dataBuffer.putFloat(matrix.m20());
        dataBuffer.putFloat(matrix.m21());
        dataBuffer.putFloat(matrix.m22());
        dataBuffer.putFloat(matrix.m23());
        dataBuffer.putFloat(matrix.m30());
        dataBuffer.putFloat(matrix.m31());
        dataBuffer.putFloat(matrix.m32());
        dataBuffer.putFloat(matrix.m33());
    }

    private void uploadAndDraw(MinecraftClient client) {
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "mind:sky_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(),
                COLOR_MODULATOR,
                MODEL_OFFSET,
                TEXTURE_MATRIX
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "mind:sky_pass",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("SkyData", uniformBuffer);
            renderPass.draw(0, 6);
        }
    }

    @Override
    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}
