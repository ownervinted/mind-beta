/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.autocrystal;

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
import mind.beta.modules.impl.combat.aura.RotationUtils;

@UtilityClass
public class CrystalRayTrace implements IMinecraft {

    private static final double[] SAMPLES = {-0.34, 0.0, 0.34};

    public BlockHitResult findCrystalPlaceHit(BlockPos base, double range) {
        return findFaceHit(base, Direction.UP, range);
    }

    public BlockHitResult findObsidianPlacementHit(BlockPos placePos, double range) {
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction offset : Direction.values()) {
            BlockPos neighbour = placePos.offset(offset);
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

        for (double a : SAMPLES) {
            for (double b : SAMPLES) {
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
                    best = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
                }
            }
        }

        return best;
    }

    public Vec3d findBreakAim(EndCrystalEntity crystal, BlockPos base, double range) {
        Vec3d top = base.toCenterPos().add(0.0, 0.98, 0.0);
        Rotation topRotation = RotationUtils.calculateAngle(top);

        if (canHitCrystal(crystal, topRotation, range)) {
            return top;
        }

        Vec3d lowerCrystal = crystal.getBoundingBox().getCenter().add(0.0, -0.35, 0.0);
        Rotation crystalRotation = RotationUtils.calculateAngle(lowerCrystal);

        if (canHitCrystal(crystal, crystalRotation, range)) {
            return lowerCrystal;
        }

        return null;
    }

    public BlockHitResult raycastCurrentRotation(double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(RotationManager.INSTANCE.getRotation().toVector().multiply(range));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eyes,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return hit;
    }

    public boolean matches(BlockHitResult actual, BlockHitResult expected) {
        return actual != null
                && expected != null
                && actual.getType() == HitResult.Type.BLOCK
                && actual.getBlockPos().equals(expected.getBlockPos())
                && actual.getSide() == expected.getSide();
    }

    public boolean canHitCrystalFromCurrentRotation(EndCrystalEntity crystal, double range) {
        return canHitCrystal(crystal, RotationManager.INSTANCE.getRotation(), range);
    }

    public boolean canHitCrystal(EndCrystalEntity crystal, Rotation rotation, double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(rotation.toVector().multiply(range));

        return crystal.getBoundingBox()
                .expand(0.12)
                .raycast(eyes, end)
                .isPresent();
    }

    private BlockHitResult raycastTo(Vec3d point, double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = point.subtract(eyes);

        if (delta.lengthSquared() < 1.0E-6 || delta.lengthSquared() > range * range) {
            return null;
        }

        Vec3d end = point.add(delta.normalize().multiply(0.05));

        return mc.world.raycast(new RaycastContext(
                eyes,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
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
