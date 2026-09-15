package com.lychcs.koikoi.model.yokai;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public abstract class Yokai {

    private final String id;
    private final String name;
    private final String atlasRegionName;
    private YokaiStage stage;
    private boolean exhausted;

    private int level = 1;
    private int currentXp = 0;

    public static final int MAX_LEVEL = 3;

    public Yokai(String id, String name, String atlasRegionName, YokaiStage initialStage) {
        this.id = id;
        this.name = name;
        this.atlasRegionName = atlasRegionName;
        this.stage = initialStage;
        this.exhausted = false;
    }

    // Bequemer Zweit-Konstruktor für Starter auf Stufe 1
    public Yokai(String id, String name, String atlasRegionName) {
        this(id, name, atlasRegionName, YokaiStage.LEVEL_1);
    }

    public abstract String getDescription();

    /**
     * Führt den individuellen Scoring-Effekt des Yokai auf dem Altar aus.
     */
    public abstract boolean activate(ScoreContext context, ScoreAccumulator acc);

    /**
     * Fügt Erfahrungspunkte hinzu und stößt automatische Level-Ups / Evolutionen an.
     */
    public void addXp(int amount) {
        if (level >= MAX_LEVEL) return;

        currentXp += amount;

        while (level < MAX_LEVEL && currentXp >= getXpToNextLevel()) {
            currentXp -= getXpToNextLevel();
            levelUp();
        }
    }

    private void levelUp() {
        level++;
        evolve();
        System.out.println(name + " ist auf Level " + level + " (" + stage + ") aufgestiegen!");
    }

    public void evolve() {
        if (stage == YokaiStage.EGG) stage = YokaiStage.LEVEL_1;
        else if (stage == YokaiStage.LEVEL_1) stage = YokaiStage.LEVEL_2;
        else if (stage == YokaiStage.LEVEL_2) stage = YokaiStage.LEVEL_3;
    }

    /**
     * Berechnet die benötigten EP für das nächste Level.
     * Level 1 -> 2: 50 XP
     * Level 2 -> 3: 123 XP
     */
    public int getXpToNextLevel() {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        return (int) (50 * Math.pow(level, 1.3));
    }

    // --- Getter & Setter ---

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAtlasRegionName() { return atlasRegionName; }
    public YokaiStage getStage() { return stage; }
    public int getLevel() { return level; }
    public int getXp() { return currentXp; }
    public int getCurrentXp() { return currentXp; }

    public boolean isExhausted() { return exhausted; }
    public void setExhausted(boolean exhausted) { this.exhausted = exhausted; }
}
