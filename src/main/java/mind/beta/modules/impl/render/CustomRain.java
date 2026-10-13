/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CustomRain extends ModuleStructure {

    public static CustomRain getInstance() {
        return Instance.get(CustomRain.class);
    }

    private final SliderSettings density = new SliderSettings(
            "Density",
            "Number of raindrops around the player"
    )
            .range(60f, 520f)
            .setValue(260f);

    private final SliderSettings radius = new SliderSettings(
            "Radius",
            "Rain rendering radius around the player"
    )
            .range(8f, 34f)
            .setValue(19f);

    private final SliderSettings spawnHeight = new SliderSettings(
            "Height",
            "Raindrop spawn height"
    )
            .range(7f, 26f)
            .setValue(16f);

    private final SliderSettings speed = new SliderSettings(
            "Speed",
            "Rain fall speed"
    )
            .range(0.35f, 2.2f)
            .setValue(1.0f);

    private final SliderSettings length = new SliderSettings(
            "Length",
            "Rain streak length"
    )
            .range(0.18f, 1.6f)
            .setValue(0.72f);

    private final SliderSettings width = new SliderSettings(
            "Thickness",
            "Raindrop thickness"
    )
            .range(0.6f, 2.4f)
            .setValue(1.05f);

    private final SliderSettings alpha = new SliderSettings(
            "Opacity",
            "Rain brightness"
    )
            .range(0.15f, 1.0f)
            .setValue(0.68f);

    private final SliderSettings wind = new SliderSettings(
            "Wind",
            "Horizontal rain drift"
    )
            .range(-1.0f, 1.0f)
            .setValue(0.16f);

    private final BooleanSetting splashes = new BooleanSetting(
            "Splashes",
            "Draw small splashes when raindrops hit a surface"
    )
            .setValue(true);

    private final BooleanSetting onlyWeather = new BooleanSetting(
            "Only During Rain",
            "Show the effect only when it is raining in the world"
    )
            .setValue(false);

    private final BooleanSetting depth = new BooleanSetting(
            "Depth",
            "Hide raindrops behind blocks"
    )
            .setValue(true);

    private final List<RainDrop> drops = new ArrayList<>();
    private final List<Splash> splashParticles = new ArrayList<>();

    private long lastFrameNanos;
    private Vec3d lastPlayerPos = Vec3d.ZERO;

    public CustomRain() {
        super(
                "CustomRain",
                "Custom atmospheric 3D rain",
                ModuleCategory.RENDER
        );

        settings(
                density,
                radius,
                spawnHeight,
                speed,
                length,
                width,
                alpha,
                wind,
                splashes,
                onlyWeather,
                depth
        );
    }

    @Override
    public void activate() {
        drops.clear();
        splashParticles.clear();
        lastFrameNanos = System.nanoTime();

        if (mc.player != null) {
            lastPlayerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            rebuildDrops();
        }
    }

    @Override
    public void deactivate() {
        drops.clear();
        splashParticles.clear();
        lastFrameNanos = 0L;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (onlyWeather.isValue() && !mc.world.isRaining()) {
            return;
        }

        long now = System.nanoTime();

        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }

        float delta =
                Math.min(
                        0.05f,
                        Math.max(
                                0.001f,
                                (now - lastFrameNanos) / 1_000_000_000.0f
                        )
                );

        lastFrameNanos = now;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        updatePool(playerPos);
        updateDrops(playerPos, delta);
        updateSplashes(delta);
        renderRain(playerPos);
        renderSplashes();

        lastPlayerPos = playerPos;
    }

    private void updatePool(Vec3d playerPos) {
        int targetCount =
                Math.max(
                        0,
                        Math.round(density.getValue())
                );

        while (drops.size() < targetCount) {
            drops.add(
                    createDrop(
                            playerPos,
                            true
                    )
            );
        }

        while (drops.size() > targetCount) {
            drops.remove(drops.size() - 1);
        }
    }

    private void rebuildDrops() {
        if (mc.player == null) {
            return;
        }

        drops.clear();

        int count =
                Math.max(
                        0,
                        Math.round(density.getValue())
                );

        Vec3d center = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (int i = 0; i < count; i++) {
            drops.add(
                    createDrop(
                            center,
                            true
                    )
            );
        }
    }

    private RainDrop createDrop(
            Vec3d center,
            boolean scatterY
    ) {
        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        float currentRadius =
                radius.getValue();

        double angle =
                random.nextDouble(
                        0.0,
                        Math.PI * 2.0
                );

        double distance =
                Math.sqrt(random.nextDouble())
                        * currentRadius;

        double x =
                center.x
                        + Math.cos(angle) * distance;

        double z =
                center.z
                        + Math.sin(angle) * distance;

        double minY =
                center.y - 3.5;

        double maxY =
                center.y + spawnHeight.getValue();

        double y =
                scatterY
                        ? random.nextDouble(
                        minY,
                        Math.max(
                                minY + 0.1,
                                maxY
                        )
                )
                        : maxY
                        + random.nextDouble(
                        0.0,
                        3.5
                );

        float speedMultiplier =
                random.nextFloat(
                        0.78f,
                        1.28f
                );

        float lengthMultiplier =
                random.nextFloat(
                        0.72f,
                        1.35f
                );

        float brightness =
                random.nextFloat(
                        0.62f,
                        1.0f
                );

        float localWind =
                random.nextFloat(
                        0.82f,
                        1.18f
                );

        return new RainDrop(
                x,
                y,
                z,
                speedMultiplier,
                lengthMultiplier,
                brightness,
                localWind,
                random.nextFloat(
                        0f,
                        (float) (Math.PI * 2.0)
                )
        );
    }

    private void respawnDrop(
            RainDrop drop,
            Vec3d center
    ) {
        RainDrop replacement =
                createDrop(
                        center,
                        false
                );

        drop.x = replacement.x;
        drop.y = replacement.y;
        drop.z = replacement.z;
        drop.speedMultiplier = replacement.speedMultiplier;
        drop.lengthMultiplier = replacement.lengthMultiplier;
        drop.brightness = replacement.brightness;
        drop.localWind = replacement.localWind;
        drop.phase = replacement.phase;
        drop.collisionTimer = 0f;
    }

    private void updateDrops(
            Vec3d playerPos,
            float delta
    ) {
        float fallSpeed =
                19.0f
                        * speed.getValue();

        float windStrength =
                wind.getValue();

        double maxDistance =
                radius.getValue()
                        + 4.0;

        double maxDistanceSq =
                maxDistance
                        * maxDistance;

        double lowerLimit =
                playerPos.y - 7.0;

        for (RainDrop drop : drops) {
            float sway =
                    (float) Math.sin(
                            System.nanoTime()
                                    * 0.0000000018
                                    + drop.phase
                    )
                            * 0.035f;

            double driftX =
                    (windStrength
                            * 4.2f
                            * drop.localWind
                            + sway)
                            * delta;

            double driftZ =
                    (
                            windStrength
                                    * 1.35f
                                    * drop.localWind
                                    + sway * 0.45f
                    )
                            * delta;

            drop.x += driftX;
            drop.z += driftZ;

            drop.y -=
                    fallSpeed
                            * drop.speedMultiplier
                            * delta;

            drop.collisionTimer -= delta;

            double dx =
                    drop.x - playerPos.x;

            double dz =
                    drop.z - playerPos.z;

            if (drop.y < lowerLimit
                    || dx * dx + dz * dz > maxDistanceSq) {
                respawnDrop(
                        drop,
                        playerPos
                );
                continue;
            }

            if (drop.collisionTimer <= 0f) {
                drop.collisionTimer =
                        0.045f
                                + ThreadLocalRandom.current()
                                .nextFloat(
                                        0f,
                                        0.035f
                                );

                if (checkCollision(drop)) {
                    if (splashes.isValue()) {
                        spawnSplash(drop);
                    }

                    respawnDrop(
                            drop,
                            playerPos
                    );
                }
            }
        }
    }

    private boolean checkCollision(RainDrop drop) {
        BlockPos pos =
                BlockPos.ofFloored(
                        drop.x,
                        drop.y,
                        drop.z
                );

        BlockState state =
                mc.world.getBlockState(pos);

        return !state.isAir()
                && !state.getCollisionShape(
                mc.world,
                pos
        ).isEmpty();
    }

    private void spawnSplash(RainDrop drop) {
        BlockPos blockPos =
                BlockPos.ofFloored(
                        drop.x,
                        drop.y,
                        drop.z
                );

        double surfaceY =
                blockPos.getY() + 1.01;

        splashParticles.add(
                new Splash(
                        drop.x,
                        surfaceY,
                        drop.z,
                        ThreadLocalRandom.current()
                                .nextFloat(
                                        0f,
                                        (float) (Math.PI * 2.0)
                                )
                )
        );

        if (splashParticles.size() > 100) {
            splashParticles.remove(0);
        }
    }

    private void updateSplashes(float delta) {
        Iterator<Splash> iterator =
                splashParticles.iterator();

        while (iterator.hasNext()) {
            Splash splash =
                    iterator.next();

            splash.age += delta;

            if (splash.age >= Splash.LIFETIME) {
                iterator.remove();
            }
        }
    }

    private void renderRain(Vec3d playerPos) {
        float baseAlpha =
                alpha.getValue();

        float baseLength =
                length.getValue();

        float baseWidth =
                width.getValue();

        float windStrength =
                wind.getValue();

        float currentRadius =
                Math.max(
                        1f,
                        radius.getValue()
                );

        boolean useDepth =
                depth.isValue();

        for (RainDrop drop : drops) {
            double dx =
                    drop.x - playerPos.x;

            double dz =
                    drop.z - playerPos.z;

            float horizontalDistance =
                    (float) Math.sqrt(
                            dx * dx + dz * dz
                    );

            float distanceFade =
                    1f
                            - clamp01(
                            horizontalDistance
                                    / currentRadius
                    );

            distanceFade =
                    0.18f
                            + distanceFade
                            * 0.82f;

            float finalAlpha =
                    baseAlpha
                            * drop.brightness
                            * distanceFade;

            if (finalAlpha <= 0.015f) {
                continue;
            }

            float streakLength =
                    baseLength
                            * drop.lengthMultiplier
                            * (
                            0.85f
                                    + drop.speedMultiplier
                                    * 0.22f
                    );

            double leanX =
                    -windStrength
                            * streakLength
                            * 0.34;

            double leanZ =
                    -windStrength
                            * streakLength
                            * 0.11;

            Vec3d head =
                    new Vec3d(
                            drop.x,
                            drop.y,
                            drop.z
                    );

            Vec3d tail =
                    new Vec3d(
                            drop.x + leanX,
                            drop.y + streakLength,
                            drop.z + leanZ
                    );

            int coreHead =
                    color(
                            204,
                            220,
                            238,
                            finalAlpha
                    );

            int coreTail =
                    color(
                            160,
                            190,
                            220,
                            0f
                    );

            int glowHead =
                    color(
                            170,
                            205,
                            235,
                            finalAlpha * 0.18f
                    );

            int glowTail =
                    color(
                            130,
                            175,
                            215,
                            0f
                    );

            Render3D.drawLineGradient(
                    head,
                    tail,
                    glowHead,
                    glowTail,
                    baseWidth * 2.7f,
                    useDepth
            );

            Render3D.drawLineGradient(
                    head,
                    tail,
                    coreHead,
                    coreTail,
                    baseWidth,
                    useDepth
            );
        }
    }

    private void renderSplashes() {
        if (!splashes.isValue()
                || splashParticles.isEmpty()) {
            return;
        }

        boolean useDepth =
                depth.isValue();

        for (Splash splash : splashParticles) {
            float progress =
                    clamp01(
                            splash.age
                                    / Splash.LIFETIME
                    );

            float eased =
                    1f
                            - (
                            1f - progress
                    )
                            * (
                            1f - progress
                    );

            float fade =
                    1f - progress;

            float splashRadius =
                    0.05f
                            + eased
                            * 0.17f;

            float height =
                    (float) Math.sin(
                            progress
                                    * Math.PI
                    )
                            * 0.07f;

            int color =
                    color(
                            190,
                            215,
                            238,
                            alpha.getValue()
                                    * fade
                                    * 0.52f
                    );

            int transparent =
                    color(
                            190,
                            215,
                            238,
                            0f
                    );

            for (int i = 0; i < 4; i++) {
                double angle =
                        splash.rotation
                                + i
                                * Math.PI
                                * 0.5;

                double cos =
                        Math.cos(angle);

                double sin =
                        Math.sin(angle);

                Vec3d start =
                        new Vec3d(
                                splash.x
                                        + cos * splashRadius * 0.18,
                                splash.y,
                                splash.z
                                        + sin * splashRadius * 0.18
                        );

                Vec3d end =
                        new Vec3d(
                                splash.x
                                        + cos * splashRadius,
                                splash.y + height,
                                splash.z
                                        + sin * splashRadius
                        );

                Render3D.drawLineGradient(
                        start,
                        end,
                        color,
                        transparent,
                        0.85f,
                        useDepth
                );
            }
        }
    }

    private int color(
            int red,
            int green,
            int blue,
            float alpha
    ) {
        int a =
                Math.max(
                        0,
                        Math.min(
                                255,
                                Math.round(
                                        clamp01(alpha)
                                                * 255f
                                )
                        )
                );

        return new Color(
                red,
                green,
                blue,
                a
        ).getRGB();
    }

    private float clamp01(float value) {
        return Math.max(
                0f,
                Math.min(
                        1f,
                        value
                )
        );
    }

    private static final class RainDrop {
        private double x;
        private double y;
        private double z;

        private float speedMultiplier;
        private float lengthMultiplier;
        private float brightness;
        private float localWind;
        private float phase;
        private float collisionTimer;

        private RainDrop(
                double x,
                double y,
                double z,
                float speedMultiplier,
                float lengthMultiplier,
                float brightness,
                float localWind,
                float phase
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.speedMultiplier = speedMultiplier;
            this.lengthMultiplier = lengthMultiplier;
            this.brightness = brightness;
            this.localWind = localWind;
            this.phase = phase;
        }
    }

    private static final class Splash {
        private static final float LIFETIME = 0.24f;

        private final double x;
        private final double y;
        private final double z;
        private final float rotation;

        private float age;

        private Splash(
                double x,
                double y,
                double z,
                float rotation
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
        }
    }
}
