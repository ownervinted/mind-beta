package mind.beta.screens.clickgui.impl.background.render;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CategoryRenderer {

    private static final ModuleCategory[] MAIN_CATEGORIES = {
            ModuleCategory.COMBAT,
            ModuleCategory.MOVEMENT,
            ModuleCategory.RENDER,
            ModuleCategory.PLAYER,
            ModuleCategory.MISC
    };

    private static final String[] MAIN_CATEGORY_ICONS = {
            "C",
            "O",
            "R",
            "P",
            "M"
    };

    private final Map<ModuleCategory, Float> categoryAnimations = new HashMap<>();

    private static final float ANIMATION_SPEED = 8f;

    private static final float ICON_SIZE = 8f;
    private static final float CATEGORY_TEXT_SIZE = 7f;

    private static final float ICON_X = 14f;
    private static final float TEXT_X = 24f;

    private static final float ITEM_X = 4f;
    private static final float ITEM_WIDTH = 84f;
    private static final float ITEM_HEIGHT = 18f;
    private static final float ITEM_RADIUS = 10f;

    private static final float MIND_SIZE = 16f;
    private static final float MIND_Y = 17f;
    private static final float SECTION_TEXT_SIZE = 6.5f;
    private static final float MAIN_START_Y = 69f;
    private static final float MAIN_ITEM_STEP = 23f;
    private static final float OTHERS_LABEL_Y = 180f;
    private static final float OTHER_START_Y = 196f;
    private static final float OTHER_ITEM_STEP = 21f;

    public CategoryRenderer() {
        for (ModuleCategory cat : MAIN_CATEGORIES) {
            categoryAnimations.put(cat, 0f);
        }
        categoryAnimations.put(ModuleCategory.LUA, 0f);
        categoryAnimations.put(ModuleCategory.THEME, 0f);
        categoryAnimations.put(ModuleCategory.CONFIG, 0f);
    }

    public void updateAnimations(
            ModuleCategory selectedCategory,
            float deltaTime
    ) {
        for (ModuleCategory cat : MAIN_CATEGORIES) {
            updateCategoryAnimation(
                    cat,
                    selectedCategory,
                    deltaTime
            );
        }
        updateCategoryAnimation(ModuleCategory.LUA, selectedCategory, deltaTime);
        updateCategoryAnimation(ModuleCategory.THEME, selectedCategory, deltaTime);
        updateCategoryAnimation(ModuleCategory.CONFIG, selectedCategory, deltaTime);
    }

    private void updateCategoryAnimation(
            ModuleCategory cat,
            ModuleCategory selected,
            float deltaTime
    ) {
        float target = cat == selected ? 1f : 0f;
        float current = categoryAnimations.getOrDefault(
                cat,
                0f
        );

        float speed = 1f - (float) Math.exp(
                -ANIMATION_SPEED * deltaTime
        );

        current += (target - current) * speed;

        if (Math.abs(target - current) < 0.001f) {
            current = target;
        }

        categoryAnimations.put(
                cat,
                current
        );
    }

    public void render(
            DrawContext context,
            float bgX,
            float bgY,
            ModuleCategory selectedCategory,
            float alphaMultiplier
    ) {
        renderMind(
                bgX,
                bgY,
                alphaMultiplier
        );

        renderSectionLabel(
                "Modules",
                bgX,
                bgY + 50f,
                alphaMultiplier
        );

        renderMainCategories(
                bgX,
                bgY,
                alphaMultiplier
        );

        renderSeparatorLine(
                bgX,
                bgY + 172f,
                alphaMultiplier
        );

        renderSectionLabel(
                "Others",
                bgX,
                bgY + OTHERS_LABEL_Y,
                alphaMultiplier
        );

        renderPlaceholderCategories(
                bgX,
                bgY,
                alphaMultiplier
        );
    }

    private void renderMind(
            float bgX,
            float bgY,
            float alphaMultiplier
    ) {
        String text = "Mind";

        float panelCenterX = bgX + 46f;

        float textWidth = Fonts.MEDIUM.getWidth(
                text,
                MIND_SIZE
        );

        float textX =
                panelCenterX -
                        textWidth / 2f;

        float textY =
                bgY +
                        MIND_Y;

        int alpha = clampAlpha(
                255f * alphaMultiplier
        );

        Fonts.MEDIUM.draw(
                text,
                textX,
                textY,
                MIND_SIZE,
                ThemeColors.color(
                        255,
                        255,
                        255,
                        alpha
                ).getRGB()
        );

        renderSeparatorLine(
                bgX,
                bgY + 42f,
                alphaMultiplier
        );
    }

    private void renderSeparatorLine(
            float bgX,
            float y,
            float alphaMultiplier
    ) {
        float panelCenterX = bgX + 46f;
        float lineWidth = 68f;
        float lineX = panelCenterX - lineWidth / 2f;

        Render2D.rect(
                lineX,
                y,
                lineWidth,
                0.7f,
                ThemeColors.color(
                        0, 128, 255,
                        clampAlpha(
                                15f * alphaMultiplier
                        )
                ).getRGB(),
                0f
        );
    }

    private void renderMainCategories(
            float bgX,
            float bgY,
            float alphaMultiplier
    ) {
        for (int i = 0; i < MAIN_CATEGORIES.length; i++) {
            ModuleCategory cat =
                    MAIN_CATEGORIES[i];

            float animation =
                    categoryAnimations.getOrDefault(
                            cat,
                            0f
                    );

            float centerY =
                    bgY +
                            MAIN_START_Y +
                            i * MAIN_ITEM_STEP;

            renderCategoryItem(
                    bgX,
                    centerY,
                    MAIN_CATEGORY_ICONS[i],
                    cat.getReadableName(),
                    animation,
                    alphaMultiplier
            );
        }
    }

    private void renderSectionLabel(
            String text,
            float bgX,
            float y,
            float alphaMultiplier
    ) {
        Fonts.MEDIUM.draw(
                text,
                bgX + 8f,
                y,
                SECTION_TEXT_SIZE,
                ThemeColors.color(
                        125,
                        135,
                        148,
                        clampAlpha(165f * alphaMultiplier)
                ).getRGB()
        );
    }

    private void renderPlaceholderCategories(
            float bgX,
            float bgY,
            float alphaMultiplier
    ) {
        String[] names = {"Config", "Theme", "Lua"};
        String[] icons = {"S", "T", "L"};

        for (int i = 0; i < names.length; i++) {
            float centerY = bgY + OTHER_START_Y + i * OTHER_ITEM_STEP;
            float animation = i == 2
                    ? categoryAnimations.getOrDefault(ModuleCategory.LUA, 0f)
                    : i == 1 ? categoryAnimations.getOrDefault(ModuleCategory.THEME, 0f)
                    : categoryAnimations.getOrDefault(ModuleCategory.CONFIG, 0f);
            renderCategoryItem(
                    bgX,
                    centerY,
                    icons[i],
                    names[i],
                    animation,
                    alphaMultiplier
            );
        }
    }

    private void renderCategoryItem(
            float bgX,
            float centerY,
            String icon,
            String categoryName,
            float animation,
            float alphaMultiplier
    ) {
        float smoothAnimation =
                smoothStep(animation);

        float itemY =
                centerY -
                        ITEM_HEIGHT / 2f;

        int backgroundAlpha =
                clampAlpha(
                        5f *
                                smoothAnimation *
                                alphaMultiplier
                );

        Render2D.rect(
                bgX + ITEM_X,
                itemY,
                ITEM_WIDTH,
                ITEM_HEIGHT,
                ThemeColors.color(
                        0, 128, 255,
                        backgroundAlpha
                ).getRGB(),
                ITEM_RADIUS
        );

        float iconSize = ICON_SIZE;
        float textSize = CATEGORY_TEXT_SIZE;

        float iconWidth = Fonts.MIND_FONT.getWidth(icon, iconSize);

        float iconX =
                bgX +
                        ICON_X -
                        iconWidth / 2f;

        float iconTop =
                centerY -
                        iconSize / 2f;

        float textTop =
                centerY -
                        textSize / 2f;

        int iconBrightness =
                (int) (
                        140f +
                                (255f - 140f) *
                                        smoothAnimation
                );

        int iconAlpha =
                clampAlpha(
                        (
                                175f +
                                        80f *
                                                smoothAnimation
                        ) *
                                alphaMultiplier
                );

        Fonts.MIND_FONT.draw(
                icon,
                iconX,
                iconTop,
                iconSize,
                ThemeColors.color(
                        iconBrightness,
                        iconBrightness,
                        iconBrightness,
                        iconAlpha
                ).getRGB()
        );

        drawWaveText(
                categoryName,
                bgX + TEXT_X,
                textTop,
                textSize,
                animation,
                alphaMultiplier
        );
    }

    private void drawWaveText(
            String text,
            float x,
            float y,
            float size,
            float animation,
            float alphaMultiplier
    ) {
        int baseAlpha =
                clampAlpha(
                        185f *
                                alphaMultiplier
                );

        Fonts.MEDIUM.draw(
                text,
                x,
                y,
                size,
                ThemeColors.color(
                        135,
                        135,
                        135,
                        baseAlpha
                ).getRGB()
        );

        if (animation <= 0f) {
            return;
        }

        float currentX = x;

        int length = text.length();

        for (int i = 0; i < length; i++) {
            String character =
                    String.valueOf(
                            text.charAt(i)
                    );

            float charWidth =
                    Fonts.MEDIUM.getWidth(
                            character,
                            size
                    );

            float position =
                    length <= 1
                            ? 0f
                            : i /
                            (float) (
                                    length - 1
                            );

            float waveWidth = 0.38f;

            float local =
                    (
                            animation -
                                    position *
                                            (
                                                    1f -
                                                            waveWidth
                                            )
                    ) /
                            waveWidth;

            local = Math.max(
                    0f,
                    Math.min(
                            1f,
                            local
                    )
            );

            local = smoothStep(local);

            if (local > 0f) {
                int whiteAlpha =
                        clampAlpha(
                                255f *
                                        local *
                                        alphaMultiplier
                        );

                Fonts.MEDIUM.draw(
                        character,
                        currentX,
                        y,
                        size,
                        ThemeColors.color(
                                255,
                                255,
                                255,
                                whiteAlpha
                        ).getRGB()
                );
            }

            currentX += charWidth;
        }
    }

    private float smoothStep(
            float value
    ) {
        value = Math.max(
                0f,
                Math.min(
                        1f,
                        value
                )
        );

        return value *
                value *
                (3f - 2f * value);
    }

    private int clampAlpha(
            float value
    ) {
        return Math.max(
                0,
                Math.min(
                        255,
                        (int) value
                )
        );
    }

    public ModuleCategory getCategoryAtPosition(
            double mouseX,
            double mouseY,
            float bgX,
            float bgY
    ) {
        if (mouseX < bgX + 4f ||
                mouseX > bgX + 88f) {
            return null;
        }

        for (int i = 0; i < MAIN_CATEGORIES.length; i++) {
            float catY =
                    MAIN_START_Y +
                            i * MAIN_ITEM_STEP;

            if (mouseY >= bgY + catY - 9f &&
                    mouseY <= bgY + catY + 11f) {
                return MAIN_CATEGORIES[i];
            }
        }

        if (mouseY >= bgY + OTHER_START_Y - 9f && mouseY <= bgY + OTHER_START_Y + 11f) {
            return ModuleCategory.CONFIG;
        }

        float themeY = OTHER_START_Y + OTHER_ITEM_STEP;
        if (mouseY >= bgY + themeY - 9f && mouseY <= bgY + themeY + 11f) {
            return ModuleCategory.THEME;
        }

        float luaY = OTHER_START_Y + 2f * OTHER_ITEM_STEP;
        if (mouseY >= bgY + luaY - 9f && mouseY <= bgY + luaY + 11f) {
            return ModuleCategory.LUA;
        }

        return null;
    }
}