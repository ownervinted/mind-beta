package mind.beta.screens.clickgui.impl.configs;

import net.minecraft.client.gui.DrawContext;
import mind.beta.util.render.theme.ThemeColors;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.screens.clickgui.impl.collection.TabPanel;
import mind.beta.screens.clickgui.impl.collection.TabPanel.Frame;
import mind.beta.screens.clickgui.impl.configs.handler.ConfigAnimationHandler;
import mind.beta.screens.clickgui.impl.configs.handler.ConfigDataHandler;
import mind.beta.screens.clickgui.impl.configs.handler.ConfigDetailsStore.Details;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.pipeline.BlurPipeline;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.shader.Scissor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class ConfigsRenderer {
    public static final float PANEL_X_OFFSET = 98f;
    public static final float PANEL_Y_OFFSET = 38f;
    public static final float PANEL_WIDTH = 292f;
    public static final float PANEL_HEIGHT = 204f;
    public static final float CORNER_RADIUS = 6f;
    private static final float LIST_TOP = 40f;
    private static final float LIST_HEIGHT = 164f;
    private static final float ROW_HEIGHT = 56f;
    private static final float ROW_STEP = 63f;
    private static final BlurPipeline POPUP_BLUR = new BlurPipeline();
    private static final float POPUP_WIDTH = 142f;
    private static final float ACTION_PILL_WIDTH = 110f;
    private static float popupHeight(int mode) { return mode == 1 ? 131f : mode == 3 ? 54f : 25f; }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final TabPanel panel = new TabPanel();
    private final ConfigDataHandler data = new ConfigDataHandler(new ConfigAnimationHandler());
    private final List<String> visible = new ArrayList<>();
    private boolean active;
    private boolean recent = true;
    private String description = "";
    private String tag = "";
    private final List<String> tags = new ArrayList<>();
    private final List<TagChip> tagChips = new ArrayList<>();
    private int nextChipId;

    private static final class TagChip {
        private final String value;
        private final int id;
        private boolean removing;

        private TagChip(String value, int id) {
            this.value = value;
            this.id = id;
        }
    }
    private String author = "";
    private String previewName;
    private int field;
    private int mode;
    private int paintedMode;
    private String paintedName;
    private float contentAlpha = 1f;
    private float overlay;
    private float animatedPopupWidth = ACTION_PILL_WIDTH;
    private float animatedPopupHeight = 25f;
    private float popupAnchorX = 146f;
    private float popupAnchorY = 80f;
    private float scroll;
    private float targetScroll;
    private String message = "";
    private boolean error;
    private long messageUntil;
    private long nextRefresh;

    public void reset(boolean active) {
        this.active = active;
        panel.reset(active);
        data.setCreating(false);
        mode = paintedMode = 0;
        overlay = 0f;
        animatedPopupWidth = ACTION_PILL_WIDTH;
        animatedPopupHeight = 25f;
        if (active) {
            refresh();
        }
    }

    public void setSelected(boolean selected) {
        panel.setActive(selected);
        if (selected && !active) {
            refresh();
        }
        if (!selected && active) {
            closeModal();
        }
        active = selected;
    }

    private void refresh() {
        data.refreshConfigs();
        nextRefresh = System.nanoTime() + 1_500_000_000L;
        rebuild();
    }

    private void rebuild() {
        visible.clear();
        visible.addAll(data.getConfigs());
        if (recent) {
            visible.sort(Comparator.<String>comparingLong(name -> data.getDetails(name).modified()).reversed()
                    .thenComparing(String.CASE_INSENSITIVE_ORDER));
        } else {
            visible.sort(String.CASE_INSENSITIVE_ORDER);
        }
        targetScroll = Math.max(0f, Math.min(maxScroll(), targetScroll));
    }

    private float maxScroll() {
        return Math.max(0f, visible.size() * ROW_STEP - 7f - LIST_HEIGHT);
    }

    public void render(DrawContext context, float bgX, float bgY, float mouseX, float mouseY,
                       float delta, int guiScale, float alphaMultiplier, ModuleCategory category) {
        setSelected(category == ModuleCategory.CONFIG);
        Frame base = panel.frame(bgX, bgY, alphaMultiplier);
        if (base.alpha() < 0.001f) {
            return;
        }
        if (active && System.nanoTime() > nextRefresh) {
            refresh();
        }
        boolean changing = mode != 0 && (paintedMode != mode || !Objects.equals(paintedName, previewName));
        contentAlpha = panel.animate("side-content", changing ? 0f : 1f, contentAlpha, 24f);
        if (changing && (contentAlpha < 0.04f || overlay < 0.04f)) {
            paintedMode = mode;
            paintedName = previewName;
        }
        overlay = panel.animate("modal", mode == 0 ? 0f : 1f, overlay, 38f);
        int sizingMode = mode == 0 ? paintedMode : mode;
        float targetWidth = sizingMode == 2 ? ACTION_PILL_WIDTH : POPUP_WIDTH;
        float targetHeight = popupHeight(sizingMode);
        float sizeStep = Math.min(1f, Math.max(0.08f, delta * 0.025f));
        animatedPopupWidth += (targetWidth - animatedPopupWidth) * sizeStep;
        animatedPopupHeight += (targetHeight - animatedPopupHeight) * sizeStep;
        if (Math.abs(animatedPopupWidth - targetWidth) < 0.1f) animatedPopupWidth = targetWidth;
        if (Math.abs(animatedPopupHeight - targetHeight) < 0.1f) animatedPopupHeight = targetHeight;
        scroll = Math.max(0f, Math.min(maxScroll(), panel.animate("scroll", targetScroll, scroll, 22f)));
        Frame f = base;
        double mx = f.localX(mouseX);
        double my = f.localY(mouseY);
        f.bold("Config", 2f, 4f, 11f, f.color(235, 243, 255, 248));
        f.regular("Your saved setups", 3f, 23f, 6.5f, f.color(165, 186, 218, 220));
        sideButton(f, "sort", recent ? "Recent" : "A-Z", 170f, 4f, 60f, 21f, true, mx, my);
        sideButton(f, "new", "+ New", 238f, 4f, 52f, 21f, true, mx, my);
        renderCards(f, mx, my, guiScale);
        if (overlay > 0.001f) {
            Frame modal = modalFrame(base);
            double modalX = mode == 0 ? -1000 : modal.localX(mouseX);
            double modalY = mode == 0 ? -1000 : modal.localY(mouseY);
            renderModal(modal, modalX, modalY);
        }
    }

    private void renderCards(Frame f, double mx, double my, int guiScale) {
        Scissor.enable(f.x(), f.y() + LIST_TOP * f.scale(), 292f * f.scale(), LIST_HEIGHT * f.scale(), guiScale);
        try {
            panel.retainItems(new HashSet<>(data.getConfigs()));
            int index = 0;
            for (int i = 0; i < visible.size(); i++) {
                String name = visible.get(i);
                float position = panel.animate("row-y:" + name, i * ROW_STEP, i * ROW_STEP, 24f);
                float y = LIST_TOP + position - scroll;
                if (y + ROW_HEIGHT < LIST_TOP || y > 204f) {
                    continue;
                }
                float entry = panel.entry(name, index++);
                float offset = 7f * (1f - entry);
                Frame row = f.shift(0f, offset).opacity(entry);
                Details info = data.getDetails(name);
                float selected = panel.animate("selected:" + name,
                        Objects.equals(name, data.getSelectedConfig()) ? 1f : 0f, 0f, 22f);
                float hover = panel.hover("row:" + name, my >= LIST_TOP && my <= 204f
                        && TabPanel.inside(mx, my, 2f, y + offset, 284f, ROW_HEIGHT));
                if (ThemeColors.isLight()) {
                    row.rect(2f, y, 284f, ROW_HEIGHT,
                            ivory(row, 0xFFFFFF, Math.round(229f + 17f * hover + 8f * selected)), 6f);
                } else {
                    row.rect(2f, y, 284f, ROW_HEIGHT,
                            row.color(16, 32, 55, 70f + 20f * hover + 34f * selected), 6f);
                }

                String title = TabPanel.trim(name, 112f, 8f);
                float authorX = 10f + Fonts.BOLD.getWidth(title, 8f) + 7f;
                row.bold(title, 10f, y + 7f, 8f, row.color(237, 244, 255, 250));
                row.regular(TabPanel.trim("by " + info.author(), Math.max(0f, 229f - authorX), 6.5f),
                        authorX, y + 8.5f, 6.5f, row.color(159, 185, 219, 225));
                row.regular(TabPanel.trim(info.description(), 242f, 6.4f), 10f, y + 22f, 6.4f,
                        row.color(171, 191, 221, 225));
                chips(row, info.tag(), 10f, y + 36f, 257f);

                boolean loaded = Objects.equals(name, data.getActiveConfig());
                float loadHover = panel.hover("load:" + name,
                        TabPanel.inside(mx, my - offset, 233f, y + 15f, 24f, 20f));
                float progress = Math.max(0f, Math.min(1f, panel.animate("loaded:" + name,
                        loaded ? 1f : 0f, loaded ? 1f : 0f, 22f)));
                if (ThemeColors.isLight()) {
                    row.rect(235f, y + 20f, 20f, 10f,
                            ThemeColors.switchTrack(progress, loadHover,
                                    Math.round((208f + 47f * progress) * row.alpha())), 5f);
                    row.rect(236f + 10f * progress, y + 21f, 8f, 8f,
                            ThemeColors.switchKnob(progress, Math.round(255f * row.alpha())), 4f);
                } else {
                    row.rect(235f, y + 20f, 20f, 10f,
                            row.color((int) (7 - 7 * progress), (int) (24 + 104 * progress),
                                    (int) (41 + 214 * progress), 85f + 35f * loadHover + 45f * progress), 5f);
                    row.rect(236f + 10f * progress, y + 21f, 8f, 8f,
                            row.color((int) (111 + 121 * progress), (int) (137 + 106 * progress),
                                    (int) (161 + 94 * progress), 175f + 80f * progress), 4f);
                }
                float dots = panel.hover("details:" + name,
                        TabPanel.inside(mx, my - offset, 264f, y + 15f, 16f, 20f));
                for (int dot = 0; dot < 3; dot++) {
                    row.rect(271f, y + 20f + dot * 4f, 1.7f, 1.7f,
                            row.color(191, 213, 243, 170f + dots * 70f), 0.85f);
                }
            }
        } finally {
            Scissor.disable();
        }
        panel.scrollBar(f, 290f, LIST_TOP + 3f, LIST_HEIGHT - 6f,
                Math.max(0f, visible.size() * ROW_STEP - 13f), scroll,
                TabPanel.inside(mx, my, 0f, LIST_TOP, 292f, LIST_HEIGHT));
    }

    private void chips(Frame f, String values, float x, float y, float maxWidth) {
        List<String> labels = new ArrayList<>();
        for (String value : values.split(",")) {
            String label = value.trim();
            if (!label.isEmpty()) {
                labels.add(label);
            }
        }
        if (labels.isEmpty()) {
            labels.add("Custom");
        }
        float gap = 4f;
        float available = Math.max(0f, maxWidth - gap * (labels.size() - 1));
        float[] widths = new float[labels.size()];
        float total = 0f;
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = Math.min(92f, Fonts.BOLD.getWidth(labels.get(i), 6f) + 14f);
            total += widths[i];
        }
        float factor = total > available ? available / total : 1f;
        for (int i = 0; i < labels.size(); i++) {
            float width = widths[i] * factor;
            f.rect(x, y, width, 14f,
                    ThemeColors.isLight() ? ivory(f, 0xDFEAFD, 235) : f.color(51, 90, 145, 80), 3f);
            String label = TabPanel.trim(labels.get(i), Math.max(0f, width - 8f), 6f);
            f.bold(label, x + (width - Fonts.BOLD.getWidth(label, 6f)) / 2f, y + 4f,
                    6f, ThemeColors.isLight() ? ivory(f, 0x4565A5, 245) : f.color(201, 222, 252, 240));
            x += width + gap;
        }
    }

    private Frame modalFrame(Frame f) {
        float grow = Math.max(.001f, overlay);
        float factor = .90f;
        float scale = f.scale() * factor * grow;
        int visibleMode = mode == 0 ? paintedMode : mode;
        float h = animatedPopupHeight;
        float w = animatedPopupWidth;
        float renderedWidth = w * scale;
        float renderedHeight = h * scale;
        float originX = f.x() + popupAnchorX * f.scale();
        float originY = f.y() + popupAnchorY * f.scale();
        float minX = f.x() + 3f * f.scale();
        float maxX = f.x() + (PANEL_WIDTH - 3f) * f.scale() - renderedWidth;
        float minY = f.y() + 3f * f.scale();
        float maxY = f.y() + (PANEL_HEIGHT - 3f) * f.scale() - renderedHeight;
        float px = Math.max(minX, Math.min(maxX, originX));
        float py = Math.max(minY, Math.min(maxY, originY));
        return new Frame(px, py, scale, f.alpha());
    }

    private void renderModal(Frame f, double mx, double my) {
        float h = animatedPopupHeight;
        float scale = f.scale();
        int accent = ThemeColors.selected().accent() & 0xFFFFFF;
        int r = (accent >> 16) & 255;
        int g = (accent >> 8) & 255;
        int b = accent & 255;
        int tint;
        if (ThemeColors.isLight()) {
            tint = ((Math.round(240f * f.alpha()) & 255) << 24) | 0xF5F5F3;
        } else {
            tint = ((Math.round(173f * f.alpha()) & 255) << 24)
                    | ((r * 19 / 100) << 16) | ((g * 19 / 100) << 8) | (b * 19 / 100);
        }
        POPUP_BLUR.drawBlur(f.x(), f.y(), animatedPopupWidth * scale, h * scale, 11f,
                new float[]{6f * scale, 6f * scale, 6f * scale, 6f * scale}, tint);

        f = f.opacity(contentAlpha);
        int primary = ThemeColors.isLight() ? ivory(f, 0x202736, 245) : f.color(236, 241, 255, 245);
        int muted = ThemeColors.isLight() ? ivory(f, 0x5F6879, 215) : f.color(170, 187, 210, 220);
        if (paintedMode != 2) {
            float closeHover = panel.hover("close", TabPanel.inside(mx, my, 123f, 4f, 14f, 14f));
            f.text("×", 126f, 6f, 8f, ThemeColors.isLight()
                    ? ivory(f, 0x424B5B, Math.round(175 + 75 * closeHover))
                    : f.color(210, 225, 247, 175 + 75 * closeHover));
        }

        if (paintedMode == 1) {
            f.bold("New config", 9f, 7f, 8.5f, primary);
            input(f, 0, "Name", data.getNewConfigName(), 9f, 22f, 124f);
            input(f, 1, "Author", author, 9f, 51f, 124f);
            input(f, 3, "Tags (comma-separated)", tag, 9f, 80f, 124f);
            sideButton(f, "create", "Create", 9f, 111f, 124f, 15f,
                    !data.getNewConfigName().isBlank() && !author.isBlank(), mx, my);
        } else if (paintedMode == 3 && paintedName != null) {
            f.bold("Delete config?", 9f, 5f, 8.5f, primary);
            f.regular(TabPanel.trim(paintedName, 124f, 8f), 9f, 18f, 8f, muted);
            sideButton(f, "delete-cancel", "Cancel", 9f, 34f, 58f, 15f, true, mx, my);
            sideButton(f, "delete-confirm", "Delete", 75f, 34f, 58f, 15f, true, mx, my);
        } else if (paintedMode == 2 && paintedName != null) {
            sideButton(f, "preview-save", "Save", 5f, 4f, 47f, 17f, true, mx, my);
            sideButton(f, "preview-delete", "Delete", 58f, 4f, 47f, 17f, true, mx, my);
        }
    }

    private float tagWidth(String text) {
        String label = TabPanel.trim(text, 34f, 6f);
        return Math.min(50f, Fonts.BOLD.getWidth(label, 6f) + 21f);
    }

    private void renderTagEditor(Frame f, double mx, double my) {
        float x = 12f;
        for (int i = 0; i < tagChips.size(); i++) {
            TagChip tagChip = tagChips.get(i);
            float amount = Math.max(0f, Math.min(1f, panel.animate("tag-chip:" + tagChip.id,
                    tagChip.removing ? 0f : 1f, 0f, 17f)));
            if (tagChip.removing && amount < 0.025f) {
                tagChips.remove(i--);
                continue;
            }
            String label = TabPanel.trim(tagChip.value, 34f, 6f);
            float width = tagWidth(tagChip.value);
            float scale = 0.88f + 0.12f * amount;
            float visualWidth = width * scale;
            float cx = x + (width - visualWidth) * 0.5f;
            float cy = 160f + 15f * (1f - scale) * 0.5f;
            Frame chip = f.opacity(amount);
            float hover = panel.hover("tag:" + tagChip.id,
                    !tagChip.removing && TabPanel.inside(mx, my, x, 160f, width, 15f));
            chip.rect(cx, cy, visualWidth, 15f * scale,
                    ThemeColors.isLight() ? ivory(chip, 0xDFEAFD, 235)
                            : chip.color(51, 90, 145, 85f + 55f * hover), 4f);
            float labelSize = 6f * scale;
            float labelWidth = Fonts.BOLD.getWidth(label, labelSize);
            float centerX = cx + (visualWidth - 12f * scale) / 2f;
            chip.bold(label, centerX - labelWidth / 2f, cy + (15f * scale - labelSize) / 2f,
                    labelSize, ThemeColors.isLight() ? ivory(chip, 0x4565A5, 245)
                            : chip.color(201, 222, 252, 240));
            chip.text("×", cx + visualWidth - 10f * scale, cy + (15f * scale - 8f * scale) / 2f,
                    8f * scale, ThemeColors.isLight() ? ivory(chip, 0x5975A8, 240)
                            : chip.color(187, 211, 241, 235));
            x += (width + 4f) * amount;
        }
    }

    private void addTag() {
        String value = tag.trim().replace(",", "");
        if (!value.isEmpty() && tags.size() < 3 && value.length() <= 24
                && tags.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
            tags.add(value);
            tagChips.add(new TagChip(value, nextChipId++));
            tag = "";
        }
    }

    private void sideButton(Frame f, String key, String label, float x, float y, float width,
                            boolean enabled, double mx, double my) {
        sideButton(f, key, label, x, y, width, 20f, enabled, mx, my);
    }

    private void sideButton(Frame f, String key, String label, float x, float y, float width, float height,
                            boolean enabled, double mx, double my) {
        float hover = panel.hover(key, TabPanel.inside(mx, my, x, y, width, height));
        float pressed = panel.pressAmount(key);
        boolean destructive = key.equals("delete-confirm");
        int background = destructive
                ? (ThemeColors.isLight() ? ivory(f, 0xEFDBDB, Math.round(225f + 25f * hover))
                : f.color(178, 42, 52, 140f + 65f * hover))
                : (ThemeColors.isLight() ? ivory(f, 0xE8E8E6, Math.round(220f + 30f * hover))
                : f.color(46, 93, 155, 45f + hover * 45f));
        f.rect(x, y + pressed * 0.5f, width, height, background, 4f);
        int textColor = destructive
                ? (ThemeColors.isLight() ? ivory(f, 0xBA3039, enabled ? 255 : 110)
                : f.color(255, 224, 226, enabled ? 255 : 110))
                : (ThemeColors.isLight() ? ivory(f, 0x303236, enabled ? 245 : 110)
                : f.color(215, 234, 255, enabled ? 245 : 110));
        f.text(label, x + (width - Fonts.MEDIUM.getWidth(label, 8f)) / 2f,
                y + (height - 8f) / 2f + pressed * 0.5f, 8f, textColor);
    }

    private void input(Frame f, int index, String label, String value, float x, float y, float width) {
        f.regular(label, x, y, 6.5f, ThemeColors.isLight() ? ivory(f, 0x59657A, 220) : f.color(159, 185, 219, 235));
        float focus = panel.animate("field:" + index, field == index && mode == 1 ? 1f : 0f, 0f, 22f);
        f.rect(x, y + 9f, width, 17f,
                ThemeColors.isLight() ? ivory(f, 0xFFFFFF, 255)
                        : f.color(4, 13, 26, 95f + focus * 25f), 3f);
        if (ThemeColors.isLight()) {
            Render2D.outline(f.x() + x * f.scale(), f.y() + (y + 9f) * f.scale(),
                    width * f.scale(), 17f * f.scale(), 0.5f * f.scale(),
                    ivory(f, 0xC7C8C9, Math.round(150f + focus * 95f)), 3f * f.scale());
        }
        Fonts.MEDIUM.draw(TabPanel.trim(value, width - 12f, 8f),
                f.x() + (x + 6f) * f.scale(), f.y() + (y + 13f) * f.scale(),
                7f * f.scale(), ThemeColors.isLight() ? ivory(f, 0x303236, 245) : f.color(216, 234, 255, 245));
        float caret = panel.animate("caret:" + index, Math.min(x + width - 6f,
                x + 6f + Fonts.MEDIUM.getWidth(value, 7f)), x + 6f, 30f);
        f.rect(caret, y + 12f, 0.6f, 10f, ThemeColors.isLight()
                ? ivory(f, 0x414348, Math.round(focus * blink() * 220f))
                : f.color(217, 236, 255, focus * blink() * 220f), 0f);
    }

    private int ivory(Frame f, int rgb, int alpha) {
        int opacity = Math.max(0, Math.min(255, Math.round(alpha * f.alpha())));
        return (opacity << 24) | (rgb & 0xFFFFFF);
    }

    private float blink() {
        return 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 350_000_000.0);
    }

    private String date(long timestamp) {
        return timestamp <= 0 ? "Unknown" : DATE.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float bgX, float bgY, ModuleCategory category) {
        if (category != ModuleCategory.CONFIG || !active) {
            return false;
        }
        Frame f = panel.snapshot(bgX, bgY, 1f);
        double mx = f.localX(mouseX), my = f.localY(mouseY);
        if (!panel.isActive()) {
            return false;
        }
        Frame side = modalFrame(f);
        double sx = side.localX(mouseX), sy = side.localY(mouseY);
        if (overlay > 0.15f) {
            float modalHeight = animatedPopupHeight;
            if (TabPanel.inside(sx, sy, 0f, 0f, animatedPopupWidth, modalHeight)) {
                return button == 0 ? modalClick(sx, sy) : true;
            }
            if (button == 0 && mode != 0) closeModal();
            return true;
        }
        if (!TabPanel.inside(mx, my, 0f, 0f, 292f, 204f)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        if (TabPanel.inside(mx, my, 238f, 4f, 52f, 21f)) {
            data.clearNewConfigName();
            description = "";
            tag = "";
            tags.clear();
            tagChips.clear();
            author = "";
            field = 0;
            mode = 1;
            paintedMode = 1;
            paintedName = previewName;
            animatedPopupWidth = POPUP_WIDTH;
            animatedPopupHeight = popupHeight(1);
            popupAnchorX = (float) mx;
            popupAnchorY = (float) my;
            data.setCreating(true);
            panel.press("new");
            return true;
        }
        if (TabPanel.inside(mx, my, 170f, 4f, 60f, 21f)) {
            recent = !recent;
            rebuild();
            panel.press("sort");
        } else if (my >= LIST_TOP && my <= 204f) {
            for (int i = 0; i < visible.size(); i++) {
                String name = visible.get(i);
                float entry = panel.entryValue(name);
                float y = LIST_TOP + panel.value("row-y:" + name, i * ROW_STEP) - scroll + 7f * (1f - entry);
                if (entry < 0.15f || !TabPanel.inside(mx, my, 2f, y, 284f, ROW_HEIGHT)) {
                    continue;
                }
                data.setSelectedConfig(name);
                if (TabPanel.inside(mx, my, 264f, y + 15f, 16f, 20f)) {
                    previewName = name;
                    mode = 2;
                    paintedMode = 2;
                    paintedName = previewName;
                    animatedPopupWidth = ACTION_PILL_WIDTH;
                    animatedPopupHeight = popupHeight(2);
                    popupAnchorX = (float) mx;
                    popupAnchorY = (float) my;
                    data.setCreating(false);
                    panel.press("details:" + name);
                } else {
                    load(name);
                    panel.press("load:" + name);
                }
                break;
            }
        }
        return true;
    }

    private boolean modalClick(double mx, double my) {
        if (mode == 0 || overlay < 0.4f || contentAlpha < 0.85f
                || paintedMode != mode || !Objects.equals(paintedName, previewName)) return true;
        if (mode != 2 && TabPanel.inside(mx, my, 123f, 4f, 14f, 14f)) {
            panel.press("close");
            closeModal();
        } else if (mode == 1) {
            if (TabPanel.inside(mx, my, 9f, 111f, 124f, 15f)) {
                panel.press("create");
                create();
            } else if (TabPanel.inside(mx, my, 9f, 31f, 124f, 17f)) {
                field = 0;
            } else if (TabPanel.inside(mx, my, 9f, 60f, 124f, 17f)) {
                field = 1;
            } else if (TabPanel.inside(mx, my, 9f, 89f, 124f, 17f)) {
                field = 3;
            }
        } else if (mode == 3 && previewName != null) {
            if (TabPanel.inside(mx, my, 9f, 34f, 58f, 15f)) {
                mode = 2;
                panel.press("delete-cancel");
            } else if (TabPanel.inside(mx, my, 75f, 34f, 58f, 15f)) {
                boolean deleted = data.deleteConfig(previewName);
                notify(deleted ? "Deleted " + previewName : data.getLastError(), !deleted);
                if (deleted) { closeModal(); rebuild(); }
                panel.press("delete-confirm");
            }
        } else if (mode == 2 && previewName != null) {
            if (TabPanel.inside(mx, my, 5f, 4f, 47f, 17f)) {
                panel.press("preview-save");
                boolean saved = data.refreshConfig(previewName);
                notify(saved ? "Saved " + previewName : data.getLastError(), !saved);
                rebuild();
            } else if (TabPanel.inside(mx, my, 58f, 4f, 47f, 17f)) {
                mode = 3;
                panel.press("preview-delete");
            }
        }
        return true;
    }

    private void create() {
        if (author.isBlank()) {
            notify("Author is required", true);
            field = 1;
            return;
        }
        if (data.getNewConfigName().isBlank()) {
            notify("Config name is required", true);
            field = 0;
            return;
        }
        if (!tag.isBlank()) {
            for (String item : tag.split(",")) {
                String candidate = item.trim();
                if (!candidate.isEmpty() && tags.size() < 3 && candidate.length() <= 24
                        && tags.stream().noneMatch(existing -> existing.equalsIgnoreCase(candidate))) {
                    tags.add(candidate);
                }
            }
            tag = "";
        }
        String name = data.getNewConfigName();
        if (!data.saveConfig(name)) {
            notify(data.getLastError(), true);
            return;
        }
        boolean detailsSaved = data.updateDetails(name, description.isBlank() ? "Local configuration" : description,
                tags.isEmpty() ? "Custom" : String.join(",", tags), 0, false, author.trim());
        notify(detailsSaved ? "Created " + name : "Config saved; details could not be saved", !detailsSaved);
        closeModal();
        recent = true;
        targetScroll = 0f;
        rebuild();
    }

    private void load(String name) {
        boolean loaded = data.loadConfig(name);
        if (loaded) {
            closeModal();
        }
        if (!loaded) {
            notify(data.getLastError(), true);
        } else {
            messageUntil = 0L;
        }
    }

    private void closeModal() {
        mode = 0;
        data.setCreating(false);
    }

    private void notify(String message, boolean error) {
        this.message = message;
        this.error = error;
        messageUntil = System.currentTimeMillis() + 2800;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double vertical, float bgX, float bgY, ModuleCategory category) {
        if (category != ModuleCategory.CONFIG || !active || mode != 0 || overlay > 0.15f) {
            return false;
        }
        Frame f = panel.snapshot(bgX, bgY, 1f);
        if (!TabPanel.inside(f.localX(mouseX), f.localY(mouseY), 0f, LIST_TOP, 292f, LIST_HEIGHT)) {
            return false;
        }
        targetScroll = Math.max(0f, Math.min(maxScroll(), targetScroll - (float) vertical * 31f));
        panel.scrolled();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active) {
            return false;
        }
        if (mode != 0) {
            if (keyCode == 256) {
                closeModal();
            } else if (mode == 1 && (keyCode == 257 || keyCode == 335)) {
                create();
            } else if (mode == 1 && keyCode == 258) {
                field = field == 0 ? 1 : field == 1 ? 3 : 0;
            } else if (mode == 1 && keyCode == 259) {
                if (field == 0) {
                    data.removeLastChar();
                } else if (field == 1 && !author.isEmpty()) {
                    author = author.substring(0, author.offsetByCodePoints(author.length(), -1));
                } else if (field == 2 && !description.isEmpty()) {
                    description = description.substring(0, description.offsetByCodePoints(description.length(), -1));
                } else if (field == 3 && !tag.isEmpty()) {
                    tag = tag.substring(0, tag.offsetByCodePoints(tag.length(), -1));
                }
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!active || Character.isISOControl(chr)) {
            return false;
        }
        if (mode == 1) {
            if (field == 0) {
                data.appendChar(chr);
            } else if (field == 1 && author.length() < 40) {
                author += chr;
            } else if (field == 2 && description.length() < 100) {
                description += chr;
            } else if (field == 3 && tag.length() < 96) {
                tag += chr;
            }
            return true;
        }
        return mode != 0;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        panel.release();
    }

    public boolean isEditing() {
        return active && mode != 0;
    }
}
