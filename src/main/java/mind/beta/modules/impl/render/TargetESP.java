/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import mind.beta.IMinecraft;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.animations.Animation;
import mind.beta.util.animations.Direction;
import mind.beta.util.animations.OutBack;
import mind.beta.util.render.сliemtpipeline.ClientPipelines;
import mind.beta.util.render.Render3D;
import mind.beta.util.timer.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TargetESP extends ModuleStructure implements IMinecraft {

    private static TargetESP instance;

    public static TargetESP getInstance() {
        return instance;
    }

    Animation espAnim = new OutBack().setMs(300).setValue(1);
    StopWatch stopWatch = new StopWatch();

    SelectSetting mode = new SelectSetting("Mode")
            .value("Rhomb", "Ghost", "Chain", "Crystals", "Circle", "Tesla", "Sandevistan")
            .selected("Rhomb");

    SliderSettings crystalRotationSpeed = new SliderSettings("Crystal Rotation Speed",
            "Crystal rotation speed")
            .range(0.1f, 2.0f)
            .visible(() -> mode.isSelected("Crystals"));

    SliderSettings teslaRadius = new SliderSettings("Tesla Radius",
            "Distance of the energy balls from the target")
            .range(0.45f, 1.4f)
            .visible(() -> mode.isSelected("Tesla"));

    SliderSettings teslaSpeed = new SliderSettings("Tesla Speed",
            "Tesla movement speed")
            .range(0.2f, 2.0f)
            .visible(() -> mode.isSelected("Tesla"));

    SliderSettings teslaArcWidth = new SliderSettings("Tesla Arc Width",
            "Tesla discharge width")
            .range(0.4f, 2.4f)
            .visible(() -> mode.isSelected("Tesla"));

    SliderSettings sandevistanCopies = new SliderSettings("Sandevistan Copies",
            "Amount of humanoid afterimages")
            .range(1, 8)
            .visible(() -> mode.isSelected("Sandevistan"));

    SliderSettings sandevistanSpacing = new SliderSettings("Sandevistan Spacing",
            "Delay between afterimages")
            .range(20f, 120f)
            .visible(() -> mode.isSelected("Sandevistan"));

    SliderSettings sandevistanFill = new SliderSettings("Sandevistan Fill",
            "Humanoid fill opacity")
            .range(0.05f, 0.6f)
            .visible(() -> mode.isSelected("Sandevistan"));


    ColorSetting color1 = new ColorSetting("Color 1", "First gradient color")
            .setColor(new Color(255, 101, 57, 255).getRGB());

    ColorSetting color2 = new ColorSetting("Color 2", "Second gradient color")
            .setColor(new Color(255, 50, 150, 255).getRGB());

    ColorSetting color3 = new ColorSetting("Color 3", "Third color for Ghost, Tesla and Sandevistan")
            .setColor(new Color(150, 50, 255, 255).getRGB())
            .visible(() -> mode.isSelected("Ghost") || mode.isSelected("Tesla") || mode.isSelected("Sandevistan"));

    private Vec3d smoothedPos = null;
    private LivingEntity lastTarget = null;
    private float movingValue = 0;
    private float hurtProgress = 0;

    private Entity lastRenderedTarget = null;
    private final List<Crystal> crystalList = new ArrayList<>();
    private float rotationAngle = 0;

    private final Vec3d[] teslaOrbWorldPositions = new Vec3d[3];
    private long teslaLastArcSeed = Long.MIN_VALUE;
    private final double[][] teslaArcNoise = new double[12][3];

    private final List<SandevistanSnapshot> sandevistanTrail = new ArrayList<>();
    private long lastSandevistanSample = 0L;
    private int sandevistanTargetId = Integer.MIN_VALUE;

    private static final double SANDEVISTAN_MOVE_EPSILON_SQ = 0.000004;
    private static final long SANDEVISTAN_IDLE_GRACE_MS = 140L;
    private Vec3d sandevistanLastObservedPos = null;
    private long sandevistanLastMoveTime = 0L;
    private boolean sandevistanMoving = false;
    private float sandevistanVisibility = 0f;
    private float sandevistanTargetHeight = 1.8f;

    private static final float SANDEVISTAN_FADE_IN_SPEED = 0.085f;
    private static final float SANDEVISTAN_FADE_OUT_SPEED = 0.055f;

    private long lastFrameTime = System.currentTimeMillis();
    private static final float TARGET_FPS = 60f;
    private static final float TARGET_FRAME_TIME = 1000f / TARGET_FPS;

    public TargetESP() {
        super("TargetEsp", "Target Esp", ModuleCategory.RENDER);
        instance = this;

        crystalRotationSpeed.setValue(0.5f);
        teslaRadius.setValue(0.78f);
        teslaSpeed.setValue(1.0f);
        teslaArcWidth.setValue(0.72f);
        sandevistanCopies.setValue(5f);
        sandevistanSpacing.setValue(42f);
        sandevistanFill.setValue(0.24f);

        settings(
                mode,
                crystalRotationSpeed,
                teslaRadius,
                teslaSpeed,
                teslaArcWidth,
                sandevistanCopies,
                sandevistanSpacing,
                sandevistanFill,
                color1,
                color2,
                color3
        );
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaMs = currentTime - lastFrameTime;
        lastFrameTime = currentTime;
        deltaMs = Math.max(1f, Math.min(deltaMs, 100f));
        return deltaMs / TARGET_FRAME_TIME;
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent e) {
        float deltaTime = getDeltaTime();

        LivingEntity target = null;

        if (Aura.getInstance() != null && Aura.getInstance().isState()) {
            target = Aura.target;
        }

        if (target == null) {
            espAnim.setDirection(Direction.BACKWARDS);
            Render3D.resetCircleSmoothing();

            if (mode.isSelected("Sandevistan")
                    && sandevistanVisibility > 0.001f
                    && !sandevistanTrail.isEmpty()) {
                sandevistanMoving = false;
                updateSandevistanVisibility(deltaTime);
                renderSandevistan(1.0f);

                if (sandevistanVisibility <= 0.01f) {
                    resetSandevistanState();
                    smoothedPos = null;
                    lastTarget = null;
                }

                return;
            }

            resetSandevistanState();
            smoothedPos = null;
            lastTarget = null;
            return;
        }

        espAnim.setDirection(Direction.FORWARDS);
        float alpha = espAnim.getOutput().floatValue();
        if (alpha <= 0.01f)
            return;

        movingValue += 2f * deltaTime;
        if (movingValue > 360000)
            movingValue = 0;

        float hurtDecay = 0.1f * deltaTime;
        hurtProgress = target.hurtTime > 0 ? (float) target.hurtTime / 10f : Math.max(0, hurtProgress - hurtDecay);

        Render3D.updateTargetEsp(deltaTime);

        if (mode.isSelected("Circle")) {
            renderCircle(e.getStack(), target, alpha);
            return;
        }

        MatrixStack stack = e.getStack();
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = e.getPartialTicks();
        Vec3d targetPos = target.getLerpedPos(partialTicks);

        if (lastTarget != target || smoothedPos == null) {
            smoothedPos = targetPos;
            lastTarget = target;
        } else {
            float smoothingFactor = Math.min(1.0f, partialTicks * 1.5f);
            double dx = targetPos.x - smoothedPos.x;
            double dy = targetPos.y - smoothedPos.y;
            double dz = targetPos.z - smoothedPos.z;
            smoothedPos = new Vec3d(
                    smoothedPos.x + dx * smoothingFactor,
                    smoothedPos.y + dy * smoothingFactor,
                    smoothedPos.z + dz * smoothingFactor);
        }

        if (mode.isSelected("Sandevistan")) {
            sandevistanTargetHeight = target.getHeight();
            updateSandevistanMovement(smoothedPos);
            updateSandevistanVisibility(deltaTime);
            updateSandevistanTrail(target, smoothedPos);
        } else if (!sandevistanTrail.isEmpty()
                || sandevistanLastObservedPos != null
                || sandevistanVisibility > 0f) {
            resetSandevistanState();
        }

        stack.push();
        stack.translate(
                smoothedPos.x - camPos.x,
                smoothedPos.y - camPos.y,
                smoothedPos.z - camPos.z);

        if (mode.isSelected("Rhomb")) {
            renderRhomb(stack, provider, target, alpha);
        } else if (mode.isSelected("Ghost")) {
            renderGhost(stack, provider, target, alpha);
        } else if (mode.isSelected("Chain")) {
            renderChain(stack, provider, target, alpha, deltaTime);
        } else if (mode.isSelected("Crystals")) {
            if (crystalList.isEmpty() || lastRenderedTarget != target) {
                createCrystals(target);
                lastRenderedTarget = target;
            }
            renderCrystals(stack, provider, target, alpha, deltaTime);
        } else if (mode.isSelected("Tesla")) {
            renderTesla(stack, provider, target, alpha, deltaTime);
        } else if (mode.isSelected("Sandevistan")) {
            renderSandevistan(alpha);
        }

        provider.draw();

        stack.pop();
    }

    private void renderCircle(MatrixStack stack, LivingEntity target, float alpha) {
        int baseColor1 = color1.getColor();
        int baseColor2 = color2.getColor();

        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }

        Render3D.drawCircle(stack, target, alpha, hurtProgress, baseColor1, baseColor2);
    }

    private void renderChain(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha,
                             float deltaTime) {
        VertexConsumer consumer = provider
                .getBuffer(ClientPipelines.CHAIN_ESP.apply(Identifier.of("mind", "images/world/chain.png")));

        float animValue = (System.currentTimeMillis() % 360000) / 1000f * 60f;

        float gradusX = (float) (20 * Math.min(1 + Math.sin(Math.toRadians(animValue)), 1));
        float gradusZ = (float) (20 * (Math.min(1 + Math.sin(Math.toRadians(animValue)), 2) - 1));
        float width = target.getWidth() * 3;

        int linksStep = 18;
        int totalAngle = 360 * 2;
        float chainSizeVal = 8;
        float down = 1.5f;
        float chainScale = 0.5f;

        int alphaVal = MathHelper.clamp((int) (alpha * 128), 0, 128);

        int baseColor1 = color1.getColor();
        int baseColor2 = color2.getColor();

        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }

        int c1 = withAlpha(baseColor1, alphaVal);
        int c2 = withAlpha(baseColor2, alphaVal);

        float rotationValue = (System.currentTimeMillis() % 720000) / 1000f * 30f;

        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2f - 0.5f * (chain == 0 ? 1.0f : 0.9f);

            stack.push();
            stack.translate(0, target.getHeight() / 2.0f, 0);
            stack.scale(chainScale, chainScale, chainScale);

            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chain == 0 ? gradusX : -gradusX));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(chain == 0 ? gradusZ : -gradusZ));

            float x = 0, y = -0.5f, z = 0;
            Matrix4f matrix = stack.peek().getPositionMatrix();

            int modif = linksStep / 2;
            for (int i = 0; i < totalAngle; i += modif) {
                float offsetX = (chain == 0 ? gradusX : -gradusX) / 100F;
                float offsetZ = (chain == 0 ? -gradusZ : gradusZ) / 100F;

                float prevSin = (float) (x + offsetX
                        + Math.sin(Math.toRadians(i - modif + rotationValue)) * width * val);
                float prevCos = (float) (z + offsetZ
                        + Math.cos(Math.toRadians(i - modif + rotationValue)) * width * val);

                float sin = (float) (x + offsetX + Math.sin(Math.toRadians(i + rotationValue)) * width * val);
                float cos = (float) (z + offsetZ + Math.cos(Math.toRadians(i + rotationValue)) * width * val);

                float u0 = 1f / 360f * (float) (i - modif) * chainSizeVal;
                float u1 = 1f / 360f * (float) i * chainSizeVal;

                consumer.vertex(matrix, prevSin, y, prevCos).texture(u0, 0).color(c1);
                consumer.vertex(matrix, sin, y, cos).texture(u1, 0).color(c1);
                consumer.vertex(matrix, sin, y + down, cos).texture(u1, 0.99f).color(c2);
                consumer.vertex(matrix, prevSin, y + down, prevCos).texture(u0, 0.99f).color(c2);
            }

            stack.pop();
        }
    }

    private void renderRhomb(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha) {
        VertexConsumer consumer = provider
                .getBuffer(ClientPipelines.ROMB_ESP.apply(Identifier.of("mind", "images/world/cube.png")));

        Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();

        stack.translate(0, target.getHeight() / 2f, 0);
        stack.multiply(camRot);

        float timeRotation = (System.currentTimeMillis() % 6283) / 1000f;
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(timeRotation) * 360));

        float size = 0.5f;
        stack.scale(size, size, 1);

        int c1 = withAlpha(color1.getColor(), (int) (255 * alpha));
        int c2 = withAlpha(color2.getColor(), (int) (255 * alpha));

        Vector3f[] quad = {
                new Vector3f(-1, -1, 0),
                new Vector3f(-1, 1, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(1, -1, 0)
        };

        var m = stack.peek();
        consumer.vertex(m, quad[0].x, quad[0].y, 0).texture(0, 0).color(c2);
        consumer.vertex(m, quad[1].x, quad[1].y, 0).texture(0, 1).color(c1);
        consumer.vertex(m, quad[2].x, quad[2].y, 0).texture(1, 1).color(c2);
        consumer.vertex(m, quad[3].x, quad[3].y, 0).texture(1, 0).color(c1);
    }

    private void renderGhost(MatrixStack stack, VertexConsumerProvider consumers, LivingEntity target, float alpha) {
        VertexConsumer consumer = consumers
                .getBuffer(ClientPipelines.GHOSTS_ESP.apply(Identifier.of("mind", "images/particle/ghost-glow.png")));

        stack.translate(0, target.getHeight() * 0.5f, 0);

        particle(stack, consumer, (sin, cos) -> new Vec3d(sin, cos, -cos), alpha, 0);
        particle(stack, consumer, (sin, cos) -> new Vec3d(-sin, sin, -cos), alpha, 1);
        particle(stack, consumer, (sin, cos) -> new Vec3d(-sin, -sin, cos), alpha, 2);
    }

    private void particle(MatrixStack stack, VertexConsumer consumer, Transformation transformation, float alpha,
                          int colorIndex) {
        double radius = 0.7f;
        double distance = 11;

        float particleSize = 0.5f;
        int alphaFactor = 15;

        long elapsed = System.currentTimeMillis();

        int baseColor;
        switch (colorIndex) {
            case 0 -> baseColor = color1.getColor();
            case 1 -> baseColor = color2.getColor();
            default -> baseColor = color3.getColor();
        }

        for (int i = 0; i < 40 * alpha; i++) {
            stack.push();

            double angle = 0.15 * ((elapsed * 0.5) - (i * distance)) / 30.0;

            double sin = Math.sin(angle) * radius;
            double cos = Math.cos(angle) * radius;

            Vec3d trans = transformation.make(sin, cos);
            stack.translate(trans.x, trans.y, trans.z);

            stack.multiply(mc.gameRenderer.getCamera().getRotation());

            float spinRotation = (elapsed * 0.1f) - (i * 10f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinRotation));

            stack.translate(particleSize / 2f, particleSize / 2f, 0);

            float x = (float) i / (float) 40;
            int lerpedColor = lerpColor(baseColor, getNextColor(colorIndex), x);

            int c1 = withAlpha(lerpedColor, (int) ((255 - i * alphaFactor) * alpha));
            int c2 = withAlpha(lerpedColor, (int) ((255 - i * alphaFactor) * alpha));

            var m = stack.peek();
            consumer.vertex(m, 0, -particleSize, 0).texture(0, 0).color(c2);
            consumer.vertex(m, -particleSize, -particleSize, 0).texture(0, 1).color(c1);
            consumer.vertex(m, -particleSize, 0, 0).texture(1, 1).color(c2);
            consumer.vertex(m, 0, 0, 0).texture(1, 0).color(c1);

            stack.pop();
        }
    }


    private void updateSandevistanTrail(
            LivingEntity target,
            Vec3d position
    ) {
        if (isSandevistanIdle(target)) {
            if (sandevistanVisibility <= 0.01f) {
                sandevistanTrail.clear();
                lastSandevistanSample = 0L;
            }
            return;
        }

        long now = System.currentTimeMillis();
        int targetId = target.getId();

        if (sandevistanTargetId != targetId) {
            sandevistanTrail.clear();
            sandevistanTargetId = targetId;
            lastSandevistanSample = 0L;
            sandevistanLastObservedPos = position;
            sandevistanLastMoveTime = now;
            sandevistanMoving = false;
            return;
        }

        long spacing = Math.max(
                16L,
                Math.round(sandevistanSpacing.getValue())
        );

        if (lastSandevistanSample != 0L
                && now - lastSandevistanSample < spacing) {
            return;
        }

        float yaw = target.getYaw();

        if (!sandevistanTrail.isEmpty()) {
            SandevistanSnapshot newest = sandevistanTrail.getFirst();
            double distance = newest.position.distanceTo(position);

            if (distance < 0.018
                    && now - newest.createdAt < spacing * 2L) {
                return;
            }
        }

        sandevistanTrail.addFirst(
                new SandevistanSnapshot(
                        position,
                        yaw,
                        now
                )
        );

        lastSandevistanSample = now;

        int maxSnapshots =
                Math.max(
                        3,
                        Math.round(
                                sandevistanCopies.getValue()
                        ) + 3
                );

        while (sandevistanTrail.size() > maxSnapshots) {
            sandevistanTrail.removeLast();
        }
    }

    private void renderSandevistan(
            float alpha
    ) {
        if (sandevistanVisibility <= 0.01f
                || sandevistanTrail.size() < 2) {
            return;
        }

        float visibility = smoothStep(sandevistanVisibility);
        alpha *= visibility;

        int copies =
                Math.max(
                        2,
                        Math.round(
                                sandevistanCopies.getValue()
                        )
                );

        int available =
                Math.min(
                        copies,
                        sandevistanTrail.size() - 1
                );

        int baseColor1 = color1.getColor();
        int baseColor2 = color2.getColor();
        int baseColor3 = color3.getColor();

        if (hurtProgress > 0f) {
            baseColor1 = lerpColor(
                    baseColor1,
                    0xFFFF3030,
                    hurtProgress * 0.55f
            );

            baseColor2 = lerpColor(
                    baseColor2,
                    0xFFFF3030,
                    hurtProgress * 0.55f
            );

            baseColor3 = lerpColor(
                    baseColor3,
                    0xFFFF3030,
                    hurtProgress * 0.55f
            );
        }

        float fillStrength =
                MathHelper.clamp(
                        sandevistanFill.getValue(),
                        0.05f,
                        0.6f
                );


        for (int i = available; i >= 1; i--) {
            SandevistanSnapshot snapshot =
                    sandevistanTrail.get(i);

            float order =
                    1f
                            - (i - 1f)
                            / Math.max(
                            1f,
                            available
                    );

            float fade =
                    order
                            * order;

            int gradientA =
                    lerpColor(
                            baseColor3,
                            baseColor2,
                            order
                    );

            int ghostColor =
                    lerpColor(
                            gradientA,
                            baseColor1,
                            order * 0.55f
                    );

            int fillColor =
                    withAlpha(
                            lightenColor(
                                    ghostColor,
                                    1.18f
                            ),
                            MathHelper.clamp(
                                    (int) (
                                            255f
                                                    * fillStrength
                                                    * alpha
                                                    * fade
                                    ),
                                    0,
                                    255
                            )
                    );

            renderSandevistanAura(
                    snapshot,
                    ghostColor,
                    alpha * fade,
                    sandevistanTargetHeight,
                    i
            );

            renderHumanoidAfterImage(
                    snapshot,
                    sandevistanTargetHeight,
                    fillColor,
                    i
            );
        }
    }

    private void resetSandevistanState() {
        sandevistanTrail.clear();
        sandevistanTargetId = Integer.MIN_VALUE;
        lastSandevistanSample = 0L;
        sandevistanLastObservedPos = null;
        sandevistanLastMoveTime = 0L;
        sandevistanMoving = false;
        sandevistanVisibility = 0f;
        sandevistanTargetHeight = 1.8f;
    }

    private void updateSandevistanVisibility(
            float deltaTime
    ) {
        float speed = sandevistanMoving
                ? SANDEVISTAN_FADE_IN_SPEED
                : SANDEVISTAN_FADE_OUT_SPEED;

        float target = sandevistanMoving
                ? 1f
                : 0f;

        if (sandevistanVisibility < target) {
            sandevistanVisibility = Math.min(
                    target,
                    sandevistanVisibility + speed * deltaTime
            );
        } else if (sandevistanVisibility > target) {
            sandevistanVisibility = Math.max(
                    target,
                    sandevistanVisibility - speed * deltaTime
            );
        }
    }

    private float smoothStep(
            float value
    ) {
        float t = MathHelper.clamp(
                value,
                0f,
                1f
        );

        return t * t * (3f - 2f * t);
    }

    private void updateSandevistanMovement(
            Vec3d currentPos
    ) {
        long now = System.currentTimeMillis();

        if (sandevistanLastObservedPos == null) {
            sandevistanLastObservedPos = currentPos;
            sandevistanLastMoveTime = now;
            sandevistanMoving = false;
            return;
        }

        double dx = currentPos.x - sandevistanLastObservedPos.x;
        double dz = currentPos.z - sandevistanLastObservedPos.z;
        double horizontalDeltaSq = dx * dx + dz * dz;

        if (horizontalDeltaSq > SANDEVISTAN_MOVE_EPSILON_SQ) {
            sandevistanLastMoveTime = now;
            sandevistanMoving = true;
        } else if (now - sandevistanLastMoveTime > SANDEVISTAN_IDLE_GRACE_MS) {
            sandevistanMoving = false;
        }

        sandevistanLastObservedPos = currentPos;
    }

    private boolean isSandevistanIdle(
            LivingEntity target
    ) {
        return !sandevistanMoving;
    }

    private void renderSandevistanAura(
            SandevistanSnapshot snapshot,
            int color,
            float alpha,
            float targetHeight,
            int trailIndex
    ) {
        if (alpha <= 0.01f) {
            return;
        }

        float scale = MathHelper.clamp(
                targetHeight / 1.8f,
                0.82f,
                1.25f
        );

        double time = snapshot.createdAt * 0.012;
        double swing = Math.sin(
                time + trailIndex * 0.48
        );

        double armSwing = swing * 0.095 * scale;
        double legSwing = swing * 0.075 * scale;

        Vec3d root = snapshot.position;
        float yaw = snapshot.yaw;

        Vec3d rightArm = rotateLocalOffset(
                0.37 * scale,
                1.08 * scale,
                armSwing,
                yaw
        );

        Vec3d leftArm = rotateLocalOffset(
                -0.37 * scale,
                1.08 * scale,
                -armSwing,
                yaw
        );

        Vec3d rightLeg = rotateLocalOffset(
                0.145 * scale,
                0.38 * scale,
                -legSwing,
                yaw
        );

        Vec3d leftLeg = rotateLocalOffset(
                -0.145 * scale,
                0.38 * scale,
                legSwing,
                yaw
        );

        int outerColor = withAlpha(
                color,
                MathHelper.clamp(
                        (int) (26f * alpha),
                        0,
                        255
                )
        );

        int middleColor = withAlpha(
                lightenColor(
                        color,
                        1.12f
                ),
                MathHelper.clamp(
                        (int) (42f * alpha),
                        0,
                        255
                )
        );

        int innerColor = withAlpha(
                lightenColor(
                        color,
                        1.24f
                ),
                MathHelper.clamp(
                        (int) (58f * alpha),
                        0,
                        255
                )
        );

        renderSandevistanAuraLayer(
                root,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg,
                yaw,
                scale,
                1.24f,
                outerColor
        );

        renderSandevistanAuraLayer(
                root,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg,
                yaw,
                scale,
                1.14f,
                middleColor
        );

        renderSandevistanAuraLayer(
                root,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg,
                yaw,
                scale,
                1.07f,
                innerColor
        );
    }

    private void renderSandevistanAuraLayer(
            Vec3d root,
            Vec3d rightArm,
            Vec3d leftArm,
            Vec3d rightLeg,
            Vec3d leftLeg,
            float yaw,
            float scale,
            float expansion,
            int color
    ) {
        drawHumanoidGlowPart(
                root.add(
                        0.0,
                        1.55 * scale,
                        0.0
                ),
                0.46f * scale * expansion,
                0.46f * scale * expansion,
                0.46f * scale * expansion,
                yaw,
                color
        );

        drawHumanoidGlowPart(
                root.add(
                        0.0,
                        1.08 * scale,
                        0.0
                ),
                0.54f * scale * expansion,
                0.68f * scale * expansion,
                0.28f * scale * expansion,
                yaw,
                color
        );

        drawHumanoidGlowPart(
                root.add(rightArm),
                0.18f * scale * expansion,
                0.68f * scale * expansion,
                0.22f * scale * expansion,
                yaw,
                color
        );

        drawHumanoidGlowPart(
                root.add(leftArm),
                0.18f * scale * expansion,
                0.68f * scale * expansion,
                0.22f * scale * expansion,
                yaw,
                color
        );

        drawHumanoidGlowPart(
                root.add(rightLeg),
                0.23f * scale * expansion,
                0.74f * scale * expansion,
                0.25f * scale * expansion,
                yaw,
                color
        );

        drawHumanoidGlowPart(
                root.add(leftLeg),
                0.23f * scale * expansion,
                0.74f * scale * expansion,
                0.25f * scale * expansion,
                yaw,
                color
        );
    }

    private void drawHumanoidGlowPart(
            Vec3d center,
            float width,
            float height,
            float depth,
            float yaw,
            int color
    ) {
        Vec3d[] vertices = createOrientedBoxVertices(
                center,
                width,
                height,
                depth,
                yaw
        );

        int[][] faces = {
                {0, 1, 2, 3},
                {4, 7, 6, 5},
                {0, 4, 5, 1},
                {1, 5, 6, 2},
                {2, 6, 7, 3},
                {3, 7, 4, 0}
        };

        for (int[] face : faces) {
            Render3D.drawQuad(
                    vertices[face[0]],
                    vertices[face[1]],
                    vertices[face[2]],
                    vertices[face[3]],
                    color,
                    false
            );
        }
    }

    private void renderHumanoidAfterImage(
            SandevistanSnapshot snapshot,
            float targetHeight,
            int fillColor,
            int trailIndex
    ) {
        float scale =
                MathHelper.clamp(
                        targetHeight / 1.8f,
                        0.82f,
                        1.25f
                );

        double time =
                snapshot.createdAt
                        * 0.012;

        double swing =
                Math.sin(
                        time
                                + trailIndex * 0.48
                );

        double armSwing =
                swing * 0.095 * scale;

        double legSwing =
                swing * 0.075 * scale;

        Vec3d root =
                snapshot.position;

        float yaw =
                snapshot.yaw;

        drawHumanoidPart(
                root.add(
                        0.0,
                        1.55 * scale,
                        0.0
                ),
                0.46f * scale,
                0.46f * scale,
                0.46f * scale,
                yaw,
                fillColor
        );

        drawHumanoidPart(
                root.add(
                        0.0,
                        1.08 * scale,
                        0.0
                ),
                0.54f * scale,
                0.68f * scale,
                0.28f * scale,
                yaw,
                fillColor
        );

        Vec3d rightArm =
                rotateLocalOffset(
                        0.37 * scale,
                        1.08 * scale,
                        armSwing,
                        yaw
                );

        Vec3d leftArm =
                rotateLocalOffset(
                        -0.37 * scale,
                        1.08 * scale,
                        -armSwing,
                        yaw
                );

        drawHumanoidPart(
                root.add(rightArm),
                0.18f * scale,
                0.68f * scale,
                0.22f * scale,
                yaw,
                fillColor
        );

        drawHumanoidPart(
                root.add(leftArm),
                0.18f * scale,
                0.68f * scale,
                0.22f * scale,
                yaw,
                fillColor
        );

        Vec3d rightLeg =
                rotateLocalOffset(
                        0.145 * scale,
                        0.38 * scale,
                        -legSwing,
                        yaw
                );

        Vec3d leftLeg =
                rotateLocalOffset(
                        -0.145 * scale,
                        0.38 * scale,
                        legSwing,
                        yaw
                );

        drawHumanoidPart(
                root.add(rightLeg),
                0.23f * scale,
                0.74f * scale,
                0.25f * scale,
                yaw,
                fillColor
        );

        drawHumanoidPart(
                root.add(leftLeg),
                0.23f * scale,
                0.74f * scale,
                0.25f * scale,
                yaw,
                fillColor
        );
    }

    private Vec3d rotateLocalOffset(
            double x,
            double y,
            double z,
            float yaw
    ) {
        double radians =
                Math.toRadians(
                        -yaw
                );

        double cos =
                Math.cos(radians);

        double sin =
                Math.sin(radians);

        return new Vec3d(
                x * cos - z * sin,
                y,
                x * sin + z * cos
        );
    }

    private void drawHumanoidPart(
            Vec3d center,
            float width,
            float height,
            float depth,
            float yaw,
            int fillColor
    ) {
        Vec3d[] vertices =
                createOrientedBoxVertices(
                        center,
                        width,
                        height,
                        depth,
                        yaw
                );

        int[][] faces = {
                {0, 1, 2, 3},
                {4, 7, 6, 5},
                {0, 4, 5, 1},
                {1, 5, 6, 2},
                {2, 6, 7, 3},
                {3, 7, 4, 0}
        };

        for (int[] face : faces) {
            Render3D.drawQuad(
                    vertices[face[0]],
                    vertices[face[1]],
                    vertices[face[2]],
                    vertices[face[3]],
                    fillColor,
                    false
            );
        }

    }

    private Vec3d[] createOrientedBoxVertices(
            Vec3d center,
            float width,
            float height,
            float depth,
            float yaw
    ) {
        double halfWidth =
                width * 0.5;

        double halfHeight =
                height * 0.5;

        double halfDepth =
                depth * 0.5;

        double[][] local = {
                {-halfWidth, -halfHeight, -halfDepth},
                {halfWidth, -halfHeight, -halfDepth},
                {halfWidth, halfHeight, -halfDepth},
                {-halfWidth, halfHeight, -halfDepth},
                {-halfWidth, -halfHeight, halfDepth},
                {halfWidth, -halfHeight, halfDepth},
                {halfWidth, halfHeight, halfDepth},
                {-halfWidth, halfHeight, halfDepth}
        };

        Vec3d[] result =
                new Vec3d[8];

        double radians =
                Math.toRadians(
                        -yaw
                );

        double cos =
                Math.cos(radians);

        double sin =
                Math.sin(radians);

        for (int i = 0; i < local.length; i++) {
            double x =
                    local[i][0];

            double y =
                    local[i][1];

            double z =
                    local[i][2];

            double rotatedX =
                    x * cos
                            - z * sin;

            double rotatedZ =
                    x * sin
                            + z * cos;

            result[i] =
                    center.add(
                            rotatedX,
                            y,
                            rotatedZ
                    );
        }

        return result;
    }

    private static class SandevistanSnapshot {
        private final Vec3d position;
        private final float yaw;
        private final long createdAt;

        private SandevistanSnapshot(
                Vec3d position,
                float yaw,
                long createdAt
        ) {
            this.position = position;
            this.yaw = yaw;
            this.createdAt = createdAt;
        }
    }

    private void renderTesla(
            MatrixStack stack,
            VertexConsumerProvider provider,
            LivingEntity target,
            float alpha,
            float deltaTime
    ) {
        long now = System.currentTimeMillis();
        double seconds = (now % 120000L) / 1000.0;
        double speed = teslaSpeed.getValue();
        double radius = teslaRadius.getValue();

        int c1 = color1.getColor();
        int c2 = color2.getColor();
        int c3 = color3.getColor();

        if (hurtProgress > 0f) {
            c1 = lerpColor(c1, 0xFFFF3A3A, hurtProgress * 0.62f);
            c2 = lerpColor(c2, 0xFFFF3A3A, hurtProgress * 0.62f);
            c3 = lerpColor(c3, 0xFFFF3A3A, hurtProgress * 0.62f);
        }

        int[] colors = {c1, c2, c3};

        Vec3d targetWorld = smoothedPos != null
                ? smoothedPos
                : target.getLerpedPos(1f);

        double bodyCenterY = target.getHeight() * 0.50;
        Vec3d bodyCenterWorld = targetWorld.add(0.0, bodyCenterY, 0.0);

        for (int i = 0; i < 3; i++) {
            double phase = i * (Math.PI * 2.0 / 3.0);

            double orbit =
                    seconds
                            * (0.95 + i * 0.075)
                            * speed
                            + phase;

            double slowDrift =
                    seconds
                            * (0.46 + i * 0.035)
                            * speed
                            + phase * 0.71;

            double breathe =
                    0.91
                            + Math.sin(seconds * 1.8 + phase) * 0.055
                            + Math.sin(seconds * 3.4 + phase * 1.7) * 0.022;

            double currentRadius =
                    radius * breathe;

            double x =
                    Math.cos(orbit)
                            * currentRadius
                            + Math.sin(slowDrift * 1.4) * 0.10;

            double z =
                    Math.sin(orbit)
                            * currentRadius
                            + Math.cos(slowDrift * 1.15) * 0.10;

            double y =
                    bodyCenterY
                            + Math.sin(orbit * 1.17 + phase * 0.35) * 0.34
                            + Math.sin(slowDrift * 2.05) * 0.09;

            if (i == 1) {
                double rotatedX = x * 0.82 - yOffset(y, bodyCenterY) * 0.23;
                double rotatedY = bodyCenterY + yOffset(y, bodyCenterY) * 0.86 + x * 0.13;
                x = rotatedX;
                y = rotatedY;
            } else if (i == 2) {
                double rotatedZ = z * 0.82 + yOffset(y, bodyCenterY) * 0.20;
                double rotatedY = bodyCenterY + yOffset(y, bodyCenterY) * 0.88 - z * 0.12;
                z = rotatedZ;
                y = rotatedY;
            }

            double micro =
                    0.018
                            + 0.010
                            * Math.sin(seconds * 5.7 + phase);

            x += Math.sin(seconds * 6.9 + phase * 2.1) * micro;
            y += Math.cos(seconds * 7.6 + phase * 1.4) * micro;
            z += Math.sin(seconds * 8.2 + phase * 2.8) * micro;

            Vec3d local = new Vec3d(x, y, z);
            teslaOrbWorldPositions[i] = targetWorld.add(local);

            renderTeslaOrb(
                    stack,
                    provider,
                    local,
                    colors[i],
                    alpha,
                    seconds,
                    i
            );
        }

        renderTeslaArcs(
                target,
                bodyCenterWorld,
                colors,
                alpha,
                now
        );
    }

    private double yOffset(
            double value,
            double center
    ) {
        return value - center;
    }

    private void renderTeslaOrb(
            MatrixStack stack,
            VertexConsumerProvider provider,
            Vec3d localPos,
            int baseColor,
            float alpha,
            double seconds,
            int index
    ) {
        VertexConsumer glowConsumer = provider.getBuffer(
                ClientPipelines.BLOOM_ESP.apply(
                        Identifier.of("mind", "images/particle/glow.png")
                )
        );

        float pulse =
                1f
                        + (float) Math.sin(seconds * (4.6 + index * 0.27) + index * 1.9) * 0.075f
                        + (float) Math.sin(seconds * 9.4 + index * 0.7) * 0.025f;

        float flicker =
                0.90f
                        + 0.10f
                        * (0.5f
                        + 0.5f
                        * (float) Math.sin(seconds * 11.5 + index * 4.1));

        stack.push();
        stack.translate(
                localPos.x,
                localPos.y,
                localPos.z
        );
        stack.multiply(
                mc.gameRenderer
                        .getCamera()
                        .getRotation()
        );

        drawTeslaBillboard(
                stack,
                glowConsumer,
                0.66f * pulse,
                withAlpha(
                        baseColor,
                        (int) (58f * alpha * flicker)
                )
        );

        drawTeslaBillboard(
                stack,
                glowConsumer,
                0.46f * pulse,
                withAlpha(
                        baseColor,
                        (int) (128f * alpha * flicker)
                )
        );

        drawTeslaBillboard(
                stack,
                glowConsumer,
                0.28f * pulse,
                withAlpha(
                        lightenColor(baseColor, 1.38f),
                        (int) (220f * alpha * flicker)
                )
        );

        drawTeslaBillboard(
                stack,
                glowConsumer,
                0.13f * pulse,
                withAlpha(
                        0xFFFFFFFF,
                        (int) (255f * alpha * flicker)
                )
        );

        stack.pop();

        renderTeslaOrbSparks(
                localPos,
                baseColor,
                alpha,
                seconds,
                index
        );
    }

    private void drawTeslaBillboard(
            MatrixStack stack,
            VertexConsumer consumer,
            float size,
            int color
    ) {
        Matrix4f matrix =
                stack.peek()
                        .getPositionMatrix();

        float half =
                size * 0.5f;

        consumer.vertex(
                        matrix,
                        -half,
                        -half,
                        0f
                )
                .texture(0f, 1f)
                .color(color);

        consumer.vertex(
                        matrix,
                        half,
                        -half,
                        0f
                )
                .texture(1f, 1f)
                .color(color);

        consumer.vertex(
                        matrix,
                        half,
                        half,
                        0f
                )
                .texture(1f, 0f)
                .color(color);

        consumer.vertex(
                        matrix,
                        -half,
                        half,
                        0f
                )
                .texture(0f, 0f)
                .color(color);
    }

    private void renderTeslaOrbSparks(
            Vec3d localPos,
            int color,
            float alpha,
            double seconds,
            int index
    ) {
        if (smoothedPos == null) {
            return;
        }

        Vec3d worldCenter =
                smoothedPos.add(localPos);

        int sparkColor =
                withAlpha(
                        lightenColor(
                                color,
                                1.35f
                        ),
                        (int) (92f * alpha)
                );

        for (int i = 0; i < 2; i++) {
            double phase =
                    index * 2.31
                            + i * 2.7;

            double length =
                    0.065
                            + 0.028
                            * (
                            0.5
                                    + 0.5
                                    * Math.sin(
                                    seconds * 5.8
                                            + phase
                            )
                    );

            double dx =
                    Math.sin(
                            seconds * 6.1
                                    + phase
                    )
                            * length;

            double dy =
                    Math.cos(
                            seconds * 6.8
                                    + phase * 1.2
                    )
                            * length;

            double dz =
                    Math.sin(
                            seconds * 7.3
                                    + phase * 1.6
                    )
                            * length;

            Render3D.drawLine(
                    worldCenter,
                    worldCenter.add(
                            dx,
                            dy,
                            dz
                    ),
                    sparkColor,
                    0.38f,
                    false
            );
        }
    }

    private void renderTeslaArcs(
            LivingEntity target,
            Vec3d bodyCenterWorld,
            int[] colors,
            float alpha,
            long now
    ) {
        if (
                teslaOrbWorldPositions[0] == null
                        || teslaOrbWorldPositions[1] == null
                        || teslaOrbWorldPositions[2] == null
        ) {
            return;
        }

        float width =
                teslaArcWidth.getValue();

        renderTeslaArc(
                teslaOrbWorldPositions[0],
                teslaOrbWorldPositions[1],
                colors[0],
                colors[1],
                alpha * 0.72f,
                width * 0.62f,
                0,
                now
        );

        renderTeslaArc(
                teslaOrbWorldPositions[1],
                teslaOrbWorldPositions[2],
                colors[1],
                colors[2],
                alpha * 0.72f,
                width * 0.62f,
                1,
                now
        );

        renderTeslaArc(
                teslaOrbWorldPositions[2],
                teslaOrbWorldPositions[0],
                colors[2],
                colors[0],
                alpha * 0.72f,
                width * 0.62f,
                2,
                now
        );

        double height =
                target.getHeight();

        double halfWidth =
                target.getWidth()
                        * 0.23;

        Vec3d[] anchors = {
                bodyCenterWorld.add(
                        0.0,
                        height * 0.31,
                        0.0
                ),
                bodyCenterWorld.add(
                        halfWidth,
                        -height * 0.03,
                        0.0
                ),
                bodyCenterWorld.add(
                        -halfWidth,
                        -height * 0.26,
                        0.0
                )
        };

        for (int i = 0; i < 3; i++) {
            renderTeslaArc(
                    teslaOrbWorldPositions[i],
                    anchors[i],
                    colors[i],
                    0xFFFFFFFF,
                    alpha,
                    width,
                    3 + i,
                    now
            );
        }

        long flicker =
                now / 135L;

        if (
                flicker % 3L == 0L
        ) {
            int from =
                    (int) (
                            Math.abs(flicker)
                                    % 3L
                    );

            int to =
                    (from + 2)
                            % 3;

            renderTeslaArc(
                    teslaOrbWorldPositions[from],
                    anchors[to],
                    colors[from],
                    colors[to],
                    alpha * 0.40f,
                    width * 0.52f,
                    7 + from,
                    now
            );
        }
    }

    private void renderTeslaArc(
            Vec3d start,
            Vec3d end,
            int startColor,
            int endColor,
            float alpha,
            float width,
            int seedOffset,
            long now
    ) {
        int segments = 15;

        long seed =
                now / 42L
                        + seedOffset * 131L;

        Vec3d direction =
                end.subtract(start);

        double length =
                direction.length();

        if (length <= 0.0001) {
            return;
        }

        Vec3d normalized =
                direction.normalize();

        Vec3d reference =
                Math.abs(normalized.y) < 0.92
                        ? new Vec3d(
                        0,
                        1,
                        0
                )
                        : new Vec3d(
                        1,
                        0,
                        0
                );

        Vec3d side =
                normalized
                        .crossProduct(reference)
                        .normalize();

        Vec3d up =
                normalized
                        .crossProduct(side)
                        .normalize();

        double arcTime =
                (now % 60000L)
                        / 1000.0;

        double bowSide =
                Math.sin(
                        arcTime * 2.1
                                + seedOffset * 1.7
                )
                        * Math.min(
                        0.045,
                        length * 0.022
                );

        double bowUp =
                Math.cos(
                        arcTime * 1.75
                                + seedOffset * 2.3
                )
                        * Math.min(
                        0.035,
                        length * 0.018
                );

        Vec3d previous =
                start;

        for (int i = 1; i <= segments; i++) {
            float t =
                    i / (float) segments;

            Vec3d point =
                    start.lerp(
                            end,
                            t
                    );

            if (i < segments) {
                double envelope =
                        Math.sin(
                                Math.PI * t
                        );

                double amplitude =
                        Math.min(
                                0.105,
                                0.025
                                        + length * 0.032
                        )
                                * envelope;

                double fineAmplitude =
                        amplitude
                                * (
                                i % 2 == 0
                                        ? 0.82
                                        : 1.0
                        );

                double n1 =
                        hashSigned(
                                seed
                                        + i * 17L
                        );

                double n2 =
                        hashSigned(
                                seed
                                        + i * 47L
                        );

                point =
                        point
                                .add(
                                        side.multiply(
                                                n1
                                                        * fineAmplitude
                                                        + bowSide
                                                        * envelope
                                        )
                                )
                                .add(
                                        up.multiply(
                                                n2
                                                        * fineAmplitude
                                                        * 0.78
                                                        + bowUp
                                                        * envelope
                                        )
                                );
            }

            int cA =
                    lerpColor(
                            startColor,
                            endColor,
                            (i - 1)
                                    / (float) segments
                    );

            int cB =
                    lerpColor(
                            startColor,
                            endColor,
                            t
                    );

            Render3D.drawLineGradient(
                    previous,
                    point,
                    withAlpha(
                            cA,
                            (int) (24f * alpha)
                    ),
                    withAlpha(
                            cB,
                            (int) (24f * alpha)
                    ),
                    Math.max(
                            0.35f,
                            width * 1.65f
                    ),
                    false
            );

            Render3D.drawLineGradient(
                    previous,
                    point,
                    withAlpha(
                            lightenColor(
                                    cA,
                                    1.36f
                            ),
                            (int) (190f * alpha)
                    ),
                    withAlpha(
                            lightenColor(
                                    cB,
                                    1.36f
                            ),
                            (int) (205f * alpha)
                    ),
                    Math.max(
                            0.28f,
                            width * 0.62f
                    ),
                    false
            );

            previous =
                    point;
        }
    }

    private double hashSigned(long value) {
        long x = value;

        x ^= x >> 33;
        x *= 0xff51afd7ed558ccdl;
        x ^= x >> 33;
        x *= 0xc4ceb9fe1a85ec53l;
        x ^= x >> 33;

        long positive =
                x & 0x7fffffffffffffffL;

        double normalized =
                positive
                        / (double) Long.MAX_VALUE;

        return normalized * 2.0 - 1.0;
    }

    private void createCrystals(Entity target) {
        crystalList.clear();
        crystalList.add(new Crystal(new Vec3d(0, 0.85, 0.8), new Vec3d(-49, 0, 40)));
        crystalList.add(new Crystal(new Vec3d(0.2, 0.85, -0.675), new Vec3d(35, 0, -30)));
        crystalList.add(new Crystal(new Vec3d(0.6, 1.35, 0.6), new Vec3d(-30, 0, 35)));
        crystalList.add(new Crystal(new Vec3d(-0.74, 1.05, 0.4), new Vec3d(-25, 0, -30)));
        crystalList.add(new Crystal(new Vec3d(0.74, 0.95, -0.4), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.475, 0.85, -0.375), new Vec3d(30, 0, -25)));
        crystalList.add(new Crystal(new Vec3d(0, 1.35, -0.6), new Vec3d(45, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(0.85, 0.7, 0.1), new Vec3d(-30, 0, 30)));
        crystalList.add(new Crystal(new Vec3d(-0.7, 1.35, -0.3), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.3, 1.35, 0.55), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.7, 0.75, 0), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.2, 0.65, -0.7), new Vec3d(0, 0, 0)));
    }

    private void renderCrystals(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha,
                                float deltaTime) {
        if (target == null || crystalList.isEmpty()) {
            return;
        }

        rotationAngle += crystalRotationSpeed.getValue() * deltaTime;
        rotationAngle = rotationAngle % 360;

        stack.push();
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAngle));

        int baseColor = color1.getColor();
        if (hurtProgress > 0) {
            baseColor = lerpColor(baseColor, 0xFFFF0000, hurtProgress);
        }

        for (Crystal crystal : crystalList) {
            crystal.render(stack, provider, alpha, baseColor);
        }

        stack.pop();
    }

    private class Crystal {
        private final Vec3d position;
        private final Vec3d rotation;
        private final float rotationSpeed;

        public Crystal(Vec3d position, Vec3d rotation) {
            this.position = position;
            this.rotation = rotation;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
        }

        public void render(MatrixStack stack, VertexConsumerProvider provider, float alpha, int baseColor) {
            stack.push();
            stack.translate(position.x, position.y, position.z);

            float timeSeconds = (System.currentTimeMillis() % 31416) / 1000f;
            float pulsation = 1.0f + (float) (Math.sin(timeSeconds * 2f) * 0.1f);
            stack.scale(pulsation, pulsation, pulsation);

            float selfRotation = (System.currentTimeMillis() % 36000) / 100.0f * rotationSpeed;
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y + selfRotation));
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));

            float userAlpha = 0.3f;

            VertexConsumer filledConsumer = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
            drawFilledCrystal(stack, filledConsumer, baseColor, userAlpha * 0.85f, alpha);

            VertexConsumer glowConsumer = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);
            stack.push();
            stack.scale(1.15f, 1.15f, 1.15f);
            drawFilledCrystal(stack, glowConsumer, baseColor, userAlpha * 0.25f, alpha);
            stack.pop();

            stack.push();
            stack.scale(1.3f, 1.3f, 1.3f);
            drawFilledCrystal(stack, glowConsumer, baseColor, userAlpha * 0.1f, alpha);
            stack.pop();

            drawBloomEffect(stack, provider, baseColor, alpha);

            stack.pop();
        }

        private void drawFilledCrystal(MatrixStack stack, VertexConsumer consumer, int baseColor, float alphaMultiplier,
                                       float anim) {
            float s = 0.05f;
            float h_prism = s * 1.0f;
            float h_pyramid = s * 1.5f;
            int numSides = 8;

            List<Vector3f> topVertices = new ArrayList<>();
            List<Vector3f> bottomVertices = new ArrayList<>();

            for (int i = 0; i < numSides; i++) {
                float angle = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(angle));
                float z = (float) (s * Math.sin(angle));
                topVertices.add(new Vector3f(x, h_prism / 2, z));
                bottomVertices.add(new Vector3f(x, -h_prism / 2, z));
            }

            Vector3f vTop = new Vector3f(0, h_prism / 2 + h_pyramid, 0);
            Vector3f vBottom = new Vector3f(0, -h_prism / 2 - h_pyramid, 0);

            int finalAlpha = (int) (alphaMultiplier * 255 * anim);
            int finalColor = withAlpha(baseColor, finalAlpha);
            int darkerColor = withAlpha(darkenColor(baseColor, 0.7f), finalAlpha);
            int lighterColor = withAlpha(lightenColor(baseColor, 1.2f), finalAlpha);

            Matrix4f matrix = stack.peek().getPositionMatrix();

            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = bottomVertices.get(i);
                Vector3f v2 = bottomVertices.get((i + 1) % numSides);
                Vector3f v3 = topVertices.get((i + 1) % numSides);
                Vector3f v4 = topVertices.get(i);

                int sideColor = (i % 2 == 0) ? finalColor : darkerColor;
                drawQuadFilled(matrix, consumer, v1, v2, v3, v4, sideColor);
            }

            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = topVertices.get(i);
                Vector3f v2 = topVertices.get((i + 1) % numSides);

                int pyramidColor = (i % 2 == 0) ? lighterColor : finalColor;
                drawTriangleFilled(matrix, consumer, vTop, v2, v1, pyramidColor);
            }

            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = bottomVertices.get(i);
                Vector3f v2 = bottomVertices.get((i + 1) % numSides);

                int pyramidColor = (i % 2 == 0) ? darkerColor : finalColor;
                drawTriangleFilled(matrix, consumer, vBottom, v1, v2, pyramidColor);
            }
        }

        private void drawTriangleFilled(Matrix4f matrix, VertexConsumer consumer, Vector3f v1, Vector3f v2, Vector3f v3,
                                        int color) {
            consumer.vertex(matrix, v1.x, v1.y, v1.z).color(color);
            consumer.vertex(matrix, v2.x, v2.y, v2.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
        }

        private void drawQuadFilled(Matrix4f matrix, VertexConsumer consumer, Vector3f v1, Vector3f v2, Vector3f v3,
                                    Vector3f v4, int color) {
            consumer.vertex(matrix, v1.x, v1.y, v1.z).color(color);
            consumer.vertex(matrix, v2.x, v2.y, v2.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
            consumer.vertex(matrix, v4.x, v4.y, v4.z).color(color);
        }

        private void drawBloomEffect(MatrixStack stack, VertexConsumerProvider provider, int baseColor, float anim) {
            VertexConsumer bloomConsumer = provider
                    .getBuffer(ClientPipelines.BLOOM_ESP.apply(Identifier.of("mind", "images/particle/glow.png")));

            int bloomAlpha = (int) (0.3f * 60 * anim);
            int bloomColor = withAlpha(baseColor, bloomAlpha);
            float bloomSize = 0.05f * 15.0f;

            Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
            int segments = 6;

            for (int i = 0; i < segments; i++) {
                stack.push();
                float angle = (360.0f / segments) * i;
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                stack.multiply(camRot);

                Matrix4f matrix = stack.peek().getPositionMatrix();

                bloomConsumer.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bloomConsumer.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);

                stack.pop();
            }

            for (int i = 0; i < segments; i++) {
                stack.push();
                float angle = (360.0f / segments) * i;
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                stack.multiply(camRot);

                Matrix4f matrix = stack.peek().getPositionMatrix();

                bloomConsumer.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bloomConsumer.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);

                stack.pop();
            }
        }
    }

    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getNextColor(int colorIndex) {
        return switch (colorIndex) {
            case 0 -> color2.getColor();
            case 1 -> color3.getColor();
            default -> color1.getColor();
        };
    }

    private int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @FunctionalInterface
    private interface Transformation {
        Vec3d make(double sin, double cos);
    }
}
