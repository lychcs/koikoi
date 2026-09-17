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
        double baseChips = context.getYakuBaseChips();
        double baseMult = context.getYakuBaseMult();

        // Der Spiegel des Enma richtet über die Basiswerte und kehrt sie um
        if (baseChips > 0.0 || baseMult > 0.0) {

            // Chips werden zu Mult (halbiert, um Balance zu wahren)
            double karmaMult = baseChips / 2.0;

            // Mult wird zu Chips (verzehnfacht, damit es sich wuchtig anfühlt)
            double karmaChips = baseMult * 10.0;

            acc.addChips("Karma Reflected (Chips)", karmaChips);
            acc.addMult("Karma Reflected (Mult)", karmaMult);

            return true;
        }
        return false;
    }
}
