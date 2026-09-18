package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.YakuProgression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Waehlt aus allen gueltigen Yaku-Varianten die Kartenbelegung mit der
 * tatsaechlich hoechsten Auszahlung.
 *
 * <p>Die Varianten liefert {@link YakuDetector#detectAll(List)} (reine,
 * deterministische Strukturerkennung). Jede Variante wird mit demselben
 * Rechenkern bewertet, den auch die echte Wertung nutzt -
 * {@link ScoreCalculator#calculate(ScoreContext, ScoreCalculator.Mode)} im
 * Modus {@link ScoreCalculator.Mode#ESTIMATE}. Dadurch entsteht keine zweite
 * Scoreformel und es wird niemals ein roher Chips- oder Mult-Bonus
 * lexikografisch mit dem anderen verglichen.</p>
 *
 * <p><b>Auswahl:</b></p>
 * <ol>
 *   <li>Hoechster {@code finalScore} der realen Auswertung gewinnt.</li>
 *   <li>Bei identischem {@code finalScore} entscheidet die sichtbare
 *       Links-nach-rechts-Reihenfolge: die aufsteigenden Sichtindizes der
 *       gewerteten Karten werden lexikografisch verglichen; die Belegung mit der
 *       fruehesten linken Abweichung (kleinerer Index) gewinnt. Ist eine
 *       Indexliste ein Praefix der anderen, gewinnt die kuerzere Liste
 *       (lexikografische Konvention, weniger gewertete Karten).</li>
 *   <li>Erst danach greifen rein technische Fallbacks (Yaku-Enum-Reihenfolge,
 *       Jahreszeit), damit das Ergebnis auch bei vollstaendig identischen
 *       Sichtindizes deterministisch bleibt.</li>
 * </ol>
 *
 * <p>Die Bewertung ist vollstaendig side-effect-frei: keine Zufallszahlen,
 * keine Mon-/Void-Dust-Vergabe, keine XP, keine erschoepften Shikigami, keine
 * Aenderung an Karten oder {@link com.lychcs.koikoi.run.RunSession}.</p>
 */
public final class YakuSelector {

    private YakuSelector() {}

    /**
     * Bewertet alle Yaku-Varianten einer ausgespielten Hand und liefert die
     * Variante mit der hoechsten realen Auszahlung (inklusive Yaku-Level,
     * Kartenbasiswerten, Hankos, Shikigami und Omamori).
     *
     * @param evaluatedCards       tatsaechlich ausgespielte Karten (ggf. durch Oni mutiert)
     * @param unplayedCards        nicht ausgespielte Karten der Hand
     * @param omamoris             aktive Omamori in Slot-Reihenfolge
     * @param activeAltarShikigami aktives Shikigami im Altar (darf null sein)
     * @param progression          Yaku-Level des Runs (darf null sein)
     * @param visualOrder          autoritative sichtbare Links-nach-rechts-Reihenfolge
     *                             der ausgespielten Karten
     */
    public static Optional<YakuResult> findBestByRealScore(
        List<Card> evaluatedCards,
        List<Card> unplayedCards,
        List<Omamori> omamoris,
        Shikigami activeAltarShikigami,
        YakuProgression progression,
        List<Card> visualOrder
    ) {
        List<YakuResult> variants = YakuDetector.detectAll(evaluatedCards);
        if (variants.isEmpty()) {
            return Optional.empty();
        }

        // Sichtindex der autoritativen Reihenfolge. Identitaetsbasiert, damit auch
        // inhaltsgleiche Kopien ueber ihre sichtbare Position unterschieden werden.
        Map<Card, Integer> visualIndex = new IdentityHashMap<>();
        if (visualOrder != null) {
            for (int index = 0; index < visualOrder.size(); index++) {
                visualIndex.putIfAbsent(visualOrder.get(index), index);
            }
        }

        HandContext hand = new HandContext(evaluatedCards);
        YakuResult best = null;
        int[] bestIndices = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (YakuResult variant : variants) {
            List<Card> ordered = orderMatchedCards(variant.matchedCards(), visualOrder);
            ScoreContext context = new ScoreContext(
                hand, unplayedCards, variant, omamoris, activeAltarShikigami, progression, ordered);

            double score = ScoreCalculator
                .calculate(context, ScoreCalculator.Mode.ESTIMATE)
                .finalScore();

            int[] indices = ascendingVisibleIndices(variant.matchedCards(), visualIndex);
            if (best == null || isBetterVariant(score, indices, variant, bestScore, bestIndices, best)) {
                best = variant;
                bestIndices = indices;
                bestScore = score;
            }
        }

        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------------
    // Auswahl- und Sortierregeln
    // ---------------------------------------------------------------------

    /**
     * {@code true}, wenn {@code candidate} gegenueber {@code best} gewinnt:
     * zuerst der reale Endscore, danach der lexikografische Vergleich der
     * aufsteigenden Sichtindizes, erst zuletzt technische Fallbacks.
     */
    private static boolean isBetterVariant(double score, int[] indices, YakuResult candidate,
                                           double bestScore, int[] bestIndices, YakuResult best) {
        if (score != bestScore) {
            return score > bestScore;
        }

        int byVisiblePosition = compareVisibleIndices(indices, bestIndices);
        if (byVisiblePosition != 0) {
            return byVisiblePosition < 0;
        }

        // Technischer Fallback: nur erreichbar, wenn dieselben Sichtindizes von
        // unterschiedlichen Yaku-Auslegungen belegt werden.
        int byYaku = Integer.compare(candidate.type().ordinal(), best.type().ordinal());
        if (byYaku != 0) {
            return byYaku < 0;
        }

        return Integer.compare(seasonOrdinal(candidate), seasonOrdinal(best)) < 0;
    }

    /**
     * Aufsteigende sichtbare Indizes der gewerteten Karten. Karten ohne Sichtindex
     * (nicht in der autoritativen Reihenfolge enthalten) stehen ganz hinten.
     */
    private static int[] ascendingVisibleIndices(List<Card> matchedCards, Map<Card, Integer> visualIndex) {
        int[] indices = new int[matchedCards.size()];
        for (int position = 0; position < indices.length; position++) {
            Integer index = visualIndex.get(matchedCards.get(position));
            indices[position] = index == null ? Integer.MAX_VALUE : index;
        }
        Arrays.sort(indices);
        return indices;
    }

    /** Lexikografischer Vergleich aufsteigender Sichtindizes (negativ = links). */
    private static int compareVisibleIndices(int[] first, int[] second) {
        return Arrays.compare(first, second);
    }

    /** Sortierschluessel der Jahreszeit; ohne Jahreszeit gilt "vor allen Seasons". */
    private static int seasonOrdinal(YakuResult result) {
        return result.matchedSeason() == null ? -1 : result.matchedSeason().ordinal();
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
