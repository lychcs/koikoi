package com.lychcs.koikoi.model;

public enum Rank {
    PETAL(5),
    RIBBON(10),
    BEAST(20),
    HIKARI(25);

    private final double baseValue;

    Rank(double baseValue) {
        this.baseValue = baseValue;
    }
    public double getBaseValue() {
        return baseValue;
    }
}
