package com.lychcs.koikoi.scoring;

/**
 * Alle Yaku mit dezimalen Basiswerten. Die Reihenfolge der Konstanten ist die
 * stabile Prioritaet bei identischem EffectiveScore (siehe YakuDetector).
 */
public enum YakuType {

    // Fallback / High Card
    LONE_SPIRIT("Lone Spirit", 10, 1),

    // Vier verschiedene Karten eines Rangs (Grund-Kombinationen)
    FOUR_PETALS("Four Petals", "Scores four different Petal cards (unique card ids).", 30, 2),
    FOUR_RIBBONS("Four Ribbons", "Scores four different Ribbon cards (unique card ids).", 40, 3),
    FOUR_BEASTS("Four Beasts", "Scores four different Beast cards (unique card ids).", 60, 4),

    // Vier verschiedene Karten einer Jahreszeit
    FOUR_OF_ONE_SEASON("Gathering", "Scores four different cards of one season.", 30, 2),

    // Beast-Duos (datengetrieben in BeastCombinations)
    BEAST_PAIR_SPRING("Koi and Crane", "Scores only the two Spring beasts Koi and Crane.", 20, 2),
    BEAST_PAIR_SUMMER("Monkey and Serpent", "Scores only the two Summer beasts Mountain Monkey and Serpent.", 20, 2),
    BEAST_PAIR_AUTUMN("Ino-Shika", "Scores only the two Autumn beasts Boar and Deer. Not the full traditional Inoshikacho.", 20, 2),
    BEAST_PAIR_WINTER("Arctic Fox and Owl", "Scores only the two Winter beasts Arctic Fox and White Owl.", 20, 2),

    // Synergien & Flush
    MONOCHROME("Monochrome", 80, 4),          // 5 Karten der gleichen Jahreszeit
    HARMONY("Harmony", 100, 5),               // Petal + Ribbon + Beast + Hikari (beliebige Jahreszeiten)
    WILD_COURT("Wild Court", 140, 5),         // 3 Beasts + 2 Ribbons

    // High-End Yaku
    TRUE_SEASON("True Season", 200, 6),       // Petal + Ribbon + Beast + Hikari (ALLE aus der gleichen Jahreszeit)
    DUSK_AND_DAWN("Dusk & Dawn", 250, 7),     // Glazing Sun + Red Moon
    IMPERIAL_COURT("Imperial Court", 400, 9); // Alle 4 Hikari

    private final String displayName;
    private final String description;
    private final double baseChips;
    private final double baseMult;

    YakuType(String displayName, double baseChips, double baseMult) {
        this(displayName, null, baseChips, baseMult);
    }

    YakuType(String displayName, String description, double baseChips, double baseMult) {
        this.displayName = displayName;
        this.description = description;
        this.baseChips = baseChips;
        this.baseMult = baseMult;
    }

    public double getEffectiveScore() {
        return baseChips * baseMult;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Beschreibung des Yaku; faellt auf den Anzeigenamen zurueck, wenn keine hinterlegt ist. */
    public String getDescription() {
        return description == null ? displayName : description;
    }

    public double getBaseChips() {
        return baseChips;
    }

    public double getBaseMult() {
        return baseMult;
    }
}
