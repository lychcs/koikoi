package com.lychcs.koikoi.graphics;

/**
 * Cardinal facing of the ASCII avatar, derived from the dominant movement
 * component.
 *
 * <p>The last facing is retained while idle, so the character keeps looking in
 * the direction it last walked.</p>
 */
public enum AsciiFacing {
    DOWN,
    UP,
    LEFT,
    RIGHT;

    /** Movement below this speed (px/s) keeps the previous facing. */
    public static final float IDLE_SPEED = 8f;

    /** true for {@link #LEFT} and {@link #RIGHT}. */
    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }

    /** true for {@link #UP} and {@link #DOWN}. */
    public boolean isVertical() {
        return this == UP || this == DOWN;
    }

    /**
     * Updates the facing from a velocity vector. The dominant component wins;
     * slow/idle motion returns {@code previous} unchanged.
     *
     * @param velocityX world pixels per second
     * @param velocityY world pixels per second (positive = up)
     * @param previous  the last facing ({@code null} falls back to {@link #DOWN})
     */
    public static AsciiFacing fromVelocity(float velocityX, float velocityY,
                                           AsciiFacing previous) {
        AsciiFacing fallback = previous == null ? DOWN : previous;
        if (velocityX * velocityX + velocityY * velocityY < IDLE_SPEED * IDLE_SPEED) {
            return fallback;
        }
        if (Math.abs(velocityX) > Math.abs(velocityY)) {
            return velocityX > 0f ? RIGHT : LEFT;
        }
        return velocityY > 0f ? UP : DOWN;
    }
}
