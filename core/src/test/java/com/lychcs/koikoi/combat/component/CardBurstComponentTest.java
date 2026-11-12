package com.lychcs.koikoi.combat.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the lifetime curve of the 3D card impact bursts. */
class CardBurstComponentTest {

    @Test
    void progressAndAlphaFollowTheLifetime() {
        CardBurstComponent burst = new CardBurstComponent();
        burst.duration = 0.5f;

        assertEquals(0f, burst.progress(), 0.0001f);
        assertEquals(1f, burst.alpha(), 0.0001f);
        assertFalse(burst.isExpired());

        burst.age = 0.25f;
        assertEquals(0.5f, burst.progress(), 0.0001f);
        assertEquals(0.5f, burst.alpha(), 0.0001f);

        burst.age = 0.5f;
        assertEquals(1f, burst.progress(), 0.0001f);
        assertEquals(0f, burst.alpha(), 0.0001f);
        assertTrue(burst.isExpired());
    }

    @Test
    void zeroDurationExpiresImmediately() {
        CardBurstComponent burst = new CardBurstComponent();
        burst.duration = 0f;

        assertEquals(1f, burst.progress(), 0.0001f);
        assertTrue(burst.isExpired());
    }

    @Test
    void ageIsClampedOutsideTheLifetime() {
        CardBurstComponent burst = new CardBurstComponent();
        burst.duration = 0.4f;
        burst.age = -2f;
        assertEquals(0f, burst.progress(), 0.0001f);

        burst.age = 10f;
        assertEquals(1f, burst.progress(), 0.0001f);
    }
}
