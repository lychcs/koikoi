package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class DarumaDoll implements Omamori {

    @Override
    public String getName() {
        return "Daruma Doll";
    }

    @Override
    public String getDescription() {
        return "If played hand contains 1 to 3 cards, grants x2.0 Mult.";
    }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int playedCards = context.hand().getTotalCardCount();

        if (playedCards > 0 && playedCards <= 3) {
            acc.multiplyMult("Daruma Doll (Focus)", 2.0);
            return true;
        }

        return false;
    }
}
