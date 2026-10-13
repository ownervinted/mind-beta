package mind.beta.scripting.api;

import java.util.Locale;

public final class LuaEasing {
    @FunctionalInterface
    public interface Curve {
        double apply(double t);
    }

    private LuaEasing() {
    }

    public static Curve named(String name) {
        String normalized = normalize(name);
        return t -> apply(normalized, t);
    }

    public static Curve cubicBezierCurve(double x1, double y1, double x2, double y2) {
        return t -> cubicBezier(t, x1, y1, x2, y2);
    }

    public static double apply(String name, double t) {
        t = clamp01(t);
        String n = normalize(name);
        return switch (n) {
            case "linear" -> t;
            case "smoothstep" -> t * t * (3.0 - 2.0 * t);
            case "smootherstep" -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            case "inquad" -> t * t;
            case "outquad" -> 1.0 - (1.0 - t) * (1.0 - t);
            case "inoutquad" -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case "incubic" -> t * t * t;
            case "outcubic" -> 1.0 - Math.pow(1.0 - t, 3.0);
            case "inoutcubic" -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case "inquart" -> t * t * t * t;
            case "outquart" -> 1.0 - Math.pow(1.0 - t, 4.0);
            case "inoutquart" -> t < 0.5 ? 8.0 * Math.pow(t, 4.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0;
            case "inquint" -> Math.pow(t, 5.0);
            case "outquint" -> 1.0 - Math.pow(1.0 - t, 5.0);
            case "inoutquint" -> t < 0.5 ? 16.0 * Math.pow(t, 5.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 5.0) / 2.0;
            case "insine" -> 1.0 - Math.cos((t * Math.PI) / 2.0);
            case "outsine" -> Math.sin((t * Math.PI) / 2.0);
            case "inoutsine" -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case "inexpo" -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            case "outexpo" -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
            case "inoutexpo" -> {
                if (t == 0.0) yield 0.0;
                if (t == 1.0) yield 1.0;
                yield t < 0.5
                        ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0
                        : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
            }
            case "incirc" -> 1.0 - Math.sqrt(1.0 - t * t);
            case "outcirc" -> Math.sqrt(1.0 - Math.pow(t - 1.0, 2.0));
            case "inoutcirc" -> t < 0.5
                    ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2.0))) / 2.0
                    : (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0;
            case "inback" -> {
                double c1 = 1.70158;
                double c3 = c1 + 1.0;
                yield c3 * t * t * t - c1 * t * t;
            }
            case "outback" -> {
                double c1 = 1.70158;
                double c3 = c1 + 1.0;
                yield 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
            }
            case "inoutback" -> {
                double c1 = 1.70158;
                double c2 = c1 * 1.525;
                yield t < 0.5
                        ? (Math.pow(2.0 * t, 2.0) * ((c2 + 1.0) * 2.0 * t - c2)) / 2.0
                        : (Math.pow(2.0 * t - 2.0, 2.0) * ((c2 + 1.0) * (t * 2.0 - 2.0) + c2) + 2.0) / 2.0;
            }
            case "outelastic" -> {
                if (t == 0.0) yield 0.0;
                if (t == 1.0) yield 1.0;
                double c4 = (2.0 * Math.PI) / 3.0;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
            default -> t * t * (3.0 - 2.0 * t);
        };
    }

    public static double cubicBezier(double progress, double x1, double y1, double x2, double y2) {
        double x = clamp01(progress);
        x1 = clamp01(x1);
        x2 = clamp01(x2);

        double t = x;
        for (int i = 0; i < 8; i++) {
            double currentX = sampleCurve(t, x1, x2) - x;
            if (Math.abs(currentX) < 1.0e-6) {
                break;
            }
            double derivative = sampleDerivative(t, x1, x2);
            if (Math.abs(derivative) < 1.0e-6) {
                break;
            }
            t -= currentX / derivative;
            t = clamp01(t);
        }

        double lower = 0.0;
        double upper = 1.0;
        for (int i = 0; i < 12; i++) {
            double currentX = sampleCurve(t, x1, x2);
            if (Math.abs(currentX - x) < 1.0e-6) {
                break;
            }
            if (currentX < x) {
                lower = t;
            } else {
                upper = t;
            }
            t = (lower + upper) * 0.5;
        }

        return sampleCurve(t, y1, y2);
    }

    private static double sampleCurve(double t, double p1, double p2) {
        double inv = 1.0 - t;
        return 3.0 * inv * inv * t * p1 + 3.0 * inv * t * t * p2 + t * t * t;
    }

    private static double sampleDerivative(double t, double p1, double p2) {
        double inv = 1.0 - t;
        return 3.0 * inv * inv * p1
                + 6.0 * inv * t * (p2 - p1)
                + 3.0 * t * t * (1.0 - p2);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "smoothstep";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("ease", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
