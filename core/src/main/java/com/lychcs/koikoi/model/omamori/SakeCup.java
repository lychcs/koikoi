package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class SakeCup implements Omamori {

    @Override
    public String getName() { return "Sake Cup"; }

    @Override
    public String getDescription() {
        return "Grants +3 Mult if at least one Petal card is played.";
    }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        if (context.hand().getRankCount(Rank.PETAL) > 0) {
            acc.addMult("Sake Cup", 3);
            return true;
        }
        return false;
    }
}
