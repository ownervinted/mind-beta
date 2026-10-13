package mind.beta.util.string.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import mind.beta.util.string.chat.helper.TextHelper;

public class ChatMessage {
    public static MutableText brandmessage() {
        return (MutableText) TextHelper.applyPredefinedGradient("Mind [beta]", "turquoise_blue", true);
    }

    public static MutableText blockesp() {
        return (MutableText) TextHelper.applyPredefinedGradient("Block Esp", "turquoise_blue", true);
    }

    public static void brandmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = TextHelper.applyPredefinedGradient("Mind [beta] --> ", "turquoise_blue", true);
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

}