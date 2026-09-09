package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.fuku.FukuContext;

public class RunSession implements FukuContext {

    private int mon = 0;
    private int maxInterestCap = 5;

    private int baseHands = 4;
    private int baseDiscards = 3;

    // Zins-Berechnung am Ende der Runde
    public int applyEndRoundInterest() {
        int interestEarned = this.mon / 5;

        if (interestEarned > maxInterestCap) {
            interestEarned = maxInterestCap;
        }

        this.mon += interestEarned;
        return interestEarned;
    }

    // --- Fuku Context Implementierung ---
    @Override
    public int getMon() { return this.mon; }

    @Override
    public void addMon(int amount) { this.mon += amount; }

    @Override
    public void addMaxHands(int amount) { this.baseHands += amount; }

    @Override
    public void addMaxDiscards(int amount) { this.baseDiscards += amount; }

    @Override
    public void addMaxInterestCap(int amount) { this.maxInterestCap += amount; }

    // --- Getter ---
    public int getBaseHands() { return baseHands; }
    public int getBaseDiscards() { return baseDiscards; }
    public int getMaxInterestCap() { return maxInterestCap; }
}
