package com.lychcs.koikoi.model.shikigami.ability;

/**
 * Deklarative Faehigkeit eines Shikigami ausserhalb der reinen Scorewertung.
 *
 * <p><b>Architekturgrenze:</b> dieses Interface und alle seine Parameter sind
 * reine Model-Typen - es gibt hier bewusst <b>keine</b> Scene2D-Typen, keine
 * Screens, keine Texturen und keinen UI-Zustand. Die UI</p>
 * <ol>
 *   <li>fragt die Faehigkeit nach {@link #getTiming()} und
 *       {@link #getSelectionMode()} (keine Instanz-Pruefungen, keine
 *       Namensvergleiche),</li>
 *   <li>baut daraus ihre Schritte und uebergibt die Auswahl als
 *       {@link ShikigamiAbilityContext},</li>
 *   <li>wertet das strukturierte {@link ShikigamiAbilityResult} aus und</li>
 *   <li>committet Karten-/Altaraenderungen sowie die Erschoepfung selbst.</li>
 * </ol>
 *
 * <p><b>Bewusste Trennung:</b> {@link #resolve(ShikigamiAbilityContext)} mutiert
 * nichts ausserhalb des Ergebnisses: keine Erschoepfung, kein Eingriff in das
 * persistente Deck, keine Actors, keine Scores. Scorebasierte Faehigkeiten
 * (z. B. Oni) laufen weiterhin ausschliesslich ueber
 * {@link com.lychcs.koikoi.model.shikigami.Shikigami#activate(
 * com.lychcs.koikoi.scoring.ScoreContext,
 * com.lychcs.koikoi.scoring.ScoreAccumulator)}.</p>
 */
public interface ShikigamiAbility {

    /** Anzeigename der Faehigkeit, z. B. {@code "Fox Trick"}. */
    String getAbilityName();

    /** Ausloesezeitpunkt der Faehigkeit. */
    ShikigamiActivationTiming getTiming();

    /** Auswahlbedarf vor dem Commit. */
    ShikigamiSelectionMode getSelectionMode();

    /** Kurzer englischer Erklaertext fuer die UI (Auswahldialog). */
    String getPromptText();

    /**
     * Wendet die Faehigkeit auf die uebergebenen Daten an und liefert das
     * strukturierte Ergebnis. Reine Berechnung: nur die konfigurierte
     * Zufallsquelle wird verwendet, sonst veraendert der Aufruf nichts.
     *
     * @param context aktuelle Hand, optional gewaehlte Karten und Zielpool
     * @return Ergebnis mit {@code APPLIED} und dem Kartenpaar oder eine
     *         Ablehnung mit englischer Begruendung; niemals {@code null}
     */
    ShikigamiAbilityResult resolve(ShikigamiAbilityContext context);
}
