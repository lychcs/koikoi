package com.lychcs.koikoi.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält den veränderlichen Zustand während der Left-to-Right Evaluierung.
 * Nach der Berechnung wird daraus das finale, immutable CalculationBreakdown erzeugt.
 */
public class ScoreAccumulator {
    private long chips;
    private long mult;
    private int earnedMon = 0;
    private int earnedVoidDust = 0;
    private final List<ScoringEvent> events = new ArrayList<>();

    public ScoreAccumulator(long startingChips, long startingMult) {
        this.chips = startingChips;
        this.mult = startingMult;
    }

    public void addChips(String source, long amount) {
        this.chips += amount;
        events.add(new ScoringEvent(source, amount, 0L, 1.0, 0, 0));
    }

    public void addMult(String source, long amount) {
        this.mult += amount;
        events.add(new ScoringEvent(source, 0L, amount, 1.0, 0, 0));
    }

    public void multiplyMult(String source, double factor) {
        this.mult = Math.round(this.mult * factor);
        events.add(new ScoringEvent(source, 0L, 0L, factor, 0, 0));
    }

    public void addMon(String source, int amount) {
        this.earnedMon += amount;
        events.add(new ScoringEvent(source, 0L, 0L, 1.0, amount, 0));
    }

    public void addVoidDust(String source, int amount) {
        this.earnedVoidDust += amount;
        events.add(new ScoringEvent(source, 0L, 0L, 1.0, 0, amount));
    }
    public long getChips() { return chips; }
    public long getMult() { return mult; }
    public int getEarnedMon() { return earnedMon; }
    public int getEarnedVoidDust() {
        return earnedVoidDust;
    }
    public List<ScoringEvent> getEvents() { return events; }
}

