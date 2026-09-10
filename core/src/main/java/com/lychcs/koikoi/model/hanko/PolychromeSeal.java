package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class PolychromeSeal implements Hanko {

    @Override
    public String getName() {
        return "Polychrome Seal";
    }

    @Override
    public String getDescription() {
        return "This card is treated as ALL FOUR seasons simultaneously when calculating Yaku (Flushes/Harvest).";
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
            HankoEffect.POLYCHROME_SEAL
        );
    }
}
