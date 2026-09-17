package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.YakuProgression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Waehlt das Yaku mit der tatsaechlich hoechsten Auszahlung.
 *
 * <p>Die Kandidatenerkennung bleibt eine reine, deterministische Aufgabe von
 * {@link YakuDetector#detectAll(List)}. Anschliessend wird jeder Kandidat mit
 * demselben Rechenkern bewertet, den auch die echte Wertung nutzt –
 * {@link ScoreCalculator#calculate(ScoreContext, ScoreCalculator.Mode)} im
 * Modus {@link ScoreCalculator.Mode#ESTIMATE}. Dadurch entsteht keine zweite
 * Scoreformel, und die Bewertung ist vollstaendig side-effect-frei: keine
 * Zufallszahlen, keine Mon-/Void-Dust-Vergabe, keine XP, keine erschoepften
 * Shikigami, keine Aenderung an Karten oder {@link com.lychcs.koikoi.run.RunSession}.</p>
 *
 * <p>Bei identischem Endscore gewinnt der zuerst erkannte Kandidat, also die
 * stabile Detektionsreihenfolge (kein Zufall).</p>
 */
public final class YakuSelector {

    private YakuSelector() {}

    /**
     * Bewertet alle Yaku-Kandidaten einer ausgespielten Hand und liefert den
     * Kandidaten mit der hoechsten realen Auszahlung (inklusive Yaku-Level,
     * Kartenbasiswerten, Hankos, Shikigami und Omamori).
     *
     * @param evaluatedCards       tatsaechlich ausgespielte Karten (ggf. durch Oni mutiert)
     * @param unplayedCards        nicht ausgespielte Karten der Hand
     * @param omamoris             aktive Omamori in Slot-Reihenfolge
     * @param activeAltarShikigami aktives Shikigami im Altar (darf null sein)
     * @param progression          Yaku-Level des Runs (darf null sein)
     * @param visualOrder          sichtbare Links-nach-rechts-Reihenfolge der ausgespielten Karten
     */
    public static Optional<YakuResult> findBestByRealScore(
        List<Card> evaluatedCards,
        List<Card> unplayedCards,
        List<Omamori> omamoris,
        Shikigami activeAltarShikigami,
        YakuProgression progression,
        List<Card> visualOrder
    ) {
        List<YakuResult> candidates = YakuDetector.detectAll(evaluatedCards);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        HandContext hand = new HandContext(evaluatedCards);
        YakuResult best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (YakuResult candidate : candidates) {
            List<Card> ordered = orderMatchedCards(candidate.matchedCards(), visualOrder);
            ScoreContext context = new ScoreContext(
                hand, unplayedCards, candidate, omamoris, activeAltarShikigami, progression, ordered);

            CalculationBreakdown estimate = ScoreCalculator.calculate(context, ScoreCalculator.Mode.ESTIMATE);
            if (estimate.finalScore() > bestScore) {
                bestScore = estimate.finalScore();
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Bringt die gewerteten Karten in die sichtbare Links-nach-rechts-Reihenfolge
     * der ausgespielten Hand. Karten, die in der Sichtreihenfolge fehlen, werden
     * stabil angehaengt, damit kein gewertetes Blatt verloren geht.
     */
    public static List<Card> orderMatchedCards(List<Card> matchedCards, List<Card> visualOrder) {
        List<Card> ordered = new ArrayList<>(matchedCards.size());
        if (visualOrder != null) {
            for (Card card : visualOrder) {
                if (matchedCards.contains(card) && !ordered.contains(card)) {
                    ordered.add(card);
                }
            }
        }
        for (Card card : matchedCards) {
            if (!ordered.contains(card)) {
                ordered.add(card);
            }
        }
        return ordered;
    }
}
