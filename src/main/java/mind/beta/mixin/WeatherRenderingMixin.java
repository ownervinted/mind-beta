package mind.beta.mixin;

import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mind.beta.modules.impl.render.Ambience;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {

    @Inject(
            method = "getPrecipitationAt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getPrecipitationAt(
            World world,
            BlockPos pos,
            CallbackInfoReturnable<Biome.Precipitation> cir
    ) {
        Ambience ambience = Ambience.getInstance();

        if (ambience == null || !ambience.isState()) {
            return;
        }

        if (ambience.weather.isSelected("Clear")) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }

        if (ambience.weather.isSelected("Snow")) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
            return;
        }

        if (ambience.weather.isSelected("Rain")
                || ambience.weather.isSelected("Thunderstorm")) {
            cir.setReturnValue(Biome.Precipitation.RAIN);
        }
    }
}