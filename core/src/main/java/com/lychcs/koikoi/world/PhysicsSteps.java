package com.lychcs.koikoi.world;

/**
 * Box2D substepping helper for the pixel-scale overworld.
 *
 * <p>Box2D clamps a body's translation to {@link #MAX_TRANSLATION} world units
 * per step. The ASCII world uses pixels as world units, so at 60 fps this cap
 * is only 2 px/step (120 px/s): a 460 px/s dash would silently move at walking
 * speed and its ghost trail would collapse. Splitting a fast frame into several
 * smaller steps keeps the dash fast while preserving Box2D collisions.</p>
 */
public final class PhysicsSteps {

    /** Box2D's built-in maximum translation per step (b2_maxTranslation). */
    public static final float MAX_TRANSLATION = 2.0f;

    /** Upper bound so a very slow frame cannot spiral into thousands of steps. */
    public static final int MAX_SUBSTEPS = 8;

    private PhysicsSteps() {
    }

    /**
     * Number of physics sub-steps needed for one frame.
     *
     * @param speedPixelsPerSecond fastest body speed of the frame
     * @param delta                frame time in seconds
     * @return 1..{@link #MAX_SUBSTEPS}
     */
    public static int forFrame(float speedPixelsPerSecond, float delta) {
        if (!(delta > 0f) || !(speedPixelsPerSecond > 0f)) {
            return 1;
        }
        float translation = speedPixelsPerSecond * delta;
        if (translation <= MAX_TRANSLATION) {
            return 1;
        }
        int needed = (int) Math.ceil(translation / MAX_TRANSLATION);
        return Math.max(1, Math.min(MAX_SUBSTEPS, needed));
    }
}
