package com.lychcs.koikoi.combat;

import com.lychcs.koikoi.model.omamori.Omamori;

import java.util.Collection;
import java.util.List;

/**
 * Passive Omamori slot infrastructure for the overworld.
 *
 * <p>Each of the 15 collectable Omamoris contributes one or two passive bonuses
 * to exactly one of the four overworld stats: mana regeneration, seal radius,
 * dash cooldown reduction and companion fire rate. The bonuses of all active
 * slots are summed and clamped, so an extreme loadout can never break the
 * combat loop.</p>
 *
 * <p>This class is pure and independent of both the ECS and the scoring engine,
 * so it can be unit-tested with every Omamori from {@code OmamoriPool}.</p>
 */
public final class OmamoriPassives {

    /** The four overworld stats an Omamori can buff. */
    public enum PassiveType {
        MANA_REGEN,
        SEAL_RADIUS,
        DASH_COOLDOWN,
        COMPANION_FIRE_RATE
    }

    /** One additive percentage bonus of an Omamori (0.10 = +10 %). */
    public record Bonus(PassiveType type, float percent) {
        public Bonus {
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
            if (percent < 0f) {
                throw new IllegalArgumentException("percent must not be negative");
            }
        }
    }

    /** Aggregated multipliers applied to {@link CombatStats}. */
    public record Stats(float manaRegenMultiplier,
                        float sealRadiusMultiplier,
                        float dashCooldownMultiplier,
                        float companionFireRateMultiplier) {

        /** Loadout without any active Omamori. */
        public static final Stats NEUTRAL = new Stats(1f, 1f, 1f, 1f);

        /** Applies the multipliers to the neutral combat values. */
        public CombatStats applyTo(CombatStats base) {
            if (base == null) {
                throw new IllegalArgumentException("base must not be null");
            }
            return new CombatStats(
                base.manaRegenPerSecond() * manaRegenMultiplier,
                base.sealRadius() * sealRadiusMultiplier,
                base.dashCooldown() * dashCooldownMultiplier,
                base.companionFireRate() * companionFireRateMultiplier);
        }
    }

    /** Upper bound of the mana regeneration multiplier. */
    public static final float MAX_MANA_REGEN_MULTIPLIER = 2.5f;

    /** Upper bound of the seal radius multiplier. */
    public static final float MAX_SEAL_RADIUS_MULTIPLIER = 2.0f;

    /** Lower bound of the dash cooldown multiplier (65 % reduction at most). */
    public static final float MIN_DASH_COOLDOWN_MULTIPLIER = 0.35f;

    /** Upper bound of the companion fire rate multiplier. */
    public static final float MAX_FIRE_RATE_MULTIPLIER = 3.0f;

    private OmamoriPassives() {
    }

    /** Passive bonuses of one Omamori (never empty for the 15 collectables). */
    public static List<Bonus> bonusesFor(Omamori omamori) {
        if (omamori == null) {
            return List.of();
        }

        return switch (omamori.getClass().getSimpleName()) {
            case "DarumaDoll" -> List.of(
                bonus(PassiveType.SEAL_RADIUS, 0.10f),
                bonus(PassiveType.MANA_REGEN, 0.05f));
            case "Furin" -> List.of(
                bonus(PassiveType.MANA_REGEN, 0.15f));
            case "HanamiDango" -> List.of(
                bonus(PassiveType.COMPANION_FIRE_RATE, 0.08f));
            case "InoshishiTusk" -> List.of(
                bonus(PassiveType.SEAL_RADIUS, 0.08f));
            case "JohariMirror" -> List.of(
                bonus(PassiveType.SEAL_RADIUS, 0.15f),
                bonus(PassiveType.MANA_REGEN, 0.10f));
            case "KintsugiBowl" -> List.of(
                bonus(PassiveType.DASH_COOLDOWN, 0.10f));
            case "KitsuneMask" -> List.of(
                bonus(PassiveType.COMPANION_FIRE_RATE, 0.12f));
            case "Koinobori" -> List.of(
                bonus(PassiveType.MANA_REGEN, 0.10f));
            case "Kuroneko" -> List.of(
                bonus(PassiveType.DASH_COOLDOWN, 0.12f));
            case "MatchaWhisk" -> List.of(
                bonus(PassiveType.COMPANION_FIRE_RATE, 0.06f));
            case "SakeCup" -> List.of(
                bonus(PassiveType.MANA_REGEN, 0.08f));
            case "TenguFeather" -> List.of(
                bonus(PassiveType.DASH_COOLDOWN, 0.18f));
            case "TeruTeruBozu" -> List.of(
                bonus(PassiveType.SEAL_RADIUS, 0.06f));
            case "ToriiGate" -> List.of(
                bonus(PassiveType.MANA_REGEN, 0.12f));
            case "YataMirror" -> List.of(
                bonus(PassiveType.SEAL_RADIUS, 0.10f),
                bonus(PassiveType.COMPANION_FIRE_RATE, 0.10f));
            default -> List.of();
        };
    }

    /** Aggregates the bonuses of all active Omamori slots. */
    public static Stats aggregate(Collection<Omamori> activeOmamoris) {
        float manaRegen = 0f;
        float sealRadius = 0f;
        float dashCooldown = 0f;
        float fireRate = 0f;

        if (activeOmamoris != null) {
            for (Omamori omamori : activeOmamoris) {
                for (Bonus bonus : bonusesFor(omamori)) {
                    switch (bonus.type()) {
                        case MANA_REGEN -> manaRegen += bonus.percent();
                        case SEAL_RADIUS -> sealRadius += bonus.percent();
                        case DASH_COOLDOWN -> dashCooldown += bonus.percent();
                        case COMPANION_FIRE_RATE -> fireRate += bonus.percent();
                    }
                }
            }
        }

        return new Stats(
            Math.min(MAX_MANA_REGEN_MULTIPLIER, 1f + manaRegen),
            Math.min(MAX_SEAL_RADIUS_MULTIPLIER, 1f + sealRadius),
            Math.max(MIN_DASH_COOLDOWN_MULTIPLIER, 1f - dashCooldown),
            Math.min(MAX_FIRE_RATE_MULTIPLIER, 1f + fireRate));
    }

    private static Bonus bonus(PassiveType type, float percent) {
        return new Bonus(type, percent);
    }
}
