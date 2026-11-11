package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * Procedural squash and stretch for the player sprite.
 *
 * <p>The GDD formula is used for the base pose:</p>
 * <pre>
 * scaleX = 1.0 + 0.15 * vx / vMax
 * scaleY = 1.0 - 0.15 * vy / vMax
 * </pre>
 *
 * <p>A dash adds a short-lived stretch along the movement axis and a matching
 * squash on the perpendicular axis. All results are clamped to a safe range so
 * the sprite can never degenerate.</p>
 */
public final class SquashStretch {

    /** Maximum deformation of the base formula (15 %). */
    public static final float MAX_DELTA = 0.15f;

    /** Extra deformation caused by a dash (12 %). */
    public static final float MAX_DASH_PULSE = 0.12f;

    /** Lower bound of any axis scale. */
    public static final float MIN_SCALE = 0.70f;

    /** Upper bound of any axis scale. */
    public static final float MAX_SCALE = 1.35f;

    private SquashStretch() {
    }

    /**
     * Horizontal scale for the given velocity.
     *
     * @param dashPulse extra stretch along the dominant dash axis (0 when not dashing)
     */
    public static float scaleX(float velocityX, float velocityY, float maxSpeed, float dashPulse) {
        float scale = 1f + MAX_DELTA * ratio(velocityX, maxSpeed);
        if (dashPulse > 0f) {
            scale += Math.abs(velocityX) >= Math.abs(velocityY) ? dashPulse : -dashPulse * 0.5f;
        }
        return clamp(scale);
    }

    /**
     * Vertical scale for the given velocity.
     *
     * @param dashPulse extra stretch along the dominant dash axis (0 when not dashing)
     */
    public static float scaleY(float velocityX, float velocityY, float maxSpeed, float dashPulse) {
        float scale = 1f - MAX_DELTA * ratio(velocityY, maxSpeed);
        if (dashPulse > 0f) {
            scale += Math.abs(velocityY) > Math.abs(velocityX) ? dashPulse : -dashPulse * 0.5f;
        }
        return clamp(scale);
    }

    /** Writes {@code (scaleX, scaleY)} into {@code out} without allocating. */
    public static Vector2 compute(float velocityX, float velocityY, float maxSpeed,
                                  float dashPulse, Vector2 out) {
        return out.set(
            scaleX(velocityX, velocityY, maxSpeed, dashPulse),
            scaleY(velocityX, velocityY, maxSpeed, dashPulse));
    }

    private static float ratio(float velocity, float maxSpeed) {
        if (maxSpeed <= 0f) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, velocity / maxSpeed));
    }

    private static float clamp(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}
