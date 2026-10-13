package mind.beta;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.Window;
import mind.beta.util.render.draw.DrawEngine;
import mind.beta.util.render.draw.DrawEngineImpl;

public interface IMinecraft {
    MinecraftClient mc = MinecraftClient.getInstance();
    Window window = MinecraftClient.getInstance().getWindow();
    Tessellator tessellator = Tessellator.getInstance();
    RenderTickCounter tickCounter = mc.getRenderTickCounter();
    DrawEngine drawEngine = new DrawEngineImpl();

}
