package mind.beta.screens.clickgui.impl.theme;

import net.minecraft.client.gui.DrawContext;
import mind.beta.screens.clickgui.impl.collection.TabPanel;
import mind.beta.util.render.theme.ClientTheme;
import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.config.ConfigSystem;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;
import mind.beta.util.render.shader.Scissor;

public final class ThemeRenderer {
    private static final float WIDTH = 292f;
    private static final float HEIGHT = 204f;
    private static final float CONTENT_TOP = 22f;
    private static final float CARD_WIDTH = 138f;
    private static final float COLUMN_STEP = 145f;
    private static final float CARD_HEIGHT = 80f;
    private static final float ROW_STEP = 88f;
    private static final long DURATION_NS = 280_000_000L;
    private static final ThemePreview[] THEMES = {
            new ThemePreview("Midnight", "Quiet blue", 0xFF101622, 0xFF182235, 0xFF448BFF),
            new ThemePreview("Ocean", "Deep and clear", 0xFF0C1A22, 0xFF112D39, 0xFF55CBEA),
            new ThemePreview("Rose", "Soft contrast", 0xFF21151E, 0xFF352332, 0xFFEA88BE),
            new ThemePreview("Emerald", "Fresh accent", 0xFF111E1B, 0xFF1C322C, 0xFF6DD7AD),
            new ThemePreview("Ivory", "Clean white", 0xFFF0F3FA, 0xFFFFFFFF, 0xFF597DE0),
            new ThemePreview("Amethyst", "Luminous violet", 0xFF191428, 0xFF302544, 0xFFAF86F6),
            new ThemePreview("Ember", "Warm copper", 0xFF281912, 0xFF40291E, 0xFFFF9567),
            new ThemePreview("Solar", "Golden hour", 0xFF26200F, 0xFF3A301A, 0xFFF3C65C),
            new ThemePreview("Cyber", "Neon lime", 0xFF141E16, 0xFF24382A, 0xFFA3F36A),
            new ThemePreview("Crimson", "Scarlet night", 0xFF25131B, 0xFF3C202B, 0xFFFA667C),
            new ThemePreview("Aurora", "Northern lights", 0xFF0E2626, 0xFF153E3B, 0xFF4CE1C7),
            new ThemePreview("Nebula", "Cosmic purple", 0xFF18152C, 0xFF2C2549, 0xFF8F70FF),
            new ThemePreview("Coral", "Vivid sunset", 0xFF2D1A22, 0xFF49303A, 0xFFFF7C8F),
            new ThemePreview("Lagoon", "Tropical blue", 0xFF0D2027, 0xFF17404A, 0xFF36B5C7),
            new ThemePreview("Blossom", "Pastel petals", 0xFF2C1E2A, 0xFF46344A, 0xFFFFB1D0),
            new ThemePreview("Volcanic", "Magma glow", 0xFF2B1815, 0xFF49271F, 0xFFFF6449),
            new ThemePreview("Forest", "Deep woodland", 0xFF13251B, 0xFF203C2B, 0xFF64C17B),
            new ThemePreview("Glacier", "Cold crystal", 0xFF14232A, 0xFF213D49, 0xFF98DFFD),
            new ThemePreview("Plum", "Royal orchid", 0xFF25192E, 0xFF3D2A48, 0xFFC77DD8),
            new ThemePreview("Sandstone", "Desert dusk", 0xFF292218, 0xFF43372A, 0xFFDDB77D),
            new ThemePreview("Nova", "Stellar gold", 0xFF2B2414, 0xFF44381D, 0xFFFFD266),
            new ThemePreview("Graphite", "Silver shadow", 0xFF1C212A, 0xFF2E3541, 0xFFAAB8CD),
            new ThemePreview("Electric", "Ultraviolet blue", 0xFF12182F, 0xFF1F2B4B, 0xFF5B79FF),
            new ThemePreview("Dusk", "Twilight haze", 0xFF20182A, 0xFF352644, 0xFFB68BE2)
    };

    private final TabPanel scrollbarPanel = new TabPanel();
    private boolean selected;
    private float from;
    private float progress;
    private long transitionStart;
    private float scroll;
    private float targetScroll;
    private long lastFrameTime;

    public void reset(boolean active) {
        scrollbarPanel.reset(active);
        selected = active;
        from = 0f;
        progress = 0f;
        transitionStart = System.nanoTime();
        lastFrameTime = transitionStart;
        scroll = 0f;
        targetScroll = 0f;
    }

    public void setSelected(boolean active) {
        if (selected == active) {
            return;
        }
        update();
        from = progress;
        selected = active;
        scrollbarPanel.setActive(active);
        transitionStart = System.nanoTime();
    }

    private float maxScroll() {
        int rows = (THEMES.length + 1) / 2;
        return Math.max(0f, 24f + (rows - 1) * ROW_STEP + CARD_HEIGHT + 4f - HEIGHT);
    }

    private void update() {
        long now = System.nanoTime();
        float delta = Math.min(0.05f, Math.max(0f, (now - lastFrameTime) / 1_000_000_000f));
        lastFrameTime = now;
        scroll += (targetScroll - scroll) * (1f - (float) Math.exp(-14f * delta));
        if (Math.abs(targetScroll - scroll) < 0.02f) {
            scroll = targetScroll;
        }
        float t = Math.min(1f, Math.max(0f, (now - transitionStart) / (float) DURATION_NS));
        float eased = selected ? 1f - (float) Math.pow(1f - t, 4) : t * t * t;
        progress = from + ((selected ? 1f : 0f) - from) * eased;
    }

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        update();
        scrollbarPanel.frame(bgX, bgY, alphaMultiplier);
        if (progress <= 0.001f || alphaMultiplier <= 0.001f) {
            return;
        }
        float scale = 0.9f + progress * 0.1f;
        float alpha = progress * alphaMultiplier;
        float baseX = bgX + 98f;
        float baseY = bgY + 38f;
        float x = baseX + WIDTH * (1f - scale) / 2f;
        float y = baseY + HEIGHT * (1f - scale) / 2f
                + (selected ? 1f : -1f) * (1f - progress) * 14f;
        Scissor.enable(baseX, baseY, WIDTH, HEIGHT, 2);
        try {
            Fonts.BOLD.draw("Theme collection", x + 4f * scale, y + 2f * scale,
                    8f * scale, color(ThemeColors.isLight() ? 0xFF263347 : 0xFFE8EFFB, alpha));
            Fonts.REGULAR.draw(ThemeColors.selected().displayName(), x + 257f * scale, y + 3f * scale,
                    6f * scale, color(ThemeColors.isLight() ? 0xFF68778F : 0xFF93A2B9, alpha));
            Scissor.enable(baseX, baseY + CONTENT_TOP, WIDTH, HEIGHT - CONTENT_TOP, 2);
            try {
                for (int i = 0; i < THEMES.length; i++) {
                    float cardY = y + (24f + (i / 2) * ROW_STEP - scroll) * scale;
                    if (cardY + CARD_HEIGHT * scale < baseY + CONTENT_TOP || cardY > baseY + HEIGHT) {
                        continue;
                    }
                    renderCard(THEMES[i], x + (i % 2) * COLUMN_STEP * scale,
                            cardY, scale, alpha);
                }
            } finally {
                Scissor.disable();
            }
            scrollbarPanel.scrollBar(new TabPanel.Frame(x, y, scale, alpha), 290f,
                    CONTENT_TOP + 3f, HEIGHT - CONTENT_TOP - 6f,
                    HEIGHT - CONTENT_TOP - 6f + maxScroll(), scroll, false);
        } finally {
            Scissor.disable();
        }
    }

    private void renderCard(ThemePreview theme, float x, float y, float s, float alpha) {
        boolean light = theme.name().equals("Ivory");
        int textPrimary = light ? 0xFF303B50 : 0xFFF0F4FC;
        int textSecondary = light ? 0xFF69768A : 0xFFA3AFC1;
        int details = light ? 0xFF526177 : 0xFFDAE4F4;
        Render2D.rect(x, y, CARD_WIDTH * s, CARD_HEIGHT * s, color(theme.background(), alpha * 0.92f), 6f * s);
        if (theme.name().equals(ThemeColors.selected().displayName())) {
            Render2D.outline(x, y, CARD_WIDTH * s, CARD_HEIGHT * s, 0.8f * s,
                    color(theme.accent(), alpha * 0.85f), 6f * s);
        }
        float px = x + 7f * s;
        float py = y + 7f * s;
        Render2D.rect(px, py, 128f * s, 43f * s, color(theme.panel(), alpha), 4f * s);
        Render2D.rect(px + 5f * s, py + 5f * s, 19f * s, 33f * s,
                color(theme.background(), alpha), 3f * s);
        for (int i = 0; i < 4; i++) {
            Render2D.rect(px + 10f * s, py + (10f + i * 7f) * s, 9f * s, 2f * s,
                    color(i == 0 ? theme.accent() : (light ? 0xFF91A1B8 : 0xFF718095),
                            alpha * (i == 0 ? 1f : 0.55f)), s);
        }
        for (int i = 0; i < 3; i++) {
            float ry = py + (6f + i * 11f) * s;
            Render2D.rect(px + 29f * s, ry, 60f * s, 8f * s,
                    color(theme.background(), alpha * 0.8f), 2f * s);
            Render2D.rect(px + 34f * s, ry + 3f * s, (21f + i * 5f) * s, 2f * s,
                    color(details, alpha * 0.7f), s);
            Render2D.rect(px + 77f * s, ry + 2f * s, 8f * s, 4f * s,
                    color(i == 1 ? (light ? 0xFF9AA8BD : 0xFF68748A) : theme.accent(), alpha), 2f * s);
        }
        Render2D.rect(px + 94f * s, py + 6f * s, 28f * s, 30f * s,
                color(theme.background(), alpha * 0.8f), 3f * s);
        Render2D.rect(px + 99f * s, py + 12f * s, 18f * s, 2f * s,
                color(details, alpha * 0.6f), s);
        Render2D.rect(px + 99f * s, py + 21f * s, 18f * s, 3f * s,
                color(theme.accent(), alpha), 1.5f * s);
        Fonts.MEDIUM.draw(theme.name(), x + 8f * s, y + 56f * s,
                7f * s, color(textPrimary, alpha));
        Fonts.REGULAR.draw(theme.description(), x + 8f * s, y + 67f * s,
                5.5f * s, color(textSecondary, alpha));
        for (int i = 0; i < 3; i++) {
            int swatch = i == 0 ? theme.accent() : i == 1 ? theme.panel() : details;
            Render2D.rect(x + (111f + i * 8f) * s, y + 61f * s, 5f * s, 5f * s,
                    color(swatch, alpha), 2.5f * s);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double vertical, float bgX, float bgY) {
        if (!selected || mouseX < bgX + 98f || mouseX > bgX + 98f + WIDTH
                || mouseY < bgY + 38f + CONTENT_TOP || mouseY > bgY + 38f + HEIGHT) {
            return false;
        }
        targetScroll = Math.max(0f, Math.min(maxScroll(), targetScroll - (float) vertical * 30f));
        scrollbarPanel.scrolled();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float bgX, float bgY) {
        update();
        if (!selected || progress <= 0.001f) {
            return false;
        }
        float baseX = bgX + 98f;
        float baseY = bgY + 38f;
        if (mouseX < baseX || mouseX > baseX + WIDTH || mouseY < baseY || mouseY > baseY + HEIGHT) {
            return false;
        }
        if (button != 0 || mouseY < baseY + CONTENT_TOP) {
            return true;
        }
        float scale = 0.9f + progress * 0.1f;
        float x = baseX + WIDTH * (1f - scale) / 2f;
        float y = baseY + HEIGHT * (1f - scale) / 2f + (1f - progress) * 14f;
        for (int i = 0; i < THEMES.length; i++) {
            float cardX = x + (i % 2) * COLUMN_STEP * scale;
            float cardY = y + (24f + (i / 2) * ROW_STEP - scroll) * scale;
            if (mouseX >= cardX && mouseX <= cardX + CARD_WIDTH * scale
                    && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT * scale) {
                ClientTheme theme = ClientTheme.fromName(THEMES[i].name());
                if (ThemeColors.selected() != theme) {
                    ThemeColors.select(theme);
                    ConfigSystem config = ConfigSystem.getInstance();
                    if (config != null) {
                        config.save();
                    }
                }
                return true;
            }
        }
        return true;
    }

    private int color(int rgb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(255f * alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    private record ThemePreview(String name, String description, int background, int panel, int accent) {
    }
}
