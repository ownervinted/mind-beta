package mind.beta.modules.impl.combat;

import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.impl.combat.macros.MacroDamage;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Macros extends ModuleStructure {
    private final BooleanSetting crystals = new BooleanSetting("Crystals", "Break your placed crystal under your crosshair")
            .setValue(true);
    private final BooleanSetting anchors = new BooleanSetting("Anchors", "Charge and activate your placed anchor under your crosshair")
            .setValue(true);
    private final BindSetting anchorBind = new BindSetting("Anchor Bind", "Place, charge once, select anchor and activate");
    private final BindSetting crystalBind = new BindSetting("Crystal Bind", "Place obsidian if needed, then a crystal under your view");
    private final MultiSelectSetting overrides = new MultiSelectSetting("Overrides", "Self Damage bypasses the damage cap; Health Reserve bypasses survival checks")
            .value("Self Damage", "Health Reserve");
    private final BooleanSetting awaitConfirmation = new BooleanSetting("Await Confirmation", "Wait for server block updates between anchor stages")
            .setValue(false);
    private final SliderSettings anchorSlot = new SliderSettings("Anchor Slot", "0: automatic; 1-9: server anchor hotbar slot")
            .range(0.0F, 9.0F).setValue(0.0F);
    private final SliderSettings glowstoneSlot = new SliderSettings("Glowstone Slot", "0: automatic; 1-9: server glowstone hotbar slot")
            .range(0.0F, 9.0F).setValue(0.0F);
    private final BooleanSetting showStatus = new BooleanSetting("Show Status", "Show macro stage and damage checks under the crosshair")
            .setValue(true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", "Select macro items from the hotbar")
            .setValue(true);
    private final SelectSetting swapMode = new SelectSetting("Swap Mode")
            .value("Legit", "Instant").selected("Instant");
    private final SliderSettings maxSelfDamage = new SliderSettings("Max Self Damage", "Maximum explosion damage to yourself in HP")
            .range(0.0F, 20.0F).setValue(6.0F);
    private final SliderSettings healthReserve = new SliderSettings("Health Reserve", "HP remaining after the explosion")
            .range(0.5F, 10.0F).setValue(2.0F);
    private final BooleanSetting restoreSlot = new BooleanSetting("Restore Slot", "Restore your selected slot after macro actions")
            .setValue(true);
    private final SliderSettings charges = new SliderSettings("Charges", "Anchor charges before activation")
            .range(1.0F, 4.0F).setValue(1.0F);
    private final SliderSettings delay = new SliderSettings("Action Delay", "Delay between actions in ticks")
            .range(0.0F, 5.0F).setValue(0.0F);

    private final Map<BlockPos, CrystalTask> crystalTasks = new LinkedHashMap<>();
    private final Map<BlockPos, AnchorTask> anchorTasks = new LinkedHashMap<>();
    private volatile Object activeWorld;
    private volatile int generation;
    private boolean ownAction;
    private int nextActionTick;
    private int lastTick = Integer.MIN_VALUE;
    private int legitOldSlot = -1;
    private int legitSelectedSlot = -1;
    private int legitRestoreTick = -1;
    private int activationOffhandSwapSlot = -1;
    private int activationOffhandRestoreTick = -1;
    private float lastSelfDamage;
    private BoundSequence boundSequence;
    private String macroState = "Idle";
    private long statusUntil;
    private String shownStatus = "";
    private long lastStatusTime;

    public Macros() {
        super("Macros", "Crystal and anchor macros", ModuleCategory.COMBAT);
        settings(crystals, anchors, anchorBind, crystalBind, autoSwitch, swapMode, restoreSlot,
                awaitConfirmation, anchorSlot, glowstoneSlot, showStatus, overrides, maxSelfDamage, healthReserve, charges, delay);
    }

    public static Macros getInstance() {
        return Instance.get(Macros.class);
    }

    @Override
    public void activate() {
        reset();
    }

    @Override
    public void deactivate() {
        reset();
    }

    private void reset() {
        restoreLegitSlot(true);
        restoreActivationOffhand(true);
        generation++;
        crystalTasks.clear();
        anchorTasks.clear();
        activeWorld = mc.world;
        ownAction = false;
        nextActionTick = 0;
        lastTick = Integer.MIN_VALUE;
        lastSelfDamage = 0.0F;
        boundSequence = null;
        macroState = "Idle";
        statusUntil = 0L;
        shownStatus = "";
        activationOffhandSwapSlot = -1;
        activationOffhandRestoreTick = -1;
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (!isState() || event.isCancelled() || mc.player == null || mc.world == null) {
            return;
        }
        if (event.isSend()) {
            return;
        }
        if (event.getPacket() instanceof BlockUpdateS2CPacket packet) {
            queueConfirmation(packet.getPos(), packet.getState());
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::queueConfirmation);
        }
    }

    public void observeManualInteraction(Hand hand, BlockHitResult hit) {
        if (!isState() || ownAction || mc.player == null || mc.world == null
                || !mc.options.useKey.isPressed() || mc.currentScreen != null) {
            return;
        }
        BlockHitResult viewed = blockUnderView(mc.player.getBlockInteractionRange());
        if (viewed == null || !viewed.getBlockPos().equals(hit.getBlockPos()) || viewed.getSide() != hit.getSide()) {
            return;
        }
        if (activeWorld != mc.world) {
            reset();
        }
        ItemStack stack = mc.player.getStackInHand(hand);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        long expires = System.currentTimeMillis() + taskLifetime();
        if (crystals.isValue() && stack.isOf(Items.END_CRYSTAL)) {
            BlockPos base = hit.getBlockPos().toImmutable();
            BlockState state = mc.world.getBlockState(base);
            if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
                return;
            }
            Set<Integer> existing = new HashSet<>();
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity crystal && atBase(crystal, base)) {
                    existing.add(crystal.getId());
                }
            }
            crystalTasks.putIfAbsent(base, new CrystalTask(expires, existing));
            trim(crystalTasks);
        } else if (anchors.isValue() && anchorDimension()
                && mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)
                && mc.world.getBlockState(hit.getBlockPos()).get(RespawnAnchorBlock.CHARGES) == 0) {
            int preferred = mc.player.getInventory().getSelectedSlot();
            AnchorTask task = new AnchorTask(expires, preferred, true);
            if (isMacroItem(stack, Items.GLOWSTONE)) {
                task.waitingForCharge = 1;
                task.lastChargeTime = System.currentTimeMillis();
                nextActionTick = Math.max(nextActionTick, mc.player.age + 1);
            }
            anchorTasks.put(hit.getBlockPos().toImmutable(), task);
            trim(anchorTasks);
        } else if (anchors.isValue() && isMacroItem(stack, Items.RESPAWN_ANCHOR) && anchorDimension()) {
            BlockPos pos = new ItemPlacementContext(mc.player, hand, stack, hit).getBlockPos().toImmutable();
            anchorTasks.putIfAbsent(pos, new AnchorTask(expires, mc.player.getInventory().getSelectedSlot(), false));
            trim(anchorTasks);
        }
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (!isState() || event.action() != 1 || mc.currentScreen != null
                || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isUsingItem() || !mc.player.isAlive() || mc.player.isSpectator()) {
            return;
        }
        if (activeWorld != mc.world) {
            reset();
        }
        if (matchesBind(anchorBind, event) && anchors.isValue()) {
            startAnchorSequence();
        } else if (matchesBind(crystalBind, event) && crystals.isValue()) {
            startCrystalSequence();
        }
    }

    private boolean matchesBind(BindSetting setting, KeyEvent event) {
        return setting.getKey() >= 0 && setting.getKey() == event.key()
                && (setting.getType() == 0 ? event.type() == InputUtil.Type.MOUSE : event.type() == InputUtil.Type.KEYSYM);
    }

    private void startAnchorSequence() {
        if (boundSequence != null || !anchorDimension()) {
            return;
        }
        BlockHitResult hit = blockUnderView(mc.player.getBlockInteractionRange());
        if (hit == null) {
            macroState = "Aim at a block";
            return;
        }
        int preferred = findSlot(Items.RESPAWN_ANCHOR);
        if (preferred < 0) {
            preferred = mc.player.getInventory().getSelectedSlot();
        }
        if (mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            AnchorTask task = new AnchorTask(System.currentTimeMillis() + taskLifetime(), preferred, true);
            task.confirmedCharges = mc.world.getBlockState(hit.getBlockPos()).get(RespawnAnchorBlock.CHARGES);
            anchorTasks.put(hit.getBlockPos().toImmutable(), task);
            return;
        }
        if (!hasItem(Items.RESPAWN_ANCHOR) || !hasItem(Items.GLOWSTONE)) {
            macroState = "Need anchor and glowstone";
            return;
        }
        BlockPos pos = placementPosition(hit, Items.RESPAWN_ANCHOR);
        if (!canPlaceBase(pos)) {
            macroState = "Anchor position blocked";
            return;
        }
        boundSequence = new BoundSequence(BoundStage.ANCHOR, pos, preferred,
                System.currentTimeMillis() + taskLifetime());
    }

    private void startCrystalSequence() {
        if (boundSequence != null) {
            return;
        }
        BlockHitResult hit = blockUnderView(mc.player.getBlockInteractionRange());
        if (hit == null || !hasItem(Items.END_CRYSTAL)) {
            macroState = hit == null ? "Aim at a block" : "Need crystals";
            return;
        }
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        boolean existingBase = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
        BlockPos base = existingBase ? hit.getBlockPos().toImmutable() : placementPosition(hit, Items.OBSIDIAN);
        if (!existingBase && (!hasItem(Items.OBSIDIAN) || !canPlaceBase(base))) {
            macroState = "Need a free obsidian position";
            return;
        }
        boundSequence = new BoundSequence(existingBase ? BoundStage.CRYSTAL : BoundStage.OBSIDIAN,
                base, mc.player.getInventory().getSelectedSlot(), System.currentTimeMillis() + taskLifetime());
    }

    private BlockPos placementPosition(BlockHitResult hit, Item item) {
        return new ItemPlacementContext(mc.player, Hand.MAIN_HAND, new ItemStack(item), hit).getBlockPos().toImmutable();
    }

    private boolean hasItem(Item item) {
        return isMacroItem(mc.player.getMainHandStack(), item) || isMacroItem(mc.player.getOffHandStack(), item) || findSlot(item) >= 0;
    }

    private boolean canPlaceBase(BlockPos pos) {
        if (!mc.world.isInHeightLimit(pos.getY()) || !mc.world.getWorldBorder().contains(pos)
                || !mc.world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        Box box = new Box(pos);
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive() && !entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }
        return true;
    }

    private boolean canPlaceCrystal(BlockPos base) {
        BlockState state = mc.world.getBlockState(base);
        if ((!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) || !mc.world.isAir(base.up())) {
            return false;
        }
        Box box = new Box(base.getX(), base.getY() + 1.0, base.getZ(), base.getX() + 1.0, base.getY() + 3.0, base.getZ() + 1.0);
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive() && !entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }
        return true;
    }

    private boolean processBoundSequence() {
        BoundSequence sequence = boundSequence;
        if (sequence == null) {
            return false;
        }
        if (sequence.expires <= System.currentTimeMillis()
                || sequence.stage == BoundStage.ANCHOR && !anchors.isValue()
                || sequence.stage != BoundStage.ANCHOR && !crystals.isValue()) {
            boundSequence = null;
            return false;
        }
        BlockHitResult hit = blockUnderView(mc.player.getBlockInteractionRange());
        if (hit == null) {
            macroState = "Waiting for your view";
            return false;
        }
        if (sequence.placementSent && sequence.stage == BoundStage.ANCHOR
                && mc.world.getBlockState(sequence.pos).isOf(Blocks.RESPAWN_ANCHOR)) {
            AnchorTask task = new AnchorTask(sequence.expires, sequence.selectedSlot, true);
            task.confirmedCharges = mc.world.getBlockState(sequence.pos).get(RespawnAnchorBlock.CHARGES);
            anchorTasks.put(sequence.pos, task);
            boundSequence = null;
            macroState = "Anchor placed";
            return false;
        }
        if (sequence.placementSent && System.currentTimeMillis() - sequence.lastPlacementTime < retryLifetime()) {
            macroState = "Waiting for server placement";
            return false;
        }
        if (sequence.attempts >= 8) {
            macroState = "Server did not place the anchor";
            boundSequence = null;
            return false;
        }
        Item item = sequence.stage == BoundStage.ANCHOR ? Items.RESPAWN_ANCHOR
                : sequence.stage == BoundStage.OBSIDIAN ? Items.OBSIDIAN : Items.END_CRYSTAL;
        if (sequence.stage == BoundStage.CRYSTAL) {
            if (!hit.getBlockPos().equals(sequence.pos) || !canPlaceCrystal(sequence.pos)) {
                macroState = "Aim at the obsidian base";
                return false;
            }
        } else if (!placementPosition(hit, item).equals(sequence.pos) || !canPlaceBase(sequence.pos)) {
            macroState = "Waiting for your placement view";
            return false;
        }
        SwitchResult selected = prepareItem(item);
        if (selected.hand() == null) {
            return true;
        }
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        boolean serverAnchor = sequence.stage == BoundStage.ANCHOR
                && isServerItem(mc.player.getStackInHand(selected.hand()), Items.RESPAWN_ANCHOR);
        ownAction = true;
        try {
            ActionResult result = mc.interactionManager.interactBlock(mc.player, selected.hand(), hit);
            if (serverAnchor) {
                sequence.placementSent = true;
                sequence.selectedSlot = selectedSlot;
                sequence.lastPlacementTime = System.currentTimeMillis();
                sequence.attempts++;
                macroState = "Waiting for server placement";
                mc.player.swingHand(selected.hand());
                scheduleNextAction();
                return true;
            }
            if (!result.isAccepted()) {
                macroState = "Placement rejected";
                return true;
            }
            mc.player.swingHand(selected.hand());
            scheduleNextAction();
            if (sequence.stage == BoundStage.ANCHOR) {
                int preferred = selected.hand() == Hand.MAIN_HAND ? selectedSlot : sequence.originalSlot;
                anchorTasks.put(sequence.pos, new AnchorTask(sequence.expires, preferred, true));
                boundSequence = null;
            } else if (sequence.stage == BoundStage.OBSIDIAN) {
                sequence.stage = BoundStage.CRYSTAL;
            } else {
                crystalTasks.put(sequence.pos, new CrystalTask(sequence.expires, new HashSet<>()));
                boundSequence = null;
            }
            macroState = "Placed " + item.toString();
        } finally {
            finishSwitch(selected);
            ownAction = false;
        }
        return true;
    }

    private SwitchResult prepareItem(Item item) {
        int forced = forcedSlot(item);
        if (forced >= 0) {
            if (mc.player.getInventory().getStack(forced).isEmpty()) {
                macroState = "Configured item slot is empty";
                return new SwitchResult(null, -1);
            }
            if (!autoSwitch.isValue() && mc.player.getInventory().getSelectedSlot() != forced) {
                macroState = "Auto Switch disabled";
                return new SwitchResult(null, -1);
            }
            return selectMainSlot(forced);
        }
        if (isMacroItem(mc.player.getMainHandStack(), item)) {
            return new SwitchResult(Hand.MAIN_HAND, -1);
        }
        if (isMacroItem(mc.player.getOffHandStack(), item)) {
            return new SwitchResult(Hand.OFF_HAND, -1);
        }
        if (!autoSwitch.isValue()) {
            macroState = "Auto Switch disabled";
            return new SwitchResult(null, -1);
        }
        int slot = findSlot(item);
        if (slot < 0) {
            macroState = "Missing item in hotbar";
            return new SwitchResult(null, -1);
        }
        return selectMainSlot(slot);
    }

    private void queueConfirmation(BlockPos pos, BlockState state) {
        Object world = mc.world;
        int session = generation;
        BlockPos immutable = pos.toImmutable();
        mc.execute(() -> {
            if (!isState() || mc.world != world || activeWorld != world || generation != session) {
                return;
            }
            AnchorTask task = anchorTasks.get(immutable);
            if (task == null) {
                return;
            }
            if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
                macroState = task.activationPending ? "Anchor removed" : "Server rejected or removed the anchor";
                anchorTasks.remove(immutable);
                return;
            }
            task.confirmedCharges = state.get(RespawnAnchorBlock.CHARGES);
            if (task.waitingForCharge >= 0 && task.confirmedCharges >= task.waitingForCharge) {
                task.waitingForCharge = -1;
            }
        });
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != EventType.POST || !isState()) {
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }
        if (activeWorld != mc.world) {
            reset();
        }
        if (lastTick == mc.player.age) {
            return;
        }
        lastTick = mc.player.age;
        try {
            restoreLegitSlot(false);
            restoreActivationOffhand(false);
            long now = System.currentTimeMillis();
            crystalTasks.entrySet().removeIf(entry -> !crystals.isValue() || entry.getValue().expires <= now);
            anchorTasks.entrySet().removeIf(entry -> !anchors.isValue() || entry.getValue().expires <= now || !anchorDimension());
            if (mc.currentScreen != null || mc.player.isUsingItem() || !mc.player.isAlive() || mc.player.isSpectator()
                    || mc.player.age < nextActionTick) {
                return;
            }
            if (boundSequence != null && !mc.player.isSneaking() && processBoundSequence()) {
                return;
            }
            Entity aimed = entityUnderView();
            if (aimed instanceof EndCrystalEntity crystal && breakPlacedCrystal(crystal)) {
                return;
            }
            if (!mc.player.isSneaking()) {
                processAnchor();
            } else if (!anchorTasks.isEmpty() || boundSequence != null) {
                macroState = "Release sneak to interact with the anchor";
            }
        } finally {
            displayStatus();
        }
    }

    private boolean breakPlacedCrystal(EndCrystalEntity crystal) {
        Iterator<Map.Entry<BlockPos, CrystalTask>> iterator = crystalTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, CrystalTask> entry = iterator.next();
            if (!atBase(crystal, entry.getKey()) || entry.getValue().existingIds.contains(crystal.getId())) {
                continue;
            }
            if (!safeCrystal(crystal)) {
                return false;
            }
            ownAction = true;
            try {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                iterator.remove();
                scheduleNextAction();
                return true;
            } finally {
                ownAction = false;
            }
        }
        return false;
    }

    private void processAnchor() {
        BlockHitResult hit = blockUnderView(mc.player.getBlockInteractionRange());
        if (hit == null) {
            if (!anchorTasks.isEmpty()) {
                macroState = "Aim at your anchor";
            }
            return;
        }

        BlockPos pos = hit.getBlockPos();
        AnchorTask task = anchorTasks.get(pos);
        if (task == null) {
            if (!anchorTasks.isEmpty()) {
                macroState = "Aim at the anchor belonging to this macro";
            }
            return;
        }

        if (task.attempts >= 8) {
            macroState = "Anchor retry limit; check server restrictions";
            return;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (task.activationPending) {
                anchorTasks.remove(pos);
                activationOffhandRestoreTick = mc.player.age + 1;
                macroState = "Anchor exploded";
            } else {
                macroState = "Waiting for the server anchor block";
            }
            return;
        }

        int current = state.get(RespawnAnchorBlock.CHARGES);

        if (task.activationPending) {
            if (System.currentTimeMillis() - task.lastActivationTime < retryLifetime()) {
                macroState = "Activation sent; waiting for server";
                return;
            }
            task.activationPending = false;
            task.activationPrepared = false;
        }

        if (awaitConfirmation.isValue() && task.waitingForCharge < 0
                && (task.confirmedCharges < 0 || current != task.confirmedCharges)) {
            macroState = "Waiting for anchor confirmation";
            return;
        }

        if (task.waitingForCharge >= 0) {
            if (current >= task.waitingForCharge) {
                task.waitingForCharge = -1;
            } else if (System.currentTimeMillis() - task.lastChargeTime >= retryLifetime()) {
                task.waitingForCharge = -1;
            } else {
                macroState = "Waiting for charge";
                return;
            }
        }

        int wanted = task.oneCharge ? 1 : Math.max(1, Math.min(4, (int) charges.getValue()));
        if (current < wanted) {
            SwitchResult selected = prepareGlowstone();
            if (selected.hand() == null) {
                return;
            }

            boolean serverGlowstone = isServerItem(mc.player.getStackInHand(selected.hand()), Items.GLOWSTONE);
            ownAction = true;
            try {
                ActionResult result = mc.interactionManager.interactBlock(mc.player, selected.hand(), hit);
                if (!result.isAccepted() && !serverGlowstone) {
                    macroState = "Charge rejected";
                    return;
                }

                task.waitingForCharge = current + 1;
                task.lastChargeTime = System.currentTimeMillis();
                task.attempts++;
                mc.player.swingHand(selected.hand());
                macroState = "Charge sent";
                scheduleNextAction();
            } finally {
                finishSwitch(selected);
                ownAction = false;
            }
            return;
        }

        if (!safeAnchor(pos)) {
            return;
        }

        if (isMacroItem(mc.player.getOffHandStack(), Items.GLOWSTONE)) {
            if (!moveGlowstoneOutOfOffhand()) {
                return;
            }
            task.activationPrepared = false;
            nextActionTick = mc.player.age + 1;
            macroState = "Preparing offhand";
            return;
        }

        if (!task.activationPrepared) {
            SwitchResult selected = prepareActivation(current);
            if (selected.hand() == null) {
                return;
            }

            task.activationPrepared = true;
            task.activationPreparedTick = mc.player.age;
            task.activationOldSlot = selected.oldSlot();
            if (legitOldSlot >= 0) {
                legitRestoreTick = mc.player.age + 3;
            }
            nextActionTick = mc.player.age + 1;
            macroState = "Preparing anchor activation";
            return;
        }

        if (mc.player.age <= task.activationPreparedTick) {
            return;
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        if (isMacroItem(mainHand, Items.GLOWSTONE) || isMacroItem(mainHand, Items.RESPAWN_ANCHOR)
                || isMacroItem(offHand, Items.GLOWSTONE)) {
            task.activationPrepared = false;
            macroState = "Waiting for neutral hands";
            return;
        }

        ownAction = true;
        try {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            task.attempts++;
            task.lastActivationTime = System.currentTimeMillis();
            task.activationPending = true;
            macroState = "Activation sent";
            scheduleNextAction();

            int restore = task.activationOldSlot;
            if (restore >= 0 && restoreSlot.isValue()) {
                legitOldSlot = restore;
                legitSelectedSlot = mc.player.getInventory().getSelectedSlot();
                legitRestoreTick = mc.player.age + 1;
            } else if (legitOldSlot >= 0) {
                legitRestoreTick = mc.player.age + 1;
            }
            task.activationOldSlot = -1;
            if (activationOffhandSwapSlot >= 0) {
                activationOffhandRestoreTick = mc.player.age + 1;
            }
        } finally {
            ownAction = false;
        }
    }

    private SwitchResult prepareActivation(int currentCharges) {
        if (currentCharges <= 0) {
            macroState = "Anchor is not charged";
            return new SwitchResult(null, -1);
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!isMacroItem(mainHand, Items.GLOWSTONE) && !isMacroItem(mainHand, Items.RESPAWN_ANCHOR)) {
            return new SwitchResult(Hand.MAIN_HAND, -1);
        }

        if (!autoSwitch.isValue()) {
            macroState = "Need a neutral main-hand item";
            return new SwitchResult(null, -1);
        }

        int slot = -1;
        for (int index = 0; index < 9; index++) {
            ItemStack stack = mc.player.getInventory().getStack(index);
            if (!isMacroItem(stack, Items.GLOWSTONE) && !isMacroItem(stack, Items.RESPAWN_ANCHOR)) {
                slot = index;
                break;
            }
        }

        if (slot < 0) {
            macroState = "Need a neutral hotbar slot";
            return new SwitchResult(null, -1);
        }

        return selectMainSlot(slot);
    }

    private boolean moveGlowstoneOutOfOffhand() {
        if (!isMacroItem(mc.player.getOffHandStack(), Items.GLOWSTONE)) {
            return true;
        }
        if (!autoSwitch.isValue() || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            macroState = "Move glowstone out of offhand";
            return false;
        }
        if (activationOffhandSwapSlot >= 0) {
            return false;
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        int slot = -1;
        for (int index = 0; index < 9; index++) {
            if (index != selected && mc.player.getInventory().getStack(index).isEmpty()) {
                slot = index;
                break;
            }
        }
        if (slot < 0) {
            for (int index = 0; index < 9; index++) {
                ItemStack stack = mc.player.getInventory().getStack(index);
                if (index != selected && !isMacroItem(stack, Items.GLOWSTONE)
                        && !isMacroItem(stack, Items.RESPAWN_ANCHOR)) {
                    slot = index;
                    break;
                }
            }
        }
        if (slot < 0) {
            macroState = "Need a slot to clear offhand";
            return false;
        }

        activationOffhandSwapSlot = slot;
        activationOffhandRestoreTick = Integer.MAX_VALUE;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 36 + slot, 40, SlotActionType.SWAP, mc.player);
        return !isMacroItem(mc.player.getOffHandStack(), Items.GLOWSTONE);
    }

    private void restoreActivationOffhand(boolean force) {
        if (activationOffhandSwapSlot < 0) {
            return;
        }
        if (mc.player == null || mc.interactionManager == null
                || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            activationOffhandSwapSlot = -1;
            activationOffhandRestoreTick = -1;
            return;
        }
        if (!force && mc.player.age < activationOffhandRestoreTick) {
            return;
        }
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                36 + activationOffhandSwapSlot, 40, SlotActionType.SWAP, mc.player);
        activationOffhandSwapSlot = -1;
        activationOffhandRestoreTick = -1;
    }

    private SwitchResult prepareGlowstone() {
        SwitchResult result = prepareItem(Items.GLOWSTONE);
        if (result.hand() == null && findSlot(Items.GLOWSTONE) < 0) {
            macroState = "Need glowstone; set Glowstone Slot for a server item";
        }
        return result;
    }

    private SwitchResult selectMainSlot(int slot) {
        int old = mc.player.getInventory().getSelectedSlot();
        if (old == slot) {
            return new SwitchResult(Hand.MAIN_HAND, -1);
        }
        if (swapMode.isSelected("Legit")) {
            if (legitOldSlot < 0 || old != legitSelectedSlot) {
                legitOldSlot = old;
            }
            legitSelectedSlot = slot;
            legitRestoreTick = mc.player.age + 2;
            mc.player.getInventory().setSelectedSlot(slot);
            return new SwitchResult(null, -1);
        }
        mc.player.getInventory().setSelectedSlot(slot);
        return new SwitchResult(Hand.MAIN_HAND, old);
    }

    private void finishSwitch(SwitchResult selected) {
        if (selected.oldSlot() >= 0 && restoreSlot.isValue()) {
            mc.player.getInventory().setSelectedSlot(selected.oldSlot());
        }
        if (legitOldSlot >= 0 && mc.player.getInventory().getSelectedSlot() == legitSelectedSlot) {
            legitRestoreTick = mc.player.age + 1;
        }
    }

    private void restoreLegitSlot(boolean force) {
        if (legitOldSlot < 0) {
            return;
        }
        if (mc.player == null || mc.world != activeWorld
                || mc.player.getInventory().getSelectedSlot() != legitSelectedSlot) {
            clearLegitSwap();
            return;
        }
        if (!force && mc.player.age < legitRestoreTick) {
            return;
        }
        if (restoreSlot.isValue()) {
            mc.player.getInventory().setSelectedSlot(legitOldSlot);
        }
        clearLegitSwap();
    }

    private void clearLegitSwap() {
        legitOldSlot = -1;
        legitSelectedSlot = -1;
        legitRestoreTick = -1;
    }

    private Vec3d predictedSelfPosition() {
        Vec3d delta = mc.player.getVelocity();
        Vec3d pos = mc.player.getEntityPos();
        if (delta.lengthSquared() > 2.25 || !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(delta))) {
            return pos;
        }
        return pos.add(delta);
    }

    private boolean safeCrystal(EndCrystalEntity crystal) {
        float current = MacroDamage.calculateCrystal(crystal.getEntityPos(), mc.player);
        float predicted = MacroDamage.calculateCrystal(crystal.getEntityPos(), mc.player, predictedSelfPosition());
        return safeDamage(Math.max(current, predicted));
    }

    private boolean safeAnchor(BlockPos pos) {
        float current = MacroDamage.calculateAnchor(pos, mc.player);
        float predicted = MacroDamage.calculateAnchor(pos, mc.player, predictedSelfPosition());
        return safeDamage(Math.max(current, predicted));
    }

    private boolean safeDamage(float damage) {
        lastSelfDamage = damage;
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (!Float.isFinite(damage) || damage < 0.0F) {
            macroState = "Damage unknown";
            return false;
        }
        if (!overrides.isSelected("Self Damage") && damage > maxSelfDamage.getValue()) {
            macroState = "Self damage limit";
            return false;
        }
        if (!overrides.isSelected("Health Reserve") && damage + healthReserve.getValue() >= health) {
            macroState = "Health reserve";
            return false;
        }
        return true;
    }

    public String getMacroState() {
        return macroState;
    }

    public float getLastSelfDamage() {
        return lastSelfDamage;
    }

    private int forcedSlot(Item item) {
        float configured = item == Items.RESPAWN_ANCHOR ? anchorSlot.getValue()
                : item == Items.GLOWSTONE ? glowstoneSlot.getValue() : 0.0F;
        return configured >= 1.0F && configured <= 9.0F ? (int) configured - 1 : -1;
    }

    private boolean isMacroItem(ItemStack stack, Item item) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.isOf(item)) {
            return true;
        }
        int forced = forcedSlot(item);
        if (forced >= 0 && stack == mc.player.getInventory().getStack(forced)) {
            return true;
        }
        if (item != Items.RESPAWN_ANCHOR && item != Items.GLOWSTONE) {
            return false;
        }
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return Identifier.ofVanilla(item == Items.RESPAWN_ANCHOR ? "respawn_anchor" : "glowstone").equals(model);
    }

    private boolean isServerItem(ItemStack stack, Item expected) {
        return !stack.isOf(expected) && isMacroItem(stack, expected);
    }

    private void displayStatus() {
        long now = System.currentTimeMillis();
        if (boundSequence != null || !anchorTasks.isEmpty() || !crystalTasks.isEmpty()) {
            statusUntil = now + 2500L;
        }
        if (!showStatus.isValue() || mc.player == null || now > statusUntil || "Idle".equals(macroState)) {
            return;
        }
        if (!macroState.equals(shownStatus) || now - lastStatusTime >= 750L) {
            String message = "Macros: " + macroState;
            if ("Self damage limit".equals(macroState) || "Health reserve".equals(macroState)) {
                message += " (" + Math.round(lastSelfDamage * 10.0F) / 10.0F + " HP)";
            }
            mc.player.sendMessage(Text.literal(message), true);
            shownStatus = macroState;
            lastStatusTime = now;
        }
    }

    private int findSlot(Item item) {
        int forced = forcedSlot(item);
        if (forced >= 0) {
            return mc.player.getInventory().getStack(forced).isEmpty() ? -1 : forced;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isMacroItem(mc.player.getInventory().getStack(slot), item)) {
                return slot;
            }
        }
        return -1;
    }

    private Entity entityUnderView() {
        Vec3d eyes = mc.player.getEyePos();
        double range = mc.player.getEntityInteractionRange();
        Vec3d end = eyes.add(mc.player.getRotationVec(1.0F).multiply(range));
        BlockHitResult block = mc.world.raycast(new RaycastContext(eyes, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player));
        double nearest = block.getType() == HitResult.Type.BLOCK ? eyes.squaredDistanceTo(block.getPos()) : range * range;
        Entity selected = null;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive() || entity.isRemoved() || !entity.canHit()) {
                continue;
            }
            var box = entity.getBoundingBox();
            var intersection = box.raycast(eyes, end);
            if (!box.contains(eyes) && intersection.isEmpty()) {
                continue;
            }
            double distance = box.contains(eyes) ? 0.0 : eyes.squaredDistanceTo(intersection.orElseThrow());
            if (distance <= nearest) {
                nearest = distance;
                selected = entity;
            }
        }
        return selected;
    }

    private BlockHitResult blockUnderView(double range) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d end = eyes.add(mc.player.getRotationVec(1.0F).multiply(range));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eyes, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        double distance = eyes.squaredDistanceTo(hit.getPos());
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive() || !entity.canHit()) {
                continue;
            }
            var box = entity.getBoundingBox();
            var intersection = box.raycast(eyes, hit.getPos());
            if (box.contains(eyes) || intersection.isPresent() && eyes.squaredDistanceTo(intersection.get()) < distance) {
                return null;
            }
        }
        return hit;
    }

    private boolean atBase(EndCrystalEntity crystal, BlockPos base) {
        Vec3d expected = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        return crystal.getEntityPos().squaredDistanceTo(expected) < 0.04;
    }

    private boolean anchorDimension() {
        return mc.world != null && !mc.world.getRegistryKey().equals(World.NETHER);
    }

    private void scheduleNextAction() {
        nextActionTick = mc.player.age + Math.max(1, (int) delay.getValue());
    }

    private long retryLifetime() {
        return Math.max(350L, taskLifetime() / 4L);
    }

    private long taskLifetime() {
        long ping = 0L;
        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                ping = Math.max(0, entry.getLatency());
            }
        }
        return Math.min(10_000L, Math.max(2500L, ping * 8L + 500L));
    }

    private <T> void trim(Map<BlockPos, T> tasks) {
        while (tasks.size() > 16) {
            Iterator<BlockPos> iterator = tasks.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private record CrystalTask(long expires, Set<Integer> existingIds) {
    }

    private static final class AnchorTask {
        private final long expires;
        private final int originalSlot;
        private final boolean oneCharge;
        private long lastChargeTime;
        private long lastActivationTime;
        private boolean activationPending;
        private boolean activationPrepared;
        private int activationPreparedTick = -1;
        private int activationOldSlot = -1;
        private int confirmedCharges = -1;
        private int waitingForCharge = -1;
        private int attempts;

        private AnchorTask(long expires, int originalSlot, boolean oneCharge) {
            this.expires = expires;
            this.originalSlot = originalSlot;
            this.oneCharge = oneCharge;
        }
    }

    private enum BoundStage {
        ANCHOR, OBSIDIAN, CRYSTAL
    }

    private static final class BoundSequence {
        private BoundStage stage;
        private boolean placementSent;
        private int selectedSlot;
        private int attempts;
        private long lastPlacementTime;
        private final BlockPos pos;
        private final int originalSlot;
        private final long expires;

        private BoundSequence(BoundStage stage, BlockPos pos, int originalSlot, long expires) {
            this.stage = stage;
            this.pos = pos;
            this.originalSlot = originalSlot;
            this.expires = expires;
        }
    }

    private record SwitchResult(Hand hand, int oldSlot) {
    }
}
