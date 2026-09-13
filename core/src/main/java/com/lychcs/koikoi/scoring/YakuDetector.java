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
        // --- 4. SEASONS (Flush-Checks & Great Harvest) ---
        boolean allSeasonsPresent = true;
        for (Season s : Season.values()) {
            int seasonCount = ctx.getSeasonCount(s);
            if (seasonCount == 0) {
                allSeasonsPresent = false;
            } else if (seasonCount >= 5) {
                results.add(YakuResult.of(YakuType.FULL_SEASON, ctx.getCardsBySeason(s)));
            } else if (seasonCount >= 3) {
                results.add(YakuResult.of(YakuType.MONOCHROME, ctx.getCardsBySeason(s)));
            }
        }

        // Great Harvest: 1 Karte aus jeder Jahreszeit
        // FIX: Auch wenn "allSeasonsPresent" durch Polychrome auf true springt,
        // müssen physisch mindestens 4 getrennte Karten vorliegen!
        if (allSeasonsPresent && ctx.getTotalCardCount() >= 4) {
            List<Card> straightCards = new ArrayList<>(4);
            java.util.Set<Card> usedCards = new java.util.HashSet<>();

            for (Season s : Season.values()) {
                for (Card c : ctx.getCardsBySeason(s)) {
                    // Prüfen, ob diese konkrete Karte (z.B. Polychrome) schon
                    // für eine ANDERE Jahreszeit in diesem Yaku hergehalten hat
                    if (!usedCards.contains(c)) {
                        straightCards.add(c);
                        usedCards.add(c);
                        break; // Nächste Jahreszeit suchen
                    }
                }
            }

            // Nur wenn wir wirklich 4 unterschiedliche Karten gefunden haben, die die 4 Slots füllen
            if (straightCards.size() == 4) {
                results.add(YakuResult.of(YakuType.GREAT_HARVEST, straightCards));
            }
        }

        return results;
    }
}
