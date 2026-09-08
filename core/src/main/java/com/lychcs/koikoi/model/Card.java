package com.lychcs.koikoi.model;

public record Card(
    CardID id,
    Season season,
    Rank rank,
    String name) {
    public boolean matchesSeasonOf(Card other) {
        return other != null && this.season == other.season();
    }
}
