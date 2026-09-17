package com.lychcs.koikoi.scoring;

/**
 * Ein einzelner Wertungsschritt. Chips und Mult sind Dezimalwerte (double),
 * damit Faktoren wie x1.5 nicht gerundet werden muessen.
 */
public record ScoringEvent(
    String sourceName,
    double addedChips,
    double addedMult,
    double xMult,
    int addedMon,
    int addedVoidDust
) {}
