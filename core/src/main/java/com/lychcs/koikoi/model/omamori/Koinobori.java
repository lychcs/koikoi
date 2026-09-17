package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.HandContext;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class Koinobori implements Omamori {

    @Override
    public String getName() { return "Koinobori"; }

    @Override
    public String getDescription() {
        return "Currently dormant. Grants no scoring effect.";
    }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        //???
        return false;
    }
}
