package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class GoldenSeal implements Hanko {

    @Override
    public String getName() {
        return "Golden Seal";
    }

    @Override
    public String getDescription() {
        return "1 in 4 chance for +3 Mon.\n1 in 20 chance for +10 Mon when scored.";
    }

    @Override
    public boolean canTarget(Card card) {
        // Stempelt nur Karten, die noch "rein" sind
        return !card.hasHanko();
    }

    @Override
    public Card applyEffect(Card targetCard) {
        return new Card(
            targetCard.id(),
            targetCard.season(),
            targetCard.rank(),
            targetCard.name(),
            HankoEffect.GOLDEN_SEAL
        );
    }
}
