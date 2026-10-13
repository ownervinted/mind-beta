/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.crystal;

import mind.beta.IMinecraft;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CrystalTracker implements IMinecraft {
    private final Map<Integer, Long> deadCrystals = new ConcurrentHashMap<>();
    private final Map<Integer, Attempt> attackedCrystals = new ConcurrentHashMap<>();
    private final Map<BlockPos, Attempt> awaitingPositions = new ConcurrentHashMap<>();
    private final Set<Integer> seenCrystals = ConcurrentHashMap.newKeySet();
    private long timeout = 300L;

    public void reset() {
        deadCrystals.clear();
        attackedCrystals.clear();
        awaitingPositions.clear();
        seenCrystals.clear();
    }

    public void update(long ping) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        timeout = Math.max(200L, Math.min(2000L, ping * 2L + 100L));
        deadCrystals.entrySet().removeIf(entry -> now - entry.getValue() >= timeout
                || mc.world.getEntityById(entry.getKey()) == null);
        attackedCrystals.entrySet().removeIf(entry -> now - entry.getValue().lastTime >= timeout * 5L
                || mc.world.getEntityById(entry.getKey()) == null || entry.getValue().shouldRemove());
        awaitingPositions.entrySet().removeIf(entry -> now - entry.getValue().lastTime >= timeout * 5L
                || entry.getValue().shouldRemove());
    }

    public boolean isDead(int id) {
        return deadCrystals.containsKey(id);
    }

    public void setDead(int id) {
        deadCrystals.put(id, System.currentTimeMillis());
    }

    public void onAttack(EndCrystalEntity crystal, boolean failsafe) {
        setDead(crystal.getId());
        attackedCrystals.compute(crystal.getId(), (id, attempt) -> {
            if (attempt == null) {
                return new Attempt(System.currentTimeMillis(), 1, crystal.getEntityPos());
            }
            attempt.touch(failsafe);
            return attempt;
        });
    }

    public boolean isCrystalBlocked(int id, int maxAttempts, long ping) {
        Attempt attempt = attackedCrystals.get(id);
        return attempt != null && attempt.canSetBlocked(maxAttempts, ping);
    }

    public void addAwaiting(BlockPos pos, boolean physicallyBlocked, boolean failsafe) {
        awaitingPositions.compute(pos.toImmutable(), (base, attempt) -> {
            if (attempt == null) {
                return new Attempt(System.currentTimeMillis(), 1,
                        new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5));
            }
            attempt.touch(!physicallyBlocked && failsafe);
            return attempt;
        });
    }

    public boolean isPositionBlocked(BlockPos pos, int maxAttempts, long ping) {
        Attempt attempt = awaitingPositions.get(pos);
        return attempt != null && attempt.canSetBlocked(maxAttempts, ping);
    }

    public Attempt confirmSpawn(BlockPos pos) {
        return awaitingPositions.remove(pos);
    }

    public Map<BlockPos, Attempt> getAwaitingPositions() {
        return awaitingPositions;
    }

    public boolean isAwaiting(BlockPos pos) {
        Attempt attempt = awaitingPositions.get(pos);
        return attempt != null && System.currentTimeMillis() - attempt.lastTime < timeout;
    }

    public boolean hasAwaiting() {
        return awaitingPositions.keySet().stream().anyMatch(this::isAwaiting);
    }

    public boolean markSeen(EndCrystalEntity crystal) {
        return seenCrystals.add(crystal.getId());
    }

    public void retainSeen(Set<Integer> aliveIds) {
        seenCrystals.retainAll(aliveIds);
    }

    public static final class Attempt {
        private long time;
        private long lastTime;
        private final Vec3d pos;
        private final Vec3d origin;
        private int attempts;

        Attempt(long time, int attempts, Vec3d pos) {
            this.time = time;
            this.lastTime = time;
            this.attempts = attempts;
            this.pos = pos;
            this.origin = mc.player == null ? Vec3d.ZERO : mc.player.getEntityPos();
        }

        public long getTime() {
            return time;
        }

        public Vec3d getPos() {
            return pos;
        }

        public int getAttempts() {
            return attempts;
        }

        public void addAttempt() {
            touch(true);
        }

        private void touch(boolean count) {
            time = System.currentTimeMillis();
            lastTime = time;
            if (count) {
                attempts++;
            }
        }

        public boolean shouldRemove() {
            return mc.player == null || mc.player.getEntityPos().squaredDistanceTo(origin) > 16.0;
        }

        public boolean canSetBlocked(int maxAttempts, long ping) {
            return attempts >= Math.max(1, maxAttempts);
        }
    }
}
