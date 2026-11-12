package com.lychcs.koikoi.ui;

import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.run.GameSeason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure mapping from Hanafuda archetypes to existing atlas card artwork. */
class HudCardSlotTest {

    @Test
    void springCardsUseTheSpringPetalRibbonAndBeastArtwork() {
        assertEquals("SPRING_PETAL", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.RED, GameSeason.SPRING));
        assertEquals("SPRING_RIBBON", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.BLUE, GameSeason.SPRING));
        assertEquals("SPRING_BEAST_CRANE", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.GOLD, GameSeason.SPRING));
    }

    @Test
    void seasonChangesSelectTheMatchingAtlasArtwork() {
        assertEquals("SUMMER_PETAL", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.RED, GameSeason.SUMMER));
        assertEquals("AUTUMN_RIBBON", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.BLUE, GameSeason.AUTUMN));
        assertEquals("WINTER_BEAST_ARCTIC_FOX", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.GOLD, GameSeason.WINTER));
    }

    @Test
    void finalSeasonFallsBackToSpringArtwork() {
        assertEquals("SPRING_PETAL", HudCardSlot.cardArtRegionName(
            CardDeckComponent.CardType.RED, GameSeason.FINAL));
    }

    @Test
    void nullInputsHaveStableFallbacks() {
        assertEquals("SPRING_PETAL", HudCardSlot.cardArtRegionName(null, null));
    }
}
