package com.lychcs.koikoi.combat.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the W pick-a-card timing and state transitions. */
class CardDeckComponentTest {

    @Test
    void shufflingCyclesEveryFourTenthsAndAutoCancels() {
        CardDeckComponent deck = new CardDeckComponent();

        deck.press();
        assertEquals(CardDeckComponent.State.SHUFFLING, deck.state);
        assertTrue(deck.showsBadge());
        assertEquals(CardDeckComponent.CardType.RED, deck.activeCard());

        deck.update(0.4f);
        assertEquals(CardDeckComponent.CardType.BLUE, deck.activeCard());
        deck.update(0.4f);
        assertEquals(CardDeckComponent.CardType.GOLD, deck.activeCard());

        deck.update(CardDeckComponent.SHUFFLE_MAX_SECONDS);
        assertEquals(CardDeckComponent.State.COOLDOWN, deck.state);
        assertFalse(deck.showsBadge());

        deck.update(CardDeckComponent.COOLDOWN_SECONDS);
        assertEquals(CardDeckComponent.State.IDLE, deck.state);
    }

    @Test
    void secondPressLocksAndLeftClickConsumesIntoCooldown() {
        CardDeckComponent deck = new CardDeckComponent();
        deck.press();
        deck.update(0.4f);

        deck.press();
        assertEquals(CardDeckComponent.State.LOCKED, deck.state);
        assertTrue(deck.showsBadge());
        assertEquals(CardDeckComponent.CardType.BLUE, deck.consumeLocked());
        assertEquals(CardDeckComponent.State.COOLDOWN, deck.state);
        assertFalse(deck.showsBadge());

        deck.update(CardDeckComponent.COOLDOWN_SECONDS);
        assertEquals(CardDeckComponent.State.IDLE, deck.state);
    }

    @Test
    void lockedBuffExpiresIntoCooldown() {
        CardDeckComponent deck = new CardDeckComponent();
        deck.press();
        deck.press();
        assertEquals(CardDeckComponent.State.LOCKED, deck.state);

        deck.update(CardDeckComponent.LOCKED_SECONDS);
        assertEquals(CardDeckComponent.State.COOLDOWN, deck.state);
    }

    @Test
    void ribbonRefundResetsLockedAndCoolingWState() {
        CardDeckComponent deck = new CardDeckComponent();
        deck.press();
        deck.update(0.4f);
        deck.press();
        assertEquals(CardDeckComponent.State.LOCKED, deck.state);

        deck.resetCooldown();
        assertEquals(CardDeckComponent.State.IDLE, deck.state);
        assertEquals(0f, deck.lockedRemaining, 0.0001f);
        assertEquals(0f, deck.cooldownRemaining, 0.0001f);

        deck.press();
        assertEquals(CardDeckComponent.State.SHUFFLING, deck.state,
            "W must be immediately usable again after a Ribbon refund");
    }
}
