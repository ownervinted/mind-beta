/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.CameraEvent;
import mind.beta.events.impl.FovEvent;
import mind.beta.events.impl.HotBarScrollEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.modules.impl.combat.aura.RotationUtils;
import mind.beta.modules.impl.player.FreeLook;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.Instance;
import mind.beta.util.math.MathUtils;
import mind.beta.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CameraSettings extends ModuleStructure {

    float fov = 110;
    float smoothFov = 30;
    float lastChangedFov = 30;

    BooleanSetting clipSetting = new BooleanSetting(
            "Camera Clipping",
            "Allow the camera to pass through blocks"
    ).setValue(true);

    BooleanSetting transparentBlocksSetting = new BooleanSetting(
            "Transparent Blocks",
            "Fade blocks between the player and third person camera"
    ).setValue(true)
            .visible(() -> clipSetting.isValue());

    SliderSettings blockAlphaSetting = new SliderSettings(
            "Block Alpha",
            "Opacity of blocks obstructing the third person camera"
    ).setValue(0.24F)
            .range(0.05F, 0.70F)
            .visible(() -> clipSetting.isValue() && transparentBlocksSetting.isValue());

    SliderSettings fadeRadiusSetting = new SliderSettings(
            "Fade Radius",
            "Radius of the camera obstruction fade"
    ).setValue(0.38F)
            .range(0.10F, 0.85F)
            .visible(() -> clipSetting.isValue() && transparentBlocksSetting.isValue());

    SliderSettings distanceSetting = new SliderSettings(
            "Camera Distance",
            "Camera distance"
    ).setValue(3.0F)
            .range(2.0F, 5.0F);

    BindSetting zoomSetting = new BindSetting(
            "Zoom",
            "Key to increase the camera distance"
    );

    public CameraSettings() {
        super("CameraSettings", "Camera Settings", ModuleCategory.RENDER);
        settings(
                clipSetting,
                transparentBlocksSetting,
                blockAlphaSetting,
                fadeRadiusSetting,
                distanceSetting,
                zoomSetting
        );
    }

    public static CameraSettings getInstance() {
        return Instance.get(CameraSettings.class);
    }

    public static boolean shouldUseTransparentBlocks() {
        CameraSettings module = getInstance();
        MinecraftClient client = MinecraftClient.getInstance();

        return module != null
                && module.isState()
                && module.clipSetting.isValue()
                && module.transparentBlocksSetting.isValue()
                && client.player != null
                && client.world != null
                && !client.options.getPerspective().isFirstPerson();
    }

    public static float getTransparentBlockAlpha() {
        CameraSettings module = getInstance();
        return module == null ? 0.24F : module.blockAlphaSetting.getValue();
    }

    public static float getTransparentBlockRadius() {
        CameraSettings module = getInstance();
        return module == null ? 0.38F : module.fadeRadiusSetting.getValue();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(zoomSetting.getKey())) {
            fov = Math.min(lastChangedFov, mc.options.getFov().getValue() - 20);
        }

        if (e.isKeyReleased(zoomSetting.getKey(), true)) {
            lastChangedFov = fov;
            fov = mc.options.getFov().getValue();
        }
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (PlayerInteractionHelper.isKey(zoomSetting)) {
            fov = (int) MathHelper.clamp(
                    fov - e.getVertical() * 10,
                    10,
                    mc.options.getFov().getValue()
            );
            e.cancel();
        }
    }

    @EventHandler
    public void onFov(FovEvent e) {
        e.setFov(
                (int) MathHelper.clamp(
                        (smoothFov = MathUtils.interpolateSmooth(1.6, smoothFov, fov)) + 1,
                        10,
                        mc.options.getFov().getValue()
                )
        );
        e.cancel();
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        e.setCameraClip(clipSetting.isValue());
        e.setDistance(distanceSetting.getValue());

        FreeLook freeLook = Instance.get(FreeLook.class);

        if (!freeLook.isState()
                || !PlayerInteractionHelper.isKey(freeLook.freeLookSetting)) {
            e.setRotation(RotationUtils.cameraAngle());
        }

        e.cancel();
    }
}
