package com.lychcs.koikoi.model.hanko;

public enum HankoEffect {
    NONE,           // Standard, kein Stempel
    WHITE_SEAL,     // +Points
    BLACK_SEAL,      // +Mult
    GOLDEN_SEAL,  // Chance of 1-4 for +3 Mon, 1-20 for +10 Mon
    STONE_SEAL, // Returns card back to hand once after being played
    POLYCHROME_SEAL,     // Zählt als alle Jahreszeiten
    YAMI_SEAL       // Hikari to Yami + Zerstörung
}
