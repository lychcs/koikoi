package com.lychcs.koikoi.model;

public enum Rank {
    PETAL(1),
    RIBBON(5),
    BEAST(10),
    HIKARI(20),
    YAMI(20);

    private final int baseValue;

    Rank(int baseValue) {
        this.baseValue = baseValue;
    }
    public int getBaseValue() {
        return baseValue;
    }
}
