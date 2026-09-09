package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class TenguFeather implements Omamori {

    @Override
    public String getName() {
        return "Tengu Feather";
    }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Der Tengu scannt die gespielten Schriftrollen (Ribbons)
        int ribbonCount = context.hand().getRankCount(Rank.RIBBON);

        if (ribbonCount > 0) {
            // Jedes Ribbon in der Hand gibt +20 Chips und +3 Mult extra
            acc.addChips("Tengu Feather (+Chips)", ribbonCount * 20);
            acc.addMult("Tengu Feather (+Mult)", ribbonCount * 3);
            return true;
        }

        return false;
    }
}
