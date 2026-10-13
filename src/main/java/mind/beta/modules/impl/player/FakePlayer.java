/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import com.mojang.authlib.GameProfile;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.Instance;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FakePlayer extends ModuleStructure {

    public static FakePlayer getInstance() {
        return Instance.get(FakePlayer.class);
    }

    OtherClientPlayerEntity fakePlayer;

    public FakePlayer() {
        super("FakePlayer", "Spawns a fake player for testing", ModuleCategory.PLAYER);
    }

    @Override
    public void activate() {
        if (mc.player == null || mc.world == null) {
            setState(false);
            return;
        }

        spawnFakePlayer();

        super.activate();
    }

    @Override
    public void deactivate() {
        removeFakePlayer();

        super.deactivate();
    }

    private void spawnFakePlayer() {
        removeFakePlayer();

        GameProfile profile = new GameProfile(
                UUID.randomUUID(),
                "TestPlayer"
        );

        fakePlayer = new OtherClientPlayerEntity(
                mc.world,
                profile
        );

        Vec3d forward = mc.player.getRotationVec(1.0F);

        double distance = 2.5D;

        Vec3d spawnPos = new Vec3d(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
        ).add(
                forward.x * distance,
                0.0D,
                forward.z * distance
        );

        fakePlayer.refreshPositionAndAngles(
                spawnPos.x,
                spawnPos.y,
                spawnPos.z,
                mc.player.getYaw() + 180.0F,
                0.0F
        );

        fakePlayer.setHeadYaw(fakePlayer.getYaw());
        fakePlayer.setBodyYaw(fakePlayer.getYaw());

        fakePlayer.setHealth(20.0F);
        fakePlayer.setOnGround(true);

        mc.world.addEntity(fakePlayer);
    }

    private void removeFakePlayer() {
        if (fakePlayer == null) {
            return;
        }

        if (mc.world != null && !fakePlayer.isRemoved()) {
            mc.world.removeEntity(
                    fakePlayer.getId(),
                    Entity.RemovalReason.DISCARDED
            );
        }

        fakePlayer = null;
    }

    public OtherClientPlayerEntity getFakePlayer() {
        return fakePlayer;
    }

    public boolean hasFakePlayer() {
        return fakePlayer != null && !fakePlayer.isRemoved();
    }
}
