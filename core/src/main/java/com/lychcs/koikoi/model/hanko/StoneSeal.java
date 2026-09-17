package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class StoneSeal implements Hanko {

    public static final long LEVEL_1_CHIP_BONUS = 50L;

    @Override
    public String getName() {
        return "Stone Seal";
    }

    @Override
    public String getDescription() {
        return "Cannot be discarded.\nGrants +" + LEVEL_1_CHIP_BONUS + " Chips when scored.";
    }

    @Override
    public boolean canTarget(Card card) {
        // Jede Karte darf (erneut) bestempelt werden; ein vorhandener Hanko wird ersetzt.
        return card != null;
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
