package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Season;

import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing an evaluated Yaku hand.
 * Contains the hand type, points awarded, and the cards that actually formed the Yaku.
 */
public record YakuResult(
    YakuType type,
    double baseChips,
    double baseMult,
    List<Card> matchedCards,
    Season matchedSeason
) {
    public YakuResult {
        Objects.requireNonNull(type, "YakuType must not be null");
        Objects.requireNonNull(matchedCards, "matchedCards must not be null");
        matchedCards = List.copyOf(matchedCards);
    }

    /**
     * Convenience factory using default base stats directly from the YakuType.
     */
    public static YakuResult of(YakuType type, List<Card> matchedCards) {
        return new YakuResult(type, type.getBaseChips(), type.getBaseMult(), matchedCards, null);
    }

    /**
     * Factory fuer Jahreszeiten-Yaku: die erkannte Season wird mitgefuehrt,
     * damit Anzeigename und Breakdown den konkreten Fund nennen koennen.
     */
    public static YakuResult ofSeason(YakuType type, Season matchedSeason, List<Card> matchedCards) {
        return new YakuResult(type, type.getBaseChips(), type.getBaseMult(), matchedCards, matchedSeason);
    }

    /**
     * Total strength of this specific hand result for ranking/selection.
     *
     * <p>Hinweis: Das ist der UNGELEVELTE Enum-Basiswert (baseChips x baseMult).
     * Die Auswahl des besten Yakus erfolgt seit Phase 2 ausschliesslich ueber die
     * reale Auszahlung in {@link YakuSelector} (Yaku-Level, Kartenbasis, Hankos,
     * Shikigami, Omamori). Dieser Wert bleibt fuer Vergleiche und Debug erhalten.</p>
     */
    public double getEffectiveScore() {
        return baseChips * baseMult;
    }

    /**
     * Anzeigename des Ergebnisses. Jahreszeiten-Yaku stellen die Season voran
     * (z. B. "Spring Gathering").
     */
    public String getDisplayName() {
        if (matchedSeason == null) {
            return type.getDisplayName();
        }
        return capitalize(matchedSeason.name()) + " " + type.getDisplayName();
    }

    /**
     * Helper for UI rendering: checks whether a given card actually scores for this Yaku.
     */
    public boolean containsCard(Card card) {
        return matchedCards.contains(card);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
