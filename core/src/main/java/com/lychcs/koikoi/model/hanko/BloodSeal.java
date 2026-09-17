package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class BloodSeal implements Hanko {

    @Override
    public String getName() {
        return "Blood Seal";
    }

    @Override
    public String getDescription() {
        return "Grants +50 Chips and +10 Mult when scored.";
    }

    @Override
    public boolean canTarget(Card card) {
        return card != null && !card.hasHanko();
    }

    @Override
    public Card applyEffect(Card targetCard) {
        return new Card(
            targetCard.id(),
            targetCard.season(),
            targetCard.rank(),
            targetCard.name(),
            HankoEffect.BLOOD_SEAL
        );
    }
}
