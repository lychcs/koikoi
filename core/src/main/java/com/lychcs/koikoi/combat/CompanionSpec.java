package com.lychcs.koikoi.combat;

/**
 * Immutable description of one Yokai battle pet (the three initial variants).
 *
 * <p>Overworld entities are ASCII-only; no atlas/PNG region is carried or
 * drawn. The Omamori-modified fire rate is applied by {@link OverworldEcs}.</p>
 */
public record CompanionSpec(String name,
                            int slot,
                            float scale,
                            float damage,
                            float baseFireInterval,
                            float projectileSpeed,
                            float attackRange,
                            float ultimateRadius,
                            float ultimateDamage,
                            float ultimateCooldown) {

    public CompanionSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        if (baseFireInterval <= 0f) {
            throw new IllegalArgumentException("baseFireInterval must be > 0");
        }
    }
}
