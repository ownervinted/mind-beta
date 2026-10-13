/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import mind.beta.IMinecraft;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.render.ambience.FogSettings;
import mind.beta.modules.impl.render.ambience.SkySettings;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;

@FieldDefaults(level = AccessLevel.PUBLIC)
public class Ambience extends ModuleStructure implements IMinecraft {

    public static Ambience getInstance() {
        return Instance.get(Ambience.class);
    }

    public SliderSettings time = new SliderSettings("Time", "Time of day (0-24000)")
            .range(0, 24000)
            .setValue(1000);

    public SelectSetting weather = new SelectSetting("Weather")
            .value("Clear", "Rain", "Snow", "Thunderstorm");

    public SliderSettings weatherIntensity = new SliderSettings("Intensity", "Precipitation intensity")
            .range(0, 100)
            .setValue(100);

    public final SkySettings sky = new SkySettings();
    public final FogSettings fog = new FogSettings();

    private double animatedTime = 1000;

    public Ambience() {
        super("Ambience", "Change the world's atmosphere", ModuleCategory.RENDER);
        settings(time, weather, weatherIntensity);
        settings(sky.settings());
        settings(fog.settings());
    }

    @Override
    public void activate() {
        animatedTime = time.getValue();
        updateWeather();
    }

    @Override
    public void deactivate() {
        if (mc.world != null) {
            mc.world.setRainGradient(0.0F);
            mc.world.setThunderGradient(0.0F);
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        double targetTime = time.getValue();
        double speed = 150 / 1000.0;
        double diff = targetTime - animatedTime;

        animatedTime += diff * speed;
        updateWeather();
    }

    private void updateWeather() {
        if (mc.world == null) {
            return;
        }

        float intensity = getWeatherIntensity();

        if (weather.isSelected("Clear")) {
            mc.world.setRainGradient(0.0F);
            mc.world.setThunderGradient(0.0F);
            return;
        }

        if (weather.isSelected("Rain")) {
            mc.world.setRainGradient(intensity);
            mc.world.setThunderGradient(0.0F);
            return;
        }

        if (weather.isSelected("Snow")) {
            mc.world.setRainGradient(intensity);
            mc.world.setThunderGradient(0.0F);
            return;
        }

        if (weather.isSelected("Thunderstorm")) {
            mc.world.setRainGradient(intensity);
            mc.world.setThunderGradient(intensity);
        }
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            event.cancel();
        }
    }

    public long getCustomTime() {
        return (long) animatedTime;
    }

    public float getWeatherIntensity() {
        return Math.max(0.0F, Math.min(1.0F, weatherIntensity.getValue() / 100.0F));
    }

    public float getActiveWeatherIntensity() {
        return weather.isSelected("Clear") ? 0.0F : getWeatherIntensity();
    }
}
