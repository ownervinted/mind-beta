package mind.beta.screens.clickgui.impl.rotation;

import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.util.render.pipeline.BlurPipeline;
import mind.beta.util.render.shader.Scissor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class RotationConfigPopup {
    public static final RotationConfigPopup INSTANCE = new RotationConfigPopup();
    private static final float W = 146f, H = 132f;
    private final RotationBuilder builder = RotationBuilder.INSTANCE;
    private boolean open;
    private static final BlurPipeline BLUR = new BlurPipeline();
    private boolean editing;
    private String name = "";
    private String selected = "";
    private int offset;
    private float smoothOffset;
    private float selectionHighlight;
    private String highlighted = "";
    private final float[] buttonFlash = new float[4];
    private final float[] buttonHover = new float[4];
    private double lastMouseX, lastMouseY;
    private float x, y;
    private float appearance;
    private Runnable onLoad;
    private float nameOpacity = 1f;
    private boolean clearingName;
    private float cursorClock;
    private float duplicateClock = -1f;
    private long lastConfigClickNanos;
    private String lastClickedConfig = "";
    private String rowFeedback = "";
    private float rowFeedbackTime;
    private static final float LIST_TOP = 38f;
    private static final float LIST_BOTTOM = 76f;

    public void toggle(float anchorX, float anchorY, Runnable onLoad) {
        if (open) { close(); return; }
        this.x = anchorX - W;
        this.y = anchorY + 3f;
        this.onLoad = onLoad;
        this.selected = builder.savedConfig == null ? "" : builder.savedConfig;
        this.name = "";
        this.nameOpacity = 1f;
        this.clearingName = false;
        this.duplicateClock = -1f;
        this.lastConfigClickNanos = 0L;
        this.lastClickedConfig = "";
        this.rowFeedback = "";
        this.rowFeedbackTime = 0f;
        this.offset = 0;
        this.smoothOffset = 0f;
        this.highlighted = this.selected;
        this.selectionHighlight = 0f;
        java.util.Arrays.fill(buttonFlash, 0f);
        this.editing = false;
        this.open = true;
    }

    public void close() { open = false; editing = false; }
    public boolean isOpen() { return open; }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float target = open ? 1f : 0f;
        float dt = Math.max(.1f, delta);
        cursorClock += dt;
        rowFeedbackTime *= (float) Math.exp(-dt * .42f);
        if (rowFeedbackTime < .003f) rowFeedbackTime = 0f;
        if (duplicateClock >= 0f) {
            duplicateClock += dt;
            if (duplicateClock > 22f) duplicateClock = -1f;
        }
        if (clearingName) {
            nameOpacity = Math.max(0f, nameOpacity - dt * .19f);
            if (nameOpacity <= 0f) {
                name = "";
                clearingName = false;
                nameOpacity = 1f;
                editing = false;
            }
        }
        float speed = 1f - (float) Math.exp(-dt * .65f);
        appearance += (target - appearance) * speed;
        if (Math.abs(target - appearance) < .003f) appearance = target;
        if (appearance <= .002f) return;

        float scale = appearance * appearance * (3f - 2f * appearance);
        float left = x + W * (1f - scale) * .5f;
        float top = y + H * (1f - scale) * .5f;
        float width = W * scale;
        float height = H * scale;
        if (width < 1f || height < 1f) return;

        int themeAccent = ThemeColors.selected().accent() & 0xFFFFFF;
        int accentR = (themeAccent >>> 16) & 255;
        int accentG = (themeAccent >>> 8) & 255;
        int accentB = themeAccent & 255;
        int popupTint;
        if (ThemeColors.isLight()) {
            popupTint = (218 << 24) | (244 << 16) | (245 << 8) | 247;
        } else {
            popupTint = (173 << 24)
                    | ((accentR * 19 / 100) << 16)
                    | ((accentG * 19 / 100) << 8)
                    | (accentB * 19 / 100);
        }
        BLUR.drawBlur(left, top, width, height, 10f,
                new float[]{6f * scale, 6f * scale, 6f * scale, 6f * scale},
                popupTint);

        int title = ThemeColors.isLight() ? 0xF0202736 : ThemeColors.color(235, 238, 248, 240).getRGB();
        int secondary = ThemeColors.isLight() ? 0xD75F6879 : ThemeColors.color(174, 185, 210, 220).getRGB();
        int normal = ThemeColors.isLight() ? 0xEB303A48 : ThemeColors.color(225, 233, 245, 235).getRGB();
        float lerp = 1f - (float) Math.exp(-dt * .32f);
        int maxOffset = Math.max(0, builder.configs().size() - 3);
        offset = Math.max(0, Math.min(maxOffset, offset));
        smoothOffset += (offset - smoothOffset) * lerp;
        if (Math.abs(offset - smoothOffset) < .003f) smoothOffset = offset;
        if (!selected.equals(highlighted)) {
            highlighted = selected;
            selectionHighlight = 0f;
        }
        selectionHighlight += (1f - selectionHighlight) * (1f - (float) Math.exp(-dt * .30f));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Fonts.BOLD.draw("Rotation Configs", left + 9f * scale, top + 9f * scale, 7f * scale, title);
        Render2D.rect(left + 9f * scale, top + 23f * scale, (W - 18f) * scale,
                .6f * scale, ThemeColors.isLight() ? 0x357C8490 : ThemeColors.color(120, 135, 162, Math.round(75f * appearance)).getRGB());
        Fonts.MEDIUM.draw("Current Config", left + 9f * scale, top + 29f * scale, 6f * scale, secondary);
        List<String> list = builder.configs();
        float rowHeight = 12f;
        float rowTop = top + LIST_TOP * scale;
        float rowBottom = top + LIST_BOTTOM * scale;
        Scissor.enable(left + 6f * scale, rowTop,
                (W - 12f) * scale, rowBottom - rowTop, 2f);
        int first = Math.max(0, (int)Math.floor(smoothOffset) - 1);
        int last = Math.min(list.size(), (int)Math.ceil(smoothOffset) + 5);
        for (int n = first; n < last; n++) {
            String entry = list.get(n);
            float yy = top + (40f + 12f * (n - smoothOffset)) * scale;
            if (entry.equals(selected)) {
                int opacity = Math.round(92f * selectionHighlight);
                Render2D.rect(left + 7f * scale, yy - 2f * scale,
                        (W - 14f) * scale, 11f * scale,
                        ThemeColors.isLight() ? ((opacity << 24) | 0xB8C0CA) : ((opacity << 24) | themeAccent), 3f * scale);
            }
            float feedback = entry.equals(rowFeedback) ? Math.min(1f, rowFeedbackTime) : 0f;
            float rowScale = 1f - .065f * feedback;
            if (feedback > .001f) {
                Render2D.rect(left + 7f * scale, yy - 2f * scale,
                        (W - 14f) * scale, 11f * scale,
                        ThemeColors.color(145, 180, 240, Math.round(115f * feedback)).getRGB(), 3f * scale);
            }
            float baseFont = 7f * scale;
            float fontSize = baseFont * rowScale;
            String clippedName = fitConfigName(entry, (W - 27f) * scale, baseFont);
            float textX = left + 12f * scale;
            float textY = yy - .4f * scale + (baseFont - fontSize) * .5f;
            Fonts.REGULAR.draw(clippedName, textX, textY, fontSize, normal);
        }
        Scissor.disable();
        if (list.size() > 3) {
            float trackY = top + 39f * scale;
            float trackHeight = 36f * scale;
            float thumbHeight = Math.max(7f * scale, trackHeight * 3f / list.size());
            float progress = maxOffset == 0 ? 0f : smoothOffset / maxOffset;
            Render2D.rect(left + (W - 6f) * scale, trackY + (trackHeight - thumbHeight) * progress,
                    1.5f * scale, thumbHeight, ThemeColors.isLight() ? 0x907C8490 : ThemeColors.color(170, 190, 225, 145).getRGB(), .75f * scale);
        }
        Fonts.MEDIUM.draw("Config Name", left + 9f * scale, top + 79f * scale, 5.8f * scale, secondary);
        Render2D.rect(left + 9f * scale, top + 88f * scale, (W - 18f) * scale,
                15f * scale, ThemeColors.isLight() ? 0xDDF0F1F4 : ThemeColors.color(40, 44, 57, Math.round(120f * appearance)).getRGB(), 3f * scale);
        float flash = 0f;
        if (duplicateClock >= 0f) {
            float progress = Math.min(1f, duplicateClock / 22f);
            float envelope = (float) Math.pow(Math.sin(Math.PI * progress), .55);
            float pulses = (float) Math.pow(Math.sin(2f * Math.PI * progress), 2f);
            flash = envelope * pulses;
        }
        int fieldText = ThemeColors.isLight()
                ? ((Math.round(240f * nameOpacity) << 24) | (flash > .01f ? 0xC13939 : 0x202736))
                : ThemeColors.color(235 + Math.round(20f * flash),
                238 - Math.round(170f * flash), 248 - Math.round(180f * flash),
                Math.round(240f * nameOpacity)).getRGB();
        float fieldFontSize = 7f * scale;
        String visibleName = fitConfigName(name, (W - 29f) * scale, fieldFontSize);
        float nameX = left + 13f * scale;
        float nameY = top + 92f * scale;
        Fonts.REGULAR.draw(visibleName, nameX, nameY, fieldFontSize, fieldText);
        if (editing && !clearingName) {
            float cursorAlpha = .18f + .82f * (.5f + .5f * (float) Math.cos(cursorClock * .26f));
            float cursorX = nameX + Fonts.REGULAR.getWidth(visibleName, fieldFontSize) + 1f * scale;
            Render2D.rect(cursorX, nameY - .5f * scale, .65f * scale, 7f * scale,
                    ThemeColors.isLight() ? ((Math.round(235f * cursorAlpha) << 24) | 0x202736)
                            : ThemeColors.color(235, 238, 248, Math.round(235f * cursorAlpha)).getRGB());
        }

        String[] actions = {"Save", "Load", "Update", "Delete"};
        for (int i = 0; i < actions.length; i++) {
            float bx = left + (9f + i * 33f) * scale;
            float by = top + 110f * scale;
            float bw = 30f * scale;
            float bh = 15f * scale;
            boolean hovered = lastMouseX >= bx && lastMouseX <= bx + bw
                    && lastMouseY >= by && lastMouseY <= by + bh;
            float hoverTarget = hovered ? 1f : 0f;
            buttonHover[i] += (hoverTarget - buttonHover[i]) * (1f - (float)Math.exp(-dt * .30f));
            buttonFlash[i] *= (float)Math.exp(-dt * .42f);
            int base = ThemeColors.isLight() ? 0xDDE3E5EA : ThemeColors.color(44, 54, 72, 120).getRGB();
            Render2D.rect(bx, by, bw, bh, base, 3f * scale);
            float overlay = Math.min(1f, buttonHover[i] * .35f + buttonFlash[i] * .65f);
            if (overlay > .005f) {
                Render2D.rect(bx, by, bw, bh,
                        ThemeColors.isLight()
                                ? ((Math.round(75f * overlay) << 24) | 0xAAB1BD)
                                : ((Math.round(125f * overlay) << 24) | themeAccent), 3f * scale);
            }
            float fontSize = 6.5f * scale * (1f - .065f * buttonFlash[i]);
            String label = actions[i];
            float tx = bx + (bw - Fonts.BOLD.getWidth(label, fontSize)) * .5f;
            float ty = by + (bh - fontSize) * .5f - .4f * scale;
            Fonts.BOLD.draw(label, tx, ty, fontSize, title);
        }
    }

    private String fitConfigName(String value, float maxWidth, float fontSize) {
        if (Fonts.REGULAR.getWidth(value, fontSize) <= maxWidth) return value;
        String suffix = "..";
        int end = value.length();
        while (end > 0 && Fonts.REGULAR.getWidth(value.substring(0, end) + suffix, fontSize) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    private String shortName(String s, int max) { return s.length() > max ? s.substring(0, max - 2) + ".." : s; }
    private boolean inside(double mx, double my) { return mx >= x && mx <= x + W && my >= y && my <= y + H; }

    public boolean click(double mx, double my, int button) {
        if (!open) return false;
        if (!inside(mx, my)) { close(); return true; }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (my >= y + 40f && my < y + 76f) {
            int index = (int)Math.floor(smoothOffset + (my - y - 40f) / 12f);
            List<String> names = builder.configs();
            if (index >= 0 && index < names.size()) {
                String clicked = names.get(index);
                long now = System.nanoTime();
                boolean doubleClick = clicked.equals(lastClickedConfig)
                        && now - lastConfigClickNanos <= 350_000_000L;
                if (!clicked.equals(selected)) {
                    selected = clicked;
                    selectionHighlight = 0f;
                    highlighted = selected;
                }
                lastClickedConfig = clicked;
                lastConfigClickNanos = now;
                if (doubleClick) {
                    rowFeedback = clicked;
                    rowFeedbackTime = 1f;
                    loadSelectedConfig();
                    lastConfigClickNanos = 0L;
                }
            }
        } else if (my >= y + 88f && my <= y + 104f) {
            editing = true;
        } else if (my >= y + 110f && my <= y + 126f) {
            int index = (int)((mx - x - 9f) / 33f);
            if (index >= 0 && index < 4 && mx >= x + 9f + index * 33f && mx <= x + 39f + index * 33f) {
                buttonFlash[index] = 1f;
            }
            if (index == 0) {
                createConfig();
            } else if (index == 1) {
                loadSelectedConfig();
            } else if (index == 2) {
                builder.updatePreset(selected);
            } else if (index == 3) {
                if (builder.deletePreset(selected)) selected = "";
            }
        }
        return true;
    }

    private void createConfig() {
        if (clearingName || name.isBlank()) return;
        String result = builder.savePreset(name);
        if (!result.isEmpty()) {
            selected = result;
            clearingName = true;
            nameOpacity = 1f;
            duplicateClock = -1f;
        } else if (builder.configs().stream().anyMatch(existing -> existing.equalsIgnoreCase(name.trim()))) {
            duplicateClock = 0f;
        }
    }

    private void loadSelectedConfig() {
        if (builder.loadPreset(selected)) {
            if (onLoad != null) onLoad.run();
        }
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!open || !inside(mx, my)) return false;
        int max = Math.max(0, builder.configs().size() - 3);
        offset = Math.max(0, Math.min(max, offset + (amount > 0 ? -1 : 1)));
        return true;
    }

    public boolean key(int key) {
        if (!open) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (editing && key == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) name = name.substring(0, name.length() - 1);
        if (editing && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            buttonFlash[0] = 1f;
            createConfig();
        }
        return true;
    }

    public boolean character(char c) {
        if (!open) return false;
        if (editing && !clearingName && name.length() < 48 && c >= 32 && "\\/:*?\"<>|".indexOf(c) < 0) name += c;
        return true;
    }
}
