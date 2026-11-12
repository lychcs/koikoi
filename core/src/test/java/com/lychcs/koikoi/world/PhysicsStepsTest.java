package com.lychcs.koikoi.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates Box2D substepping for the pixel-scale dash. */
class PhysicsStepsTest {

    @Test
    void walkSpeedFitsInASingleStepAtSixtyFps() {
        assertEquals(1, PhysicsSteps.forFrame(120f, 1f / 60f));
    }

    @Test
    void dashIsSplitIntoFourSteps() {
        assertEquals(4, PhysicsSteps.forFrame(460f, 1f / 60f),
            "a 460 px/s dash must not be clamped to the 120 px/s walk cap");
    }

    @Test
    void lowFpsWalkStillGetsTheFullDistance() {
        assertEquals(2, PhysicsSteps.forFrame(120f, 1f / 30f));
    }

    @Test
    void hugeFramesAreCapped() {
        assertEquals(PhysicsSteps.MAX_SUBSTEPS, PhysicsSteps.forFrame(5000f, 0.1f));
    }

    @Test
    void invalidInputsUseOneStep() {
        assertEquals(1, PhysicsSteps.forFrame(0f, 1f / 60f));
        assertEquals(1, PhysicsSteps.forFrame(120f, 0f));
        assertEquals(1, PhysicsSteps.forFrame(-10f, 0.016f));
    }
}
