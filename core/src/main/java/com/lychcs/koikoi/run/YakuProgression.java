package com.lychcs.koikoi.run;

import com.lychcs.koikoi.scoring.YakuType;
import java.util.EnumMap;
import java.util.Map;

public class YakuProgression {

    /** Basis-Chips, die pro weiterem Level hinzukommen. */
    public static final double CHIPS_PER_LEVEL = 15.0;

    /** Basis-Mult, der pro weiterem Level hinzukommt. */
    public static final double MULT_PER_LEVEL = 2.0;

    private final Map<YakuType, Integer> levels = new EnumMap<>(YakuType.class);
    private final Map<YakuType, Integer> xp = new EnumMap<>(YakuType.class);

    public int getLevel(YakuType type) {
        return levels.getOrDefault(type, 1);
    }

    public int getXp(YakuType type) {
        return xp.getOrDefault(type, 0);
    }

    /**
     * Read-only Schwelle bis zum naechsten Level des aktuellen Levels.
     * Wird unter anderem vom Inventar genutzt; veraendert keinen Zustand.
     */
    public int getXpToNextLevel(YakuType type) {
        long required = xpRequiredForLevel(getLevel(type));
        return (int) Math.min(Integer.MAX_VALUE, required);
    }

    /**
     * Fuegt Erfahrungspunkte hinzu. Mehrere Level-ups in einem Aufruf werden
     * vollstaendig verarbeitet (Schwelle steigt mit dem Level: 5, 10, 15, ...).
     * Extreme Betraege koennen weder negativ werden noch den int-Bereich sprengen.
     */
    public void addXp(YakuType type, int amount) {
        if (type == null || amount == 0) {
            return;
        }

        // Rechnung in long, damit grosse XP-Mengen nicht ueberlaufen.
        long availableXp = (long) getXp(type) + amount;
        if (availableXp < 0L) {
            availableXp = 0L;
        }

        int currentLevel = getLevel(type);
        long requiredXp = xpRequiredForLevel(currentLevel);

        // Mehrere Level-ups in einem Aufruf verarbeiten.
        while (requiredXp > 0L && availableXp >= requiredXp) {
            availableXp -= requiredXp;
            currentLevel++;
            requiredXp = xpRequiredForLevel(currentLevel);
        }

        levels.put(type, currentLevel);
        xp.put(type, (int) Math.min(Integer.MAX_VALUE, availableXp));
    }

    /** Benoetigte XP fuer den Aufstieg vom angegebenen Level (Level * 5). */
    private static long xpRequiredForLevel(int level) {
        return (long) level * 5L;
    }

    // Berechnet die modifizierten Basis-Chips basierend auf dem Level
    // (CHIPS_PER_LEVEL Chips und MULT_PER_LEVEL Mult pro weiterem Level)
    public double getUpgradedChips(YakuType type) {
        int lvl = getLevel(type);
        return type.getBaseChips() + (lvl - 1) * CHIPS_PER_LEVEL;
    }

    public double getUpgradedMult(YakuType type) {
        int lvl = getLevel(type);
        return type.getBaseMult() + (lvl - 1) * MULT_PER_LEVEL;
    }
}