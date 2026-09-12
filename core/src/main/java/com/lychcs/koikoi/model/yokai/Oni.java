package com.lychcs.koikoi.model.yokai;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public class Oni extends Yokai {

    public Oni(YokaiStage initialStage) {
        super(initialStage);
    }

    public Oni() {
        this(YokaiStage.LEVEL_1);
    }

    @Override
    public String getName() {
        return switch (getStage()) {
            case EGG -> "Oni-Ei";
            case LEVEL_1 -> "Ko-Oni";
            case LEVEL_2 -> "Oni";
            case LEVEL_3 -> "Dai-Oni";
            case YAMI -> "Rasetsu (Yami Oni)";
        };
    }

    @Override
    public String getDescription() {
        return switch (getStage()) {
            case EGG -> "Ein schlummernder Dämon. Benötigt Evolution.";
            case LEVEL_1 -> "Verleiht dieser Hand +50 Chips.";
            case LEVEL_2 -> "Verleiht dieser Hand +8 Mult.";
            case LEVEL_3 -> "Verdoppelt den Multiplikator dieser Hand (x2.0 Mult).";
            case YAMI -> "Opfert Seelen für +66 Chips und x2.5 Mult.";
        };
    }

    @Override
    public boolean activate(ScoreContext context, ScoreAccumulator acc) {
        switch (getStage()) {
            case LEVEL_1 -> {
                acc.addChips(getName() + " (Brute Force)", 50);
                return true;
            }
            case LEVEL_2 -> {
                acc.addMult(getName() + " (Demonic Rage)", 8);
                return true;
            }
            case LEVEL_3 -> {
                acc.multiplyMult(getName() + " (Calamity Blow)", 2.0);
                return true;
            }
            case YAMI -> {
                acc.addChips(getName() + " (Void Cleave)", 66);
                acc.multiplyMult(getName() + " (Abyssal Strike)", 2.5);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Gibt den Namen der Region im TextureAtlas zurück, passend zur Stufe.
     */
    public String getAtlasRegionName() {
        return switch (getStage()) {
            case LEVEL_1 -> "YOKAI_KO_ONI";
            case LEVEL_2 -> "YOKAI_ONI";
            case LEVEL_3 -> "YOKAI_DAI_ONI";
            case YAMI -> "YOKAI_YAMI_ONI";
            default -> "YOKAI_KO_ONI";
        };
    }
}
