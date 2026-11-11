package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Squash & stretch tests for the GDD formula and the dash pulse bounds. */
class SquashStretchTest {

    private static final float MAX_SPEED = 120f;

    @Test
    void neutralPoseAtRest() {
        assertEquals(1f, SquashStretch.scaleX(0f, 0f, MAX_SPEED, 0f), 0.0001f);
        assertEquals(1f, SquashStretch.scaleY(0f, 0f, MAX_SPEED, 0f), 0.0001f);
    }

    @Test
    void horizontalMotionStretchesHorizontally() {
        assertEquals(1.15f, SquashStretch.scaleX(MAX_SPEED, 0f, MAX_SPEED, 0f), 0.0001f);
        assertEquals(1f, SquashStretch.scaleY(MAX_SPEED, 0f, MAX_SPEED, 0f), 0.0001f);
    }

    @Test
    void horizontalMotionSquashesWhenMovingLeft() {
        assertEquals(0.85f, SquashStretch.scaleX(-MAX_SPEED, 0f, MAX_SPEED, 0f), 0.0001f);
    }

    @Test
    void verticalMotionSquashesVertically() {
        assertEquals(0.85f, SquashStretch.scaleY(0f, MAX_SPEED, MAX_SPEED, 0f), 0.0001f);
        assertEquals(1.15f, SquashStretch.scaleY(0f, -MAX_SPEED, MAX_SPEED, 0f), 0.0001f);
    }

    @Test
    void deformationNeverExceedsTheSafeRange() {
        for (float velocity = -10_000f; velocity <= 10_000f; velocity += 250f) {
            float scaleX = SquashStretch.scaleX(velocity, velocity, MAX_SPEED, 0f);
            float scaleY = SquashStretch.scaleY(velocity, velocity, MAX_SPEED, 0f);

            assertTrue(scaleX >= SquashStretch.MIN_SCALE && scaleX <= SquashStretch.MAX_SCALE);
            assertTrue(scaleY >= SquashStretch.MIN_SCALE && scaleY <= SquashStretch.MAX_SCALE);
        }
    }

    @Test
    void dashPulseStretchesAlongTheDashAxis() {
        float scaleX = SquashStretch.scaleX(MAX_SPEED, 0f, MAX_SPEED, SquashStretch.MAX_DASH_PULSE);
        float scaleY = SquashStretch.scaleY(MAX_SPEED, 0f, MAX_SPEED, SquashStretch.MAX_DASH_PULSE);

        assertTrue(scaleX > 1.15f, "dash should stretch the horizontal axis, was " + scaleX);
        assertTrue(scaleY < 1f, "dash should squash the vertical axis, was " + scaleY);
    }

    @Test
    void computeWritesBothScales() {
        Vector2 out = SquashStretch.compute(MAX_SPEED, 0f, MAX_SPEED, 0f, new Vector2());

        assertEquals(1.15f, out.x, 0.0001f);
        assertEquals(1f, out.y, 0.0001f);
    }

    @Test
    void zeroMaxSpeedIsHandled() {
        assertEquals(1f, SquashStretch.scaleX(50f, 0f, 0f, 0f), 0.0001f);
        assertEquals(1f, SquashStretch.scaleY(0f, 50f, 0f, 0f), 0.0001f);
    }
}
