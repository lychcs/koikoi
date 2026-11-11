package com.lychcs.koikoi.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates dominant-axis facing and idle facing retention. */
class AsciiFacingTest {

    @Test
    void cardinalDirectionsFollowTheDominantComponent() {
        assertEquals(AsciiFacing.RIGHT, AsciiFacing.fromVelocity(100f, 0f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.LEFT, AsciiFacing.fromVelocity(-100f, 0f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.UP, AsciiFacing.fromVelocity(0f, 100f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.DOWN, AsciiFacing.fromVelocity(0f, -100f, AsciiFacing.UP));
    }

    @Test
    void diagonalMovementPicksTheDominantAxis() {
        assertEquals(AsciiFacing.RIGHT, AsciiFacing.fromVelocity(100f, 60f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.LEFT, AsciiFacing.fromVelocity(-100f, 60f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.UP, AsciiFacing.fromVelocity(60f, 100f, AsciiFacing.DOWN));
        assertEquals(AsciiFacing.DOWN, AsciiFacing.fromVelocity(60f, -100f, AsciiFacing.UP));
    }

    @Test
    void idleKeepsTheLastFacing() {
        assertEquals(AsciiFacing.LEFT, AsciiFacing.fromVelocity(0f, 0f, AsciiFacing.LEFT));
        assertEquals(AsciiFacing.UP, AsciiFacing.fromVelocity(2f, 3f, AsciiFacing.UP));
        assertEquals(AsciiFacing.DOWN, AsciiFacing.fromVelocity(0f, 0f, null));
    }
}
