package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;

/**
 * Ein einzelner Wertungsschritt. Chips und Mult sind Dezimalwerte (double),
 * damit Faktoren wie x1.5 nicht gerundet werden muessen.
 *
 * <p>Jedes Event kennt seine Quelle eindeutig ({@link ScoringSourceType}) und
 * optional die betroffene Karte sowie das verursachende Omamori beziehungsweise
 * Shikigami. Es enthaelt bewusst keine UI-Referenzen; die UI ordnet Actors ueber
 * diese Model-Objekte zu.</p>
 */
public record ScoringEvent(
    ScoringSourceType sourceType,
    String sourceName,
    Card card,
    Omamori omamori,
    Shikigami shikigami,
    double addedChips,
    double addedMult,
    double xMult,
    int addedMon,
    int addedVoidDust
) {
    /** Komfort-Konstruktor fuer Events ohne Zuordnungsobjekt. */
    public ScoringEvent(String sourceName, double addedChips, double addedMult, double xMult,
                        int addedMon, int addedVoidDust) {
        this(ScoringSourceType.UNATTRIBUTED, sourceName, null, null, null,
            addedChips, addedMult, xMult, addedMon, addedVoidDust);
    }

    /** true, wenn dieses Event Chips, Mult oder einen Mult-Faktor veraendert. */
    public boolean affectsScore() {
        return addedChips != 0.0 || addedMult != 0.0 || xMult != 1.0;
    }
}
