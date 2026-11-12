package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the procedural 3D heightmap that the third-person camera walks on. */
class HeightfieldTest {

    private static final long SEED = 0x5EED_1A5F_0BADF00DL;
    private static final int RESOLUTION = 32;
    private static final float SIZE = 64f;
    private static final float AMPLITUDE = 5.5f;

    @Test
    void sameSeedProducesIdenticalTerrain() {
        Heightfield first = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        Heightfield second = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);

        for (int row = 0; row <= RESOLUTION; row++) {
            for (int col = 0; col <= RESOLUTION; col++) {
                assertEquals(first.getHeightAt(col, row), second.getHeightAt(col, row), 0.0001f);
            }
        }
        assertEquals(first.getSize(), second.getSize(), 0.0001f);
    }

    @Test
    void differentSeedsProduceDifferentHills() {
        Heightfield first = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        Heightfield second = Heightfield.generate(SEED + 1, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);

        boolean differs = false;
        for (int row = 0; row <= RESOLUTION && !differs; row++) {
            for (int col = 0; col <= RESOLUTION && !differs; col++) {
                differs = Math.abs(first.getHeightAt(col, row) - second.getHeightAt(col, row)) > 0.001f;
            }
        }
        assertTrue(differs, "a different seed must grow different terrain");
    }

    @Test
    void heightsStayInsideTheAmplitude() {
        Heightfield field = Heightfield.generate(SEED, 0f, 0f, SIZE, RESOLUTION, AMPLITUDE);

        assertTrue(field.getMinHeight() >= -AMPLITUDE - 0.001f);
        assertTrue(field.getMaxHeight() <= AMPLITUDE + 0.001f);
        assertTrue(field.getMaxHeight() - field.getMinHeight() > 0.2f,
            "rolling hills must have actual height variation");
    }

    @Test
    void gridVerticesSampleExactly() {
        Heightfield field = Heightfield.generate(SEED, -10f, 7f, SIZE, RESOLUTION, AMPLITUDE);

        for (int row = 0; row <= RESOLUTION; row += 5) {
            for (int col = 0; col <= RESOLUTION; col += 5) {
                assertEquals(field.getHeightAt(col, row),
                    field.getHeight(field.getX(col), field.getZ(row)), 0.0001f);
            }
        }
        assertEquals(SIZE / RESOLUTION, field.getStep(), 0.0001f);
        assertEquals(RESOLUTION + 1, field.getVerticesPerSide());
    }

    @Test
    void queriesOutsideThePatchAreClamped() {
        Heightfield field = Heightfield.generate(SEED, 0f, 0f, SIZE, RESOLUTION, AMPLITUDE);

        assertEquals(field.getHeightAt(0, 0), field.getHeight(-1000f, -1000f), 0.0001f);
        assertEquals(field.getHeightAt(RESOLUTION, RESOLUTION),
            field.getHeight(1000f, 1000f), 0.0001f);
    }

    @Test
    void interpolatedHeightsStayBetweenTheirCorners() {
        Heightfield field = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);

        for (int row = 0; row < RESOLUTION; row++) {
            for (int col = 0; col < RESOLUTION; col++) {
                float cornerMin = Math.min(
                    Math.min(field.getHeightAt(col, row), field.getHeightAt(col + 1, row)),
                    Math.min(field.getHeightAt(col, row + 1), field.getHeightAt(col + 1, row + 1)));
                float cornerMax = Math.max(
                    Math.max(field.getHeightAt(col, row), field.getHeightAt(col + 1, row)),
                    Math.max(field.getHeightAt(col, row + 1), field.getHeightAt(col + 1, row + 1)));

                float midX = field.getX(col) + field.getStep() * 0.5f;
                float midZ = field.getZ(row) + field.getStep() * 0.5f;
                float sampled = field.getHeight(midX, midZ);

                assertTrue(sampled >= cornerMin - 0.0001f && sampled <= cornerMax + 0.0001f,
                    "bilinear sampling must stay inside its cell");
            }
        }
    }

    @Test
    void invalidSizeIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> Heightfield.generate(SEED, 0f, 0f, 0f, RESOLUTION, AMPLITUDE));
    }

    @Test
    void resolutionIsRaisedToTheMinimum() {
        Heightfield field = Heightfield.generate(SEED, 0f, 0f, SIZE, 1, AMPLITUDE);

        assertEquals(Heightfield.MIN_RESOLUTION, field.getResolution());
        assertNotEquals(0, field.getVerticesPerSide());
    }

    @Test
    void flattenCircleLevelsTheClearing() {
        Heightfield field = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        Heightfield natural = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        field.flattenCircle(0f, 0f, 10f, 3f);

        float reference = field.getHeight(0f, 0f);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12.0;
            float x = (float) Math.cos(angle) * 8f;
            float z = (float) Math.sin(angle) * 8f;
            assertEquals(reference, field.getHeight(x, z), 0.3f,
                "the clearing interior must be level");
        }

        // Distant hills stay untouched and the bounds stay consistent.
        assertEquals(natural.getHeight(28f, 28f), field.getHeight(28f, 28f), 0.0001f);
        assertTrue(field.getMinHeight() >= -AMPLITUDE - 0.001f);
        assertTrue(field.getMaxHeight() <= AMPLITUDE + 0.001f);
    }

    @Test
    void flattenCircleIgnoresInvalidClearingRequests() {
        Heightfield field = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        float before = field.getHeightAt(0, 0);

        field.flattenCircle(10000f, 10000f, 1f, 1f);
        field.flattenCircle(0f, 0f, -5f, 1f);

        assertEquals(before, field.getHeightAt(0, 0), 0.0001f);
    }

    @Test
    void raiseRimTurnsThePatchEdgeIntoAmountainWall() {
        Heightfield field = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        float centerBefore = field.getHeight(0f, 0f);
        float maxBefore = field.getMaxHeight();

        field.raiseRim(Heightfield.DEFAULT_RIM_WIDTH, Heightfield.DEFAULT_RIM_HEIGHT);

        assertEquals(centerBefore, field.getHeight(0f, 0f), 0.001f,
            "the playable interior must stay untouched");
        assertTrue(field.getMaxHeight() > maxBefore + 10f,
            "the rim must raise the outer edge into mountains");
        float edge = field.getHeight(-31.5f, 0f);
        assertTrue(edge > centerBefore + 10f,
            "the patch border must be a visible cliff, was " + edge);
    }

    @Test
    void raiseRimIgnoresInvalidArguments() {
        Heightfield field = Heightfield.generate(SEED, -32f, -32f, SIZE, RESOLUTION, AMPLITUDE);
        float before = field.getHeightAt(0, 0);

        field.raiseRim(0f, 10f);
        field.raiseRim(10f, -1f);

        assertEquals(before, field.getHeightAt(0, 0), 0.0001f);
    }

}
