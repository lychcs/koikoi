package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HandContext {

    private final Map<Rank, List<Card>> cardsByRank = new EnumMap<>(Rank.class);
    private final Map<Season, List<Card>> cardsBySeason = new EnumMap<>(Season.class);
    private final Set<CardID> cardIds = EnumSet.noneOf(CardID.class);
    private final List<Card> allCards;

    public HandContext(List<Card> cards) {
        this.allCards = List.copyOf(cards);

        // Pre-fill bucket collections to avoid null checks later
        for (Rank rank : Rank.values()) {
            cardsByRank.put(rank, new ArrayList<>());
        }
        for (Season season : Season.values()) {
            cardsBySeason.put(season, new ArrayList<>());
        }

        // Single pass aggregation
        for (Card card : cards) {
            cardsByRank.get(card.rank()).add(card);
            cardsBySeason.get(card.season()).add(card);
            cardIds.add(card.id());
        }
    }

    public List<Card> getCardsByRank(Rank rank) {
        return Collections.unmodifiableList(cardsByRank.get(rank));
    }

    public List<Card> getCardsBySeason(Season season) {
        return Collections.unmodifiableList(cardsBySeason.get(season));
    }

    public int getRankCount(Rank rank) {
        return cardsByRank.get(rank).size();
    }

    public int getSeasonCount(Season season) {
        return cardsBySeason.get(season).size();
    }

    public boolean containsCardId(CardID cardID) {
        return cardIds.contains(cardID);
    }

    public Set<CardID> getCardIds() {
        return Collections.unmodifiableSet(cardIds);
    }

    public List<Card> getAllCards() {
        return allCards;
    }

    public int getTotalCardCount() {
        return allCards.size();
    }
}
