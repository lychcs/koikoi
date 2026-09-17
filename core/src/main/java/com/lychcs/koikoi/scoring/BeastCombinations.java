package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.CardID;

import java.util.List;

/**
 * Zentrale, datengetriebene Definition der Beast-Duo-Yaku.
 *
 * Jede Kombination besteht aus genau zwei verschiedenen Beast-Karten. Nur diese
 * beiden Karten duerfen fuer das jeweilige Yaku werten; die Reihenfolge der
 * ausgespielten Karten spielt keine Rolle.
 */
public final class BeastCombinations {

    /** Eine Beast-Kombination: zwei konkrete Karten-IDs und ihr Yaku. */
    public record BeastCombination(YakuType yakuType, CardID first, CardID second) {}

    private static final List<BeastCombination> COMBINATIONS = List.of(
        new BeastCombination(YakuType.BEAST_PAIR_SPRING, CardID.SPRING_BEAST_KOI, CardID.SPRING_BEAST_CRANE),
        new BeastCombination(YakuType.BEAST_PAIR_SUMMER, CardID.SUMMER_BEAST_MOUNTAIN_MONKEY, CardID.SUMMER_BEAST_SERPENT),
        new BeastCombination(YakuType.BEAST_PAIR_AUTUMN, CardID.AUTUMN_BEAST_BOAR, CardID.AUTUMN_BEAST_DEER),
        new BeastCombination(YakuType.BEAST_PAIR_WINTER, CardID.WINTER_BEAST_ARCTIC_FOX, CardID.WINTER_BEAST_WHITE_OWL)
    );

    private BeastCombinations() {}

    public static List<BeastCombination> all() {
        return COMBINATIONS;
    }
}