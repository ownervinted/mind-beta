/* Copyright Mind */
package mind.beta.screens.clickgui.impl.rotation;

import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import mind.beta.modules.module.setting.implement.SelectSetting;
import mind.beta.modules.module.setting.implement.SliderSettings;
import mind.beta.screens.clickgui.impl.collection.TabPanel;
import mind.beta.screens.clickgui.impl.settingsrender.SelectComponent;
import mind.beta.screens.clickgui.impl.settingsrender.SliderComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class RotationBuilderPanel {
    private static final float WIDTH = 216f;
    private static final float HEIGHT = 250f;
    private static final String[] TABS = {"Motion", "Limits", "Aim", "Curve", "Configs"};
    private static final int[] MOTION = {0, 1, 2, 3, 4};
    private static final int[] LIMITS = {5, 6, 7};
    private static final int[] AIM = {8, 9, 10};
    private static final int[] AIM_SNAP = {8, 10};
    private static final Identifier BACKGROUND = Identifier.of("mind", "textures/background/pink.png");

    private final SliderSettings[] settings = new SliderSettings[11];
    private final SliderComponent[] sliders = new SliderComponent[11];
    private final SelectSetting aim = new SelectSetting("Aim behavior").value("Sticky", "Out of hitbox", "Snap");
    private final SelectComponent aimComponent = new SelectComponent(aim);
    private final float[] buttonHover = new float[4];
    private final float[] tabHover = new float[5];
    private final Map<String, Float> configTransitions = new HashMap<>();

    private float x, y, progress, opacity = 1f, pageOpacity = 1f;
    private int selectedPage, paintedPage, dragging = -1, pointDrag = -1, selectedConfig = -1, configScroll;
    private long previousFrame = System.nanoTime(), dirtyAt;
    private boolean wanted, editingName;
    private String configName = "", message = "", deletePending;
    private long deletePendingUntil, messageUntil;
    private float reveal;

    public RotationBuilderPanel() {
        String[] names = {"Approach speed", "Tracking speed", "Return speed", "Acceleration", "Deceleration",
                "Yaw limit", "Pitch limit", "Fine smoothing", "Outside distance", "Pre-aim ticks", "Jitter amount"};
        float[] minimum = {1, 1, 1, .01f, .01f, 1, 1, .05f, .5f, 1, 0};
        float[] maximum = {100, 100, 100, 1, 1, 180, 90, 1, 18, 5, 4};
        for (int i = 0; i < names.length; i++) {
            settings[i] = new SliderSettings(names[i], "").range(minimum[i], maximum[i]);
            if (i == 9) settings[i].setInteger(true);
            sliders[i] = new SliderComponent(settings[i]);
        }
        syncFromProfile();
    }

    public void render(DrawContext context, float bgX, float bgY, float mouseX, float mouseY, float guiAlpha, boolean show) {
        long now = System.nanoTime();
        float dt = Math.max(.0001f, Math.min(.05f, (now - previousFrame) / 1_000_000_000f));
        previousFrame = now;
        if (show != wanted) {
            wanted = show;
            if (!show) { editingName = false; release(); }
        }
        progress = approach(progress, wanted ? 1f : 0f, dt, 16f);
        if (Math.abs(progress - (wanted ? 1f : 0f)) < .001f) progress = wanted ? 1f : 0f;
        if (progress <= .001f) return;
        opacity = Math.max(0f, Math.min(1f, guiAlpha * progress));
        x = bgX - WIDTH - 9f;
        y = bgY;
        float ease = easeOut(progress);
        float scale = .86f + .14f * ease;
        float cx = x + WIDTH / 2f, cy = y + HEIGHT / 2f;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);
        double mx = localX(mouseX);
        double my = localY(mouseY);

        drawBackground();
        int main = color(234, 242, 255, 248);
        int secondary = color(156, 183, 218, 220);
        text("Rotation Builder", x + 12, y + 11, 10.5f, main, true);
        text("Build your own aim behavior", x + 12, y + 28, 6.3f, secondary, false);
        rect(x + 12, y + 41, WIDTH - 24, .6f, color(190, 216, 255, 27), 0);

        for (int i = 0; i < TABS.length; i++) {
            float bx = x + 8f + i * 40f;
            boolean hovered = inside(mx, my, bx, y + 47, 40, 21);
            tabHover[i] = approach(tabHover[i], hovered ? 1f : 0f, dt, 17f);
            float on = selectedPage == i ? 1f : 0f;
            rect(bx, y + 47f, 39f, 20f,
                    ThemeColors.isLight() ? color(116, 161, 231, 15 + 26 * on + 12 * tabHover[i])
                            : color(53, 129, 226, 15 + 48 * on + 25 * tabHover[i]), 4f);
            center(TABS[i], bx, y + 47f, 39f, 20f, 6.2f,
                    color((int) (168 + 65 * on), (int) (192 + 45 * on), 244, 185 + 55 * on), false);
            if (on > .5f) rect(bx + 10, y + 66, 19, 1f, color(42, 149, 255, 218), 1f);
        }
        if (selectedPage != paintedPage) {
            pageOpacity = approach(pageOpacity, 0f, dt, 22f);
            if (pageOpacity < .07f) {
                paintedPage = selectedPage;
                pageOpacity = 0f;
                reveal = 0f;
            }
        } else {
            pageOpacity = approach(pageOpacity, 1f, dt, 18f);
        }
        reveal = approach(reveal, 1f, dt, 11f);

        context.getMatrices().pushMatrix();
        float pageOffset = (1f - pageOpacity) * 7f;
        context.getMatrices().translate(0f, pageOffset);
        float savedOpacity = opacity;
        opacity *= pageOpacity;
        if (paintedPage == 0) renderSliders(context, MOTION, 77, 32, mx, my, dt);
        if (paintedPage == 1) {
            renderSliders(context, LIMITS, 88, 39, mx, my, dt);
            hint("Yaw/Pitch cap the turn per tick.", 216);
            hint("Smoothing controls micro-movement.", 229);
        }
        if (paintedPage == 2) renderAim(context, mx, my, dt);
        if (paintedPage == 3) renderCurve(mx, my);
        if (paintedPage == 4) renderConfigs(mx, my, dt);
        opacity = savedOpacity;
        context.getMatrices().popMatrix();
        if (System.currentTimeMillis() < messageUntil) {
            rect(x + 10f, y + 21f, WIDTH - 20f, 18f, color(8, 32, 57, 222), 4f);
            center(TabPanel.trim(message, WIDTH - 32f, 6f), x + 10f, y + 21f,
                    WIDTH - 20f, 18f, 6f, main, false);
        }
        context.getMatrices().popMatrix();
        if (dirtyAt > 0 && System.currentTimeMillis() - dirtyAt > 330L) flush();
    }

    private void drawBackground() {
        if (ThemeColors.isLight()) {
            rect(x, y, WIDTH, HEIGHT,
                    (Math.round(255f * opacity) << 24) | 0xF7F9FF, 8f);
        } else {
            Render2D.texture(BACKGROUND, x, y, WIDTH, HEIGHT, 0f, 0f, 1f, 1f,
                    ThemeColors.textureTint(Math.round(255f * opacity)), 1f, 8f);
            rect(x, y, WIDTH, HEIGHT, color(0, 64, 128, 42), 8f);
        }
    }

    private void renderSliders(DrawContext ctx, int[] indices, float start, float step, double mx, double my, float dt) {
        RotationBuilder.Profile p = RotationBuilder.INSTANCE.active();
        for (int i = 0; i < indices.length; i++) {
            int id = indices[i];
            float posY = y + start + step * i;
            float entry = Math.max(0f, Math.min(1f, reveal * 2.1f - i * .17f));
            SliderComponent slider = sliders[id];
            slider.position(x + 14f, posY + (1 - entry) * 5f);
            slider.size(WIDTH - 28, 19);
            slider.setAlphaMultiplier(opacity * entry);
            slider.render(ctx, (int) mx, (int) my, dt);
            float changed = settings[id].getValue();
            if (Math.abs(changed - get(p, id)) > .0001f && entry > .3f) {
                set(p, id, changed);
                markDirty();
            }
        }
    }

    private void renderAim(DrawContext ctx, double mx, double my, float dt) {
        RotationBuilder.Profile p = RotationBuilder.INSTANCE.active();
        aimComponent.position(x + 14, y + 81);
        aimComponent.size(WIDTH - 28, 22);
        aimComponent.setAlphaMultiplier(opacity);
        aimComponent.render(ctx, (int) mx, (int) my, dt);
        if ("Sticky".equals(p.aim)) {
            rect(x + 16, y + 121, WIDTH - 32, 77, color(10, 31, 55, 106), 5f);
            drawStickyPreview();
        } else {
            renderSliders(ctx, "Snap".equals(p.aim) ? AIM_SNAP : AIM, 117, 31, mx, my, dt);
        }
        rect(x + 12, y + 215, WIDTH - 24, 26, color(10, 27, 48, 92), 5f);
        String first = switch (p.aim) {
            case "Snap" -> "Wait outside, snap in on attack, snap out.";
            case "Out of hitbox" -> "Ease outside, then glide in before attack.";
            default -> "Locks to one fixed point in the hitbox.";
        };
        text(first, x + 20, y + 222, 6f, color(192, 213, 239, 225), false);
        text("Outside margin is measured in degrees.", x + 20, y + 232, 5.6f, color(151, 176, 208, 185), false);
    }

    private void drawStickyPreview() {
        float cx = x + WIDTH * .5f, cy = y + 159f;
        rect(cx - 13f, cy - 23f, 26f, 46f, color(30, 111, 211, 55), 4f);
        Render2D.outline(cx - 13f, cy - 23f, 26f, 46f, .6f,
                color(114, 189, 255, 150), 4f);
        rect(cx - 1.5f, cy - 3f, 3f, 6f, color(188, 226, 255, 252), 1.5f);
        rect(cx - 12f, cy - 1f, 24f, .5f, color(102, 181, 255, 90), 0f);
        rect(cx - .25f, cy - 22f, .5f, 44f, color(102, 181, 255, 90), 0f);
    }

    private void renderCurve(double mx, double my) {
        RotationBuilder.Profile p = RotationBuilder.INSTANCE.active();
        text("Velocity easing", x + 13, y + 83, 8f, color(231, 242, 255, 245), true);
        text("Controls how turning speed falls near aim.", x + 13, y + 98, 6f, color(156, 188, 221, 216), false);
        float gx = x + 19f, gy = y + 119f, gw = WIDTH - 38f, gh = 89f;
        rect(gx, gy, gw, gh, color(6, 22, 40, 115), 4f);
        for (int i = 1; i <= 3; i++) {
            rect(gx + gw * i / 4, gy, .5f, gh, color(184, 213, 245, 20), 0);
            rect(gx, gy + gh * i / 4, gw, .5f, color(184, 213, 245, 20), 0);
        }
        int accent = color(53, 158, 255, 252);
        for (int i = 0; i < 85; i++) {
            float a = i / 85f, b = (i + 1) / 85f;
            line(gx + gw * a, gy + gh * (1f - p.curve(a)),
                    gx + gw * b, gy + gh * (1f - p.curve(b)), accent);
        }
        float x1 = gx + p.x1 * gw, y1 = gy + (1 - p.y1) * gh;
        float x2 = gx + p.x2 * gw, y2 = gy + (1 - p.y2) * gh;
        line(gx, gy + gh, x1, y1, color(145, 179, 214, 130));
        line(gx + gw, gy, x2, y2, color(145, 179, 214, 130));
        rect(x1 - 3.5f, y1 - 3.5f, 7f, 7f, color(229, 243, 255, 255), 3.5f);
        rect(x2 - 3.5f, y2 - 3.5f, 7f, 7f, color(229, 243, 255, 255), 3.5f);
        if (pointDrag >= 0) {
            float nx = RotationBuilder.clamp((float) (mx - gx) / gw, 0, 1);
            float ny = RotationBuilder.clamp(1f - (float) (my - gy) / gh, 0, 1);
            if (pointDrag == 0) { p.x1 = nx; p.y1 = ny; }
            else { p.x2 = nx; p.y2 = ny; }
            markDirty();
        }
        text("Drag the two points to shape the curve", x + 18f, y + 222, 6.3f,
                color(174, 200, 231, 230), false);
    }

    private void renderConfigs(double mx, double my, float dt) {
        List<String> names = RotationBuilder.INSTANCE.configs();
        configScroll = Math.max(0, Math.min(configScroll, Math.max(0, names.size() - 3)));
        if (selectedConfig >= names.size()) selectedConfig = -1;
        rect(x + 11, y + 78, WIDTH - 22, 23, color(8, 27, 47, 112), 4f);
        String nameText = configName.isEmpty() ? "Name your preset..." : TabPanel.trim(configName, WIDTH - 48, 7f);
        text(nameText, x + 20, y + 85, 7f,
                configName.isEmpty() ? color(142, 167, 200, 178) : color(234, 243, 255, 248), false);
        if (editingName && System.currentTimeMillis() % 1000L < 500L) {
            float caret = x + 20 + Fonts.MEDIUM.getWidth(nameText, 7f);
            rect(caret + 1f, y + 84, .8f, 11, color(195, 228, 255, 240), 0f);
        }
        rect(x + 11, y + 109, WIDTH - 22, 83, color(7, 21, 39, 73), 5f);
        for (int i = 0; i < 3; i++) {
            int index = configScroll + i;
            if (index >= names.size()) break;
            String name = names.get(index);
            float top = y + 111 + 27 * i;
            boolean focused = selectedConfig == index;
            boolean hovered = inside(mx, my, x + 13, top, WIDTH - 26, 25);
            float target = focused ? 1f : hovered ? .5f : 0f;
            float selected = approach(configTransitions.getOrDefault(name, 0f), target, dt, 16f);
            configTransitions.put(name, selected);
            float entry = Math.min(1f, Math.max(0f, reveal * 2f - i * .22f));
            float shift = (1f - entry) * 5f;
            rect(x + 13, top + 1f + shift, WIDTH - 26, 24,
                    color((int) (18 + selected * 12), (int) (43 + selected * 54),
                            (int) (77 + selected * 92), (70 + selected * 42) * entry), 4f);
            text(TabPanel.trim(name, WIDTH - 60, 7f), x + 22, top + 9 + shift, 7f,
                    color(226, 237, 255, 235 * entry), false);
            if (name.equals(RotationBuilder.INSTANCE.savedConfig)) {
                rect(x + WIDTH - 25f, top + 11f + shift, 5f, 5f, color(39, 171, 255, 240 * entry), 2.5f);
            }
        }
        if (names.isEmpty()) center("No rotation presets yet", x + 13, y + 124, WIDTH - 26, 43,
                7f, color(150, 182, 218, 200), false);
        if (names.size() > 3) text((configScroll + 1) + "–" + Math.min(names.size(), configScroll + 3) + " / " + names.size(),
                x + WIDTH - 48, y + 195, 5.2f, color(142, 179, 218, 200), false);
        renderButton("Save new", 0, x + 11, y + 205, 95, 18, mx, my, dt, true);
        renderButton("Update", 1, x + 110, y + 205, 95, 18, mx, my, dt, selectedConfig >= 0);
        renderButton("Load", 2, x + 11, y + 227, 95, 18, mx, my, dt, selectedConfig >= 0);
        boolean confirmDelete = deletePending != null && System.currentTimeMillis() < deletePendingUntil;
        renderButton(confirmDelete ? "Confirm delete" : "Delete", 3, x + 110, y + 227, 95, 18, mx, my, dt, selectedConfig >= 0);
    }

    private void renderButton(String label, int index, float xx, float yy, float w, float h,
                              double mx, double my, float dt, boolean enabled) {
        buttonHover[index] = approach(buttonHover[index], enabled && inside(mx, my, xx, yy, w, h) ? 1 : 0, dt, 18f);
        rect(xx, yy, w, h, color(index == 0 ? 22 : 19, index == 0 ? 100 : 49,
                index == 0 ? 173 : 86, enabled ? 118 + 36 * buttonHover[index] : 40), 4f);
        center(label, xx, yy, w, h, 6.5f, color(231, 242, 255, enabled ? 240 : 98), false);
    }

    private void syncFromProfile() {
        RotationBuilder.Profile p = RotationBuilder.INSTANCE.active();
        for (int i = 0; i < settings.length; i++) settings[i].setValue(get(p, i));
        aim.setSelected(p.aim);
    }

    private void markDirty() { dirtyAt = System.currentTimeMillis(); }
    private void flush() {
        dirtyAt = 0L;
        RotationBuilder.INSTANCE.save();
    }

    private void toast(String text) {
        message = text;
        messageUntil = System.currentTimeMillis() + 2300L;
    }

    private static float get(RotationBuilder.Profile p, int id) {
        return switch (id) {
            case 0 -> p.acquire;
            case 1 -> p.track;
            case 2 -> p.returning;
            case 3 -> p.acceleration;
            case 4 -> p.deceleration;
            case 5 -> p.yaw;
            case 6 -> p.pitch;
            case 7 -> p.smoothing;
            case 8 -> p.outside;
            case 9 -> p.leadTicks;
            default -> p.jitter;
        };
    }

    private static void set(RotationBuilder.Profile p, int id, float v) {
        switch (id) {
            case 0 -> p.acquire = v;
            case 1 -> p.track = v;
            case 2 -> p.returning = v;
            case 3 -> p.acceleration = v;
            case 4 -> p.deceleration = v;
            case 5 -> p.yaw = v;
            case 6 -> p.pitch = v;
            case 7 -> p.smoothing = v;
            case 8 -> p.outside = v;
            case 9 -> p.leadTicks = Math.round(v);
            case 10 -> p.jitter = v;
        }
    }

    private static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1f - (float) Math.exp(-dt * speed));
    }

    private static float easeOut(float t) { return 1f - (float) Math.pow(1f - t, 3); }

    private int color(int r, int g, int b, float a) {
        return ThemeColors.color(r, g, b, Math.max(0, Math.min(255, Math.round(a * opacity)))).getRGB();
    }

    private void rect(float xx, float yy, float w, float h, int c, float radius) {
        Render2D.rect(xx, yy, w, h, c, radius);
    }

    private void text(String str, float xx, float yy, float size, int c, boolean bold) {
        if (bold) Fonts.BOLD.draw(str, xx, yy, size, c);
        else Fonts.MEDIUM.draw(str, xx, yy, size, c);
    }

    private void center(String str, float xx, float yy, float w, float h, float size, int c, boolean bold) {
        float width = bold ? Fonts.BOLD.getWidth(str, size) : Fonts.MEDIUM.getWidth(str, size);
        text(str, xx + (w - width) / 2f, yy + (h - size) / 2f - .2f, size, c, bold);
    }

    private void hint(String str, float yOffset) {
        text(str, x + 14, y + yOffset, 6.5f, color(169, 194, 225, 224), false);
    }

    private void line(float x1, float y1, float x2, float y2, int color) {
        int steps = Math.max(1, (int) (Math.hypot(x2 - x1, y2 - y1) * 1.5));
        for (int n = 0; n <= steps; n++) {
            float t = (float) n / steps;
            rect(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 1f, 1f, color, 0f);
        }
    }

    private static boolean inside(double mx, double my, float xx, float yy, float w, float h) {
        return mx >= xx && mx <= xx + w && my >= yy && my <= yy + h;
    }

    private double localX(double mouseX) {
        float s = .86f + .14f * easeOut(progress);
        return (mouseX - (x + WIDTH / 2f)) / s + x + WIDTH / 2f;
    }

    private double localY(double mouseY) {
        float s = .86f + .14f * easeOut(progress);
        return (mouseY - (y + HEIGHT / 2f)) / s + y + HEIGHT / 2f;
    }

    public boolean click(double mouseX, double mouseY, int button) {
        if (!wanted || progress < .72f || button != 0) return false;
        double mx = localX(mouseX), my = localY(mouseY);
        if (!inside(mx, my, x, y, WIDTH, HEIGHT)) return false;
        if (my >= y + 47f && my < y + 68f) {
            int i = (int) ((mx - x - 8f) / 40f);
            if (i >= 0 && i < TABS.length) {
                selectedPage = i;
                deletePending = null;
                editingName = false;
                release();
            }
            return true;
        }
        if (paintedPage != selectedPage || pageOpacity < .75f) return true;
        if (paintedPage == 0 || paintedPage == 1 || paintedPage == 2) {
            if (paintedPage == 2 && aimComponent.mouseClicked(mx, my, button)) {
                RotationBuilder.INSTANCE.active().aim = aim.getSelected();
                markDirty();
                return true;
            }
            int[] ids = paintedPage == 0 ? MOTION : paintedPage == 1 ? LIMITS :
                    "Sticky".equals(RotationBuilder.INSTANCE.active().aim) ? new int[0] :
                    "Snap".equals(RotationBuilder.INSTANCE.active().aim) ? AIM_SNAP : AIM;
            for (int id : ids) {
                if (sliders[id].mouseClicked(mx, my, button)) {
                    dragging = id;
                    return true;
                }
            }
        } else if (paintedPage == 3 && my >= y + 119 && my <= y + 210) {
            RotationBuilder.Profile p = RotationBuilder.INSTANCE.active();
            double dx1 = mx - (x + 19 + p.x1 * (WIDTH - 38));
            double dy1 = my - (y + 119 + (1f - p.y1) * 89f);
            double dx2 = mx - (x + 19 + p.x2 * (WIDTH - 38));
            double dy2 = my - (y + 119 + (1f - p.y2) * 89f);
            pointDrag = dx1 * dx1 + dy1 * dy1 < dx2 * dx2 + dy2 * dy2 ? 0 : 1;
        } else if (paintedPage == 4) {
            if (inside(mx, my, x + 11, y + 78, WIDTH - 22, 23)) {
                editingName = true;
                return true;
            }
            editingName = false;
            List<String> names = RotationBuilder.INSTANCE.configs();
            if (inside(mx, my, x + 13, y + 110, WIDTH - 26, 82)) {
                int id = configScroll + (int) ((my - y - 110) / 27);
                if (id >= 0 && id < names.size()) {
                    selectedConfig = id;
                    configName = names.get(id);
                    deletePending = null;
                }
                return true;
            }
            RotationBuilder store = RotationBuilder.INSTANCE;
            String name = selectedConfig >= 0 && selectedConfig < names.size() ? names.get(selectedConfig) : null;
            if (inside(mx, my, x + 11, y + 205, 95, 18)) {
                String result = store.savePreset(configName);
                if (result.isEmpty()) toast("Invalid or duplicate name");
                else { selectByName(result); toast("Preset saved"); }
                return true;
            }
            if (inside(mx, my, x + 110, y + 205, 95, 18)) {
                if (name != null && !store.updatePreset(name).isEmpty()) toast("Preset updated");
                return true;
            }
            if (inside(mx, my, x + 11, y + 227, 95, 18)) {
                if (name != null && store.loadPreset(name)) {
                    syncFromProfile();
                    configName = name;
                    toast("Preset loaded");
                }
                return true;
            }
            if (inside(mx, my, x + 110, y + 227, 95, 18)) {
                if (name != null) {
                    if (name.equals(deletePending) && System.currentTimeMillis() < deletePendingUntil) {
                        if (store.deletePreset(name)) {
                            configName = "";
                            selectedConfig = -1;
                            configScroll = 0;
                            toast("Preset deleted");
                        }
                        deletePending = null;
                    } else {
                        deletePending = name;
                        deletePendingUntil = System.currentTimeMillis() + 3000L;
                        toast("Click again to delete");
                    }
                }
                return true;
            }
        }
        return true;
    }

    private void selectByName(String name) {
        List<String> names = RotationBuilder.INSTANCE.configs();
        selectedConfig = names.indexOf(name);
        if (selectedConfig >= 0) configScroll = Math.max(0, selectedConfig - 1);
    }

    public boolean scroll(double mouseX, double mouseY, double vertical) {
        if (!wanted || selectedPage != 4 || pageOpacity < .6f) return false;
        if (!inside(localX(mouseX), localY(mouseY), x, y, WIDTH, HEIGHT)) return false;
        int count = RotationBuilder.INSTANCE.configs().size();
        configScroll = Math.max(0, Math.min(Math.max(0, count - 3), configScroll - (int) Math.signum(vertical)));
        return true;
    }

    public void release() {
        if (dragging >= 0) sliders[dragging].mouseReleased(0, 0, 0);
        if (dragging >= 0 || pointDrag >= 0) flush();
        dragging = -1;
        pointDrag = -1;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if (!wanted) return false;
        if (editingName) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { editingName = false; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editingName = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !configName.isEmpty()) {
                configName = configName.substring(0, configName.offsetByCodePoints(configName.length(), -1));
            }
            return true;
        }
        for (SliderComponent sc : sliders) if (sc.keyPressed(key, scan, mods)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean charTyped(char value, int modifiers) {
        if (!wanted) return false;
        if (editingName) {
            if (!Character.isISOControl(value) && configName.length() < 48 && "\\/:*?\"<>|".indexOf(value) < 0) {
                configName += value;
            }
            return true;
        }
        for (SliderComponent sc : sliders) if (sc.charTyped(value, modifiers)) return true;
        return false;
    }
}
