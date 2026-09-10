package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class Kuroneko implements Omamori {

    @Override
    public String getName() {
        return "Kuroneko";
    }
    @Override
    public String getDescription() { return "..."; }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Die Katze schaut auf die aktuell gespielte Hand
        int beastCount = context.hand().getRankCount(Rank.BEAST);

        if (beastCount > 0) {
            // +15 Chips pro gespieltem Beast
            acc.addChips("Kuroneko (+Chips)", beastCount * 15);
            // x1.5 Mult oben drauf, weil Tiere im Spiel sind
            acc.multiplyMult("Kuroneko (xMult)", 1.5);
            return true;
        }

        return false;
    }
}
