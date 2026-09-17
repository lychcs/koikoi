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
    YakuProgression progression
) {
    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(unplayedCards, "unplayedCards must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");
        unplayedCards = List.copyOf(unplayedCards);
        omamoris = List.copyOf(omamoris);
    }

    public static ScoreContext preview(HandContext hand, List<Card> unplayedCards, YakuResult bestYaku, Shikigami activeAltarShikigami, YakuProgression progression) {
        return new ScoreContext(hand, unplayedCards, bestYaku, List.of(), activeAltarShikigami, progression);
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
}