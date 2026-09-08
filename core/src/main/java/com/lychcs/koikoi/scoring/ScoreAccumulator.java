package com.lychcs.koikoi.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält den veränderlichen Zustand während der Left-to-Right Evaluierung.
 * Nach der Berechnung wird daraus das finale, immutable CalculationBreakdown erzeugt.
 */
public class ScoreAccumulator {
    private int chips;
    private int mult;
    private final List<ScoringEvent> events = new ArrayList<>();

    public ScoreAccumulator(int startingChips, int startingMult) {
        this.chips = startingChips;
        this.mult = startingMult;
    }

    public void addChips(String source, int amount) {
        this.chips += amount;
        events.add(new ScoringEvent(source, amount, 0, 1.0));
    }

    public void addMult(String source, int amount) {
        this.mult += amount;
        events.add(new ScoringEvent(source, 0, amount, 1.0));
    }

    public void multiplyMult(String source, double factor) {
        this.mult = (int) Math.round(this.mult * factor);
        events.add(new ScoringEvent(source, 0, 0, factor));
    }

    public int getChips() { return chips; }
    public int getMult() { return mult; }
    public List<ScoringEvent> getEvents() { return events; }
}

// Event-Log für JavaFX (Wer hat wann was gemacht?)
record ScoringEvent(String sourceName, int addedChips, int addedMult, double xMult) {}
