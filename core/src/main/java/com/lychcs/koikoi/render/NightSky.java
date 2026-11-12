package com.lychcs.koikoi.render;

import com.badlogic.gdx.math.Vector3;

/**
 * Fixed celestial configuration of the ASCII night sky.
 *
 * <p>The moon sits at one world direction and is drawn by the ASCII post shader
 * as a cluster of bright terminal glyphs with procedural craters; cloud bands
 * drift slowly across the sky with {@code ~}, {@code -} and {@code .} ink. The
 * direction is shared with unit tests so the "moon must be above the horizon"
 * invariant cannot silently regress.</p>
 */
public final class NightSky {

    /** Direction from the camera towards the moon (normalized, above horizon). */
    public static final Vector3 MOON_DIRECTION =
        new Vector3(-0.30f, 0.40f, -0.87f).nor();

    /** Angular radius of the moon disc in degrees. */
    public static final float MOON_RADIUS_DEGREES = 5.5f;

    /** Horizontal and vertical drift of the cloud layers (screen-space units/s). */
    public static final float CLOUD_DRIFT_X = 0.013f;
    public static final float CLOUD_DRIFT_Y = 0.004f;

    /** Warm ivory ink of the moon. */
    public static final float MOON_RED = 1.00f;
    public static final float MOON_GREEN = 0.92f;
    public static final float MOON_BLUE = 0.74f;

    /** Cool, dim ink of the drifting cloud glyphs. */
    public static final float CLOUD_RED = 0.34f;
    public static final float CLOUD_GREEN = 0.50f;
    public static final float CLOUD_BLUE = 0.46f;

    private NightSky() {
    }
}
