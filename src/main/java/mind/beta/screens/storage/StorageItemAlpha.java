package mind.beta.screens.storage;

import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;

import java.util.IdentityHashMap;
import java.util.Map;

public final class StorageItemAlpha {
    private static final Map<ItemGuiElementRenderState, Float> ALPHAS = new IdentityHashMap<>();
    private static float recordingAlpha = -1f;

    private StorageItemAlpha() {
    }

    public static void begin(float alpha) {
        recordingAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    public static void end() {
        recordingAlpha = -1f;
    }

    public static void capture(ItemGuiElementRenderState state) {
        if (recordingAlpha >= 0f) {
            ALPHAS.put(state, recordingAlpha);
        }
    }

    public static int modulate(ItemGuiElementRenderState state, int original) {
        Float alpha = ALPHAS.get(state);
        if (alpha == null) {
            return original;
        }

        float fraction = Math.max(0f, Math.min(1f, alpha));
        if (fraction >= 0.999f) {
            return original;
        }

        int a = Math.round(((original >>> 24) & 255) * fraction);
        int r = Math.round(((original >>> 16) & 255) * fraction);
        int g = Math.round(((original >>> 8) & 255) * fraction);
        int b = Math.round((original & 255) * fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void clear() {
        ALPHAS.clear();
        recordingAlpha = -1f;
    }
}
