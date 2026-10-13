/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.crystal;

import mind.beta.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CrystalPrediction implements IMinecraft {
    private final Map<Integer, Motion> history = new HashMap<>();
    private int lastTick = Integer.MIN_VALUE;

    public void reset() {
        history.clear();
        lastTick = Integer.MIN_VALUE;
    }

    public void update() {
        if (mc.player == null || mc.world == null || lastTick == mc.player.age) {
            return;
        }
        lastTick = mc.player.age;
        Set<Integer> alive = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            alive.add(player.getId());
            Vec3d pos = player.getEntityPos();
            Motion previous = history.get(player.getId());
            Vec3d delta = previous == null ? Vec3d.ZERO : pos.subtract(previous.position());
            if (delta.lengthSquared() > 4.0) {
                delta = Vec3d.ZERO;
            }
            double confidence = 0.5;
            if (previous != null) {
                Vec3d before = new Vec3d(previous.velocity().x, 0.0, previous.velocity().z);
                Vec3d now = new Vec3d(delta.x, 0.0, delta.z);
                if (now.lengthSquared() < 1.0E-5) {
                    confidence = 0.0;
                } else if (before.lengthSquared() > 1.0E-5) {
                    confidence = MathHelper.clamp((before.normalize().dotProduct(now.normalize()) + 1.0) * 0.5, 0.0, 1.0);
                }
            }
            history.put(player.getId(), new Motion(pos, delta, confidence));
        }
        history.keySet().retainAll(alive);
    }

    public Vec3d predict(Entity entity, int ticks) {
        if (entity == null || mc.world == null) {
            return Vec3d.ZERO;
        }
        Vec3d position = entity.getEntityPos();
        if (ticks <= 0) {
            return position;
        }
        Motion motion = history.get(entity.getId());
        Vec3d velocity = entity == mc.player || motion == null ? entity.getVelocity() : motion.velocity();
        double confidence = motion == null || entity == mc.player ? 1.0 : motion.confidence();
        int horizon = Math.min(5, ticks);
        double horizontalScale = 0.35 + confidence * 0.65;
        velocity = new Vec3d(MathHelper.clamp(velocity.x, -1.0, 1.0) * horizontalScale,
                MathHelper.clamp(velocity.y, -1.5, 1.5), MathHelper.clamp(velocity.z, -1.0, 1.0) * horizontalScale);
        Box box = entity.getBoundingBox();
        boolean grounded = entity.isOnGround();
        for (int tick = 0; tick < horizon; tick++) {
            double moveY = grounded ? 0.0 : velocity.y;
            double y = clearDistance(entity, box, new Vec3d(0.0, moveY, 0.0));
            box = box.offset(0.0, y, 0.0);
            double x = clearDistance(entity, box, new Vec3d(velocity.x, 0.0, 0.0));
            box = box.offset(x, 0.0, 0.0);
            double z = clearDistance(entity, box, new Vec3d(0.0, 0.0, velocity.z));
            box = box.offset(0.0, 0.0, z);
            position = position.add(x, y, z);
            grounded = moveY < 0.0 && Math.abs(y - moveY) > 1.0E-4
                    || !mc.world.isSpaceEmpty(entity, box.offset(0.0, -0.02, 0.0));
            double nextY = grounded ? 0.0 : (velocity.y - 0.08) * 0.98;
            velocity = new Vec3d(Math.abs(x - velocity.x) > 1.0E-4 ? 0.0 : velocity.x * 0.91,
                    nextY, Math.abs(z - velocity.z) > 1.0E-4 ? 0.0 : velocity.z * 0.91);
        }
        return position;
    }

    private double clearDistance(Entity entity, Box box, Vec3d delta) {
        double distance = delta.x != 0.0 ? delta.x : delta.y != 0.0 ? delta.y : delta.z;
        if (distance == 0.0 || mc.world.isSpaceEmpty(entity, box.offset(delta))) {
            return distance;
        }
        double low = 0.0;
        double high = 1.0;
        for (int iteration = 0; iteration < 8; iteration++) {
            double middle = (low + high) * 0.5;
            if (mc.world.isSpaceEmpty(entity, box.offset(delta.multiply(middle)))) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return distance * low;
    }

    private record Motion(Vec3d position, Vec3d velocity, double confidence) {
    }
}
