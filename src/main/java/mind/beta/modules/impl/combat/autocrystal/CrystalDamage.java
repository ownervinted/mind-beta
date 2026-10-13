/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.autocrystal;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import mind.beta.IMinecraft;

@UtilityClass
public class CrystalDamage implements IMinecraft {

    public float estimate(Vec3d explosion, LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }

        double distance = entity.getBoundingBox().getCenter().distanceTo(explosion);
        if (distance >= 12.0) {
            return 0.0F;
        }

        double exposure = exposure(explosion, entity.getBoundingBox());
        double impact = (1.0 - distance / 12.0) * exposure;

        if (impact <= 0.0) {
            return 0.0F;
        }

        double raw = ((impact * impact + impact) * 0.5 * 7.0 * 12.0) + 1.0;
        int armor = Math.min(20, Math.max(0, entity.getArmor()));
        double armorMultiplier = 1.0 - armor / 25.0;

        return (float) Math.max(0.0, raw * armorMultiplier);
    }

    private double exposure(Vec3d explosion, Box box) {
        double[] xs = {0.2, 0.5, 0.8};
        double[] ys = {0.15, 0.5, 0.85};
        double[] zs = {0.2, 0.5, 0.8};

        int clear = 0;
        int total = 0;

        for (double fx : xs) {
            for (double fy : ys) {
                for (double fz : zs) {
                    Vec3d sample = new Vec3d(
                            lerp(fx, box.minX, box.maxX),
                            lerp(fy, box.minY, box.maxY),
                            lerp(fz, box.minZ, box.maxZ)
                    );

                    total++;

                    BlockHitResult hit = mc.world.raycast(new RaycastContext(
                            sample,
                            explosion,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            (Entity) null
                    ));

                    if (hit == null || hit.getType() == HitResult.Type.MISS) {
                        clear++;
                    }
                }
            }
        }

        return total == 0 ? 0.0 : (double) clear / total;
    }

    private double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
