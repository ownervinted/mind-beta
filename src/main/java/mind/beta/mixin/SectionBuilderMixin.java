package mind.beta.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import mind.beta.util.render.camera.CameraObstructionFade;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {

    @WrapWithCondition(
            method = "build",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLjava/util/List;)V"
            ),
            require = 0
    )
    private boolean mind$skipCameraObstructionBlock(
            BlockRenderManager blockRenderManager,
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts
    ) {
        return !CameraObstructionFade.shouldHideInChunk(pos);
    }
}
