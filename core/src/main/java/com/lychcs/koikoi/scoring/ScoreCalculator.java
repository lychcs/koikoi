package com.lychcs.koikoi.scoring;

import java.util.List;

public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static CalculationBreakdown calculate(ScoreContext context) {
        int yakuChips = context.getYakuBaseChips();
        int yakuBaseMult = context.getYakuBaseMult();
        String yakuName = context.bestYaku() != null ? context.bestYaku().type().getDisplayName() : "High Card";

        // 1. Base Setup (NEU: floatingBank wird hier NICHT mehr reingemischt!)
        int startingChips = yakuChips;
        int startingMult = Math.max(1, yakuBaseMult);

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult);
        acc.addChips("Base " + yakuName, 0);

        // 2. Card Effects (Foil etc.)
        for (var card : context.hand().getAllCards()) {
        }

        // 3. Omamori (Joker) Evaluierung
        for (var omamori : context.omamoris()) {
            omamori.evaluate(context.hand(), acc);
        }

        // 4. Koi-Koi Push-Your-Luck Multiplikator (Wirkt nur noch auf DIESE Hand!)
        if (context.koiKoiMult() > 1.0) {
            acc.multiplyMult("Koi-Koi Risk", context.koiKoiMult());
        }

        // 5. Final Payout (NEU: floatingBank wird erst ganz am Ende addiert)
        long handPayout = (long) acc.getChips() * acc.getMult();
        long finalPayout = context.floatingBank() + handPayout;

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
