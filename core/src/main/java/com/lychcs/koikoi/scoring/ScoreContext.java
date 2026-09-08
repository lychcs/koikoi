package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Omamori;
import java.util.List;
import java.util.Objects;

/**
 * Context payload passed into the score calculation engine.
 * Nutzt jetzt die Balatro-inspirierte Pipeline-Struktur für sequentielle Auswertung.
 */
public record ScoreContext(
    int floatingBank,
    HandContext hand,          // WICHTIG: Die Omamoris müssen die Hand analysieren können
    YakuResult bestYaku,       // Die gewertete Hand (z.B. WILD_COURT). Kann null sein!
    List<Omamori> omamoris,    // Order matters! (Index 0 = Leftmost)
    double koiKoiMult          // Push-your-luck Multiplikator der aktuellen Runde
) {
    public ScoreContext {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(omamoris, "omamoris must not be null");
        // Defensive Copy, um externe Manipulation der Reihenfolge während der Calculation zu verhindern
        omamoris = List.copyOf(omamoris);
    }

    /**
     * Für die schnelle Live-Vorschau beim Kartenklick (ohne globale Relikte).
     * Ideal, um dem Spieler den garantierten Basis-Score anzuzeigen.
     */
    public static ScoreContext preview(int floatingBank, HandContext hand, YakuResult bestYaku, double koiKoiMult) {
        return new ScoreContext(
            floatingBank,
            hand,
            bestYaku,
            List.of(), // Keine Omamoris in der puren Basis-Preview
            koiKoiMult
        );
    }

    // Base Chips der aktuell stärksten Hand (sicher gegen null)
    public int getYakuBaseChips() {
        return bestYaku != null ? bestYaku.baseChips() : 0;
    }

    // Base Mult der aktuell stärksten Hand (sicher gegen null)
    public int getYakuBaseMult() {
        return bestYaku != null ? bestYaku.baseMult() : 0;
    }
}
