package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.Season;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Erkennt alle Yaku einer ausgespielten Hand und transportiert mit
 * {@code matchedCards} genau die Karten, mit denen ein Yaku erfuellt wurde.
 * Nur diese Karten werden gewertet.
 *
 * <p>Die Detektionsreihenfolge ist stabil und dient als Tie-Break, wenn zwei
 * Yaku denselben EffectiveScore besitzen (keine Zufallsentscheidung).</p>
 */
public final class YakuDetector {

    /** Anzahl verschiedener Karten fuer die Grund- und Season-Grund-Yaku. */
    private static final int DISTINCT_GROUP_SIZE = 4;

    private static final int WILD_COURT_BEASTS = 3;
    private static final int WILD_COURT_RIBBONS = 2;
    private static final int MONOCHROME_CARDS = 5;

    /** Reihenfolge fuer "eine Karte je Rang". */
    private static final List<Rank> RANK_ORDER = List.of(Rank.PETAL, Rank.RIBBON, Rank.BEAST, Rank.HIKARI);

    private YakuDetector() {}

    public static Optional<YakuResult> findBestYaku(List<Card> cards) {
        List<YakuResult> all = detectAll(cards);
        if (all.isEmpty()) {
            return Optional.empty();
        }

        // Das zuerst erkannte Yaku gewinnt bei identischem EffectiveScore.
        YakuResult best = all.get(0);
        for (int i = 1; i < all.size(); i++) {
            YakuResult current = all.get(i);
            if (current.getEffectiveScore() > best.getEffectiveScore()) {
                best = current;
            }
        }
        return Optional.of(best);
    }

    public static List<YakuResult> detectAll(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        HandContext ctx = new HandContext(cards);
        List<YakuResult> results = new ArrayList<>();

        int hikari = ctx.getRankCount(Rank.HIKARI);
        int beasts = ctx.getRankCount(Rank.BEAST);
        int ribbons = ctx.getRankCount(Rank.RIBBON);
        int petals = ctx.getRankCount(Rank.PETAL);

        // --- 1. IMPERIAL COURT (4 Hikari) ---
        if (hikari >= DISTINCT_GROUP_SIZE) {
            results.add(YakuResult.of(YakuType.IMPERIAL_COURT,
                selectDistinctBest(ctx.getCardsByRank(Rank.HIKARI), DISTINCT_GROUP_SIZE)));
        }

        // --- 2. DUSK AND DAWN (Glazing Sun + Red Moon) ---
        List<Card> duskAndDawn = findCardsByIds(ctx, CardID.SUMMER_HIKARI_GLAZING_SUN, CardID.AUTUMN_HIKARI_RED_MOON);
        if (!duskAndDawn.isEmpty()) {
            results.add(YakuResult.of(YakuType.DUSK_AND_DAWN, duskAndDawn));
        }

        // --- 3. TRUE SEASON (Petal + Ribbon + Beast + Hikari in einer Jahreszeit) ---
        boolean hasTrueSeason = false;
        for (Season season : Season.values()) {
            List<Card> trueSeason = selectOnePerRank(ctx.getCardsBySeason(season));
            if (trueSeason.size() == DISTINCT_GROUP_SIZE) {
                results.add(YakuResult.of(YakuType.TRUE_SEASON, trueSeason));
                hasTrueSeason = true;
                break; // Eine Jahreszeit reicht fuer die Hand
            }
        }

        // --- 4. HARMONY (alle vier Raenge, Jahreszeit egal) ---
        if (!hasTrueSeason && petals >= 1 && ribbons >= 1 && beasts >= 1 && hikari >= 1) {
            List<Card> harmony = selectOnePerRank(ctx.getAllCards());
            if (harmony.size() == DISTINCT_GROUP_SIZE) {
                results.add(YakuResult.of(YakuType.HARMONY, harmony));
            }
        }

        // --- 5. WILD COURT (3 Beasts + 2 Ribbons) ---
        if (beasts >= WILD_COURT_BEASTS && ribbons >= WILD_COURT_RIBBONS) {
            List<Card> wildCourt = new ArrayList<>(WILD_COURT_BEASTS + WILD_COURT_RIBBONS);
            wildCourt.addAll(selectDistinctBest(ctx.getCardsByRank(Rank.BEAST), WILD_COURT_BEASTS));
            wildCourt.addAll(selectDistinctBest(ctx.getCardsByRank(Rank.RIBBON), WILD_COURT_RIBBONS));
            if (wildCourt.size() == WILD_COURT_BEASTS + WILD_COURT_RIBBONS) {
                results.add(YakuResult.of(YakuType.WILD_COURT, wildCourt));
            }
        }

        // --- 6. MONOCHROME (5 Karten derselben Jahreszeit) ---
        for (Season season : Season.values()) {
            if (ctx.getSeasonCount(season) >= MONOCHROME_CARDS) {
                results.add(YakuResult.of(YakuType.MONOCHROME,
                    selectDistinctBest(ctx.getCardsBySeason(season), MONOCHROME_CARDS)));
            }
        }

        // --- 7. Grund-Yaku: vier verschiedene Karten eines Rangs ---
        addDistinctRankYaku(results, ctx, Rank.PETAL, YakuType.FOUR_PETALS);
        addDistinctRankYaku(results, ctx, Rank.RIBBON, YakuType.FOUR_RIBBONS);
        addDistinctRankYaku(results, ctx, Rank.BEAST, YakuType.FOUR_BEASTS);

        // --- 8. FOUR_OF_ONE_SEASON (vier verschiedene Karten einer Jahreszeit) ---
        YakuResult gathering = findBestSeasonGathering(ctx);
        if (gathering != null) {
            results.add(gathering);
        }

        // --- 9. Beast-Duos (zentrale, datengetriebene Definition) ---
        for (BeastCombinations.BeastCombination combination : BeastCombinations.all()) {
            List<Card> pair = findBeastPair(ctx, combination.first(), combination.second());
            if (pair != null) {
                results.add(YakuResult.of(combination.yakuType(), pair));
            }
        }

        // --- 10. LONE SPIRIT (Fallback / High Card) ---
        List<Card> loneSpirit = selectDistinctBest(ctx.getAllCards(), 1);
        if (!loneSpirit.isEmpty()) {
            results.add(YakuResult.of(YakuType.LONE_SPIRIT, loneSpirit));
        }

        return results;
    }

    // ---------------------------------------------------------------------
    // Detektions-Helfer (alle deterministisch, keine Zufallsentscheidung)
    // ---------------------------------------------------------------------

    private static void addDistinctRankYaku(List<YakuResult> results, HandContext ctx, Rank rank, YakuType type) {
        List<Card> candidates = ctx.getCardsByRank(rank);
        if (countDistinctIds(candidates) < DISTINCT_GROUP_SIZE) {
            return;
        }

        List<Card> matched = selectDistinctBest(candidates, DISTINCT_GROUP_SIZE);
        if (matched.size() == DISTINCT_GROUP_SIZE) {
            results.add(YakuResult.of(type, matched));
        }
    }

    /**
     * Sucht das beste "vier verschiedene Karten einer Jahreszeit"-Yaku.
     * Bei mehreren moeglichen Jahreszeiten gewinnt der hoechste Kartenbasiswert,
     * bei Gleichstand die fruehere Season (Enum-Reihenfolge).
     */
    private static YakuResult findBestSeasonGathering(HandContext ctx) {
        YakuResult best = null;
        double bestBaseSum = -1.0;

        for (Season season : Season.values()) {
            List<Card> candidates = ctx.getCardsBySeason(season);
            if (countDistinctIds(candidates) < DISTINCT_GROUP_SIZE) {
                continue;
            }

            List<Card> matched = selectDistinctBest(candidates, DISTINCT_GROUP_SIZE);
            if (matched.size() < DISTINCT_GROUP_SIZE) {
                continue;
            }

            double baseSum = sumBaseValue(matched);
            if (baseSum > bestBaseSum) {
                bestBaseSum = baseSum;
                best = YakuResult.ofSeason(YakuType.FOUR_OF_ONE_SEASON, season, matched);
            }
        }
        return best;
    }

    /** Beide Karten muessen vorhanden sein; Ergebnis sind genau diese beiden Karten. */
    private static List<Card> findCardsByIds(HandContext ctx, CardID first, CardID second) {
        Card firstCard = findCard(ctx, first);
        Card secondCard = findCard(ctx, second);
        if (firstCard == null || secondCard == null) {
            return List.of();
        }
        return List.of(firstCard, secondCard);
    }

    /**
     * Beast-Duo: beide Karten muessen ausgespielt sein UND den Rang BEAST besitzen.
     * Doppelte Exemplare derselben CardID ersetzen die zweite Karte nicht.
     */
    private static List<Card> findBeastPair(HandContext ctx, CardID first, CardID second) {
        Card firstCard = findBeastCard(ctx, first);
        Card secondCard = findBeastCard(ctx, second);
        if (firstCard == null || secondCard == null) {
            return null;
        }
        return List.of(firstCard, secondCard);
    }

    private static Card findCard(HandContext ctx, CardID id) {
        for (Card card : ctx.getAllCards()) {
            if (card.id() == id) {
                return card;
            }
        }
        return null;
    }

    private static Card findBeastCard(HandContext ctx, CardID id) {
        for (Card card : ctx.getCardsByRank(Rank.BEAST)) {
            if (card.id() == id) {
                return card;
            }
        }
        return null;
    }

    /** Waehlt genau eine Karte je Rang; leer, wenn ein Rang fehlt oder keine Karte frei ist. */
    private static List<Card> selectOnePerRank(List<Card> cards) {
        List<Card> selected = new ArrayList<>(RANK_ORDER.size());
        Set<CardID> usedIds = EnumSet.noneOf(CardID.class);

        for (Rank rank : RANK_ORDER) {
            List<Card> candidates = new ArrayList<>();
            for (Card card : cards) {
                if (card.rank() == rank && !usedIds.contains(card.id())) {
                    candidates.add(card);
                }
            }

            List<Card> bestOfRank = selectDistinctBest(candidates, 1);
            if (bestOfRank.isEmpty()) {
                return List.of();
            }

            Card chosen = bestOfRank.get(0);
            selected.add(chosen);
            usedIds.add(chosen.id());
        }
        return selected;
    }

    /**
     * Waehlt deterministisch bis zu {@code count} Karten mit unterschiedlicher CardID:
     * zuerst der hoechste Kartenbasiswert, bei Gleichstand die kleinste
     * CardID-Reihenfolge (Enum-Ordinal), danach die stabile Season-Reihenfolge.
     * Karten mit doppelter CardID zaehlen nur einmal.
     */
    private static List<Card> selectDistinctBest(List<Card> candidates, int count) {
        List<Card> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
            .comparingDouble((Card card) -> card.rank().getBaseValue()).reversed()
            .thenComparingInt(card -> card.id().ordinal())
            .thenComparingInt(card -> card.season().ordinal()));

        List<Card> selected = new ArrayList<>(Math.min(count, sorted.size()));
        Set<CardID> usedIds = EnumSet.noneOf(CardID.class);

        for (Card card : sorted) {
            if (selected.size() >= count) {
                break;
            }
            if (usedIds.add(card.id())) {
                selected.add(card);
            }
        }
        return selected;
    }

    private static int countDistinctIds(List<Card> cards) {
        Set<CardID> ids = EnumSet.noneOf(CardID.class);
        for (Card card : cards) {
            ids.add(card.id());
        }
        return ids.size();
    }

    private static double sumBaseValue(List<Card> cards) {
        double sum = 0.0;
        for (Card card : cards) {
            sum += card.rank().getBaseValue();
        }
        return sum;
    }
}
