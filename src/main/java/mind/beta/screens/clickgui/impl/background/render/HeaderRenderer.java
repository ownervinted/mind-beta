package mind.beta.screens.clickgui.impl.background.render;

import mind.beta.util.render.theme.ThemeColors;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.screens.clickgui.impl.background.search.SearchHandler;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.font.Fonts;

import java.awt.*;

public class HeaderRenderer {

    public enum SortMode {
        DEFAULT("Default"),
        ALPHABETICAL("A - Z"),
        REVERSE_ALPHABETICAL("Z - A"),
        ENABLED_FIRST("Enabled first"),
        DISABLED_FIRST("Disabled first");

        private final String label;
        SortMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private static SortMode sortMode = SortMode.DEFAULT;
    private static SortMode previousSortMode = SortMode.DEFAULT;
    private static long sortChangeTime;
    private static float sortX;
    private static float sortY;
    private static boolean sortVisible;
    private static float sortWidth = 82f;
    private static final float SORT_HEIGHT = 15f;

    public static SortMode getSortMode() { return sortMode; }
    public static void closeSortMenu() { }

    private static final float HEADER_HEIGHT = 32f;
    private static final float HEADER_X_OFFSET = 92f;
    private static final float HEADER_RADIUS = 8f;
    private static final float HEADER_SLIDE_DISTANCE = 8f;

    private static final float SEARCH_BOX_X_OFFSET = 282f;
    private static final float SEARCH_BOX_Y_OFFSET = 7f;
    private static final float SEARCH_BOX_WIDTH = 105f;
    private static final float SEARCH_BOX_HEIGHT = 19f;
    private static final float SEARCH_TEXT_SIZE = 7f;
    private static final float SEARCH_TEXT_X = 9f;
    private static final float SEARCH_ICON_SIZE = 8f;
    private static final float SEARCH_ICON_RIGHT = 8f;
    private static final float SEARCH_DIVIDER_GAP = 26f;

    private static final long SEARCH_HINT_HOLD_TIME = 10_000L;
    private static final long SEARCH_HINT_FADE_TIME = 700L;

    public void render(
            float bgX,
            float bgY,
            float bgWidth,
            ModuleCategory selectedCategory,
            ModuleCategory previousCategory,
            ModuleCategory currentCategory,
            float headerTransition,
            SearchHandler searchHandler,
            float alphaMultiplier
    ) {
        float headerX = bgX + HEADER_X_OFFSET;
        float headerWidth = bgWidth - HEADER_X_OFFSET;

        int headerColor = GuiTheme.panelColor(alphaMultiplier);

        Render2D.rect(
                headerX,
                bgY,
                headerWidth,
                HEADER_HEIGHT,
                headerColor,
                0f,
                HEADER_RADIUS,
                0f,
                0f
        );

        renderSearchBox(
                bgX,
                bgY,
                searchHandler,
                alphaMultiplier
        );

        renderCategoryLabel(
                bgX,
                bgY,
                bgWidth,
                previousCategory,
                currentCategory,
                headerTransition,
                searchHandler,
                alphaMultiplier
        );

        renderSortControl(bgX, bgY, currentCategory, headerTransition,
                searchHandler.getNormalPanelAlpha() * alphaMultiplier);
    }

    private void renderSearchBox(
            float bgX,
            float bgY,
            SearchHandler searchHandler,
            float alphaMultiplier
    ) {
        float searchBoxX = bgX + SEARCH_BOX_X_OFFSET;
        float searchBoxY = bgY + SEARCH_BOX_Y_OFFSET;
        float searchBoxW = SEARCH_BOX_WIDTH;
        float searchBoxH = SEARCH_BOX_HEIGHT;

        int textAlpha = clampAlpha(185f * alphaMultiplier);

        float dividerX =
                searchBoxX
                        + searchBoxW
                        - SEARCH_DIVIDER_GAP;

        float textAreaX =
                searchBoxX
                        + SEARCH_TEXT_X;

        if (
                searchHandler.isSearchActive()
                        && !searchHandler.getSearchText().isEmpty()
        ) {
            renderSearchText(
                    searchBoxX,
                    searchBoxY,
                    searchBoxW,
                    searchBoxH,
                    textAreaX,
                    searchHandler,
                    alphaMultiplier
            );
        }

        renderAnimatedSearchHint(
                dividerX,
                searchBoxY,
                alphaMultiplier
        );

        Render2D.rect(
                dividerX,
                searchBoxY + 4f,
                0.6f,
                searchBoxH - 8f,
                ThemeColors.color(
                        205,
                        227,
                        255,
                        clampAlpha(55f * alphaMultiplier)
                ).getRGB()
        );

        float iconX =
                searchBoxX
                        + searchBoxW
                        - SEARCH_ICON_RIGHT
                        - SEARCH_ICON_SIZE;

        Fonts.MIND_FONT.draw(
                "F",
                iconX,
                searchBoxY + 6f,
                SEARCH_ICON_SIZE,
                ThemeColors.color(
                        255,
                        255,
                        255,
                        textAlpha
                ).getRGB()
        );
    }

    private void renderAnimatedSearchHint(
            float dividerX,
            float searchBoxY,
            float alphaMultiplier
    ) {
        String firstText = "Ctrl + F";
        String secondText = "Search";

        long phaseLength =
                SEARCH_HINT_HOLD_TIME
                        + SEARCH_HINT_FADE_TIME;

        long fullCycle =
                phaseLength * 2L;

        long time =
                System.currentTimeMillis()
                        % fullCycle;

        float firstAlpha;
        float secondAlpha;

        if (time < SEARCH_HINT_HOLD_TIME) {
            firstAlpha = 1f;
            secondAlpha = 0f;
        } else if (time < phaseLength) {
            float progress =
                    (time - SEARCH_HINT_HOLD_TIME)
                            / (float) SEARCH_HINT_FADE_TIME;

            progress = smoothStep(progress);

            firstAlpha = 1f - progress;
            secondAlpha = progress;
        } else if (
                time
                        < phaseLength
                        + SEARCH_HINT_HOLD_TIME
        ) {
            firstAlpha = 0f;
            secondAlpha = 1f;
        } else {
            float progress =
                    (
                            time
                                    - phaseLength
                                    - SEARCH_HINT_HOLD_TIME
                    )
                            / (float) SEARCH_HINT_FADE_TIME;

            progress = smoothStep(progress);

            firstAlpha = progress;
            secondAlpha = 1f - progress;
        }

        float firstWidth =
                Fonts.MEDIUM.getWidth(
                        firstText,
                        SEARCH_TEXT_SIZE
                );

        float secondWidth =
                Fonts.MEDIUM.getWidth(
                        secondText,
                        SEARCH_TEXT_SIZE
                );

        float firstX =
                dividerX
                        - 8f
                        - firstWidth;

        float secondX =
                dividerX
                        - 8f
                        - secondWidth;

        int firstColor =
                ThemeColors.color(
                        255,
                        255,
                        255,
                        clampAlpha(
                                185f
                                        * firstAlpha
                                        * alphaMultiplier
                        )
                ).getRGB();

        int secondColor =
                ThemeColors.color(
                        255,
                        255,
                        255,
                        clampAlpha(
                                185f
                                        * secondAlpha
                                        * alphaMultiplier
                        )
                ).getRGB();

        if (firstAlpha > 0.001f) {
            Fonts.MEDIUM.draw(
                    firstText,
                    firstX,
                    searchBoxY + 6f,
                    SEARCH_TEXT_SIZE,
                    firstColor
            );
        }

        if (secondAlpha > 0.001f) {
            Fonts.MEDIUM.draw(
                    secondText,
                    secondX,
                    searchBoxY + 6f,
                    SEARCH_TEXT_SIZE,
                    secondColor
            );
        }
    }

    private void renderSearchText(
            float searchBoxX,
            float searchBoxY,
            float searchBoxW,
            float searchBoxH,
            float textAreaX,
            SearchHandler searchHandler,
            float alphaMultiplier
    ) {
        Scissor.enable(
                searchBoxX + 4f,
                searchBoxY + 2f,
                searchBoxW - SEARCH_DIVIDER_GAP,
                searchBoxH - 4f,
                3
        );

        if (searchHandler.hasSearchSelection()) {
            renderSearchSelection(
                    textAreaX,
                    searchBoxY,
                    searchBoxH,
                    searchHandler,
                    alphaMultiplier
            );
        }

        Fonts.MEDIUM.draw(
                searchHandler.getSearchText(),
                textAreaX,
                searchBoxY + 6f,
                SEARCH_TEXT_SIZE,
                ThemeColors.color(
                        220,
                        220,
                        225,
                        (int) (255f * alphaMultiplier)
                ).getRGB()
        );

        Scissor.disable();
    }

    private void renderSearchSelection(
            float textAreaX,
            float searchBoxY,
            float searchBoxH,
            SearchHandler searchHandler,
            float alphaMultiplier
    ) {
        int start =
                searchHandler.getSearchSelectionStart();

        int end =
                searchHandler.getSearchSelectionEnd();

        String beforeSelection =
                searchHandler
                        .getSearchText()
                        .substring(
                                0,
                                start
                        );

        String selection =
                searchHandler
                        .getSearchText()
                        .substring(
                                start,
                                end
                        );

        float selectionX =
                textAreaX
                        + Fonts.MEDIUM.getWidth(
                        beforeSelection,
                        SEARCH_TEXT_SIZE
                );

        float selectionWidth =
                Fonts.MEDIUM.getWidth(
                        selection,
                        SEARCH_TEXT_SIZE
                );

        int selAlpha =
                (int) (
                        100f
                                * alphaMultiplier
                );

        Render2D.rect(
                selectionX,
                searchBoxY + 3f,
                selectionWidth,
                searchBoxH - 6f,
                ThemeColors.color(
                        0,
                        128,
                        255,
                        selAlpha
                ).getRGB(),
                2f
        );
    }

    private void renderCategoryLabel(
            float bgX,
            float bgY,
            float bgWidth,
            ModuleCategory previousCategory,
            ModuleCategory currentCategory,
            float headerTransition,
            SearchHandler searchHandler,
            float alphaMultiplier
    ) {
        float headerX =
                bgX
                        + HEADER_X_OFFSET;

        float categoryX =
                headerX
                        + 10f;

        float baseY =
                bgY
                        + 12f;

        float categoryAlpha =
                searchHandler
                        .getNormalPanelAlpha()
                        * alphaMultiplier;

        if (categoryAlpha > 0.01f) {
            float eased =
                    easeOutQuart(
                            headerTransition
                    );

            if (
                    previousCategory != null
                            && headerTransition < 1f
            ) {
                String oldName =
                        previousCategory
                                .getReadableName();

                float oldAlpha =
                        (1f - eased)
                                * categoryAlpha;

                float oldOffsetY =
                        eased
                                * HEADER_SLIDE_DISTANCE;

                int oldAlphaInt =
                        (int) (
                                110f
                                        * oldAlpha
                        );

                if (oldAlphaInt > 0) {
                    Fonts.MEDIUM.draw(
                            oldName,
                            categoryX,
                            baseY + oldOffsetY,
                            8.5f,
                            ThemeColors.color(
                                    135,
                                    135,
                                    140,
                                    oldAlphaInt
                            ).getRGB()
                    );
                }
            }

            if (currentCategory != null) {
                String newName =
                        currentCategory
                                .getReadableName();

                float newAlpha =
                        eased
                                * categoryAlpha;

                float newOffsetY =
                        (1f - eased)
                                * -HEADER_SLIDE_DISTANCE;

                int newAlphaInt =
                        (int) (
                                225f
                                        * newAlpha
                        );

                if (newAlphaInt > 0) {
                    Fonts.MEDIUM.draw(
                            newName,
                            categoryX,
                            baseY + newOffsetY,
                            8.5f,
                            ThemeColors.color(
                                    225,
                                    225,
                                    230,
                                    newAlphaInt
                            ).getRGB()
                    );
                }
            }
        }
    }

    private void renderSortControl(float bgX, float bgY, ModuleCategory category,
                                   float transition, float alpha) {
        sortVisible = category == ModuleCategory.COMBAT || category == ModuleCategory.MOVEMENT
                || category == ModuleCategory.RENDER || category == ModuleCategory.PLAYER
                || category == ModuleCategory.MISC;
        if (!sortVisible || alpha <= 0.01f) return;

        float labelWidth = Fonts.MEDIUM.getWidth(category.getReadableName(), 8.5f);
        sortWidth = 0f;
        for (SortMode mode : SortMode.values()) {
            sortWidth = Math.max(sortWidth, Fonts.BOLD.getWidth(mode.getLabel(), 6f));
        }
        sortWidth += 16f;
        sortX = Math.min(bgX + 187f, bgX + HEADER_X_OFFSET + 17f + labelWidth);
        sortY = bgY + 9f;

        float categoryProgress = easeOutQuart(Math.max(0f, Math.min(1f, transition)));
        float opacityFactor = alpha * categoryProgress;
        float slide = (1f - categoryProgress) * -HEADER_SLIDE_DISTANCE;

        Render2D.rect(sortX, sortY + slide, sortWidth, SORT_HEIGHT,
                ThemeColors.color(12, 30, 48, clampAlpha(65f * opacityFactor)).getRGB(), 4f);

        float changeProgress = sortChangeTime == 0L ? 1f :
                Math.min(1f, (System.nanoTime() - sortChangeTime) / 220_000_000f);
        changeProgress = easeOutQuart(changeProgress);
        float textY = sortY + (SORT_HEIGHT - 6f) / 2f + slide;
        if (changeProgress < 1f) {
            Fonts.BOLD.draw(previousSortMode.getLabel(),
                    sortX + (sortWidth - Fonts.BOLD.getWidth(previousSortMode.getLabel(), 6f)) / 2f,
                    textY - changeProgress * 5f, 6f,
                    ThemeColors.color(230, 236, 244, clampAlpha(205f * opacityFactor * (1f - changeProgress))).getRGB());
        }
        Fonts.BOLD.draw(sortMode.getLabel(),
                sortX + (sortWidth - Fonts.BOLD.getWidth(sortMode.getLabel(), 6f)) / 2f,
                textY + (1f - changeProgress) * 5f, 6f,
                ThemeColors.color(230, 236, 244, clampAlpha(205f * opacityFactor * changeProgress)).getRGB());
    }

    public static void renderSortOverlay(float alpha) { }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!sortVisible || button != 0) return false;
        if (mouseX >= sortX && mouseX <= sortX + sortWidth
                && mouseY >= sortY && mouseY <= sortY + SORT_HEIGHT) {
            previousSortMode = sortMode;
            SortMode[] modes = SortMode.values();
            sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
            sortChangeTime = System.nanoTime();
            return true;
        }
        return false;
    }

    private int clampAlpha(float value) {
        return Math.max(
                0,
                Math.min(
                        255,
                        (int) value
                )
        );
    }

    private float easeOutQuart(float x) {
        return 1f
                - (float) Math.pow(
                1f - x,
                4
        );
    }

    private float smoothStep(float x) {
        x =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                x
                        )
                );

        return x
                * x
                * (
                3f
                        - 2f
                        * x
        );
    }

    public boolean isSearchBoxHovered(
            double mouseX,
            double mouseY,
            float bgX,
            float bgY
    ) {
        float searchBoxX =
                bgX
                        + SEARCH_BOX_X_OFFSET;

        float searchBoxY =
                bgY
                        + SEARCH_BOX_Y_OFFSET;

        float searchBoxW =
                SEARCH_BOX_WIDTH;

        float searchBoxH =
                SEARCH_BOX_HEIGHT;

        return mouseX >= searchBoxX
                && mouseX <= searchBoxX + searchBoxW
                && mouseY >= searchBoxY
                && mouseY <= searchBoxY + searchBoxH;
    }
}
