package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * Geometry of a ground-projected Hanko seal (circle based AoE).
 *
 * <p>All methods are pure math and unit-tested independently of the ECS and the
 * renderer: the expanding radius drives both the shader animation and the damage
 * boundary, so both always agree.</p>
 */
public final class SealGeometry {

    /** Tolerance so a target exactly on the boundary counts as inside. */
    public static final float BOUNDARY_EPSILON = 0.001f;

    private SealGeometry() {
    }

    /** true, if the point lies inside or exactly on the seal boundary. */
    public static boolean contains(float centerX, float centerY, float radius,
                                   float pointX, float pointY) {
        if (radius <= 0f) {
            return false;
        }
        float dx = pointX - centerX;
        float dy = pointY - centerY;
        return dx * dx + dy * dy <= radius * radius + BOUNDARY_EPSILON;
    }

    /** true, if a circle (target radius) overlaps the seal area. */
    public static boolean overlaps(float centerX, float centerY, float radius,
                                   float targetX, float targetY, float targetRadius) {
        return contains(centerX, centerY, radius + Math.max(0f, targetRadius), targetX, targetY);
    }

    /**
     * Expanding seal radius with a cubic ease-out, so the seal snaps open and
     * settles at its final size.
     *
     * @param progress 0 at spawn, 1 at full expansion (clamped)
     */
    public static float expandingRadius(float startRadius, float endRadius, float progress) {
        float t = clamp01(progress);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        return startRadius + (endRadius - startRadius) * eased;
    }

    /**
     * Clamps the aim point to the caster's maximum range. A target inside the
     * range is returned unchanged.
     */
    public static Vector2 clampCastTarget(float playerX, float playerY,
                                          float aimX, float aimY,
                                          float maxRange, Vector2 out) {
        float dx = aimX - playerX;
        float dy = aimY - playerY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length <= maxRange || length == 0f || maxRange <= 0f) {
            return out.set(aimX, aimY);
        }
        return out.set(playerX + dx / length * maxRange, playerY + dy / length * maxRange);
    }

    /** Snaps a world position to the centre of the enclosing square grid cell. */
    public static Vector2 snapToGrid(float x, float y, float gridSize, Vector2 out) {
        if (gridSize <= 0f) {
            return out.set(x, y);
        }
        float half = gridSize * 0.5f;
        return out.set(
            (float) Math.floor(x / gridSize) * gridSize + half,
            (float) Math.floor(y / gridSize) * gridSize + half);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
