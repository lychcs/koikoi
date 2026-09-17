package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class BloodSeal implements Hanko {

    /** Chips-Bonus beim Werten. */
    public static final long CHIP_BONUS = 50L;

    /** Mult-Bonus beim Werten. */
    public static final long MULT_BONUS = 10L;

    @Override
    public String getName() {
        return "Blood Seal";
    }

    @Override
    public String getDescription() {
        // Einziger Effekt: +Chips und +Mult. Keine Kartenzerstoerung, keine Verbannung.
        return "Grants +" + CHIP_BONUS + " Chips and +" + MULT_BONUS + " Mult when scored.";
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
            HankoEffect.BLOOD_SEAL
        );
    }
}
