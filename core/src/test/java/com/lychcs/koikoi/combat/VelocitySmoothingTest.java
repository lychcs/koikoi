package com.lychcs.koikoi.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the weighted acceleration/deceleration curve. */
class VelocitySmoothingTest {

    @Test
    void approachEasesTowardTheTarget() {
        float value = VelocitySmoothing.approach(0f, 100f, 0.1f, 10f);
        assertEquals(100f * (1f - (float) Math.exp(-1f)), value, 0.001f);
    }

    @Test
    void zeroDeltaSnapsToTheTarget() {
        assertEquals(100f, VelocitySmoothing.approach(0f, 100f, 0f, 10f), 1e-4f);
    }

    @Test
    void decelerationUsesTheSameExponentialContract() {
        float value = VelocitySmoothing.approach(100f, 0f, 0.1f, 14f);
        assertTrue(value > 0f && value < 100f);
        assertEquals(100f - 100f * (1f - (float) Math.exp(-1.4f)), value, 0.001f);
    }

    @Test
    void repeatedStepsConverge() {
        float value = 0f;
        for (int i = 0; i < 120; i++) {
            value = VelocitySmoothing.approach(value, 120f, 1f / 60f, 10f);
        }
        assertEquals(120f, value, 0.5f);
    }
}
