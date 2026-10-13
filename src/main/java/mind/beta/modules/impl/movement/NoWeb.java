/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.util.Instance;
import mind.beta.util.move.MoveUtil;
import mind.beta.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoWeb extends ModuleStructure {
    public static NoWeb getInstance() {
        return Instance.get(NoWeb.class);
    }

    public final SelectSetting webMode = new SelectSetting("Mode").value("Grim");

    public NoWeb() {
        super("NoWeb", "No Web", ModuleCategory.MOVEMENT);
        settings(webMode);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (PlayerInteractionHelper.isPlayerInBlock(Blocks.COBWEB)) {
            double[] speed = MoveUtil.calculateDirection(0.35);
            mc.player.addVelocity(speed[0], 0, speed[1]);
            mc.player.setVelocity(speed[0], mc.options.jumpKey.isPressed() ? 0.65f : mc.options.sneakKey.isPressed() ? -0.65f : 0, speed[1]);
        }
    }
}