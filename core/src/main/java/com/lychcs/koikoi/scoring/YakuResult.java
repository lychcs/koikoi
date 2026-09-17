package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;

import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing an evaluated Yaku hand.
 * Contains the hand type, points awarded, and the cards that formed it.
 */
public record YakuResult(
    YakuType type,
    long baseChips,
    long baseMult,
    List<Card> contributingCards
) {
    public YakuResult {
        Objects.requireNonNull(type, "YakuType must not be null");
        Objects.requireNonNull(contributingCards, "contributingCards must not be null");
        contributingCards = List.copyOf(contributingCards);
    }

    /**
     * Convenience factory using default base stats directly from the YakuType.
     */
    public static YakuResult of(YakuType type, List<Card> contributingCards) {
        return new YakuResult(type, type.getBaseChips(), type.getBaseMult(), contributingCards);
    }

    /**
     * Total strength of this specific hand result for ranking/selection.
     */
    public long getEffectiveScore() {
        return baseChips * baseMult;
    }

    /**
     * Helper for UI rendering: checks whether a given card is part of this Yaku.
     */
    public boolean containsCard(Card card) {
        return contributingCards.contains(card);
    }
}
