package com.lychcs.koikoi.scoring;

public enum YakuType {

    // Fallback / High Card
    LONE_SPIRIT("Lone Spirit", 10, 1),

    // 5er-Ränge (Grund-Kombinationen)
    PETAL_STORM("Petal Storm", 30, 2),        // 5 Petals
    RIBBON_PARADE("Ribbon Parade", 40, 3),    // 5 Ribbons
    BEAST_STAMPEDE("Beast Stampede", 60, 4),  // 5 Beasts

    // Synergien & Flush
    MONOCHROME("Monochrome", 80, 4),          // 5 Karten der gleichen Jahreszeit
    HARMONY("Harmony", 100, 5),               // Petal + Ribbon + Beast + Hikari (beliebige Jahreszeiten)
    WILD_COURT("Wild Court", 140, 5),         // 3 Beasts + 2 Ribbons

    // High-End Yaku
    TRUE_SEASON("True Season", 200, 6),       // Petal + Ribbon + Beast + Hikari (ALLE aus der gleichen Jahreszeit)
    DUSK_AND_DAWN("Dusk & Dawn", 250, 7),     // Glazing Sun + Red Moon
    IMPERIAL_COURT("Imperial Court", 400, 9); // Alle 4 Hikari

    private final String displayName;
    private final long baseChips;
    private final long baseMult;

    YakuType(String displayName, long baseChips, long baseMult) {
        this.displayName = displayName;
        this.baseChips = baseChips;
        this.baseMult = baseMult;
    }

    public long getEffectiveScore() {
        return baseChips * baseMult;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getBaseChips() {
        return baseChips;
    }

    public long getBaseMult() {
        return baseMult;
    }
}
