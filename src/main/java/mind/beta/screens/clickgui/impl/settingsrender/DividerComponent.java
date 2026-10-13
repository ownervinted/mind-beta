package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.modules.module.setting.implement.DividerSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;

public final class DividerComponent extends AbstractSettingComponent {
    public DividerComponent(DividerSetting setting) { super(setting); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Render2D.rect(x + 1f, y + height * .5f, width - 2f, .55f,
                applyAlpha(ThemeColors.color(165, 180, 205, 45)).getRGB(), 0f);
    }
}
