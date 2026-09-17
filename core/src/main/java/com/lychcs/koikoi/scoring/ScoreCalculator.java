package com.lychcs.koikoi.scoring;

import com.badlogic.gdx.math.MathUtils;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.hanko.BloodSeal;
import com.lychcs.koikoi.model.hanko.StoneSeal;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;

import java.util.List;

/**
 * Gemeinsamer, reiner Score-Berechnungskern. Es werden ausschliesslich die
 * Karten gewertet, mit denen das erkannte Yaku erfuellt wurde
 * ({@code context.scoringCards()}) – in sichtbarer Links-nach-rechts-Reihenfolge.
 * Alle Werte sind Dezimalwerte (double); es gibt keine Zwischenrundung.
 *
 * <p>Der Kern kennt genau zwei Modi:</p>
 * <ul>
 *   <li>{@link Mode#ESTIMATE}: vollstaendig side-effect-frei – keine
 *       Zufallszahlen, keine Mon-/Void-Dust-Belohnungen, keine Zustandsaenderung.
 *       Wird fuer die Auswahl des besten Yaku nach realer Auszahlung genutzt.</li>
 *   <li>{@link Mode#COMMIT}: echte, einmalige Wertung inklusive Zufallseffekten
 *       und Belohnungen.</li>
 * </ul>
 *
 * <p>Reihenfolge (identisch fuer Mathematik und Animation): Yaku-Basis →
 * gewertete Karten links nach rechts (Kartenbasis, danach Hanko derselben Karte)
 * → aktive Omamori in Slot-Reihenfolge → aktives Shikigami (Begleiterbonus ganz
 * am Ende) → Endscore. Der Begleiterbonus wird also NACH den Omamori berechnet
 * und angezeigt.</p>
 */
public final class ScoreCalculator {

    /** Berechnungsmodus. */
    public enum Mode {
        /** Echte Wertung: Zufallseffekte und Belohnungen werden erzeugt. */
        COMMIT,
        /** Reine Bewertung: keine Seiteneffekte, keine Belohnungen, kein Zufall. */
        ESTIMATE
    }

    private ScoreCalculator() {}

    /** Echte Wertung (entspricht {@link Mode#COMMIT}). */
    public static CalculationBreakdown calculate(ScoreContext context) {
        return calculate(context, Mode.COMMIT);
    }

    /**
     * Bewertet eine Hand mit dem gemeinsamen Rechenkern.
     *
     * @param mode COMMIT fuer die echte, einmalige Wertung; ESTIMATE fuer die
     *             side-effect-freie Bewertung von Yaku-Kandidaten
     */
    public static CalculationBreakdown calculate(ScoreContext context, Mode mode) {
        boolean commit = mode == Mode.COMMIT;

        double yakuChips = context.getYakuBaseChips();
        double yakuBaseMult = context.getYakuBaseMult();

        // 1. Yaku-Basis-Chips und Yaku-Basis-Mult initialisieren.
        double startingChips = Math.max(0.0, yakuChips);
        double startingMult = Math.max(1.0, yakuBaseMult);

        ScoreAccumulator acc = new ScoreAccumulator(startingChips, startingMult, commit);

        // 2. Gewertete Karten von links nach rechts:
        //    erst Rank-Basis-Chips der Karte, danach der Hanko-Effekt derselben Karte.
        for (Card card : context.scoringCards()) {
            acc.beginCardBase(card);
            acc.addChips(card.name() + " (Base)", card.rank().getBaseValue());
            acc.beginHanko(card);
            applyHanko(acc, card, commit);
        }
        acc.endContext();

        // 3. Aktive Omamori von links nach rechts (exakte Slot-Reihenfolge).
        //    Yata Mirror kopiert dabei seinen linken Nachbarn.
        for (Omamori omamori : context.omamoris()) {
            acc.beginOmamori(omamori);
            omamori.evaluate(context, acc);
            acc.endContext();
        }

        // 4. Aktives Shikigami (nur wenn vorhanden und einsatzbereit) - der
        //    Begleiterbonus kommt immer ganz am Ende.
        Shikigami shikigami = context.activeAltarShikigami();
        if (shikigami != null && !shikigami.isExhausted()) {
            acc.beginShikigami(shikigami);
            shikigami.activate(context, acc);
            acc.endContext();
        }

        // 5. Endscore: Chips x Mult als Dezimalwert, ohne Rundung.
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

    /**
     * Hanko-Effekt einer gewerteten Karte. Zufallseffekte (Gold/Void) werden
     * ausschliesslich im COMMIT-Modus ausgewuerfelt: im ESTIMATE-Modus wird kein
     * Zufallszustand verbraucht.
     */
    private static void applyHanko(ScoreAccumulator acc, Card card, boolean commit) {
        switch (card.effect()) {
            case WHITE_SEAL -> acc.addChips(card.name() + " (White Seal)", 30.0);
            case BLACK_SEAL -> acc.addMult(card.name() + " (Black Seal)", 4.0);
            case GOLDEN_SEAL -> {
                if (commit) {
                    if (MathUtils.random(1, 4) == 1) {
                        acc.addMon(card.name() + " (Gold Seal)", 3);
                    }
                    if (MathUtils.random(1, 20) == 1) {
                        acc.addMon(card.name() + " (Jackpot Seal)", 10);
                    }
                }
            }
            case VOID_SEAL -> {
                if (commit) {
                    if (MathUtils.random(1, 4) == 1) {
                        acc.addVoidDust(card.name() + " (Void Echo)", 5);
                    }
                    if (MathUtils.random(1, 20) == 1) {
                        acc.addVoidDust(card.name() + " (Void Rift)", 10);
                    }
                }
            }
            case BLOOD_SEAL -> {
                // Finales Blood Seal: nur +Chips und +Mult (keine Bannung, keine Zerstoerung).
                acc.addChips(card.name() + " (Blood Seal)", BloodSeal.CHIP_BONUS);
                acc.addMult(card.name() + " (Blood Seal Mult)", BloodSeal.MULT_BONUS);
            }
            case STONE_SEAL -> acc.addChips(card.name() + " (Stone Seal)", StoneSeal.LEVEL_1_CHIP_BONUS);
            case POLYCHROME_SEAL -> {
                // Wird im HandContext verarbeitet
            }
            case NONE -> {
                // Keine Siegel-Wirkung
            }
        }
    }
}

