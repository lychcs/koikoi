package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class ToriiGate implements Omamori {

    /** Multiplikator, wenn die Bedingung erfuellt ist. */
    private static final double MUNDANE_PURITY_MULT = 1.5;

    @Override
    public String getName() { return "Torii Gate"; }

    @Override
    public String getDescription() {
        return "Grants x" + MUNDANE_PURITY_MULT + " Mult if the played hand contains no Beast card.";
    }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Bedingung: Es wurde keine einzige Karte mit dem Rang BEAST gespielt.
        boolean containsBeast = context.hand().getRankCount(Rank.BEAST) > 0;

        if (!containsBeast) {
            acc.multiplyMult("Mundane Purity", MUNDANE_PURITY_MULT);
            return true;
        }
        return false;
    }
}
