package com.lychcs.koikoi.scoring;

/**
 * Herkunft eines {@link ScoringEvent}. Damit kann die Score-Animation ihre
 * Ziele eindeutig zuordnen, ohne Beschreibungstexte auszuwerten.
 *
 * <p>Hinweis: Fuer den Yaku-Basiswert existiert bewusst kein ScoringEvent – er
 * steht als Kopfwert in {@link CalculationBreakdown#yakuChips()} und
 * {@link CalculationBreakdown#yakuBaseMult()}.</p>
 */
public enum ScoringSourceType {
    /** Kartenbasis-Chips einer gewerteten Karte. */
    CARD_BASE,
    /** Hanko-/Siegel-Effekt einer gewerteten Karte. */
    HANKO,
    /** Scoreeffekt des aktiven Shikigami im Altar. */
    SHIKIGAMI,
    /** Scoreeffekt eines aktiven Omamori. */
    OMAMORI,
    /** Nicht zum Score gehoerende Belohnung (Mon, Void Dust). */
    REWARD,
    /** Aufruf ohne zuordenbaren Kontext. */
    UNATTRIBUTED
}
