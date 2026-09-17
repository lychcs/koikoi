package com.lychcs.koikoi.model.omamori;
import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class JohariMirror implements Omamori {

    @Override
    public String getName() { return "Johari no Kagami"; }

    @Override
    public String getDescription() {
        return "Reflects base values: adds half of base Chips as Mult, and 10x base Mult as Chips.";
    }

    @Override
    public Rarity getRarity() { return Rarity.LEGENDARY; }

    @Override
    public boolean evaluate(ScoreContext context, ScoreAccumulator acc) {
        long baseChips = context.getYakuBaseChips();
        long baseMult = context.getYakuBaseMult();

        // Der Spiegel des Enma richtet über die Basiswerte und kehrt sie um
        if (baseChips > 0 || baseMult > 0) {

            // Chips werden zu Mult (halbiert, um Balance zu wahren)
            long karmaMult = baseChips / 2L;

            // Mult wird zu Chips (verzehnfacht, damit es sich wuchtig anfühlt)
            long karmaChips = baseMult * 10L;

            acc.addChips("Karma Reflected (Chips)", karmaChips);
            acc.addMult("Karma Reflected (Mult)", karmaMult);

            return true;
        }
        return false;
    }
}
