package com.lychcs.koikoi.combat;

/**
 * Exponential acceleration/deceleration helper for movement with weight.
 *
 * <p>The ECS keeps its instantaneous MOBA velocity; the screen eases the
 * Box2D body toward that target so the character accelerates and coasts with
 * Witcher-style weight instead of snapping between speeds.</p>
 */
public final class VelocitySmoothing {

    private VelocitySmoothing() {
    }

    /**
     * Eases {@code current} toward {@code target}.
     *
     * @param current current velocity component
     * @param target  desired velocity component
     * @param delta   frame time in seconds
     * @param rate    exponential rate (1/s); larger is snappier
     * @return the smoothed component
     */
    public static float approach(float current, float target, float delta, float rate) {
        if (!(delta > 0f) || !(rate > 0f)) {
            return target;
        }
        float alpha = 1f - (float) Math.exp(-rate * delta);
        return current + (target - current) * alpha;
    }
}
