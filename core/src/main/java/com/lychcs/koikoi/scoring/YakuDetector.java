package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.model.hanko.HankoEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Erkennt alle gueltigen Yaku-Varianten einer ausgespielten Hand.
 *
 * <p>Eine Variante ist ein konkretes physisches Kartenblatt: eine gueltige
 * Belegung der von einem Yaku geforderten Plaetze mit tatsaechlich ausgespielten
 * Karten. Kann derselbe Platz von mehreren gleichartigen Karten erfuellt werden
 * (z. B. zwei inhaltsgleiche Kopien oder zwei verschiedene Stempel), entstehen
 * mehrere Varianten.</p>
 *
 * <p>Dieser Detektor bevorzugt keine Variante. Er erzeugt ausschliesslich
 * strukturell gueltige Varianten eines erkannten Yaku - keine beliebigen
 * Teilmengen aller Karten - und ueberlaesst die Auswahl {@link YakuSelector}:
 * dort entscheidet die reale Auszahlung ueber den gemeinsamen Score-Kern
 * ({@link ScoreCalculator.Mode#ESTIMATE}); bei identischem Endscore entscheidet
 * die sichtbare Links-nach-rechts-Reihenfolge.</p>
 *
 * <p>Es gibt keine Rang-, Hanko- oder Positionsheuristik. Die Reihenfolge der
 * uebergebenen Karten ist fuer das Ergebnis unerheblich; sie bestimmt nur die
 * Reihenfolge der gelieferten Varianten und die Reihenfolge der Karten innerhalb
 * einer Variante (aufsteigende Indizes = sichtbare Reihenfolge).</p>
 */
public final class YakuDetector {

    /** Anzahl Karten fuer die Vier-Karten-Yaku (Rang und Season). */
    private static final int FOUR_CARD_GROUP_SIZE = 4;

    private static final int WILD_COURT_BEASTS = 3;
    private static final int WILD_COURT_RIBBONS = 2;
    private static final int MONOCHROME_CARDS = 5;

    /** Reihenfolge fuer "eine Karte je Rang". */
    private static final List<Rank> RANK_ORDER = List.of(Rank.PETAL, Rank.RIBBON, Rank.BEAST, Rank.HIKARI);

    private YakuDetector() {}

    /**
     * Liefert alle gueltigen Yaku-Varianten der uebergebenen (ausgespielten)
     * Karten. Jede Variante traegt mit {@code matchedCards} genau die Karten,
     * die fuer sie gewertet werden.
     */
    public static List<YakuResult> detectAll(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        List<Card> hand = List.copyOf(cards);
        List<YakuResult> variants = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // --- 1. IMPERIAL COURT (vier physische Hikari-Karten) ---
        List<Integer> hikari = indicesOfRank(hand, Rank.HIKARI);
        if (hikari.size() >= FOUR_CARD_GROUP_SIZE) {
            for (int[] subset : combinations(hikari, FOUR_CARD_GROUP_SIZE)) {
                addVariant(variants, seen, hand, subset, YakuType.IMPERIAL_COURT, null);
            }
        }

        // --- 2. DUSK AND DAWN (Glazing Sun + Red Moon, beide weiterhin HIKARI) ---
        List<Integer> glazingSun = indicesOfCardId(hand, CardID.SUMMER_HIKARI_GLAZING_SUN, Rank.HIKARI);
        List<Integer> redMoon = indicesOfCardId(hand, CardID.AUTUMN_HIKARI_RED_MOON, Rank.HIKARI);
        for (int sun : glazingSun) {
            for (int moon : redMoon) {
                addVariant(variants, seen, hand, new int[] {sun, moon}, YakuType.DUSK_AND_DAWN, null);
            }
        }

        // --- 3. TRUE SEASON (Petal + Ribbon + Beast + Hikari in einer Jahreszeit) ---
        boolean hasTrueSeason = false;
        for (Season season : Season.values()) {
            for (int[] choice : oneCardPerRank(hand, indicesOfVirtualSeason(hand, season))) {
                addVariant(variants, seen, hand, choice, YakuType.TRUE_SEASON, season);
                hasTrueSeason = true;
            }
        }

        // --- 4. HARMONY (alle vier Raenge, Jahreszeit egal) ---
        // Regel unveraendert: liegt ein True Season vor, wird Harmony nicht angeboten.
        if (!hasTrueSeason) {
            for (int[] choice : oneCardPerRank(hand, allIndices(hand))) {
                addVariant(variants, seen, hand, choice, YakuType.HARMONY, null);
            }
        }

        // --- 5. WILD COURT (3 physische Beasts + 2 physische Ribbons) ---
        List<Integer> beasts = indicesOfRank(hand, Rank.BEAST);
        List<Integer> ribbons = indicesOfRank(hand, Rank.RIBBON);
        if (beasts.size() >= WILD_COURT_BEASTS && ribbons.size() >= WILD_COURT_RIBBONS) {
            for (int[] beastSet : combinations(beasts, WILD_COURT_BEASTS)) {
                for (int[] ribbonSet : combinations(ribbons, WILD_COURT_RIBBONS)) {
                    addVariant(variants, seen, hand, merge(beastSet, ribbonSet), YakuType.WILD_COURT, null);
                }
            }
        }

        // --- 6. MONOCHROME (5 physische Karten derselben Jahreszeit) ---
        for (Season season : Season.values()) {
            List<Integer> seasonCards = indicesOfVirtualSeason(hand, season);
            if (seasonCards.size() < MONOCHROME_CARDS) {
                continue;
            }
            for (int[] subset : combinations(seasonCards, MONOCHROME_CARDS)) {
                addVariant(variants, seen, hand, subset, YakuType.MONOCHROME, null);
            }
        }

        // --- 7. Grund-Yaku: vier verschiedene Karten eines Rangs ---
        addDistinctRankVariants(variants, seen, hand, Rank.PETAL, YakuType.FOUR_PETALS);
        addDistinctRankVariants(variants, seen, hand, Rank.RIBBON, YakuType.FOUR_RIBBONS);
        addDistinctRankVariants(variants, seen, hand, Rank.BEAST, YakuType.FOUR_BEASTS);

        // --- 8. FOUR_OF_ONE_SEASON (vier physische Karten einer Jahreszeit) ---
        for (Season season : Season.values()) {
            List<Integer> seasonCards = indicesOfVirtualSeason(hand, season);
            if (seasonCards.size() < FOUR_CARD_GROUP_SIZE) {
                continue;
            }
            for (int[] subset : combinations(seasonCards, FOUR_CARD_GROUP_SIZE)) {
                addVariant(variants, seen, hand, subset, YakuType.FOUR_OF_ONE_SEASON, season);
            }
        }

        // --- 9. Beast-Duos (zentrale, datengetriebene Definition) ---
        for (BeastCombinations.BeastCombination combination : BeastCombinations.all()) {
            List<Integer> first = indicesOfCardId(hand, combination.first(), Rank.BEAST);
            List<Integer> second = indicesOfCardId(hand, combination.second(), Rank.BEAST);
            for (int firstIndex : first) {
                for (int secondIndex : second) {
                    addVariant(variants, seen, hand, new int[] {firstIndex, secondIndex},
                        combination.yakuType(), null);
                }
            }
        }

        // --- 10. LONE SPIRIT (Fallback / High Card) ---
        for (int index = 0; index < hand.size(); index++) {
            addVariant(variants, seen, hand, new int[] {index}, YakuType.LONE_SPIRIT, null);
        }

        return variants;
    }

    // ---------------------------------------------------------------------
    // Varianten-Aufbau
    // ---------------------------------------------------------------------

    /**
     * Fuegt eine Variante hinzu. Die Indizes werden aufsteigend sortiert, damit
     * {@code matchedCards} bereits der sichtbaren Links-nach-rechts-Reihenfolge
     * der uebergebenen Karten entspricht. Identische Belegungen (gleicher
     * Yaku-Typ, gleiche Jahreszeit, gleiche Kartenindizes) entstehen nur einmal.
     */
    private static void addVariant(List<YakuResult> variants, Set<String> seen, List<Card> hand,
                                   int[] indices, YakuType type, Season season) {
        int[] sorted = indices.clone();
        Arrays.sort(sorted);

        StringBuilder key = new StringBuilder(32);
        key.append(type.ordinal()).append(':').append(season == null ? -1 : season.ordinal()).append(':');
        for (int index : sorted) {
            key.append(index).append(',');
        }
        if (!seen.add(key.toString())) {
            return;
        }

        List<Card> matched = new ArrayList<>(sorted.length);
        for (int index : sorted) {
            matched.add(hand.get(index));
        }

        variants.add(season == null
            ? YakuResult.of(type, matched)
            : YakuResult.ofSeason(type, season, matched));
    }

    /**
     * Vier verschiedene Karten eines Rangs: alle gueltigen Belegungen, also
     * alle 4er-Teilmengen mit vier unterschiedlichen CardIDs. Duplikate
     * derselben CardID sind - wie bisher - nicht zulaessig.
     */
    private static void addDistinctRankVariants(List<YakuResult> variants, Set<String> seen, List<Card> hand,
                                                Rank rank, YakuType type) {
        List<Integer> candidates = indicesOfRank(hand, rank);
        if (distinctCardIdCount(hand, candidates) < FOUR_CARD_GROUP_SIZE) {
            return;
        }

        for (int[] subset : combinations(candidates, FOUR_CARD_GROUP_SIZE)) {
            if (distinctCardIdCount(hand, subset) == FOUR_CARD_GROUP_SIZE) {
                addVariant(variants, seen, hand, subset, type, null);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Strukturelle Helfer (rein, ohne Rang-/Hanko-Praeferenzen)
    // ---------------------------------------------------------------------

    private static List<Integer> allIndices(List<Card> hand) {
        List<Integer> indices = new ArrayList<>(hand.size());
        for (int index = 0; index < hand.size(); index++) {
            indices.add(index);
        }
        return indices;
    }

    private static List<Integer> indicesOfRank(List<Card> hand, Rank rank) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < hand.size(); index++) {
            if (hand.get(index).rank() == rank) {
                indices.add(index);
            }
        }
        return indices;
    }

    /**
     * Indizes aller Karten mit dieser CardID; mit {@code requiredRank} zusaetzlich
     * auf einen Rang eingeschraenkt (Effekte wie Oni koennen den Rang aendern).
     */
    private static List<Integer> indicesOfCardId(List<Card> hand, CardID id, Rank requiredRank) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < hand.size(); index++) {
            Card card = hand.get(index);
            if (card.id() == id && (requiredRank == null || card.rank() == requiredRank)) {
                indices.add(index);
            }
        }
        return indices;
    }

    /**
     * Indizes der Karten, die in der virtuellen Season-Sicht zu dieser Jahreszeit
     * zaehlen - identisch zur Regel in {@link HandContext} (Polychrome zaehlt als
     * jede Jahreszeit).
     */
    private static List<Integer> indicesOfVirtualSeason(List<Card> hand, Season season) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < hand.size(); index++) {
            Card card = hand.get(index);
            if (card.season() == season || card.effect() == HankoEffect.POLYCHROME_SEAL) {
                indices.add(index);
            }
        }
        return indices;
    }

    /**
     * Alle Belegungen mit genau einer Karte je Rang aus {@code candidates}.
     * Leer, wenn ein Rang nicht belegbar ist.
     */
    private static List<int[]> oneCardPerRank(List<Card> hand, List<Integer> candidates) {
        List<int[]> choices = new ArrayList<>();
        fillRankSlots(hand, candidates, 0, new int[RANK_ORDER.size()], choices);
        return choices;
    }

    private static void fillRankSlots(List<Card> hand, List<Integer> candidates, int rankIndex,
                                      int[] current, List<int[]> choices) {
        if (rankIndex == RANK_ORDER.size()) {
            choices.add(current.clone());
            return;
        }

        Rank rank = RANK_ORDER.get(rankIndex);
        for (int index : candidates) {
            if (hand.get(index).rank() != rank) {
                continue;
            }
            current[rankIndex] = index;
            fillRankSlots(hand, candidates, rankIndex + 1, current, choices);
        }
    }

    /** Alle Index-Kombinationen der Groesse {@code size} in aufsteigender Reihenfolge. */
    private static List<int[]> combinations(List<Integer> pool, int size) {
        List<int[]> result = new ArrayList<>();
        if (size <= 0 || pool.size() < size) {
            return result;
        }
        collectCombinations(pool, size, 0, 0, new int[size], result);
        return result;
    }

    private static void collectCombinations(List<Integer> pool, int size, int poolIndex, int chosen,
                                            int[] current, List<int[]> result) {
        if (chosen == size) {
            result.add(current.clone());
            return;
        }
        for (int index = poolIndex; index <= pool.size() - (size - chosen); index++) {
            current[chosen] = pool.get(index);
            collectCombinations(pool, size, index + 1, chosen + 1, current, result);
        }
    }

    private static int[] merge(int[] first, int[] second) {
        int[] merged = new int[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private static int distinctCardIdCount(List<Card> hand, List<Integer> indices) {
        Set<CardID> ids = new HashSet<>();
        for (int index : indices) {
            ids.add(hand.get(index).id());
        }
        return ids.size();
    }

    private static int distinctCardIdCount(List<Card> hand, int[] indices) {
        Set<CardID> ids = new HashSet<>();
        for (int index : indices) {
            ids.add(hand.get(index).id());
        }
        return ids.size();
    }
}
