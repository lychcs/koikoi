package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class JohariMirror implements Omamori {

    @Override
    public String getName() { return "Johari no Kagami"; }

    @Override
    public String getDescription() { return "..."; }

    @Override
    public Rarity getRarity() { return Rarity.LEGENDARY; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        int baseChips = context.getYakuBaseChips();
        int baseMult = context.getYakuBaseMult();

        // Der Spiegel des Enma richtet über die Basiswerte und kehrt sie um
        if (baseChips > 0 || baseMult > 0) {

            // Chips werden zu Mult (halbiert, um Balance zu wahren)
            int karmaMult = baseChips / 2;

            // Mult wird zu Chips (verzehnfacht, damit es sich wuchtig anfühlt)
            int karmaChips = baseMult * 10;

            acc.addChips("Karma Reflected (Chips)", karmaChips);
            acc.addMult("Karma Reflected (Mult)", karmaMult);

            return true;
        }
        return false;
    }
}
