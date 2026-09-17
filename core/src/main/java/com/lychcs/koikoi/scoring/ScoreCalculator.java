package com.lychcs.koikoi.scoring;

import com.badlogic.gdx.math.MathUtils;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.hanko.BloodSeal;
import com.lychcs.koikoi.model.hanko.StoneSeal;

import java.util.List;

/**
 * Wertet eine ausgespielte Hand. Es werden ausschliesslich die Karten gewertet,
 * mit denen das erkannte Yaku erfuellt wurde ({@code matchedCards}).
 * Alle Werte sind Dezimalwerte (double); es gibt keine Zwischenrundung.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static CalculationBreakdown calculate(ScoreContext context) {
        double yakuChips = context.getYakuBaseChips();
        double yakuBaseMult = context.getYakuBaseMult();
        String yakuName = context.bestYaku() != null ? context.bestYaku().getDisplayName() : "High Card";

        // 1. Base Setup (Yaku-Chips und Yaku-Mult)
        double startingChips = Math.max(0.0, yakuChips);
        double startingMult = Math.max(1.0, yakuBaseMult);

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult);
        acc.addChips("Base " + yakuName, 0.0);

        // 2. Basis-Chips der Karten, die zum erkannten Yaku gehoeren.
        //    Autoritative Wertquelle ist Rank.getBaseValue().
        List<Card> scoringCards = context.matchedCards();
        for (Card card : scoringCards) {
            acc.addChips(card.name() + " (Base)", card.rank().getBaseValue());
        }

        // 3. Card Effects (Hankos / Seals) - nur auf den gewerteten Karten.
        for (Card card : scoringCards) {
            switch (card.effect()) {
                case WHITE_SEAL -> {
                    acc.addChips(card.name() + " (White Seal)", 30.0);
                }
                case BLACK_SEAL -> {
                    acc.addMult(card.name() + " (Black Seal)", 4.0);
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
                    // Finales Blood Seal: nur +Chips und +Mult (keine Bannung, keine Zerstoerung).
                    acc.addChips(card.name() + " (Blood Seal)", BloodSeal.CHIP_BONUS);
                    acc.addMult(card.name() + " (Blood Seal Mult)", BloodSeal.MULT_BONUS);
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

        // 4. Shikigami Evaluierung (falls eines im Altar liegt und einsatzbereit ist)
        if (context.activeAltarShikigami() != null && !context.activeAltarShikigami().isExhausted()) {
            context.activeAltarShikigami().activate(context, acc);
        }

        // 5. Omamori Evaluierung (siehe Beschreibungen: diese pruefen die ausgespielte Hand)
        for (var omamori : context.omamoris()) {
            omamori.evaluate(context, acc);
        }

        // 6. Final Payout: Chips x Mult als Dezimalwert, ohne Rundung.
        double finalScore = acc.getChips() * acc.getMult();

        return new CalculationBreakdown(
            yakuChips,
            yakuBaseMult,
            acc.getChips(),
            acc.getMult(),
            finalScore,
            List.copyOf(acc.getEvents())
        );
    }
}
