package com.lychcs.koikoi.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hit flash and kiting knockback on the roaming ASCII Yokai. */
class OverworldYokaiEntityTest {

    private static OverworldYokaiEntity yokaiAt(float x, float y) {
        return new OverworldYokaiEntity("test", "oni", "Oni",
            OverworldYokaiEntity.WalkAnimations.placeholder(), x, y, 64f, 64f);
    }

    @Test
    void hitFlashStartsBrightAndFadesOut() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);

        yokai.flashHit(OverworldYokaiEntity.HIT_FLASH_SECONDS);
        assertTrue(yokai.isHitFlashing());
        assertEquals(1f, yokai.getHitFlashRatio(), 0.001f);

        yokai.update(0.08f, box -> false);
        assertEquals(0.5f, yokai.getHitFlashRatio(), 0.02f);

        yokai.update(0.1f, box -> false);
        assertFalse(yokai.isHitFlashing());
        assertEquals(0f, yokai.getHitFlashRatio(), 0.001f);
    }

    @Test
    void stateTimeAdvancesWithMovementUpdates() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        assertEquals(0f, yokai.getStateTime(), 0.0001f);
        yokai.update(0.05f, box -> false);
        assertEquals(0.05f, yokai.getStateTime(), 0.001f);
        yokai.update(0.05f, box -> false);
        assertEquals(0.10f, yokai.getStateTime(), 0.001f);
    }

    @Test
    void knockbackMovesAwayFromTheCasterAndDecays() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        float startX = yokai.getWorldX();

        yokai.knockback(1f, 0f, 30f);
        yokai.update(0.05f, box -> false);

        assertTrue(yokai.getWorldX() > startX, "heavy cards must create kiting distance");
        assertEquals(0f, yokai.getKnockbackRemaining(), 0.001f);
    }

    @Test
    void knockbackStopsAtBlockingTerrain() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        float startX = yokai.getWorldX();

        yokai.knockback(1f, 0f, 30f);
        yokai.update(0.05f, box -> true);

        assertEquals(startX, yokai.getWorldX(), 0.001f);
        assertEquals(0f, yokai.getKnockbackRemaining(), 0.001f);
    }

    @Test
    void aggroChaseFollowsWaypointsPastTheRoamBox() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        yokai.setAggro(true);
        yokai.setChaseTarget(420f, 100f);

        // 25 clamped 0.1 s steps at 104 px/s ≈ 260 px, far beyond the 3x3 box.
        for (int i = 0; i < 25; i++) {
            yokai.update(0.1f, box -> false);
        }

        assertTrue(yokai.isAggro());
        assertTrue(yokai.getWorldX() > 250f,
            "an aggro Yokai must leave its roam box while chasing, was "
                + yokai.getWorldX());
        assertTrue(yokai.getWorldX() <= 420.01f);
    }

    @Test
    void aggroChaseStopsAtBlockingTerrain() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        yokai.setAggro(true);
        yokai.setChaseTarget(420f, 100f);
        float startX = yokai.getWorldX();

        for (int i = 0; i < 10; i++) {
            yokai.update(0.1f, box -> true);
        }

        assertEquals(startX, yokai.getWorldX(), 0.001f);
    }

    @Test
    void takingACardHitAggrosTheYokai() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        assertFalse(yokai.isAggro());

        yokai.flashHit(OverworldYokaiEntity.HIT_FLASH_SECONDS);

        assertTrue(yokai.isAggro(), "hits must pull the Yokai into the kiting engagement");
    }

    @Test
    void knockbackWhileAggroIsNotClampedToTheRoamBox() {
        OverworldYokaiEntity yokai = yokaiAt(100f, 100f);
        yokai.setAggro(true);
        yokai.knockback(1f, 0f, 155f);

        for (int i = 0; i < 5; i++) {
            yokai.update(0.05f, box -> false);
        }

        assertTrue(yokai.getWorldX() > yokai.getRoamMaxX() + 1f,
            "knockback must not snap back to the roam box while aggro");
    }

}