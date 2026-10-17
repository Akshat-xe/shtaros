package grandtweaks.motion;

import grandtweaks.GrandConfig;

public class GrandMotion {
    private static double smoothedDX = 0.0;
    private static double smoothedDY = 0.0;
    private static double velocity = 0.0;

    public static void applySmoothing(double rawDX, double rawDY) {
        if (!GrandConfig.motionSmooth) {
            smoothedDX = rawDX;
            smoothedDY = rawDY;
            velocity = Math.hypot(rawDX, rawDY);
            return;
        }

        // 60Hz Anti-Judder / Anti-Tear Responsive Camera Damping
        // Blends fast instantaneous response with momentum decay to remove jagged frame pacing tearing
        double alpha = 0.50; // 50% responsive current input, 50% smooth momentum
        smoothedDX = smoothedDX * (1.0 - alpha) + rawDX * alpha;
        smoothedDY = smoothedDY * (1.0 - alpha) + rawDY * alpha;

        // Zero-drift deadband
        if (Math.abs(smoothedDX) < 0.002) smoothedDX = 0.0;
        if (Math.abs(smoothedDY) < 0.002) smoothedDY = 0.0;

        velocity = Math.hypot(smoothedDX, smoothedDY);
    }

    public static double getSmoothedDX() {
        return smoothedDX;
    }

    public static double getSmoothedDY() {
        return smoothedDY;
    }

    public static double getVelocity() {
        return velocity;
    }
}
