package com.lychcs.koikoi.run;

import com.lychcs.koikoi.scoring.YakuType;
import java.util.EnumMap;
import java.util.Map;

public class YakuProgression {
    private final Map<YakuType, Integer> levels = new EnumMap<>(YakuType.class);
    private final Map<YakuType, Integer> xp = new EnumMap<>(YakuType.class);

    public int getLevel(YakuType type) {
        return levels.getOrDefault(type, 1);
    }

    public int getXp(YakuType type) {
        return xp.getOrDefault(type, 0);
    }

    public void addXp(YakuType type, int amount) {
        int currentXp = getXp(type) + amount;
        int currentLevel = getLevel(type);

        // Benötigte XP für das nächste Level (z.B. Level * 5)
        int requiredXp = currentLevel * 5;

        if (currentXp >= requiredXp) {
            currentXp -= requiredXp;
            levels.put(type, currentLevel + 1);
        }
        xp.put(type, currentXp);
    }

    // Berechnet die modifizierten Basis-Chips basierend auf dem Level (+15 Chips & +2 Mult pro Level)
    public double getUpgradedChips(YakuType type) {
        int lvl = getLevel(type);
        return type.getBaseChips() + (lvl - 1) * 15.0;
    }

    public double getUpgradedMult(YakuType type) {
        int lvl = getLevel(type);
        return type.getBaseMult() + (lvl - 1) * 2.0;
    }
}
