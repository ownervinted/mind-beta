package mind.beta.screens.storage;

import mind.beta.modules.impl.render.StorageViewer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class WorldStorageProbe {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final long HOVER_MS = 600L;
    private static final long RETRY_MS = 15000L;
    private static final long TIMEOUT_MS = 3000L;
    private static final double MAX_DISTANCE_SQUARED = 16.0;

    private record Target(String dimension, BlockPos pos) {}

    private static final Map<Target, Long> lastAttempt = new HashMap<>();
    private static Target hoveredTarget;
    private static long hoveredSince;
    private static Target pendingTarget;
    private static long pendingSince;
    private static boolean hiddenScreen;
    private static int pendingSyncId = -1;
    private static String pendingTitle;
    private static int closeDelayTicks = -1;
    private static boolean wasConnected;

    private WorldStorageProbe() {}

    public static boolean beginVanillaScan() {
        if (!StorageViewer.isScanEnabled() || MC.player == null || MC.world == null
                || MC.interactionManager == null || pendingTarget != null
                || MC.currentScreen != null || MC.player.isSneaking()
                || MC.player.isSprinting() || MC.player.isUsingItem()
                || !MC.player.isOnGround()
                || MC.options.useKey.isPressed() || MC.options.attackKey.isPressed()
                || MC.player.getVelocity().horizontalLengthSquared() > 0.0004
                || MC.player.currentScreenHandler != MC.player.playerScreenHandler
                || !(MC.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            clearHover();
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        if (!WorldStorageCache.supports(MC.world.getBlockState(pos).getBlock())
                || MC.player.getEyePos().squaredDistanceTo(hit.getPos()) > MAX_DISTANCE_SQUARED
                || WorldStorageCache.lookup(MC.world, pos) != null) {
            clearHover();
            return false;
        }

        Target target = new Target(MC.world.getRegistryKey().getValue().toString(), pos.toImmutable());
        long now = System.currentTimeMillis();
        if (!target.equals(hoveredTarget)) {
            hoveredTarget = target;
            hoveredSince = now;
            return false;
        }

        if (now - hoveredSince < HOVER_MS
                || now - lastAttempt.getOrDefault(target, 0L) < RETRY_MS) {
            return false;
        }

        if (lastAttempt.size() > 512) lastAttempt.clear();
        lastAttempt.put(target, now);
        pendingTarget = target;
        pendingSince = now;
        hiddenScreen = false;
        pendingSyncId = -1;
        pendingTitle = null;
        closeDelayTicks = -1;
        clearHover();
        return true;
    }

    public static void tick() {
        if (MC.player == null || MC.world == null) {
            if (wasConnected) lastAttempt.clear();
            wasConnected = false;
            reset();
            return;
        }
        wasConnected = true;

        if (pendingTarget == null) return;

        if (closeDelayTicks >= 0) {
            if (--closeDelayTicks <= 0) {
                closePendingHandler();
                reset();
            }
            return;
        }

        if (!StorageViewer.isScanEnabled() ||
                !pendingTarget.dimension().equals(MC.world.getRegistryKey().getValue().toString()) ||
                System.currentTimeMillis() - pendingSince > TIMEOUT_MS) {
            closePendingHandler();
            reset();
        }
    }

    public static boolean suppressScreen(Screen screen) {
        if (!StorageViewer.isScanEnabled() || pendingTarget == null
                || hiddenScreen || MC.currentScreen != null
                || !(screen instanceof HandledScreen<?> handled)) return false;
        ScreenHandler handler = handled.getScreenHandler();
        if (WorldStorageCache.storageSlots(handler) <= 0) return false;
        hiddenScreen = true;
        pendingTitle = screen.getTitle().getString();
        return true;
    }

    public static void onOpenScreen(OpenScreenS2CPacket packet) {
        if (pendingTarget == null || !hiddenScreen) return;
        pendingSyncId = packet.getSyncId();
        pendingTitle = packet.getName().getString();
    }

    public static void onInventory(InventoryS2CPacket packet) {
        if (pendingTarget == null || !hiddenScreen || pendingSyncId < 0
                || packet.syncId() != pendingSyncId || MC.player == null || MC.world == null) return;

        ScreenHandler handler = MC.player.currentScreenHandler;
        if (handler == null || handler.syncId != pendingSyncId) return;
        int slots = WorldStorageCache.storageSlots(handler);
        if (slots <= 0 || packet.contents().size() < slots) return;
        if (!MC.world.getRegistryKey().getValue().toString().equals(pendingTarget.dimension())) {
            closePendingHandler();
            reset();
            return;
        }

        WorldStorageCache.remember(MC.world, pendingTarget.pos(),
                pendingTitle == null ? "Storage" : pendingTitle,
                packet.contents().subList(0, slots));
        closeDelayTicks = 2;
    }

    private static void closePendingHandler() {
        if (!hiddenScreen || pendingSyncId < 0 || MC.player == null) return;
        if (MC.player.currentScreenHandler != MC.player.playerScreenHandler
                && MC.player.currentScreenHandler.syncId == pendingSyncId) {
            MC.player.closeHandledScreen();
        }
    }

    private static void clearHover() {
        hoveredTarget = null;
        hoveredSince = 0L;
    }

    private static void reset() {
        pendingTarget = null;
        pendingSince = 0L;
        hiddenScreen = false;
        pendingSyncId = -1;
        pendingTitle = null;
        closeDelayTicks = -1;
        clearHover();
    }
}
