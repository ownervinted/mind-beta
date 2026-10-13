package mind.beta.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class CustomCapeMixin {

    @Unique
    private static final int CAPE_FRAME_COUNT = 102;

    @Unique
    private static final long CAPE_FRAME_TIME = 42L;

    @Unique
    private static final AssetInfo.TextureAssetInfo[] CAPE_FRAMES =
            new AssetInfo.TextureAssetInfo[CAPE_FRAME_COUNT];

    @Unique
    private static final AssetInfo.TextureAssetInfo ELYTRA_ASSET =
            new AssetInfo.TextureAssetInfo(Identifier.of("mind", "capes/cape"));

    static {
        for (int i = 0; i < CAPE_FRAME_COUNT; i++) {
            CAPE_FRAMES[i] = new AssetInfo.TextureAssetInfo(
                    Identifier.of("mind", "capes/cape" + i)
            );
        }
    }

    @Unique
    private static AssetInfo.TextureAssetInfo mind$getCapeFrame() {
        int frame = (int) ((System.currentTimeMillis() / CAPE_FRAME_TIME) % CAPE_FRAME_COUNT);
        return CAPE_FRAMES[frame];
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void replaceCape(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || !player.getUuid().equals(client.player.getUuid())) {
            return;
        }

        SkinTextures old = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(
                old.body(),
                mind$getCapeFrame(),
                ELYTRA_ASSET,
                old.model(),
                old.secure()
        ));
    }
}
