package com.lychcs.koikoi.model.shikigami.ability;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.DeckTemplates;
import com.lychcs.koikoi.model.Season;

import java.util.List;

/**
 * Reine Eingabedaten einer Faehigkeitsauswertung: die aktuelle Kampfhand, der
 * vollstaendige Vorlagenpool und die vom Spieler (oder der UI) getroffene
 * Auswahl. Enthaelt bewusst keine Actors, Labels, Screens oder Zufallszustaende.
 *
 * <p>Alle Listen werden als unveraenderliche Kopie gehalten; der Aufrufer kann
 * seine Hand danach weiterveraendern, ohne das Ergebnis zu beeinflussen.</p>
 */
public record ShikigamiAbilityContext(
    ShikigamiActivationTiming timing,
    List<Card> hand,
    List<Card> playedCards,
    List<Card> deckPool,
    Card sourceCard,
    Card targetTemplate,
    Season currentSeason
) {

    public ShikigamiAbilityContext {
        if (timing == null) {
            throw new IllegalArgumentException("timing must not be null");
        }
        hand = hand == null ? List.of() : List.copyOf(hand);
        playedCards = playedCards == null ? List.of() : List.copyOf(playedCards);
        deckPool = deckPool == null ? DeckTemplates.all() : List.copyOf(deckPool);
    }

    /** Auswertung beim Einsetzen in den Altar, ohne Kartenauswahl (Level 1). */
    public static ShikigamiAbilityContext forAltarPlaced(List<Card> hand) {
        return forAltarPlaced(hand, null, null, null);
    }

    /** Auswertung beim Einsetzen in den Altar mit gewaehlter Quellkarte (Level 2). */
    public static ShikigamiAbilityContext forAltarPlaced(List<Card> hand, Card sourceCard) {
        return forAltarPlaced(hand, sourceCard, null, null);
    }

    /** Auswertung beim Einsetzen in den Altar mit Quell- und Zielkarte (Level 3). */
    public static ShikigamiAbilityContext forAltarPlaced(
        List<Card> hand, Card sourceCard, Card targetTemplate, Season currentSeason) {
        return new ShikigamiAbilityContext(
            ShikigamiActivationTiming.ON_ALTAR_PLACED, hand, List.of(),
            DeckTemplates.all(), sourceCard, targetTemplate, currentSeason);
    }

    /**
     * Auswertung beim tatsaechlichen Spielen der Hand (vorgesehen fuer
     * {@link ShikigamiActivationTiming#ON_HAND_PLAYED}; in dieser Phase ist keine
     * solche Faehigkeit implementiert).
     */
    public static ShikigamiAbilityContext forHandPlayed(
        List<Card> hand, List<Card> playedCards, Card sourceCard, Season currentSeason) {
        return new ShikigamiAbilityContext(
            ShikigamiActivationTiming.ON_HAND_PLAYED, hand, playedCards,
            DeckTemplates.all(), sourceCard, null, currentSeason);
    }

    /** true, wenn eine Quellkarte uebergeben wurde. */
    public boolean hasSourceCard() {
        return sourceCard != null;
    }

    /** true, wenn eine Zielvorlage uebergeben wurde. */
    public boolean hasTargetTemplate() {
        return targetTemplate != null;
    }

    /** true, wenn die Quelle tatsaechlich in der aktuellen Hand liegt. */
    public boolean containsSourceCard() {
        return sourceCard != null && hand.contains(sourceCard);
    }

    /**
     * Gewichtete Zielkandidaten der <b>im Kontext gewaehlten</b> Quellkarte (alle
     * Vorlagen anderer CardID). Fuer die Level-3-Galerie der UI gedacht; die
     * Aufloesung der Faehigkeit nutzt immer die tatsaechlich verwendete Quellkarte.
     */
    public List<Card> candidatesForSource() {
        return DeckTemplates.candidatesFor(sourceCard);
    }
}
