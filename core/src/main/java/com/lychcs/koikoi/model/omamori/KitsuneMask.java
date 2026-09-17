package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class KitsuneMask implements Omamori {

    @Override
    public String getName() {
        return "Kitsune Mask";
    }

    @Override
    public String getDescription() {
        return "Grants +5 Mult for each unplayed Winter card remaining in hand.";
    }

    @Override
    public Rarity getRarity() { return Rarity.COMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int winterHeldCount = 0;

        // Der Fuchs schaut auf die ungespielten Karten (Held-in-Hand)
        for (Card card : context.unplayedCards()) {
            if (card.season() == Season.WINTER) {
                winterHeldCount++;
            }
        }

        if (winterHeldCount > 0) {
            // +5 Mult für JEDE gehaltene Winter-Karte!
            acc.addMult("Kitsune Mask (Winter Held)", winterHeldCount * 5);
            return true; // Löst Animation aus
        }

        return false;
    }
}
