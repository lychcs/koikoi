package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class MatchaWhisk implements Omamori {

    @Override
    public String getName() { return "Matcha Whisk"; }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int uniqueSeasons = 0;

        for (Season s : Season.values()) {
            if (context.hand().getSeasonCount(s) > 0) {
                uniqueSeasons++;
            }
        }

        // Exakt zwei Jahreszeiten bedeuten einen perfekten Blend
        if (uniqueSeasons == 2) {
            acc.addChips("Perfect Blend", 50);
            return true;
        }
        return false;
    }
}
