package mind.beta.util.render.sky;

import org.joml.Matrix4f;

public final class SkyFrameState {

    private static final Matrix4f INVERSE_VIEW = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();
    private static boolean valid;

    private SkyFrameState() {
    }

    public static void capture(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (viewMatrix == null || projectionMatrix == null) {
            valid = false;
            return;
        }

        INVERSE_VIEW.set(viewMatrix).invert();
        INVERSE_PROJECTION.set(projectionMatrix).invert();
        valid = true;
    }

    public static Snapshot snapshot() {
        if (!valid) return null;
        return new Snapshot(new Matrix4f(INVERSE_VIEW), new Matrix4f(INVERSE_PROJECTION));
    }

    public record Snapshot(Matrix4f inverseView, Matrix4f inverseProjection) {
    }
}
