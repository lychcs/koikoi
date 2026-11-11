package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * 8-way movement vector helper.
 *
 * <p>Keyboard input is converted into a unit-length direction: cardinals keep
 * their length of 1, diagonals are normalised to {@code 1/sqrt(2)} per axis so a
 * diagonal walk is exactly as fast as a straight one.</p>
 */
public final class MovementVector {

    /** Per-axis component of a normalised diagonal direction. */
    public static final float DIAGONAL = 0.70710678f;

    private MovementVector() {
    }

    /**
     * Writes the normalised 8-way direction of the axis input into {@code out}.
     * Only the sign of each axis is used, so analog values behave like keys.
     */
    public static Vector2 fromAxis(float axisX, float axisY, Vector2 out) {
        float x = Math.signum(axisX);
        float y = Math.signum(axisY);

        if (x != 0f && y != 0f) {
            return out.set(x * DIAGONAL, y * DIAGONAL);
        }
        return out.set(x, y);
    }

    /** true, if the axis input requests any movement. */
    public static boolean hasInput(float axisX, float axisY) {
        return axisX != 0f || axisY != 0f;
    }

    /** Length of the direction produced by {@link #fromAxis(float, float, Vector2)}. */
    public static float lengthOf(float axisX, float axisY) {
        return fromAxis(axisX, axisY, new Vector2()).len();
    }
}
