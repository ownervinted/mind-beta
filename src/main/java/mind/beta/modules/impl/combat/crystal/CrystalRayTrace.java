/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.crystal;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;

@UtilityClass
public class CrystalRayTrace implements IMinecraft {

    private static final double[] FACE_SAMPLES = {-0.32, 0.0, 0.32};

    public BlockHitResult findCrystalPlaceHit(BlockPos base, double range) {
        return findFaceHit(base, Direction.UP, range);
    }

    public BlockHitResult findCrystalPlaceHit(BlockPos base, double range, double wallRange, boolean strict) {
        if (strict) {
            return findStrictCrystalHit(base, range);
        }

        Vec3d crystalVec = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        Vec3d eyes = mc.player.getEyePos();
        double distanceSq = eyes.squaredDistanceTo(crystalVec);

        if (distanceSq > range * range) {
            return null;
        }

        boolean visible = isVisible(crystalVec);
        if (!visible && distanceSq > wallRange * wallRange) {
            return null;
        }

        return new BlockHitResult(
                crystalVec,
                Direction.UP,
                base,
                false
        );
    }

    public BlockHitResult findStrictCrystalHit(BlockPos base, double range) {
        Vec3d eyes = mc.player.getEyePos();
        double centerY = base.toCenterPos().y;

        if (eyes.y > centerY) {
            BlockHitResult up = findFaceHit(base, Direction.UP, range);
            if (up != null) {
                return up;
            }
        }

        Direction[] sides = {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.DOWN
        };

        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction side : sides) {
            BlockHitResult hit = findFaceHit(base, side, range);
            if (hit == null) {
                continue;
            }

            double distance = eyes.squaredDistanceTo(hit.getPos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }

        return best;
    }

    public BlockHitResult findBlockPlacementHit(BlockPos targetPos, double range) {
        Direction[] order = {
                Direction.DOWN,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.UP
        };

        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction offset : order) {
            BlockPos neighbour = targetPos.offset(offset);
            var state = mc.world.getBlockState(neighbour);

            if (state.isAir()
                    || state.isReplaceable()
                    || state.getCollisionShape(mc.world, neighbour).isEmpty()) {
                continue;
            }

            Direction face = offset.getOpposite();
            BlockHitResult hit = findFaceHit(neighbour, face, range);

            if (hit == null) {
                continue;
            }

            double distance = mc.player.getEyePos().squaredDistanceTo(hit.getPos());

            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }

        return best;
    }

    public BlockHitResult findFaceHit(BlockPos pos, Direction face, double range) {
        Vec3d eyes = mc.player.getEyePos();

        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (double a : FACE_SAMPLES) {
            for (double b : FACE_SAMPLES) {
                Vec3d point = facePoint(pos, face, a, b);

                if (eyes.squaredDistanceTo(point) > range * range) {
                    continue;
                }

                BlockHitResult hit = raycastTo(point, range);

                if (hit == null
                        || hit.getType() != HitResult.Type.BLOCK
                        || !hit.getBlockPos().equals(pos)
                        || hit.getSide() != face) {
                    continue;
                }

                double distance = eyes.squaredDistanceTo(hit.getPos());

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new BlockHitResult(
                            hit.getPos(),
                            hit.getSide(),
                            hit.getBlockPos(),
                            false
                    );
                }
            }
        }

        return best;
    }

    public BlockHitResult raycastCurrentRotation(double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(
                RotationManager.INSTANCE.getRotation()
                        .toVector()
                        .multiply(range)
        );

        BlockHitResult hit = mc.world.raycast(
                new RaycastContext(
                        eyes,
                        end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return new BlockHitResult(
                hit.getPos(),
                hit.getSide(),
                hit.getBlockPos(),
                false
        );
    }

    public boolean matches(BlockHitResult actual, BlockHitResult expected) {
        return actual != null
                && expected != null
                && actual.getBlockPos().equals(expected.getBlockPos())
                && actual.getSide() == expected.getSide();
    }

    public boolean isVisible(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();

        BlockHitResult hit = mc.world.raycast(
                new RaycastContext(
                        eyes,
                        point,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );

        return hit == null
                || hit.getType() == HitResult.Type.MISS
                || eyes.squaredDistanceTo(hit.getPos()) >= eyes.squaredDistanceTo(point) - 0.04;
    }

    public boolean hasLineOfSight(Vec3d point, double range) {
        Vec3d eyes = mc.player.getEyePos();

        if (eyes.squaredDistanceTo(point) > range * range) {
            return false;
        }

        return isVisible(point);
    }

    public boolean canHitCrystalFromCurrentRotation(EndCrystalEntity crystal, double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(
                RotationManager.INSTANCE.getRotation()
                        .toVector()
                        .multiply(range)
        );

        return intersectsCrystal(crystal, eyes, end);
    }

    public boolean canHitCrystal(EndCrystalEntity crystal, Rotation rotation, double range) {
        Vec3d eyes = mc.player.getEyePos();
        return intersectsCrystal(crystal, eyes, eyes.add(rotation.toVector().multiply(range)));
    }

    public boolean canHitCrystalVisible(EndCrystalEntity crystal, Rotation rotation, double range, boolean requireVisible) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(rotation.toVector().multiply(range));
        var intersection = crystal.getBoundingBox().raycast(eyes, end);
        if (intersection.isEmpty()) {
            return false;
        }
        Vec3d point = intersection.get();
        if (requireVisible && !isVisible(point)) {
            return false;
        }
        double distance = eyes.squaredDistanceTo(point);
        for (var entity : mc.world.getEntities()) {
            if (entity == mc.player || entity == crystal || !entity.isAlive() || !entity.canHit()) {
                continue;
            }
            var otherHit = entity.getBoundingBox().raycast(eyes, point);
            if (otherHit.isPresent() && eyes.squaredDistanceTo(otherHit.get()) < distance - 1.0E-5) {
                return false;
            }
        }
        return true;
    }

    private boolean intersectsCrystal(EndCrystalEntity crystal, Vec3d eyes, Vec3d end) {
        return crystal.getBoundingBox().raycast(eyes, end).isPresent();
    }

    private BlockHitResult raycastTo(Vec3d point, double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = point.subtract(eyes);

        if (delta.lengthSquared() <= 1.0E-6
                || delta.lengthSquared() > range * range) {
            return null;
        }

        Vec3d end = point.add(delta.normalize().multiply(0.04));

        return mc.world.raycast(
                new RaycastContext(
                        eyes,
                        end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );
    }

    private Vec3d facePoint(BlockPos pos, Direction face, double a, double b) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        return switch (face) {
            case UP -> new Vec3d(x + a, pos.getY() + 1.0, z + b);
            case DOWN -> new Vec3d(x + a, pos.getY(), z + b);
            case NORTH -> new Vec3d(x + a, y + b, pos.getZ());
            case SOUTH -> new Vec3d(x + a, y + b, pos.getZ() + 1.0);
            case WEST -> new Vec3d(pos.getX(), y + b, z + a);
            case EAST -> new Vec3d(pos.getX() + 1.0, y + b, z + a);
        };
    }
}
