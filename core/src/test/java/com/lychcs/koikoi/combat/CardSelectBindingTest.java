package com.lychcs.koikoi.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the TF-style shuffle/lock binding: E next to WASD, plus R and RMB. */
class CardSelectBindingTest {

    @Test
    void eShufflesInTheOpenField() {
        assertTrue(CardSelectBinding.shouldShuffle(false, true, false, false, false));
    }

    @Test
    void eOpensTheTavernShopOrShrineInsteadOfTheDeck() {
        assertFalse(CardSelectBinding.shouldShuffle(false, true, false, true, false));
        assertTrue(CardSelectBinding.shouldShuffle(true, true, false, true, false),
            "R must keep working while standing on an interaction tile");
    }

    @Test
    void rAlwaysRequestsTheDeck() {
        assertTrue(CardSelectBinding.shouldShuffle(true, false, false, false, false));
        assertTrue(CardSelectBinding.shouldShuffle(true, false, true, true, true));
    }

    @Test
    void rightClickShufflesUnlessItLockedOnToAHostile() {
        assertTrue(CardSelectBinding.shouldShuffle(false, false, true, false, false));
        assertFalse(CardSelectBinding.shouldShuffle(false, false, true, false, true));
    }

    @Test
    void noInputLeavesTheDeckAlone() {
        assertFalse(CardSelectBinding.shouldShuffle(false, false, false, false, false));
        assertFalse(CardSelectBinding.shouldShuffle(false, false, false, true, true));
    }
}
