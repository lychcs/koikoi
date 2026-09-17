package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.YakuProgression;

import java.util.List;
import java.util.Objects;

public record ScoreContext(
    HandContext hand,
    List<Card> unplayedCards,
    YakuResult bestYaku,
    List<Omamori> omamoris,
    Shikigami activeAltarShikigami,
    YakuProgression progression,
    List<Card> orderedScoringCards
) {
    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(unplayedCards, "unplayedCards must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");
        unplayedCards = List.copyOf(unplayedCards);
        omamoris = List.copyOf(omamoris);
        orderedScoringCards = orderedScoringCards == null ? List.of() : List.copyOf(orderedScoringCards);
    }

    public static ScoreContext preview(HandContext hand, List<Card> unplayedCards, YakuResult bestYaku, Shikigami activeAltarShikigami, YakuProgression progression) {
        return new ScoreContext(hand, unplayedCards, bestYaku, List.of(), activeAltarShikigami, progression, List.of());
    }

    public double getYakuBaseChips() {
        if (bestYaku == null) return 0.0;
        if (progression != null) {
            return progression.getUpgradedChips(bestYaku.type());
        }
        return bestYaku.baseChips();
    }

    public double getYakuBaseMult() {
        if (bestYaku == null) return 0.0;
        if (progression != null) {
            return progression.getUpgradedMult(bestYaku.type());
        }
        return bestYaku.baseMult();
    }

    /**
     * Karten, mit denen das ausgewaehlte Yaku tatsaechlich erfuellt wurde.
     * Nur diese Karten liefern Kartenbasis- und Hanko-Werte.
     */
    public List<Card> matchedCards() {
        return bestYaku == null ? List.of() : bestYaku.matchedCards();
    }

    /**
     * Karten, die tatsaechlich gewertet werden – in der sichtbaren
     * Links-nach-rechts-Reihenfolge der ausgespielten Hand (gefiltert auf
     * {@link #matchedCards()}). Ist keine Reihenfolge gesetzt, gilt die
     * Reihenfolge der Yaku-Erkennung.
     */
    public List<Card> scoringCards() {
        if (orderedScoringCards.isEmpty()) {
            return matchedCards();
        }
        return orderedScoringCards;
    }
}