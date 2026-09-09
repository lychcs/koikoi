package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class TeruTeruBozu implements Omamori {

    @Override
    public String getName() { return "Teru Teru Bozu"; }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int unplayedPetals = 0;

        for (Card card : context.unplayedCards()) {
            if (card.rank() == Rank.PETAL) {
                unplayedPetals++;
            }
        }

        if (unplayedPetals > 0) {
            acc.addChips("Waiting for Rain", unplayedPetals * 10);
            return true;
        }
        return false;
    }
}
