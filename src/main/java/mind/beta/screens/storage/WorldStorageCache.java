package mind.beta.screens.storage;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldStorageCache {
    public record Snapshot(String title, List<ItemStack> items, long updatedAt) {}
    private record Key(String dimension, BlockPos pos) {}

    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Map<Key, Snapshot> CACHE = new HashMap<>();
    private static BlockPos pendingPos;
    private static String pendingDimension;
    private static long pendingUntil;
    private static ScreenHandler capturedHandler;
    private static Key currentKey;
    private static boolean wasInWorld;

    private WorldStorageCache() {}

    public static boolean supports(Block block) {
        return block instanceof ChestBlock || block instanceof EnderChestBlock
                || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock || block instanceof DispenserBlock
                || block instanceof DropperBlock;
    }

    public static void recordInteraction(BlockPos pos) {
        if (MC.world == null || !supports(MC.world.getBlockState(pos).getBlock())) return;
        pendingPos = pos.toImmutable();
        pendingDimension = MC.world.getRegistryKey().getValue().toString();
        pendingUntil = System.currentTimeMillis() + 1600L;
    }

    public static void tick() {
        if (MC.world == null || MC.player == null) {
            if (wasInWorld) CACHE.clear();
            wasInWorld = false;
            pendingPos = null;
            currentKey = null;
            capturedHandler = null;
            return;
        }
        wasInWorld = true;
        if (!(MC.currentScreen instanceof HandledScreen<?>)) {
            capturedHandler = null;
            currentKey = null;
            if (System.currentTimeMillis() > pendingUntil) pendingPos = null;
            return;
        }
        ScreenHandler handler = MC.player.currentScreenHandler;
        int slots = storageSlots(handler);
        if (slots == 0) return;
        if (handler != capturedHandler) {
            capturedHandler = handler;
            currentKey = null;
            if (pendingPos != null && System.currentTimeMillis() <= pendingUntil &&
                    MC.world.getRegistryKey().getValue().toString().equals(pendingDimension)) {
                currentKey = new Key(pendingDimension, pendingPos);
            }
            pendingPos = null;
        }
        if (currentKey == null || handler.slots.size() < slots) return;
        List<ItemStack> copy = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) copy.add(handler.getSlot(i).getStack().copy());
        String title = MC.currentScreen.getTitle().getString();
        CACHE.put(currentKey, new Snapshot(title, List.copyOf(copy), System.currentTimeMillis()));
    }

    public static int storageSlots(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler) return Math.max(9, handler.slots.size() - 36);
        if (handler instanceof ShulkerBoxScreenHandler) return 27;
        if (handler instanceof HopperScreenHandler) return 5;
        if (handler instanceof Generic3x3ContainerScreenHandler) return 9;
        return 0;
    }

    public static void remember(World world, BlockPos pos, String title, List<ItemStack> contents) {
        if (world == null || !supports(world.getBlockState(pos).getBlock())) return;
        List<ItemStack> copied = new ArrayList<>(contents.size());
        for (ItemStack stack : contents) copied.add(stack.copy());
        CACHE.put(new Key(world.getRegistryKey().getValue().toString(), pos.toImmutable()),
                new Snapshot(title, List.copyOf(copied), System.currentTimeMillis()));
    }

    public static Snapshot lookup(World world, BlockPos pos) {
        if (world == null || !supports(world.getBlockState(pos).getBlock())) return null;
        Key key = new Key(world.getRegistryKey().getValue().toString(), pos);
        return CACHE.get(key);
    }
}
