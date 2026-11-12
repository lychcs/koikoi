package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the fixed night-sky configuration used by the ASCII shader. */
class NightSkyTest {

    @Test
    void moonDirectionIsNormalizedAndAboveTheHorizon() {
        assertEquals(1f, NightSky.MOON_DIRECTION.len(), 0.0001f);
        assertTrue(NightSky.MOON_DIRECTION.y > 0.2f,
            "the moon must be reachable in the sky, not below the horizon");
        assertTrue(NightSky.MOON_DIRECTION.z < 0f,
            "the default north-facing view should see the moon");
    }

    @Test
    void moonAndCloudInkStayInGamut() {
        assertTrue(NightSky.MOON_RED >= NightSky.MOON_GREEN);
        assertTrue(NightSky.MOON_GREEN >= NightSky.MOON_BLUE);
        assertTrue(NightSky.MOON_RED <= 1f && NightSky.CLOUD_BLUE <= 1f);
        assertTrue(NightSky.CLOUD_RED >= 0f && NightSky.CLOUD_GREEN > 0f);
    }

    @Test
    void cloudsDriftAndTheMoonHasAUsableAngularRadius() {
        assertNotEquals(0f, NightSky.CLOUD_DRIFT_X);
        assertNotEquals(0f, NightSky.CLOUD_DRIFT_Y);
        assertTrue(NightSky.MOON_RADIUS_DEGREES > 0.5f);
        assertTrue(NightSky.MOON_RADIUS_DEGREES < 15f);
    }
}
