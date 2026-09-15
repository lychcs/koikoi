package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai; // Wichtig: Yokai importieren!

import java.util.List;
import java.util.Objects;

public record ScoreContext(
    HandContext hand,
    List<Card> unplayedCards,
    YakuResult bestYaku,
    List<Omamori> omamoris,
    Yokai activeAltarYokai
) {

    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(unplayedCards, "unplayedCards must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");

        // Defensive Copy
        unplayedCards = List.copyOf(unplayedCards);
        omamoris = List.copyOf(omamoris);
    }

    public static ScoreContext preview(HandContext hand, List<Card> unplayedCards, YakuResult bestYaku, Yokai activeAltarYokai) {
        return new ScoreContext(
            hand,
            unplayedCards,
            bestYaku,
            List.of(),
            activeAltarYokai
        );
    }

    public int getYakuBaseChips() {
        return bestYaku != null ? bestYaku.baseChips() : 0;
    }

    public int getYakuBaseMult() {
        return bestYaku != null ? bestYaku.baseMult() : 0;
    }
}
