package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * Smooth delay-follow and sine bobbing of a Yokai companion.
 *
 * <p>Following uses frame-rate independent exponential smoothing:
 * {@code t = 1 - exp(-lag * delta)}. The companion therefore trails the player
 * smoothly instead of snapping, and the result never overshoots the target. A
 * far-away companion snaps back to its slot (leash) so it can never be lost.</p>
 */
public final class CompanionFollow {

    /** Default smoothing strength in inverse seconds. */
    public static final float DEFAULT_LAG = 4.5f;

    /** Beyond this distance the companion teleports back to its slot. */
    public static final float LEASH_SNAP_DISTANCE = 360f;

    /** Frequency of the vertical bobbing (GDD: {@code sin(time * 3.0)}). */
    public static final float BOB_FREQUENCY = 3.0f;

    /** Amplitude of the vertical bobbing (GDD: {@code * 4.0}). */
    public static final float BOB_AMPLITUDE = 4.0f;

    private CompanionFollow() {
    }

    /**
     * One smoothing step from {@code (currentX, currentY)} towards the target.
     * Writes the new position into {@code out}.
     */
    public static Vector2 step(float currentX, float currentY,
                               float targetX, float targetY,
                               float lag, float delta, Vector2 out) {
        float dx = targetX - currentX;
        float dy = targetY - currentY;
        float distanceSquared = dx * dx + dy * dy;

        if (distanceSquared >= LEASH_SNAP_DISTANCE * LEASH_SNAP_DISTANCE) {
            return out.set(targetX, targetY);
        }

        float safeLag = Math.max(0f, lag);
        float safeDelta = Math.max(0f, delta);
        float t = 1f - (float) Math.exp(-safeLag * safeDelta);
        t = Math.max(0f, Math.min(1f, t));

        return out.set(currentX + dx * t, currentY + dy * t);
    }

    /** Vertical bobbing offset: {@code sin(time * 3.0 + phase) * 4.0}. */
    public static float bobOffset(float time, float phase) {
        return (float) Math.sin(time * BOB_FREQUENCY + phase) * BOB_AMPLITUDE;
    }
}
