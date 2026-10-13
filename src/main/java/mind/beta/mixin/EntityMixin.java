package mind.beta.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mind.beta.IMinecraft;
import mind.beta.events.api.EventManager;
import mind.beta.events.impl.BoundingBoxControlEvent;
import mind.beta.events.impl.PlayerVelocityStrafeEvent;

@Mixin(Entity.class)
public abstract class EntityMixin implements IMinecraft {

    @Shadow private Box boundingBox;
    @Shadow public float yaw;
    @Unique
    private boolean client$local;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityType<?> type, World world, CallbackInfo ci) {
        client$local = (Entity)(Object)this instanceof ClientPlayerEntity;
    }
   
    @Unique
    private final MinecraftClient client = MinecraftClient.getInstance();
  
    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    public final void getBoundingBox(CallbackInfoReturnable<Box> cir) {
        BoundingBoxControlEvent event = new BoundingBoxControlEvent(boundingBox, (Entity) (Object) this);
        EventManager.callEvent(event);
        cir.setReturnValue(event.getBox());
    }
    
    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d hookVelocity(Vec3d movementInput, float speed, float yaw) {
        if ((Object) this == mc.player) {
            PlayerVelocityStrafeEvent event = new PlayerVelocityStrafeEvent(movementInput, speed, yaw, Entity.movementInputToVelocity(movementInput, speed, yaw));
            EventManager.callEvent(event);
            return event.getVelocity();
        }
        return Entity.movementInputToVelocity(movementInput, speed, yaw);
    }
}
