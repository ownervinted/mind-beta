package mind.beta.screens.clickgui.impl.module.render;

import mind.beta.screens.clickgui.impl.settingsrender.*;
import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.screens.clickgui.impl.module.handler.ModuleAnimationHandler;
import mind.beta.screens.clickgui.impl.module.handler.ModuleScrollHandler;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SettingsPanelRenderer {

    private static final float SETTINGS_PANEL_CORNER_RADIUS = 0f;
    private static final float SETTINGS_CONTENT_INSET = 12f;
    private static final float CORNER_INSET = 3f;
    private static final int SETTING_HEIGHT = 16;
    private static final int SETTING_SPACING = 2;

    private static final float TRANSITION_MIN_SCALE = 0.90f;

    private final ModuleAnimationHandler animationHandler;

    public SettingsPanelRenderer(ModuleAnimationHandler animationHandler) {
        this.animationHandler = animationHandler;
    }

    public void render(
            DrawContext context,
            ModuleStructure selectedModule,
            List<AbstractSettingComponent> settingComponents,
            ModuleStructure previousSelectedModule,
            List<AbstractSettingComponent> previousSettingComponents,
            boolean moduleSwitching,
            float transitionProgress,
            float x,
            float y,
            float width,
            float height,
            float mouseX,
            float mouseY,
            float delta,
            int guiScale,
            float alphaMultiplier,
            ModuleScrollHandler scrollHandler,
            ModuleAnimationHandler animHandler
    ) {
        animHandler.updateSettingAnimations(settingComponents);
        animHandler.updateVisibilityAnimations(settingComponents);

        if (selectedModule == null) {
            String text = "Select a module";
            float textSize = 7f;
            float textWidth = Fonts.MEDIUM.getWidth(text, textSize);
            float textHeight = Fonts.MEDIUM.getHeight(textSize);
            float centerX = x + (width - textWidth) / 2f;
            float centerY = y + (height - textHeight) / 2f;

            Fonts.MEDIUM.draw(
                    text,
                    centerX,
                    centerY,
                    textSize,
                    ThemeColors.color(
                            100,
                            100,
                            100,
                            (int) (150 * alphaMultiplier)
                    ).getRGB()
            );

            return;
        }

        boolean hasDescription =
                selectedModule.getDescription() != null
                        && !selectedModule.getDescription().isBlank();

        float headerBottom =
                y + (hasDescription ? 30f : 22f);

        float contentStartY =
                headerBottom + 8f;

        if (
                moduleSwitching
                        && previousSelectedModule != null
        ) {
            float progress = easeOutCubic(
                    Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    transitionProgress
                            )
                    )
            );

            float oldOffset = -28f * progress;
            float oldAlpha = 1f - progress;

            renderModuleHeader(
                    context,
                    previousSelectedModule,
                    x,
                    y,
                    alphaMultiplier * oldAlpha,
                    oldOffset
            );

            float newOffset = 28f * (1f - progress);
            float newAlpha = progress;

            renderModuleHeader(
                    context,
                    selectedModule,
                    x,
                    y,
                    alphaMultiplier * newAlpha,
                    newOffset
            );
        } else {
            renderModuleHeader(
                    context,
                    selectedModule,
                    x,
                    y,
                    alphaMultiplier,
                    0f
            );
        }

        Render2D.rect(
                x + 8f,
                headerBottom,
                width - 16f,
                0.7f,
                ThemeColors.color(
                        0,
                        128,
                        255,
                        (int) (15f * alphaMultiplier)
                ).getRGB(),
                0f
        );

        float sideInset = CORNER_INSET;

        float clipY = headerBottom + 1f;
        float clipBottom = y + height - 1f;
        float clipH = Math.max(0f, clipBottom - clipY);
        float clipX = x + sideInset;
        float clipW = width - sideInset * 2f;

        Scissor.enable(
                clipX,
                clipY,
                clipW,
                clipH,
                guiScale
        );

        if (
                moduleSwitching
                        && previousSelectedModule != null
                        && !previousSettingComponents.isEmpty()
        ) {
            float oldProgress = easeOutCubic(
                    Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    transitionProgress
                            )
                    )
            );

            renderAnimatedContent(
                    context,
                    previousSettingComponents,
                    x,
                    y,
                    width,
                    height,
                    mouseX,
                    mouseY,
                    delta,
                    guiScale,
                    alphaMultiplier * (1f - oldProgress),
                    scrollHandler,
                    true,
                    contentStartY
            );
        }

        float newProgress = easeOutCubic(
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                transitionProgress
                        )
                )
        );

        renderAnimatedContent(
                context,
                settingComponents,
                x,
                y,
                width,
                height,
                mouseX,
                mouseY,
                delta,
                guiScale,
                alphaMultiplier,
                scrollHandler,
                false,
                contentStartY
        );

        Scissor.disable();

        if (!moduleSwitching) {
            for (AbstractSettingComponent c : settingComponents) {
                if (
                        c instanceof SelectComponent selectComponent
                                && selectComponent.isExpanded()
                ) {
                    selectComponent.renderDropdown(
                            context,
                            (int) mouseX,
                            (int) mouseY,
                            delta
                    );
                } else if (
                        c instanceof MultiSelectComponent multiSelectComponent
                                && multiSelectComponent.isExpanded()
                ) {
                    multiSelectComponent.renderDropdown(
                            context,
                            (int) mouseX,
                            (int) mouseY,
                            delta
                    );
                }
            }
        }

        boolean hasVisibleSettings = false;

        for (AbstractSettingComponent c : settingComponents) {
            float visAnim = animHandler.getVisibilityAnimations()
                    .getOrDefault(c, 0f);

            if (visAnim > 0.01f) {
                hasVisibleSettings = true;
                break;
            }
        }

        if (!hasVisibleSettings && !moduleSwitching) {
            String text = "This module doesn't have settings";
            float textSize = 6f;
            float textWidth = Fonts.MEDIUM.getWidth(text, textSize);
            float textHeight = Fonts.MEDIUM.getHeight(textSize);
            float centerX = x + (width - textWidth) / 2f;
            float centerY = y + (height - textHeight) / 2f + 10f;

            Fonts.MEDIUM.draw(
                    text,
                    centerX,
                    centerY,
                    textSize,
                    ThemeColors.color(
                            100,
                            100,
                            100,
                            (int) (150 * alphaMultiplier)
                    ).getRGB()
            );
        }

        renderScrollFade(
                x + sideInset,
                clipY,
                width - sideInset * 2f,
                clipH,
                scrollHandler.getSettingScrollTopFade() * alphaMultiplier,
                scrollHandler.getSettingScrollBottomFade() * alphaMultiplier,
                60,
                12
        );
    }

    private void renderModuleHeader(
            DrawContext context,
            ModuleStructure module,
            float x,
            float y,
            float alpha,
            float offsetX
    ) {
        if (module == null || alpha <= 0.01f) {
            return;
        }

        float headerX = x + offsetX;

        String desc = module.getDescription();

        boolean hasDescription =
                desc != null
                        && !desc.isBlank();

        if (hasDescription) {
            Fonts.MEDIUM.draw(
                    module.getName(),
                    headerX + 9f,
                    y + 7f,
                    8f,
                    ThemeColors.color(
                            255,
                            255,
                            255,
                            (int) (200f * alpha)
                    ).getRGB()
            );

            String renderedDesc =
                    desc.length() > 55
                            ? desc.substring(0, 55) + "..."
                            : desc;

            Fonts.MEDIUM.draw(
                    renderedDesc,
                    headerX + 9f,
                    y + 20f,
                    5.5f,
                    ThemeColors.color(
                            128,
                            128,
                            128,
                            (int) (150f * alpha)
                    ).getRGB()
            );
        } else {
            Fonts.MEDIUM.draw(
                    module.getName(),
                    headerX + 9f,
                    y + 8f,
                    9f,
                    ThemeColors.color(
                            255,
                            255,
                            255,
                            (int) (220f * alpha)
                    ).getRGB()
            );
        }
    }

    private void renderAnimatedContent(
            DrawContext context,
            List<AbstractSettingComponent> components,
            float x,
            float y,
            float width,
            float height,
            float mouseX,
            float mouseY,
            float delta,
            int guiScale,
            float alphaMultiplier,
            ModuleScrollHandler scrollHandler,
            boolean oldLayer,
            float contentStartY
    ) {
        if (alphaMultiplier <= 0.01f) {
            return;
        }

        List<Float> finalYPositions = new ArrayList<>();
        List<Float> animatedHeights = new ArrayList<>();

        float posY =
                contentStartY
                        + (float) scrollHandler.getSettingDisplayScroll();

        for (AbstractSettingComponent c : components) {
            float heightAnim = animationHandler.getHeightAnimations()
                    .getOrDefault(
                            c,
                            c.getSetting().isVisible() ? 1f : 0f
                    );

            if (heightAnim <= 0.001f) {
                finalYPositions.add(null);
                animatedHeights.add(0f);
                continue;
            }

            finalYPositions.add(posY);

            float baseHeight = getComponentBaseHeight(c);
            float layoutHeight = baseHeight * heightAnim;

            animatedHeights.add(layoutHeight);

            posY +=
                    layoutHeight
                            + SETTING_SPACING * heightAnim;
        }

        float visibleTop = contentStartY - 7f;

        float visibleBottom =
                y
                        + height
                        - 1f;

        for (int i = 0; i < components.size(); i++) {
            AbstractSettingComponent c = components.get(i);

            Float startY = finalYPositions.get(i);

            if (startY == null) {
                continue;
            }

            float visAnim = animationHandler.getVisibilityAnimations()
                    .getOrDefault(
                            c,
                            c.getSetting().isVisible() ? 1f : 0f
                    );

            float heightAnim = animationHandler.getHeightAnimations()
                    .getOrDefault(
                            c,
                            c.getSetting().isVisible() ? 1f : 0f
                    );

            if (
                    visAnim <= 0.001f
                            && heightAnim <= 0.001f
            ) {
                continue;
            }

            float animatedHeight =
                    animatedHeights.get(i);

            float settingProgress = animationHandler
                    .getSettingAnimations()
                    .getOrDefault(c, 1f);

            settingProgress = Math.max(
                    0f,
                    Math.min(
                            1f,
                            settingProgress
                    )
            );

            float easedProgress =
                    easeOutCubic(settingProgress);

            float offset;

            if (oldLayer) {
                offset = -28f * easedProgress;
            } else {
                offset = 28f * (1f - easedProgress);
            }

            float componentAlpha;

            if (oldLayer) {
                componentAlpha =
                        visAnim
                                * alphaMultiplier
                                * (1f - easedProgress);
            } else {
                componentAlpha =
                        visAnim
                                * alphaMultiplier
                                * easedProgress;
            }

            float renderX =
                    x
                            + SETTINGS_CONTENT_INSET
                            + offset;

            c.position(
                    renderX,
                    startY
            );

            c.size(
                    width
                            - SETTINGS_CONTENT_INSET
                            - 8f,
                    c instanceof ColorComponent ? SETTING_HEIGHT : getComponentBaseHeight(c)
            );

            c.setAlphaMultiplier(
                    componentAlpha
            );

            if (
                    startY + animatedHeight >= visibleTop
                            && startY <= visibleBottom
                            && componentAlpha > 0.01f
            ) {
                float itemClipTop =
                        Math.max(
                                startY,
                                visibleTop
                        );

                float itemClipBottom =
                        Math.min(
                                startY + animatedHeight,
                                visibleBottom
                        );

                float itemClipHeight =
                        itemClipBottom
                                - itemClipTop;

                if (itemClipHeight > 0.5f) {
                    c.render(
                            context,
                            (int) mouseX,
                            (int) mouseY,
                            delta
                    );
                }
            }
        }
    }

    public float calculateTotalHeight(
            List<AbstractSettingComponent> settingComponents,
            ModuleAnimationHandler animHandler
    ) {
        float total = 0f;
        boolean hasPrevious = false;

        for (AbstractSettingComponent c : settingComponents) {
            float heightAnim = animHandler.getHeightAnimations()
                    .getOrDefault(
                            c,
                            c.getSetting().isVisible() ? 1f : 0f
                    );

            if (heightAnim <= 0.001f) {
                continue;
            }

            if (hasPrevious) {
                total +=
                        SETTING_SPACING
                                * heightAnim;
            }

            total +=
                    getComponentBaseHeight(c)
                            * heightAnim;

            hasPrevious = true;
        }

        return total;
    }

    private float getComponentBaseHeight(
            AbstractSettingComponent c
    ) {
        if (c instanceof BezierComponent) return 88f;
        if (c instanceof HitboxPointComponent) return 88f;
        if (c instanceof MultiPointComponent) return 88f;
        if (c instanceof DividerComponent) return 8f;
        if (c instanceof SelectComponent) {
            return Math.max(SETTING_HEIGHT, ((SelectComponent) c).getTotalHeight());
        }

        if (c instanceof MultiSelectComponent) {
            return Math.max(SETTING_HEIGHT, ((MultiSelectComponent) c).getTotalHeight());
        }

        if (c instanceof ColorComponent) {
            return Math.max(SETTING_HEIGHT, ((ColorComponent) c).getTotalHeight());
        }

        return SETTING_HEIGHT;
    }

    private void renderScrollFade(
            float x,
            float y,
            float w,
            float h,
            float topFade,
            float bottomFade,
            int alpha,
            int size
    ) {
        if (topFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha =
                        alpha
                                * topFade
                                * (1f - i / (float) size);

                Render2D.rect(
                        x,
                        y + i,
                        w,
                        1,
                        ThemeColors.color(
                                20,
                                20,
                                20,
                                (int) fadeAlpha
                        ).getRGB(),
                        0
                );
            }
        }

        if (bottomFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha =
                        alpha
                                * bottomFade
                                * (i / (float) size);

                Render2D.rect(
                        x,
                        y + h - size + i,
                        w,
                        1,
                        ThemeColors.color(
                                20,
                                20,
                                20,
                                (int) fadeAlpha
                        ).getRGB(),
                        0
                );
            }
        }
    }

    private float clamp(float value) {
        return Math.max(
                0f,
                Math.min(
                        1f,
                        value
                )
        );
    }

    private float easeOutCubic(float x) {
        x = clamp(x);

        return 1f - (float) Math.pow(
                1f - x,
                3f
        );
    }

    private float easeInOutCubic(float x) {
        x = clamp(x);

        if (x < 0.5f) {
            return 4f
                    * x
                    * x
                    * x;
        }

        return 1f - (float) Math.pow(
                -2f * x + 2f,
                3f
        ) / 2f;
    }
}