/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura.target;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import mind.beta.IMinecraft;
import mind.beta.modules.impl.combat.AntiBot;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.util.repository.friend.FriendUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TargetSelector implements IMinecraft {
    final MultiPoint pointFinder = new MultiPoint();
    LivingEntity currentTarget;
    Stream<LivingEntity> potentialTargets;

    public TargetSelector() {
        this.currentTarget = null;
    }

    public void lockTarget(LivingEntity target) {
        if (this.currentTarget == null) {
            this.currentTarget = target;
        }
    }

    public void releaseTarget() {
        this.currentTarget = null;
    }

    public void validateTarget(Predicate<LivingEntity> predicate) {
        findFirstMatch(predicate).ifPresent(this::lockTarget);

        if (this.currentTarget != null && !predicate.test(this.currentTarget)) {
            releaseTarget();
        }
    }

    public void validateTargetSorted(Predicate<LivingEntity> predicate, Comparator<LivingEntity> comparator) {
        if (this.currentTarget != null && predicate.test(this.currentTarget)) {
            return;
        }

        this.currentTarget = this.potentialTargets
                .filter(predicate)
                .sorted(comparator)
                .findFirst()
                .orElse(null);
    }

    public void validateTargetPreferred(
            Predicate<LivingEntity> keepCurrentPredicate,
            Predicate<LivingEntity> primaryPredicate,
            Comparator<LivingEntity> primaryComparator,
            Predicate<LivingEntity> fallbackPredicate,
            Comparator<LivingEntity> fallbackComparator
    ) {
        if (this.currentTarget != null && keepCurrentPredicate.test(this.currentTarget)) {
            return;
        }

        List<LivingEntity> candidates = this.potentialTargets.toList();

        LivingEntity primary = candidates.stream()
                .filter(primaryPredicate)
                .min(primaryComparator)
                .orElse(null);

        if (primary != null) {
            this.currentTarget = primary;
            return;
        }

        this.currentTarget = candidates.stream()
                .filter(fallbackPredicate)
                .min(fallbackComparator)
                .orElse(null);
    }

    public void searchTargets(Iterable<Entity> entities, float maxDistance, float maxFov, boolean ignoreWalls) {
        if (currentTarget != null && (!pointFinder.hasValidPoint(currentTarget, maxDistance, ignoreWalls) || getFov(currentTarget, maxDistance, ignoreWalls) > maxFov)) {
            releaseTarget();
        }

        this.potentialTargets = createStreamFromEntities(entities, maxDistance, maxFov, ignoreWalls);
    }

    private double getFov(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Vec3d attackVector = pointFinder.computeVector(entity, maxDistance, RotationManager.INSTANCE.getRotation(), new LinearRotation().randomValue(), ignoreWalls).getLeft();
        return RaycastHelper.rayTrace(maxDistance, entity.getBoundingBox()) ? 0 : RotationManager.computeRotationDifference(RotationUtils.cameraAngle(), RotationUtils.calculateAngle(attackVector));
    }

    private Stream<LivingEntity> createStreamFromEntities(Iterable<Entity> entities, float maxDistance, float maxFov, boolean ignoreWalls) {
        return StreamSupport.stream(entities.spliterator(), false)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> pointFinder.hasValidPoint(entity, maxDistance, ignoreWalls) && getFov(entity, maxDistance, ignoreWalls) < maxFov)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceTo(mc.player)));
    }

    private Optional<LivingEntity> findFirstMatch(Predicate<LivingEntity> predicate) {
        return this.potentialTargets.filter(predicate).findFirst();
    }

    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class EntityFilter {
        List<String> targetSettings;

        public boolean isValid(LivingEntity entity) {
            if (isLocalPlayer(entity)) return false;
            if (isInvalidHealth(entity)) return false;
            if (isBotPlayer(entity)) return false;
            return isValidEntityType(entity);
        }

        private boolean isLocalPlayer(LivingEntity entity) {
            return entity == mc.player;
        }

        private boolean isInvalidHealth(LivingEntity entity) {
            return !entity.isAlive() || entity.getHealth() <= 0;
        }

        private boolean isBotPlayer(LivingEntity entity) {
            return entity instanceof PlayerEntity player && AntiBot.getInstance().isBot(player);
        }

        private boolean isFriendPlayer(LivingEntity entity) {
            return entity instanceof PlayerEntity && FriendUtils.isFriend(entity);
        }

        private boolean isValidEntityType(LivingEntity entity) {
            if (entity instanceof PlayerEntity player) {
                if (FriendUtils.isFriend(player)) {
                    return targetSettings.contains("Friends");
                }
                return targetSettings.contains("Players");
            }
            if (entity instanceof AnimalEntity) {
                return targetSettings.contains("Animals");
            }
            if (entity instanceof MobEntity) {
                return targetSettings.contains("Mobs");
            }
            if (entity instanceof ArmorStandEntity) {
                return targetSettings.contains("Armor Stands");
            }
            return false;
        }
    }
}