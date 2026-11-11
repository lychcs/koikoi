package com.lychcs.koikoi.combat;

/**
 * Receives procedural particle requests from ECS systems.
 *
 * <p>Implementations (the overworld screen) create particle entities; systems
 * stay independent of textures and of the renderer.</p>
 */
public interface ParticleBurst {

    /** Dust and cherry-blossom petals around a world position. */
    void burst(float x, float y, int dustCount, int petalCount);

    /** Bright sparks of exorcism magic around a world position. */
    default void sparks(float x, float y, int count) {
        burst(x, y, 0, 0);
    }

    /**
     * ASCII glyph impact burst (for example {@code "*#%*"} on an explosive
     * card). Default implementations fall back to the normal dust/petal burst.
     */
    default void glyphBurst(float x, float y, String glyphs,
                            float red, float green, float blue) {
        burst(x, y, glyphs == null ? 0 : glyphs.length(), 0);
    }
}
