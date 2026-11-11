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
}