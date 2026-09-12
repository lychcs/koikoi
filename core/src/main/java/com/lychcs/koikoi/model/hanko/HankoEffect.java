package com.lychcs.koikoi.model.hanko;

public enum HankoEffect {
    NONE,           // Standard, kein Stempel
    WHITE_SEAL,     // +Points
    BLACK_SEAL,      // +Mult
    GOLDEN_SEAL,  // Chance of 1-4 for +3 Mon, 1-20 for +10 Mon
    VOID_SEAL,   // Chance of 1-4 for +5 Void Dust, 1-20 for +10 Void Dust
    STONE_SEAL, // Returns card back to hand once after being played
    POLYCHROME_SEAL,     // Zählt als alle Jahreszeiten
    BLOOD_SEAL       // Chance of 1-4 to destroy card for the season
}
