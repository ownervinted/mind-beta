/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat;

import net.minecraft.entity.LivingEntity;
import mind.beta.Initialization;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.types.EventType;
import mind.beta.events.impl.DrawEvent;
import mind.beta.events.impl.HotBarScrollEvent;
import mind.beta.events.impl.InputEvent;
import mind.beta.events.impl.KeyEvent;
import mind.beta.events.impl.RotationUpdateEvent;
import mind.beta.events.impl.TickEvent;
import mind.beta.modules.impl.combat.aura.Rotation;
import mind.beta.modules.impl.combat.aura.RotationManager;
import mind.beta.modules.impl.combat.aura.attack.AttackManager;
import mind.beta.modules.impl.combat.aura.mace.MaceResolver;
import mind.beta.modules.impl.combat.aura.mace.MaceThrowManager;
import mind.beta.modules.impl.combat.aura.mace.WindJumpController;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.util.Instance;
import mind.beta.util.render.Render2D;
import mind.beta.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MaceHelper extends ModuleStructure {

    private static final float INDICATOR_FONT_SIZE = 6.0F;
    private static final float INDICATOR_LINE_HEIGHT = 8.0F;
    private static final float INDICATOR_X_OFFSET = 13.0F;
    private static final float INDICATOR_Y_OFFSET = 11.0F;
    private static final long INDICATOR_EVENT_HOLD_MS = 3000L;

    private static final int COLOR_TEXT = 0xFFE8EAF0;
    private static final int COLOR_MUTED = 0xFF9DA3AF;
    private static final int COLOR_GOOD = 0xFF7DFF9B;
    private static final int COLOR_WARN = 0xFFFFD479;
    private static final int COLOR_BAD = 0xFFFF7D7D;
    private static final int COLOR_INFO = 0xFF86C8FF;
    private static final int COLOR_BACKGROUND = 0xB312141A;
    public static MaceHelper getInstance() {
        return Instance.get(MaceHelper.class);
    }

    private final MaceThrowManager throwManager =
            new MaceThrowManager(this);

    private final WindJumpController windJump =
            new WindJumpController(this);

    private String lastObservedMaceStatus = "";
    private String stickyMaceEvent = "";
    private long stickyMaceEventUntil = 0L;

    private final BooleanSetting indicators = new BooleanSetting(
            "Mace Indicators",
            "Shows throw and mace resolver diagnostics near the crosshair"
    ).setValue(true);

    public MaceHelper() {
        super(
                "MaceHelper",
                "Mace Helper",
                ModuleCategory.COMBAT
        );

        settings(
                throwManager.getThrowSwapMode(),
                windJump.getModeSetting(),
                throwManager.getKeySetting(),
                windJump.getKeySetting(),
                indicators
        );
    }

    public boolean isThrowSequenceActive() {
        return isState() && (throwManager.isActive() || windJump.isRunning());
    }

    public boolean isMovementLockedByThrow() {
        return isState()
                && (throwManager.shouldBlockMovement() || windJump.isMovementBlocked());
    }

    @Override
    public void activate() {
        throwManager.cancel();
        windJump.deactivate();
    }

    @Override
    public void deactivate() {
        throwManager.cancel();
        windJump.deactivate();
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (!isState()) {
            throwManager.cancel();
            windJump.deactivate();
            return;
        }
        throwManager.tick();
        windJump.onTick(event);
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (!isState() || throwManager.isActive() || windJump.isRunning()) {
            return;
        }
        throwManager.onKey(event);
        if (!throwManager.isActive()) {
            windJump.onKey(event);
        }
    }

    @EventHandler
    private void onScroll(HotBarScrollEvent event) {
        if (isState() && !throwManager.isActive() && !windJump.isRunning()) {
            windJump.onScroll(event);
        }
    }

    @EventHandler
    private void onRotationUpdate(
            RotationUpdateEvent event
    ) {
        if (windJump.isRunning()) {
            windJump.onRotationUpdate(event);
            return;
        }

        if (event.getType() == EventType.PRE) {
            if (throwManager.isActive()) {
                throwManager.updateRotation();
            }
            return;
        }

        if (event.getType() == EventType.POST
                && throwManager.isActive()) {
            throwManager.process();
        }
    }

    @EventHandler
    private void onInput(InputEvent event) {
        if (windJump.isRunning() || windJump.getJumpRemainingMs() >= 0L) {
            windJump.onInput(event);
            return;
        }

        if (throwManager.shouldBlockMovement()) {
            event.setDirectionalLow(
                    false,
                    false,
                    false,
                    false
            );
            event.setSprinting(false);
            event.setJumping(false);
            throwManager.onMovementInputBlocked();
        }

        if (throwManager.consumeWindOnlyJump()) {
            event.setJumping(true);
        }
    }

    @EventHandler
    private void onDraw(DrawEvent event) {
        if (!indicators.isValue()
                || mc.player == null
                || mc.world == null) {
            return;
        }

        List<IndicatorLine> lines =
                buildIndicatorLines();

        if (lines.isEmpty()) {
            return;
        }

        float x =
                mc.getWindow().getScaledWidth()
                        / 2.0F
                        + INDICATOR_X_OFFSET;

        float y =
                mc.getWindow().getScaledHeight()
                        / 2.0F
                        + INDICATOR_Y_OFFSET;

        for (IndicatorLine line : lines) {
            drawIndicatorLine(line, x, y);
            y += INDICATOR_LINE_HEIGHT;
        }
    }

    private List<IndicatorLine> buildIndicatorLines() {
        List<IndicatorLine> lines =
                new ArrayList<>();

        boolean windRelevant =
                windJump.isRunning()
                        || windJump.getJumpRemainingMs() >= 0L;

        boolean throwRelevant =
                windRelevant
                        || throwManager.isActive()
                        || throwManager
                        .getWindOnlyJumpRemainingMs() >= 0L;

        if (throwRelevant) {
            lines.add(
                    new IndicatorLine(
                            "THROW  "
                                    + (windRelevant ? "Wind Only" : throwManager.getDebugAction())
                                    + "  |  "
                                    + (windRelevant ? windJump.getDebugState() : throwManager.getDebugState()),
                            COLOR_INFO
                    )
            );

            Rotation serverRotation =
                    RotationManager.INSTANCE
                            .getServerRotation();

            float serverPitch =
                    serverRotation != null
                            ? serverRotation.getPitch()
                            : mc.player.getPitch();

            float targetPitch =
                    windRelevant
                            ? windJump.getDebugRequiredPitch()
                            : throwManager.getDebugRequiredPitch();

            if (Float.isNaN(targetPitch)) {
                lines.add(
                        new IndicatorLine(
                                "PITCH  "
                                        + formatSigned(
                                        serverPitch
                                ),
                                COLOR_MUTED
                        )
                );
            } else {
                lines.add(
                        new IndicatorLine(
                                "PITCH  "
                                        + formatSigned(
                                        serverPitch
                                )
                                        + "  ->  "
                                        + formatSigned(
                                        targetPitch
                                ),
                                Math.abs(
                                        serverPitch
                                                - targetPitch
                                ) <= 1.0F
                                        ? COLOR_GOOD
                                        : COLOR_WARN
                        )
                );
            }
        }

        Aura aura = Aura.getInstance();

        if (aura == null
                || !aura.isState()
                || !aura.getMaceOnFall().isValue()) {
            return lines;
        }

        MaceResolver resolver =
                aura.getMaceResolverController();

        AttackManager attackManager =
                getAttackManager();

        lines.add(
                new IndicatorLine(
                        "RES  "
                                + resolver
                                .getSetting()
                                .getSelected()
                                + "  |  "
                                + resolver
                                .getPredictionSetting()
                                .getSelected(),
                        COLOR_TEXT
                )
        );

        boolean smashReady =
                resolver.isForcedSmashReady();

        lines.add(
                new IndicatorLine(
                        "SMASH  "
                                + (
                                smashReady
                                        ? "READY"
                                        : "WAIT"
                        )
                                + "  |  FALL "
                                + formatOne(
                                mc.player.fallDistance
                        ),
                        smashReady
                                ? COLOR_GOOD
                                : COLOR_WARN
                )
        );

        Rotation serverRotation =
                RotationManager.INSTANCE
                        .getServerRotation();

        float serverPitch =
                serverRotation != null
                        ? serverRotation.getPitch()
                        : mc.player.getPitch();

        lines.add(
                new IndicatorLine(
                        "PITCH  S "
                                + formatSigned(
                                serverPitch
                        )
                                + "  |  C "
                                + formatSigned(
                                mc.player.getPitch()
                        ),
                        COLOR_MUTED
                )
        );

        LivingEntity target = Aura.target;

        if (target != null
                && target.isAlive()) {
            double distance =
                    resolver
                            .getDebugTargetDistance(
                                    target
                            );

            double safeRange =
                    resolver
                            .getDebugSafeRange(
                                    target
                            );

            boolean safe =
                    resolver
                            .isDebugTargetInSafeReach(
                                    target
                            );

            lines.add(
                    new IndicatorLine(
                            "TARGET  "
                                    + target
                                    .getName()
                                    .getString()
                                    + "  "
                                    + formatTwo(distance)
                                    + " / "
                                    + formatTwo(safeRange),
                            safe
                                    ? COLOR_GOOD
                                    : COLOR_BAD
                    )
            );
        } else {
            lines.add(
                    new IndicatorLine(
                            "TARGET  none",
                            COLOR_WARN
                    )
            );
        }

        if (attackManager != null) {
            String status =
                    attackManager
                            .getMaceDebugStatus();

            updateStickyMaceEvent(status);

            lines.add(
                    new IndicatorLine(
                            "STATE  " + status,
                            statusColor(status)
                    )
            );

            if (!stickyMaceEvent.isEmpty()
                    && System.currentTimeMillis()
                    <= stickyMaceEventUntil
                    && !stickyMaceEvent.equals(
                    status
            )) {
                lines.add(
                        new IndicatorLine(
                                "LAST  "
                                        + stickyMaceEvent,
                                statusColor(
                                        stickyMaceEvent
                                )
                        )
                );
            }

            String lastHit =
                    attackManager
                            .getMaceLastHitDebugLine();

            if (!lastHit.isEmpty()) {
                lines.add(
                        new IndicatorLine(
                                lastHit,
                                COLOR_GOOD
                        )
                );
            }
        }

        return lines;
    }

    private void updateStickyMaceEvent(
            String status
    ) {
        if (status == null
                || status.isEmpty()) {
            return;
        }

        if (status.equals(
                lastObservedMaceStatus
        )) {
            return;
        }

        lastObservedMaceStatus = status;

        if (status.equalsIgnoreCase(
                "Ready to smash"
        )
                || status.equalsIgnoreCase(
                "Aura disabled"
        )) {
            return;
        }

        stickyMaceEvent = status;
        stickyMaceEventUntil =
                System.currentTimeMillis()
                        + INDICATOR_EVENT_HOLD_MS;
    }

    private AttackManager getAttackManager() {
        try {
            if (Initialization.getInstance()
                    == null
                    || Initialization.getInstance()
                    .getManager()
                    == null
                    || Initialization.getInstance()
                    .getManager()
                    .getAttackPerpetrator()
                    == null) {
                return null;
            }

            return Initialization.getInstance()
                    .getManager()
                    .getAttackPerpetrator()
                    .getAttackHandler();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void drawIndicatorLine(
            IndicatorLine line,
            float x,
            float y
    ) {
        float textWidth =
                Fonts.BOLD.getWidth(
                        line.text(),
                        INDICATOR_FONT_SIZE
                );

        float textHeight =
                Fonts.BOLD.getHeight(
                        INDICATOR_FONT_SIZE
                );

        float width = textWidth + 8.0F;

        float height =
                Math.max(
                        7.0F,
                        textHeight + 2.0F
                );

        Render2D.rect(
                x,
                y,
                width,
                height,
                COLOR_BACKGROUND,
                2.5F
        );

        Render2D.rect(
                x,
                y,
                1.5F,
                height,
                line.color(),
                2.5F,
                0.0F,
                0.0F,
                2.5F
        );

        Fonts.BOLD.draw(
                line.text(),
                x + 4.0F,
                y + (
                        height - textHeight
                ) / 2.0F,
                INDICATOR_FONT_SIZE,
                line.color()
        );
    }

    private int statusColor(
            String status
    ) {
        String lower =
                status.toLowerCase(
                        Locale.ROOT
                );

        if (lower.contains(
                "ready to smash"
        )
                || lower.contains(
                "swap ready"
        )) {
            return COLOR_GOOD;
        }

        if (lower.contains(
                "out of reach"
        )
                || lower.contains(
                "no mace"
        )
                || lower.contains(
                "blocked"
        )
                || lower.contains(
                "rotation not aligned"
        )) {
            return COLOR_BAD;
        }

        if (lower.contains(
                "waiting"
        )
                || lower.contains(
                "not ready"
        )
                || lower.contains(
                "sync"
        )
                || lower.contains(
                "re-arm"
        )
                || lower.contains(
                "used"
        )) {
            return COLOR_WARN;
        }

        return COLOR_TEXT;
    }

    private String formatSigned(
            float value
    ) {
        return String.format(
                Locale.US,
                "%+.1f",
                value
        );
    }

    private String formatOne(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                value
        );
    }

    private String formatTwo(
            double value
    ) {
        if (!Double.isFinite(value)
                || value == Double.MAX_VALUE) {
            return "-";
        }

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }

    private record IndicatorLine(
            String text,
            int color
    ) {
    }
}
