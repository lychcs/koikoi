package com.lychcs.koikoi.scoring;

public record ScoringEvent(
    String sourceName,
    long addedChips,
    long addedMult,
    double xMult,
    int addedMon,
    int addedVoidDust
) {}
