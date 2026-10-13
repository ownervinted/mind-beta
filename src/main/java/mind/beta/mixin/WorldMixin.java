package mind.beta.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import mind.beta.modules.impl.render.Ambience;

@Mixin(World.class)
public class WorldMixin {

    @Inject(
            method = "getRainGradient",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getRainGradient(
            float tickProgress,
            CallbackInfoReturnable<Float> cir
    ) {
        Ambience ambience = Ambience.getInstance();

        if (ambience == null || !ambience.isState()) {
            return;
        }

        if (ambience.weather.isSelected("Clear")) {
            cir.setReturnValue(0.0F);
            return;
        }

        cir.setReturnValue(ambience.getWeatherIntensity());
    }

    @Inject(
            method = "getThunderGradient",
            at = @At("HEAD"),
            cancellable = true
    )
    private void getThunderGradient(
            float tickProgress,
            CallbackInfoReturnable<Float> cir
    ) {
        Ambience ambience = Ambience.getInstance();

        if (ambience == null || !ambience.isState()) {
            return;
        }

        if (ambience.weather.isSelected("Thunderstorm")) {
            cir.setReturnValue(ambience.getWeatherIntensity());
            return;
        }

        cir.setReturnValue(0.0F);
    }
}