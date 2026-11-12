package com.lychcs.koikoi.combat;

import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.model.Deck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates the actual Hanafuda names shown in the overworld HUD. */
class CardDeckNamesTest {

    @Test
    void mapsArchetypesToActualStarterDeckCards() {
        Deck deck = new Deck();
        deck.initializeDeck();

        assertEquals("Spring Cherry Blossom Storm",
            CardDeckNames.forType(CardDeckComponent.CardType.RED, deck));
        assertEquals("Spring Ribbon",
            CardDeckNames.forType(CardDeckComponent.CardType.BLUE, deck));
        assertEquals("Spring Koi",
            CardDeckNames.forType(CardDeckComponent.CardType.GOLD, deck));
    }

    @Test
    void fallsBackToTheArchetypeWhenNoDeckCardMatches() {
        assertEquals("RED", CardDeckNames.forType(CardDeckComponent.CardType.RED, null));
        assertEquals("BLUE", CardDeckNames.forType(CardDeckComponent.CardType.BLUE, new Deck()));
        assertEquals("?", CardDeckNames.forType(null, new Deck()));
    }

    @Test
    void stripsOnlyTheCopySuffix() {
        assertEquals("Spring Ribbon", CardDeckNames.clean("Spring Ribbon #1"));
        assertEquals("Spring Ribbon", CardDeckNames.clean("Spring Ribbon"));
        assertEquals("Ribbon #2 of Spring", CardDeckNames.clean("Ribbon #2 of Spring"));
    }

    @Test
    void hudLineListsTheRealHanafudaNamesOfAllSlots() {
        Deck deck = new Deck();
        deck.initializeDeck();
        CardDeckComponent cycling = new CardDeckComponent();

        assertEquals("[ Spring Cherry Blossom Storm ]   [ Spring Ribbon ]   [ Spring Koi ]",
            CardDeckNames.hudLine(cycling, deck));
    }

    @Test
    void hudLineHighlightsTheCyclingSlotWithAngleBrackets() {
        Deck deck = new Deck();
        deck.initializeDeck();
        CardDeckComponent cycling = new CardDeckComponent();

        cycling.press();
        assertEquals("< Spring Cherry Blossom Storm >   [ Spring Ribbon ]   [ Spring Koi ]",
            CardDeckNames.hudLine(cycling, deck));

        cycling.update(CardDeckComponent.SHUFFLE_INTERVAL);
        assertEquals("[ Spring Cherry Blossom Storm ]   < Spring Ribbon >   [ Spring Koi ]",
            CardDeckNames.hudLine(cycling, deck));
    }

    @Test
    void hudLineFallsBackToArchetypesWithoutADeckComponent() {
        assertEquals("[ RED ]   [ BLUE ]   [ GOLD ]", CardDeckNames.hudLine(null, null));
    }

}