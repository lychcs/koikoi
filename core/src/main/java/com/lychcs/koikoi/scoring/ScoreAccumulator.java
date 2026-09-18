package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält den veränderlichen Zustand während der Left-to-Right Evaluierung.
 * Nach der Berechnung wird daraus das finale, immutable CalculationBreakdown erzeugt.
 *
 * Chips und Mult sind Dezimalwerte (double). Es findet an keiner Stelle eine
 * Rundung oder ein Cast auf einen Ganzzahltyp statt. Programmatisch ungueltige
 * Werte (NaN, unendlich, negative Betraege, Mult-Faktor &lt;= 0) werden nicht
 * still umgewandelt, sondern mit einer verstaendlichen Exception abgelehnt.
 *
 * <p>Zusaetzlich fuehrt der Accumulator den aktuellen Event-Kontext
 * (Karte, Shikigami, Omamori). Effektklassen rufen weiterhin ihre gewohnten
 * Methoden auf; der Kontext wird vom ScoreCalculator gesetzt. So traegt jedes
 * {@link ScoringEvent} seine Quelle, ohne dass Text geparst werden muss.</p>
 */
public class ScoreAccumulator {

    private double chips;
    private double mult;
    private int earnedMon = 0;
    private int earnedVoidDust = 0;
    private final List<ScoringEvent> events = new ArrayList<>();
    /** true = Belohnungen (Mon/Void Dust) werden erfasst (echte Wertung). */
    private final boolean rewardsEnabled;

    // Aktueller Event-Kontext.
    private ScoringSourceType contextType = ScoringSourceType.UNATTRIBUTED;
    private Card contextCard;
    private Omamori contextOmamori;
    private Shikigami contextShikigami;

    public ScoreAccumulator(double startingChips, double startingMult) {
        this(startingChips, startingMult, true);
    }

    /**
     * @param rewardsEnabled false im Bewertungsmodus (keine Mon-/Void-Dust-Events)
     */
    public ScoreAccumulator(double startingChips, double startingMult, boolean rewardsEnabled) {
        this.chips = requireNonNegative("ScoreAccumulator(startingChips)", startingChips);
        this.mult = requirePositive("ScoreAccumulator(startingMult)", startingMult);
        this.rewardsEnabled = rewardsEnabled;
    }

    /** Kontext fuer die Basis-Chips einer gewerteten Karte. */
    public void beginCardBase(Card card) {
        begin(ScoringSourceType.CARD_BASE, card, null, null);
    }

    /** Kontext fuer den Hanko-Scoreeffekt einer gewerteten Karte. */
    public void beginHanko(Card card) {
        begin(ScoringSourceType.HANKO, card, null, null);
    }

    /** Kontext fuer den Scoreeffekt des aktiven Shikigami. */
    public void beginShikigami(Shikigami shikigami) {
        begin(ScoringSourceType.SHIKIGAMI, null, null, shikigami);
    }

    /** Kontext fuer den Scoreeffekt eines Omamori. */
    public void beginOmamori(Omamori omamori) {
        begin(ScoringSourceType.OMAMORI, null, omamori, null);
    }

    /** Beendet den aktuellen Kontext. */
    public void endContext() {
        begin(ScoringSourceType.UNATTRIBUTED, null, null, null);
    }

    private void begin(ScoringSourceType type, Card card, Omamori omamori, Shikigami shikigami) {
        this.contextType = type;
        this.contextCard = card;
        this.contextOmamori = omamori;
        this.contextShikigami = shikigami;
    }

    public void addChips(String source, double amount) {
        double value = requireNonNegative("addChips(" + source + ")", amount);
        this.chips = requireFiniteResult("addChips(" + source + ")", this.chips + value);
        events.add(new ScoringEvent(contextType, source, contextCard, contextOmamori, contextShikigami,
            value, 0.0, 1.0, 0, 0));
    }

    public void addMult(String source, double amount) {
        double value = requireNonNegative("addMult(" + source + ")", amount);
        this.mult = requireFiniteResult("addMult(" + source + ")", this.mult + value);
        events.add(new ScoringEvent(contextType, source, contextCard, contextOmamori, contextShikigami,
            0.0, value, 1.0, 0, 0));
    }

    /** Multiplikativer Mult-Effekt: keine Rundung, Faktor muss endlich und &gt; 0 sein. */
    public void multiplyMult(String source, double factor) {
        double safeFactor = requirePositive("multiplyMult(" + source + ")", factor);
        this.mult = requireFiniteResult("multiplyMult(" + source + ")", this.mult * safeFactor);
        events.add(new ScoringEvent(contextType, source, contextCard, contextOmamori, contextShikigami,
            0.0, 0.0, safeFactor, 0, 0));
    }

    public void addMon(String source, int amount) {
        if (!rewardsEnabled || amount == 0) {
            return;
        }
        this.earnedMon += amount;
        events.add(new ScoringEvent(ScoringSourceType.REWARD, source, contextCard, contextOmamori, contextShikigami,
            0.0, 0.0, 1.0, amount, 0));
    }

    public void addVoidDust(String source, int amount) {
        if (!rewardsEnabled || amount == 0) {
            return;
        }
        this.earnedVoidDust += amount;
        events.add(new ScoringEvent(ScoringSourceType.REWARD, source, contextCard, contextOmamori, contextShikigami,
            0.0, 0.0, 1.0, 0, amount));
    }

    public double getChips() { return chips; }
    public double getMult() { return mult; }
    public int getEarnedMon() { return earnedMon; }
    public int getEarnedVoidDust() {
        return earnedVoidDust;
    }
    public List<ScoringEvent> getEvents() { return events; }

    /** Betrag muss endlich und &gt;= 0 sein (Chips/Mult werden nie negativ). */
    private static double requireNonNegative(String context, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                context + ": invalid value " + value + " (expected: finite and >= 0)");
        }
        return value;
    }

    /** Multiplikator muss endlich und &gt; 0 sein. */
    private static double requirePositive(String context, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                context + ": invalid value " + value + " (expected: finite and > 0)");
        }
        return value;
    }

    /** Zwischenergebnis muss endlich bleiben (normale grosse Werte sind erlaubt). */
    private static double requireFiniteResult(String context, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                context + ": intermediate result is not finite (" + value + ")");
        }
        return value;
    }
}

