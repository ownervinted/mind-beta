/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.impl.render.worldparticles.Particle;
import mind.beta.modules.impl.render.worldparticles.ParticleSpawner;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.timer.StopWatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorldParticles extends ModuleStructure {

    public static WorldParticles getInstance() {
        return Instance.get(WorldParticles.class);
    }

    final List<Particle> particles = new ArrayList<>();
    final StopWatch timer = new StopWatch();

    Vec3d lastPlayerPos = Vec3d.ZERO;
    Vec3d playerVelocity = Vec3d.ZERO;
    double playerSpeed = 0;

    public SelectSetting mode = new SelectSetting("Mode")
            .value("3D Cubes", "Crown", "Cube", "Dollar", "Heart", "Lightning", "Line", "Diamond", "Snowflake", "Star", "Star 2", "Triangle", "Glow", "Random")
            .selected("Star");

    public SliderSettings cubeCount = new SliderSettings("Count", "Number of particles")
            .range(10.0f, 500.0f)
            .setValue(100.0f);

    public SliderSettings lifeTime = new SliderSettings("Lifetime", "Lifetime in seconds")
            .range(2.0f, 60.0f)
            .setValue(10.0f);

    public SliderSettings size = new SliderSettings("Size", "Particle size")
            .range(0.1f, 1.5f)
            .setValue(1.5f);

    public SliderSettings glowSize = new SliderSettings("Glow", "Glow size")
            .range(0.1f, 5.0f)
            .setValue(3f);

    public BooleanSetting physics = new BooleanSetting("Physics", "Make particles fall downward")
            .setValue(false);

    public BooleanSetting randomColor = new BooleanSetting("Random Color", "Give each particle a random color")
            .setValue(false);

    public BooleanSetting whiteOnSpawn = new BooleanSetting("White on Spawn", "Particles start white and gradually change color")
            .setValue(true);

    public BooleanSetting whiteCenter = new BooleanSetting("White Center", "Use a white center for textured particles")
            .setValue(false)
            .visible(() -> !mode.getSelected().equals("3D Cubes"));

    public ColorSetting cubeColor = new ColorSetting("Color", "Particle color")
            .value(0xFF896148)
            .visible(() -> !randomColor.isValue());

    public WorldParticles() {
        super("WorldParticles", "Floating particles in the world", ModuleCategory.RENDER);
        settings(mode, cubeCount, lifeTime, size, glowSize, physics, randomColor, whiteOnSpawn, whiteCenter, cubeColor);
    }

    @Override
    public void deactivate() {
        particles.clear();
        lastPlayerPos = Vec3d.ZERO;
        playerVelocity = Vec3d.ZERO;
        playerSpeed = 0;
    }

    private Particle.ParticleType getParticleType() {
        String selected = mode.getSelected();
        return switch (selected) {
            case "3D Cubes" -> Particle.ParticleType.CUBE_3D;
            case "Crown" -> Particle.ParticleType.CROWN;
            case "Cube" -> Particle.ParticleType.CUBE_BLAST;
            case "Dollar" -> Particle.ParticleType.DOLLAR;
            case "Heart" -> Particle.ParticleType.HEART;
            case "Lightning" -> Particle.ParticleType.LIGHTNING;
            case "Line" -> Particle.ParticleType.LINE;
            case "Diamond" -> Particle.ParticleType.RHOMBUS;
            case "Snowflake" -> Particle.ParticleType.SNOWFLAKE;
            case "Star" -> Particle.ParticleType.STAR;
            case "Star 2" -> Particle.ParticleType.STAR_ALT;
            case "Triangle" -> Particle.ParticleType.TRIANGLE;
            case "Glow" -> Particle.ParticleType.GLOW;
            case "Random" -> Particle.ParticleType.RANDOM;
            default -> Particle.ParticleType.CUBE_3D;
        };
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d currentPos = mc.player.getEntityPos();

        if (lastPlayerPos != Vec3d.ZERO) {
            playerVelocity = currentPos.subtract(lastPlayerPos);
            playerSpeed = playerVelocity.horizontalLength();
        }
        lastPlayerPos = currentPos;

        double despawnDistSq = ParticleSpawner.getDespawnDistanceSquared();
        for (Particle p : particles) {
            if (!p.isFadingOut()) {
                double distSq = p.getHorizontalDistanceSquaredTo(currentPos);
                if (distSq > despawnDistSq) {
                    p.startFadeOut();
                }
            }
        }

        int actualDelay = ParticleSpawner.calculateSpawnDelay(playerSpeed);

        if (particles.size() < cubeCount.getValue() && timer.finished(actualDelay)) {
            int spawnCount = ParticleSpawner.calculateSpawnCount(playerSpeed, particles.size(), (int) cubeCount.getValue());
            long lifeTimeMs = (long) (lifeTime.getValue() * 1000);
            Particle.ParticleType type = getParticleType();

            for (int i = 0; i < spawnCount && particles.size() < cubeCount.getValue(); i++) {
                Particle particle = ParticleSpawner.createParticle(currentPos, playerVelocity, playerSpeed, lifeTimeMs, type);
                particle.setPhysics(physics.isValue());
                particle.setSize(size.getValue());
                particles.add(particle);
            }

            timer.reset();
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (particles.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = e.getStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        long now = System.currentTimeMillis();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();
        float rotation = (float) (now % 9000L) / 9000.0F * 360.0F;
        int baseColor = cubeColor.getColor();
        float glow = glowSize.getValue();
        boolean useRandomColor = randomColor.isValue();
        boolean useWhiteOnSpawn = whiteOnSpawn.isValue();
        boolean useWhiteCenter = whiteCenter.isValue();

        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.update(now);
            if (p.shouldRemove()) {
                iterator.remove();
            }
        }

        for (Particle p : particles) {
            double distSq = p.getDistanceSquaredTo(cameraPos);
            if (distSq < 150 * 150) {
                p.render(matrices, immediate, cameraPos, baseColor, rotation, cameraYaw, cameraPitch, glow, useRandomColor, useWhiteOnSpawn, useWhiteCenter);
            }
        }

        immediate.draw();
    }
}