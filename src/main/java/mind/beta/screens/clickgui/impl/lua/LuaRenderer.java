package mind.beta.screens.clickgui.impl.lua;

import net.minecraft.client.gui.DrawContext;
import mind.beta.Initialization;
import mind.beta.scripting.LuaModule;
import mind.beta.scripting.LuaScript;
import mind.beta.scripting.ScriptManager;
import mind.beta.scripting.extensions.LuaExtensionRegistry;
import mind.beta.screens.clickgui.impl.collection.TabPanel;
import mind.beta.screens.clickgui.impl.collection.TabPanel.Frame;
import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.render.shader.Scissor;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LuaRenderer {
    private static final float LIST_TOP = 48f;
    private static final float LIST_HEIGHT = 156f;
    private static final float ROW_HEIGHT = 45f;
    private static final float ROW_STEP = 52f;
    private final TabPanel panel = new TabPanel();
    private float scroll;
    private float targetScroll;
    private boolean active;
    private String notice = "";
    private boolean noticeError;
    private long noticeUntil;

    public void reset(boolean active) {
        this.active = active;
        panel.reset(active);
    }

    public void onOpened() {
        reset(true);
    }

    public void onSelected() {
        setSelected(true);
    }

    public void setSelected(boolean active) {
        this.active = active;
        panel.setActive(active);
    }

    public void render(DrawContext context, float bgX, float bgY, float mouseX, float mouseY,
                       float delta, float alphaMultiplier) {
        Frame f = panel.frame(bgX, bgY, alphaMultiplier);
        if (f.alpha() <= 0.001f) {
            return;
        }
        ScriptManager manager = getManager();
        List<String> files = manager == null ? List.of() : manager.getDiscoveredScripts();
        double mx = f.localX(mouseX);
        double my = f.localY(mouseY);
        f.eyebrow("SCRIPT LIBRARY", 3f, 0f, f.color(131, 157, 191, 170));
        f.bold("Lua scripts", 2f, 12f, 11.5f, f.color(234, 242, 255, 248));
        String count = manager == null ? "Scripts are unavailable"
                : manager.getActiveScriptCount() + " active  /  " + files.size() + " in your library";
        panel.label(f, "count", count, 3f, 31f, 5.5f, 180f, false, 141, 161, 190, 190);
        panel.button(f, "reload-all", "Reload", 196f, 9f, 43f, 22f,
                TabPanel.inside(mx, my, 196f, 9f, 43f, 22f), manager != null, false);
        panel.button(f, "folder", "Folder", 243f, 9f, 46f, 22f,
                TabPanel.inside(mx, my, 243f, 9f, 46f, 22f), manager != null, false);
        f.gradient(3f, 42f, 286f, 0.5f, f.color(158, 190, 231, 25), f.color(158, 190, 231, 0), 0f);
        clampScroll(files.size());
        float maximum = Math.max(0f, contentHeight(files.size()) - LIST_HEIGHT);
        scroll = Math.max(0f, Math.min(maximum, panel.animate("scroll", targetScroll, scroll, 20f)));
        Scissor.enable(f.x(), f.y() + LIST_TOP * f.scale(), 292f * f.scale(), LIST_HEIGHT * f.scale(), 2f);
        try {
            if (files.isEmpty()) {
                f.gradient(2f, LIST_TOP, 286f, LIST_HEIGHT, f.color(16, 35, 59, 95), f.color(6, 17, 31, 40), 7f);
                f.rect(20f, 78f, 19f, 1.2f, f.color(145, 193, 255, 190), 1f);
                f.bold("Start with an idea.", 20f, 96f, 11f, f.color(234, 243, 255, 245));
                f.regular("Your scripts belong here.", 20f, 117f, 6.3f, f.color(155, 179, 210, 210));
                f.regular("Add a .lua file to Folder, then Reload.", 20f, 133f, 5.5f, f.color(135, 156, 184, 175));
            }
            panel.retainItems(new HashSet<>(files));
            int visible = 0;
            for (int i = 0; i < files.size(); i++) {
                String name = files.get(i);
                float rowY = panel.animate("row-y:" + name, i * ROW_STEP, i * ROW_STEP, 24f);
                float y = LIST_TOP + rowY - scroll;
                if (y + ROW_HEIGHT < LIST_TOP || y > LIST_TOP + LIST_HEIGHT) {
                    continue;
                }
                float entry = panel.entry(name, visible++);
                renderRow(manager, name, f.shift(0f, 7f * (1f - entry)).opacity(entry),
                        y, mx, my - 7f * (1f - entry));
            }
        } finally {
            Scissor.disable();
        }
        panel.scrollBar(f, 290f, LIST_TOP + 3f, LIST_HEIGHT - 6f, Math.max(0f, contentHeight(files.size()) - 6f), scroll,
                TabPanel.inside(mx, my, 0f, LIST_TOP, 292f, LIST_HEIGHT));
        panel.toast(f, notice, noticeUntil, noticeError);
    }

    private void renderRow(ScriptManager manager, String name, Frame f, float y, double mx, double my) {
        LuaScript script = manager.getScript(name);
        boolean loaded = script != null;
        boolean enabled = loaded && script.isEnabled();
        boolean inList = my >= LIST_TOP && my <= LIST_TOP + LIST_HEIGHT;
        float hover = panel.hover("row:" + name, inList && TabPanel.inside(mx, my, 2f, y, 285f, ROW_HEIGHT));
        float toggle = panel.animate("toggle:" + name, enabled ? 1f : 0f, enabled ? 1f : 0f, 24f);
        float available = panel.animate("loaded:" + name, loaded ? 1f : 0f, loaded ? 1f : 0f, 22f);
        f.gradient(2f, y, 285f, ROW_HEIGHT, f.color(20, 39, 64, 65f + 45f * hover),
                f.color(7, 19, 35, 80f + 35f * hover), 6f);
        f.rect(2f, y + 11f, 1.3f, 23f, loaded ? f.color(142, 191, 255, 20f + toggle * 75f + hover * 65f)
                : f.color(230, 132, 132, 180), 1f);
        f.rect(15f, y + ROW_HEIGHT - 0.5f, 182f + hover * 68f, 0.4f,
                f.color(146, 177, 219, 9f + 13f * hover), 0f);
        String title = name.toLowerCase(java.util.Locale.ROOT).endsWith(".lua")
                ? name.substring(0, name.length() - 4) : name;
        f.bold(TabPanel.trim(title, 179f, 7.7f), 15f, y + 10f, 7.7f,
                loaded ? f.color(231, 240, 253, 190f + 50f * toggle + 10f * hover) : f.color(235, 143, 143, 245));
        String description = loaded ? details(script) : loadError(manager, name);
        Frame detail = f.shift(0f, -2f * hover).opacity(1f - hover);
        detail.regular(TabPanel.trim(description, 184f, 5.4f), 15f, y + 27f, 5.4f,
                loaded ? detail.color(144, 165, 193, 185) : detail.color(220, 132, 132, 220));
        Frame path = f.shift(0f, 2f * (1f - hover)).opacity(hover);
        path.regular(TabPanel.trim("scripts/" + name, 184f, 5.4f), 15f, y + 27f, 5.4f,
                path.color(176, 199, 231, 215));
        panel.button(f, "reload:" + name, "Reload", 211f, y + 11f, 35f, 22f,
                inList && TabPanel.inside(mx, my, 211f, y + 11f, 35f, 22f), true, false);
        float switchHover = panel.hover("switch:" + name,
                loaded && inList && TabPanel.inside(mx, my, 250f, y + 10f, 33f, 24f));
        float pressed = panel.pressAmount("switch:" + name);
        renderSwitch(f, 257f, y + 17f + pressed * 0.3f, toggle, available, switchHover);
    }

    private void renderSwitch(Frame f, float x, float y, float value, float available, float hover) {
        float opacity = 0.3f + 0.7f * available;
        if (ThemeColors.isLight()) {
            int ring = (Math.max(0, Math.min(255, Math.round(28f * hover * opacity * f.alpha()))) << 24)
                    | (ThemeColors.selected().accent() & 0xFFFFFF);
            f.rect(x - 2f, y - 2f, 26f, 15f, ring, 7.5f);
            f.rect(x, y, 22f, 11f, ThemeColors.switchTrack(value, hover,
                    Math.round((205f + 50f * value) * opacity * f.alpha())), 5.5f);
            f.rect(x + 1.3f + 11f * value, y + 1.3f, 8.4f, 8.4f,
                    ThemeColors.switchKnob(value, Math.round(255f * opacity * f.alpha())), 4.2f);
            return;
        }
        f.rect(x - 2f, y - 2f, 26f, 15f, f.color(110, 170, 250, hover * 13f * opacity), 7.5f);
        f.rect(x, y, 22f, 11f,
                f.color(Math.round(9f + 30f * value), Math.round(25f + 81f * value),
                        Math.round(44f + 166f * value), (125f + 75f * value) * opacity), 5.5f);
        f.rect(x + 1.3f + 11f * value, y + 1.3f, 8.4f, 8.4f,
                f.color(Math.round(125f + 109f * value), Math.round(148f + 96f * value),
                        Math.round(177f + 78f * value), (190f + 55f * value) * opacity), 4.2f);
    }

    private String details(LuaScript script) {
        Set<String> categories = new LinkedHashSet<>();
        String description = "";
        for (LuaModule module : script.getModules()) {
            categories.add(module.getCategory().getReadableName());
            if (description.isBlank() && module.getDescription() != null && !module.getDescription().isBlank()) {
                description = module.getDescription();
            }
        }
        LuaExtensionRegistry registry = LuaExtensionRegistry.getInstance();
        List<String> rotations = registry.auraRotationNamesFor(script);
        List<String> resolvers = registry.maceResolverNamesFor(script);
        if (!rotations.isEmpty()) {
            categories.add("Aura: " + String.join(", ", rotations));
        }
        if (!resolvers.isEmpty()) {
            categories.add("Mace: " + String.join(", ", resolvers));
        }
        String types = categories.isEmpty() ? "Script plugin" : String.join(" / ", categories);
        return description.isBlank() ? types : types + " · " + description;
    }

    private String loadError(ScriptManager manager, String name) {
        for (String error : manager.getErrors()) {
            if (error.startsWith(name + ":")) {
                return error.substring(name.length() + 1).trim();
            }
        }
        return "Reload to try again";
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float bgX, float bgY) {
        if (!active) {
            return false;
        }
        Frame f = panel.snapshot(bgX, bgY, 1f);
        double mx = f.localX(mouseX);
        double my = f.localY(mouseY);
        if (!panel.isActive() || !TabPanel.inside(mx, my, 0f, 0f, 292f, 204f)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        ScriptManager manager = getManager();
        if (manager == null) {
            return true;
        }
        if (TabPanel.inside(mx, my, 196f, 9f, 43f, 22f)) {
            panel.press("reload-all");
            manager.reload();
            targetScroll = 0f;
            showNotice("Scripts reloaded", false);
            return true;
        }
        if (TabPanel.inside(mx, my, 243f, 9f, 46f, 22f)) {
            panel.press("folder");
            if (!manager.openScriptsDirectory()) {
                showNotice("Could not open scripts folder", true);
            }
            return true;
        }
        if (!TabPanel.inside(mx, my, 0f, LIST_TOP, 292f, LIST_HEIGHT)) {
            return true;
        }
        List<String> files = manager.getDiscoveredScripts();
        for (int i = 0; i < files.size(); i++) {
            String name = files.get(i);
            float entry = panel.entryValue(name);
            float y = LIST_TOP + panel.value("row-y:" + name, i * ROW_STEP)
                    - scroll + 7f * (1f - entry);
            if (entry < 0.15f) {
                continue;
            }
            if (TabPanel.inside(mx, my, 211f, y + 11f, 35f, 22f)) {
                panel.press("reload:" + name);
                boolean success = manager.reloadScript(name);
                showNotice(success ? "Reloaded " + name : "Load error: " + loadError(manager, name), !success);
                return true;
            }
            if (TabPanel.inside(mx, my, 250f, y + 10f, 33f, 24f)) {
                LuaScript script = manager.getScript(name);
                if (script != null) {
                    panel.press("switch:" + name);
                    manager.toggleScript(script);
                }
                return true;
            }
        }
        return true;
    }

    public void mouseReleased() {
        panel.release();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double vertical, float bgX, float bgY) {
        if (!active) {
            return false;
        }
        Frame f = panel.snapshot(bgX, bgY, 1f);
        if (!panel.isActive() || !TabPanel.inside(f.localX(mouseX), f.localY(mouseY), 0f, LIST_TOP, 292f, LIST_HEIGHT)) {
            return false;
        }
        ScriptManager manager = getManager();
        if (manager != null) {
            targetScroll -= (float) vertical * 31f;
            clampScroll(manager.getDiscoveredScripts().size());
            panel.scrolled();
        }
        return true;
    }

    private float contentHeight(int count) {
        return Math.max(0f, count * ROW_STEP - (ROW_STEP - ROW_HEIGHT));
    }

    private void clampScroll(int count) {
        float max = Math.max(0f, contentHeight(count) - LIST_HEIGHT);
        targetScroll = Math.max(0f, Math.min(max, targetScroll));
    }

    private void showNotice(String text, boolean error) {
        notice = text;
        noticeError = error;
        noticeUntil = System.currentTimeMillis() + 2600;
    }

    private ScriptManager getManager() {
        Initialization instance = Initialization.getInstance();
        return instance == null || instance.getManager() == null ? null : instance.getManager().getScriptManager();
    }
}
