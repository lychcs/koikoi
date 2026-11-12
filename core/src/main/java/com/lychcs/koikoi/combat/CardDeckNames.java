package com.lychcs.koikoi.combat;

import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.Rank;

/**
 * Resolves the human-readable Hanafuda card names shown for the three
 * overworld W-card slots.
 *
 * <p>The W deck uses three archetypes: RED (Hikari/fire), BLUE (Ribbon/refund)
 * and GOLD (Beast/stun). The first matching card in the player's actual deck is
 * displayed, so renamed/evolved cards stay accurate.</p>
 */
public final class CardDeckNames {

    private CardDeckNames() {
    }

    /** Actual deck card name for a W slot, or the archetype label as fallback. */
    public static String forType(CardDeckComponent.CardType type, Deck deck) {
        if (type == null) {
            return "?";
        }
        Rank wanted = switch (type) {
            case RED -> Rank.HIKARI;
            case BLUE -> Rank.RIBBON;
            case GOLD -> Rank.BEAST;
        };
        if (deck != null) {
            for (Card card : deck.getCards()) {
                if (card.rank() == wanted) {
                    return clean(card.name());
                }
            }
        }
        return type.label;
    }

    /** Strips the per-copy suffix (for example {@code " #1"}) for HUD display. */
    public static String clean(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.replaceFirst("\\s+#\\d+$", "");
    }

    /**
     * One-line FPS HUD summary of the cycling W deck.
     *
     * <p>All three equipped slots are listed with the actual Hanafuda names of
     * the player's deck. While the deck shuffles or holds a locked card, the
     * highlighted slot is wrapped in {@code < >} so the bottom-centre HUD shows
     * which card the next throw will consume.</p>
     */
    public static String hudLine(CardDeckComponent deck, Deck playerDeck) {
        if (deck == null) {
            StringBuilder fallback = new StringBuilder();
            CardDeckComponent.CardType[] types = CardDeckComponent.CardType.values();
            for (int i = 0; i < types.length; i++) {
                if (i > 0) {
                    fallback.append("   ");
                }
                fallback.append("[ ").append(forType(types[i], playerDeck)).append(" ]");
            }
            return fallback.toString();
        }

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < deck.equipped.length; i++) {
            if (i > 0) {
                line.append("   ");
            }
            boolean active = deck.showsBadge()
                && i == Math.floorMod(deck.activeIndex, deck.equipped.length);
            String name = forType(deck.equipped[i], playerDeck);
            line.append(active ? "< " : "[ ").append(name).append(active ? " >" : " ]");
        }
        return line.toString();
    }

}