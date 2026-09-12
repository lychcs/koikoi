package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class VoidSeal implements Hanko {

    @Override
    public String getName() {
        return "Void Seal";
    }

    @Override
    public String getDescription() {
        return "1 in 4 chance for +5 Void Dust.\n1 in 20 chance for +10 Void Dust when scored.";
    }

    @Override
    public boolean canTarget(Card card) {
        return !card.hasHanko();
    }

    @Override
    public Card applyEffect(Card targetCard) {
        return new Card(
            targetCard.id(),
            targetCard.season(),
            targetCard.rank(),
            targetCard.name(),
            HankoEffect.VOID_SEAL
        );
    }
}
