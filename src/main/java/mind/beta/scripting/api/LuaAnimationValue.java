package mind.beta.scripting.api;

public final class LuaAnimationValue {
    private enum Mode {
        IDLE,
        TWEEN,
        SPRING
    }

    private double value;
    private double startValue;
    private double targetValue;
    private double velocity;
    private long startedAtNanos;
    private long lastUpdateNanos;
    private long durationNanos;
    private LuaEasing.Curve curve = LuaEasing.named("smoothstep");
    private Mode mode = Mode.IDLE;
    private double springStiffness = 180.0;
    private double springDamping = 22.0;

    public LuaAnimationValue(double initialValue) {
        value = initialValue;
        startValue = initialValue;
        targetValue = initialValue;
        long now = System.nanoTime();
        startedAtNanos = now;
        lastUpdateNanos = now;
    }

    public double get() {
        update();
        return value;
    }

    public double getVelocity() {
        update();
        return velocity;
    }

    public double getTarget() {
        return targetValue;
    }

    public double progress() {
        update();
        if (mode != Mode.TWEEN || durationNanos <= 0L) {
            return isDone() ? 1.0 : 0.0;
        }
        return clamp01((double) (System.nanoTime() - startedAtNanos) / durationNanos);
    }

    public boolean isDone() {
        update();
        return mode == Mode.IDLE;
    }

    public void snap(double nextValue) {
        value = nextValue;
        startValue = nextValue;
        targetValue = nextValue;
        velocity = 0.0;
        mode = Mode.IDLE;
        long now = System.nanoTime();
        startedAtNanos = now;
        lastUpdateNanos = now;
    }

    public void animateTo(double target, long durationMs, LuaEasing.Curve easing) {
        update();
        startValue = value;
        targetValue = target;
        durationNanos = Math.max(1L, durationMs) * 1_000_000L;
        curve = easing == null ? LuaEasing.named("smoothstep") : easing;
        velocity = 0.0;
        startedAtNanos = System.nanoTime();
        lastUpdateNanos = startedAtNanos;
        mode = Mode.TWEEN;
    }

    public void springTo(double target, double stiffness, double damping) {
        update();
        targetValue = target;
        springStiffness = Math.max(0.1, stiffness);
        springDamping = Math.max(0.0, damping);
        lastUpdateNanos = System.nanoTime();
        mode = Mode.SPRING;
    }

    public void stop() {
        update();
        targetValue = value;
        velocity = 0.0;
        mode = Mode.IDLE;
    }

    private void update() {
        long now = System.nanoTime();
        switch (mode) {
            case TWEEN -> updateTween(now);
            case SPRING -> updateSpring(now);
            default -> lastUpdateNanos = now;
        }
    }

    private void updateTween(long now) {
        double t = clamp01((double) (now - startedAtNanos) / durationNanos);
        double eased = curve.apply(t);
        double previous = value;
        value = startValue + (targetValue - startValue) * eased;

        double dt = Math.max(1.0e-6, (now - lastUpdateNanos) / 1_000_000_000.0);
        velocity = (value - previous) / dt;
        lastUpdateNanos = now;

        if (t >= 1.0) {
            value = targetValue;
            velocity = 0.0;
            mode = Mode.IDLE;
        }
    }

    private void updateSpring(long now) {
        double totalDt = Math.min(0.1, Math.max(0.0, (now - lastUpdateNanos) / 1_000_000_000.0));
        lastUpdateNanos = now;

        if (totalDt <= 0.0) {
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(totalDt / (1.0 / 120.0)));
        double dt = totalDt / steps;
        for (int i = 0; i < steps; i++) {
            double displacement = targetValue - value;
            double acceleration = displacement * springStiffness - velocity * springDamping;
            velocity += acceleration * dt;
            value += velocity * dt;
        }

        if (Math.abs(targetValue - value) < 0.0005 && Math.abs(velocity) < 0.001) {
            value = targetValue;
            velocity = 0.0;
            mode = Mode.IDLE;
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
