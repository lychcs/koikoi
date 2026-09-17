package com.lychcs.koikoi.model.hanko;

public enum HankoEffect {
    NONE,
    WHITE_SEAL,       // +30 Chips
    BLACK_SEAL,       // +4 Mult
    GOLDEN_SEAL,      // Chance auf Mon
    VOID_SEAL,        // Chance auf Void Dust
    STONE_SEAL,       // Nicht discardbar, +50 Chips
    POLYCHROME_SEAL,  // Zaehlt als alle Jahreszeiten
    BLOOD_SEAL        // +50 Chips und +10 Mult (BloodSeal.CHIP_BONUS / MULT_BONUS)
}
