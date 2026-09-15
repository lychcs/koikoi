package com.lychcs.koikoi.scoring;

import java.util.List;
import java.util.Objects;

/**
 * Immutable report detailing each calculation step.
 * Designed to drive UI animations and provide breakdown data for tooltips.
 */
public record CalculationBreakdown(
    int yakuChips,
    int yakuBaseMult,
    int totalChips,
    int totalMult,
    long finalPayout,
    List<ScoringEvent> events
) {
    public CalculationBreakdown {
        Objects.requireNonNull(events, "events must not be null");
        events = List.copyOf(events);
    }

    public boolean isJackpot(long threshold) {
        return finalPayout >= threshold;
    }

    public boolean hasModifiers() {
        return !events.isEmpty();
    }
}
