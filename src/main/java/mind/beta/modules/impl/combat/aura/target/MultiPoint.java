/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.target;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MultiPoint implements IMinecraft {
    private final Random random = new SecureRandom();
    private Vec3d offset = Vec3d.ZERO;


    public Pair<Vec3d, Box> computeVector(LivingEntity entity, float maxDistance, Rotation initialRotation, Vec3d velocity, boolean ignoreWalls) {
        Pair<List<Vec3d>, Box> candidatePoints = generateCandidatePoints(entity, maxDistance, ignoreWalls);
        Vec3d bestVector = findBestVector(candidatePoints.getLeft(), initialRotation);

        if (Aura.getInstance().options.isSelected("Height Interpolation") && entity != null) {
            bestVector = Vector.hitbox(entity, 1, 1.1F, 1, 6);
        }

        return new Pair<>((bestVector == null ? entity.getEyePos() : bestVector).add(offset), candidatePoints.getRight());
    }


    public Pair<List<Vec3d>, Box> generateCandidatePoints(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Box entityBox = entity.getBoundingBox();
        double stepY = entityBox.getLengthY() / 10.0F;

        List<Vec3d> list = Stream.iterate(entityBox.minY, y -> y <= entityBox.maxY, y -> y + stepY)
                .map(y -> new Vec3d(entityBox.getCenter().x, y, entityBox.getCenter().z))
                .filter(point -> isValidPoint(mc.player.getEyePos(), point, maxDistance, ignoreWalls))
                .toList();

        return new Pair<>(list, entityBox);
    }


    public boolean hasValidPoint(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Box entityBox = entity.getBoundingBox();
        double stepY = entityBox.getLengthY() / 10.0F;

        return Stream.iterate(entityBox.minY, y -> y < entityBox.maxY, y -> y + stepY)
                .map(y -> new Vec3d(entityBox.getCenter().x, y, entityBox.getCenter().z))
                .anyMatch(point -> isValidPoint(mc.player.getEyePos(), point, maxDistance, ignoreWalls));
    }

    private boolean isValidPoint(Vec3d startPoint, Vec3d endPoint, float maxDistance, boolean ignoreWalls) {
        return startPoint.distanceTo(endPoint) <= maxDistance && (ignoreWalls || !RaycastHelper.raycast(startPoint, endPoint, RaycastContext.ShapeType.COLLIDER).getType().equals(HitResult.Type.BLOCK));
    }

    private Vec3d findBestVector(List<Vec3d> candidatePoints, Rotation initialRotation) {
        return candidatePoints.stream().min(Comparator.comparing(point -> calculateRotationDifference(mc.player.getEyePos(), point, initialRotation))).orElse(null);
    }

    private double calculateRotationDifference(Vec3d startPoint, Vec3d endPoint, Rotation initialRotation) {
        Rotation targetRotation = RotationUtils.fromVec3d(endPoint.subtract(startPoint));
        Rotation delta = RotationUtils.calculateDelta(initialRotation, targetRotation);
        return Math.hypot(delta.getYaw(), delta.getPitch());
    }

    private void updateOffset(Vec3d velocity) {
        offset = offset.add(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(velocity);
    }
}
