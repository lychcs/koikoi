package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class WhiteSeal implements Hanko {

    /** Chips-Bonus beim Werten (zentrale Quelle fuer Wertung und Auswahlprioritaet). */
    public static final double CHIP_BONUS = 30.0;

    @Override
    public String getName() {
        return "White Seal";
    }

    @Override
    public String getDescription() {
        return "Grants +" + (long) CHIP_BONUS + " Chips when scored.";
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
            HankoEffect.WHITE_SEAL
        );
    }
}
