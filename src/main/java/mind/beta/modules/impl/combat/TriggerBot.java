/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.PacketEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.impl.combat.aura.impl.LinearRotation;
import mind.beta.modules.impl.combat.aura.impl.RotationMode;
import mind.beta.modules.impl.combat.aura.target.MultiPoint;
import mind.beta.modules.impl.combat.aura.target.TargetSelector;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.MultiSelectSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.string.PlayerInteractionHelper;

import java.util.Objects;

public class TriggerBot extends ModuleStructure {
    private static final float RANGE_MARGIN = 0.253F;
    private final TargetSelector targetSelector = new TargetSelector();
    private final MultiPoint pointFinder = new MultiPoint();
    public LivingEntity target;

    public SliderSettings attackRange = new SliderSettings("Attack Range", "Attack range to the target")
            .setValue(3).range(1F, 6F);

    MultiSelectSetting targetType = new MultiSelectSetting("Target Type", "Filter the target list by type")
            .value("Players", "Mobs", "Animals", "Armor Stands")
            .selected("Players", "Mobs", "Animals");

    public MultiSelectSetting attackSetting = new MultiSelectSetting("Settings", "Attack settings")
            .value("Critical Hits Only", "Randomize Critical Hits", "Attack Through Walls")
            .selected("Critical Hits Only");

    @Getter
    public SelectSetting sprintReset = new SelectSetting("Sprint Reset")
            .value("Legit", "Intensive")
            .selected("Legit");

    @Getter
    public BooleanSetting smartCrits = new BooleanSetting("Smart Critical Hits", "Attack on the ground when the cooldown is ready")
            .setValue(true)
            .visible(() -> attackSetting.isSelected("Critical Hits Only"));

    public TriggerBot() {
        super("TriggerBot", "Trigger Bot", ModuleCategory.COMBAT);
        settings(attackRange, targetType, attackSetting, sprintReset, smartCrits);
    }

    public static TriggerBot getInstance() {
        return Instance.get(TriggerBot.class);
    }

    @Override
    public void deactivate() {
        target = null;
    }

    private LivingEntity updateTarget() {
        TargetSelector.EntityFilter filter = new TargetSelector.EntityFilter(targetType.getSelected());
        float range = attackRange.getValue() + RANGE_MARGIN;
        targetSelector.searchTargets(mc.world.getEntities(), range, 360, attackSetting.isSelected("Attack Through Walls"));
        targetSelector.validateTarget(filter::isValid);
        return targetSelector.getCurrentTarget();
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        if (PlayerInteractionHelper.nullCheck()) return;

        switch (e.getType()) {
            case EventType.PRE -> target = updateTarget();
            case EventType.POST -> {
                if (target != null) {
                    Initialization.getInstance().getManager().getAttackPerpetrator().performTriggerAttack(getConfig(), this);
                }
            }
        }
    }

    public AttackExecutor.AttackPerpetratorConfigurable getConfig() {
        float baseRange = attackRange.getValue() + RANGE_MARGIN;

        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target,
                baseRange,
                RotationManager.INSTANCE.getRotation(),
                getSmoothMode().randomValue(),
                attackSetting.isSelected("Attack Through Walls")
        );

        Vec3d computedPoint = pointData.getLeft();
        Box hitbox = pointData.getRight();

        Rotation rotation = RotationUtils.fromVec3d(computedPoint.subtract(Objects.requireNonNull(mc.player).getEyePos()));

        return new AttackExecutor.AttackPerpetratorConfigurable(
                target,
                rotation,
                baseRange,
                attackSetting.getSelected(),
                null,
                hitbox
        );
    }

    public RotationMode getSmoothMode() {
        return new LinearRotation();
    }

    public boolean isResetSprintLegit() {
        return sprintReset.isSelected("Legit");
    }

    public boolean isResetSprintPacket() {
        return sprintReset.isSelected("Intensive");
    }

    public boolean isOnlyCrits() {
        return attackSetting.isSelected("Critical Hits Only");
    }

    public boolean isRandomizeCrit() {
        return attackSetting.isSelected("Randomize Critical Hits");
    }

    @EventHandler
    public void tick(TickEvent e) {}

    @EventHandler
    public void onPacket(PacketEvent e) {}
}