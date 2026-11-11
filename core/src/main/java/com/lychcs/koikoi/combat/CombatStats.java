package com.lychcs.koikoi.combat;

/**
 * Base combat values of the overworld player and its companions.
 *
 * <p>The values are the neutral reference; {@link OmamoriPassives} produces a
 * modified copy from the active amulet loadout.</p>
 *
 * @param manaRegenPerSecond   mana restored per second
 * @param sealRadius           final radius of a ground seal in world pixels
 * @param dashCooldown         seconds between two dashes
 * @param companionFireRate    shots per second of a companion (multiplier base)
 */
public record CombatStats(float manaRegenPerSecond, float sealRadius,
                          float dashCooldown, float companionFireRate) {

    /** Neutral loadout without any Omamori bonus. */
    public static final CombatStats BASE = new CombatStats(6f, 110f, 0.9f, 1f);

    public CombatStats {
        if (manaRegenPerSecond < 0f) {
            throw new IllegalArgumentException("manaRegenPerSecond must not be negative");
        }
        if (sealRadius <= 0f) {
            throw new IllegalArgumentException("sealRadius must be > 0");
        }
        if (dashCooldown <= 0f) {
            throw new IllegalArgumentException("dashCooldown must be > 0");
        }
        if (companionFireRate <= 0f) {
            throw new IllegalArgumentException("companionFireRate must be > 0");
        }
    }
}
