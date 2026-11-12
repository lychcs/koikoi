package com.lychcs.koikoi.run;

/** Unique overworld payload granted when a Yaku is completed. */
public enum YakuEffectKind {
    NONE,
    /** Pulsating ASCII rune field with tick damage and a dash reset. */
    KARMA_FIELD,
    /** Temporary on-hit healing as a percentage of card damage. */
    LIFESTEAL,
    /** Mana refund plus an instant 3P-card cooldown reset. */
    RESOURCE_REFUND,
    /** AoE slow/status spread with petal particles. */
    PETAL_SPREAD
}