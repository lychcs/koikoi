package com.lychcs.koikoi.model.shikigami.ability;

import com.lychcs.koikoi.model.Card;

/**
 * Strukturiertes Ergebnis einer Faehigkeitsauswertung. Die UI wertet
 * ausschliesslich {@link #isApplied()} und das Kartenpaar aus - kein
 * Textvergleich, keine Instanzpruefung.
 *
 * <p>{@code sourceCard} ist die ersetzte Handkarte, {@code replacementCard} die
 * neue temporaere Karte (Hanko der Quelle bleibt erhalten). Beide sind nur bei
 * {@link Outcome#APPLIED} gesetzt.</p>
 */
public record ShikigamiAbilityResult(
    Outcome outcome,
    Card sourceCard,
    Card replacementCard,
    String message
) {

    public enum Outcome {
        /** Faehigkeit erfolgreich angewendet: Kartenpaar ist gesetzt. */
        APPLIED,
        /** Vom Spieler abgebrochen: keine Aenderung, keine Erschoepfung. */
        CANCELLED,
        /** Faehigkeit greift beim aktuellen Zeitpunkt/Stufe nicht. */
        NOT_APPLICABLE,
        /** Keine Karte in der Hand: nichts zu transformieren. */
        EMPTY_HAND,
        /** Auswahl fehlt, ist veraltet oder verletzt eine Regel (z. B. gleiche CardID). */
        INVALID_SELECTION,
        /** Es gibt keine zulaessige Zielkarte. */
        NO_VALID_TARGET,
        /** Faehigkeit wurde in diesem Einsatz bereits angewendet. */
        ALREADY_RESOLVED,
        /** Unerwarteter Fehler: sicherer Rueckkehrzustand, keine Aenderung. */
        FAILED
    }

    public ShikigamiAbilityResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        message = message == null ? "" : message;
        if (outcome == Outcome.APPLIED && (sourceCard == null || replacementCard == null)) {
            throw new IllegalArgumentException(
                "APPLIED requires both the source card and the replacement card");
        }
    }

    /** Erfolgreiche Kartenersetzung. */
    public static ShikigamiAbilityResult applied(Card sourceCard, Card replacementCard, String message) {
        return new ShikigamiAbilityResult(Outcome.APPLIED, sourceCard, replacementCard, message);
    }

    /** Abbruch durch den Spieler. */
    public static ShikigamiAbilityResult cancelled(String abilityName) {
        return new ShikigamiAbilityResult(Outcome.CANCELLED, null, null,
            abilityName + " cancelled. No card was changed.");
    }

    /** Stufe/Zeitpunkt unpassend. */
    public static ShikigamiAbilityResult notApplicable(String abilityName, String reason) {
        return new ShikigamiAbilityResult(Outcome.NOT_APPLICABLE, null, null,
            abilityName + " is not applicable: " + reason);
    }

    /** Leere Hand. */
    public static ShikigamiAbilityResult emptyHand(String abilityName) {
        return new ShikigamiAbilityResult(Outcome.EMPTY_HAND, null, null,
            "Your hand is empty: " + abilityName + " cannot be activated.");
    }

    /** Ungueltige oder veraltete Auswahl. */
    public static ShikigamiAbilityResult invalidSelection(String message) {
        return new ShikigamiAbilityResult(Outcome.INVALID_SELECTION, null, null, message);
    }

    /** Keine zulaessige Zielkarte. */
    public static ShikigamiAbilityResult noValidTarget(String abilityName) {
        return new ShikigamiAbilityResult(Outcome.NO_VALID_TARGET, null, null,
            abilityName + " found no different card to transform into.");
    }

    /** Bereits in diesem Einsatz angewendet. */
    public static ShikigamiAbilityResult alreadyResolved(String abilityName) {
        return new ShikigamiAbilityResult(Outcome.ALREADY_RESOLVED, null, null,
            abilityName + " was already used in this battle.");
    }

    /** Unerwarteter Fehler. */
    public static ShikigamiAbilityResult failed(String message) {
        return new ShikigamiAbilityResult(Outcome.FAILED, null, null, message);
    }

    /** true, wenn genau eine Karte ersetzt wurde. */
    public boolean isApplied() {
        return outcome == Outcome.APPLIED;
    }

    /** true, wenn nichts veraendert wurde (keine Mutation, keine Erschoepfung). */
    public boolean isRejection() {
        return !isApplied();
    }

    /** true, wenn der Spieler den Vorgang abgebrochen hat. */
    public boolean isCancelled() {
        return outcome == Outcome.CANCELLED;
    }
}
