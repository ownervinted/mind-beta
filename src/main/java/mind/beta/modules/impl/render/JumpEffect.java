/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.IMinecraft;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.JumpEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.ColorUtil;
import mind.beta.util.render.сliemtpipeline.ClientPipelines;
import mind.beta.util.timer.StopWatch;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpEffect extends ModuleStructure implements IMinecraft {

    private final List<Flow> flows = new ArrayList<>();

    final Identifier glowTexture = Identifier.of("mind", "images/particle/glow.png");

    final SliderSettings maxSize = new SliderSettings("Max Size", "Maximum flow size")
            .setValue(2f)
            .range(1.0f, 2.0f);

    final SliderSettings speed = new SliderSettings("Speed", "Animation speed")
            .setValue(1600f)
            .range(1000f, 2500f);

    final BooleanSetting glow = new BooleanSetting("Glow", "Glow effect")
            .setValue(true);

    final ColorSetting color1 = new ColorSetting("Color 1", "First color")
            .value(ColorUtil.getColor(137, 97, 72, 255));

    final ColorSetting color2 = new ColorSetting("Color 2", "Second color")
            .value(ColorUtil.getColor(255, 255, 255, 255));

    private static final double SURFACE_EPSILON = 0.018;
    private static final double MAX_SURFACE_SEARCH_DOWN = 2.5;
    private static final double STEP_LENGTH = 0.12;
    private static final double MAX_STEP_HEIGHT = 0.72;
    private static final double MAX_DROP_HEIGHT = 1.35;
    private static final float CORE_WIDTH = 0.018f;
    private static final float GLOW_WIDTH = 0.085f;

    public JumpEffect() {
        super("JumpEffect", "Energy Flow", ModuleCategory.RENDER);
        settings(maxSize, speed, glow, color1, color2);
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || mc.world == null || event.getPlayer() != mc.player) return;

        double surfaceY = findSurfaceY(mc.player.getX(), mc.player.getZ(), mc.player.getY() + 0.2);
        if (Double.isNaN(surfaceY)) return;

        Vec3d origin = new Vec3d(mc.player.getX(), surfaceY + SURFACE_EPSILON, mc.player.getZ());
        flows.add(createFlow(origin));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        long lifetime = (long) speed.getValue();

        Iterator<Flow> iterator = flows.iterator();
        while (iterator.hasNext()) {
            Flow flow = iterator.next();
            if (flow.timer.elapsedTime() >= lifetime) {
                iterator.remove();
            }
        }

        if (flows.isEmpty() || mc.world == null) return;

        MatrixStack matrices = event.getStack();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (Flow flow : flows) {
            renderFlow(matrices, immediate, flow, cameraPos);
        }

        immediate.draw();
    }

    private Flow createFlow(Vec3d origin) {
        long seed = Double.doubleToLongBits(origin.x)
                ^ Long.rotateLeft(Double.doubleToLongBits(origin.z), 21)
                ^ System.nanoTime();
        Random random = new Random(seed);

        int primaryCount = 6 + random.nextInt(4);
        double maxLength = 1.15 + maxSize.getValue() * 0.72;
        List<Branch> branches = new ArrayList<>();

        double rotation = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < primaryCount; i++) {
            double baseAngle = rotation + Math.PI * 2.0 * i / primaryCount;
            double angle = baseAngle + randomRange(random, -0.24, 0.24);
            double length = maxLength * randomRange(random, 0.72, 1.0);
            Branch primary = buildBranch(origin, angle, length, random, 0);
            if (primary.points.size() >= 2) {
                branches.add(primary);
                spawnSecondaryBranches(branches, primary, random, maxLength);
            }
        }

        return new Flow(origin, branches, new StopWatch(), seed);
    }

    private Branch buildBranch(Vec3d origin, double initialAngle, double maxLength, Random random, int depth) {
        List<Vec3d> points = new ArrayList<>();
        points.add(origin);

        Vec3d current = origin;
        double angle = initialAngle;
        double travelled = 0.0;
        double drift = randomRange(random, -0.028, 0.028);
        int maxSteps = Math.max(2, (int) Math.ceil(maxLength / STEP_LENGTH));

        for (int i = 0; i < maxSteps; i++) {
            float t = (float) i / Math.max(1, maxSteps - 1);
            angle += drift + randomRange(random, -0.055, 0.055) * (0.35 + t * 0.65);

            double step = Math.min(STEP_LENGTH * randomRange(random, 0.86, 1.14), maxLength - travelled);
            if (step <= 0.001) break;

            double nextX = current.x + Math.cos(angle) * step;
            double nextZ = current.z + Math.sin(angle) * step;
            Vec3d next = projectToSurface(current, nextX, nextZ);
            if (next == null) break;

            if (next.distanceTo(current) < 0.02) break;

            points.add(next);
            travelled += step;
            current = next;

            if (depth == 0 && i > maxSteps * 0.35 && random.nextDouble() < 0.035) {
                angle += randomRange(random, -0.16, 0.16);
            }
        }

        return new Branch(points, depth, random.nextFloat());
    }

    private void spawnSecondaryBranches(List<Branch> branches, Branch primary, Random random, double maxLength) {
        if (primary.points.size() < 6) return;

        int childCount = random.nextInt(3);
        for (int i = 0; i < childCount; i++) {
            int minIndex = Math.max(2, primary.points.size() / 3);
            int maxIndex = Math.max(minIndex + 1, primary.points.size() - 2);
            if (maxIndex <= minIndex) continue;

            int index = minIndex + random.nextInt(maxIndex - minIndex);
            Vec3d origin = primary.points.get(index);
            Vec3d previous = primary.points.get(index - 1);

            double parentAngle = Math.atan2(origin.z - previous.z, origin.x - previous.x);
            double side = random.nextBoolean() ? 1.0 : -1.0;
            double angle = parentAngle + side * randomRange(random, 0.48, 0.9);
            double length = maxLength * randomRange(random, 0.22, 0.42);

            Branch branch = buildBranch(origin, angle, length, random, 1);
            if (branch.points.size() >= 2) {
                branches.add(branch);
            }
        }
    }

    private Vec3d projectToSurface(Vec3d previous, double x, double z) {
        double searchY = previous.y + MAX_STEP_HEIGHT;
        double surfaceY = findSurfaceY(x, z, searchY);
        if (Double.isNaN(surfaceY)) return null;

        double previousSurfaceY = previous.y - SURFACE_EPSILON;
        double delta = surfaceY - previousSurfaceY;

        if (delta > MAX_STEP_HEIGHT || delta < -MAX_DROP_HEIGHT) return null;

        return new Vec3d(x, surfaceY + SURFACE_EPSILON, z);
    }

    private void renderFlow(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Flow flow, Vec3d cameraPos) {
        float life = flow.timer.elapsedTime();
        float lifetime = speed.getValue();
        float progress = Math.min(life / lifetime, 1f);
        if (progress >= 1f) return;

        float reveal = easeOutCubic(Math.min(progress / 0.58f, 1f));
        float alpha = progress < 0.72f ? 1f : 1f - (progress - 0.72f) / 0.28f;
        alpha = clamp01(alpha);

        int c1 = color1.getColor();
        int c2 = color2.getColor();

        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.BLOOM_ESP.apply(glowTexture));

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (Branch branch : flow.branches) {
            renderBranch(buffer, matrix, branch, reveal, alpha, c1, c2);
        }

        matrices.pop();

        if (glow.isValue()) {
            renderFlowTips(matrices, immediate, flow, cameraPos, reveal, alpha, c1, c2);
        }
    }

    private void renderBranch(VertexConsumer buffer, Matrix4f matrix, Branch branch,
                              float reveal, float alpha, int c1, int c2) {
        if (branch.points.size() < 2) return;

        float branchDelay = branch.depth == 0 ? branch.phase * 0.08f : 0.14f + branch.phase * 0.10f;
        float localReveal = clamp01((reveal - branchDelay) / Math.max(0.01f, 1f - branchDelay));
        if (localReveal <= 0f) return;

        int totalSegments = branch.points.size() - 1;
        float visibleSegments = totalSegments * localReveal;
        int fullSegments = Math.min(totalSegments, (int) visibleSegments);
        float partial = visibleSegments - fullSegments;

        float depthAlpha = branch.depth == 0 ? 1f : 0.72f;
        float widthScale = branch.depth == 0 ? 1f : 0.68f;

        for (int i = 0; i < fullSegments; i++) {
            float t0 = (float) i / totalSegments;
            float t1 = (float) (i + 1) / totalSegments;
            drawEnergySegment(buffer, matrix, branch.points.get(i), branch.points.get(i + 1),
                    t0, t1, CORE_WIDTH * widthScale, alpha * depthAlpha, c1, c2, false);

            if (glow.isValue()) {
                drawEnergySegment(buffer, matrix, branch.points.get(i), branch.points.get(i + 1),
                        t0, t1, GLOW_WIDTH * widthScale, alpha * depthAlpha * 0.24f, c1, c2, true);
            }
        }

        if (fullSegments < totalSegments && partial > 0.001f) {
            Vec3d a = branch.points.get(fullSegments);
            Vec3d b = branch.points.get(fullSegments + 1);
            Vec3d partialEnd = a.lerp(b, partial);
            float t0 = (float) fullSegments / totalSegments;
            float t1 = (fullSegments + partial) / totalSegments;

            drawEnergySegment(buffer, matrix, a, partialEnd,
                    t0, t1, CORE_WIDTH * widthScale, alpha * depthAlpha, c1, c2, false);

            if (glow.isValue()) {
                drawEnergySegment(buffer, matrix, a, partialEnd,
                        t0, t1, GLOW_WIDTH * widthScale, alpha * depthAlpha * 0.24f, c1, c2, true);
            }
        }
    }

    private void drawEnergySegment(VertexConsumer buffer, Matrix4f matrix,
                                   Vec3d start, Vec3d end,
                                   float t0, float t1,
                                   float width, float alpha,
                                   int c1, int c2, boolean soft) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLength < 1.0E-5) return;

        double nx = -dz / horizontalLength;
        double nz = dx / horizontalLength;
        double half = width * 0.5;

        Vec3d a = new Vec3d(start.x + nx * half, start.y, start.z + nz * half);
        Vec3d b = new Vec3d(start.x - nx * half, start.y, start.z - nz * half);
        Vec3d c = new Vec3d(end.x - nx * half, end.y, end.z - nz * half);
        Vec3d d = new Vec3d(end.x + nx * half, end.y, end.z + nz * half);

        float colorT0 = smoothStep(t0);
        float colorT1 = smoothStep(t1);
        int startColor = ColorUtil.multAlpha(ColorUtil.lerpColor(c1, c2, colorT0), alpha * (soft ? 0.8f : 1f));
        int endColor = ColorUtil.multAlpha(ColorUtil.lerpColor(c1, c2, colorT1), alpha * (soft ? 0.8f : 1f));

        emitQuad(buffer, matrix,
                a, 0f, 0f, startColor,
                b, 1f, 0f, startColor,
                c, 1f, 1f, endColor,
                d, 0f, 1f, endColor);
    }

    private void renderFlowTips(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                Flow flow, Vec3d cameraPos, float reveal, float alpha,
                                int c1, int c2) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.BLOOM_ESP.apply(glowTexture));

        for (Branch branch : flow.branches) {
            if (branch.points.size() < 2) continue;

            float branchDelay = branch.depth == 0 ? branch.phase * 0.08f : 0.14f + branch.phase * 0.10f;
            float localReveal = clamp01((reveal - branchDelay) / Math.max(0.01f, 1f - branchDelay));
            if (localReveal <= 0f) continue;

            float pointPosition = (branch.points.size() - 1) * localReveal;
            int index = Math.min(branch.points.size() - 2, Math.max(0, (int) pointPosition));
            float partial = pointPosition - index;
            Vec3d tip = branch.points.get(index).lerp(branch.points.get(index + 1), partial);

            int color = ColorUtil.multAlpha(ColorUtil.lerpColor(c1, c2, smoothStep(localReveal)),
                    alpha * (branch.depth == 0 ? 0.72f : 0.48f));
            float size = branch.depth == 0 ? 0.12f : 0.085f;
            renderHorizontalGlow(matrices, buffer, tip, size, color, cameraPos);
        }
    }

    private void renderHorizontalGlow(MatrixStack matrices, VertexConsumer buffer,
                                      Vec3d pos, float size, int color, Vec3d cameraPos) {
        matrices.push();

        matrices.translate(
                pos.x - cameraPos.x,
                pos.y - cameraPos.y + 0.006,
                pos.z - cameraPos.z
        );
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float half = size * 0.5f;

        buffer.vertex(matrix, -half, -half, 0).texture(0, 0).color(color);
        buffer.vertex(matrix, half, -half, 0).texture(1, 0).color(color);
        buffer.vertex(matrix, half, half, 0).texture(1, 1).color(color);
        buffer.vertex(matrix, -half, half, 0).texture(0, 1).color(color);

        matrices.pop();
    }

    private void emitQuad(VertexConsumer buffer, Matrix4f matrix,
                          Vec3d p0, float u0, float v0, int c0,
                          Vec3d p1, float u1, float v1, int c1,
                          Vec3d p2, float u2, float v2, int c2,
                          Vec3d p3, float u3, float v3, int c3) {
        buffer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).texture(u0, v0).color(c0);
        buffer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).texture(u1, v1).color(c1);
        buffer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).texture(u2, v2).color(c2);
        buffer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).texture(u3, v3).color(c3);
    }

    private double findSurfaceY(double worldX, double worldZ, double startY) {
        if (mc.world == null) return Double.NaN;

        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        int startBlockY = (int) Math.floor(startY + 0.5);
        int minBlockY = (int) Math.floor(startY - MAX_SURFACE_SEARCH_DOWN) - 1;

        double localX = worldX - blockX;
        double localZ = worldZ - blockZ;
        double best = Double.NaN;

        for (int y = startBlockY; y >= minBlockY; y--) {
            BlockPos blockPos = new BlockPos(blockX, y, blockZ);
            BlockState state = mc.world.getBlockState(blockPos);
            if (state.isAir()) continue;

            VoxelShape shape = state.getCollisionShape(mc.world, blockPos);
            if (shape.isEmpty()) continue;

            for (Box box : shape.getBoundingBoxes()) {
                if (localX < box.minX - 1.0E-4 || localX > box.maxX + 1.0E-4) continue;
                if (localZ < box.minZ - 1.0E-4 || localZ > box.maxZ + 1.0E-4) continue;

                double surfaceY = y + box.maxY;
                if (surfaceY > startY + MAX_STEP_HEIGHT) continue;

                if (Double.isNaN(best) || surfaceY > best) {
                    best = surfaceY;
                }
            }

            if (!Double.isNaN(best)) {
                return best;
            }
        }

        return Double.NaN;
    }

    private double randomRange(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3f - 2f * t);
    }

    private float easeOutCubic(float value) {
        float t = clamp01(value);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Flow {
        final Vec3d origin;
        final List<Branch> branches;
        final StopWatch timer;
        final long seed;

        Flow(Vec3d origin, List<Branch> branches, StopWatch timer, long seed) {
            this.origin = origin;
            this.branches = branches;
            this.timer = timer;
            this.seed = seed;
        }
    }

    private static final class Branch {
        final List<Vec3d> points;
        final int depth;
        final float phase;

        Branch(List<Vec3d> points, int depth, float phase) {
            this.points = points;
            this.depth = depth;
            this.phase = phase;
        }
    }
}
