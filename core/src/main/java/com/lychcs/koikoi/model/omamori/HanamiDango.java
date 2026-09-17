package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class HanamiDango implements Omamori {

    @Override
    public String getName() { return "Hanami Dango"; }

    @Override
    public String getDescription() {
        return "Grants +15 Chips if at least one Spring card is played.";
    }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        if (context.hand().getSeasonCount(Season.SPRING) > 0) {
            acc.addChips("Hanami Dango", 15);
            return true;
        }
        return false;
    }
}
