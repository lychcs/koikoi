package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class BlackSeal implements Hanko {

    /** Additiver Mult-Bonus beim Werten (zentrale Quelle fuer Wertung und Auswahlprioritaet). */
    public static final double MULT_BONUS = 4.0;

    @Override
    public String getName() {
        return "Black Seal";
    }

    @Override
    public String getDescription() {
        return "Grants +" + (long) MULT_BONUS + " Multiplier when this card is played.";
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
            HankoEffect.BLACK_SEAL
        );
    }
}
