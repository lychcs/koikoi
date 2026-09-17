package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.run.YakuProgression;

import java.util.List;
import java.util.Objects;

public record ScoreContext(
    HandContext hand,
    List<Card> unplayedCards,
    YakuResult bestYaku,
    List<Omamori> omamoris,
    Yokai activeAltarYokai,
    YakuProgression progression
) {
    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(unplayedCards, "unplayedCards must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");
        unplayedCards = List.copyOf(unplayedCards);
        omamoris = List.copyOf(omamoris);
    }

    public static ScoreContext preview(HandContext hand, List<Card> unplayedCards, YakuResult bestYaku, Yokai activeAltarYokai, YakuProgression progression) {
        return new ScoreContext(hand, unplayedCards, bestYaku, List.of(), activeAltarYokai, progression);
    }

    public long getYakuBaseChips() {
        if (bestYaku == null) return 0L;
        if (progression != null) {
            return progression.getUpgradedChips(bestYaku.type());
        }
        return bestYaku.baseChips();
    }

    public long getYakuBaseMult() {
        if (bestYaku == null) return 0L;
        if (progression != null) {
            return progression.getUpgradedMult(bestYaku.type());
        }
        return bestYaku.baseMult();
    }
}
