/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.events.impl.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.*;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.ColorUtil;
import mind.beta.util.Instance;
import mind.beta.util.math.MathUtils;
import mind.beta.util.move.MoveUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FreeCam extends ModuleStructure {
    public static FreeCam getInstance() {
        return Instance.get(FreeCam.class);
    }

    private final SliderSettings speedSetting = new SliderSettings("Speed", "Select the debug camera speed").setValue(2.0F).range(0.5F, 5.0F);
    private final BooleanSetting freezeSetting = new BooleanSetting("Freeze", "Freeze the player in place").setValue(false);
    private final BooleanSetting reloadChunksSetting = new BooleanSetting("Reload Chunks", "Disable cave culling").setValue(true);
    private final BooleanSetting toggleOnLogSetting = new BooleanSetting("Toggle On Log", "Disable on disconnect").setValue(true);

    public final ColorSetting fakeplayer = new ColorSetting("Color 1", "First gradient color")
            .value(ColorUtil.getColor(255, 50, 100, 255));

    public Vec3d pos, prevPos;

    public FreeCam() {
        super("FreeCam", "Free Cam", ModuleCategory.PLAYER);
        settings(speedSetting, freezeSetting, reloadChunksSetting, toggleOnLogSetting);
    }

    @Override
    public void activate() {
        prevPos = pos = mc.getEntityRenderDispatcher().camera.getCameraPos();

        if (reloadChunksSetting.isValue()) {
            mc.worldRenderer.reload();
        }

        super.activate();
    }

    @Override
    public void deactivate() {
        if (reloadChunksSetting.isValue()) {
            mc.execute(mc.worldRenderer::reload);
        }

        super.deactivate();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        switch (e.getPacket()) {
            case PlayerMoveC2SPacket move when freezeSetting.isValue() -> e.cancel();
            case PlayerRespawnS2CPacket respawn -> setState(false);
            case GameJoinS2CPacket join -> setState(false);
            default -> {}
        }
    }

    @EventHandler
    public void onMove(MoveEvent e) {
        if (freezeSetting.isValue()) {
            e.setMovement(Vec3d.ZERO);
        }
    }

    @EventHandler
    public void onInput(InputEvent e) {
        float speed = speedSetting.getValue();
        double[] motion = MoveUtil.calculateDirection(e.forward(), e.sideways(), speed);

        prevPos = pos;
        pos = pos.add(motion[0], e.getInput().jump() ? speed : e.getInput().sneak() ? -speed : 0, motion[1]);

        e.inputNone();
    }

    @EventHandler
    public void onCameraPosition(CameraPositionEvent e) {
        e.setPos(MathUtils.interpolate(prevPos, pos));
        mc.options.setPerspective(Perspective.FIRST_PERSON);
    }

    @EventHandler
    public void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        if (toggleOnLogSetting.isValue()) {
            setState(false);
        }
    }
}