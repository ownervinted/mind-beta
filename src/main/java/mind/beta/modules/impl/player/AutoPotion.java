/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.events.api.types.EventType;
import mind.beta.mixin.ClientWorldAccessor;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationRequest;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.util.math.TaskPriority;
import mind.beta.util.timer.StopWatch;

@SuppressWarnings("all")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoPotion extends ModuleStructure {

    final BooleanSetting autoOff = new BooleanSetting("Auto Disable", "Automatically disable the module after use")
            .setValue(false);

    final MultiSelectSetting potions = new MultiSelectSetting("Throw", "Select potions to throw automatically")
            .value("Strength", "Speed", "Fire Resistance")
            .selected("Strength", "Speed");

    final StopWatch timer = new StopWatch();

    boolean spoofed = false;
    boolean isActivePotion = false;
    int rotationTicks = 0;
    int selectedSlot = -1;
    final float THROW_PITCH = 90f;
    final int ROTATION_WAIT_TICKS = 2;

    public AutoPotion() {
        super("AutoPotion", ModuleCategory.PLAYER);
        settings(potions, autoOff);
    }

    @Override
    public void deactivate() {
        isActivePotion = false;
        spoofed = false;
        rotationTicks = 0;
        selectedSlot = -1;
        RotationManager.INSTANCE.startReturning();
    }

    private enum PotionType {
        STRENGTH(StatusEffects.STRENGTH, "Strength"),
        SPEED(StatusEffects.SPEED, "Speed"),
        FIRE_RESISTANCE(StatusEffects.FIRE_RESISTANCE, "Fire Resistance");

        final RegistryEntry<StatusEffect> effect;
        final String settingName;

        PotionType(RegistryEntry<StatusEffect> effect, String settingName) {
            this.effect = effect;
            this.settingName = settingName;
        }

        public boolean isEnabled(AutoPotion module) {
            return module.potions.isSelected(this.settingName);
        }
    }

    private int findPotionSlot(PotionType type) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isOf(Items.SPLASH_POTION)) {
                PotionContentsComponent potionComponent = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (potionComponent != null) {
                    for (StatusEffectInstance effect : potionComponent.getEffects()) {
                        if (effect.getEffectType() == type.effect) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    private boolean hasEffect(RegistryEntry<StatusEffect> effect) {
        return mc.player != null && mc.player.hasStatusEffect(effect);
    }

    private boolean canBuff(PotionType type) {
        if (hasEffect(type.effect)) return false;
        return type.isEnabled(this) && findPotionSlot(type) != -1;
    }

    private boolean canBuff() {
        if (mc.player == null || mc.world == null) return false;

        return (canBuff(PotionType.STRENGTH) || canBuff(PotionType.SPEED) || canBuff(PotionType.FIRE_RESISTANCE))
                && mc.player.isOnGround()
                && timer.finished(500);
    }

    private boolean isActive() {
        return isActivePotion || canBuff(PotionType.STRENGTH) || canBuff(PotionType.SPEED) || canBuff(PotionType.FIRE_RESISTANCE);
    }

    private boolean shouldThrow() {
        if (mc.player == null || mc.world == null) return false;

        return isActive()
                && canBuff()
                && mc.world.getBlockState(mc.player.getBlockPos().down()).getBlock() != Blocks.AIR;
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == EventType.PRE) {
            if (shouldThrow() || spoofed) {
                performRotation();
            }
        }
    }

    private void performRotation() {
        Rotation throwRotation = new Rotation(mc.player.getYaw(), THROW_PITCH);
        RotationRequest config = new RotationRequest(new LinearRotation(), true, true);

        RotationManager.INSTANCE.rotateTo(
                throwRotation,
                3,
                config,
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );

        if (!spoofed) {
            spoofed = true;
            isActivePotion = true;
            rotationTicks = 0;
            selectedSlot = mc.player.getInventory().getSelectedSlot();
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (isActivePotion && !shouldThrow() && !spoofed) {
            isActivePotion = false;
            if (autoOff.isValue()) {
                setState(false);
            }
        }

        if (spoofed) {
            processThrow();
        }
    }

    private void processThrow() {
        rotationTicks++;

        Rotation currentRotation = RotationManager.INSTANCE.getRotation();
        boolean rotationReady = currentRotation != null && currentRotation.getPitch() >= 80f;
        boolean waitedEnough = rotationTicks >= ROTATION_WAIT_TICKS;

        if (rotationReady && waitedEnough) {
            boolean threwAny = false;

            if (canBuff(PotionType.STRENGTH)) {
                throwPotion(PotionType.STRENGTH);
                threwAny = true;
            }
            if (canBuff(PotionType.SPEED)) {
                throwPotion(PotionType.SPEED);
                threwAny = true;
            }
            if (canBuff(PotionType.FIRE_RESISTANCE)) {
                throwPotion(PotionType.FIRE_RESISTANCE);
                threwAny = true;
            }

            if (selectedSlot != -1) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            }

            timer.reset();
            spoofed = false;
            rotationTicks = 0;
            isActivePotion = false;

            if (autoOff.isValue() || !threwAny) {
                setState(false);
            }
        }

        if (rotationTicks > 10) {
            resetThrowState();
        }
    }

    private void resetThrowState() {
        spoofed = false;
        rotationTicks = 0;
        isActivePotion = false;
        if (selectedSlot != -1) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
        }
    }

    private void throwPotion(PotionType type) {
        if (!type.isEnabled(this) || hasEffect(type.effect)) return;
        if (mc.player == null || mc.player.networkHandler == null) return;

        int slot = findPotionSlot(type);
        if (slot == -1) return;

        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), THROW_PITCH));
    }

    private void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.player == null || mc.player.networkHandler == null || mc.world == null) return;

        try {
            ClientWorldAccessor worldAccessor = (ClientWorldAccessor) mc.world;
            PendingUpdateManager pendingUpdateManager = worldAccessor.getPendingUpdateManager().incrementSequence();

            int sequence = pendingUpdateManager.getSequence();
            mc.player.networkHandler.sendPacket(packetCreator.predict(sequence));

            pendingUpdateManager.close();
        } catch (Exception e) {
            mc.player.networkHandler.sendPacket(packetCreator.predict(0));
        }
    }
}