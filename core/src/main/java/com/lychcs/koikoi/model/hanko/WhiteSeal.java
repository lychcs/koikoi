package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class WhiteSeal implements Hanko {

    @Override
    public String getName() {
        return "White Seal";
    }

    @Override
    public String getDescription() {
        return "Grants +30 Base Chips when this card is played.";
    }

    @Override
    public boolean canTarget(Card card) {
        return !card.hasHanko(); // Darf nur auf Karten ohne Stempel
    }

    @Override
    public Card applyEffect(Card targetCard) {
        return new Card(
            targetCard.id(),
            targetCard.season(),
            targetCard.rank(),
            targetCard.name(),
            HankoEffect.WHITE_SEAL
        );
    }
}
