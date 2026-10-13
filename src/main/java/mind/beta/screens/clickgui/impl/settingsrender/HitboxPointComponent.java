package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.modules.module.setting.implement.HitboxPointSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class HitboxPointComponent extends AbstractSettingComponent {
    private final HitboxPointSetting point;
    private boolean dragging;

    public HitboxPointComponent(HitboxPointSetting setting) {
        super(setting);
        this.point = setting;
    }

    private float left() { return x + 11f; }
    private float right() { return x + width - 11f; }
    private float top() { return y + 18f; }
    private float bottom() { return y + 74f; }
    private float cx() { return left() + (right() - left()) * point.pointX.getValue(); }
    private float cy() { return bottom() - (bottom() - top()) * point.pointY.getValue(); }

    @Override
    public void render(DrawContext context, int mx, int my, float delta) {
        if (dragging) {
            if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                move(mx, my);
            } else {
                dragging = false;
            }
        }

        Fonts.MEDIUM.draw(point.getName(), x + 1f, y + 2f, 6.5f,
                applyAlpha(ThemeColors.color(226, 237, 248, 220)).getRGB());

        float lx = left(), rx = right(), ty = top(), by = bottom();
        int fill = ThemeColors.isLight()
                ? applyAlpha(ThemeColors.color(228, 233, 242, 170)).getRGB()
                : applyAlpha(ThemeColors.color(26, 47, 76, 96)).getRGB();
        int grid = ThemeColors.isLight()
                ? applyAlpha(0x78000000)
                : applyAlpha(ThemeColors.color(150, 185, 220, 72)).getRGB();
        int accent = applyAlpha(ThemeColors.selected().accent());
        int hint = ThemeColors.isLight()
                ? applyAlpha(ThemeColors.color(66, 76, 92, 205)).getRGB()
                : applyAlpha(ThemeColors.color(178, 193, 217, 190)).getRGB();

        if (!ThemeColors.isLight()) {
            Render2D.rect(lx, ty, rx - lx, by - ty, fill, 4f);
            Render2D.outline(lx, ty, rx - lx, by - ty, .75f, grid, 4f);
        }
        Render2D.rect(lx + (rx - lx) / 2f, ty + 1f, .5f, by - ty - 2f, grid);
        Render2D.rect(lx + 1f, ty + (by - ty) / 2f, rx - lx - 2f, .5f, grid);
        Render2D.rect(cx() - 4.5f, cy() - 4.5f, 9f, 9f, accent, 4.5f);
        Fonts.MEDIUM.draw("LMB drag • X " + Math.round(point.pointX.getValue() * 100f) + "%  Y "
                        + Math.round(point.pointY.getValue() * 100f) + "%",
                lx, by + 4f, 5.5f, hint);
    }

    private void move(double mx, double my) {
        float nx = (float) ((mx - left()) / (right() - left()));
        float ny = (float) ((bottom() - my) / (bottom() - top()));
        point.pointX.setValue(Math.max(.05f, Math.min(.95f, nx)));
        point.pointY.setValue(Math.max(.05f, Math.min(.95f, ny)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (mx < left() - 2 || mx > right() + 2 || my < top() - 2 || my > bottom() + 2) return false;
        dragging = true;
        move(mx, my);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        move(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    @Override
    public boolean isHover(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
