/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.GlassHandsRenderEvent;
import mind.beta.events.impl.WorldChangeEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.ColorSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.util.render.shader.GlassHandsRenderer;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlassHands extends ModuleStructure {

    private static GlassHands instance;

    SliderSettings blurRadius = new SliderSettings("Blur Strength", "Glass blur strength")
            .setValue(2.5f).range(1.0f, 5.0f);

    SliderSettings blurIterations = new SliderSettings("Quality", "Number of blur iterations")
            .setValue(3).range(1, 5);

    SliderSettings saturation = new SliderSettings("Saturation", "Color saturation")
            .setValue(0).range(0.0f, 2.0f);

    BooleanSetting enableTint = new BooleanSetting("Tint", "Enable a color tint for the glass")
            .setValue(false);

    SliderSettings tintIntensity = new SliderSettings("Tint Strength", "Tint intensity")
            .setValue(0.2f).range(0.0f, 0.5f)
            .visible(enableTint::isValue);

    ColorSetting tintColor = new ColorSetting("Tint Color", "Glass tint color")
            .value(0xFF00FFFF)
            .visible(enableTint::isValue);

    BooleanSetting enableEdgeGlow = new BooleanSetting("Edge Glow", "Glow around the glass edges")
            .setValue(true);

    SliderSettings edgeGlowIntensity = new SliderSettings("Glow Strength", "Edge glow intensity")
            .setValue(0.2f).range(0.0f, 1.0f)
            .visible(enableEdgeGlow::isValue);

    public GlassHands() {
        super("GlassHands", "Give hands and items a glass appearance", ModuleCategory.RENDER);
        settings(blurRadius, blurIterations, saturation, enableTint, tintIntensity, tintColor, enableEdgeGlow, edgeGlowIntensity);
        instance = this;
    }

    public static GlassHands getInstance() {
        return instance;
    }

    @Override
    public void activate() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
    }

    @Override
    public void deactivate() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.setEnabled(false);
        }
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (!isState()) return;

        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
    }

    @EventHandler
    public void onGlassHandsRender(GlassHandsRenderEvent event) {
        if (!isState()) return;

        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer == null) return;

        updateRendererSettings();

        if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
        } else if (event.getPhase() == GlassHandsRenderEvent.Phase.POST) {
            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    private void updateRendererSettings() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer == null) return;

        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getInt());
        renderer.setSaturation(saturation.getValue());
        renderer.setReflect(true);

        if (enableTint.isValue()) {
            renderer.setTintColor(tintColor.getColor());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }

        if (enableEdgeGlow.isValue()) {
            renderer.setEdgeGlowIntensity(edgeGlowIntensity.getValue());
        } else {
            renderer.setEdgeGlowIntensity(0.0f);
        }
    }
}