package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import mind.beta.modules.module.setting.implement.MultiPointSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class MultiPointComponent extends AbstractSettingComponent {
    private final MultiPointSetting setting;
    private int dragging = -1;

    public MultiPointComponent(MultiPointSetting setting) {
        super(setting);
        this.setting = setting;
    }

    private RotationBuilder.Profile profile() {
        return RotationBuilder.INSTANCE.active();
    }

    private float left() { return x + 11f; }
    private float right() { return x + width - 11f; }
    private float top() { return y + 18f; }
    private float bottom() { return y + 74f; }
    private float pointX(RotationBuilder.Point point) { return left() + (right() - left()) * point.x; }
    private float pointY(RotationBuilder.Point point) { return bottom() - (bottom() - top()) * point.y; }

    @Override
    public void render(DrawContext context, int mx, int my, float delta) {
        if (dragging >= 0) {
            if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                move(mx, my);
            } else {
                dragging = -1;
            }
        }

        Fonts.MEDIUM.draw(setting.getName(), x + 1f, y + 2f, 6.5f,
                applyAlpha(ThemeColors.color(226, 237, 248, 220)).getRGB());

        float lx = left(), rx = right(), ty = top(), by = bottom();
        int fill = ThemeColors.isLight()
                ? applyAlpha(ThemeColors.color(228, 233, 242, 170)).getRGB()
                : applyAlpha(ThemeColors.color(26, 47, 76, 96)).getRGB();
        int grid = ThemeColors.isLight()
                ? applyAlpha(0x78000000)
                : applyAlpha(ThemeColors.color(150, 185, 220, 72)).getRGB();
        int accent = applyAlpha(ThemeColors.selected().accent());
        int accentAlt = applyAlpha(ThemeColors.color(163, 204, 255, 225)).getRGB();
        int hint = ThemeColors.isLight()
                ? applyAlpha(ThemeColors.color(66, 76, 92, 205)).getRGB()
                : applyAlpha(ThemeColors.color(178, 193, 217, 190)).getRGB();

        if (!ThemeColors.isLight()) {
            Render2D.rect(lx, ty, rx - lx, by - ty, fill, 4f);
            Render2D.outline(lx, ty, rx - lx, by - ty, .75f, grid, 4f);
        }
        Render2D.rect(lx + (rx - lx) / 2f, ty + 1f, .5f, by - ty - 2f, grid);
        Render2D.rect(lx + 1f, ty + (by - ty) / 2f, rx - lx - 2f, .5f, grid);

        List<RotationBuilder.Point> points = profile().multiPoints;
        for (int i = 0; i < points.size(); i++) {
            RotationBuilder.Point point = points.get(i);
            int color = i == dragging ? accentAlt : accent;
            Render2D.rect(pointX(point) - 4.5f, pointY(point) - 4.5f, 9f, 9f, color, 4.5f);
        }

        Fonts.MEDIUM.draw(points.size() + " points • LMB drag • RMB add/remove",
                lx, by + 4f, 5.5f, hint);
    }

    private boolean inside(double mx, double my) {
        return mx >= left() && mx <= right() && my >= top() && my <= bottom();
    }

    private int find(double mx, double my) {
        List<RotationBuilder.Point> points = profile().multiPoints;
        for (int i = points.size() - 1; i >= 0; i--) {
            RotationBuilder.Point point = points.get(i);
            if (Math.hypot(mx - pointX(point), my - pointY(point)) <= 7.5f) return i;
        }
        return -1;
    }

    private void move(double mx, double my) {
        List<RotationBuilder.Point> points = profile().multiPoints;
        if (dragging < 0 || dragging >= points.size()) return;
        RotationBuilder.Point point = points.get(dragging);
        float nx = (float) ((mx - left()) / (right() - left()));
        float ny = (float) ((bottom() - my) / (bottom() - top()));
        point.x = RotationBuilder.clamp(nx, .05f, .95f);
        point.y = RotationBuilder.clamp(ny, .05f, .95f);
        RotationBuilder.INSTANCE.save();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) return false;
        int index = find(mx, my);
        List<RotationBuilder.Point> points = profile().multiPoints;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (index >= 0) {
                if (points.size() > 1) {
                    points.remove(index);
                }
            } else if (points.size() < 12) {
                points.add(new RotationBuilder.Point(
                        RotationBuilder.clamp((float) ((mx - left()) / (right() - left())), .05f, .95f),
                        RotationBuilder.clamp((float) ((bottom() - my) / (bottom() - top())), .05f, .95f)
                ));
            }
            RotationBuilder.INSTANCE.save();
            dragging = -1;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0) {
            dragging = index;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging < 0 || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        move(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging < 0) return false;
        dragging = -1;
        RotationBuilder.INSTANCE.save();
        return true;
    }

    @Override
    public boolean isHover(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
