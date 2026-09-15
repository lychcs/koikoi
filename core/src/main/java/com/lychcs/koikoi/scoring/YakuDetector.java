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
        if (hikari >= 4) {
            results.add(YakuResult.of(YakuType.IMPERIAL_COURT, ctx.getCardsByRank(Rank.HIKARI)));
        }

        // --- 2. DUSK AND DAWN ---
        if (ctx.containsCardId(CardID.SUMMER_HIKARI_GLAZING_SUN) && ctx.containsCardId(CardID.AUTUMN_HIKARI_RED_MOON)) {
            List<Card> pair = new ArrayList<>();
            for (Card c : ctx.getAllCards()) {
                if (c.id() == CardID.SUMMER_HIKARI_GLAZING_SUN || c.id() == CardID.AUTUMN_HIKARI_RED_MOON) {
                    pair.add(c);
                }
            }
            results.add(YakuResult.of(YakuType.DUSK_AND_DAWN, pair));
        }

        // --- 3. TRUE SEASON (4 Ränge aus EXAKT einer Jahreszeit) ---
        boolean hasTrueSeason = false;
        for (Season s : Season.values()) {
            boolean sP = false, sR = false, sB = false, sH = false;
            for (Card c : ctx.getCardsBySeason(s)) {
                if (c.rank() == Rank.PETAL) sP = true;
                if (c.rank() == Rank.RIBBON) sR = true;
                if (c.rank() == Rank.BEAST) sB = true;
                if (c.rank() == Rank.HIKARI) sH = true;
            }

            if (sP && sR && sB && sH) {
                results.add(YakuResult.of(YakuType.TRUE_SEASON, ctx.getCardsBySeason(s)));
                hasTrueSeason = true;
                break; // Eins reicht für die Hand
            }
        }

        // --- 4. HARMONY (4 Ränge, Jahreszeit egal) ---
        // Gilt nur, wenn es nicht schon eine True Season ist!
        if (!hasTrueSeason && petals >= 1 && ribbons >= 1 && beasts >= 1 && hikari >= 1) {
            results.add(YakuResult.of(YakuType.HARMONY, ctx.getAllCards()));
        }

        // --- 5. WILD COURT (3 Beasts + 2 Ribbons) ---
        if (beasts >= 3 && ribbons >= 2) {
            List<Card> wildCourtCards = new ArrayList<>(5);
            wildCourtCards.addAll(ctx.getCardsByRank(Rank.BEAST).subList(0, 3));
            wildCourtCards.addAll(ctx.getCardsByRank(Rank.RIBBON).subList(0, 2));
            results.add(YakuResult.of(YakuType.WILD_COURT, wildCourtCards));
        }

        // --- 6. MONOCHROME (5 Karten gleicher Jahreszeit) ---
        for (Season s : Season.values()) {
            if (ctx.getSeasonCount(s) >= 5) {
                results.add(YakuResult.of(YakuType.MONOCHROME, ctx.getCardsBySeason(s)));
            }
        }

        // --- 7. 5er-RÄNGE ---
        if (petals >= 5) results.add(YakuResult.of(YakuType.PETAL_STORM, ctx.getCardsByRank(Rank.PETAL)));
        if (ribbons >= 5) results.add(YakuResult.of(YakuType.RIBBON_PARADE, ctx.getCardsByRank(Rank.RIBBON)));
        if (beasts >= 5) results.add(YakuResult.of(YakuType.BEAST_STAMPEDE, ctx.getCardsByRank(Rank.BEAST)));

        // --- 8. LONE SPIRIT (Fallback / High Card) ---
        // Dies wird IMMER hinzugefügt. Da es den niedrigsten Score hat,
        // gewinnt es nur, wenn absolut kein anderes Yaku in der Liste ist.
        Card bestCard = cards.get(0);
        for (Card c : cards) {
            // Wir vergleichen den Basis-Wert des Rangs (Hikari > Beast > Ribbon > Petal)
            if (c.rank().getBaseValue() > bestCard.rank().getBaseValue()) {
                bestCard = c;
            }
        }
        // Nur diese eine beste Karte wird gewertet!
        results.add(YakuResult.of(YakuType.LONE_SPIRIT, List.of(bestCard)));

        return results;
    }
}
