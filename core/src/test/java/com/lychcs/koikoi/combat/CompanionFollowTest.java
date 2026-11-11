package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Delay-follow physics and sine bobbing of the Yokai companions. */
class CompanionFollowTest {

    private final Vector2 out = new Vector2();

    @Test
    void stepMovesTowardsTheTargetWithoutOvershoot() {
        CompanionFollow.step(0f, 0f, 100f, 0f, CompanionFollow.DEFAULT_LAG, 0.1f, out);

        assertTrue(out.x > 0f, "companion must move towards the player");
        assertTrue(out.x < 100f, "companion must not overshoot");
        assertEquals(0f, out.y, 0.0001f);
    }

    @Test
    void stepConvergesToTheTarget() {
        float x = 0f;
        float y = 0f;

        for (int i = 0; i < 200; i++) {
            CompanionFollow.step(x, y, 120f, 80f, CompanionFollow.DEFAULT_LAG, 0.016f, out);
            x = out.x;
            y = out.y;
        }

        assertEquals(120f, x, 0.1f);
        assertEquals(80f, y, 0.1f);
    }

    @Test
    void stepIsFrameRateIndependent() {
        CompanionFollow.step(0f, 0f, 100f, 0f, CompanionFollow.DEFAULT_LAG, 0.1f, out);
        CompanionFollow.step(out.x, out.y, 100f, 0f, CompanionFollow.DEFAULT_LAG, 0.1f, out);
        float twoSmallSteps = out.x;

        CompanionFollow.step(0f, 0f, 100f, 0f, CompanionFollow.DEFAULT_LAG, 0.2f, out);
        float oneBigStep = out.x;

        assertEquals(oneBigStep, twoSmallSteps, 0.0001f);
    }

    @Test
    void farAwayCompanionSnapsBackOntoTheLeash() {
        CompanionFollow.step(0f, 0f,
            CompanionFollow.LEASH_SNAP_DISTANCE + 10f, 0f,
            CompanionFollow.DEFAULT_LAG, 0.016f, out);

        assertEquals(CompanionFollow.LEASH_SNAP_DISTANCE + 10f, out.x, 0.0001f);
        assertEquals(0f, out.y, 0.0001f);
    }

    @Test
    void bobFollowsTheGddFormula() {
        // sin(time * 3.0 + phase) * 4.0
        assertEquals(0f, CompanionFollow.bobOffset(0f, 0f), 0.0001f);
        assertEquals(4f, CompanionFollow.bobOffset((float) (Math.PI / 6.0), 0f), 0.0001f);
        assertEquals(-4f, CompanionFollow.bobOffset((float) (Math.PI / 2.0), 0f), 0.0001f);
    }

    @Test
    void bobStaysWithinItsAmplitude() {
        for (float time = 0f; time < 10f; time += 0.01f) {
            float offset = CompanionFollow.bobOffset(time, 1.3f);
            assertTrue(Math.abs(offset) <= CompanionFollow.BOB_AMPLITUDE + 0.0001f);
        }
    }
}
