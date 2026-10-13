/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.math.MathUtils;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.repository.friend.FriendUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ProjectileHelper extends ModuleStructure {

    private final SliderSettings searchDistance = new SliderSettings("Search Range", "Target search radius around the player")
            .setValue(16).range(5F, 64F);

    private final MultiSelectSetting targetType = new MultiSelectSetting("Target Type", "Filter targets by type")
            .value("Players", "Mobs", "Animals", "Armor Stand")
            .selected("Players", "Mobs", "Animals");

    private final TargetSelector targetSelector = new TargetSelector();
    private LivingEntity currentTarget;

    public ProjectileHelper() {
        super("ProjectileHelper", "Projectile Helper", ModuleCategory.COMBAT);
        settings(searchDistance, targetType);
    }

    public LivingEntity getTarget(World world, Iterable<Entity> entities) {
        List<Entity> entityList = StreamSupport.stream(entities.spliterator(), false).collect(Collectors.toList());

        List<LivingEntity> validTargets = entityList.stream()
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());

        LivingEntity nearestTarget = null;
        double nearestDistance = Double.MAX_VALUE;
        Vec3d playerPos = mc.player.getEntityPos();

        for (LivingEntity target : validTargets) {
            double distance = target.getEntityPos().distanceTo(playerPos);
            if (distance < nearestDistance && distance <= searchDistance.getValue()) {
                nearestDistance = distance;
                nearestTarget = target;
            }
        }

        currentTarget = nearestTarget;
        return currentTarget;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null) return false;
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;

        if (!targetType.isSelected("Players") && entity instanceof PlayerEntity) return false;
        if (!targetType.isSelected("Mobs") && entity instanceof MobEntity) return false;
        if (!targetType.isSelected("Animals") && entity instanceof AnimalEntity) return false;
        if (!targetType.isSelected("Armor Stand") && entity instanceof ArmorStandEntity) return false;
        return true;
    }

    public Vec3d getPredictedPosition(LivingEntity target, Vec3d shooterPos, float projectileSpeed, float gravity) {
        Vec3d targetPos = target.getEntityPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d targetVelocity = target.getVelocity();
        Vec3d delta = targetPos.subtract(shooterPos);

        double a = projectileSpeed * projectileSpeed - targetVelocity.lengthSquared();
        double b = -2 * delta.dotProduct(targetVelocity);
        double c = -delta.lengthSquared();

        double t;
        double discriminant = b * b - 4 * a * c;
        if (discriminant > 0) {
            double t1 = (-b + Math.sqrt(discriminant)) / (2 * a);
            double t2 = (-b - Math.sqrt(discriminant)) / (2 * a);
            t = Math.max(t1, t2);
        } else {
            t = delta.length() / projectileSpeed;
        }

        Vec3d predicted = targetPos.add(targetVelocity.multiply(t));
        predicted = predicted.add(0, 0.5 * gravity * t * t, 0);

        return predicted;
    }

    private boolean isHoldingProjectile() {
        ItemStack main = mc.player.getMainHandStack();
        return main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem || main.getItem() instanceof TridentItem;
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        if (e.getType() != EventType.PRE) return;

        ItemStack stack = mc.player.getMainHandStack();

        if (!isValidWeaponState(stack)) {
            currentTarget = null;
            return;
        }

        updateTarget();

        if (currentTarget != null) {
            performAim();
        }
    }

    private boolean isValidWeaponState(ItemStack stack) {
        boolean holdingBow = stack.getItem() instanceof BowItem;
        boolean holdingCrossbow = stack.getItem() instanceof CrossbowItem && ((CrossbowItem) stack.getItem()).isCharged(stack);
        boolean holdingTrident = stack.getItem() instanceof TridentItem;

        if (!holdingBow && !holdingCrossbow && !holdingTrident) {
            return false;
        }

        if (holdingBow && mc.player.getActiveItem() != stack) {
            return false;
        }

        return true;
    }

    private void updateTarget() {
        if (currentTarget != null && !currentTarget.isAlive()) {
            currentTarget = null;
        }

        if (currentTarget == null) {
            currentTarget = getTarget(mc.world, mc.world.getEntities());
            if (currentTarget == mc.player) currentTarget = null;
        }

        if (FriendUtils.isFriend(currentTarget)) currentTarget = null;
    }

    private void performAim() {
        Vec3d shooterPos = mc.player.getEntityPos()
                .add(0, mc.player.getEyeHeight(mc.player.getPose()), 0)
                .add(mc.player.getVelocity());

        float projectileSpeed = 6.0f;
        float gravity = 0.02f;

        Vec3d predictedPos = getPredictedPosition(currentTarget, shooterPos, projectileSpeed, gravity);

        double dx = predictedPos.x - shooterPos.x;
        double dy = predictedPos.y - shooterPos.y;
        double dz = predictedPos.z - shooterPos.z;
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f + MathUtils.getRandom(-1, 1);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distanceXZ)) + MathUtils.getRandom(-1, 1);

        RotationManager.INSTANCE.rotateTo(
                new Rotation(yaw, pitch),
                RotationRequest.DEFAULT,
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }
}