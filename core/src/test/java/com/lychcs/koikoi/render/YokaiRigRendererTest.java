package com.lychcs.koikoi.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure metadata/animation contracts for the procedural Yokai rigs. */
class YokaiRigRendererTest {

    @Test
    void speciesIdsSelectDistinctArchetypes() {
        assertEquals(YokaiRigRenderer.Archetype.CHOCHIN_OBAKE,
            YokaiRigRenderer.archetypeFor("chochin_obake", "Lantern Spirit"));
        assertEquals(YokaiRigRenderer.Archetype.KITSUNE_BI,
            YokaiRigRenderer.archetypeFor("kitsune", "Wild Kitsune"));
        assertEquals(YokaiRigRenderer.Archetype.ONI_GASHIRA,
            YokaiRigRenderer.archetypeFor("oni", "Wild Oni"));
    }

    @Test
    void displayNamesCanResolveArchetypesWithoutSpeciesIds() {
        assertEquals(YokaiRigRenderer.Archetype.CHOCHIN_OBAKE,
            YokaiRigRenderer.archetypeFor(null, "Chōchin-Obake"));
        assertEquals(YokaiRigRenderer.Archetype.KITSUNE_BI,
            YokaiRigRenderer.archetypeFor(null, "Fox Spirit"));
        assertEquals(YokaiRigRenderer.Archetype.ONI_GASHIRA,
            YokaiRigRenderer.archetypeFor("", "Stone Brute"));
    }

    @Test
    void threeTailSpringsAndDefeatBurstsAreConfigured() {
        assertEquals(3, YokaiRigRenderer.TAIL_COUNT);
        assertTrue(YokaiRigRenderer.DEFEAT_DURATION > 0f);
        assertTrue(YokaiRigRenderer.MAX_DEFEAT_BURSTS > 0);
    }
}
