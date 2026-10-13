package mind.beta.screens.clickgui.impl.module.render;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.scripting.LuaModule;
import mind.beta.screens.clickgui.impl.module.handler.ModuleAnimationHandler;
import mind.beta.screens.clickgui.impl.module.handler.ModuleBindHandler;
import mind.beta.screens.clickgui.impl.module.handler.ModuleScrollHandler;
import mind.beta.screens.clickgui.impl.module.util.ModuleDisplayHelper;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.font.Fonts;

import java.awt.*;
import mind.beta.screens.clickgui.impl.background.render.HeaderRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleListRenderer {

    private static final float MODULE_ITEM_HEIGHT = 22f;
    private static final float MODULE_STEP = MODULE_ITEM_HEIGHT + 2f;
    private final Map<ModuleStructure, Float> displayedRows = new HashMap<>();
    private final Map<ModuleStructure, Integer> previousTargets = new HashMap<>();
    private long lastSortFrameNanos = System.nanoTime();
    private boolean rowPositionsReady;
    private final Map<ModuleStructure, Float> visualHoverAnimations = new HashMap<>();
    private long lastHoverFrameNanos = System.nanoTime();

    private static final float MODULE_LIST_CORNER_RADIUS = 6f;
    private static final float CORNER_INSET = 0f;
    private static final float MODULE_LIST_X_OFFSET = -3f;
    private static final float BIND_BOX_HEIGHT = 9f;
    private static final float BIND_BOX_MIN_WIDTH = 18f;
    private static final float BIND_BOX_PADDING = 6f;
    private static final float BIND_WIDTH_ANIM_SPEED = 12f;
    private static final float SWITCH_WIDTH = 20f;
    private static final float SWITCH_HEIGHT = 10f;
    private static final float SWITCH_KNOB_SIZE = 8f;

    private final ModuleAnimationHandler animationHandler;
    private final ModuleBindHandler bindHandler;
    private final ModuleDisplayHelper displayHelper;

    private final Map<ModuleStructure, String> bindCurrentTexts = new HashMap<>();
    private final Map<ModuleStructure, String> bindPreviousTexts = new HashMap<>();
    private final Map<ModuleStructure, Long> bindTextChangeStartTimes = new HashMap<>();
    private static final float BIND_TEXT_CHANGE_DURATION = 140f;

    public ModuleListRenderer(ModuleAnimationHandler animationHandler, ModuleBindHandler bindHandler, ModuleDisplayHelper displayHelper) {
        this.animationHandler = animationHandler;
        this.bindHandler = bindHandler;
        this.displayHelper = displayHelper;
    }

    public void render(DrawContext context, List<ModuleStructure> displayModules, ModuleStructure selectedModule,
                       ModuleStructure bindingModule, float x, float y, float width, float height,
                       float mouseX, float mouseY, int guiScale, float alphaMultiplier,
                       ModuleAnimationHandler animHandler, ModuleScrollHandler scrollHandler) {

        float topInset = CORNER_INSET;
        float bottomInset = CORNER_INSET;
        float sideInset = CORNER_INSET;

        Scissor.enable(x + MODULE_LIST_X_OFFSET + sideInset, y + topInset - 1.5f, width - sideInset * 2 - MODULE_LIST_X_OFFSET, height - topInset - bottomInset + 3, guiScale);

        if (animHandler.isCategoryTransitioning() && !animHandler.getOldModules().isEmpty()) {
            float oldAlpha = (1f - animHandler.getCategoryTransitionProgress()) * alphaMultiplier;
            float oldOffsetY = animHandler.easeInCubic(animHandler.getCategoryTransitionProgress()) * -animHandler.getCategorySlideDistance();
            float oldScale = 1f - animHandler.getCategoryTransitionProgress() * 0.1f;

            renderModuleItems(context, animHandler.getOldModules(), animHandler.getOldModuleAnimations(),
                    selectedModule, bindingModule, x, y, width, height, mouseX, mouseY,
                    oldAlpha, 0f, oldOffsetY, oldScale, (float) animHandler.getOldModuleDisplayScroll(), false, topInset, bottomInset, animHandler);
        }

        float newAlpha;
        float newOffsetY;
        float newScale;

        if (animHandler.isCategoryTransitioning()) {
            float entryProgress = Math.max(0f, (animHandler.getCategoryTransitionProgress() - 0.2f) / 0.8f);
            entryProgress = animHandler.easeOutQuart(entryProgress);
            newAlpha = entryProgress * alphaMultiplier;
            newOffsetY = (1f - entryProgress) * animHandler.getCategorySlideDistance();
            newScale = 0.9f + entryProgress * 0.1f;
        } else {
            newAlpha = alphaMultiplier;
            newOffsetY = 0f;
            newScale = 1f;
        }

        List<ModuleStructure> sortedModules = sortModules(displayModules);
        updateAnimatedRows(sortedModules, animHandler.isCategoryTransitioning());
        updateVisualHover(sortedModules, x, y, width, height, mouseX, mouseY,
                (float) scrollHandler.getModuleDisplayScroll(), animHandler);
        renderModuleItems(context, sortedModules, animHandler.getModuleAnimations(),
                selectedModule, bindingModule, x, y, width, height, mouseX, mouseY,
                newAlpha, 0f, newOffsetY, newScale, (float) scrollHandler.getModuleDisplayScroll(), true, topInset, bottomInset, animHandler);

        Scissor.disable();

        renderScrollFade(x, y + topInset, width, height - topInset - bottomInset,
                scrollHandler.getModuleScrollTopFade() * alphaMultiplier,
                scrollHandler.getModuleScrollBottomFade() * alphaMultiplier, 45, 12);
    }

    public static List<ModuleStructure> sortModules(List<ModuleStructure> modules) {
        List<ModuleStructure> result = new ArrayList<>(modules);
        Comparator<ModuleStructure> alphabetical = Comparator.comparing(ModuleStructure::getName,
                String.CASE_INSENSITIVE_ORDER);
        switch (HeaderRenderer.getSortMode()) {
            case ALPHABETICAL -> result.sort(alphabetical);
            case REVERSE_ALPHABETICAL -> result.sort(alphabetical.reversed());
            case ENABLED_FIRST -> result.sort(Comparator.comparing(ModuleStructure::isState).reversed()
                    .thenComparing(alphabetical));
            case DISABLED_FIRST -> result.sort(Comparator.comparing(ModuleStructure::isState)
                    .thenComparing(alphabetical));
            default -> { }
        }
        return result;
    }

    private void updateAnimatedRows(List<ModuleStructure> modules, boolean categoryTransitioning) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0f, (now - lastSortFrameNanos) / 1_000_000_000f));
        lastSortFrameNanos = now;

        boolean listChanged = !displayedRows.keySet().containsAll(modules)
                || displayedRows.size() != modules.size();
        if (!rowPositionsReady || listChanged) {
            displayedRows.clear();
            previousTargets.clear();
            for (int i = 0; i < modules.size(); i++) {
                displayedRows.put(modules.get(i), (float) i);
                previousTargets.put(modules.get(i), i);
            }
            rowPositionsReady = true;
            return;
        }

        if (categoryTransitioning) {
            for (int i = 0; i < modules.size(); i++) {
                ModuleStructure module = modules.get(i);
                displayedRows.put(module, (float) i);
                previousTargets.put(module, i);
            }
            return;
        }

        float factor = 1f - (float) Math.exp(-13f * dt);
        for (int i = 0; i < modules.size(); i++) {
            ModuleStructure module = modules.get(i);
            float current = displayedRows.getOrDefault(module, (float) i);
            float next = current + (i - current) * factor;
            displayedRows.put(module, Math.abs(i - next) < 0.002f ? (float) i : next);
            previousTargets.put(module, i);
        }
    }

    private void updateVisualHover(List<ModuleStructure> modules, float x, float y,
                                   float width, float height, float mouseX, float mouseY,
                                   float scrollOffset, ModuleAnimationHandler animHandler) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0f, (now - lastHoverFrameNanos) / 1_000_000_000f));
        lastHoverFrameNanos = now;
        float factor = 1f - (float) Math.exp(-14f * dt);
        ModuleStructure hovered = null;
        if (!animHandler.isCategoryTransitioning()
                && mouseX >= x + MODULE_LIST_X_OFFSET && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height) {
            float nearest = Float.MAX_VALUE;
            float startY = y + CORNER_INSET + 2f + scrollOffset;
            for (int i = 0; i < modules.size(); i++) {
                ModuleStructure module = modules.get(i);
                float row = displayedRows.getOrDefault(module, (float) i);
                float position = startY + row * MODULE_STEP;
                float itemProgress = animHandler.getModuleAnimations().getOrDefault(module, 1f);
                float posProgress = animHandler.getPositionAnimations().getOrDefault(module, 1f);
                position += (1f - itemProgress) * 20f
                        + (1f - easeOutCubic(posProgress)) * 15f;
                float distance = Math.abs(mouseY - (position + MODULE_ITEM_HEIGHT / 2f));
                if (mouseY >= position && mouseY <= position + MODULE_ITEM_HEIGHT && distance < nearest) {
                    hovered = module;
                    nearest = distance;
                }
            }
        }
        visualHoverAnimations.keySet().retainAll(modules);
        for (ModuleStructure module : modules) {
            float current = visualHoverAnimations.getOrDefault(module, 0f);
            float target = module == hovered ? 1f : 0f;
            float next = current + (target - current) * factor;
            visualHoverAnimations.put(module, Math.abs(target - next) < 0.001f ? target : next);
        }
    }

    private void renderModuleItems(DrawContext context, List<ModuleStructure> moduleList, Map<ModuleStructure, Float> animations,
                                   ModuleStructure selectedModule, ModuleStructure bindingModule,
                                   float x, float y, float width, float height, float mouseX, float mouseY,
                                   float alphaMultiplier, float offsetX, float offsetY, float scale, float scrollOffset,
                                   boolean interactive, float topInset, float bottomInset, ModuleAnimationHandler animHandler) {
        if (alphaMultiplier <= 0.01f) return;

        float startY = y + topInset + 2f + scrollOffset;
        float centerY = y + height / 2f;
        float visibleTop = y + topInset;
        float visibleBottom = y + height - bottomInset;

        for (int i = 0; i < moduleList.size(); i++) {
            ModuleStructure module = moduleList.get(i);
            float row = interactive ? displayedRows.getOrDefault(module, (float) i) : i;
            float modY = startY + row * MODULE_STEP;

            if (modY + MODULE_ITEM_HEIGHT < visibleTop || modY > visibleBottom) continue;

            float itemProgress = animations.getOrDefault(module, 1f);
            float posAnim = animHandler.getPositionAnimations().getOrDefault(module, 1f);
            float alphaAnim = animHandler.getModuleAlphaAnimations().getOrDefault(module, 1f);
            float combinedAlpha = itemProgress * alphaMultiplier * alphaAnim;

            if (combinedAlpha <= 0.01f) continue;

            float itemAnimOffsetY = (1f - itemProgress) * 20f;
            float posAnimOffsetY = (1f - easeOutCubic(posAnim)) * 15f;

            float scaledModY = centerY + (modY - centerY) * scale;
            float scaledHeight = MODULE_ITEM_HEIGHT * scale;
            float scaledWidth = width * scale;
            float animX = x + MODULE_LIST_X_OFFSET + offsetX;
            float animY = scaledModY + itemAnimOffsetY + posAnimOffsetY;

            boolean selected = interactive && module == selectedModule;
            boolean isHighlighted = interactive && module == animHandler.getHighlightedModule() && animHandler.getHighlightAnimation() > 0.01f;
            float hoverAnim = interactive ? visualHoverAnimations.getOrDefault(module, 0f) : 0f;
            float stateAnim = interactive ? animHandler.getStateAnimations().getOrDefault(module, module.isState() ? 1f : 0f) : (module.isState() ? 1f : 0f);

            int baseBgAlpha = 0;
            int hoverBgAlpha = 18;
            int selectedBgAlpha = 5;

            int bgAlpha;
            Color bgColor;

            if (selected) {
                bgAlpha = (int) ((selectedBgAlpha + hoverAnim * 8) * combinedAlpha);
                bgColor = ThemeColors.color(0, 128, 255, Math.min(255, bgAlpha));
            } else {
                bgAlpha = (int) ((baseBgAlpha + (hoverBgAlpha - baseBgAlpha) * hoverAnim) * combinedAlpha);
                int r = (int) (7 + 10 * hoverAnim);
                int g = (int) (25 + 30 * hoverAnim);
                int b = (int) (43 + 52 * hoverAnim);
                bgColor = ThemeColors.color(r, g, b, Math.min(255, bgAlpha));
            }

            if (bgAlpha > 0) {
                Render2D.rect(animX, animY, scaledWidth, scaledHeight, bgColor.getRGB(), 5);
            }

            float stateTextOffset = 0f;

            if (module instanceof LuaModule) {
                String badgeText = "lua";
                float badgeTextSize = 5f * scale;
                float badgeWidth = Fonts.MEDIUM.getWidth(badgeText, badgeTextSize) + 6f * scale;
                float badgeHeight = 8f * scale;
                float badgeX = animX + 5f * scale;
                float badgeY = animY + (scaledHeight - badgeHeight) / 2f;

                Render2D.rect(
                        badgeX,
                        badgeY,
                        badgeWidth,
                        badgeHeight,
                        ThemeColors.color(0, 128, 255, Math.min(255, (int) (28 * combinedAlpha))).getRGB(),
                        2.5f * scale
                );

                Fonts.MEDIUM.draw(
                        badgeText,
                        badgeX + 3f * scale,
                        badgeY + 1.7f * scale,
                        badgeTextSize,
                        ThemeColors.color(137, 197, 255, Math.min(255, (int) (205 * combinedAlpha))).getRGB()
                );

                stateTextOffset = badgeWidth + 4f * scale;
            }

            String name = module.getName();

            int baseGray = 132;
            int targetWhite = 248;
            int textBrightness = (int) (baseGray + (targetWhite - baseGray) * stateAnim);
            int textAlphaValue = (int) ((185 + 70 * stateAnim) * combinedAlpha);

            if (hoverAnim > 0.01f && stateAnim < 0.99f) {
                textBrightness = (int) (textBrightness + (28 * hoverAnim * (1 - stateAnim)));
                textAlphaValue = (int) (textAlphaValue + (35 * hoverAnim * (1 - stateAnim)));
            }

            int textRed = Math.min(255, textBrightness);
            int textGreen = Math.min(255, textBrightness);
            int textBlue = Math.min(255, textBrightness + (int) (5 * stateAnim));

            if (isHighlighted) {
                float highlight = animHandler.getHighlightAnimation();
                textRed = (int) (textRed + (155 - textRed) * highlight);
                textGreen = (int) (textGreen + (215 - textGreen) * highlight);
                textBlue = (int) (textBlue + (255 - textBlue) * highlight);
                textAlphaValue = Math.min(255, (int) (textAlphaValue + 45f * highlight));
            }

            Color textColor = ThemeColors.color(
                    Math.max(0, Math.min(255, textRed)),
                    Math.max(0, Math.min(255, textGreen)),
                    Math.max(0, Math.min(255, textBlue)),
                    Math.min(255, textAlphaValue)
            );

            float textX = animX + 5f * scale + stateTextOffset;
            float textSize = 7f * scale;
            float textY = animY + (scaledHeight - textSize) / 2f - 1f * scale;
            Fonts.MEDIUM.draw(name, textX, textY, textSize, textColor.getRGB());

            if (interactive) {
                float switchWidth = SWITCH_WIDTH * scale;
                float switchX = animX + scaledWidth - switchWidth - 5f * scale;
                renderBindBox(module, bindingModule, animX, animY, scaledWidth, scaledHeight, scale, combinedAlpha, stateTextOffset, switchX, animHandler);
                renderSwitch(module, animX, animY, scaledWidth, scaledHeight, scale, stateAnim, hoverAnim, combinedAlpha);
            }
        }
    }

    private void renderSwitch(ModuleStructure module, float moduleX, float moduleY, float moduleWidth, float moduleHeight,
                              float scale, float stateAnim, float hoverAnim, float combinedAlpha) {
        float switchWidth = SWITCH_WIDTH * scale;
        float switchHeight = SWITCH_HEIGHT * scale;
        float knobSize = SWITCH_KNOB_SIZE * scale;

        float switchX = moduleX + moduleWidth - switchWidth - 5f * scale;
        float switchY = moduleY + (moduleHeight - switchHeight) / 2f;

        float progress = Math.max(0f, Math.min(1f, stateAnim));

        int offR = 7;
        int offG = 24;
        int offB = 41;
        int onR = 0;
        int onG = 128;
        int onB = 255;

        int trackR = (int) (offR + (onR - offR) * progress);
        int trackG = (int) (offG + (onG - offG) * progress);
        int trackB = (int) (offB + (onB - offB) * progress);
        int trackAlpha = (int) ((85 + 35 * hoverAnim + 45 * progress) * combinedAlpha);

        Render2D.rect(
                switchX,
                switchY,
                switchWidth,
                switchHeight,
                ThemeColors.isLight()
                        ? ThemeColors.switchTrack(progress, hoverAnim, Math.min(255, (int) (combinedAlpha * 255f)))
                        : ThemeColors.color(trackR, trackG, trackB, Math.min(255, trackAlpha)).getRGB(),
                switchHeight / 2f
        );

        float knobTravel = switchWidth - knobSize - 2f * scale;
        float knobX = switchX + scale + knobTravel * progress;
        float knobY = switchY + (switchHeight - knobSize) / 2f;

        int knobR = (int) (111 + 121 * progress);
        int knobG = (int) (137 + 106 * progress);
        int knobB = (int) (161 + 94 * progress);
        int knobAlpha = (int) ((175 + 80 * progress) * combinedAlpha);

        Render2D.rect(
                knobX,
                knobY,
                knobSize,
                knobSize,
                ThemeColors.isLight()
                        ? ThemeColors.switchKnob(progress, Math.min(255, knobAlpha))
                        : ThemeColors.color(knobR, knobG, knobB, Math.min(255, knobAlpha)).getRGB(),
                knobSize / 2f
        );
    }

    private void renderBindBox(ModuleStructure module, ModuleStructure bindingModule, float moduleX, float moduleY,
                               float moduleWidth, float moduleHeight, float scale, float combinedAlpha,
                               float stateTextOffset, float switchX, ModuleAnimationHandler animHandler) {
        boolean isBinding = module == bindingModule;
        int key = module.getKey();

        float bindAlpha = animHandler.getBindBoxAlphaAnimations().getOrDefault(module, 0f);

        if (bindAlpha <= 0.01f && !isBinding && (key == GLFW.GLFW_KEY_UNKNOWN || key == -1)) {
            return;
        }

        String bindText = isBinding ? "..." : bindHandler.getBindDisplayName(key);
        updateBindTextState(module, bindText);

        float textWidth = Fonts.MEDIUM.getWidth(bindText, 5 * scale);
        float targetWidth = Math.max(BIND_BOX_MIN_WIDTH, textWidth + BIND_BOX_PADDING * 2);

        float currentWidth = animHandler.getBindBoxWidthAnimations().getOrDefault(module, targetWidth);

        float widthDiff = targetWidth - currentWidth;
        if (Math.abs(widthDiff) > 0.1f) {
            currentWidth += widthDiff * BIND_WIDTH_ANIM_SPEED * 0.016f;
            animHandler.getBindBoxWidthAnimations().put(module, currentWidth);
        } else {
            currentWidth = targetWidth;
            animHandler.getBindBoxWidthAnimations().put(module, currentWidth);
        }

        float boxHeight = BIND_BOX_HEIGHT * scale;
        float targetBoxWidth = currentWidth * scale;
        float gap = 4f * scale;
        float boxRight = switchX - gap;
        float boxX = boxRight - targetBoxWidth;

        float nameRight = moduleX + 5f * scale + stateTextOffset
                + Fonts.BOLD.getWidth(module.getName(), 7f * scale);
        float minBoxX = nameRight + gap;

        if (boxX < minBoxX) {
            boxX = minBoxX;
            targetBoxWidth = boxRight - boxX;
        }

        if (targetBoxWidth <= 1f) {
            return;
        }

        float boxY = moduleY + (moduleHeight - boxHeight) / 2f + 0.5f * scale;
        float finalAlpha = combinedAlpha * bindAlpha;
        int bgAlpha = (int) (32 * finalAlpha);

        if (bgAlpha > 0) {
            Render2D.rect(
                    boxX,
                    boxY,
                    targetBoxWidth,
                    boxHeight,
                    ThemeColors.color(8, 32, 56, Math.min(255, bgAlpha)).getRGB(),
                    3f * scale
            );
        }

        if (bindAlpha > 0.5f) {
            renderAnimatedBindText(
                    module,
                    bindText,
                    boxX,
                    boxY,
                    targetBoxWidth,
                    boxHeight,
                    scale,
                    finalAlpha
            );
        }
    }

    private void updateBindTextState(ModuleStructure module, String newText) {
        String current = bindCurrentTexts.get(module);

        if (current == null) {
            bindCurrentTexts.put(module, newText);
            bindPreviousTexts.put(module, newText);
            bindTextChangeStartTimes.put(module, 0L);
            return;
        }

        if (!current.equals(newText)) {
            bindPreviousTexts.put(module, current);
            bindCurrentTexts.put(module, newText);
            bindTextChangeStartTimes.put(module, System.currentTimeMillis());
        }
    }

    private void renderAnimatedBindText(
            ModuleStructure module,
            String fallbackText,
            float boxX,
            float boxY,
            float boxWidth,
            float boxHeight,
            float scale,
            float finalAlpha
    ) {
        String current = bindCurrentTexts.getOrDefault(module, fallbackText);
        String previous = bindPreviousTexts.getOrDefault(module, current);
        long start = bindTextChangeStartTimes.getOrDefault(module, 0L);

        float progress = 1f;
        if (start > 0L) {
            progress = Math.min(1f, (System.currentTimeMillis() - start) / BIND_TEXT_CHANGE_DURATION);
            progress = easeOutCubic(progress);
        }

        int baseAlpha = Math.min(255, (int) (175 * finalAlpha));
        float textY = boxY + (boxHeight - 5f * scale) / 2f;
        float centerX = boxX + boxWidth / 2f;

        if (progress < 0.999f && !previous.equals(current)) {
            float oldAlpha = 1f - progress;
            float newAlpha = progress;
            float oldOffsetY = -3f * scale * progress;
            float newOffsetY = 3f * scale * (1f - progress);

            if (oldAlpha > 0.01f) {
                float oldWidth = Fonts.BOLD.getWidth(previous, 5f * scale);
                int oldTextAlpha = Math.max(0, Math.min(255, (int) (baseAlpha * oldAlpha)));
                Fonts.BOLD.draw(
                        previous,
                        centerX - oldWidth / 2f,
                        textY + oldOffsetY,
                        5f * scale,
                        ThemeColors.color(176, 207, 238, oldTextAlpha).getRGB()
                );
            }

            float newWidth = Fonts.BOLD.getWidth(current, 5f * scale);
            int newTextAlpha = Math.max(0, Math.min(255, (int) (baseAlpha * newAlpha)));
            Fonts.BOLD.draw(
                    current,
                    centerX - newWidth / 2f,
                    textY + newOffsetY,
                    5f * scale,
                    ThemeColors.color(176, 207, 238, newTextAlpha).getRGB()
            );
        } else {
            float currentWidth = Fonts.BOLD.getWidth(current, 5f * scale);
            Fonts.BOLD.draw(
                    current,
                    centerX - currentWidth / 2f,
                    textY,
                    5f * scale,
                    ThemeColors.color(176, 207, 238, baseAlpha).getRGB()
            );
        }
    }

    private void renderScrollFade(float x, float y, float w, float h, float topFade, float bottomFade, int alpha, int size) {
        if (topFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha = alpha * topFade * (1f - i / (float) size);
                Render2D.rect(x, y + i, w, 1, ThemeColors.color(4, 19, 34, (int) fadeAlpha).getRGB(), 0);
            }
        }
        if (bottomFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha = alpha * bottomFade * (i / (float) size);
                Render2D.rect(x, y + h - size + i, w, 1, ThemeColors.color(4, 19, 34, (int) fadeAlpha).getRGB(), 0);
            }
        }
    }

    public ModuleStructure getModuleAtPosition(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                               float listX, float listY, float listWidth, float listHeight,
                                               double scrollOffset, boolean isTransitioning) {
        if (isTransitioning) return null;
        float hitListX = listX + MODULE_LIST_X_OFFSET;
        float hitListWidth = listWidth - MODULE_LIST_X_OFFSET;
        if (mouseX < hitListX || mouseX > hitListX + hitListWidth || mouseY < listY || mouseY > listY + listHeight) return null;

        List<ModuleStructure> sortedModules = sortModules(displayModules);
        float startY = listY + CORNER_INSET + 2f + (float) scrollOffset;
        for (int i = sortedModules.size() - 1; i >= 0; i--) {
            ModuleStructure module = sortedModules.get(i);
            float modY = startY + displayedRows.getOrDefault(module, (float) i) * MODULE_STEP;
            if (mouseX >= hitListX && mouseX <= hitListX + hitListWidth && mouseY >= modY && mouseY <= modY + MODULE_ITEM_HEIGHT) {
                return module;
            }
        }
        return null;
    }

    public boolean isStarClicked(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                 float listX, float listY, float listWidth, float listHeight,
                                 double scrollOffset, ModuleDisplayHelper displayHelper, boolean isTransitioning) {
        return false;
    }

    public ModuleStructure getModuleForStarClick(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                                 float listX, float listY, float listWidth, float listHeight,
                                                 double scrollOffset, ModuleDisplayHelper displayHelper, boolean isTransitioning) {
        return null;
    }

    private float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1 - x, 3);
    }
}
