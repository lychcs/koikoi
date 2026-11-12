package com.lychcs.koikoi.render;

/**
 * Procedural CPU heightmap of the 3D overworld ground.
 *
 * <p>The field covers a square patch of world space (libGDX units, 1 unit =
 * one 64 px map tile) and stores one height per grid vertex. Height queries
 * are bilinearly interpolated, so the third-person camera, billboards and seal
 * decals all follow the same smooth surface the terrain mesh is built from.</p>
 *
 * <p>Terrain is generated with fractal simplex noise ({@link SimplexNoise}) and
 * is therefore deterministic per seed while still varying with the location
 * seed of the ASCII map. No GPU resource is owned here; this class is pure
 * data and can be unit tested without a GL context.</p>
 */
public final class Heightfield {

    /** Minimum grid resolution; smaller values are clamped. */
    public static final int MIN_RESOLUTION = 2;

    /** Smallest accepted patch size in world units. */
    public static final float MIN_SIZE = 1f;

    /** Base noise frequency in cycles per world unit (rolling hills). */
    private static final float BASE_FREQUENCY = 0.045f;
    /** Ridge frequency of the secondary detail layer. */
    private static final float RIDGE_FREQUENCY = 0.085f;
    /** Octaves of the fractal noise. */
    private static final int OCTAVES = 4;

    /** Default width of the rocky mountain rim around the terrain patch. */
    public static final float DEFAULT_RIM_WIDTH = 11f;
    /** Default height of the rocky mountain rim above the rolling hills. */
    public static final float DEFAULT_RIM_HEIGHT = 26f;

    private final int resolution;
    private final float originX;
    private final float originZ;
    private final float size;
    private final float step;
    private final float amplitude;
    private final float[] heights;
    private float minHeight;
    private float maxHeight;

    private Heightfield(int resolution, float originX, float originZ, float size,
                        float amplitude, float[] heights) {
        this.resolution = resolution;
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.step = size / resolution;
        this.amplitude = amplitude;
        this.heights = heights;
        recomputeBounds();
    }

    /**
     * Generates a deterministic heightmap.
     *
     * @param seed       location seed; equal seeds produce identical terrain
     * @param originX    world X of the patch's minimum corner
     * @param originZ    world Z of the patch's minimum corner
     * @param size       side length of the square patch in world units
     * @param resolution number of grid cells per side
     * @param amplitude  peak height variation in world units
     */
    public static Heightfield generate(long seed, float originX, float originZ,
                                       float size, int resolution, float amplitude) {
        if (!(size >= MIN_SIZE) || Float.isNaN(size) || Float.isInfinite(size)) {
            throw new IllegalArgumentException("size must be >= " + MIN_SIZE + " but was " + size);
        }
        int grid = Math.max(MIN_RESOLUTION, resolution);
        float amplitudeScale = Math.max(0.001f, Math.abs(amplitude));

        SimplexNoise noise = new SimplexNoise(seed);
        int vertices = grid + 1;
        float step = size / grid;
        float[] heights = new float[vertices * vertices];

        for (int row = 0; row < vertices; row++) {
            float z = originZ + row * step;
            for (int col = 0; col < vertices; col++) {
                float x = originX + col * step;

                float hills = noise.fbm2(x, z, OCTAVES, BASE_FREQUENCY, 2.02f, 0.5f);
                // A sharpened ridge layer adds hill crests and shallow valleys.
                float ridge = 1f - Math.abs(noise.noise2(
                    x * RIDGE_FREQUENCY + 13.7f, z * RIDGE_FREQUENCY - 8.1f));
                ridge = ridge * 2f - 1f;

                heights[row * vertices + col] =
                    amplitudeScale * (hills * 0.72f + ridge * 0.28f);
            }
        }

        return new Heightfield(grid, originX, originZ, size, amplitudeScale, heights);
    }

    /** Bilinearly interpolated terrain height at a world position (units). */
    public float getHeight(float worldX, float worldZ) {
        float localX = (worldX - originX) / step;
        float localZ = (worldZ - originZ) / step;

        float clampedX = clamp(localX, 0f, resolution);
        float clampedZ = clamp(localZ, 0f, resolution);

        int col = (int) clampedX;
        int row = (int) clampedZ;
        if (col >= resolution) {
            col = resolution - 1;
        }
        if (row >= resolution) {
            row = resolution - 1;
        }

        float fx = clampedX - col;
        float fz = clampedZ - row;

        float h00 = getHeightAt(col, row);
        float h10 = getHeightAt(col + 1, row);
        float h01 = getHeightAt(col, row + 1);
        float h11 = getHeightAt(col + 1, row + 1);

        float bottom = h00 + (h10 - h00) * fx;
        float top = h01 + (h11 - h01) * fx;
        return bottom + (top - bottom) * fz;
    }

    /** Height of one grid vertex; indices are clamped to the patch. */
    public float getHeightAt(int col, int row) {
        int clampedCol = col < 0 ? 0 : Math.min(col, resolution);
        int clampedRow = row < 0 ? 0 : Math.min(row, resolution);
        return heights[clampedRow * (resolution + 1) + clampedCol];
    }

    /**
     * Levels a circular clearing (temple platform, shrine forecourt) to the
     * average height inside the circle.
     *
     * <p>All vertices inside {@code radius} receive that average height; between
     * {@code radius} and {@code radius + blend} the original terrain fades back
     * in with a smoothstep, so the clearing has no hard rim and the surrounding
     * hills still roll naturally. Min/max bounds are recomputed afterwards.</p>
     *
     * @param centerX world X of the clearing centre
     * @param centerZ world Z of the clearing centre
     * @param radius  radius of the flat area in world units
     * @param blend   width of the smooth transition ring in world units
     */
    public void flattenCircle(float centerX, float centerZ, float radius, float blend) {
        if (!(radius > 0f) || Float.isNaN(radius)) {
            return;
        }
        int perSide = resolution + 1;
        float radius2 = radius * radius;
        float blendWidth = Math.max(0.001f, blend);

        double sum = 0d;
        int count = 0;
        for (int row = 0; row < perSide; row++) {
            float dz = getZ(row) - centerZ;
            for (int col = 0; col < perSide; col++) {
                float dx = getX(col) - centerX;
                if (dx * dx + dz * dz <= radius2) {
                    sum += heights[row * perSide + col];
                    count++;
                }
            }
        }
        if (count == 0) {
            return;
        }

        float average = (float) (sum / count);
        float outer = radius + blendWidth;
        for (int row = 0; row < perSide; row++) {
            float dz = getZ(row) - centerZ;
            for (int col = 0; col < perSide; col++) {
                float dx = getX(col) - centerX;
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                if (distance > outer) {
                    continue;
                }
                int index = row * perSide + col;
                if (distance <= radius) {
                    heights[index] = average;
                } else {
                    float t = (distance - radius) / blendWidth;
                    float smooth = t * t * (3f - 2f * t);
                    heights[index] = average + (heights[index] - average) * smooth;
                }
            }
        }
        recomputeBounds();
    }

    private void recomputeBounds() {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (float height : heights) {
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }
        this.minHeight = lowest;
        this.maxHeight = highest;
    }

    /**
     * Raises a steep, jagged rocky rim around the patch edges.
     *
     * <p>The world boundary becomes a visible mountain wall instead of an
     * invisible map edge: heights rise with a smoothstep towards the patch
     * border, modulated by a deterministic hash so the silhouette stays
     * jagged. The playable interior is untouched because the rim only affects
     * the outer {@code rimWidth} units.</p>
     *
     * @param rimWidth  width of the raised rim in world units
     * @param rimHeight peak raise at the patch edge in world units
     */
    public void raiseRim(float rimWidth, float rimHeight) {
        if (!(rimWidth > 0f) || !(rimHeight > 0f)) {
            return;
        }
        int perSide = resolution + 1;
        float maxX = originX + size;
        float maxZ = originZ + size;
        float width = Math.min(rimWidth, size * 0.45f);

        for (int row = 0; row < perSide; row++) {
            float z = getZ(row);
            float edgeZ = Math.min(z - originZ, maxZ - z);
            for (int col = 0; col < perSide; col++) {
                float x = getX(col);
                float edgeX = Math.min(x - originX, maxX - x);
                float distance = Math.min(edgeX, edgeZ);
                if (distance >= width) {
                    continue;
                }
                float t = 1f - distance / width;
                float smooth = t * t * (3f - 2f * t);
                float jagged = 0.78f + 0.44f * hash01(col, row);
                heights[row * perSide + col] += rimHeight * smooth * jagged;
            }
        }
        recomputeBounds();
    }

    /**
     * Raises a square mountain ring (Chebyshev distance from the hub) with four
     * narrow cardinal gaps, so the four seasonal realms are separated by a
     * visible ridge but stay connected through the paved passes.
     *
     * @param centerX      world X of the hub centre
     * @param centerZ      world Z of the hub centre
     * @param radius       Chebyshev distance of the ridge crest in world units
     * @param width        ridge width in world units
     * @param height       peak raise in world units
     * @param gateHalfWidth half width of the four cardinal passes
     */
    public void raiseChebyshevRing(float centerX, float centerZ,
                                   float radius, float width, float height,
                                   float gateHalfWidth) {
        if (!(radius > 0f) || !(width > 0f) || !(height > 0f)) {
            return;
        }
        int perSide = resolution + 1;
        float halfWidth = width * 0.5f;
        for (int row = 0; row < perSide; row++) {
            float z = getZ(row);
            float dz = z - centerZ;
            for (int col = 0; col < perSide; col++) {
                float x = getX(col);
                float dx = x - centerX;
                if (Math.abs(dx) <= gateHalfWidth || Math.abs(dz) <= gateHalfWidth) {
                    continue; // narrow cardinal pass
                }
                float chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                float edge = Math.abs(chebyshev - radius);
                if (edge >= halfWidth) {
                    continue;
                }
                float t = 1f - edge / halfWidth;
                float smooth = t * t * (3f - 2f * t);
                float jagged = 0.82f + 0.36f * hash01(col, row);
                heights[row * perSide + col] += height * smooth * jagged;
            }
        }
        recomputeBounds();
    }

    /** Deterministic 0..1 hash for the jagged mountain silhouette. */
    private static float hash01(int col, int row) {
        int hash = col * 0x27d4eb2d ^ row * 0x9e3779b9;
        hash ^= hash >>> 15;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }

    /** World X of a grid column. */
    public float getX(int col) {
        return originX + clamp(col, 0, resolution) * step;
    }

    /** World Z of a grid row. */
    public float getZ(int row) {
        return originZ + clamp(row, 0, resolution) * step;
    }

    public int getResolution() {
        return resolution;
    }

    /** Vertices per side ({@code resolution + 1}). */
    public int getVerticesPerSide() {
        return resolution + 1;
    }

    public float getOriginX() {
        return originX;
    }

    public float getOriginZ() {
        return originZ;
    }

    public float getSize() {
        return size;
    }

    /** Distance between two grid vertices in world units. */
    public float getStep() {
        return step;
    }

    public float getAmplitude() {
        return amplitude;
    }

    public float getMinHeight() {
        return minHeight;
    }

    public float getMaxHeight() {
        return maxHeight;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}

