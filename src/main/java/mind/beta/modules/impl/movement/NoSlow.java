/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.impl.UsingItemEvent;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.util.Instance;
import mind.beta.util.inventory.script.Script;
import mind.beta.util.string.PlayerInteractionHelper;
import mind.beta.util.timer.StopWatch;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoSlow extends ModuleStructure {
    public static NoSlow getInstance() {
        return Instance.get(NoSlow.class);
    }

    private final StopWatch notifWatch = new StopWatch();
    private final Script script = new Script();
    private boolean finish;

    public final SelectSetting itemMode = new SelectSetting("Item Mode").value("Grim", "Matrix", "SlothAC", "Polar");

    public NoSlow() {
        super("NoSlow", "No Slow", ModuleCategory.MOVEMENT);
        settings(itemMode);
    }

    private int ticks = 0;
    private int cycleCounter = 0;

    private boolean isOnSnowOrCarpet() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos playerPos = mc.player.getBlockPos();
        var block = mc.world.getBlockState(playerPos).getBlock();
        return block == Blocks.SNOW ||
                block == Blocks.WHITE_CARPET ||
                block == Blocks.ORANGE_CARPET ||
                block == Blocks.MAGENTA_CARPET ||
                block == Blocks.LIGHT_BLUE_CARPET ||
                block == Blocks.YELLOW_CARPET ||
                block == Blocks.LIME_CARPET ||
                block == Blocks.PINK_CARPET ||
                block == Blocks.GRAY_CARPET ||
                block == Blocks.LIGHT_GRAY_CARPET ||
                block == Blocks.CYAN_CARPET ||
                block == Blocks.PURPLE_CARPET ||
                block == Blocks.BLUE_CARPET ||
                block == Blocks.BROWN_CARPET ||
                block == Blocks.GREEN_CARPET ||
                block == Blocks.RED_CARPET ||
                block == Blocks.BLACK_CARPET;
    }

    private boolean shouldLeaveShieldVanilla() {
        if (mc.player == null) {
            return false;
        }

        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState()) {
            return false;
        }

        boolean usingShield = mc.player.isUsingItem()
                && !mc.player.getActiveItem().isEmpty()
                && mc.player.getActiveItem().getUseAction() == UseAction.BLOCK;

        return usingShield || AttackManager.isShieldTransitionActive();
    }

    @EventHandler
    public void onUpdate(TickEvent event) {
        if (mc.player == null) return;

        if (shouldLeaveShieldVanilla()) {
            ticks = 0;
            cycleCounter = 0;
            return;
        }

        if (itemMode.isSelected("Matrix") || itemMode.isSelected("SlothAC")) {
            if (!mc.player.isUsingRiptide()) {
                if (mc.player.isUsingItem()) {
                    ticks++;
                } else {
                    ticks = 0;
                    cycleCounter = 0;
                }
            }
        } else {
            if (mc.player.getActiveHand() == Hand.MAIN_HAND || mc.player.getActiveHand() == Hand.OFF_HAND) {
                ticks++;
            } else {
                ticks = 0;
            }
        }
    }

    @EventHandler
    public void onUsingItem(UsingItemEvent e) {
        if (mc.player == null) return;

        if (shouldLeaveShieldVanilla()) {
            return;
        }

        Hand first = mc.player.getActiveHand();
        Hand second = first.equals(Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;

        switch (e.getType()) {
            case EventType.ON -> {
                handleItemUse(e, first, second);
            }
            case EventType.POST -> {
                while (!script.isFinished()) script.update();
            }
        }
    }

    private void handleItemUse(UsingItemEvent e, Hand first, Hand second) {
        switch (itemMode.getSelected()) {
            case "Grim" -> {
                if (mc.player.getOffHandStack().getUseAction().equals(UseAction.NONE) || mc.player.getMainHandStack().getUseAction().equals(UseAction.NONE)) {
                    PlayerInteractionHelper.interactItem(first);
                    PlayerInteractionHelper.interactItem(second);
                    e.cancel();
                }
            }
            case "Matrix" -> {
                int[] thresholds;

                if (mc.player.isJumping()) {
                    thresholds = new int[]{2, 2, 2};
                } else {
                    thresholds = new int[]{2, 3, 3};
                }

                int threshold = thresholds[cycleCounter % thresholds.length];

                if (ticks >= threshold) {
                    e.cancel();
                    ticks = 0;
                    cycleCounter++;
                }
            }
            case "SlothAC" -> {
                int[] thresholds = new int[]{2, 2, 2};
                int threshold = thresholds[cycleCounter % 2];
                if (ticks >= threshold) {
                    e.cancel();
                    ticks = 0;
                    cycleCounter++;
                }
            }
            case "Polar" -> {
                if (ticks > 0F && mc.player.getItemUseTime() > 1F) {
                    boolean mainHandCrossbow = mc.player.getMainHandStack().getItem() instanceof CrossbowItem;
                    boolean offHandCrossbow = mc.player.getOffHandStack().getItem() instanceof CrossbowItem;

                    if (mainHandCrossbow || offHandCrossbow) {
                        if (ticks > 0F && mc.player.getItemUseTime() > 1) {
                            e.cancel();
                            ticks = 0;
                        }
                    } else if (mc.player.isOnGround() && isOnSnowOrCarpet()) {
                        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                                mc.player.getBlockPos().up(),
                                Direction.DOWN
                        ));
                        mc.player.setVelocity(
                                mc.player.getVelocity().x,
                                mc.player.getVelocity().y,
                                mc.player.getVelocity().z
                        );
                        e.cancel();
                        ticks = 0;
                    }
                }
            }
        }
    }
}