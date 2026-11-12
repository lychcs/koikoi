package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the smooth terrain following of the camera and 3D entities. */
class TerrainFollowTest {

    @Test
    void smoothApproachesTheTargetWithoutOvershooting() {
        float current = -2f;
        float target = 4f;
        float smoothed = TerrainFollow.smooth(current, target, 0.016f, 14f);

        assertTrue(smoothed > current, "the height must move towards the target");
        assertTrue(smoothed < target, "one small step must not overshoot the target");
    }

    @Test
    void smoothConvergesOverSeveralFrames() {
        float height = 0f;
        for (int i = 0; i < 200; i++) {
            height = TerrainFollow.smooth(height, 5f, 1f / 60f, 14f);
        }
        assertEquals(5f, height, 0.01f);
    }

    @Test
    void smoothWithNaNOrZeroDeltaFallsBackToTheSample() {
        assertEquals(3f, TerrainFollow.smooth(Float.NaN, 3f, 0.016f, 14f), 0.0001f);
        assertEquals(3f, TerrainFollow.smooth(1f, 3f, 0f, 14f), 0.0001f);
        assertEquals(3f, TerrainFollow.smooth(1f, 3f, 0.016f, 0f), 0.0001f);
    }

    @Test
    void smoothClampsLargeFrameSteps() {
        float clampedStep = TerrainFollow.smooth(0f, 10f, 5f, 14f);
        float maxStep = TerrainFollow.smooth(0f, 10f, TerrainFollow.MAX_STEP_SECONDS, 14f);
        assertEquals(maxStep, clampedStep, 0.0001f);
    }

    @Test
    void heightIsClampedAboveTheGround() {
        assertEquals(1.6f, TerrainFollow.clampAboveGround(1.0f, 0.5f, 1.1f), 0.0001f);
        assertEquals(2.5f, TerrainFollow.clampAboveGround(2.5f, 0.5f, 1.1f), 0.0001f);
        assertEquals(0.5f, TerrainFollow.clampAboveGround(-3.0f, 0.5f, 0f), 0.0001f);
    }
}
