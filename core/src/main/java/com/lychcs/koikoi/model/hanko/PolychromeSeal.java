package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public class PolychromeSeal implements Hanko {

    @Override
    public String getName() {
        return "Polychrome Seal";
    }

    @Override
    public String getDescription() {
        return "Counts as all four seasons when detecting Yaku (Gathering, Monochrome and True Season).";
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
            HankoEffect.POLYCHROME_SEAL
        );
    }
}
