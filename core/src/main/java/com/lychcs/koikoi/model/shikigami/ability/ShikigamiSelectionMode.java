package com.lychcs.koikoi.model.shikigami.ability;

/**
 * Deklarativer Auswahlbedarf einer Faehigkeit vor dem Commit.
 *
 * <p>Die UI liest diesen Wert und baut daraus ihre Schritte; das Modell enthaelt
 * dafuer bewusst keine Actors, Dialoge oder Layouts.</p>
 */
public enum ShikigamiSelectionMode {

    /** Keine Auswahl und keine Rueckfrage: die Faehigkeit wird sofort angewendet. */
    NONE,

    /** Nur eine abbrechbare Bestaetigung: die Auswahl trifft die Faehigkeit selbst. */
    CONFIRM_ONLY,

    /** Der Spieler waehlt genau eine Karte aus der aktuellen Hand. */
    SELECT_HAND_CARD,

    /** Der Spieler waehlt erst die Quellkarte und danach frei die Zielkarte. */
    SELECT_HAND_CARD_AND_TARGET_CARD;

    /** true, wenn vor dem Commit eine Karte aus der Hand gewaehlt wird. */
    public boolean requiresHandCard() {
        return this == SELECT_HAND_CARD || this == SELECT_HAND_CARD_AND_TARGET_CARD;
    }

    /** true, wenn zusaetzlich die Zielkarte frei gewaehlt wird. */
    public boolean requiresTargetCard() {
        return this == SELECT_HAND_CARD_AND_TARGET_CARD;
    }

    /** true, wenn vor dem Commit eine (abbrechbare) Bestaetigung noetig ist. */
    public boolean needsConfirmation() {
        return this != NONE;
    }
}
