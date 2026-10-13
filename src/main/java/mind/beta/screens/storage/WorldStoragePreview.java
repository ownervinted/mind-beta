package mind.beta.screens.storage;

import mind.beta.modules.impl.render.StorageViewer;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class WorldStoragePreview {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final float SLOT = 18f;
    private static final float FADE_SPEED = 8f;
    private static final float TEXT_SIZE = 7f;
    private static float alpha;
    private static long previousFrame = System.nanoTime();
    private static BlockPos activePos;
    private static WorldStorageCache.Snapshot activeSnapshot;

    private WorldStoragePreview() {}

    public static void render(DrawContext context) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0f, (now - previousFrame) / 1_000_000_000f));
        previousFrame = now;

        BlockPos lookedAt = null;
        WorldStorageCache.Snapshot next = null;
        if (StorageViewer.isEnabled() && MC.world != null && MC.player != null
                && MC.currentScreen == null && MC.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            lookedAt = hit.getBlockPos();
            next = WorldStorageCache.lookup(MC.world, lookedAt);
        }

        if (next != null && (activePos == null || activePos.equals(lookedAt) || alpha <= 0.02f)) {
            activePos = lookedAt.toImmutable();
            activeSnapshot = next;
        }

        boolean visible = next != null && activePos != null && activePos.equals(lookedAt);
        float goal = visible ? 1f : 0f;
        alpha += (goal - alpha) * (1f - (float) Math.exp(-FADE_SPEED * dt));
        if (Math.abs(alpha - goal) < 0.004f) alpha = goal;

        if (alpha <= 0.004f) {
            alpha = 0f;
            activePos = null;
            activeSnapshot = null;
            return;
        }
        if (activePos == null || activeSnapshot == null || MC.world == null || MC.player == null) return;

        float[] screen = projectAboveBlock(activePos);
        if (screen == null) return;

        int size = activeSnapshot.items().size();
        int columns = size <= 5 ? 5 : 9;
        int rows = Math.max(1, (size + columns - 1) / columns);
        float width = columns * SLOT + 12f;
        float height = rows * SLOT + 27f;
        float x = screen[0] - width / 2f;
        float y = screen[1] - height - 8f;
        if (x < 0 || x + width > Render2D.getFixedScaledWidth() || y < 0 || y + height > Render2D.getFixedScaledHeight()) return;

        float fade = alpha * alpha * (3f - 2f * alpha);
        Render2D.rect(x, y, width, height, ThemeColors.color(10, 16, 26, opacity(220, fade)).getRGB(), 5f);
        Render2D.outline(x, y, width, height, 0.6f, ThemeColors.color(45, 107, 180, opacity(115, fade)).getRGB(), 5f);

        String title = activeSnapshot.title();
        if (title.length() > 23) title = title.substring(0, 21) + "..";
        Fonts.MEDIUM.draw(title, x + 6f, y + 7f, TEXT_SIZE,
                ThemeColors.color(235, 242, 255, opacity(245, fade)).getRGB());
        String cached = "Cached";
        Fonts.MEDIUM.draw(cached, x + width - 6f - Fonts.MEDIUM.getWidth(cached, 6f), y + 7f, 6f,
                ThemeColors.color(133, 172, 205, opacity(210, fade)).getRGB());

        float conversion = Render2D.getFixedScaledWidth() / (float) context.getScaledWindowWidth();
        for (int i = 0; i < size; i++) {
            float slotX = x + 6f + (i % columns) * SLOT;
            float slotY = y + 21f + (i / columns) * SLOT;
            Render2D.rect(slotX, slotY, 17f, 17f,
                    ThemeColors.color(38, 52, 71, opacity(110, fade)).getRGB(), 2f);
            ItemStack stack = activeSnapshot.items().get(i);
            if (stack.isEmpty()) continue;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(slotX / conversion, slotY / conversion);
            context.getMatrices().scale(1f / conversion, 1f / conversion);
            StorageItemAlpha.begin(fade);
            try {
                context.drawItem(stack, 0, 0);
            } finally {
                StorageItemAlpha.end();
                context.getMatrices().popMatrix();
            }
            if (stack.getCount() > 1) {
                String count = Integer.toString(stack.getCount());
                float textSize = 5.5f;
                Fonts.MEDIUM.draw(count,
                        slotX + 16f - Fonts.MEDIUM.getWidth(count, textSize),
                        slotY + 11f, textSize,
                        ThemeColors.color(255, 255, 255, opacity(255, fade)).getRGB());
            }
        }
    }

    private static int opacity(int base, float fraction) {
        return Math.max(0, Math.min(255, Math.round(base * fraction)));
    }

    private static float[] projectAboveBlock(BlockPos pos) {
        Camera camera = MC.gameRenderer.getCamera();
        Vec3d relative = Vec3d.ofCenter(pos).add(0, 0.9, 0).subtract(camera.getCameraPos());
        Vector3f view = camera.getRotation().conjugate(new org.joml.Quaternionf())
                .transform(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        if (view.z >= -0.05f) return null;

        float width = Render2D.getFixedScaledWidth();
        float height = Render2D.getFixedScaledHeight();
        float fov = MC.options.getFov().getValue().floatValue();
        float focal = (float) (height / (2.0 * Math.tan(Math.toRadians(fov) / 2.0)));
        return new float[]{width * 0.5f + view.x * focal / -view.z,
                height * 0.5f - view.y * focal / -view.z};
    }
}
