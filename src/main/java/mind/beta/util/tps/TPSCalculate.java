package mind.beta.util.tps;

import lombok.Getter;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;

@Getter
public class TPSCalculate {

    private static TPSCalculate instance;

    private float TPS = 20;
    private float adjustTicks = 0;
    private long timestamp;

    public TPSCalculate() {
        instance = this;
        Initialization.getInstance().getManager().getEventManager().register(this);
    }

    public static TPSCalculate getInstance() {
        return instance;
    }

    @EventHandler
    private void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            updateTPS();
        }
    }

    private void updateTPS() {
        long delay = System.nanoTime() - timestamp;
        float maxTPS = 20;
        float rawTPS = maxTPS * (1e9f / delay);
        float boundedTPS = MathHelper.clamp(rawTPS, 0, maxTPS);
        TPS = (float) round(boundedTPS);
        adjustTicks = boundedTPS - maxTPS;
        timestamp = System.nanoTime();
    }

    public double round(double input) {
        return Math.round(input * 100.0) / 100.0;
    }

    public float getTpsRounded() {
        return (float) (Math.round(TPS * 2) / 2.0);
    }
}