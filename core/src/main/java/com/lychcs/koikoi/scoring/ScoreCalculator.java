package com.lychcs.koikoi.scoring;

import java.util.List;

public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static CalculationBreakdown calculate(ScoreContext context) {
        // Sicherer Abruf über den Context (verhindert NPE, falls bestYaku null ist)
        int yakuChips = context.getYakuBaseChips();
        int yakuBaseMult = context.getYakuBaseMult();
        String yakuName = context.bestYaku() != null ? context.bestYaku().type().getDisplayName() : "High Card";

        // 1. Base Setup
        int startingChips = context.floatingBank() + yakuChips;
        int startingMult = Math.max(1, yakuBaseMult); // Fallback auf 1

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult);
        acc.addChips("Base " + yakuName, 0); // Initiales Event fürs UI

        // 2. Card Effects (effectMult / Foil etc.)
        for (var card : context.hand().getAllCards()) {
            // Platzhalter für Editionen
        }

        // 3. Omamori (Joker) Evaluierung (Left-to-Right)
        for (var omamori : context.omamoris()) {
            omamori.evaluate(context.hand(), acc);
        }

        // 4. Koi-Koi Push-Your-Luck Multiplikator
        if (context.koiKoiMult() > 1.0) {
            acc.multiplyMult("Koi-Koi Risk", context.koiKoiMult());
        }

        // 5. Final Payout
        long finalPayout = (long) acc.getChips() * acc.getMult();

        return new CalculationBreakdown(
            context.floatingBank(),
            yakuChips,
            yakuBaseMult,
            acc.getChips(),
            acc.getMult(),
            finalPayout,
            List.copyOf(acc.getEvents())
        );
    }
}
