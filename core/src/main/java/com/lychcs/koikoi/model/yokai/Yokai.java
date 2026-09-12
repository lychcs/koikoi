package com.lychcs.koikoi.model.yokai;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public abstract class Yokai {
    private YokaiStage stage;
    private boolean exhausted;

    public Yokai(YokaiStage initialStage) {
        this.stage = initialStage;
        this.exhausted = false;
    }

    public abstract String getName();
    public abstract String getDescription();

    public YokaiStage getStage() { return stage; }

    public void evolve() {
        if (stage == YokaiStage.EGG) stage = YokaiStage.LEVEL_1;
        else if (stage == YokaiStage.LEVEL_1) stage = YokaiStage.LEVEL_2;
        else if (stage == YokaiStage.LEVEL_2) stage = YokaiStage.LEVEL_3;
    }

    public void transformToYami() {
        this.stage = YokaiStage.YAMI;
    }

    public boolean isExhausted() { return exhausted; }
    public void setExhausted(boolean exhausted) { this.exhausted = exhausted; }

    /**
     * Wird während der Score-Berechnung aufgerufen, wenn der Yokai im Altar liegt.
     * Skaliert idealerweise mit dem aktuellen getStage().
     */
    public abstract boolean activate(ScoreContext context, ScoreAccumulator acc);

    public abstract String getAtlasRegionName();
}
