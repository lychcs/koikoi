package com.lychcs.koikoi.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    private final Map<Rank, Integer> distributionMap = Map.of(
        Rank.HIKARI, 1,
        Rank.BEAST, 2,
        Rank.RIBBON, 3,
        Rank.PETAL, 6
    );

    public void initializeDeck() {
        cards.clear();

        List<Rank> ranksPerSeason = distributionMap.entrySet().stream()
            .flatMap(entry -> Collections.nCopies(entry.getValue(), entry.getKey()).stream())
            .toList();

        cards.addAll(
            Arrays.stream(Season.values())
                .flatMap(season -> ranksPerSeason.stream()
                    .map(rank -> new Card(
                        CardID.UNKNOWN,
                        season,
                        rank,
                        season + " " + rank
                    ))
                )
                .toList()
        );
    }

    public List<Card> getCards() {
        return cards;
    }

    public boolean evolveCard(Card oldCard, Card newCard) {
        int index = cards.indexOf(oldCard);
        if (index != -1) {
            cards.set(index, newCard); // Ersetzt die Instanz an der exakt gleichen Listen-Position
            return true;
        }
        return false;
    }
}
