package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.model.hanko.BlackSeal;
import com.lychcs.koikoi.model.hanko.BloodSeal;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.hanko.StoneSeal;
import com.lychcs.koikoi.model.hanko.WhiteSeal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erkennt alle Yaku einer ausgespielten Hand und transportiert mit
 * {@code matchedCards} genau die Karten, mit denen ein Yaku erfuellt wurde.
 * Nur diese Karten werden gewertet.
 *
 * <p>{@link #detectAll(List)} ist eine reine, deterministische
 * Kandidatenerkennung. Die Auswahl des besten Yakus erfolgt nicht mehr hier,
 * sondern in {@link YakuSelector} anhand der realen Auszahlung ueber den
 * gemeinsamen Score-Kern. Die Detektionsreihenfolge dient dort als Tie-Break,
 * wenn zwei Yaku denselben Endscore erreichen (keine Zufallsentscheidung).</p>
 */
public final class YakuDetector {

    /** Anzahl Karten fuer die Vier-Karten-Yaku (Rang und Season). */
    private static final int FOUR_CARD_GROUP_SIZE = 4;

    private static final int WILD_COURT_BEASTS = 3;
    private static final int WILD_COURT_RIBBONS = 2;
    private static final int MONOCHROME_CARDS = 5;

    /** Reihenfolge fuer "eine Karte je Rang". */
    private static final List<Rank> RANK_ORDER = List.of(Rank.PETAL, Rank.RIBBON, Rank.BEAST, Rank.HIKARI);

    /**
     * Auswahl-Reihenfolge innerhalb eines Yaku-Platzes. Sie wird pro Aufruf aus der
     * uebergebenen (sichtbaren Links-nach-rechts-)Reihenfolge gebildet:
     * <ol>
     *   <li>tatsaechliche Scorestaerke: Rang-Basiswert, danach Hanko-Chips-Bonus,
     *       danach Hanko-Mult-Bonus,</li>
     *   <li><b>sichtbarer Index</b> der ausgespielten Karten (autoritativ bei
     *       Gleichstand: die weiter links liegende Karte gewinnt),</li>
     *   <li>erst danach ein technischer Fallback (CardID, Name, Season), der bei
     *       Karten einer Hand praktisch unerreichbar ist.</li>
     * </ol>
     * "Gleich stark" heisst: gleicher Rang-Basiswert UND gleiche Hanko-Scorewirkung.
     * Kein Zufall, keine Abhaengigkeit von #1/#2/#3-Namen.
     */
    private static Comparator<Card> priorityComparator(Map<Card, Integer> visualIndex) {
        return (first, second) -> {
            int byRank = Double.compare(second.rank().getBaseValue(), first.rank().getBaseValue());
            if (byRank != 0) return byRank;

            int byChips = Double.compare(hankoChips(second.effect()), hankoChips(first.effect()));
            if (byChips != 0) return byChips;

            int byMult = Double.compare(hankoMult(second.effect()), hankoMult(first.effect()));
            if (byMult != 0) return byMult;

            int byPosition = Integer.compare(positionOf(visualIndex, first), positionOf(visualIndex, second));
            if (byPosition != 0) return byPosition;

            int byId = Integer.compare(first.id().ordinal(), second.id().ordinal());
            if (byId != 0) return byId;

            int byName = first.name().compareTo(second.name());
            if (byName != 0) return byName;

            return Integer.compare(first.season().ordinal(), second.season().ordinal());
        };
    }

    /** Sichtbarer Index der Karte; unbekannte Karten stehen ganz hinten. */
    private static int positionOf(Map<Card, Integer> visualIndex, Card card) {
        Integer index = visualIndex.get(card);
        return index == null ? Integer.MAX_VALUE : index;
    }

    /** Chips-Bonus der Hanko-Scorewirkung (zentrale Hanko-Konstanten). */
    private static double hankoChips(HankoEffect effect) {
        return switch (effect) {
            case WHITE_SEAL -> WhiteSeal.CHIP_BONUS;
            case STONE_SEAL -> StoneSeal.LEVEL_1_CHIP_BONUS;
            case BLOOD_SEAL -> BloodSeal.CHIP_BONUS;
            default -> 0.0;
        };
    }

    /** Additiver Mult-Bonus der Hanko-Scorewirkung (zentrale Hanko-Konstanten). */
    private static double hankoMult(HankoEffect effect) {
        return switch (effect) {
            case BLACK_SEAL -> BlackSeal.MULT_BONUS;
            case BLOOD_SEAL -> BloodSeal.MULT_BONUS;
            default -> 0.0;
        };
    }

    private YakuDetector() {}

    public static List<YakuResult> detectAll(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        // Autoritative Auswahlreihenfolge: die uebergebene (sichtbare) Reihenfolge der
        // ausgespielten Karten. Identitaetsbasiert, damit auch inhaltsgleiche Kopien
        // ueber ihre Position unterschieden werden.
        Map<Card, Integer> visualIndex = new IdentityHashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            visualIndex.putIfAbsent(cards.get(i), i);
        }
        Comparator<Card> priority = priorityComparator(visualIndex);

        HandContext ctx = new HandContext(cards);
        List<YakuResult> results = new ArrayList<>();

        int hikari = ctx.getRankCount(Rank.HIKARI);
        int beasts = ctx.getRankCount(Rank.BEAST);
        int ribbons = ctx.getRankCount(Rank.RIBBON);
        int petals = ctx.getRankCount(Rank.PETAL);

        // --- 1. IMPERIAL COURT (4 Hikari-Karten, exakt vier in matchedCards) ---
        if (hikari >= FOUR_CARD_GROUP_SIZE) {
            List<Card> imperialCourt = selectPhysicalBest(ctx.getCardsByRank(Rank.HIKARI), FOUR_CARD_GROUP_SIZE, priority);
            if (imperialCourt.size() == FOUR_CARD_GROUP_SIZE) {
                results.add(YakuResult.of(YakuType.IMPERIAL_COURT, imperialCourt));
            }
        }

        // --- 2. DUSK AND DAWN (Glazing Sun + Red Moon, beide weiterhin HIKARI) ---
        List<Card> duskAndDawn = findCardsByIdAndRank(
            ctx, CardID.SUMMER_HIKARI_GLAZING_SUN, CardID.AUTUMN_HIKARI_RED_MOON, Rank.HIKARI, priority);
        if (!duskAndDawn.isEmpty()) {
            results.add(YakuResult.of(YakuType.DUSK_AND_DAWN, duskAndDawn));
        }

        // --- 3. TRUE SEASON (Petal + Ribbon + Beast + Hikari in einer Jahreszeit) ---
        boolean hasTrueSeason = false;
        for (Season season : Season.values()) {
            List<Card> trueSeason = selectOnePerRank(ctx.getVirtualCardsBySeason(season), priority);
            if (trueSeason.size() == FOUR_CARD_GROUP_SIZE) {
                results.add(YakuResult.of(YakuType.TRUE_SEASON, trueSeason));
                hasTrueSeason = true;
                break; // Eine Jahreszeit reicht fuer die Hand
            }
        }

        // --- 4. HARMONY (alle vier Raenge, Jahreszeit egal) ---
        if (!hasTrueSeason && petals >= 1 && ribbons >= 1 && beasts >= 1 && hikari >= 1) {
            List<Card> harmony = selectOnePerRank(ctx.getAllCards(), priority);
            if (harmony.size() == FOUR_CARD_GROUP_SIZE) {
                results.add(YakuResult.of(YakuType.HARMONY, harmony));
            }
        }

        // --- 5. WILD COURT (3 physische Beasts + 2 physische Ribbons, Duplikate erlaubt) ---
        if (beasts >= WILD_COURT_BEASTS && ribbons >= WILD_COURT_RIBBONS) {
            List<Card> wildCourt = new ArrayList<>(WILD_COURT_BEASTS + WILD_COURT_RIBBONS);
            wildCourt.addAll(selectPhysicalBest(ctx.getCardsByRank(Rank.BEAST), WILD_COURT_BEASTS, priority));
            wildCourt.addAll(selectPhysicalBest(ctx.getCardsByRank(Rank.RIBBON), WILD_COURT_RIBBONS, priority));
            if (wildCourt.size() == WILD_COURT_BEASTS + WILD_COURT_RIBBONS) {
                results.add(YakuResult.of(YakuType.WILD_COURT, wildCourt));
            }
        }

        // --- 6. MONOCHROME (5 physische Karten derselben Jahreszeit, Duplikate erlaubt) ---
        for (Season season : Season.values()) {
            if (ctx.getVirtualSeasonCount(season) >= MONOCHROME_CARDS) {
                List<Card> monochrome = selectPhysicalBest(ctx.getVirtualCardsBySeason(season), MONOCHROME_CARDS, priority);
                // Nur ein vollstaendiges Set aus genau fuenf Karten ist ein Yaku.
                if (monochrome.size() == MONOCHROME_CARDS) {
                    results.add(YakuResult.of(YakuType.MONOCHROME, monochrome));
                }
            }
        }

        // --- 7. Grund-Yaku: vier verschiedene Karten eines Rangs ---
        addDistinctRankYaku(results, ctx, Rank.PETAL, YakuType.FOUR_PETALS, priority);
        addDistinctRankYaku(results, ctx, Rank.RIBBON, YakuType.FOUR_RIBBONS, priority);
        addDistinctRankYaku(results, ctx, Rank.BEAST, YakuType.FOUR_BEASTS, priority);

        // --- 8. FOUR_OF_ONE_SEASON (vier physische Karten einer Jahreszeit, Duplikate erlaubt) ---
        YakuResult gathering = findBestSeasonGathering(ctx, priority);
        if (gathering != null) {
            results.add(gathering);
        }

        // --- 9. Beast-Duos (zentrale, datengetriebene Definition) ---
        for (BeastCombinations.BeastCombination combination : BeastCombinations.all()) {
            List<Card> pair = findBeastPair(ctx, combination.first(), combination.second(), priority);
            if (pair != null) {
                results.add(YakuResult.of(combination.yakuType(), pair));
            }
        }

        // --- 10. LONE SPIRIT (Fallback / High Card) ---
        List<Card> loneSpirit = selectDistinctBest(ctx.getAllCards(), 1, priority);
        if (!loneSpirit.isEmpty()) {
            results.add(YakuResult.of(YakuType.LONE_SPIRIT, loneSpirit));
        }

        return results;
    }

    // ---------------------------------------------------------------------
    // Detektions-Helfer (alle deterministisch, keine Zufallsentscheidung)
    // ---------------------------------------------------------------------

    private static void addDistinctRankYaku(List<YakuResult> results, HandContext ctx, Rank rank,
                                            YakuType type, Comparator<Card> priority) {
        List<Card> candidates = ctx.getCardsByRank(rank);
        if (countDistinctIds(candidates) < FOUR_CARD_GROUP_SIZE) {
            return;
        }

        List<Card> matched = selectDistinctBest(candidates, FOUR_CARD_GROUP_SIZE, priority);
        if (matched.size() == FOUR_CARD_GROUP_SIZE) {
            results.add(YakuResult.of(type, matched));
        }
    }

    /**
     * Sucht das beste "vier physische Karten einer Jahreszeit"-Yaku. Duplikate
     * derselben CardID sind erlaubt. Bei mehreren moeglichen Jahreszeiten gewinnt
     * der hoechste Kartenbasiswert, bei Gleichstand die fruehere Season
     * (Enum-Reihenfolge).
     */
    private static YakuResult findBestSeasonGathering(HandContext ctx, Comparator<Card> priority) {
        YakuResult best = null;
        double bestBaseSum = -1.0;

        for (Season season : Season.values()) {
            List<Card> candidates = ctx.getVirtualCardsBySeason(season);
            if (candidates.size() < FOUR_CARD_GROUP_SIZE) {
                continue;
            }

            List<Card> matched = selectPhysicalBest(candidates, FOUR_CARD_GROUP_SIZE, priority);
            if (matched.size() != FOUR_CARD_GROUP_SIZE) {
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

    /**
     * Beide Karten muessen vorhanden sein UND den geforderten Rang besitzen.
     * Damit loest Dusk &amp; Dawn nicht mehr aus, wenn ein Effekt (z. B. Oni)
     * eine der Karten in einen anderen Rang umgewandelt hat.
     */
    private static List<Card> findCardsByIdAndRank(HandContext ctx, CardID first, CardID second,
                                                   Rank requiredRank, Comparator<Card> priority) {
        Card firstCard = findCardOfRank(ctx, first, requiredRank, priority);
        Card secondCard = findCardOfRank(ctx, second, requiredRank, priority);
        if (firstCard == null || secondCard == null) {
            return List.of();
        }
        return List.of(firstCard, secondCard);
    }

    /**
     * Beast-Duo: beide Karten muessen ausgespielt sein UND den Rang BEAST besitzen.
     * Doppelte Exemplare derselben CardID ersetzen die zweite Karte nicht.
     */
    private static List<Card> findBeastPair(HandContext ctx, CardID first, CardID second,
                                            Comparator<Card> priority) {
        Card firstCard = findCardOfRank(ctx, first, Rank.BEAST, priority);
        Card secondCard = findCardOfRank(ctx, second, Rank.BEAST, priority);
        if (firstCard == null || secondCard == null) {
            return null;
        }
        return List.of(firstCard, secondCard);
    }

    /**
     * Beste Karte mit dieser CardID, aber ausschliesslich innerhalb des
     * geforderten Rangs. Mehrere physische Kopien werden deterministisch
     * ueber die Auswahlprioritaet (Staerke, danach sichtbare Position) aufgeloest.
     */
    private static Card findCardOfRank(HandContext ctx, CardID id, Rank requiredRank, Comparator<Card> priority) {
        List<Card> matches = new ArrayList<>(1);
        for (Card card : ctx.getCardsByRank(requiredRank)) {
            if (card.id() == id) {
                matches.add(card);
            }
        }

        List<Card> best = selectPhysicalBest(matches, 1, priority);
        return best.isEmpty() ? null : best.get(0);
    }

    /** Waehlt genau eine Karte je Rang; leer, wenn ein Rang fehlt oder keine Karte frei ist. */
    private static List<Card> selectOnePerRank(List<Card> cards, Comparator<Card> priority) {
        List<Card> selected = new ArrayList<>(RANK_ORDER.size());
        Set<CardID> usedIds = EnumSet.noneOf(CardID.class);

        for (Rank rank : RANK_ORDER) {
            List<Card> candidates = new ArrayList<>();
            for (Card card : cards) {
                if (card.rank() == rank && !usedIds.contains(card.id())) {
                    candidates.add(card);
                }
            }

            List<Card> bestOfRank = selectDistinctBest(candidates, 1, priority);
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
     * Gemeinsame Auswahllogik fuer alle Yaku: sortiert die Kandidaten nach der
     * uebergebenen Prioritaet (Staerke, danach sichtbare Position) und entnimmt bis
     * zu {@code count} Karten. Mit {@code distinctIds} zaehlt jede CardID nur einmal
     * (fuer Yaku, die verschiedene CardIDs verlangen).
     */
    private static List<Card> selectByPriority(List<Card> candidates, int count, boolean distinctIds,
                                               Comparator<Card> priority) {
        if (count <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        List<Card> sorted = new ArrayList<>(candidates);
        sorted.sort(priority);

        List<Card> selected = new ArrayList<>(Math.min(count, sorted.size()));
        Set<CardID> usedIds = EnumSet.noneOf(CardID.class);

        for (Card card : sorted) {
            if (selected.size() >= count) {
                break;
            }
            if (distinctIds && !usedIds.add(card.id())) {
                continue;
            }
            selected.add(card);
        }
        return selected;
    }

    /**
     * Waehlt bis zu {@code count} physische Karten; Duplikate derselben CardID
     * sind erlaubt (vier-of-one-season, Monochrome, Wild Court, Imperial Court).
     */
    private static List<Card> selectPhysicalBest(List<Card> candidates, int count, Comparator<Card> priority) {
        return selectByPriority(candidates, count, false, priority);
    }

    /**
     * Waehlt bis zu {@code count} Karten mit unterschiedlicher CardID
     * (Four Petals, Four Ribbons, Four Beasts sowie "eine Karte je Rang").
     */
    private static List<Card> selectDistinctBest(List<Card> candidates, int count, Comparator<Card> priority) {
        return selectByPriority(candidates, count, true, priority);
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
