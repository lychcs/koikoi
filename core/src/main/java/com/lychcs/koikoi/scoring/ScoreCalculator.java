package com.lychcs.koikoi.scoring;

import com.badlogic.gdx.math.MathUtils;
import com.lychcs.koikoi.model.hanko.HankoEffect;

import java.util.List;

public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static CalculationBreakdown calculate(ScoreContext context) {
        int yakuChips = context.getYakuBaseChips();
        int yakuBaseMult = context.getYakuBaseMult();
        String yakuName = context.bestYaku() != null ? context.bestYaku().type().getDisplayName() : "High Card";

        // 1. Base Setup
        int startingChips = yakuChips;
        int startingMult = Math.max(1, yakuBaseMult);

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult);
        acc.addChips("Base " + yakuName, 0);

        // 2. Card Effects (Hankos / Seals)
        for (var card : context.hand().getAllCards()) {
            switch (card.effect()) {
                case WHITE_SEAL -> {
                    acc.addChips(card.name() + " (White Seal)", 30);
                }
                case BLACK_SEAL -> {
                    acc.addMult(card.name() + " (Black Seal)", 4);
                }
                case GOLDEN_SEAL -> {
                    if (MathUtils.random(1, 4) == 1) {
                        acc.addMon(card.name() + " (Gold Seal)", 3);
                    }
                    if (MathUtils.random(1, 20) == 1) {
                        acc.addMon(card.name() + " (Jackpot Seal)", 10);
                    }
                }
                case VOID_SEAL -> {
                    if (MathUtils.random(1, 4) == 1) {
                        acc.addVoidDust(card.name() + " (Void Echo)", 5);
                    }
                    if (MathUtils.random(1, 20) == 1) {
                        acc.addVoidDust(card.name() + " (Void Rift)", 10);
                    }
                }
                case BLOOD_SEAL -> {
                    acc.addChips(card.name() + " (Blood Sacrifice)", 50);
                    acc.addMult(card.name() + " (Blood Surge)", 10);
                }
                case STONE_SEAL -> {
                    // Wird im GameScreen verarbeitet
                }
                case POLYCHROME_SEAL -> {
                    // Wird im HandContext verarbeitet
                }
                case NONE -> {
                    // Keine Siegel-Wirkung
                }
            }
        }

        // 3. Yokai Evaluierung (falls einer im Altar liegt und einsatzbereit ist)
        if (context.activeAltarYokai() != null && !context.activeAltarYokai().isExhausted()) {
            context.activeAltarYokai().activate(context, acc);
        }

        // 4. Omamori Evaluierung
        for (var omamori : context.omamoris()) {
            omamori.evaluate(context, acc);
        }

        // 5. Final Payout
        long handPayout = (long) acc.getChips() * acc.getMult();
        long finalPayout = handPayout;

        return new CalculationBreakdown(
            yakuChips,
            yakuBaseMult,
            acc.getChips(),
            acc.getMult(),
            finalPayout,
            List.copyOf(acc.getEvents())
        );
    }
}
