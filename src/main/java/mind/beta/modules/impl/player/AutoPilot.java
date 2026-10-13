/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.Instance;
import mind.beta.util.math.TaskPriority;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AutoPilot extends ModuleStructure {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public ItemEntity target;

    private float lastYaw, lastPitch;

    private float targetYaw, targetPitch;

    Rotation rot = new Rotation(0, 0);

    public AutoPilot() {
        super("AutoPilot", "Auto Pilot", ModuleCategory.MISC);
    }

    public static AutoPilot getInstance() {
        return Instance.get(AutoPilot.class);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            target = null;
            return;
        }

        target = findTarget();

        if (target != null) {
            processRotation();
        } else {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    private void processRotation() {
        double dx = target.getEntityPos().getX() - mc.player.getEntityPos().getX();
        double dy = (target.getEntityPos().getY()) - (mc.player.getEntityPos().getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = target.getEntityPos().getZ() - mc.player.getEntityPos().getZ();

        targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0);
        targetPitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);

        float maxRotation = 1024;

        float yawDiff = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float yawStep = MathHelper.clamp(yawDiff, -maxRotation, maxRotation);
        lastYaw += yawStep;

        float pitchDiff = MathHelper.wrapDegrees(targetPitch - lastPitch);
        float pitchStep = MathHelper.clamp(pitchDiff, -maxRotation, maxRotation);
        lastPitch += pitchStep;
        mc.player.setYaw(lastYaw);
        mc.player.setPitch(lastPitch);
        rot.setYaw(lastYaw);
        rot.setPitch(lastPitch);
        RotationManager.INSTANCE.rotateTo(rot, RotationRequest.DEFAULT, TaskPriority.HIGH_IMPORTANCE_1, this);
    }

    private ItemEntity findTarget() {
        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class,
                        mc.player.getBoundingBox().expand(50.0),
                        e -> e.isAlive() && isValidItem(e))
                .stream()
                .sorted(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .collect(Collectors.toList());
        return items.isEmpty() ? null : items.get(0);
    }

    private boolean isValidItem(ItemEntity item) {
        var stack = item.getStack();
        return stack.getItem() == Items.SPAWNER ||
                stack.getItem() == Items.PLAYER_HEAD ||
                stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                stack.getItem().toString().contains("_spawn_egg");
    }

    @Override
    public void deactivate() {
        target = null;
        if (mc.player != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    mc.player.getEntityPos().getX(),
                    mc.player.getEntityPos().getY(),
                    mc.player.getEntityPos().getZ(),
                    mc.player.getYaw(),
                    mc.player.getPitch(),
                    mc.player.isOnGround(),
                    false
            ));
        }
    }
}