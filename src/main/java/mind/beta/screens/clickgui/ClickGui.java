package mind.beta.screens.clickgui;

import mind.beta.util.render.theme.ThemeColors;
import mind.beta.util.modules.ModuleBindings;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import mind.beta.IMinecraft;
import mind.beta.Initialization;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.screens.clickgui.impl.DragHandler;
import mind.beta.screens.clickgui.impl.background.BackgroundComponent;
import mind.beta.screens.clickgui.impl.background.render.HeaderRenderer;
import mind.beta.screens.clickgui.impl.module.render.ModuleListRenderer;
import mind.beta.screens.clickgui.impl.configs.ConfigsRenderer;
import mind.beta.screens.clickgui.impl.lua.LuaRenderer;
import mind.beta.screens.clickgui.impl.theme.ThemeRenderer;
import mind.beta.screens.clickgui.impl.module.ModuleComponent;
import mind.beta.screens.clickgui.impl.settingsrender.BindComponent;
import mind.beta.screens.clickgui.impl.settingsrender.TextComponent;
import mind.beta.util.interfaces.AbstractSettingComponent;
import mind.beta.util.animations.Direction;
import mind.beta.util.animations.GuiAnimation;
import mind.beta.util.math.FrameRateCounter;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.GuiTransform;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.pipeline.BlurPipeline;
import mind.beta.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ClickGui extends Screen implements IMinecraft {

    public static ClickGui INSTANCE = new ClickGui();

    private static final int FIXED_GUI_SCALE = 2;

    private final BackgroundComponent background = new BackgroundComponent();
    private final ModuleComponent moduleComponent = new ModuleComponent();
    private final ConfigsRenderer configsRenderer = new ConfigsRenderer();
    private final LuaRenderer luaRenderer = new LuaRenderer();
    private final ThemeRenderer themeRenderer = new ThemeRenderer();
    private final DragHandler dragHandler = new DragHandler();
    private final BlurPipeline blurPipeline = new BlurPipeline();

    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;

    private final GuiAnimation openAnimation = new GuiAnimation();

    private boolean closing = false;

    private float hintAlphaAnimation = 0f;
    private float bindOverlayAlpha = 0f;
    private long bindOverlayLastTime = System.nanoTime();

    private static final float HINT_ANIM_SPEED = 6f;
    private static final float OFFSET_THRESHOLD = 5f;
    private static final float BIND_OVERLAY_SPEED = 12f;
    private static final float BIND_OVERLAY_BLUR = 7f;

    private int lastMouseX;
    private int lastMouseY;
    private float lastDelta;

    public ClickGui() {
        super(Text.of("MenuScreen"));
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void init() {
        super.init();

        closing = false;

        openAnimation
                .setMs(320)
                .setValue(1.0)
                .setDirection(Direction.FORWARDS)
                .reset();

        hintAlphaAnimation = 0f;
        bindOverlayAlpha = 0f;
        bindOverlayLastTime = System.nanoTime();

        long handle = mc.getWindow().getHandle();

        GLFW.glfwSetInputMode(
                handle,
                GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_NORMAL
        );

        double centerX =
                mc.getWindow().getWidth() / 2.0;

        double centerY =
                mc.getWindow().getHeight() / 2.0;

        GLFW.glfwSetCursorPos(
                handle,
                centerX,
                centerY
        );

        background.setSearchActive(false);

        themeRenderer.reset(selectedCategory == ModuleCategory.THEME);
        configsRenderer.reset(selectedCategory == ModuleCategory.CONFIG);
        luaRenderer.reset(selectedCategory == ModuleCategory.LUA);
        mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.close();
        updateModules();
    }

    private void updateModules() {
        List<ModuleStructure> modules =
                new ArrayList<>();

        try {
            var repo =
                    Initialization
                            .getInstance()
                            .getManager()
                            .getModuleRepository();

            if (repo != null) {
                for (ModuleStructure m : repo.modules()) {
                    if (m.getCategory() == selectedCategory) {
                        modules.add(m);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        moduleComponent.updateModules(
                ModuleListRenderer.sortModules(modules),
                selectedCategory
        );
    }

    public void openGui() {
        if (mc.currentScreen == null) {
            closing = false;

            openAnimation
                    .setMs(320)
                    .setValue(1.0)
                    .setDirection(Direction.FORWARDS)
                    .reset();

            mc.setScreen(this);
        }
    }

    @Override
    public void tick() {
        moduleComponent.tick();
        super.tick();
    }

    private float[] calculateBackground(float scale) {
        int vw =
                mc.getWindow().getWidth()
                        / FIXED_GUI_SCALE;

        int vh =
                mc.getWindow().getHeight()
                        / FIXED_GUI_SCALE;

        float bgX =
                (vw - BackgroundComponent.BG_WIDTH)
                        / 2f
                        + dragHandler.getOffsetX();

        float bgY =
                (vh - BackgroundComponent.BG_HEIGHT)
                        / 2f
                        + dragHandler.getOffsetY();

        return new float[]{
                bgX,
                bgY,
                vw,
                vh
        };
    }

    private boolean isAnyBindListening() {
        for (
                AbstractSettingComponent c :
                moduleComponent.getSettingComponents()
        ) {
            if (
                    c instanceof BindComponent bindComponent
                            && bindComponent.isListening()
            ) {
                return true;
            }
        }

        return false;
    }

    private void updateHintAnimation(
            float deltaTime
    ) {
        float offsetX =
                Math.abs(
                        dragHandler.getOffsetX()
                );

        float offsetY =
                Math.abs(
                        dragHandler.getOffsetY()
                );

        boolean shouldShow =
                offsetX > OFFSET_THRESHOLD
                        || offsetY > OFFSET_THRESHOLD;

        float target =
                shouldShow
                        ? 1f
                        : 0f;

        float diff =
                target - hintAlphaAnimation;

        if (Math.abs(diff) < 0.001f) {
            hintAlphaAnimation = target;
        } else {
            float factor =
                    1f - (float) Math.exp(
                            -HINT_ANIM_SPEED
                                    * deltaTime
                    );

            hintAlphaAnimation +=
                    diff * factor;

            hintAlphaAnimation =
                    Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    hintAlphaAnimation
                            )
                    );
        }
    }

    private void updateBindOverlay(
            float ignoredDelta
    ) {
        long now = System.nanoTime();

        float deltaTime =
                Math.min(
                        (now - bindOverlayLastTime)
                                / 1_000_000_000f,
                        0.05f
                );

        bindOverlayLastTime = now;

        float target =
                moduleComponent.getBindingModule() != null
                        ? 1f
                        : 0f;

        float step =
                BIND_OVERLAY_SPEED
                        * deltaTime;

        if (Math.abs(target - bindOverlayAlpha) <= step) {
            bindOverlayAlpha = target;
        } else {
            bindOverlayAlpha +=
                    Math.signum(target - bindOverlayAlpha)
                            * step;
        }

        bindOverlayAlpha =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                bindOverlayAlpha
                        )
                );
    }

    private void renderBindOverlay(
            float bgX,
            float bgY,
            float alphaMultiplier
    ) {
        if (bindOverlayAlpha <= 0.001f) {
            return;
        }

        float linearProgress =
                bindOverlayAlpha;

        float textProgress =
                easeOutCubic(bindOverlayAlpha);

        float alpha =
                linearProgress
                        * Math.max(
                        alphaMultiplier,
                        0.75f
                );

        Render2D.blur(
                bgX,
                bgY,
                BackgroundComponent.BG_WIDTH,
                BackgroundComponent.BG_HEIGHT,
                BIND_OVERLAY_BLUR * linearProgress,
                8f,
                ThemeColors.color(0, 0, 0, 100).getRGB()
        );

        if (!ThemeColors.isLight()) {
            Render2D.rect(
                    bgX,
                    bgY,
                    BackgroundComponent.BG_WIDTH,
                    BackgroundComponent.BG_HEIGHT,
                    ThemeColors.dim(Math.min(255, (int) (34f * alpha))),
                    8f
            );
        }

        float centerX =
                bgX
                        + BackgroundComponent.BG_WIDTH
                        / 2f;

        float centerY =
                bgY
                        + BackgroundComponent.BG_HEIGHT
                        / 2f;

        String title = "Press a key";
        float titleSize = 11f;

        float titleWidth =
                Fonts.MEDIUM.getWidth(
                        title,
                        titleSize
                );

        Fonts.MEDIUM.draw(
                title,
                centerX - titleWidth / 2f,
                centerY - 13f,
                titleSize,
                ThemeColors.color(
                        245,
                        247,
                        250,
                        Math.min(
                                255,
                                (int) (255f * alpha * textProgress)
                        )
                ).getRGB()
        );

        String description =
                "Esc to exit | Backspace to remove";

        float descriptionSize = 6.5f;

        float descriptionWidth =
                Fonts.MEDIUM.getWidth(
                        description,
                        descriptionSize
                );

        Fonts.MEDIUM.draw(
                description,
                centerX - descriptionWidth / 2f,
                centerY + 7f,
                descriptionSize,
                ThemeColors.color(
                        155,
                        170,
                        188,
                        Math.min(
                                255,
                                (int) (190f * alpha * textProgress)
                        )
                ).getRGB()
        );
    }

    private float easeOutCubic(float value) {
        float t = Math.max(
                0f,
                Math.min(
                        1f,
                        value
                )
        );

        float inv = 1f - t;

        return 1f - inv * inv * inv;
    }

    private boolean isModuleCategory(
            ModuleCategory category
    ) {
        return category == ModuleCategory.COMBAT
                || category == ModuleCategory.MOVEMENT
                || category == ModuleCategory.RENDER
                || category == ModuleCategory.PLAYER
                || category == ModuleCategory.MISC;
    }

    @Override
    public void render(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;

        FrameRateCounter.INSTANCE.recordFrame();

        if (
                closing
                        && openAnimation.isFinished(
                        Direction.BACKWARDS
                )
        ) {
            closing = false;

            TextComponent.typing = false;

            moduleComponent.setBindingModule(
                    null
            );

            dragHandler.stopDrag();

            long handle =
                    mc.getWindow().getHandle();

            GLFW.glfwSetInputMode(
                    handle,
                    GLFW.GLFW_CURSOR,
                    GLFW.GLFW_CURSOR_DISABLED
            );

            mc.setScreen(null);

            return;
        }

        super.render(
                context,
                mouseX,
                mouseY,
                delta
        );
    }

    public void renderOverlay(
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        if (mc.getWindow() == null) {
            return;
        }

        float delta = lastDelta;

        int mouseX = lastMouseX;
        int mouseY = lastMouseY;

        float scrollSpeed =
                Math.min(
                        1f,
                        60f / Math.max(
                                FrameRateCounter.INSTANCE.getFps(),
                                1
                        )
                );

        float currentScale =
                openAnimation
                        .getOutput()
                        .floatValue();

        float alphaMultiplier =
                currentScale;

        int guiScale =
                mc.getWindow().calculateScaleFactor(
                        mc.options.getGuiScale().getValue(),
                        mc.forcesUnicodeFont()
                );

        float scale =
                (float) FIXED_GUI_SCALE
                        / guiScale;

        float[] bg =
                calculateBackground(scale);

        float bgX = bg[0];
        float bgY = bg[1];

        float guiCenterX =
                bgX
                        + BackgroundComponent.BG_WIDTH
                        * 0.5f;

        float guiCenterY =
                bgY
                        + BackgroundComponent.BG_HEIGHT
                        * 0.5f;

        float safeScale =
                Math.max(
                        currentScale,
                        0.001f
                );

        float mx =
                (float) transformMouseX(
                        mouseX,
                        scale,
                        safeScale,
                        guiCenterX
                );

        float my =
                (float) transformMouseY(
                        mouseY,
                        scale,
                        safeScale,
                        guiCenterY
                );

        if (!closing) {
            dragHandler.update(
                    mx,
                    my
            );
        }

        updateHintAnimation(delta);
        updateBindOverlay(delta);

        context.getMatrices().pushMatrix();

        context.getMatrices().scale(
                scale,
                scale
        );

        int dimAlpha =
                (int) (
                        92
                                * alphaMultiplier
                );

        if (dimAlpha > 0) {
            Render2D.rect(
                    0,
                    0,
                    5000,
                    5000,
                    ThemeColors.dim(dimAlpha),
                    0
            );
        }

        context.getMatrices().pushMatrix();

        context.getMatrices().translate(
                guiCenterX,
                guiCenterY
        );

        context.getMatrices().scale(
                currentScale,
                currentScale
        );

        context.getMatrices().translate(
                -guiCenterX,
                -guiCenterY
        );

        GuiTransform.begin(
                currentScale,
                guiCenterX,
                guiCenterY
        );

        try {

            if (alphaMultiplier > 0.001f) {

                background.renderBackgroundImage(
                        bgX,
                        bgY,
                        alphaMultiplier
                );

                blurPipeline.drawBlur(
                        bgX,
                        bgY,
                        BackgroundComponent.BG_WIDTH,
                        BackgroundComponent.BG_HEIGHT,
                        50.0f,
                        new float[]{
                                10f,
                                10f,
                                10f,
                                10f
                        },
                        ThemeColors.color(
                                32,
                                32,
                                32,
                                15
                        ).getRGB()
                );
            }

            background.render(
                    context,
                    bgX,
                    bgY,
                    selectedCategory,
                    delta,
                    alphaMultiplier
            );

            if (selectedCategory == ModuleCategory.COMBAT
                    && moduleComponent.getSelectedModule() instanceof mind.beta.modules.impl.combat.Aura aura
                    && aura.isCustomRotationSelected()) {
                aura.syncCustomRotationSettings();
            }

            background.renderCategoryPanel(
                    bgX,
                    bgY,
                    alphaMultiplier
            );

            background.renderHeader(
                    bgX,
                    bgY,
                    selectedCategory,
                    alphaMultiplier
            );

            background.renderCategoryNames(
                    context,
                    bgX,
                    bgY,
                    selectedCategory,
                    alphaMultiplier
            );

            float mlX =
                    bgX + 98f;

            float mlY =
                    bgY + 38f;

            float mlW =
                    120f;

            float mlH =
                    BackgroundComponent.BG_HEIGHT
                            - 46f;

            float spX =
                    bgX + 216f;

            float spY =
                    bgY + 38f;

            float spW =
                    174f;

            float spH =
                    BackgroundComponent.BG_HEIGHT
                            - 46f;

            float normalAlpha =
                    background.getNormalPanelAlpha();

            float searchAlpha =
                    background.getSearchPanelAlpha();

            themeRenderer.setSelected(selectedCategory == ModuleCategory.THEME);
            configsRenderer.setSelected(selectedCategory == ModuleCategory.CONFIG);
            luaRenderer.setSelected(selectedCategory == ModuleCategory.LUA);
            if (normalAlpha > 0.01f) {
                themeRenderer.render(context, bgX, bgY, alphaMultiplier * normalAlpha);

                configsRenderer.render(
                        context,
                        bgX,
                        bgY,
                        mx,
                        my,
                        delta,
                        FIXED_GUI_SCALE,
                        alphaMultiplier
                                * normalAlpha,
                        selectedCategory
                );

                luaRenderer.render(
                        context,
                        bgX,
                        bgY,
                        mx,
                        my,
                        delta,
                        alphaMultiplier * normalAlpha
                );

                boolean shouldRenderModules =
                        isModuleCategory(
                                selectedCategory
                        );

                if (shouldRenderModules) {

                    moduleComponent.updateScroll(
                            delta,
                            scrollSpeed
                    );

                    moduleComponent.updateScrollFades(
                            delta,
                            scrollSpeed,
                            mlH,
                            spH
                    );

                    moduleComponent.renderModuleList(
                            context,
                            mlX,
                            mlY,
                            mlW,
                            mlH,
                            mx,
                            my,
                            FIXED_GUI_SCALE,
                            alphaMultiplier
                                    * normalAlpha
                    );

                    moduleComponent.renderSettingsPanel(
                            context,
                            spX,
                            spY,
                            spW,
                            spH,
                            mx,
                            my,
                            delta,
                            FIXED_GUI_SCALE,
                            alphaMultiplier
                                    * normalAlpha
                    );

                    Render2D.rect(
                            spX + 2.5f,
                            bgY + 42f,
                            0.7f,
                            BackgroundComponent.BG_HEIGHT - 48f,
                            ThemeColors.color(
                                    0,
                                    128,
                                    255,
                                    (int) (
                                            15f
                                                    * alphaMultiplier
                                                    * normalAlpha
                                    )
                            ).getRGB(),
                            0f
                    );
                }

            }

            if (searchAlpha > 0.01f) {
                background.renderSearchResults(
                        context,
                        bgX,
                        bgY,
                        mx,
                        my,
                        FIXED_GUI_SCALE,
                        alphaMultiplier
                );
            }

            Scissor.reset();
            mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.render(context, (int) mx, (int) my, delta);

            renderBindOverlay(
                    bgX,
                    bgY,
                    alphaMultiplier
            );

        } finally {

            GuiTransform.end();

            context.getMatrices().popMatrix();

            context.getMatrices().popMatrix();
        }
    }

    private double transformMouseX(
            double mouseX,
            float scale,
            float animationScale,
            float centerX
    ) {
        float x =
                (float) (
                        mouseX / scale
                );

        return centerX
                + (
                x - centerX
        ) / Math.max(
                animationScale,
                0.001f
        );
    }

    private double transformMouseY(
            double mouseY,
            float scale,
            float animationScale,
            float centerY
    ) {
        float y =
                (float) (
                        mouseY / scale
                );

        return centerY
                + (
                y - centerY
        ) / Math.max(
                animationScale,
                0.001f
        );
    }

    @Override
    public boolean mouseClicked(
            Click click,
            boolean doubled
    ) {
        if (closing) {
            return false;
        }

        if (moduleComponent.getBindingModule() != null) {
            return true;
        }

        int guiScale =
                mc.getWindow().calculateScaleFactor(
                        mc.options.getGuiScale().getValue(),
                        mc.forcesUnicodeFont()
                );

        float scale =
                (float) FIXED_GUI_SCALE
                        / guiScale;

        float currentScale =
                openAnimation
                        .getOutput()
                        .floatValue();

        float[] bg =
                calculateBackground(scale);

        float bgX = bg[0];
        float bgY = bg[1];

        float guiCenterX =
                bgX
                        + BackgroundComponent.BG_WIDTH
                        * 0.5f;

        float guiCenterY =
                bgY
                        + BackgroundComponent.BG_HEIGHT
                        * 0.5f;

        double mx =
                transformMouseX(
                        click.x(),
                        scale,
                        Math.max(
                                currentScale,
                                0.001f
                        ),
                        guiCenterX
                );

        double my =
                transformMouseY(
                        click.y(),
                        scale,
                        Math.max(
                                currentScale,
                                0.001f
                        ),
                        guiCenterY
                );

        if (mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.isOpen()
                && mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.click(mx, my, click.button())) {
            return true;
        }

        if (
                background.isSearchBoxHovered(
                        mx,
                        my,
                        bgX,
                        bgY
                )
                        && click.button() == 0
        ) {
            background.setSearchActive(true);
            return true;
        }

        if (background.isSearchActive()) {

            if (click.button() == 0) {

                ModuleStructure searchModule =
                        background
                                .getSearchModuleAtPosition(
                                        mx,
                                        my,
                                        bgX,
                                        bgY
                                );

                if (searchModule != null) {
                    searchModule.switchState();
                    return true;
                }

                float panelX =
                        bgX + 70f;

                float panelY =
                        bgY + 38f;

                float panelW =
                        BackgroundComponent.BG_WIDTH
                                - 78f;

                float panelH =
                        BackgroundComponent.BG_HEIGHT
                                - 46f;

                if (
                        mx >= panelX
                                && mx <= panelX + panelW
                                && my >= panelY
                                && my <= panelY + panelH
                ) {
                    return true;
                }

                if (
                        !background.isSearchBoxHovered(
                                mx,
                                my,
                                bgX,
                                bgY
                        )
                ) {
                    background.setSearchActive(false);
                }

            } else if (click.button() == 1) {

                ModuleStructure searchModule =
                        background
                                .getSearchModuleAtPosition(
                                        mx,
                                        my,
                                        bgX,
                                        bgY
                                );

                if (searchModule != null) {
                    focusSearchModule(searchModule);
                    return true;
                }
            }

            return true;
        }

        if (isModuleCategory(selectedCategory) && HeaderRenderer.mouseClicked(mx, my, click.button())) {
            return true;
        }

        if (selectedCategory == ModuleCategory.LUA) {
            if (luaRenderer.mouseClicked(mx, my, click.button(), bgX, bgY)) {
                return true;
            }
        }

        if (selectedCategory == ModuleCategory.CONFIG
                && configsRenderer.mouseClicked(mx, my, click.button(), bgX, bgY, selectedCategory)) {
            return true;
        }

        if (selectedCategory == ModuleCategory.THEME
                && themeRenderer.mouseClicked(mx, my, click.button(), bgX, bgY)) {
            return true;
        }

        float mlX =
                bgX + 98f;

        float mlY =
                bgY + 38f;

        float mlW =
                120f;

        float mlH =
                BackgroundComponent.BG_HEIGHT
                        - 48f;

        if (click.button() == 2) {

            if (isAnyBindListening()) {

                for (
                        AbstractSettingComponent c :
                        moduleComponent
                                .getSettingComponents()
                ) {

                    if (
                            c instanceof BindComponent
                                    bindComponent
                                    && bindComponent
                                    .isListening()
                    ) {

                        bindComponent
                                .handleMiddleMouseBind();

                        return true;
                    }
                }
            }

            if (
                    moduleComponent
                            .getBindingModule()
                            != null
            ) {
                return true;
            }

            ModuleStructure module =
                    moduleComponent
                            .getModuleAtPosition(
                                    mx,
                                    my,
                                    mlX,
                                    mlY,
                                    mlW,
                                    mlH
                            );

            if (module != null) {

                moduleComponent
                        .setBindingModule(
                                module
                        );

                return true;
            }

            if (
                    dragHandler.startDrag(
                            mx,
                            my,
                            bgX,
                            bgY,
                            BackgroundComponent.BG_WIDTH,
                            BackgroundComponent.BG_HEIGHT
                    )
            ) {
                return true;
            }
        }

        ModuleCategory cat =
                background.getCategoryAtPosition(
                        mx,
                        my,
                        bgX,
                        bgY
                );
        if (cat == ModuleCategory.THEME && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            mind.beta.util.render.theme.BackgroundMode.toggle();
            return true;
        }

        if (cat != null) {

            HeaderRenderer.closeSortMenu();
            selectedCategory = cat;
            themeRenderer.setSelected(cat == ModuleCategory.THEME);
            configsRenderer.setSelected(cat == ModuleCategory.CONFIG);
            luaRenderer.setSelected(cat == ModuleCategory.LUA);

            updateModules();

            return true;
        }

        if (isModuleCategory(
                selectedCategory
        )) {

            ModuleStructure starModule =
                    moduleComponent
                            .getModuleForStarClick(
                                    mx,
                                    my,
                                    mlX,
                                    mlY,
                                    mlW,
                                    mlH
                            );

            if (
                    starModule != null
                            && click.button() == 0
            ) {

                moduleComponent
                        .toggleFavorite(
                                starModule
                        );

                return true;
            }

            ModuleStructure module =
                    moduleComponent
                            .getModuleAtPosition(
                                    mx,
                                    my,
                                    mlX,
                                    mlY,
                                    mlW,
                                    mlH
                            );

            if (module != null) {

                if (click.button() == 0) {
                    module.switchState();
                } else if (click.button() == 1) {
                    moduleComponent
                            .selectModule(
                                    module
                            );
                }

                return true;
            }

            float spX =
                    bgX + 216f;

            float spY =
                    bgY + 38f;

            float spW =
                    174f;

            float spH =
                    BackgroundComponent.BG_HEIGHT
                            - 48f;

            if (
                    mx >= spX
                            && mx <= spX + spW
                            && my >= spY
                            && my <= spY + spH
            ) {

                for (
                        AbstractSettingComponent c :
                        moduleComponent
                                .getSettingComponents()
                ) {

                    if (
                            c.getSetting().isVisible()
                                    && c.mouseClicked(
                                    mx,
                                    my,
                                    click.button()
                            )
                    ) {
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(
                click,
                doubled
        );
    }

    @Override
    public boolean mouseReleased(
            Click click
    ) {
        if (closing) {
            return false;
        }

        if (moduleComponent.getBindingModule() != null) {
            return true;
        }

        int guiScale =
                mc.getWindow().calculateScaleFactor(
                        mc.options.getGuiScale().getValue(),
                        mc.forcesUnicodeFont()
                );

        float scale =
                (float) FIXED_GUI_SCALE
                        / guiScale;

        float currentScale =
                Math.max(
                        openAnimation
                                .getOutput()
                                .floatValue(),
                        0.001f
                );

        float[] bg =
                calculateBackground(scale);

        float bgX = bg[0];
        float bgY = bg[1];

        float guiCenterX =
                bgX
                        + BackgroundComponent.BG_WIDTH
                        * 0.5f;

        float guiCenterY =
                bgY
                        + BackgroundComponent.BG_HEIGHT
                        * 0.5f;

        double mx =
                transformMouseX(
                        click.x(),
                        scale,
                        currentScale,
                        guiCenterX
                );

        double my =
                transformMouseY(
                        click.y(),
                        scale,
                        currentScale,
                        guiCenterY
                );

        configsRenderer.mouseReleased(mx, my, click.button());
        luaRenderer.mouseReleased();

        for (
                AbstractSettingComponent c :
                moduleComponent
                        .getSettingComponents()
        ) {

            if (
                    c.getSetting().isVisible()
                            && c.mouseReleased(
                            mx,
                            my,
                            click.button()
                    )
            ) {
                return true;
            }
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontal,
            double vertical
    ) {
        if (mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.isOpen()) {
            int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
            float scale = (float) FIXED_GUI_SCALE / guiScale;
            float[] bg = calculateBackground(scale);
            double mx = transformMouseX(mouseX, scale, Math.max(openAnimation.getOutput().floatValue(), .001f), bg[0] + BackgroundComponent.BG_WIDTH * .5f);
            double my = transformMouseY(mouseY, scale, Math.max(openAnimation.getOutput().floatValue(), .001f), bg[1] + BackgroundComponent.BG_HEIGHT * .5f);
            return mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.scroll(mx, my, vertical);
        }
        if (closing) {
            return false;
        }

        if (isAnyBindListening()) {

            for (
                    AbstractSettingComponent c :
                    moduleComponent
                            .getSettingComponents()
            ) {

                if (
                        c instanceof BindComponent
                                bindComponent
                                && bindComponent
                                .isListening()
                ) {

                    bindComponent.handleScrollBind(
                            vertical
                    );

                    return true;
                }
            }
        }

        if (
                moduleComponent
                        .getBindingModule()
                        != null
        ) {
            return true;
        }

        int guiScale =
                mc.getWindow().calculateScaleFactor(
                        mc.options.getGuiScale().getValue(),
                        mc.forcesUnicodeFont()
                );

        float scale =
                (float) FIXED_GUI_SCALE
                        / guiScale;

        float currentScale =
                openAnimation
                        .getOutput()
                        .floatValue();

        float[] bg =
                calculateBackground(scale);

        float bgX = bg[0];
        float bgY = bg[1];

        float guiCenterX =
                bgX
                        + BackgroundComponent.BG_WIDTH
                        * 0.5f;

        float guiCenterY =
                bgY
                        + BackgroundComponent.BG_HEIGHT
                        * 0.5f;

        double mx =
                transformMouseX(
                        mouseX,
                        scale,
                        Math.max(
                                currentScale,
                                0.001f
                        ),
                        guiCenterX
                );

        double my =
                transformMouseY(
                        mouseY,
                        scale,
                        Math.max(
                                currentScale,
                                0.001f
                        ),
                        guiCenterY
                );

        if (background.isSearchActive()) {

            float panelX =
                    bgX + 70f;

            float panelY =
                    bgY + 38f;

            float panelW =
                    BackgroundComponent.BG_WIDTH
                            - 78f;

            float panelH =
                    BackgroundComponent.BG_HEIGHT
                            - 46f;

            if (
                    mx >= panelX
                            && mx <= panelX + panelW
                            && my >= panelY
                            && my <= panelY + panelH
            ) {

                background.handleSearchScroll(
                        vertical,
                        panelH
                );

                return true;
            }
        }

        if (selectedCategory == ModuleCategory.THEME) {
            return themeRenderer.mouseScrolled(mx, my, vertical, bgX, bgY);
        }

        if (selectedCategory == ModuleCategory.CONFIG) {
            return configsRenderer.mouseScrolled(mx, my, vertical, bgX, bgY, selectedCategory);
        }

        if (selectedCategory == ModuleCategory.LUA) {
            if (luaRenderer.mouseScrolled(mx, my, vertical, bgX, bgY)) {
                return true;
            }
        }

        float mlX =
                bgX + 98f;

        float mlY =
                bgY + 38f;

        float mlW =
                120f;

        float mlH =
                BackgroundComponent.BG_HEIGHT
                        - 48f;

        if (
                mx >= mlX
                        && mx <= mlX + mlW
                        && my >= mlY
                        && my <= mlY + mlH
        ) {

            moduleComponent.handleModuleScroll(
                    vertical,
                    mlH
            );

            return true;
        }

        float spX =
                bgX + 216f;

        float spY =
                bgY + 38f;

        float spW =
                174f;

        float spH =
                BackgroundComponent.BG_HEIGHT
                        - 48f;

        if (
                mx >= spX
                        && mx <= spX + spW
                        && my >= spY
                        && my <= spY + spH
        ) {

            moduleComponent.handleSettingScroll(
                    vertical,
                    spH
            );

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                horizontal,
                vertical
        );
    }

    private void focusSearchModule(ModuleStructure module) {
        if (module == null) {
            return;
        }

        background.setSearchActive(false);
        selectedCategory = module.getCategory();
        updateModules();
        moduleComponent.selectModuleFromSearch(module);
    }

    @Override
    public boolean keyPressed(
            KeyInput input
    ) {
        if (mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.isOpen()) {
            return mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.key(input.key());
        }
        ModuleStructure binding =
                moduleComponent
                        .getBindingModule();

        if (binding != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                moduleComponent.setBindingModule(
                        null
                );

                return true;
            }

            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                binding.setKey(
                        GLFW.GLFW_KEY_UNKNOWN
                );

                moduleComponent.setBindingModule(
                        null
                );

                return true;
            }

            ModuleBindings.assignUnique(
                    binding,
                    input.key()
            );

            moduleComponent.setBindingModule(
                    null
            );

            return true;
        }

        if (!closing && !background.isSearchActive() && selectedCategory == ModuleCategory.CONFIG
                && configsRenderer.keyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }

        boolean ctrlDown =
                (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                        || GLFW.glfwGetKey(
                        mc.getWindow().getHandle(),
                        GLFW.GLFW_KEY_LEFT_CONTROL
                ) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(
                        mc.getWindow().getHandle(),
                        GLFW.GLFW_KEY_RIGHT_CONTROL
                ) == GLFW.GLFW_PRESS;

        if (
                ctrlDown
                        && input.key() == GLFW.GLFW_KEY_F
        ) {
            if (!background.isSearchActive()) {
                background.setSearchActive(true);
            }

            return true;
        }

        if (
                input.key()
                        == GLFW.GLFW_KEY_ESCAPE
        ) {

            if (configsRenderer.isEditing()) {
                return true;
            }

            if (background.isSearchActive()) {
                background.setSearchActive(false);
                return true;
            }

            close();

            return true;
        }

        if (closing) {
            return false;
        }

        if (background.isSearchActive()) {
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                ModuleStructure searchModule =
                        background.getSelectedSearchModule();

                if (searchModule != null) {
                    focusSearchModule(searchModule);
                }

                return true;
            }

            if (
                    background.handleSearchKey(
                            input.key()
                    )
            ) {
                return true;
            }
        }

        if (
                dragHandler.isResetNeeded(
                        input.key(),
                        input.modifiers()
                )
        ) {
            dragHandler.reset();

            return true;
        }

        for (
                AbstractSettingComponent c :
                moduleComponent
                        .getSettingComponents()
        ) {
            if (
                    c.getSetting().isVisible()
                            && c.keyPressed(
                            input.key(),
                            input.scancode(),
                            input.modifiers()
                    )
            ) {
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(
            CharInput input
    ) {
        if (mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.isOpen()) {
            return mind.beta.screens.clickgui.impl.rotation.RotationConfigPopup.INSTANCE.character((char) input.codepoint());
        }
        if (closing) {
            return false;
        }

        if (moduleComponent.getBindingModule() != null) {
            return true;
        }

        if (background.isSearchActive()) {

            if (
                    background.handleSearchChar(
                            (char) input.codepoint()
                    )
            ) {
                return true;
            }
        }

        if (!background.isSearchActive() && selectedCategory == ModuleCategory.CONFIG
                && configsRenderer.charTyped((char) input.codepoint(), input.modifiers())) {
            return true;
        }

        for (
                AbstractSettingComponent c :
                moduleComponent
                        .getSettingComponents()
        ) {

            if (
                    c.getSetting().isVisible()
                            && c.charTyped(
                            (char) input.codepoint(),
                            input.modifiers()
                    )
            ) {
                return true;
            }
        }

        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void startActualClose() {
        openAnimation
                .setDirection(
                        Direction.BACKWARDS
                )
                .reset();

        TextComponent.typing = false;

        moduleComponent.setBindingModule(
                null
        );

        background.setSearchActive(false);
        HeaderRenderer.closeSortMenu();

        dragHandler.stopDrag();
    }

    @Override
    public void close() {
        if (!closing) {

            closing = true;

            long handle =
                    mc.getWindow().getHandle();

            GLFW.glfwSetInputMode(
                    handle,
                    GLFW.GLFW_CURSOR,
                    GLFW.GLFW_CURSOR_DISABLED
            );

            startActualClose();
        }
    }
}
