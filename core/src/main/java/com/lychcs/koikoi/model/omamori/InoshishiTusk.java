package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class InoshishiTusk implements Omamori {

    @Override
    public String getName() { return "Inoshishi Tusk"; }

    @Override
    public Rarity getRarity() { return Rarity.UNCOMMON; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        // Triggert nur bei einer vollen Hand (exakt 5 Karten)
        if (context.hand().getTotalCardCount() == 5) {
            acc.addMult("Wild Charge", 15);
            return true;
        }
        return false;
    }
}
