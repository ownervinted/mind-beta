package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import mind.beta.Initialization;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.events.api.EventHandler;
import mind.beta.events.impl.KeyEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.setting.implement.BindSetting;
import mind.beta.screens.clickgui.ClickGui;
import mind.beta.util.animations.Animation;
import mind.beta.util.animations.Direction;
import mind.beta.util.animations.OutBack;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Notifications extends AbstractHudElement {

    private static final int FORCED_GUI_SCALE = 2;

    private static Notifications instance;

    public static Notifications getInstance() {
        return instance;
    }

    private final List<Notification> list = new ArrayList<>();

    private static final float NOTIFICATION_HEIGHT = 16f;
    private static final float NOTIFICATION_GAP = 3f;
    private static final float CROSSHAIR_OFFSET_Y = 28f;

    public Notifications() {
        super("Notifications", 0, 0, 110, 16, false);

        instance = this;

        if (Initialization.getInstance() != null
                && Initialization.getInstance().getManager() != null
                && Initialization.getInstance().getManager().getEventManager() != null) {
            Initialization.getInstance()
                    .getManager()
                    .getEventManager()
                    .register(this);
        }
    }

    private int getCurrentGuiScale() {
        int scale = mc.options.getGuiScale().getValue();

        if (scale == 0) {
            scale = mc.getWindow().calculateScaleFactor(
                    0,
                    mc.forcesUnicodeFont()
            );
        }

        return scale;
    }

    private float getVirtualWidth() {
        return mc.getWindow().getFramebufferWidth()
                / (float) FORCED_GUI_SCALE;
    }

    private float getVirtualHeight() {
        return mc.getWindow().getFramebufferHeight()
                / (float) FORCED_GUI_SCALE;
    }

    @Override
    public boolean visible() {
        return !list.isEmpty();
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (event.action() != GLFW.GLFW_PRESS) {
            return;
        }

        if (mc.currentScreen instanceof ClickGui) {
            return;
        }

        if (Initialization.getInstance() == null
                || Initialization.getInstance().getManager() == null
                || Initialization.getInstance().getManager().getModuleProvider() == null) {
            return;
        }

        List<ModuleStructure> modules = Initialization.getInstance()
                .getManager()
                .getModuleProvider()
                .getModuleStructures();

        for (ModuleStructure module : modules) {
            if (!module.isState()) {
                continue;
            }

            for (var setting : module.settings()) {
                if (!(setting instanceof BindSetting bindSetting)) {
                    continue;
                }

                if (!bindSetting.isVisible()) {
                    continue;
                }

                if (bindSetting.getType() != 1) {
                    continue;
                }

                if (bindSetting.getKey() != event.key()) {
                    continue;
                }

                addNotification(
                        module.getName()
                                + " • "
                                + bindSetting.getName(),
                        1800L
                );
            }
        }
    }

    @Override
    public void tick() {
        list.forEach(notification -> {
            if (System.currentTimeMillis() > notification.removeTime
                    || notification.text.contains("Hi I'm a notification")
                    && !isChat(mc.currentScreen)) {
                notification.anim.setDirection(Direction.BACKWARDS);
            }
        });

        list.removeIf(
                notification ->
                        notification.anim.isFinished(Direction.BACKWARDS)
        );

        if (isChat(mc.currentScreen)) {
            boolean hasExample =
                    list.stream()
                            .anyMatch(
                                    notification ->
                                            notification.text.contains(
                                                    "Hi I'm a notification"
                                            )
                            );

            if (!hasExample) {
                addNotification(
                        "Hi I'm a notification",
                        99999999L
                );
            }
        }

        updatePosition();
    }

    private void updatePosition() {
        if (mc.getWindow() == null) {
            return;
        }

        float virtualWidth = getVirtualWidth();
        float virtualHeight = getVirtualHeight();

        float crosshairX = virtualWidth / 2f;
        float crosshairY = virtualHeight / 2f;

        this.setX(
                (int) (crosshairX - 60f)
        );

        this.setY(
                (int) (crosshairY + CROSSHAIR_OFFSET_Y)
        );
    }

    public void addNotification(
            String text,
            long duration
    ) {
        Animation anim =
                new OutBack()
                        .setMs(700)
                        .setValue(1);

        anim.setDirection(Direction.FORWARDS);

        int targetIndex = list.size();

        float targetY =
                targetIndex
                        * (
                        NOTIFICATION_HEIGHT
                                + NOTIFICATION_GAP
                );

        Notification notification =
                new Notification(
                        text,
                        anim,
                        System.currentTimeMillis(),
                        System.currentTimeMillis()
                                + duration
                );

        notification.currentY = targetY;
        notification.targetY = targetY;
        notification.velocityY = 0f;

        list.add(notification);

        if (list.size() > 12) {
            list.removeFirst();
        }

        updateTargetPositions();
    }

    private void updateTargetPositions() {
        float offsetY = 0f;

        for (Notification notification : list) {
            float anim =
                    notification.anim
                            .getOutput()
                            .floatValue();

            notification.targetY = offsetY;

            offsetY +=
                    (
                            NOTIFICATION_HEIGHT
                                    + NOTIFICATION_GAP
                    )
                            * anim;
        }
    }

    private int clampAlpha(int value) {
        return Math.max(
                0,
                Math.min(
                        255,
                        value
                )
        );
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

    @Override
    public void drawDraggable(
            DrawContext context,
            int alpha
    ) {
        alpha = clampAlpha(alpha);

        if (alpha <= 0) {
            return;
        }

        float alphaFactor =
                alpha / 255.0f;

        updatePosition();
        updateTargetPositions();

        float springStiffness = 180f;
        float damping = 12f;
        float deltaTime = 0.016f;

        for (Notification notification : list) {
            float diff =
                    notification.targetY
                            - notification.currentY;

            float springForce =
                    diff
                            * springStiffness;

            float dampingForce =
                    notification.velocityY
                            * damping;

            float acceleration =
                    springForce
                            - dampingForce;

            notification.velocityY +=
                    acceleration
                            * deltaTime;

            notification.currentY +=
                    notification.velocityY
                            * deltaTime;

            if (Math.abs(diff) < 0.01f
                    && Math.abs(notification.velocityY) < 0.01f) {
                notification.currentY =
                        notification.targetY;

                notification.velocityY = 0f;
            }
        }

        float maxWidth = 0f;
        float totalHeight = 0f;

        float crosshairX =
                getVirtualWidth()
                        * 0.5f;

        for (Notification notification : list) {
            float anim =
                    notification.anim
                            .getOutput()
                            .floatValue();

            if (anim <= 0.01f) {
                continue;
            }

            anim =
                    Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    anim
                            )
                    );

            float textWidth =
                    Fonts.MEDIUM.getWidth(
                            notification.text,
                            6f
                    );

            float width =
                    textWidth
                            + 32f;

            maxWidth =
                    Math.max(
                            maxWidth,
                            width
                    );

            float startY =
                    this.getY()
                            + notification.currentY;

            float startX =
                    crosshairX
                            - width
                            * 0.5f;

            int bgAlpha =
                    clampAlpha(
                            255f
                                    * anim
                                    * alphaFactor
                    );

            int iconAlpha =
                    clampAlpha(
                            225f
                                    * anim
                                    * alphaFactor
                    );

            HudPanelStyle.panel(
                    startX,
                    startY,
                    width,
                    NOTIFICATION_HEIGHT,
                    8f,
                    bgAlpha
            );

            Fonts.MIND_FONT.draw(
                    "N",
                    startX + 6f,
                    startY + 4f,
                    8f,
                    HudPanelStyle.accent(iconAlpha)
            );

            Render2D.rect(
                    startX + 18f,
                    startY + 4f,
                    0.7f,
                    NOTIFICATION_HEIGHT - 8f,
                    ThemeColors.color(
                            0,
                            128,
                            255,
                            clampAlpha(
                                    24f
                                            * anim
                                            * alphaFactor
                            )
                    ).getRGB(),
                    0f
            );

            Fonts.MEDIUM.draw(
                    notification.text,
                    startX + 23f,
                    startY + 4.5f,
                    6f,
                    HudPanelStyle.text(bgAlpha)
            );

            totalHeight =
                    Math.max(
                            totalHeight,
                            notification.currentY
                                    + NOTIFICATION_HEIGHT
                    );
        }

        if (maxWidth > 0f) {
            setWidth(
                    (int) Math.ceil(maxWidth)
            );
        }

        setHeight(
                (int) Math.ceil(
                        Math.max(
                                NOTIFICATION_HEIGHT,
                                totalHeight
                        )
                )
        );
    }

    public static class Notification {
        String text;
        Animation anim;
        long startTime;
        long removeTime;

        float currentY;
        float targetY;
        float velocityY;

        Notification(
                String text,
                Animation anim,
                long startTime,
                long removeTime
        ) {
            this.text = text;
            this.anim = anim;
            this.startTime = startTime;
            this.removeTime = removeTime;
            this.currentY = 0f;
            this.targetY = 0f;
            this.velocityY = 0f;
        }
    }
}
