package com.lychcs.koikoi.model;

import com.lychcs.koikoi.scoring.HandContext;
import com.lychcs.koikoi.scoring.ScoreAccumulator;

public interface Omamori {
    String getName();

    /**
     * @param hand Der voraggregierte Kontext der gespielten Karten
     * @param acc Der aktuelle Punkte-Akkumulator (Left-to-Right state)
     * @return true, wenn das Omamori getriggert wurde (für UI Sounds/Particles)
     */
    boolean evaluate(HandContext hand, ScoreAccumulator acc);
}
