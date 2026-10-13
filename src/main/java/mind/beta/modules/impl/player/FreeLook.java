/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.CameraEvent;
import mind.beta.events.impl.FovEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.MouseRotationEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FreeLook extends ModuleStructure {

    Perspective perspective;
    Rotation rotation;
    public static BindSetting freeLookSetting = new BindSetting("Free Look", "Key for free look");

    public FreeLook() {
        super("FreeLook", "Free Look", ModuleCategory.RENDER);
        settings(freeLookSetting);
        rotation = null;
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(freeLookSetting.getKey())) {
            perspective = mc.options.getPerspective();
            if (rotation == null) {
                rotation = RotationUtils.cameraAngle();
            }
        }
    }

    @EventHandler
    public void onFov(FovEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting)) {
            handleFreeLookActivation();
        } else if (perspective != null) {
            handleFreeLookDeactivation();
        }
    }

    private void handleFreeLookActivation() {
        if (mc.options.getPerspective().isFirstPerson()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        if (rotation == null) {
            rotation = RotationUtils.cameraAngle();
        }
    }

    private void handleFreeLookDeactivation() {
        mc.options.setPerspective(perspective);
        perspective = null;
        rotation = null;
    }

    @EventHandler
    public void onMouseRotation(MouseRotationEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting)) {
            if (rotation == null) {
                rotation = RotationUtils.cameraAngle();
            }
            rotation.setYaw(rotation.getYaw() + e.getCursorDeltaX() * 0.15F);
            rotation.setPitch(MathHelper.clamp(rotation.getPitch() + e.getCursorDeltaY() * 0.15F, -90F, 90F));
            e.cancel();
        } else {
            rotation = null;
        }
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting) && rotation != null) {
            e.setRotation(rotation);
            e.cancel();
        }
    }
}