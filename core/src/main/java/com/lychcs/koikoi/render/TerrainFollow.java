package com.lychcs.koikoi.render;

/**
 * Smooth terrain-following helpers for the first-person camera and 3D entities.
 *
 * <p>Sampling the heightfield directly every frame makes the camera snap on
 * every terrain vertex. These helpers ease the current height towards the
 * sampled ground height with a frame-rate independent exponential filter and
 * then clamp the result above the surface, so walking up a hill never lets the
 * eye sink into the ground.</p>
 *
 * <p>Pure Java, no GL: the behaviour is covered by unit tests.</p>
 */
public final class TerrainFollow {

    /** Largest delta accepted by one smoothing step (protects after a hitch). */
    public static final float MAX_STEP_SECONDS = 0.25f;

    private TerrainFollow() {
    }

    /**
     * Eases {@code current} towards {@code target}.
     *
     * @param current current smoothed height
     * @param target  freshly sampled terrain height
     * @param delta   frame time in seconds
     * @param rate    convergence rate per second (higher = snappier)
     * @return the smoothed height; never overshoots the target
     */
    public static float smooth(float current, float target, float delta, float rate) {
        if (!(rate > 0f) || !(delta > 0f) || Float.isNaN(current)) {
            return target;
        }
        float step = Math.min(delta, MAX_STEP_SECONDS);
        float alpha = 1f - (float) Math.exp(-rate * step);
        if (alpha <= 0f) {
            return current;
        }
        if (alpha >= 1f) {
            return target;
        }
        return current + (target - current) * alpha;
    }

    /**
     * Keeps a smoothed height at least {@code minOffset} above the sampled
     * ground so the camera/entity can never clip into a rising slope.
     */
    public static float clampAboveGround(float height, float ground, float minOffset) {
        float floor = ground + Math.max(0f, minOffset);
        return height < floor ? floor : height;
    }
}
