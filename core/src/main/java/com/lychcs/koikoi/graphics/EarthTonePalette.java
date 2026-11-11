package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Color;

/**
 * Maps the bright 128-colour master palette onto a muted Japanese earth-tone
 * range: moss, bamboo beige, slate, burnt umber and soft terracotta.
 *
 * <p>The transform keeps each source hue recognizable but desaturates harsh
 * neon colours, adds a warm parchment bias and caps highlights so sprites and
 * the player silhouette never turn into a white/grey block.</p>
 */
public final class EarthTonePalette {

    /** Warm parchment/earth bias shared by every mapped colour. */
    private static final Color WARM_BIAS = new Color(0.58f, 0.50f, 0.38f, 1f);
    /** Fraction of the original chroma removed before the warm bias. */
    private static final float DESATURATION = 0.28f;
    /** Fraction of the warm earth bias mixed into each colour. */
    private static final float WARM_MIX = 0.22f;
    /** Highlights are capped here so pale entries stay pastel, never white. */
    private static final float MAX_CHANNEL = 0.80f;

    private EarthTonePalette() {
    }

    /** Returns a new earth-toned copy of one palette entry. */
    public static Color transform(Color source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        Color color = new Color(source);
        float luma = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
        color.lerp(new Color(luma, luma, luma, 1f), DESATURATION);
        color.lerp(WARM_BIAS, WARM_MIX);

        float max = Math.max(color.r, Math.max(color.g, color.b));
        if (max > MAX_CHANNEL) {
            float scale = MAX_CHANNEL / max;
            color.r *= scale;
            color.g *= scale;
            color.b *= scale;
        }

        color.r = Math.min(1f, Math.max(0f, color.r));
        color.g = Math.min(1f, Math.max(0f, color.g));
        color.b = Math.min(1f, Math.max(0f, color.b));
        color.a = 1f;
        return color;
    }

    /** Returns a new array with every palette entry earth-toned. */
    public static Color[] transform(Color[] palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        Color[] mapped = new Color[palette.length];
        for (int i = 0; i < palette.length; i++) {
            mapped[i] = transform(palette[i]);
        }
        return mapped;
    }
}
