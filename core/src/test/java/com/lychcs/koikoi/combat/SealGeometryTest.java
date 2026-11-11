package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hanko AoE boundary tests for the ground seal and the cast range clamp. */
class SealGeometryTest {

    @Test
    void centreIsInside() {
        assertTrue(SealGeometry.contains(100f, 100f, 50f, 100f, 100f));
    }

    @Test
    void exactlyOnTheBoundaryCountsAsInside() {
        assertTrue(SealGeometry.contains(0f, 0f, 50f, 50f, 0f));
        assertTrue(SealGeometry.contains(0f, 0f, 50f, 0f, -50f));
    }

    @Test
    void justOutsideTheBoundaryIsRejected() {
        assertFalse(SealGeometry.contains(0f, 0f, 50f, 50.5f, 0f));
        assertFalse(SealGeometry.contains(0f, 0f, 50f, 35f, 36f));
    }

    @Test
    void zeroRadiusContainsNothing() {
        assertFalse(SealGeometry.contains(0f, 0f, 0f, 0f, 0f));
    }

    @Test
    void targetRadiusExtendsTheSealBoundary() {
        // Distance 60, seal 50 + target radius 10 => exactly on the boundary.
        assertTrue(SealGeometry.overlaps(0f, 0f, 50f, 60f, 0f, 10f));
        assertFalse(SealGeometry.overlaps(0f, 0f, 50f, 60.5f, 0f, 10f));
    }

    @Test
    void expandingRadiusIsMonotonicAndSettlesAtTheEndRadius() {
        float previous = -1f;
        for (int step = 0; step <= 20; step++) {
            float radius = SealGeometry.expandingRadius(20f, 120f, step / 20f);
            assertTrue(radius >= previous, "radius must never shrink");
            previous = radius;
        }

        assertEquals(20f, SealGeometry.expandingRadius(20f, 120f, 0f), 0.0001f);
        assertEquals(120f, SealGeometry.expandingRadius(20f, 120f, 1f), 0.0001f);
        assertEquals(120f, SealGeometry.expandingRadius(20f, 120f, 5f), 0.0001f);
    }

    @Test
    void castTargetInsideTheRangeIsUnchanged() {
        Vector2 out = SealGeometry.clampCastTarget(10f, 10f, 40f, 10f, 100f, new Vector2());

        assertEquals(40f, out.x, 0.0001f);
        assertEquals(10f, out.y, 0.0001f);
    }

    @Test
    void castTargetIsClampedToTheMaximumRange() {
        Vector2 out = SealGeometry.clampCastTarget(0f, 0f, 300f, 0f, 100f, new Vector2());

        assertEquals(100f, out.x, 0.0001f);
        assertEquals(0f, out.y, 0.0001f);
        assertEquals(100f, out.len(), 0.0001f);
    }

    @Test
    void gridSnappingUsesCellCentres() {
        Vector2 out = SealGeometry.snapToGrid(70f, 130f, 64f, new Vector2());

        assertEquals(96f, out.x, 0.0001f);
        assertEquals(160f, out.y, 0.0001f);
    }
}
