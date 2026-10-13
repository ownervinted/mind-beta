package mind.beta.mixin;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import mind.beta.modules.impl.combat.MaceHelper;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import mind.beta.events.api.EventManager;
import mind.beta.events.impl.PacketEvent;
import mind.beta.util.config.impl.proxy.ProxyConfig;
import mind.beta.util.proxy.Proxy;

import java.net.InetSocketAddress;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @ModifyVariable(
            method = "send(Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> mind$syncUseItemRotation(Packet<?> packet) {
        if (!(packet instanceof PlayerInteractItemC2SPacket useItem)) {
            return packet;
        }

        RotationManager manager = RotationManager.INSTANCE;
        boolean silentRotation = manager.shouldUseSilentRotationForItems();

        boolean maceSequence = false;
        try {
            MaceHelper helper = MaceHelper.getInstance();
            maceSequence = helper != null
                    && helper.isState()
                    && helper.isThrowSequenceActive();
        } catch (Throwable ignored) {
        }

        Rotation locked = manager.getItemUseRotationLock();

        if (!silentRotation && !maceSequence && locked == null) {
            return packet;
        }

        if (locked == null) {
            locked = manager.getOrLockItemUseRotation();
        }

        if (locked == null) {
            return packet;
        }



        if (Float.floatToRawIntBits(useItem.getYaw()) == Float.floatToRawIntBits(locked.getYaw())
                && Float.floatToRawIntBits(useItem.getPitch()) == Float.floatToRawIntBits(locked.getPitch())) {
            return packet;
        }

        return new PlayerInteractItemC2SPacket(
                useItem.getHand(),
                useItem.getSequence(),
                locked.getYaw(),
                locked.getPitch()
        );
    }

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void handlePacketPre(Packet<T> packet, PacketListener listener, CallbackInfo info) {
        PacketEvent packetEvent = new PacketEvent(packet, PacketEvent.Type.RECEIVE);
        EventManager.callEvent(packetEvent);
        if (packetEvent.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendPre(Packet<?> packet, CallbackInfo info) {
        PacketEvent packetEvent = new PacketEvent(packet, PacketEvent.Type.SEND);
        EventManager.callEvent(packetEvent);
        if (packetEvent.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "addHandlers", at = @At("RETURN"))
    private static void addHandlersHook(ChannelPipeline pipeline, NetworkSide side, boolean local, PacketSizeLogger packetSizeLogger, CallbackInfo ci) {
        ProxyConfig config = ProxyConfig.getInstance();
        Proxy proxy = config.getDefaultProxy();

        if (proxy != null && config.isProxyEnabled() && !proxy.isEmpty() && side == NetworkSide.CLIENTBOUND && !local) {
            InetSocketAddress proxyAddress = new InetSocketAddress(proxy.getIp(), proxy.getPort());

            if (proxy.type == Proxy.ProxyType.SOCKS4) {
                pipeline.addFirst("mind_socks4_proxy", new Socks4ProxyHandler(proxyAddress, proxy.username));
            } else {
                pipeline.addFirst("mind_socks5_proxy", new Socks5ProxyHandler(proxyAddress, proxy.username, proxy.password));
            }

            config.setLastUsedProxy(new Proxy(proxy));
        }
    }
}