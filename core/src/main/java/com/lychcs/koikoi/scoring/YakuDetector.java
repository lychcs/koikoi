package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.Season;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class YakuDetector {

    private YakuDetector() {}

    /**
     * Ermittelt die stärkste Hand ohne Stream- und Comparator-Overhead.
     */
    public static Optional<YakuResult> findBestYaku(List<Card> cards) {
        List<YakuResult> all = detectAll(cards);
        if (all.isEmpty()) {
            return Optional.empty();
        }

        YakuResult best = all.get(0);
        for (int i = 1; i < all.size(); i++) {
            YakuResult current = all.get(i);
            if (current.getEffectiveScore() > best.getEffectiveScore()) {
                best = current;
            }
        }
        return Optional.of(best);
    }

    /**
     * Führt die Regelauswertung auf den voraggregierten Buckets durch.
     */
    public static List<YakuResult> detectAll(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        HandContext ctx = new HandContext(cards);
        List<YakuResult> results = new ArrayList<>();

        int yami = ctx.getRankCount(Rank.YAMI);
        int hikari = ctx.getRankCount(Rank.HIKARI);
        int beasts = ctx.getRankCount(Rank.BEAST);
        int ribbons = ctx.getRankCount(Rank.RIBBON);
        int petals = ctx.getRankCount(Rank.PETAL);

        // --- 1. HIKARI & YAMI ZWEIG ---
        if (yami >= 4) {
            results.add(YakuResult.of(YakuType.ECLIPSE, ctx.getCardsByRank(Rank.YAMI)));
        } else if (yami >= 2) {
            results.add(YakuResult.of(YakuType.SHADOW_CONVERGENCE, ctx.getCardsByRank(Rank.YAMI)));
        }

        if (hikari >= 4) {
            results.add(YakuResult.of(YakuType.IMPERIAL_COURT, ctx.getCardsByRank(Rank.HIKARI)));
        }

        // Schneller O(1) Set-Lookup + manuelle Allokation ohne Stream-Pipeline
        if (ctx.containsCardId(CardID.SUMMER_HIKARI_GLAZING_SUN)
            && ctx.containsCardId(CardID.AUTUMN_HIKARI_RED_MOON)) {
            List<Card> pair = new ArrayList<>(2);
            for (Card card : ctx.getAllCards()) {
                if (card.id() == CardID.SUMMER_HIKARI_GLAZING_SUN
                    || card.id() == CardID.AUTUMN_HIKARI_RED_MOON) {
                    pair.add(card);
                }
            }
            results.add(YakuResult.of(YakuType.DUSK_AND_DAWN, pair));
        }

        // --- 2. BEASTS & RIBBONS (Hierarchischer Ausschluss) ---
        if (beasts >= 3 && ribbons >= 2) {
            List<Card> wildCourtCards = new ArrayList<>(5);
            wildCourtCards.addAll(ctx.getCardsByRank(Rank.BEAST).subList(0, 3));
            wildCourtCards.addAll(ctx.getCardsByRank(Rank.RIBBON).subList(0, 2));
            results.add(YakuResult.of(YakuType.WILD_COURT, wildCourtCards));
        } else {
            if (beasts >= 4) {
                results.add(YakuResult.of(YakuType.BEAST_STAMPEDE, ctx.getCardsByRank(Rank.BEAST)));
            } else if (beasts == 3) {
                results.add(YakuResult.of(YakuType.BEAST_TRIO, ctx.getCardsByRank(Rank.BEAST)));
            }

            if (ribbons >= 4) {
                results.add(YakuResult.of(YakuType.RIBBON_PARADE, ctx.getCardsByRank(Rank.RIBBON)));
            } else if (ribbons == 3) {
                results.add(YakuResult.of(YakuType.RIBBON_TRIO, ctx.getCardsByRank(Rank.RIBBON)));
            }
        }

        // --- 3. PETALS ---
        if (petals >= 5) {
            results.add(YakuResult.of(YakuType.PETAL_CLUSTER, ctx.getCardsByRank(Rank.PETAL)));
        }

        // --- 4. SEASONS (Flush-Checks & Great Harvest) ---
        boolean allSeasonsPresent = true;
        for (Season s : Season.values()) {
            int seasonCount = ctx.getSeasonCount(s);
            if (seasonCount == 0) {
                allSeasonsPresent = false;
            } else if (seasonCount >= 5) {
                results.add(YakuResult.of(YakuType.FULL_SEASON, ctx.getCardsBySeason(s)));
            } else if (seasonCount >= 3) {
                // Bugfix: Deckt 3 und 4 Karten gleicher Season ab
                results.add(YakuResult.of(YakuType.MONOCHROME, ctx.getCardsBySeason(s)));
            }
        }

        if (allSeasonsPresent) {
            List<Card> straightCards = new ArrayList<>(4);
            for (Season s : Season.values()) {
                straightCards.add(ctx.getCardsBySeason(s).get(0));
            }
            results.add(YakuResult.of(YakuType.GREAT_HARVEST, straightCards));
        }

        return results;
    }
}
