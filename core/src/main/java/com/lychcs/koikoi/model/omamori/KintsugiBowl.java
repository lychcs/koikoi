package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class KintsugiBowl implements Omamori {

    @Override
    public String getName() { return "Kintsugi Bowl"; }

    @Override
    public Rarity getRarity() { return Rarity.RARE; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Triggert NUR, wenn genau 1 Karte gespielt wird
        if (context.hand().getTotalCardCount() == 1) {
            acc.addChips("Kintsugi (Broken)", 40);
            acc.addMult("Kintsugi (Gold)", 4);
            return true;
        }
        return false;
    }
}
