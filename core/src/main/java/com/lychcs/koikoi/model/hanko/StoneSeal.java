package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class StoneSeal implements Hanko {

    @Override
    public String getName() {
        return "Stone Seal";
    }

    @Override
    public String getDescription() {
        return "Returns this card back to your hand after being played.\nThe seal breaks (is removed) afterwards.";
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
            HankoEffect.STONE_SEAL
        );
    }
}
