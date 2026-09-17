package com.lychcs.koikoi.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält den veränderlichen Zustand während der Left-to-Right Evaluierung.
 * Nach der Berechnung wird daraus das finale, immutable CalculationBreakdown erzeugt.
 *
 * Chips und Mult sind Dezimalwerte (double). Es findet an keiner Stelle eine
 * Rundung oder ein Cast auf einen Ganzzahltyp statt; ungueltige Werte (NaN,
 * unendlich, negative Mult-Faktoren) werden hier zentral abgefangen.
 */
public class ScoreAccumulator {

    private double chips;
    private double mult;
    private int earnedMon = 0;
    private int earnedVoidDust = 0;
    private final List<ScoringEvent> events = new ArrayList<>();

    public ScoreAccumulator(double startingChips, double startingMult) {
        this.chips = clamp(validate(startingChips));
        this.mult = clamp(Math.max(1.0, validate(startingMult)));
    }

    public void addChips(String source, double amount) {
        double value = validate(amount);
        this.chips = clamp(this.chips + value);
        events.add(new ScoringEvent(source, value, 0.0, 1.0, 0, 0));
    }

    public void addMult(String source, double amount) {
        double value = validate(amount);
        this.mult = clamp(this.mult + value);
        events.add(new ScoringEvent(source, 0.0, value, 1.0, 0, 0));
    }

    /** Multiplikativer Mult-Effekt: keine Rundung, nur Validierung des Faktors. */
    public void multiplyMult(String source, double factor) {
        double safeFactor = (Double.isFinite(factor) && factor > 0.0) ? factor : 1.0;
        this.mult = clamp(this.mult * safeFactor);
        events.add(new ScoringEvent(source, 0.0, 0.0, safeFactor, 0, 0));
    }

    public void addMon(String source, int amount) {
        this.earnedMon += amount;
        events.add(new ScoringEvent(source, 0.0, 0.0, 1.0, amount, 0));
    }

    public void addVoidDust(String source, int amount) {
        this.earnedVoidDust += amount;
        events.add(new ScoringEvent(source, 0.0, 0.0, 1.0, 0, amount));
    }

    public double getChips() { return chips; }
    public double getMult() { return mult; }
    public int getEarnedMon() { return earnedMon; }
    public int getEarnedVoidDust() {
        return earnedVoidDust;
    }
    public List<ScoringEvent> getEvents() { return events; }

    /** Ungueltige, negative oder nicht-endliche Betraege werden zu 0. */
    private static double validate(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    /** Verhindert unendliche Zwischenergebnisse, ohne zu runden. */
    private static double clamp(double value) {
        return Double.isFinite(value) ? value : Double.MAX_VALUE;
    }
}

