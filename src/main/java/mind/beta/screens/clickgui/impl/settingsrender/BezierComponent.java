package mind.beta.screens.clickgui.impl.settingsrender;

import mind.beta.modules.module.setting.implement.BezierSetting;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class BezierComponent extends AbstractSettingComponent {
    private final BezierSetting curve;
    private int dragging = 0;
    private static final float PAD = 12f;
    private static final float GRAPH_HEIGHT = 84f;

    public BezierComponent(BezierSetting setting) { super(setting); curve = setting; }
    private float gx() { return x + PAD; }
    private float gy() { return y + 17f; }
    private float gw() { return Math.max(40f, width - PAD * 2f); }
    private float gh() { return GRAPH_HEIGHT - 20f; }
    private float px(float v) { return gx() + gw() * v; }
    private float py(float v) { return gy() + gh() * (1f - v); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float bezier(float t, float a, float b) {
        float s = 1f - t;
        return 3f * s*s*t*a + 3f*s*t*t*b + t*t*t;
    }
    private void segment(float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / .7f));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            Render2D.rect(x1 + dx * t - 1.05f, y1 + dy * t - 1.05f,
                    2.1f, 2.1f, color, 1.05f);
        }
    }
    @Override public void render(DrawContext context, int mx, int my, float delta) {
        if (dragging != 0) {
            if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) update(mx, my);
            else dragging = 0;
        }
        Fonts.MEDIUM.draw(curve.getName(), x + 1f, y + 3f, 6.5f,
                applyAlpha(ThemeColors.color(226, 237, 248, 220)).getRGB());
        int grid = ThemeColors.isLight()
                ? applyAlpha(0x5F000000)
                : applyAlpha(ThemeColors.color(125, 150, 183, 45)).getRGB();
        int handleLine = ThemeColors.isLight()
                ? applyAlpha(0xB9000000)
                : applyAlpha(ThemeColors.color(145, 175, 220, 105)).getRGB();
        int accent = applyAlpha((245 << 24) | (ThemeColors.selected().accent() & 0xFFFFFF));
        if (!ThemeColors.isLight()) {
            Render2D.rect(gx(), gy(), gw(), gh(),
                    applyAlpha(ThemeColors.color(8, 15, 27, 85)).getRGB(), 3f);
        }
        for (int i=1;i<4;i++) {
            Render2D.rect(gx()+gw()*i/4f,gy(),.5f,gh(),grid,0f);
            Render2D.rect(gx(),gy()+gh()*i/4f,gw(),.5f,grid,0f);
        }
        float ax = px(curve.x1.getValue()), ay = py(curve.y1.getValue());
        float bx = px(curve.x2.getValue()), by = py(curve.y2.getValue());
        segment(px(0),py(0),ax,ay,handleLine);
        segment(px(1),py(1),bx,by,handleLine);
        float lx = px(0), ly = py(0);
        for(int i=1;i<=160;i++) {
            float t=i/160f;
            float cx=px(bezier(t,curve.x1.getValue(),curve.x2.getValue()));
            float cy=py(bezier(t,curve.y1.getValue(),curve.y2.getValue()));
            segment(lx,ly,cx,cy,accent);
            lx=cx;ly=cy;
        }
        Render2D.rect(ax-3,ay-3,6,6,accent,3f);
        Render2D.rect(bx-3,by-3,6,6,accent,3f);
    }
    private boolean near(double mx,double my,float tx,float ty) {
        return Math.abs(mx-tx)<9 && Math.abs(my-ty)<9;
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(button!=0) return false;
        if(near(mx,my,px(curve.x1.getValue()),py(curve.y1.getValue()))) dragging=1;
        else if(near(mx,my,px(curve.x2.getValue()),py(curve.y2.getValue()))) dragging=2;
        else return false;
        update(mx,my); return true;
    }
    private void update(double mx,double my) {
        if(dragging==0) return;
        float xx=clamp((float)((mx-gx())/gw()));
        float yy=clamp(1f-(float)((my-gy())/gh()));
        if(dragging==1) { curve.x1.setValue(xx); curve.y1.setValue(yy); }
        else { curve.x2.setValue(xx); curve.y2.setValue(yy); }
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(dragging==0) return false;
        update(mx,my); return true;
    }
    @Override public boolean mouseReleased(double mx,double my,int button) {
        if(dragging==0) return false;
        dragging=0; return true;
    }
    @Override public boolean isHover(double mx,double my) {
        return mx>=x && mx<=x+width && my>=y && my<=y+height;
    }
}
