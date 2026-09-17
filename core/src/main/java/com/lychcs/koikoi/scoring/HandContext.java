package com.lychcs.koikoi.scoring;

import com.lychcs.koikoi.model.*;
import com.lychcs.koikoi.model.hanko.HankoEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HandContext {

    private final Map<Rank, List<Card>> cardsByRank = new EnumMap<>(Rank.class);
    /** Virtuelle Season-Sicht fuer die Yaku-Erkennung (PolyChrome zaehlt als jede Season). */
    private final Map<Season, List<Card>> cardsByVirtualSeason = new EnumMap<>(Season.class);
    /** Natuerliche Season-Sicht (jede Karte zaehlt ausschliesslich mit card.season()). */
    private final Map<Season, List<Card>> cardsByNaturalSeason = new EnumMap<>(Season.class);
    private final Set<CardID> cardIds = EnumSet.noneOf(CardID.class);
    private final List<Card> allCards;

    public HandContext(List<Card> cards) {
        this.allCards = List.copyOf(cards);

        // Pre-fill bucket collections to avoid null checks later
        for (Rank rank : Rank.values()) {
            cardsByRank.put(rank, new ArrayList<>());
        }
        for (Season season : Season.values()) {
            cardsByVirtualSeason.put(season, new ArrayList<>());
            cardsByNaturalSeason.put(season, new ArrayList<>());
        }

        // Single pass aggregation
        for (Card card : cards) {
            cardsByRank.get(card.rank()).add(card);

            // Natuerliche Sicht: unveraendert die eigene Season der Karte.
            cardsByNaturalSeason.get(card.season()).add(card);

            // Virtuelle Sicht: Polychrome zaehlt bei der Yaku-Erkennung als jede Season.
            if (card.effect() == HankoEffect.POLYCHROME_SEAL) {
                for (Season season : Season.values()) {
                    cardsByVirtualSeason.get(season).add(card);
                }
            } else {
                cardsByVirtualSeason.get(card.season()).add(card);
            }

            cardIds.add(card.id());
        }
    }

    public List<Card> getCardsByRank(Rank rank) {
        return Collections.unmodifiableList(cardsByRank.get(rank));
    }

    /**
     * Virtuelle Season-Sicht fuer die Yaku-Erkennung:
     * eine Polychrome-Karte zaehlt als jede Season.
     */
    public List<Card> getVirtualCardsBySeason(Season season) {
        return Collections.unmodifiableList(cardsByVirtualSeason.get(season));
    }

    /** Virtuelle Season-Anzahl (Yaku-Erkennung, Polychrome zaehlt ueberall mit). */
    public int getVirtualSeasonCount(Season season) {
        return cardsByVirtualSeason.get(season).size();
    }

    /**
     * Natuerliche Season-Sicht: jede Karte zaehlt ausschliesslich mit
     * {@code card.season()} (fuer seasonabhaengige Omamori).
     */
    public List<Card> getNaturalCardsBySeason(Season season) {
        return Collections.unmodifiableList(cardsByNaturalSeason.get(season));
    }

    /** Natuerliche Season-Anzahl (ohne virtuelle PolyChrome-Ausweitung). */
    public int getNaturalSeasonCount(Season season) {
        return cardsByNaturalSeason.get(season).size();
    }

    /**
     * Bestehende API: liefert die VIRTUELLE Season-Sicht (Yaku-Erkennung),
     * identisch zu {@link #getVirtualCardsBySeason(Season)}.
     */
    public List<Card> getCardsBySeason(Season season) {
        return getVirtualCardsBySeason(season);
    }

    /**
     * Bestehende API: liefert die VIRTUELLE Season-Anzahl (Yaku-Erkennung),
     * identisch zu {@link #getVirtualSeasonCount(Season)}.
     */
    public int getSeasonCount(Season season) {
        return getVirtualSeasonCount(season);
    }

    public int getRankCount(Rank rank) {
        return cardsByRank.get(rank).size();
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
