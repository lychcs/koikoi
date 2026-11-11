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
}