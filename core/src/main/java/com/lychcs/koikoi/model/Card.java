package com.lychcs.koikoi.model;

import com.lychcs.koikoi.model.hanko.HankoEffect;

public record Card(
    CardID id,
    Season season,
    Rank rank,
    String name,
    HankoEffect effect) {

    public boolean matchesSeasonOf(Card other) {
        return other != null && this.season == other.season();
    }
    public boolean hasHanko() {
        return this.effect != HankoEffect.NONE;
    }
}
