/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.WorldRenderEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.Instance;
import mind.beta.util.render.Render3D;

import java.awt.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockOverlay extends ModuleStructure {
    public static BlockOverlay getInstance() {
        return Instance.get(BlockOverlay.class);
    }

    public BlockOverlay() {
        super("BlockOverlay", "Block Overlay", ModuleCategory.RENDER);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.crosshairTarget instanceof BlockHitResult result && result.getType().equals(HitResult.Type.BLOCK)) {
            BlockPos pos = result.getBlockPos();
            Render3D.drawShapeAlternative(pos, mc.world.getBlockState(pos).getOutlineShape(mc.world, pos), new Color(109, 252, 255,230).getRGB(), 1.5f, true, true);
        }
    }
}
