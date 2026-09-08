package com.lychcs.koikoi.scoring;

public enum YakuType {
    // Tier 1: Low (Score: 20 - 80)
    PETAL_CLUSTER("Petal Cluster", 20, 1),       // 5 Petals (High Card)
    MONOCHROME("Monochrome", 25, 2),             // 3 cards of the same Season (Small Flush)
    RIBBON_TRIO("Ribbon Trio", 40, 2),           // 3 Ribbons (Low Three of a Kind)

    // Tier 2: Mid (Score: 210 - 270)
    BEAST_TRIO("Beast Trio", 70, 3),             // 3 Beasts (Mid Three of a Kind)
    FULL_SEASON("Full Season", 80, 3),           // 5 cards of the same Season (Flush)
    RIBBON_PARADE("Ribbon Parade", 90, 3),       // 4 Ribbons (Low Four of a Kind)

    // Tier 3: Strong (Score: 400 - 800)
    GREAT_HARVEST("Great Harvest", 100, 4),      // 1 card from each Season (Straight)
    BEAST_STAMPEDE("Beast Stampede", 120, 4),   // 4 Beasts (Mid Four of a Kind)
    DUSK_AND_DAWN("Dusk & Dawn", 140, 5),          // Glazing Sun + Red Moon
    WILD_COURT("Wild Court", 160, 5),            // 3 Beasts + 2 Ribbons (Full House)

    // Tier 4: Boss / High Stakes (Score: 1,080 - 3,150)
    SHADOW_CONVERGENCE("Shadow Convergence", 180, 6), // 2 Yami cards (Rare High Pair)
    IMPERIAL_COURT("Imperial Court", 300, 8),         // All 4 Hikari cards (Royal Flush)
    ECLIPSE("Eclipse", 350, 9);                       // All 4 Yami cards (Jackpot)

    private final String displayName;
    private final int baseChips;
    private final int baseMult;

    YakuType(String displayName, int baseChips, int baseMult) {
        this.displayName = displayName;
        this.baseChips = baseChips;
        this.baseMult = baseMult;
    }

    public int getEffectiveScore() {
        return baseChips * baseMult;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseChips() {
        return baseChips;
    }

    public int getBaseMult() {
        return baseMult;
    }
}
