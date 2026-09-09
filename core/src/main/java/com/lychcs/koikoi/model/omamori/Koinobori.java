package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class Koinobori implements Omamori {

    @Override
    public String getName() { return "Koinobori"; }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Feuert erst, sobald Koi-Koi (Risk) größer als 1.0 ist
        if (context.koiKoiMult() > 1.0) {
            int extraChips = 100;
            int extraMult = (int) (context.koiKoiMult() * 3);

            acc.addChips("Upstream Swim (Chips)", extraChips);
            acc.addMult("Upstream Swim (Mult)", extraMult);
            return true;
        }
        return false;
    }
}
