package com.lychcs.koikoi.scoring;

import java.util.List;
import java.util.Objects;

/**
 * Immutable report detailing each calculation step.
 * Designed to drive UI animations and provide breakdown data for tooltips.
 * Alle Scorewerte sind Dezimalwerte (double) ohne Zwischenrundung.
 */
public record CalculationBreakdown(
    double yakuChips,
    double yakuBaseMult,
    double totalChips,
    double totalMult,
    double finalScore,
    List<ScoringEvent> events
) {
    public CalculationBreakdown {
        Objects.requireNonNull(events, "events must not be null");
        events = List.copyOf(events);
    }

    public boolean isJackpot(double threshold) {
        return finalScore >= threshold;
    }

    public boolean hasModifiers() {
        return !events.isEmpty();
    }
}
