package com.lychcs.koikoi.scoring;

import com.badlogic.gdx.math.MathUtils;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.hanko.StoneSeal;

import java.util.List;

public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static CalculationBreakdown calculate(ScoreContext context) {
        long yakuChips = context.getYakuBaseChips();
        long yakuBaseMult = context.getYakuBaseMult();
        String yakuName = context.bestYaku() != null ? context.bestYaku().type().getDisplayName() : "High Card";

        // 1. Base Setup
        long startingChips = yakuChips;
        long startingMult = Math.max(1L, yakuBaseMult);

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult);
        acc.addChips("Base " + yakuName, 0L);

        // 2. Card Effects (Hankos / Seals)
        for (var card : context.hand().getAllCards()) {
            switch (card.effect()) {
                case WHITE_SEAL -> {
                    acc.addChips(card.name() + " (White Seal)", 30L);
                }
                case BLACK_SEAL -> {
                    acc.addMult(card.name() + " (Black Seal)", 4L);
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
                    acc.addChips(card.name() + " (Blood Sacrifice)", 50L);
                    acc.addMult(card.name() + " (Blood Surge)", 10L);
                }
                case STONE_SEAL -> {
                    acc.addChips(card.name() + " (Stone Seal)", StoneSeal.LEVEL_1_CHIP_BONUS);
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
        long handPayout = acc.getChips() * acc.getMult();
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
