package mind.beta.util.render.camera;

import mind.beta.modules.impl.render.CameraSettings;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraObstructionFade {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Set<BlockPos> HIDDEN_IN_CHUNK = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, FadeState> STATES = new HashMap<>();

    private static long lastFrameNanos = 0L;

    private static final long HIDE_SETTLE_MS = 90L;
    private static final long RESTORE_OVERLAP_MS = 180L;

    private static final float FADE_IN_SPEED = 4.25F;
    private static final float FADE_OUT_SPEED = 5.25F;

    private CameraObstructionFade() {
    }

    public static boolean shouldHideInChunk(BlockPos pos) {
        return HIDDEN_IN_CHUNK.contains(pos);
    }

    public static void updateAndRender(
            Camera camera
    ) {
        if (mc.world == null || mc.player == null) {
            clearImmediately();
            return;
        }

        long now = System.currentTimeMillis();
        long frameNow = System.nanoTime();

        float frameSeconds;

        if (lastFrameNanos == 0L) {
            frameSeconds = 1.0F / 60.0F;
        } else {
            frameSeconds = (frameNow - lastFrameNanos) / 1_000_000_000.0F;
            frameSeconds = MathHelper.clamp(
                    frameSeconds,
                    1.0F / 240.0F,
                    1.0F / 15.0F
            );
        }

        lastFrameNanos = frameNow;

        Set<BlockPos> desired = CameraSettings.shouldUseTransparentBlocks()
                ? collectObstructions(camera, mc.player)
                : Set.of();

        for (FadeState state : STATES.values()) {
            state.desired = false;
        }

        for (BlockPos pos : desired) {
            FadeState state = STATES.computeIfAbsent(
                    pos,
                    ignored -> new FadeState(pos)
            );

            state.desired = true;

            if (!HIDDEN_IN_CHUNK.contains(pos)) {
                HIDDEN_IN_CHUNK.add(pos);
                scheduleRebuild(pos);
                state.hideReadyAt = now + HIDE_SETTLE_MS;
            }

            state.releaseScheduled = false;
            state.releaseAt = 0L;
        }

        float targetAlpha = MathHelper.clamp(
                CameraSettings.getTransparentBlockAlpha(),
                0.03F,
                0.95F
        );

        Iterator<Map.Entry<BlockPos, FadeState>> iterator =
                STATES.entrySet().iterator();

        while (iterator.hasNext()) {
            FadeState state = iterator.next().getValue();

            if (state.desired) {
                if (now >= state.hideReadyAt) {
                    state.alpha = approach(
                            state.alpha,
                            targetAlpha,
                            FADE_IN_SPEED * frameSeconds
                    );
                } else {
                    state.alpha = 1.0F;
                }
            } else {
                state.alpha = approach(
                        state.alpha,
                        1.0F,
                        FADE_OUT_SPEED * frameSeconds
                );

                if (state.alpha >= 0.995F
                        && HIDDEN_IN_CHUNK.contains(state.pos)
                        && !state.releaseScheduled) {
                    HIDDEN_IN_CHUNK.remove(state.pos);
                    scheduleRebuild(state.pos);
                    state.releaseScheduled = true;
                    state.releaseAt = now + RESTORE_OVERLAP_MS;
                    state.alpha = 1.0F;
                }

                if (state.releaseScheduled
                        && now >= state.releaseAt
                        && !HIDDEN_IN_CHUNK.contains(state.pos)) {
                    iterator.remove();
                }
            }
        }

        renderHiddenBlocks(camera);
    }

    private static Set<BlockPos> collectObstructions(
            Camera camera,
            PlayerEntity player
    ) {
        Set<BlockPos> result = new HashSet<>();

        Vec3d cameraPos = camera.getCameraPos();
        Box playerBox = player.getBoundingBox();

        Vec3d center = playerBox.getCenter();
        double height = playerBox.getLengthY();
        double width = Math.max(
                playerBox.getLengthX(),
                playerBox.getLengthZ()
        );

        Vec3d toPlayer = center.subtract(cameraPos);

        if (toPlayer.lengthSquared() < 0.05) {
            return result;
        }

        Vec3d horizontal = new Vec3d(
                toPlayer.x,
                0.0,
                toPlayer.z
        );

        Vec3d right;

        if (horizontal.lengthSquared() < 1.0E-5) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            horizontal = horizontal.normalize();
            right = new Vec3d(
                    -horizontal.z,
                    0.0,
                    horizontal.x
            );
        }

        double shoulderOffset = Math.max(0.18, width * 0.43);

        List<Vec3d> targets = new ArrayList<>(7);

        targets.add(new Vec3d(
                center.x,
                playerBox.minY + height * 0.52,
                center.z
        ));

        targets.add(new Vec3d(
                center.x,
                playerBox.minY + height * 0.88,
                center.z
        ));

        targets.add(new Vec3d(
                center.x,
                playerBox.minY + height * 0.20,
                center.z
        ));

        Vec3d shoulders = new Vec3d(
                center.x,
                playerBox.minY + height * 0.70,
                center.z
        );

        targets.add(shoulders.add(right.multiply(shoulderOffset)));
        targets.add(shoulders.subtract(right.multiply(shoulderOffset)));

        Vec3d torso = new Vec3d(
                center.x,
                playerBox.minY + height * 0.46,
                center.z
        );

        targets.add(torso.add(right.multiply(shoulderOffset * 0.72)));
        targets.add(torso.subtract(right.multiply(shoulderOffset * 0.72)));

        float radius = MathHelper.clamp(
                CameraSettings.getTransparentBlockRadius(),
                0.05F,
                1.25F
        );

        for (Vec3d target : targets) {
            collectAlongSegment(
                    cameraPos,
                    target,
                    playerBox,
                    radius,
                    result
            );
        }

        return result;
    }

    private static void collectAlongSegment(
            Vec3d start,
            Vec3d end,
            Box playerBox,
            float radius,
            Set<BlockPos> output
    ) {
        Vec3d delta = end.subtract(start);
        double distance = delta.length();

        if (distance < 0.05) {
            return;
        }

        int steps = Math.max(
                1,
                (int) Math.ceil(distance / 0.12)
        );

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d sample = start.add(delta.multiply(t));

            int minX = MathHelper.floor(sample.x - radius);
            int minY = MathHelper.floor(sample.y - radius);
            int minZ = MathHelper.floor(sample.z - radius);
            int maxX = MathHelper.floor(sample.x + radius);
            int maxY = MathHelper.floor(sample.y + radius);
            int maxZ = MathHelper.floor(sample.z + radius);

            for (BlockPos mutable : BlockPos.iterate(
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ
            )) {
                BlockPos pos = mutable.toImmutable();

                if (output.contains(pos)) {
                    continue;
                }

                if (!isFadeCandidate(pos, playerBox)) {
                    continue;
                }

                Box blockBox = new Box(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        pos.getX() + 1.0,
                        pos.getY() + 1.0,
                        pos.getZ() + 1.0
                );

                if (distanceSquaredToBox(sample, blockBox)
                        <= radius * radius) {
                    output.add(pos);
                }
            }
        }
    }

    private static boolean isFadeCandidate(
            BlockPos pos,
            Box playerBox
    ) {
        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (BlockRenderLayers.getBlockLayer(state)
                == BlockRenderLayer.TRANSLUCENT) {
            return false;
        }

        Box blockBox = new Box(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 1.0,
                pos.getZ() + 1.0
        );

        return !blockBox.intersects(playerBox);
    }

    private static double distanceSquaredToBox(
            Vec3d point,
            Box box
    ) {
        double dx = axisDistance(
                point.x,
                box.minX,
                box.maxX
        );

        double dy = axisDistance(
                point.y,
                box.minY,
                box.maxY
        );

        double dz = axisDistance(
                point.z,
                box.minZ,
                box.maxZ
        );

        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(
            double value,
            double min,
            double max
    ) {
        if (value < min) {
            return min - value;
        }

        if (value > max) {
            return value - max;
        }

        return 0.0;
    }

    private static void renderHiddenBlocks(
            Camera camera
    ) {
        if (STATES.isEmpty()
                || mc.world == null
                || mc.player == null) {
            return;
        }

        VertexConsumerProvider.Immediate consumers =
                mc.getBufferBuilders()
                        .getEntityVertexConsumers();

        RenderLayer layer =
                TexturedRenderLayers.getBlockTranslucentCull();

        VertexConsumer baseConsumer =
                consumers.getBuffer(layer);

        BlockRenderManager blockRenderManager =
                mc.getBlockRenderManager();

        Vec3d cameraPos = camera.getCameraPos();

        for (FadeState fade : STATES.values()) {
            if (fade.alpha <= 0.001F) {
                continue;
            }

            BlockState state =
                    mc.world.getBlockState(fade.pos);

            if (state.isAir()) {
                continue;
            }

            MatrixStack matrices = new MatrixStack();

            matrices.translate(
                    fade.pos.getX() - cameraPos.x,
                    fade.pos.getY() - cameraPos.y,
                    fade.pos.getZ() - cameraPos.z
            );

            Random random = Random.create(
                    state.getRenderingSeed(fade.pos)
            );

            BlockStateModel model =
                    blockRenderManager.getModel(state);

            List<BlockModelPart> parts =
                    model.getParts(random);

            VertexConsumer alphaConsumer =
                    new AlphaVertexConsumer(
                            baseConsumer,
                            fade.alpha
                    );

            blockRenderManager.renderBlock(
                    state,
                    fade.pos,
                    mc.world,
                    matrices,
                    alphaConsumer,
                    false,
                    parts
            );
        }

        consumers.draw(layer);
    }

    private static float approach(
            float current,
            float target,
            float amount
    ) {
        if (current < target) {
            return Math.min(
                    current + amount,
                    target
            );
        }

        if (current > target) {
            return Math.max(
                    current - amount,
                    target
            );
        }

        return current;
    }

    private static void scheduleRebuild(
            BlockPos pos
    ) {
        if (mc.world == null) {
            return;
        }

        mc.world.scheduleBlockRenders(
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
    }

    private static void clearImmediately() {
        if (HIDDEN_IN_CHUNK.isEmpty()
                && STATES.isEmpty()) {
            return;
        }

        Set<BlockPos> restore =
                new HashSet<>(HIDDEN_IN_CHUNK);

        HIDDEN_IN_CHUNK.clear();
        STATES.clear();
        lastFrameNanos = 0L;

        if (mc.world != null) {
            for (BlockPos pos : restore) {
                scheduleRebuild(pos);
            }
        }
    }

    private static final class FadeState {

        private final BlockPos pos;

        private float alpha = 1.0F;
        private boolean desired = false;

        private long hideReadyAt = 0L;
        private boolean releaseScheduled = false;
        private long releaseAt = 0L;

        private FadeState(
                BlockPos pos
        ) {
            this.pos = pos.toImmutable();
        }
    }

    private static final class AlphaVertexConsumer
            implements VertexConsumer {

        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaVertexConsumer(
                VertexConsumer delegate,
                float alpha
        ) {
            this.delegate = delegate;
            this.alpha = MathHelper.clamp(
                    alpha,
                    0.0F,
                    1.0F
            );
        }

        @Override
        public VertexConsumer vertex(
                float x,
                float y,
                float z
        ) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(
                int red,
                int green,
                int blue,
                int alpha
        ) {
            delegate.color(
                    red,
                    green,
                    blue,
                    MathHelper.clamp(
                            Math.round(alpha * this.alpha),
                            0,
                            255
                    )
            );
            return this;
        }

        @Override
        public VertexConsumer color(
                int argb
        ) {
            int sourceAlpha =
                    argb >>> 24;

            int resultAlpha =
                    MathHelper.clamp(
                            Math.round(sourceAlpha * this.alpha),
                            0,
                            255
                    );

            delegate.color(
                    resultAlpha << 24
                            | argb & 0x00FFFFFF
            );

            return this;
        }

        @Override
        public VertexConsumer texture(
                float u,
                float v
        ) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(
                int u,
                int v
        ) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(
                int u,
                int v
        ) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(
                float x,
                float y,
                float z
        ) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(
                float width
        ) {
            delegate.lineWidth(width);
            return this;
        }
    }
}
