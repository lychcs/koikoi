package com.lychcs.koikoi.model.omamori;

import com.lychcs.koikoi.scoring.ScoreAccumulator;
import com.lychcs.koikoi.scoring.ScoreContext;

public interface Omamori {
    String getName();

    default String getDescription() { return "No description available."; }

    default Rarity getRarity() { return Rarity.COMMON; }

    boolean evaluate(ScoreContext context, ScoreAccumulator acc);
}
