package com.lychcs.koikoi.render;

import java.util.Random;

/**
 * Seeded 2D simplex noise used by the procedural 3D heightmap.
 *
 * <p>Pure Java and free of libGDX/OpenGL calls so the terrain shape can be
 * validated in plain unit tests. {@link #noise2(float, float)} returns a value
 * in {@code [-1, 1]}; {@link #fbm2(float, float, int, float, float, float)}
 * layers octaves for rolling hills.</p>
 *
 * <p>The implementation follows Stefan Gustavson's classic 2D simplex
 * algorithm: skewed triangular grid, three gradient contributions, and a
 * radial falloff that keeps the result continuous and cheap.</p>
 */
public final class SimplexNoise {

    /** Skew factor of the 2D simplex grid. */
    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1f);
    /** Unskew factor of the 2D simplex grid. */
    private static final float G2 = (3f - (float) Math.sqrt(3.0)) / 6f;

    /** 12 gradient directions of the classic simplex implementation. */
    private static final float[][] GRAD3 = {
        {1f, 1f}, {-1f, 1f}, {1f, -1f}, {-1f, -1f},
        {1f, 0f}, {-1f, 0f}, {1f, 0f}, {-1f, 0f},
        {0f, 1f}, {0f, -1f}, {0f, 1f}, {0f, -1f}
    };

    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    /**
     * Creates a noise field from a deterministic seed.
     *
     * @param seed any long; equal seeds produce identical terrain
     */
    public SimplexNoise(long seed) {
        int[] source = new int[256];
        for (int i = 0; i < source.length; i++) {
            source[i] = i;
        }
        Random random = new Random(seed);
        for (int i = source.length - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int value = source[i];
            source[i] = source[swap];
            source[swap] = value;
        }
        for (int i = 0; i < 512; i++) {
            int value = source[i & 255];
            perm[i] = value;
            permMod12[i] = value % 12;
        }
    }

    /** 2D simplex noise in the range {@code [-1, 1]}. */
    public float noise2(float xin, float yin) {
        float skew = (xin + yin) * F2;
        int i = fastFloor(xin + skew);
        int j = fastFloor(yin + skew);

        float unskew = (i + j) * G2;
        float x0 = xin - (i - unskew);
        float y0 = yin - (j - unskew);

        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = 0f;
        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if (t0 > 0f) {
            float[] g = GRAD3[permMod12[ii + perm[jj]]];
            t0 *= t0;
            n0 = t0 * t0 * (g[0] * x0 + g[1] * y0);
        }

        float n1 = 0f;
        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if (t1 > 0f) {
            float[] g = GRAD3[permMod12[ii + i1 + perm[jj + j1]]];
            t1 *= t1;
            n1 = t1 * t1 * (g[0] * x1 + g[1] * y1);
        }

        float n2 = 0f;
        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if (t2 > 0f) {
            float[] g = GRAD3[permMod12[ii + 1 + perm[jj + 1]]];
            t2 *= t2;
            n2 = t2 * t2 * (g[0] * x2 + g[1] * y2);
        }

        // 70 is the empirical scaling factor of this gradient set.
        return 70f * (n0 + n1 + n2);
    }

    /**
     * Fractal Brownian motion built from {@link #noise2(float, float)}.
     *
     * @param x         base x coordinate (already scaled by the caller)
     * @param y         base y coordinate (already scaled by the caller)
     * @param octaves   number of layers (at least 1)
     * @param frequency frequency multiplier of the first octave
     * @param lacunarity frequency multiplier between octaves
     * @param gain      amplitude multiplier between octaves
     * @return a value roughly in {@code [-1, 1]}
     */
    public float fbm2(float x, float y, int octaves, float frequency,
                      float lacunarity, float gain) {
        int layers = Math.max(1, octaves);
        float amplitude = 1f;
        float frequencyScale = frequency;
        float sum = 0f;
        float normalizer = 0f;
        for (int i = 0; i < layers; i++) {
            sum += noise2(x * frequencyScale, y * frequencyScale) * amplitude;
            normalizer += amplitude;
            amplitude *= gain;
            frequencyScale *= lacunarity;
        }
        if (normalizer <= 0f) {
            return 0f;
        }
        return sum / normalizer;
    }

    private static int fastFloor(float value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
