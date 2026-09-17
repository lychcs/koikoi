package com.lychcs.koikoi.model.shikigami;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public abstract class Shikigami {

    private final String id;
    private final String baseName;
    private final String atlasRegionName;
    private ShikigamiStage stage;
    private boolean exhausted;

    private int level = 1;
    private int currentXp = 0;

    public static final int MAX_LEVEL = 3;

    public Shikigami(String id, String baseName, String atlasRegionName, ShikigamiStage initialStage) {
        this.id = id;
        this.baseName = baseName;
        this.atlasRegionName = atlasRegionName;
        this.stage = initialStage;
        this.exhausted = false;

        // Synchronisiert das Start-Level mit der gewählten Stage
        this.level = switch (initialStage) {
            case EGG -> 0;
            case LEVEL_1 -> 1;
            case LEVEL_2 -> 2;
            case LEVEL_3 -> 3;
        };
    }

    public Shikigami(String id, String baseName, String atlasRegionName) {
        this(id, baseName, atlasRegionName, ShikigamiStage.LEVEL_1);
    }

    public abstract String getDescription();
    public abstract boolean activate(ScoreContext context, ScoreAccumulator acc);

    /**
     * Fügt Erfahrungspunkte hinzu.
     * Erreicht die Leiste das Maximum, steigt der Shikigami sofort ein Level auf
     * und entwickelt sich wie in Pokémon zur nächsten Stufe weiter.
     */
    public void addXp(int amount) {
        if (level >= MAX_LEVEL) return;

        currentXp += amount;

        while (level < MAX_LEVEL && currentXp >= getXpToNextLevel()) {
            currentXp -= getXpToNextLevel();
            levelUp();
        }

        if (level >= MAX_LEVEL) {
            currentXp = 0; // Kein XP-Überhang auf Maximalstufe
        }
    }

    private void levelUp() {
        level++;
        evolve();
        System.out.println("Evolving! " + baseName + " hat sich zu " + getName() + " (Stufe " + level + ") entwickelt!");
    }

    public void evolve() {
        if (stage == ShikigamiStage.EGG) stage = ShikigamiStage.LEVEL_1;
        else if (stage == ShikigamiStage.LEVEL_1) stage = ShikigamiStage.LEVEL_2;
        else if (stage == ShikigamiStage.LEVEL_2) stage = ShikigamiStage.LEVEL_3;
    }

    /**
     * Benötigte EP bis zum nächsten Evolutionssprung.
     * Level 1 -> 2: 50 XP
     * Level 2 -> 3: 125 XP
     * Level 3: Max
     */
    public int getXpToNextLevel() {
        if (level >= MAX_LEVEL) return 0;
        return (int) (50 * Math.pow(level, 1.32));
    }

    // --- Getter & Setter ---

    public String getId() { return id; }
    public String getName() { return baseName; } // Wird von Subklassen wie Oni überschrieben
    public String getAtlasRegionName() { return atlasRegionName; }
    public ShikigamiStage getStage() { return stage; }
    public int getLevel() { return level; }
    public int getCurrentXp() { return currentXp; }
    public int getXp() { return currentXp; }

    public boolean isExhausted() { return exhausted; }
    public void setExhausted(boolean exhausted) { this.exhausted = exhausted; }
}
