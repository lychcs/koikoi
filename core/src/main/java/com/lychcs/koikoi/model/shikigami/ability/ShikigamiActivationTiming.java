package com.lychcs.koikoi.model.shikigami.ability;

/**
 * Ausloesezeitpunkt einer Shikigami-Faehigkeit. Die UI entscheidet anhand dieses
 * Werts, <b>wann</b> sie eine Faehigkeit anbietet - sie prueft niemals eine
 * konkrete Klasse und vergleicht keine Namen.
 *
 * <p>Fuer jeden Zeitpunkt sind Trigger, Auswahlbedarf, Commit und Erschoepfung
 * getrennt definiert (Eigenschaft der Architektur, nicht der einzelnen
 * Faehigkeit):</p>
 *
 * <ul>
 *   <li><b>{@link #ON_ALTAR_PLACED}</b> - Trigger: der Begleiter wird in den Altar
 *       gelegt. Auswahl: keine bis zwei Kartenschritte ({@link ShikigamiSelectionMode}).
 *       Commit: erst nach der letzten Bestaetigung; vorher ist der Begleiter nur
 *       vorgemerkt (kein Bag-Austrag, keine Erschoepfung, keine Animation).
 *       Erschoepfung: unmittelbar nach erfolgreichem Commit. UI-Verantwortung:
 *       Dialog/Auswahl, Validierung der Auswahl, Rueckkehr in den sicheren Zustand
 *       bei Abbruch. Beispiel: Kitsune "Fox Trick".</li>
 *   <li><b>{@link #ON_HAND_PLAYED}</b> - Trigger: die Hand wird tatsaechlich
 *       gespielt (nicht beim Einsetzen in den Altar, nicht in der Vorschau/ESTIMATE).
 *       Auswahl: optional (Karten koennen vor dem Spielen oder im Altar gewaehlt
 *       werden). Commit: beim Spielen der Hand. Erschoepfung: mit dem Commit.
 *       UI-Verantwortung: den Kartenschritt dem Spielvorgang voranstellen.
 *       Vorgesehenes spaeteres Beispiel: ein Shikigami, das gespielte Karten
 *       "einfriert" - sie werden gewertet, bleiben aber in der Hand. In dieser
 *       Phase ist bewusst keine solche Faehigkeit implementiert.</li>
 *   <li><b>{@link #ON_HAND_SCORED}</b> - Trigger: waehrend der Wertung der
 *       gespielten Hand. Auswahl: keine. Commit: im autoritativen
 *       {@code ScoreCalculator}. Erschoepfung: nach der Wertung durch die UI.
 *       UI-Verantwortung: nur die Anzeige der vom Rechenkern gelieferten Events.
 *       Beispiel: Oni (bestehendes Verhalten, unveraendert).</li>
 * </ul>
 */
public enum ShikigamiActivationTiming {

    /** Faehigkeit beginnt beim Einsetzen in den Altar (kann Auswahl benoetigen). */
    ON_ALTAR_PLACED,

    /** Faehigkeit greift erst beim tatsaechlichen Spielen der Hand (Kartenfluss). */
    ON_HAND_PLAYED,

    /** Scorebasierte Faehigkeit, Teil des ScoreCalculators (bestehende Shikigami). */
    ON_HAND_SCORED
}
