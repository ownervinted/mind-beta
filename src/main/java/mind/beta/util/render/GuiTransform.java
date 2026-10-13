package mind.beta.util.render;

public final class GuiTransform {

    private static boolean active;
    private static float scale = 1f;
    private static float centerX;
    private static float centerY;

    private GuiTransform() {
    }

    public static void begin(float newScale, float newCenterX, float newCenterY) {
        scale = Math.max(0f, Math.min(1f, newScale));
        centerX = newCenterX;
        centerY = newCenterY;
        active = true;
    }

    public static void end() {
        active = false;
        scale = 1f;
        centerX = 0f;
        centerY = 0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static float getScale() {
        return scale;
    }

    public static float transformX(float x) {
        return centerX + (x - centerX) * scale;
    }

    public static float transformY(float y) {
        return centerY + (y - centerY) * scale;
    }

    public static float transformWidth(float width) {
        return width * scale;
    }

    public static float transformHeight(float height) {
        return height * scale;
    }

    public static float transformRadius(float radius) {
        return radius * scale;
    }

    public static float transformThickness(float thickness) {
        return thickness * scale;
    }

    public static float getCenterX() {
        return centerX;
    }

    public static float getCenterY() {
        return centerY;
    }

    public static float[] transformRect(float x, float y, float width, float height) {
        return new float[]{
                transformX(x),
                transformY(y),
                transformWidth(width),
                transformHeight(height)
        };
    }
}
