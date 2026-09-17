package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class ToriiGate implements Omamori {

    @Override
    public String getName() { return "Torii Gate"; }

    @Override
    public String getDescription() {
        return "Grants x1.5 Mult if played hand contains no Beast and no Yami cards.";
    }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int beasts = context.hand().getRankCount(Rank.BEAST);
        int hikari = context.hand().getRankCount(Rank.HIKARI);
        int yami = context.hand().getRankCount(Rank.YAMI);

        // Prüft, ob Karten gespielt wurden UND keine starken Ränge dabei sind
        if (context.hand().getTotalCardCount() > 0 && beasts == 0 && yami == 0) {
            acc.multiplyMult("Mundane Purity", 1.5);
            return true;
        }
        return false;
    }
}
