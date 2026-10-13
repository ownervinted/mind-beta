/* Copyright Mind */

package mind.beta.modules.impl.combat.aura.attack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public final class ShieldAttackCheck {

    private ShieldAttackCheck() {
    }

    public static boolean isProtected(LivingEntity target, Entity attacker) {
        if (target == null || attacker == null || !target.isBlocking()
                || !target.getActiveItem().isOf(Items.SHIELD)) {
            return false;
        }

        Vec3d attackerPosition = attacker.getEntityPos();
        Vec3d targetPosition = target.getEntityPos();
        double dx = attackerPosition.x - targetPosition.x;
        double dz = attackerPosition.z - targetPosition.z;
        double horizontalDistanceSquared = dx * dx + dz * dz;

        if (horizontalDistanceSquared < 1.0E-8D) {
            return true;
        }

        Vec3d forward = target.getRotationVector(0.0F, target.getHeadYaw());
        double facing = (forward.x * dx + forward.z * dz)
                / Math.sqrt(horizontalDistanceSquared);

        return facing > 0.0D;
    }
}
