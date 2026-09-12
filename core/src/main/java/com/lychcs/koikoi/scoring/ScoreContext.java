package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai; // Wichtig: Yokai importieren!

import java.util.List;
import java.util.Objects;

public record ScoreContext(
    int floatingBank,          // Kann perspektivisch auch komplett weg
    HandContext hand,
    List<Card> unplayedCards,
    YakuResult bestYaku,
    List<Omamori> omamoris,
    Yokai activeAltarYokai     // NEU: Ersetzt den koiKoiMult
) {
    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(unplayedCards, "unplayedCards must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");

        // Defensive Copy
        unplayedCards = List.copyOf(unplayedCards);
        omamoris = List.copyOf(omamoris);
    }

    /**
     * Preview für das UI (ohne globale Omamoris, aber Yokai wird berücksichtigt)
     */
    public static ScoreContext preview(HandContext hand, List<Card> unplayedCards, YakuResult bestYaku, Yokai activeAltarYokai) {
        return new ScoreContext(
            0,                 // floatingBank ist jetzt immer 0
            hand,
            unplayedCards,
            bestYaku,
            List.of(),         // Keine Omamoris in der puren Basis-Preview
            activeAltarYokai   // Yokai in die Preview übergeben
        );
    }

    public int getYakuBaseChips() {
        return bestYaku != null ? bestYaku.baseChips() : 0;
    }

    public int getYakuBaseMult() {
        return bestYaku != null ? bestYaku.baseMult() : 0;
    }
}
