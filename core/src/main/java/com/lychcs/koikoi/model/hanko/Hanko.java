package com.lychcs.koikoi.model.hanko;

import com.lychcs.koikoi.model.Card;

public interface Hanko {
    String getName();
    String getDescription();
    /**
     * Prüft, ob dieser Stempel auf die ausgewählte Karte angewendet werden kann.
     */
    boolean canTarget(Card card);

    /**
     * Führt die Magie aus und gibt die veränderte Karte zurück.
     */

    Card applyEffect(Card targetCard);
}
