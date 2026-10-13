/* Copyright Mind */
package mind.beta.modules.impl.combat.aura.custom;

import mind.beta.modules.module.setting.implement.BezierSetting;
import mind.beta.modules.module.setting.implement.ButtonSetting;
import mind.beta.modules.module.setting.implement.DividerSetting;
import mind.beta.modules.module.setting.implement.HitboxPointSetting;
import mind.beta.modules.module.setting.implement.MultiPointSetting;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.modules.module.setting.implement.TextSetting;

import java.util.List;
import java.util.function.Supplier;

public final class RotationBuilderSettings {
    private final RotationBuilder builder = RotationBuilder.INSTANCE;
    private final Supplier<Boolean> custom;

    public final DividerSetting topDivider = new DividerSetting();
    public final DividerSetting editorDivider = new DividerSetting();
    public final DividerSetting bottomDivider = new DividerSetting();

    public final SelectSetting section = new SelectSetting("Custom Editor")
            .value("Aim Motion", "Aim Behavior", "Limits", "Bezier", "Multipoints")
            .selected("Aim Motion");

    public final SelectSetting aim = new SelectSetting("Aim Behavior")
            .value("Sticky", "Out of hitbox", "Snap")
            .selected("Sticky");

    public final SliderSettings outside = slider("Outside Offset", .5f, 18f);
    public final SliderSettings leadTicks = slider("Aim Lead (Ticks)", 1f, 5f);
    public final SliderSettings jitter = slider("Jitter", 0f, 4f);
    public final SliderSettings acquire = slider("Acquire Speed", 1f, 100f);
    public final SliderSettings track = slider("Track Speed", 1f, 100f);
    public final SliderSettings returning = slider("Return Speed", 1f, 100f);
    public final SliderSettings yaw = slider("Yaw Limit", 1f, 180f);
    public final SliderSettings pitch = slider("Pitch Limit", 1f, 90f);
    public final SliderSettings acceleration = slider("Acceleration", .01f, 1f);
    public final SliderSettings deceleration = slider("Deceleration", .01f, 1f);
    public final SliderSettings smoothing = slider("Smoothing", .05f, 1f);

    public final SliderSettings stickyX = slider("Sticky X", .05f, .95f);
    public final SliderSettings stickyY = slider("Sticky Y", .05f, .95f);
    public final HitboxPointSetting stickyPoint = new HitboxPointSetting("Sticky Point", stickyX, stickyY);

    public final SliderSettings pointSwitchTicks = slider("Point Switch Ticks", 1f, 40f);
    public final MultiPointSetting multiPoints = new MultiPointSetting("Multipoints");

    public final SliderSettings snapOutsideSpeed = slider("Snap Outside Speed", 1f, 180f);
    public final SliderSettings snapHoldTicks = slider("Snap Hold", 0f, 5f);
    public final SliderSettings snapReturnSpeed = slider("Snap Return Speed", 1f, 180f);
    public final SliderSettings snapTriggerTicks = slider("Snap Trigger", 0f, 3f);

    public final SliderSettings x1 = slider("Bezier Control X1", 0f, 1f);
    public final SliderSettings y1 = slider("Bezier Control Y1", 0f, 1f);
    public final SliderSettings x2 = slider("Bezier Control X2", 0f, 1f);
    public final SliderSettings y2 = slider("Bezier Control Y2", 0f, 1f);
    public final BezierSetting curve = new BezierSetting("Bezier Curve", x1, y1, x2, y2);

    public final TextSetting configName = new TextSetting("Config Name", "Name for a new rotation preset")
            .setText("")
            .setMax(48);
    public final SelectSetting configList = new SelectSetting("Current Config");
    public final ButtonSetting saveConfig = button("Create Config", "Save", () -> {
        sync();
        String saved = builder.savePreset(configName.getText());
        if (!saved.isEmpty()) refreshConfigs(saved);
    });
    public final ButtonSetting loadConfig = button("Load Config", "Load", () -> {
        if (builder.loadPreset(configList.getSelected())) applyProfile();
    });
    public final ButtonSetting updateConfig = button("Update Config", "Update", () -> {
        sync();
        String saved = builder.updatePreset(configList.getSelected());
        if (!saved.isEmpty()) refreshConfigs(saved);
    });
    public final ButtonSetting deleteConfig = button("Delete Config", "Delete", () -> {
        if (builder.deletePreset(configList.getSelected())) refreshConfigs(null);
    });

    private String snapshot;

    public RotationBuilderSettings(Supplier<Boolean> custom) {
        this.custom = custom;

        topDivider.visible(custom);
        editorDivider.visible(custom);
        bottomDivider.visible(custom);
        section.visible(custom);

        aim.visible(show("Aim Behavior"));
        outside.visible(() -> custom.get() && showSection("Aim Behavior") && !aim.isSelected("Sticky"));
        leadTicks.visible(() -> custom.get() && showSection("Aim Behavior") && aim.isSelected("Out of hitbox"));
        jitter.visible(() -> custom.get() && showSection("Aim Behavior") && aim.isSelected("Out of hitbox"));

        acquire.visible(show("Aim Motion"));
        track.visible(show("Aim Motion"));
        returning.visible(show("Aim Motion"));
        acceleration.visible(show("Aim Motion"));
        deceleration.visible(show("Aim Motion"));
        smoothing.visible(show("Aim Motion"));
        snapOutsideSpeed.visible(() -> custom.get() && showSection("Aim Motion") && aim.isSelected("Snap"));
        snapHoldTicks.visible(() -> custom.get() && showSection("Aim Motion") && aim.isSelected("Snap"));
        snapReturnSpeed.visible(() -> custom.get() && showSection("Aim Motion") && aim.isSelected("Snap"));
        snapTriggerTicks.visible(() -> custom.get() && showSection("Aim Motion") && aim.isSelected("Snap"));

        yaw.visible(show("Limits"));
        pitch.visible(show("Limits"));

        curve.visible(show("Bezier"));
        stickyPoint.visible(() -> custom.get() && showSection("Aim Behavior") && aim.isSelected("Sticky"));

        multiPoints.visible(() -> custom.get() && showSection("Multipoints") && !aim.isSelected("Sticky"));
        pointSwitchTicks.visible(() -> custom.get() && showSection("Multipoints") && !aim.isSelected("Sticky"));

        configList.visible(show("Configs"));
        configName.visible(show("Configs"));
        saveConfig.visible(show("Configs"));
        loadConfig.visible(show("Configs"));
        updateConfig.visible(show("Configs"));
        deleteConfig.visible(show("Configs"));

        refreshSectionOptions();
        applyProfile();
        refreshConfigs(builder.savedConfig);
    }

    private Supplier<Boolean> show(String name) {
        return () -> custom.get() && showSection(name);
    }

    private boolean showSection(String name) {
        return section.isSelected(name);
    }

    private void refreshSectionOptions() {
        if (aim.isSelected("Sticky")) {
            section.setValues(List.of("Aim Behavior"), "Aim Behavior");
        } else if (aim.isSelected("Snap")) {
            section.setValues(List.of("Aim Motion", "Aim Behavior", "Limits", "Multipoints"), section.getSelected());
        } else {
            section.setValues(List.of("Aim Motion", "Aim Behavior", "Limits", "Bezier", "Multipoints"), section.getSelected());
        }
    }

    private static SliderSettings slider(String name, float min, float max) {
        return new SliderSettings(name, "").range(min, max);
    }

    private static ButtonSetting button(String name, String label, Runnable action) {
        return new ButtonSetting(name, "").setButtonName(label).setRunnable(action);
    }

    private void refreshConfigs(String selected) {
        configList.setValues(builder.configs(), selected == null ? "" : selected);
    }

    public void applyProfile() {
        RotationBuilder.Profile profile = builder.active();
        aim.selected(profile.aim);
        outside.setValue(profile.outside);
        leadTicks.setValue(profile.leadTicks);
        jitter.setValue(profile.jitter);
        acquire.setValue(profile.acquire);
        track.setValue(profile.track);
        returning.setValue(profile.returning);
        yaw.setValue(profile.yaw);
        pitch.setValue(profile.pitch);
        acceleration.setValue(profile.acceleration);
        deceleration.setValue(profile.deceleration);
        smoothing.setValue(profile.smoothing);
        stickyX.setValue(profile.stickyX);
        stickyY.setValue(profile.stickyY);
        pointSwitchTicks.setValue(profile.pointSwitchTicks);
        snapOutsideSpeed.setValue(profile.snapOutsideSpeed);
        snapHoldTicks.setValue(profile.snapHoldTicks);
        snapReturnSpeed.setValue(profile.snapReturnSpeed);
        snapTriggerTicks.setValue(profile.snapTriggerTicks);
        x1.setValue(profile.x1);
        y1.setValue(profile.y1);
        x2.setValue(profile.x2);
        y2.setValue(profile.y2);
        refreshSectionOptions();
        snapshot = fingerprint();
    }

    private String fingerprint() {
        return String.join("|",
                aim.getSelected(),
                Float.toString(outside.getValue()),
                Float.toString(leadTicks.getValue()),
                Float.toString(jitter.getValue()),
                Float.toString(acquire.getValue()),
                Float.toString(track.getValue()),
                Float.toString(returning.getValue()),
                Float.toString(yaw.getValue()),
                Float.toString(pitch.getValue()),
                Float.toString(acceleration.getValue()),
                Float.toString(deceleration.getValue()),
                Float.toString(smoothing.getValue()),
                Float.toString(stickyX.getValue()),
                Float.toString(stickyY.getValue()),
                Float.toString(pointSwitchTicks.getValue()),
                Float.toString(snapOutsideSpeed.getValue()),
                Float.toString(snapHoldTicks.getValue()),
                Float.toString(snapReturnSpeed.getValue()),
                Float.toString(snapTriggerTicks.getValue()),
                Float.toString(x1.getValue()),
                Float.toString(y1.getValue()),
                Float.toString(x2.getValue()),
                Float.toString(y2.getValue())
        );
    }

    public void sync() {
        refreshSectionOptions();
        String current = fingerprint();
        if (current.equals(snapshot)) return;

        RotationBuilder.Profile profile = builder.active();
        profile.aim = aim.getSelected();
        profile.outside = outside.getValue();
        profile.leadTicks = Math.max(1, Math.min(5, Math.round(leadTicks.getValue())));
        profile.jitter = jitter.getValue();
        profile.acquire = acquire.getValue();
        profile.track = track.getValue();
        profile.returning = returning.getValue();
        profile.yaw = yaw.getValue();
        profile.pitch = pitch.getValue();
        profile.acceleration = acceleration.getValue();
        profile.deceleration = deceleration.getValue();
        profile.smoothing = smoothing.getValue();
        profile.stickyX = stickyX.getValue();
        profile.stickyY = stickyY.getValue();
        profile.pointSwitchTicks = pointSwitchTicks.getValue();
        profile.snapOutsideSpeed = snapOutsideSpeed.getValue();
        profile.snapHoldTicks = snapHoldTicks.getValue();
        profile.snapReturnSpeed = snapReturnSpeed.getValue();
        profile.snapTriggerTicks = snapTriggerTicks.getValue();
        profile.x1 = x1.getValue();
        profile.y1 = y1.getValue();
        profile.x2 = x2.getValue();
        profile.y2 = y2.getValue();
        builder.save();
        snapshot = current;
    }
}
