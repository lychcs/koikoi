package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class Furin implements Omamori {

    @Override
    public String getName() { return "Wind Chime (Furin)"; }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int summerHeld = 0;
        for (Card card : context.unplayedCards()) {
            if (card.season() == Season.SUMMER) summerHeld++;
        }

        if (summerHeld > 0) {
            acc.addChips("Furin (Summer Held)", summerHeld * 10);
            return true;
        }
        return false;
    }
}
