/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.macros;

import mind.beta.IMinecraft;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;


public final class MacroDamage implements IMinecraft {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private MacroDamage() {
    }

    public static float calculateCrystal(Vec3d pos, LivingEntity entity) {
        return calculateCrystal(pos, entity, entity == null ? null : entity.getEntityPos());
    }

    public static float calculateCrystal(Vec3d pos, LivingEntity entity, Vec3d predictedPos) {
        return calculate(pos, entity, predictedPos, 6.0F, null);
    }

    public static float calculateAnchor(BlockPos pos, LivingEntity entity) {
        return calculateAnchor(pos, entity, entity == null ? null : entity.getEntityPos());
    }

    public static float calculateAnchor(BlockPos pos, LivingEntity entity, Vec3d predictedPos) {
        if (pos == null) {
            return Float.POSITIVE_INFINITY;
        }
        return calculate(pos.toCenterPos(), entity, predictedPos, 5.0F, pos);
    }

    private static float calculate(Vec3d pos, LivingEntity entity, Vec3d predictedPos, float power, BlockPos ignoredBlock) {
        if (mc.world == null || pos == null || entity == null || predictedPos == null || !entity.isAlive()) {
            return Float.POSITIVE_INFINITY;
        }
        if (entity instanceof PlayerEntity player && (player.isSpectator() || player.getAbilities().creativeMode)) {
            return 0.0F;
        }
        return compute(pos, entity, predictedPos, power, ignoredBlock);
    }

    private static float compute(Vec3d explosionPos, LivingEntity entity, Vec3d predictedPos, float power, BlockPos ignoredBlock) {
        double normalizedDistance = predictedPos.distanceTo(explosionPos) / (power * 2.0);
        if (normalizedDistance > 1.0) {
            return 0.0F;
        }
        Box box = entity.getBoundingBox().offset(predictedPos.subtract(entity.getEntityPos()));
        double exposure = exposure(explosionPos, entity, box, ignoredBlock);
        double impact = (1.0 - normalizedDistance) * exposure;
        float damage = (float) (int) ((impact * impact + impact) * (7.0 * power) + 1.0);
        if (entity instanceof PlayerEntity) {
            damage = switch (mc.world.getDifficulty()) {
                case PEACEFUL -> 0.0F;
                case EASY -> Math.min(damage * 0.5F + 1.0F, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5F;
            };
        }
        float armor = entity.getArmor();
        float toughness = (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float reduction = MathHelper.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        damage *= 1.0F - reduction / 25.0F;
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage *= Math.max(0.0F, 1.0F - (resistance.getAmplifier() + 1) * 0.2F);
        }
        int protection = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemEnchantmentsComponent component = entity.getEquippedStack(slot)
                    .getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            for (var entry : component.getEnchantmentEntries()) {
                if (entry.getKey().matchesKey(Enchantments.PROTECTION)) {
                    protection += entry.getIntValue();
                } else if (entry.getKey().matchesKey(Enchantments.BLAST_PROTECTION)) {
                    protection += entry.getIntValue() * 2;
                }
            }
        }
        return Math.max(0.0F, damage * (1.0F - MathHelper.clamp(protection, 0, 20) / 25.0F));
    }

    private static double exposure(Vec3d explosionPos, LivingEntity entity, Box box, BlockPos ignoredBlock) {
        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        int visible = 0;
        int total = 0;
        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3d sample = new Vec3d(MathHelper.lerp(x, box.minX, box.maxX) + offsetX,
                            MathHelper.lerp(y, box.minY, box.maxY), MathHelper.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    if (unobstructed(sample, explosionPos, entity, ignoredBlock)) {
                        visible++;
                    }
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) visible / total;
    }

    private static boolean unobstructed(Vec3d start, Vec3d end, LivingEntity entity, BlockPos ignoredBlock) {
        RaycastContext context = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, entity);
        if (ignoredBlock == null) {
            return mc.world.raycast(context).getType() == HitResult.Type.MISS;
        }
        BlockHitResult hit = BlockView.raycast(start, end, context, (ctx, pos) -> {
            var state = pos.equals(ignoredBlock) ? Blocks.AIR.getDefaultState() : mc.world.getBlockState(pos);
            return mc.world.raycastBlock(start, end, pos, ctx.getBlockShape(state, mc.world, pos), state);
        }, ctx -> null);
        return hit == null;
    }

}
