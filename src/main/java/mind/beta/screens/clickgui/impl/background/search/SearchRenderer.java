package mind.beta.screens.clickgui.impl.background.search;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.Color;
import java.util.List;

public class SearchRenderer {

    private final SearchHandler searchHandler;

    private static final float INPUT_WIDTH = 220f;
    private static final float RESULT_WIDTH = 210f;
    private static final float RESULT_SIZE = 7f;
    private static final float RESULT_HEIGHT = 18f;
    private static final float RESULT_GAP = 3f;
    private static final float RESULT_TEXT_LEFT = 9f;
    private static final float RESULT_ICON_RIGHT = 20f;

    private static final float MAX_BLUR = 7f;

    public SearchRenderer(SearchHandler searchHandler) {
        this.searchHandler = searchHandler;
    }

    public void render(
            DrawContext context,
            float bgX,
            float bgY,
            float bgWidth,
            float bgHeight,
            float mouseX,
            float mouseY,
            int guiScale,
            float alphaMultiplier
    ) {
        float searchAlpha = searchHandler.getSearchPanelAlpha();

        if (searchAlpha <= 0.0001f) {
            return;
        }

        float blurProgress = searchAlpha;

        renderGuiBlur(
                bgX,
                bgY,
                bgWidth,
                bgHeight,
                blurProgress
        );

        float alpha =
                searchAlpha
                        * Math.max(
                        alphaMultiplier,
                        0.75f
                );

        renderSearch(
                context,
                bgX,
                bgY,
                bgWidth,
                bgHeight,
                mouseX,
                mouseY,
                alpha
        );
    }

    private void renderGuiBlur(
            float bgX,
            float bgY,
            float bgWidth,
            float bgHeight,
            float blurProgress
    ) {
        float blurStrength = MAX_BLUR * blurProgress;

        if (blurStrength > 0.001f) {
            Render2D.blur(
                    bgX,
                    bgY,
                    bgWidth,
                    bgHeight,
                    blurStrength,
                    8f,
                    ThemeColors.color(
                            0,
                            0,
                            0,
                            100
                    ).getRGB()
            );
        }
    }

    private void renderSearch(
            DrawContext context,
            float bgX,
            float bgY,
            float bgWidth,
            float bgHeight,
            float mouseX,
            float mouseY,
            float alpha
    ) {
        float centerX =
                bgX + bgWidth / 2f;

        float centerY =
                bgY + bgHeight / 2f;

        float titleY =
                centerY - 72f;

        float inputY =
                titleY + 29f;

        float resultY =
                inputY + 30f;

        renderTitle(
                centerX,
                titleY,
                alpha
        );

        renderInput(
                centerX,
                inputY,
                alpha
        );

        float clipLeft =
                Math.max(
                        bgX,
                        centerX - RESULT_WIDTH / 2f
                );

        float clipTop = resultY;
        float clipRight =
                Math.min(
                        bgX + bgWidth,
                        centerX + RESULT_WIDTH / 2f
                );
        float clipBottom = bgY + bgHeight - 1f;

        searchHandler.setSearchViewportHeight(
                Math.max(0f, clipBottom - clipTop)
        );

        context.enableScissor(
                (int) Math.floor(clipLeft),
                (int) Math.floor(clipTop),
                (int) Math.ceil(clipRight),
                (int) Math.ceil(clipBottom)
        );

        renderResults(
                centerX,
                resultY,
                mouseX,
                mouseY,
                clipLeft,
                clipTop,
                clipRight,
                clipBottom,
                alpha
        );

        context.disableScissor();
    }

    private void renderTitle(
            float centerX,
            float y,
            float alpha
    ) {
        String title = "Search";

        float width =
                Fonts.MEDIUM.getWidth(
                        title,
                        11f
                );

        Fonts.MEDIUM.draw(
                title,
                centerX - width / 2f,
                y,
                11f,
                ThemeColors.color(
                        245,
                        245,
                        250,
                        (int) (255f * alpha)
                ).getRGB()
        );
    }

    private void renderInput(
            float centerX,
            float y,
            float alpha
    ) {
        float inputX =
                centerX - INPUT_WIDTH / 2f;

        String text =
                searchHandler.getSearchText();

        if (text.isEmpty()) {
            renderCursor(
                    inputX,
                    y,
                    alpha
            );
        } else {
            renderInputText(
                    inputX,
                    y,
                    alpha
            );
        }

        float focus =
                searchHandler.getSearchFocusAnimation();

        Render2D.rect(
                inputX,
                y + 17f,
                INPUT_WIDTH,
                0.7f,
                ThemeColors.color(
                        155,
                        155,
                        170,
                        (int) (
                                (110f + focus * 100f) * alpha
                        )
                ).getRGB()
        );
    }

    private void renderInputText(
            float x,
            float y,
            float alpha
    ) {
        String text =
                searchHandler.getSearchText();

        if (searchHandler.hasSearchSelection()) {
            int start =
                    searchHandler.getSearchSelectionStart();

            int end =
                    searchHandler.getSearchSelectionEnd();

            String before =
                    text.substring(0, start);

            String selected =
                    text.substring(start, end);

            float selectionX =
                    x + Fonts.MEDIUM.getWidth(
                            before,
                            8f
                    );

            float selectionWidth =
                    Fonts.MEDIUM.getWidth(
                            selected,
                            8f
                    );

            Render2D.rect(
                    selectionX,
                    y - 2f,
                    selectionWidth,
                    13f,
                    ThemeColors.color(
                            120,
                            125,
                            190,
                            (int) (
                                    90f
                                            * searchHandler.getSearchSelectionAnimation()
                                            * alpha
                            )
                    ).getRGB(),
                    2f
            );
        }

        Fonts.MEDIUM.draw(
                text,
                x,
                y,
                8f,
                ThemeColors.color(
                        235,
                        235,
                        242,
                        (int) (255f * alpha)
                ).getRGB()
        );

        if (!searchHandler.hasSearchSelection()) {
            renderCursor(
                    x,
                    y,
                    alpha
            );
        }
    }

    private void renderCursor(
            float x,
            float y,
            float alpha
    ) {
        String text =
                searchHandler.getSearchText();

        int cursor =
                Math.min(
                        searchHandler.getSearchCursorPosition(),
                        text.length()
                );

        String before =
                text.substring(0, cursor);

        float cursorX =
                x + Fonts.MEDIUM.getWidth(
                        before,
                        8f
                );

        float blink =
                (float) (
                        Math.sin(
                                searchHandler.getSearchCursorBlink()
                                        * Math.PI
                                        * 2f
                        ) * 0.5f + 0.5f
                );

        if (blink < 0.25f) {
            return;
        }

        Render2D.rect(
                cursorX,
                y - 1f,
                0.7f,
                12f,
                ThemeColors.color(
                        235,
                        235,
                        245,
                        (int) (
                                255f * blink * alpha
                        )
                ).getRGB()
        );
    }

    private void renderResults(
            float centerX,
            float startY,
            float mouseX,
            float mouseY,
            float clipLeft,
            float clipTop,
            float clipRight,
            float clipBottom,
            float alpha
    ) {
        List<ModuleStructure> results =
                searchHandler.getSearchRenderResults();
        List<ModuleStructure> activeResults =
                searchHandler.getSearchResults();

        if (results.isEmpty()) {
            return;
        }

        float scroll = searchHandler.getSearchScrollOffset();
        float cardX = centerX - RESULT_WIDTH / 2f;
        int hoveredIndex = -1;

        for (ModuleStructure module : results) {
            float animation =
                    searchHandler.getSearchResultAnimations()
                            .getOrDefault(module, 1f);

            if (animation <= 0.001f) {
                continue;
            }

            float localY =
                    searchHandler.getSearchResultPosition(module);

            float itemY = startY + scroll + localY;

            if (itemY + RESULT_HEIGHT <= clipTop) {
                continue;
            }

            if (itemY >= clipBottom) {
                continue;
            }

            float entrance = easeOutCubic(animation);
            float visualY =
                    itemY + (1f - entrance) * 8f;

            if (visualY < clipTop
                    || visualY + RESULT_HEIGHT > clipBottom) {
                continue;
            }

            boolean active = activeResults.contains(module);

            boolean hovered =
                    active
                            && searchHandler.isSearchActive()
                            && !searchHandler.isSearchClosing()
                            && mouseX >= cardX
                            && mouseX <= cardX + RESULT_WIDTH
                            && mouseY >= visualY
                            && mouseY <= visualY + RESULT_HEIGHT;

            if (hovered) {
                hoveredIndex = activeResults.indexOf(module);
            }

            boolean selected =
                    module == searchHandler.getSelectedSearchModule();

            float exitScale = 0.94f + animation * 0.06f;
            float drawX =
                    centerX - (RESULT_WIDTH * exitScale) / 2f;
            float drawWidth = RESULT_WIDTH * exitScale;

            renderResult(
                    module.getName(),
                    drawX,
                    visualY,
                    drawWidth,
                    animation * alpha,
                    selected,
                    hovered
            );
        }

        if (searchHandler.isSearchActive()
                && !searchHandler.isSearchClosing()) {
            searchHandler.setHoveredSearchIndex(hoveredIndex);
        }
    }

    private float easeOutCubic(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private void renderResult(
            String displayName,
            float x,
            float y,
            float width,
            float alpha,
            boolean selected,
            boolean hovered
    ) {
        float backgroundAlpha;

        if (selected) {
            backgroundAlpha = 42f;
        } else if (hovered) {
            backgroundAlpha = 30f;
        } else {
            backgroundAlpha = 18f;
        }

        Render2D.rect(
                x,
                y + 1f,
                width,
                RESULT_HEIGHT - 2f,
                ThemeColors.color(
                        8,
                        24,
                        42,
                        (int) (backgroundAlpha * alpha)
                ).getRGB(),
                4f
        );

        int textAlpha;

        if (selected) {
            textAlpha = (int) (255f * alpha);
        } else if (hovered) {
            textAlpha = (int) (235f * alpha);
        } else {
            textAlpha = (int) (170f * alpha);
        }

        float maxTextWidth =
                width
                        - RESULT_TEXT_LEFT
                        - RESULT_ICON_RIGHT
                        - 5f;

        String text =
                trimText(
                        displayName,
                        maxTextWidth,
                        RESULT_SIZE
                );

        Fonts.MEDIUM.draw(
                text,
                x + RESULT_TEXT_LEFT,
                y + 5f,
                RESULT_SIZE,
                ThemeColors.color(
                        235,
                        235,
                        242,
                        textAlpha
                ).getRGB()
        );

        int iconAlpha =
                (int) (
                        (hovered || selected
                                ? 255f
                                : 150f)
                                * alpha
                );

        Fonts.MIND_FONT.draw(
                "A",
                x + width - RESULT_ICON_RIGHT,
                y + 5f,
                7.5f,
                ThemeColors.color(
                        255,
                        255,
                        255,
                        iconAlpha
                ).getRGB()
        );
    }

    private String trimText(
            String text,
            float maxWidth,
            float size
    ) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (Fonts.MEDIUM.getWidth(
                text,
                size
        ) <= maxWidth) {
            return text;
        }

        String suffix = "...";

        float suffixWidth =
                Fonts.MEDIUM.getWidth(
                        suffix,
                        size
                );

        if (suffixWidth >= maxWidth) {
            return "";
        }

        int low = 0;
        int high = text.length();

        while (low < high) {
            int mid =
                    (low + high + 1) / 2;

            String candidate =
                    text.substring(0, mid)
                            + suffix;

            if (Fonts.MEDIUM.getWidth(
                    candidate,
                    size
            ) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        if (low <= 0) {
            return suffix;
        }

        return text.substring(0, low)
                + suffix;
    }

    private void renderEmpty(
            float centerX,
            float y,
            float alpha
    ) {
        String text =
                "No modules found";

        float width =
                Fonts.MEDIUM.getWidth(
                        text,
                        5.7f
                );

        Fonts.MEDIUM.draw(
                text,
                centerX - width / 2f,
                y,
                5.7f,
                ThemeColors.color(
                        110,
                        110,
                        120,
                        (int) (170f * alpha)
                ).getRGB()
        );
    }

    public ModuleStructure getModuleAtPosition(
            double mouseX,
            double mouseY,
            float bgX,
            float bgY,
            float bgWidth,
            float bgHeight,
            SearchHandler handler
    ) {
        if (!handler.isSearchActive()
                || handler.isSearchClosing()) {
            return null;
        }

        List<ModuleStructure> results =
                handler.getSearchRenderResults();

        float centerX = bgX + bgWidth / 2f;
        float titleY = bgY + bgHeight / 2f - 72f;
        float startY = titleY + 29f + 30f;
        float clipTop = startY;
        float clipBottom = bgY + bgHeight - 1f;
        float cardX = centerX - RESULT_WIDTH / 2f;
        float scroll = handler.getSearchScrollOffset();

        for (ModuleStructure module : results) {
            if (!handler.getSearchResults().contains(module)) {
                continue;
            }

            float animation =
                    handler.getSearchResultAnimations()
                            .getOrDefault(module, 1f);

            if (animation <= 0.2f) {
                continue;
            }

            float itemY =
                    startY
                            + scroll
                            + handler.getSearchResultPosition(module);

            float visualY = itemY + (1f - animation) * 8f;

            if (visualY < clipTop
                    || visualY + RESULT_HEIGHT > clipBottom) {
                continue;
            }

            if (mouseX >= cardX
                    && mouseX <= cardX + RESULT_WIDTH
                    && mouseY >= visualY
                    && mouseY <= visualY + RESULT_HEIGHT) {
                return module;
            }
        }

        return null;
    }
}
