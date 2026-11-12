package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Friendly NPC head tracking and breathing contracts. */
class VillageNpcRendererTest {

    @Test
    void headYawTracksThePlayerButClampsAtTheNeck() {
        float east = VillageNpcRenderer.headYawTarget(0f, 0f, 0f, 2f, 0f, 2f);
        assertEquals(-VillageNpcRenderer.MAX_HEAD_YAW, east, 0.001f);

        float west = VillageNpcRenderer.headYawTarget(0f, 0f, 0f, -2f, 0f, 2f);
        assertEquals(VillageNpcRenderer.MAX_HEAD_YAW, west, 0.001f);
    }

    @Test
    void headReturnsForwardWhenThePlayerIsFarAway() {
        assertEquals(0f,
            VillageNpcRenderer.headYawTarget(0.7f, 0f, 0f, 20f, 0f,
                VillageNpcRenderer.HEAD_TRACK_RADIUS + 1f),
            0.001f);
    }

    @Test
    void breathingIsASmoothSineOffset() {
        assertEquals(0f, VillageNpcRenderer.breathOffset(0f), 0.001f);
        float peakTime = (float) (Math.PI * 0.5 / 1.7);
        assertTrue(VillageNpcRenderer.breathOffset(peakTime) > 0.017f);
    }
}
