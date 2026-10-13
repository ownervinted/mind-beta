/**
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.DrawEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.impl.misc.ClickPearl;
import mind.beta.modules.impl.player.NameProtect;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.util.Instance;
import mind.beta.util.repository.friend.FriendUtils;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.Render3D;
import mind.beta.util.render.font.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector4f;

public class Predictions extends ModuleStructure {

    private static final int MAX_TICKS = 300;
    private static final int SUB_STEPS = 3;

    private static final double PEARL_SPEED = 1.5;
    private static final double PEARL_GRAVITY = 0.03;
    private static final double PEARL_AIR_DRAG = 0.99;
    private static final double PEARL_WATER_DRAG = 0.80;

    private static final double ARROW_GRAVITY = 0.05;
    private static final double ARROW_AIR_DRAG = 0.99;
    private static final double ARROW_WATER_DRAG = 0.60;
    private static final double ARROW_MAX_SPEED = 3.0;

    private static final float MARKER_LERP = 0.15f;
    private static final float NORMAL_LERP = 0.18f;
    private static final float APPEAR_SPEED = 0.12f;
    private static final float DISAPPEAR_SPEED = 0.10f;
    private static final float DISTANCE_APPEAR_SPEED = 0.14f;
    private static final float DISTANCE_DISAPPEAR_SPEED = 0.11f;

    private static final double MARKER_MIN_HALF_SIZE = 0.22;
    private static final double MARKER_MAX_HALF_SIZE = 0.70;
    private static final double MARKER_OFFSET = 0.030;

    private final ColorSetting selfColor = new ColorSetting(
            "Self Color",
            "Prediction color for your projectiles"
    ).value(new Color(0, 128, 255, 235).getRGB());

    private final ColorSetting friendColor = new ColorSetting(
            "Friend Color",
            "Prediction color for friends"
    ).value(new Color(84, 214, 128, 235).getRGB());

    private final ColorSetting enemyColor = new ColorSetting(
            "Enemy Color",
            "Prediction color for other players"
    ).value(new Color(255, 82, 105, 235).getRGB());

    private final Map<String, MarkerState> markers = new HashMap<>();

    public Predictions() {
        super("Predictions", "Predictions", ModuleCategory.RENDER);
        settings(
                selfColor,
                friendColor,
                enemyColor
        );
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        DrawContext context = event.getDrawContext();

        for (MarkerState state : markers.values()) {
            if (!state.isPearl
                    || state.smoothPos == null
                    || state.distanceAlpha <= 0.03f) {
                continue;
            }

            ScreenPoint screen = projectToScreen(
                    state.smoothPos.add(0.0, getLabelLift(state.smoothPos), 0.0)
            );

            if (screen == null) {
                continue;
            }

            float horizontalDistance = getHorizontalDistanceToImpact(
                    state.smoothPos
            );

            drawPearlInfo2D(
                    context,
                    screen.x(),
                    screen.y(),
                    state.color,
                    state.distanceAlpha,
                    horizontalDistance,
                    state.ownerName,
                    state.ownerType,
                    state.isFlying
            );
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.world == null || mc.player == null) {
            markers.clear();
            return;
        }

        markers.values().forEach(state -> state.seen = false);

        float tickDelta = event.getPartialTicks();

        collectHeldPrediction(tickDelta);
        collectFlyingPearls(tickDelta);

        List<String> remove = new ArrayList<>();

        for (Map.Entry<String, MarkerState> entry : markers.entrySet()) {
            MarkerState state = entry.getValue();

            if (state.seen) {
                state.alpha = MathHelper.clamp(state.alpha + APPEAR_SPEED, 0f, 1f);
            } else {
                state.alpha = MathHelper.clamp(state.alpha - DISAPPEAR_SPEED, 0f, 1f);

                if (state.alpha <= 0f) {
                    remove.add(entry.getKey());
                    continue;
                }
            }

            float distanceTarget = state.seen && state.isPearl ? 1f : 0f;

            state.distanceAlpha = moveTowards(
                    state.distanceAlpha,
                    distanceTarget,
                    distanceTarget > state.distanceAlpha
                            ? DISTANCE_APPEAR_SPEED
                            : DISTANCE_DISAPPEAR_SPEED
            );

            if (state.isFlying && state.seen) {
                if (state.lineAppearStartedAt == 0L) {
                    state.lineAppearStartedAt = System.currentTimeMillis();
                }

                float elapsed = (System.currentTimeMillis() - state.lineAppearStartedAt) / 1000f;
                state.lineAlpha = smoothstep(MathHelper.clamp(elapsed, 0f, 1f));
            } else {
                state.lineAlpha = 0f;
                state.lineAppearStartedAt = 0L;
            }

            if (state.smoothPos == null) {
                state.smoothPos = state.targetPos;
                state.smoothNormal = state.targetNormal;
            } else if (state.seen && !state.isFlying) {
                state.smoothPos = lerpVec(state.smoothPos, state.targetPos, MARKER_LERP);

                Vec3d normal = lerpVec(state.smoothNormal, state.targetNormal, NORMAL_LERP);
                state.smoothNormal = normal.lengthSquared() > 1.0E-7
                        ? normal.normalize()
                        : state.targetNormal;
            } else if (state.isFlying && state.frozenImpact != null) {
                state.smoothPos = state.frozenImpact.pos();
                state.smoothNormal = state.frozenImpact.normal();
            }

            renderImpactMarker(
                    state.smoothPos,
                    state.smoothNormal,
                    state.color,
                    state.alpha,
                    state.key.hashCode()
            );

            if (state.isFlying
                    && state.path != null
                    && state.path.size() > 1
                    && state.lineAlpha > 0.002f) {
                renderFlyingTrajectory(
                        state.path,
                        state.currentProjectilePos,
                        state.color,
                        state.lineAlpha,
                        state.key.hashCode()
                );
            }
        }

        remove.forEach(markers::remove);
    }

    private void collectHeldPrediction(float tickDelta) {
        ItemStack active = mc.player.getActiveItem();

        if (mc.player.isUsingItem() && active.getItem() instanceof BowItem) {
            float useTicks = mc.player.getItemUseTime()
                    + mc.getRenderTickCounter().getTickProgress(false);

            float pull = getBowPower(useTicks);

            if (pull > 0.02f) {
                Vec3d look = getLookDirection();
                Vec3d start = getSmoothEyePosition(tickDelta);
                Vec3d motion = look.multiply(ARROW_MAX_SPEED * pull)
                        .add(getInheritedPlayerMotion());

                PredictionResult result = traceHeld(
                        start,
                        motion,
                        ARROW_GRAVITY,
                        ARROW_AIR_DRAG,
                        ARROW_WATER_DRAG
                );

                if (result != null) {
                    updateMarker(
                            "hand:bow",
                            result.impact(),
                            selfColor.getColor(),
                            false,
                            false,
                            null
                    );
                }
            }

            return;
        }

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        ClickPearl clickPearl = ClickPearl.getInstance();
        boolean clickPearlBindHeld = clickPearl != null
                && clickPearl.isState()
                && clickPearl.isBindHeld();

        if (clickPearlBindHeld
                || main.isOf(Items.ENDER_PEARL)
                || off.isOf(Items.ENDER_PEARL)) {
            Vec3d look = getLookDirection();
            Vec3d start = getSmoothEyePosition(tickDelta);
            Vec3d motion = look.multiply(PEARL_SPEED)
                    .add(getInheritedPlayerMotion());

            PredictionResult result = traceHeld(
                    start,
                    motion,
                    PEARL_GRAVITY,
                    PEARL_AIR_DRAG,
                    PEARL_WATER_DRAG
            );

            if (result != null) {
                updateMarker(
                        "hand:pearl",
                        result.impact(),
                        selfColor.getColor(),
                        true,
                        false,
                        null
                );

                MarkerState previewState = markers.get("hand:pearl");
                if (previewState != null) {
                    previewState.ownerType = OwnerType.SELF;
                    previewState.ownerName = getProtectedSelfName();
                }
            }
        }
    }

    private void collectFlyingPearls(float tickDelta) {
        Set<Integer> alive = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl) || pearl.isRemoved()) {
                continue;
            }

            alive.add(pearl.getId());

            String key = "entity:" + pearl.getId();
            MarkerState state = markers.get(key);

            if (state == null || state.frozenImpact == null || state.frozenPath == null) {
                FlyingPrediction prediction = simulateFlyingPearl(pearl, tickDelta);

                if (prediction == null || prediction.impact() == null) {
                    continue;
                }

                state = markers.computeIfAbsent(key, MarkerState::new);

                state.frozenImpact = prediction.impact();
                state.frozenPath = List.copyOf(prediction.path());
                state.targetPos = prediction.impact().pos();
                state.targetNormal = prediction.impact().normal();
                state.smoothPos = prediction.impact().pos();
                state.smoothNormal = prediction.impact().normal();
                state.color = getOwnerColor(pearl);
                state.ownerType = getOwnerType(pearl);
                state.ownerName = getOwnerName(pearl);
                state.isPearl = true;
                state.isFlying = true;
                state.lineAlpha = 0f;
                state.lineAppearStartedAt = System.currentTimeMillis();
            }

            state.seen = true;
            state.targetPos = state.frozenImpact.pos();
            state.targetNormal = state.frozenImpact.normal();
            state.path = state.frozenPath;
            state.currentProjectilePos = getInterpolatedEntityPosition(pearl, tickDelta);
            state.color = getOwnerColor(pearl);
            state.ownerType = getOwnerType(pearl);
            state.ownerName = getOwnerName(pearl);
            state.isPearl = true;
            state.isFlying = true;
        }

        markers.entrySet().removeIf(entry ->
                entry.getKey().startsWith("entity:")
                        && !alive.contains(parseEntityId(entry.getKey()))
                        && entry.getValue().alpha <= 0f
        );
    }

    private int parseEntityId(String key) {
        try {
            return Integer.parseInt(key.substring("entity:".length()));
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private void updateMarker(
            String key,
            Impact impact,
            int color,
            boolean pearl,
            boolean flying,
            List<Vec3d> path
    ) {
        MarkerState state = markers.computeIfAbsent(key, MarkerState::new);

        state.seen = true;
        state.targetPos = impact.pos();
        state.targetNormal = impact.normal();
        state.color = color;
        state.isPearl = pearl;
        state.isFlying = flying;
        state.path = path;

        if (state.smoothPos == null) {
            state.smoothPos = impact.pos();
            state.smoothNormal = impact.normal();
        }
    }

    private PredictionResult traceHeld(
            Vec3d start,
            Vec3d initialMotion,
            double gravity,
            double airDrag,
            double waterDrag
    ) {
        Vec3d position = start;
        Vec3d motion = initialMotion;

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            for (int step = 0; step < SUB_STEPS; step++) {
                Vec3d previous = position;
                position = position.add(motion.multiply(1.0 / SUB_STEPS));

                BlockHitResult blockHit = raycastBlock(previous, position, mc.player);

                if (blockHit.getType() != HitResult.Type.MISS) {
                    return new PredictionResult(createImpact(blockHit, motion), tick + 1);
                }

                if (position.y < mc.world.getBottomY() - 8) {
                    return null;
                }
            }

            double drag = isWater(position) ? waterDrag : airDrag;
            motion = motion.multiply(drag).add(0.0, -gravity, 0.0);
        }

        return null;
    }

    private FlyingPrediction simulateFlyingPearl(
            EnderPearlEntity pearl,
            float tickDelta
    ) {
        Vec3d position = getInterpolatedEntityPosition(pearl, tickDelta);
        Vec3d motion = pearl.getVelocity();

        List<Vec3d> path = new ArrayList<>();
        path.add(position);

        Entity owner = pearl.getOwner();

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            Vec3d tickStart = position;

            for (int step = 0; step < SUB_STEPS; step++) {
                Vec3d previous = position;
                position = position.add(motion.multiply(1.0 / SUB_STEPS));

                BlockHitResult blockHit = raycastBlock(previous, position, pearl);

                Box swept = pearl.getBoundingBox()
                        .offset(previous.subtract(pearl.getEntityPos()))
                        .stretch(position.subtract(previous))
                        .expand(0.25);

                EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                        mc.world,
                        pearl,
                        previous,
                        position,
                        swept,
                        entity -> entity != owner
                                && entity != pearl
                                && !entity.isRemoved()
                                && !entity.isSpectator()
                );

                HitResult nearest = nearestHit(previous, blockHit, entityHit);

                if (nearest != null) {
                    Vec3d hitPos = nearest.getPos();

                    if (path.getLast().squaredDistanceTo(hitPos) > 1.0E-8) {
                        path.add(hitPos);
                    }

                    return new FlyingPrediction(
                            path,
                            createImpact(nearest, motion)
                    );
                }

                if (position.y < mc.world.getBottomY() - 8) {
                    return null;
                }
            }

            if (tickStart.squaredDistanceTo(position) > 1.0E-8) {
                path.add(position);
            }

            double drag = isWater(position) ? PEARL_WATER_DRAG : PEARL_AIR_DRAG;
            motion = motion.multiply(drag).add(0.0, -PEARL_GRAVITY, 0.0);
        }

        return null;
    }

    private BlockHitResult raycastBlock(Vec3d start, Vec3d end, Entity source) {
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                source
        ));
    }

    private HitResult nearestHit(
            Vec3d start,
            BlockHitResult blockHit,
            EntityHitResult entityHit
    ) {
        HitResult nearest = blockHit.getType() == HitResult.Type.MISS
                ? null
                : blockHit;

        if (entityHit != null && (
                nearest == null
                        || start.squaredDistanceTo(entityHit.getPos())
                        < start.squaredDistanceTo(nearest.getPos())
        )) {
            nearest = entityHit;
        }

        return nearest;
    }

    private Impact createImpact(HitResult hit, Vec3d motion) {
        Vec3d normal;

        if (hit instanceof BlockHitResult blockHit) {
            normal = Vec3d.of(blockHit.getSide().getVector());
        } else {
            normal = motion.lengthSquared() > 1.0E-7
                    ? motion.normalize().negate()
                    : new Vec3d(0, 1, 0);
        }

        return new Impact(hit.getPos(), normal);
    }

    private void renderFlyingTrajectory(
            List<Vec3d> points,
            Vec3d projectilePos,
            int baseColor,
            float alpha,
            int seed
    ) {
        if (points == null || points.size() < 2 || projectilePos == null) {
            return;
        }

        int nearestIndex = findNearestPathIndex(points, projectilePos);
        int trailSegments = 9;
        int firstSegment = Math.max(0, nearestIndex - trailSegments);
        int lastSegment = Math.min(nearestIndex, points.size() - 1);

        long now = System.currentTimeMillis();

        for (int i = firstSegment; i < lastSegment; i++) {
            Vec3d start = points.get(i);
            Vec3d end = points.get(i + 1);

            if (start.squaredDistanceTo(end) < 1.0E-8) {
                continue;
            }

            float fade0 = tailOnlyFade(
                    i,
                    nearestIndex,
                    trailSegments
            );

            float fade1 = tailOnlyFade(
                    i + 1,
                    nearestIndex,
                    trailSegments
            );

            float wave0 = 0.94f + 0.06f * (float) Math.sin(
                    now * 0.0055 - i * 0.34 + seed * 0.013
            );

            float wave1 = 0.94f + 0.06f * (float) Math.sin(
                    now * 0.0055 - (i + 1) * 0.34 + seed * 0.013
            );

            int c0 = multiplyAlpha(
                    baseColor,
                    alpha * 0.78f * fade0 * wave0
            );

            int c1 = multiplyAlpha(
                    baseColor,
                    alpha * 0.78f * fade1 * wave1
            );

            drawTrajectoryRibbon(
                    start,
                    end,
                    c0,
                    c1
            );
        }
    }

    private int findNearestPathIndex(
            List<Vec3d> points,
            Vec3d projectilePos
    ) {
        int nearest = 0;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            double distance = points.get(i).squaredDistanceTo(projectilePos);

            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = i;
            }
        }

        return nearest;
    }

    private float tailOnlyFade(
            int pointIndex,
            int projectileIndex,
            int trailSegments
    ) {
        int behind = projectileIndex - pointIndex;

        if (behind < 0 || behind > trailSegments) {
            return 0f;
        }

        float t = 1f - behind / (float) trailSegments;

        return smoothstep(
                MathHelper.clamp(t, 0f, 1f)
        );
    }

    private void drawTrajectoryRibbon(
            Vec3d start,
            Vec3d end,
            int colorStart,
            int colorEnd
    ) {
        Vec3d direction = end.subtract(start);

        if (direction.lengthSquared() < 1.0E-8) {
            return;
        }

        Vec3d midpoint = start.add(end).multiply(0.5);
        Vec3d cameraDirection = Render3D.lastCameraPos.subtract(midpoint);

        Vec3d side = direction.crossProduct(cameraDirection);

        if (side.lengthSquared() < 1.0E-7) {
            side = direction.crossProduct(new Vec3d(0, 1, 0));
        }

        if (side.lengthSquared() < 1.0E-7) {
            side = new Vec3d(1, 0, 0);
        }

        double distance = Math.max(1.0, Render3D.lastCameraPos.distanceTo(midpoint));
        double halfWidth = MathHelper.clamp(distance * 0.00085, 0.009, 0.034);

        Vec3d offset = side.normalize().multiply(halfWidth);

        Vec3d p1 = start.add(offset);
        Vec3d p2 = end.add(offset);
        Vec3d p3 = end.subtract(offset);
        Vec3d p4 = start.subtract(offset);

        Render3D.drawGradientQuad(
                p1,
                p2,
                p3,
                p4,
                colorStart,
                colorEnd,
                colorEnd,
                colorStart,
                false
        );
    }

    private void renderImpactMarker(
            Vec3d position,
            Vec3d normal,
            int baseColor,
            float alpha,
            int seed
    ) {
        if (position == null || normal == null || alpha <= 0.001f) {
            return;
        }

        Vec3d n = normal.lengthSquared() > 1.0E-7
                ? normal.normalize()
                : new Vec3d(0, 1, 0);

        Vec3d reference = Math.abs(n.y) < 0.90
                ? new Vec3d(0, 1, 0)
                : new Vec3d(1, 0, 0);

        Vec3d right = n.crossProduct(reference).normalize();
        Vec3d up = right.crossProduct(n).normalize();

        double distance = Math.max(1.0, Render3D.lastCameraPos.distanceTo(position));
        double half = MathHelper.clamp(
                0.22 + distance * 0.0085,
                MARKER_MIN_HALF_SIZE,
                MARKER_MAX_HALF_SIZE
        );

        float pulse = 0.5f + 0.5f * (float) Math.sin(
                System.currentTimeMillis() * 0.0038 + seed * 0.01
        );

        half *= 0.97 + pulse * 0.035;

        Vec3d origin = position.add(n.multiply(MARKER_OFFSET));

        Vec3d r = right.multiply(half);
        Vec3d u = up.multiply(half);

        Vec3d p1 = origin.add(r).add(u);
        Vec3d p2 = origin.subtract(r).add(u);
        Vec3d p3 = origin.subtract(r).subtract(u);
        Vec3d p4 = origin.add(r).subtract(u);

        int fillCenter = multiplyAlpha(baseColor, 0.24f * alpha);
        int fillEdge = multiplyAlpha(baseColor, 0.16f * alpha);
        int edge = multiplyAlpha(baseColor, 0.94f * alpha);
        int edgeSoft = multiplyAlpha(baseColor, 0.42f * alpha);

        drawDoubleSidedGradientQuad(
                p1,
                p2,
                p3,
                p4,
                fillCenter,
                fillEdge,
                fillCenter,
                fillEdge
        );

        double edgeWidth = MathHelper.clamp(half * 0.045, 0.010, 0.034);

        drawSurfaceEdge(p1, p2, n, edgeWidth, edge, edgeSoft);
        drawSurfaceEdge(p2, p3, n, edgeWidth, edge, edgeSoft);
        drawSurfaceEdge(p3, p4, n, edgeWidth, edge, edgeSoft);
        drawSurfaceEdge(p4, p1, n, edgeWidth, edge, edgeSoft);
    }

    private void drawSurfaceEdge(
            Vec3d start,
            Vec3d end,
            Vec3d normal,
            double width,
            int color,
            int colorSoft
    ) {
        Vec3d edgeDirection = end.subtract(start);

        if (edgeDirection.lengthSquared() < 1.0E-8) {
            return;
        }

        Vec3d inward = normal.crossProduct(edgeDirection).normalize().multiply(width);

        Vec3d a = start.add(inward);
        Vec3d b = end.add(inward);
        Vec3d c = end.subtract(inward);
        Vec3d d = start.subtract(inward);

        drawDoubleSidedGradientQuad(
                a,
                b,
                c,
                d,
                colorSoft,
                color,
                color,
                colorSoft
        );
    }

    private void drawDoubleSidedGradientQuad(
            Vec3d p1,
            Vec3d p2,
            Vec3d p3,
            Vec3d p4,
            int c1,
            int c2,
            int c3,
            int c4
    ) {
        Render3D.drawGradientQuad(
                p1,
                p2,
                p3,
                p4,
                c1,
                c2,
                c3,
                c4,
                false
        );

        Render3D.drawGradientQuad(
                p4,
                p3,
                p2,
                p1,
                c4,
                c3,
                c2,
                c1,
                false
        );
    }

    private double getLabelLift(Vec3d position) {
        double distance = Math.max(
                1.0,
                mc.gameRenderer.getCamera().getCameraPos().distanceTo(position)
        );

        return MathHelper.clamp(
                0.38 + distance * 0.0045,
                0.38,
                0.78
        );
    }

    private void drawPearlInfo2D(
            DrawContext context,
            float centerX,
            float centerY,
            int baseColor,
            float alpha,
            float distanceBlocks,
            String ownerName,
            OwnerType ownerType,
            boolean flying
    ) {
        if (alpha <= 0.03f) {
            return;
        }

        float eased = smoothstep(MathHelper.clamp(alpha, 0f, 1f));
        float size = 6.2f;
        float gap = 4.0f;

        String safeName = ownerName == null || ownerName.isBlank()
                ? "Unknown"
                : shortenName(ownerName);

        String distanceText = formatDistance(distanceBlocks);

        float nameWidth = Fonts.MEDIUM.getWidth(safeName, size);
        float distanceWidth = Fonts.MEDIUM.getWidth(distanceText, size);
        float height = Fonts.MEDIUM.getHeight(size);

        float contentWidth = nameWidth
                + gap
                + distanceWidth;

        float boxWidth = contentWidth + 10f;
        float boxHeight = height + 4f;

        float x = centerX - boxWidth * 0.5f;
        float y = centerY - boxHeight * 0.5f;

        int panelAlpha = MathHelper.clamp(
                Math.round(82f * eased),
                0,
                255
        );

        int textAlpha = MathHelper.clamp(
                Math.round(230f * eased),
                0,
                255
        );

        int nameAlpha = MathHelper.clamp(
                Math.round(245f * eased),
                0,
                255
        );

        int panel = (panelAlpha << 24) | 0x050B12;
        int distanceColor = (textAlpha << 24) | 0xF0F3F8;
        int nameColor = (nameAlpha << 24) | (baseColor & 0x00FFFFFF);

        Render2D.rect(
                x,
                y,
                boxWidth,
                boxHeight,
                panel,
                3f
        );

        float drawX = x + 5f;
        float textY = y + 2f;

        Fonts.MEDIUM.draw(
                safeName,
                drawX,
                textY,
                size,
                nameColor
        );

        drawX += nameWidth + gap;

        Fonts.MEDIUM.draw(
                distanceText,
                drawX,
                textY,
                size,
                distanceColor
        );
    }

    private String shortenName(String name) {
        String clean = name.trim();

        if (clean.length() <= 12) {
            return clean;
        }

        return clean.substring(0, 12) + "...";
    }

    private String formatDistance(float blocks) {
        if (blocks >= 100f) {
            return String.format("%.0fb", blocks);
        }

        return String.format("%.1fb", blocks);
    }

    private float getHorizontalDistanceToImpact(Vec3d impact) {
        if (mc.player == null || impact == null) {
            return 0f;
        }

        Vec3d player = mc.player.getEntityPos();

        double dx = impact.x - player.x;
        double dz = impact.z - player.z;

        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private float moveTowards(
            float current,
            float target,
            float speed
    ) {
        if (current < target) {
            return Math.min(target, current + speed);
        }

        if (current > target) {
            return Math.max(target, current - speed);
        }

        return current;
    }

    private ScreenPoint projectToScreen(Vec3d worldPos) {
        Vec3d camera = Render3D.lastCameraPos;

        Vector4f vector = new Vector4f(
                (float) (worldPos.x - camera.x),
                (float) (worldPos.y - camera.y),
                (float) (worldPos.z - camera.z),
                1.0f
        );

        vector.mul(Render3D.lastWorldSpaceMatrix);
        vector.mul(Render3D.lastProjMat);

        if (vector.w <= 0.001f) {
            return null;
        }

        float invW = 1.0f / vector.w;
        float ndcX = vector.x * invW;
        float ndcY = vector.y * invW;

        if (ndcX < -1.35f
                || ndcX > 1.35f
                || ndcY < -1.35f
                || ndcY > 1.35f) {
            return null;
        }

        float screenX = (ndcX * 0.5f + 0.5f)
                * mc.getWindow().getScaledWidth();

        float screenY = (0.5f - ndcY * 0.5f)
                * mc.getWindow().getScaledHeight();

        return new ScreenPoint(screenX, screenY);
    }

    private Vec3d getSmoothEyePosition(float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, mc.player.lastX, mc.player.getX()),
                MathHelper.lerp(tickDelta, mc.player.lastY, mc.player.getY())
                        + mc.player.getEyeHeight(mc.player.getPose()),
                MathHelper.lerp(tickDelta, mc.player.lastZ, mc.player.getZ())
        );
    }

    private Vec3d getInterpolatedEntityPosition(
            Entity entity,
            float tickDelta
    ) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.lastX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ())
        );
    }

    private Vec3d getLookDirection() {
        return mc.player
                .getRotationVector(mc.player.getPitch(), mc.player.getYaw())
                .normalize();
    }

    private Vec3d getInheritedPlayerMotion() {
        Vec3d velocity = mc.player.getVelocity();

        return new Vec3d(
                velocity.x,
                mc.player.isOnGround() ? 0.0 : velocity.y,
                velocity.z
        );
    }

    private float getBowPower(float ticks) {
        float power = ticks / 20f;
        power = (power * power + power * 2f) / 3f;
        return MathHelper.clamp(power, 0f, 1f);
    }

    private boolean isWater(Vec3d position) {
        BlockPos pos = BlockPos.ofFloored(position);
        return mc.world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    private int getOwnerColor(EnderPearlEntity pearl) {
        return switch (getOwnerType(pearl)) {
            case SELF -> selfColor.getColor();
            case FRIEND -> friendColor.getColor();
            case ENEMY -> enemyColor.getColor();
        };
    }

    private OwnerType getOwnerType(EnderPearlEntity pearl) {
        Entity owner = pearl.getOwner();

        if (owner == mc.player) {
            return OwnerType.SELF;
        }

        if (owner instanceof PlayerEntity player && FriendUtils.isFriend(player)) {
            return OwnerType.FRIEND;
        }

        return OwnerType.ENEMY;
    }

    private String getOwnerName(EnderPearlEntity pearl) {
        Entity owner = pearl.getOwner();

        if (owner == null) {
            return "Unknown";
        }

        if (owner == mc.player) {
            return getProtectedSelfName();
        }

        if (owner instanceof PlayerEntity player) {
            if (shouldProtectFriend(player)) {
                return getProtectedName();
            }

            return player.getName().getString();
        }

        return owner.getName().getString();
    }

    private String getProtectedSelfName() {
        NameProtect nameProtect = Instance.get(NameProtect.class);

        if (nameProtect != null && nameProtect.isState()) {
            String protectedName = nameProtect.getNameSetting().getText();

            if (protectedName != null && !protectedName.isBlank()) {
                return protectedName;
            }
        }

        if (mc.player == null) {
            return "Self";
        }

        return mc.player.getName().getString();
    }

    private boolean shouldProtectFriend(PlayerEntity player) {
        if (player == null || !FriendUtils.isFriend(player)) {
            return false;
        }

        NameProtect nameProtect = Instance.get(NameProtect.class);

        return nameProtect != null
                && nameProtect.isState()
                && nameProtect.getFriendsSetting().isValue();
    }

    private String getProtectedName() {
        NameProtect nameProtect = Instance.get(NameProtect.class);

        if (nameProtect == null) {
            return "Protected";
        }

        String protectedName = nameProtect.getNameSetting().getText();

        if (protectedName == null || protectedName.isBlank()) {
            return "Protected";
        }

        return protectedName;
    }

    private float trajectoryFade(float t) {
        if (t <= 0.70f) {
            return 1f;
        }

        float x = MathHelper.clamp((t - 0.70f) / 0.30f, 0f, 1f);
        return 1f - x * x * (3f - 2f * x);
    }

    private Vec3d lerpVec(Vec3d from, Vec3d to, float speed) {
        return new Vec3d(
                MathHelper.lerp(speed, from.x, to.x),
                MathHelper.lerp(speed, from.y, to.y),
                MathHelper.lerp(speed, from.z, to.z)
        );
    }

    private float smoothstep(float t) {
        float clamped = MathHelper.clamp(t, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private float easeOutCubic(float t) {
        float clamped = MathHelper.clamp(t, 0f, 1f);
        float inv = 1f - clamped;
        return 1f - inv * inv * inv;
    }

    private int multiplyAlpha(int color, float multiplier) {
        int sourceAlpha = (color >>> 24) & 0xFF;
        int alpha = MathHelper.clamp(
                Math.round(sourceAlpha * MathHelper.clamp(multiplier, 0f, 1f)),
                0,
                255
        );

        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private record Impact(
            Vec3d pos,
            Vec3d normal
    ) {
    }

    private record PredictionResult(
            Impact impact,
            int ticks
    ) {
    }

    private record FlyingPrediction(
            List<Vec3d> path,
            Impact impact
    ) {
    }

    private record ScreenPoint(
            float x,
            float y
    ) {
    }

    private enum OwnerType {
        SELF,
        FRIEND,
        ENEMY
    }

    private static class MarkerState {

        final String key;

        Vec3d targetPos;
        Vec3d targetNormal;

        Vec3d smoothPos;
        Vec3d smoothNormal;

        List<Vec3d> path;
        List<Vec3d> frozenPath;
        Impact frozenImpact;
        Vec3d currentProjectilePos;

        String ownerName = "Unknown";
        OwnerType ownerType = OwnerType.ENEMY;

        int color;

        float alpha;
        float lineAlpha;
        float distanceAlpha;

        long lineAppearStartedAt;

        boolean isPearl;
        boolean isFlying;
        boolean seen;

        MarkerState(String key) {
            this.key = key;
        }
    }
}
