package mind.beta.screens.hud;

import mind.beta.util.render.theme.ThemeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import mind.beta.client.draggables.AbstractHudElement;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.util.network.Network;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.GuiTransform;
import mind.beta.util.render.font.Fonts;

import java.awt.Color;

public class TargetHud extends AbstractHudElement {

    private static final Identifier STEVE_TEXTURE =
            Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private static final float HUD_WIDTH = 128f;
    private static final float HUD_HEIGHT = 34f;
    private static final float LEFT_WIDTH = 34f;
    private static final float RADIUS = 9f;

    private static final float FACE_SIZE = 21f;
    private static final float FONT_SIZE = 7f;
    private static final float HP_FONT_SIZE = 6.5f;

    private static final long TERMINATED_DURATION = 1500L;
    private static final float TERMINATED_FONT_SIZE = 10.5f;
    private static final float TERMINATED_SPACING = 2f;

    private LivingEntity lastTarget;

    private boolean terminated;
    private long terminatedStartedAt;

    private float visibilityAnimation;
    private float blurAnimation;

    private float healthAnimation;
    private float trailAnimation;
    private float absorptionAnimation;
    private float displayedHealth;

    private long lastFrameTime = System.currentTimeMillis();

    public TargetHud() {
        super("TargetHud", 10, 80, (int) HUD_WIDTH, (int) HUD_HEIGHT, true);
    }

    @Override
    public boolean visible() {
        return visibilityAnimation > 0f
                || blurAnimation > 0f
                || terminated
                || Aura.target != null
                || isChat(mc.currentScreen);
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        syncTarget();
        this.lastTickDelta = tickDelta;
        drawDraggable(context, 200);
    }

    @Override
    public void tick() {
        syncTarget();
    }

    private void syncTarget() {
        if (terminated) {
            return;
        }

        LivingEntity auraTarget = Aura.target;

        if (lastTarget != null
                && lastTarget != mc.player
                && isTerminated(lastTarget)) {
            startTermination();
            return;
        }

        if (auraTarget != null) {
            if (isTerminated(auraTarget)) {
                lastTarget = auraTarget;
                startTermination();
                return;
            }

            if (lastTarget != auraTarget) {
                lastTarget = auraTarget;
                resetTargetAnimations();
            }

            return;
        }

        if (isChat(mc.currentScreen) && mc.player != null) {
            if (lastTarget != mc.player) {
                lastTarget = mc.player;
                resetTargetAnimations();
            }
        }
    }

    private boolean isTerminated(LivingEntity entity) {
        return entity != null
                && entity != mc.player
                && (!entity.isAlive() || entity.getHealth() <= 0f);
    }

    private void startTermination() {
        if (terminated || lastTarget == null || lastTarget == mc.player) {
            return;
        }

        terminated = true;
        terminatedStartedAt = System.currentTimeMillis();
        visibilityAnimation = 1f;
        blurAnimation = 1f;
    }

    private void finishTermination() {
        terminated = false;
        terminatedStartedAt = 0L;
        visibilityAnimation = 0f;
        blurAnimation = 0f;
        lastTarget = null;
    }

    private void resetTargetAnimations() {
        terminated = false;
        terminatedStartedAt = 0L;

        if (lastTarget == null) {
            return;
        }

        float health = getHealth(lastTarget);
        float maxHealth = Math.max(1f, lastTarget.getMaxHealth());
        float absorption = lastTarget.getAbsorptionAmount();

        healthAnimation = clamp01(health / maxHealth);
        trailAnimation = healthAnimation;
        absorptionAnimation = clamp01(absorption / maxHealth);
        displayedHealth = health + absorption;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        long now = System.currentTimeMillis();
        float delta = Math.min((now - lastFrameTime) / 1000f, 0.05f);
        lastFrameTime = now;

        float terminatedProgress = 0f;

        if (terminated) {
            terminatedProgress = clamp01(
                    (now - terminatedStartedAt) / (float) TERMINATED_DURATION
            );

            if (terminatedProgress >= 1f) {
                finishTermination();
                return;
            }
        }

        boolean shouldShow =
                terminated
                        || Aura.target != null
                        || isChat(mc.currentScreen);

        visibilityAnimation = moveLinear(
                visibilityAnimation,
                shouldShow ? 1f : 0f,
                delta,
                shouldShow ? 7.5f : 8.5f
        );

        blurAnimation = visibilityAnimation;

        if (visibilityAnimation <= 0f && !shouldShow) {
            lastTarget = null;
            return;
        }

        if (lastTarget == null) {
            return;
        }

        setWidth((int) HUD_WIDTH);
        setHeight((int) HUD_HEIGHT);

        float x = getX();
        float y = getY();

        float terminationEnvelope = terminated
                ? (float) Math.sin(Math.PI * terminatedProgress)
                : 0f;

        float shakeX = terminated
                ? (float) Math.sin(now * 0.047) * 0.62f * terminationEnvelope
                : 0f;

        float shakeY = terminated
                ? (float) Math.sin(now * 0.061 + 1.35) * 0.34f * terminationEnvelope
                : 0f;

        x += shakeX;
        y += shakeY;

        float centerX = x + HUD_WIDTH * 0.5f;
        float centerY = y + HUD_HEIGHT * 0.5f;

        float open = easeInOutCubic(visibilityAnimation);

        if (terminated) {
            float exit = smoothStep(
                    clamp01((terminatedProgress - 0.88f) / 0.12f)
            );

            open *= 1f - exit;
        }

        if (open <= 0.001f) {
            return;
        }

        float contentAlpha = terminated
                ? 1f - smoothStep(clamp01(terminatedProgress / 0.34f))
                : 1f;

        float terminatedAlpha = terminated
                ? smoothStep(clamp01((terminatedProgress - 0.08f) / 0.28f))
                : 0f;

        if (terminated) {
            float textExit = smoothStep(
                    clamp01((terminatedProgress - 0.84f) / 0.16f)
            );

            terminatedAlpha *= 1f - textExit;
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(open, open);
        context.getMatrices().translate(-centerX, -centerY);

        GuiTransform.begin(open, centerX, centerY);

        try {
            drawBackground(
                    x,
                    y,
                    HUD_WIDTH,
                    HUD_HEIGHT,
                    RADIUS,
                    LEFT_WIDTH,
                    contentAlpha
            );

            if (contentAlpha > 0.002f) {
                drawFace(
                        x,
                        y,
                        HUD_HEIGHT,
                        LEFT_WIDTH,
                        contentAlpha
                );

                drawContent(
                        x,
                        y,
                        HUD_WIDTH,
                        HUD_HEIGHT,
                        LEFT_WIDTH,
                        delta,
                        contentAlpha
                );
            }

            if (terminatedAlpha > 0.002f) {
                drawTerminated(
                        x,
                        y,
                        HUD_WIDTH,
                        HUD_HEIGHT,
                        terminatedAlpha
                );
            }
        } finally {
            GuiTransform.end();
            context.getMatrices().popMatrix();
        }
    }

    private void drawBackground(
            float x,
            float y,
            float width,
            float height,
            float radius,
            float leftWidth,
            float contentAlpha
    ) {
        if (terminated) {
            Render2D.rect(x, y, width, height, 0xFFB41420, radius);
        } else {
            HudPanelStyle.panel(x, y, width, height, radius, 255);
        }

        if (contentAlpha > 0.002f) {
            Render2D.rect(
                    x + leftWidth,
                    y + 6f,
                    0.7f,
                    Math.max(0f, height - 12f),
                    withAlpha(
                            targetHudColor(0, 128, 255, 18).getRGB(),
                            contentAlpha
                    ),
                    0f
            );
        }
    }

    private void drawFace(
            float x,
            float y,
            float height,
            float leftWidth,
            float contentAlpha
    ) {
        Identifier texture = STEVE_TEXTURE;
        boolean player = lastTarget instanceof PlayerEntity;

        if (player) {
            EntityRenderer<? super LivingEntity, ?> baseRenderer =
                    mc.getEntityRenderDispatcher().getRenderer(lastTarget);

            if (baseRenderer instanceof LivingEntityRenderer<?, ?, ?>) {
                @SuppressWarnings("unchecked")
                LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                        (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

                LivingEntityRenderState state =
                        renderer.getAndUpdateRenderState(lastTarget, lastTickDelta);

                texture = renderer.getTexture(state);
            }
        }

        float hurt = lastTarget.hurtTime > 0
                ? Math.min(1f, lastTarget.hurtTime / 10f)
                : 0f;

        int greenBlue = (int) (255f * (1f - hurt * 0.65f));
        int color = (Math.round(255f * contentAlpha) << 24)
                | (255 << 16) | (greenBlue << 8) | greenBlue;

        float faceSize = Math.min(leftWidth, height) - 6f;
        float faceX = x + leftWidth * 0.5f - faceSize * 0.5f;
        float faceY = y + height * 0.5f - faceSize * 0.5f;
        float faceRadius = 7f;

        Render2D.texture(
                texture,
                faceX,
                faceY,
                faceSize,
                faceSize,
                8f / 64f,
                8f / 64f,
                16f / 64f,
                16f / 64f,
                color,
                0f,
                faceRadius
        );

        Render2D.texture(
                texture,
                faceX,
                faceY,
                faceSize,
                faceSize,
                40f / 64f,
                8f / 64f,
                48f / 64f,
                16f / 64f,
                color,
                0f,
                faceRadius
        );
    }

    private void drawContent(
            float x,
            float y,
            float width,
            float height,
            float leftWidth,
            float delta,
            float contentAlpha
    ) {

        float maxHealth = Math.max(1f, lastTarget.getMaxHealth());
        float health = getHealth(lastTarget);
        float absorption = lastTarget.getAbsorptionAmount();

        boolean hiddenHealth =
                lastTarget.isInvisible()
                        && !Network.isSlothAC()
                        && !Network.isCopyTime();

        float targetHealth = hiddenHealth ? 1f : clamp01(health / maxHealth);
        float targetAbsorption = hiddenHealth ? 0f : clamp01(absorption / maxHealth);
        float targetDisplay = hiddenHealth ? maxHealth : health;

        healthAnimation = dampSnap(healthAnimation, targetHealth, delta, 11f, 0.001f);

        if (targetHealth > trailAnimation) {
            trailAnimation = targetHealth;
        }

        trailAnimation = dampSnap(trailAnimation, targetHealth, delta, 6.5f, 0.001f);
        absorptionAnimation = dampSnap(absorptionAnimation, targetAbsorption, delta, 9f, 0.001f);
        displayedHealth = dampSnap(displayedHealth, targetDisplay, delta, 10f, 0.01f);

        float contentX = x + leftWidth + 7f;
        float rightPadding = 7f;
        float contentRight = x + width - rightPadding;
        float contentWidth = Math.max(0f, contentRight - contentX);

        String name = lastTarget.getName().getString();
        String healthString = hiddenHealth
                ? "??"
                : formatHearts(displayedHealth, maxHealth);

        float nameY = y + 7f;
        float hpWidth = Fonts.MEDIUM.getWidth(healthString, HP_FONT_SIZE);
        float nameSpace = Math.max(0f, contentWidth - hpWidth - 6f);
        while (!name.isEmpty() && Fonts.MEDIUM.getWidth(name, FONT_SIZE) > nameSpace) {
            int last = name.offsetByCodePoints(name.length(), -1);
            name = name.substring(0, last);
        }

        float barX = contentX;
        float barY = y + height - 9f;
        float barWidth = contentWidth;
        float barHeight = 4f;
        float barRadius = barHeight * 0.5f;

        Render2D.rect(
                barX,
                barY,
                barWidth,
                barHeight,
                withAlpha(
                        targetHudColor(8, 18, 30, 175).getRGB(),
                        contentAlpha
                ),
                barRadius
        );

        float trail = clamp01(trailAnimation);
        float hp = clamp01(healthAnimation);
        float absorptionProgress = clamp01(absorptionAnimation);

        if (trail > hp + 0.002f) {
            Render2D.rect(
                    barX,
                    barY,
                    barWidth * trail,
                    barHeight,
                    withAlpha(
                            targetHudColor(70, 105, 145, 165).getRGB(),
                            contentAlpha
                    ),
                    barRadius
            );
        }

        if (hp > 0.002f) {
            int healthColor = getHealthColor(hp);

            Render2D.rect(
                    barX,
                    barY,
                    barWidth * hp,
                    barHeight,
                    withAlpha(healthColor, contentAlpha),
                    barRadius
            );
        }

        if (absorptionProgress > 0.002f && !Network.isFunTime()) {
            float absorptionWidth = Math.min(
                    barWidth * absorptionProgress,
                    Math.max(0f, barWidth - barWidth * hp)
            );

            if (absorptionWidth > 0.01f) {
                Render2D.rect(
                        barX + barWidth * hp,
                        barY,
                        absorptionWidth,
                        barHeight,
                        withAlpha(
                                targetHudColor(225, 182, 75, 235).getRGB(),
                                contentAlpha
                        ),
                        barRadius
                );
            }
        }
        Fonts.MEDIUM.draw(
                name,
                contentX,
                nameY,
                FONT_SIZE,
                withAlpha(
                        targetHudColor(228, 232, 238, 236).getRGB(),
                        contentAlpha
                )
        );

        Fonts.MEDIUM.draw(
                healthString,
                contentRight - hpWidth,
                nameY + 0.25f,
                HP_FONT_SIZE,
                withAlpha(
                        terminated && ThemeColors.isLight()
                                ? new Color(205, 227, 255, 230).getRGB()
                                : HudPanelStyle.accent(230),
                        contentAlpha
                )
        );

    }

    private void drawTerminated(
            float x,
            float y,
            float width,
            float height,
            float alpha
    ) {
        String text = "TERMINATED";

        float textWidth = getSpacedTextWidth(
                text,
                TERMINATED_FONT_SIZE,
                TERMINATED_SPACING
        );

        float textHeight = Fonts.BOLD.getHeight(
                TERMINATED_FONT_SIZE
        );

        float textX = x + width * 0.5f - textWidth * 0.5f;
        float textY = y + height * 0.5f - textHeight * 0.5f + 0.3f;

        drawSpacedBoldText(
                text,
                textX,
                textY,
                TERMINATED_FONT_SIZE,
                TERMINATED_SPACING,
                targetHudColor(
                        255,
                        255,
                        255,
                        Math.round(245f * alpha)
                ).getRGB()
        );
    }

    private float getSpacedTextWidth(
            String text,
            float size,
            float spacing
    ) {
        float width = 0f;

        for (int i = 0; i < text.length(); i++) {
            width += Fonts.BOLD.getWidth(
                    String.valueOf(text.charAt(i)),
                    size
            );

            if (i < text.length() - 1) {
                width += spacing;
            }
        }

        return width;
    }

    private void drawSpacedBoldText(
            String text,
            float x,
            float y,
            float size,
            float spacing,
            int color
    ) {
        float cursor = x;

        for (int i = 0; i < text.length(); i++) {
            String glyph = String.valueOf(text.charAt(i));

            Fonts.BOLD.draw(
                    glyph,
                    cursor,
                    y,
                    size,
                    color
            );

            cursor += Fonts.BOLD.getWidth(glyph, size);

            if (i < text.length() - 1) {
                cursor += spacing;
            }
        }
    }

    private float getHealth(LivingEntity entity) {
        if (entity.isInvisible()
                && !Network.isSlothAC()
                && !Network.isCopyTime()) {
            return entity.getMaxHealth();
        }

        return entity.getHealth();
    }

    private String formatHearts(float health, float maxHealth) {
        if (maxHealth <= 0f) {
            return "0";
        }

        float hearts = clamp01(health / maxHealth) * 10f;

        if (hearts >= 9.95f) {
            return "10";
        }

        if (hearts >= 1f) {
            return String.format("%.1f", hearts);
        }

        return String.format("%.2f", hearts);
    }

    private Color targetHudColor(int red, int green, int blue, int alpha) {
        if (terminated && ThemeColors.isLight()) {
            return new Color(red, green, blue, alpha);
        }
        return ThemeColors.color(red, green, blue, alpha);
    }

    private int getHealthColor(float healthPercent) {
        float t = clamp01(healthPercent);

        int red = Math.round(34f + (0f - 34f) * t);
        int green = Math.round(92f + (128f - 92f) * t);
        int blue = Math.round(172f + (255f - 172f) * t);

        return targetHudColor(red, green, blue, 235).getRGB();
    }

    private float moveLinear(
            float current,
            float target,
            float delta,
            float speed
    ) {
        float step = speed * delta;

        if (current < target) {
            return Math.min(target, current + step);
        }

        if (current > target) {
            return Math.max(target, current - step);
        }

        return current;
    }

    private float easeInOutCubic(float value) {
        float t = clamp01(value);

        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3f) * 0.5f;
    }

    private float dampSnap(
            float current,
            float target,
            float delta,
            float speed,
            float snapDistance
    ) {
        float diff = target - current;

        if (Math.abs(diff) <= snapDistance) {
            return target;
        }

        float factor = 1f - (float) Math.exp(-speed * delta);
        float next = current + diff * factor;

        if (Math.abs(target - next) <= snapDistance) {
            return target;
        }

        return next;
    }

    private float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3f - 2f * t);
    }

    private int withAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int multiplied = Math.max(
                0,
                Math.min(
                        255,
                        Math.round(alpha * clamp01(factor))
                )
        );

        return (multiplied << 24) | (color & 0x00FFFFFF);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
