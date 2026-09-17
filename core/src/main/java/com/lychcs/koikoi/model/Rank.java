package com.lychcs.koikoi.model;

public enum Rank {
    PETAL(1),
    RIBBON(5),
    BEAST(10),
    HIKARI(20);

    private final double baseValue;

    Rank(double baseValue) {
        this.baseValue = baseValue;
    }
    public double getBaseValue() {
        return baseValue;
    }
}
