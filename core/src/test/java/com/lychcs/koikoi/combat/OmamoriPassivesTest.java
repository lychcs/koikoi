package com.lychcs.koikoi.combat;

import com.lychcs.koikoi.model.omamori.DarumaDoll;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;
import com.lychcs.koikoi.model.omamori.TenguFeather;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Passive Omamori slot infrastructure: 15 amulets, stacking and clamping. */
class OmamoriPassivesTest {

    @Test
    void everyCollectableOmamoriContributesAtLeastOneBonus() {
        List<Omamori> all = OmamoriPool.getRandomDistinctOmamoris(15, List.of());

        assertEquals(15, all.size(), "the pool must contain all 15 Omamoris");
        for (Omamori omamori : all) {
            assertFalse(OmamoriPassives.bonusesFor(omamori).isEmpty(),
                omamori.getClass().getSimpleName() + " has no passive bonus");
        }
    }

    @Test
    void allFourPassiveTypesAreCovered() {
        EnumSet<OmamoriPassives.PassiveType> types = EnumSet.noneOf(OmamoriPassives.PassiveType.class);

        for (Omamori omamori : OmamoriPool.getRandomDistinctOmamoris(15, List.of())) {
            for (OmamoriPassives.Bonus bonus : OmamoriPassives.bonusesFor(omamori)) {
                types.add(bonus.type());
            }
        }

        assertEquals(EnumSet.allOf(OmamoriPassives.PassiveType.class), types);
    }

    @Test
    void emptyLoadoutIsNeutral() {
        OmamoriPassives.Stats stats = OmamoriPassives.aggregate(List.of());

        assertEquals(1f, stats.manaRegenMultiplier(), 0.0001f);
        assertEquals(1f, stats.sealRadiusMultiplier(), 0.0001f);
        assertEquals(1f, stats.dashCooldownMultiplier(), 0.0001f);
        assertEquals(1f, stats.companionFireRateMultiplier(), 0.0001f);
    }

    @Test
    void singleAmuletProvidesItsBonuses() {
        OmamoriPassives.Stats stats = OmamoriPassives.aggregate(List.of(new DarumaDoll()));

        assertEquals(1.10f, stats.sealRadiusMultiplier(), 0.0001f);
        assertEquals(1.05f, stats.manaRegenMultiplier(), 0.0001f);
        assertEquals(1f, stats.dashCooldownMultiplier(), 0.0001f);
    }

    @Test
    void bonusesStackAndClampAtTheirUpperBounds() {
        List<Omamori> tenDarumas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenDarumas.add(new DarumaDoll());
        }

        OmamoriPassives.Stats stats = OmamoriPassives.aggregate(tenDarumas);

        assertEquals(OmamoriPassives.MAX_SEAL_RADIUS_MULTIPLIER, stats.sealRadiusMultiplier(), 0.0001f);
        assertEquals(1.5f, stats.manaRegenMultiplier(), 0.0001f);
    }

    @Test
    void dashCooldownNeverFallsBelowItsFloor() {
        List<Omamori> tenFeathers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenFeathers.add(new TenguFeather());
        }

        OmamoriPassives.Stats stats = OmamoriPassives.aggregate(tenFeathers);

        assertEquals(OmamoriPassives.MIN_DASH_COOLDOWN_MULTIPLIER,
            stats.dashCooldownMultiplier(), 0.0001f);
        assertTrue(stats.dashCooldownMultiplier() > 0f);
    }

    @Test
    void statsApplyToTheBaseCombatValues() {
        OmamoriPassives.Stats neutral = OmamoriPassives.Stats.NEUTRAL;
        CombatStats neutralStats = neutral.applyTo(CombatStats.BASE);

        assertEquals(CombatStats.BASE.manaRegenPerSecond(), neutralStats.manaRegenPerSecond(), 0.0001f);
        assertEquals(CombatStats.BASE.sealRadius(), neutralStats.sealRadius(), 0.0001f);
        assertEquals(CombatStats.BASE.dashCooldown(), neutralStats.dashCooldown(), 0.0001f);
        assertEquals(CombatStats.BASE.companionFireRate(), neutralStats.companionFireRate(), 0.0001f);

        OmamoriPassives.Stats buffed = OmamoriPassives.aggregate(List.of(new DarumaDoll()));
        CombatStats buffedStats = buffed.applyTo(CombatStats.BASE);

        assertEquals(CombatStats.BASE.sealRadius() * 1.10f, buffedStats.sealRadius(), 0.0001f);
        assertEquals(CombatStats.BASE.manaRegenPerSecond() * 1.05f,
            buffedStats.manaRegenPerSecond(), 0.0001f);
    }
}
