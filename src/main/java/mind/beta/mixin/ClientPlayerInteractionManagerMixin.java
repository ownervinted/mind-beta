package mind.beta.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.consume.UseAction;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mind.beta.events.api.EventManager;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.BlockBreakingEvent;
import mind.beta.events.impl.ClickSlotEvent;
import mind.beta.events.impl.UsingItemEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.RotationManager;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {


    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void mind$syncManualBlockRotation(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        Aura aura = Aura.getInstance();

        boolean shieldUse = player.getMainHandStack().getUseAction() == UseAction.BLOCK
                || player.getOffHandStack().getUseAction() == UseAction.BLOCK;







        if (aura != null
                && aura.isState()
                && Aura.target != null
                && Aura.target.isAlive()
                && shieldUse
                && client.options.useKey.isPressed()) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        if (aura == null
                || !aura.isState()
                || Aura.target == null
                || !Aura.target.isAlive()
                || !client.options.useKey.isPressed()
                || !(client.crosshairTarget instanceof BlockHitResult cameraHit)
                || !cameraHit.getBlockPos().equals(hitResult.getBlockPos())
                || cameraHit.getSide() != hitResult.getSide()) {
            return;
        }

        RotationManager.INSTANCE.syncManualBlockUseToCamera();
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void gameModeHook(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        UsingItemEvent event = new UsingItemEvent(EventType.START);
        EventManager.callEvent(event);

        if (event.isCancelled()) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    public void interactItemHook(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue() instanceof ActionResult.Success success
                && !success.swingSource().equals(ActionResult.SwingSource.CLIENT)) {
            UsingItemEvent event = new UsingItemEvent(EventType.PRE);
            EventManager.callEvent(event);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    public void stopUsingItemHook(CallbackInfo ci) {
        UsingItemEvent event = new UsingItemEvent(EventType.POST);
        EventManager.callEvent(event);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
    private void injectBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        EventManager.callEvent(new BlockBreakingEvent(pos, direction));
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void clickSlotHook(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo info) {
        ClickSlotEvent event = new ClickSlotEvent(syncId, slotId, button, actionType);
        EventManager.callEvent(event);
        if (event.isCancelled()) info.cancel();
    }
}
