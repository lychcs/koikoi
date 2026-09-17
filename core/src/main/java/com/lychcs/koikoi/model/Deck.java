package com.lychcs.koikoi.model;

import com.lychcs.koikoi.model.hanko.HankoEffect;
import java.util.ArrayList;
import java.util.List;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public void initializeDeck() {
        cards.clear();

        // === SPRING ===
        addCard(CardID.SPRING_HIKARI_CHERRY_BLOSSOM_STORM, Season.SPRING, Rank.HIKARI, "Cherry Blossom Storm", 1);
        addCard(CardID.SPRING_BEAST_KOI, Season.SPRING, Rank.BEAST, "Koi", 1);
        addCard(CardID.SPRING_BEAST_CRANE, Season.SPRING, Rank.BEAST, "Crane", 1);
        addCard(CardID.SPRING_RIBBON, Season.SPRING, Rank.RIBBON, "Ribbon", 3);
        addCard(CardID.SPRING_PETAL, Season.SPRING, Rank.PETAL, "Petal", 6);

        // === SUMMER ===
        addCard(CardID.SUMMER_HIKARI_GLAZING_SUN, Season.SUMMER, Rank.HIKARI, "Glazing Sun", 1);
        addCard(CardID.SUMMER_BEAST_MOUNTAIN_MONKEY, Season.SUMMER, Rank.BEAST, "Mountain Monkey", 1);
        addCard(CardID.SUMMER_BEAST_SERPENT, Season.SUMMER, Rank.BEAST, "Serpent", 1);
        addCard(CardID.SUMMER_RIBBON, Season.SUMMER, Rank.RIBBON, "Ribbon", 3);
        addCard(CardID.SUMMER_PETAL, Season.SUMMER, Rank.PETAL, "Petal", 6);

        // === AUTUMN ===
        addCard(CardID.AUTUMN_HIKARI_RED_MOON, Season.AUTUMN, Rank.HIKARI, "Red Moon", 1);
        addCard(CardID.AUTUMN_BEAST_BOAR, Season.AUTUMN, Rank.BEAST, "Boar", 1);
        addCard(CardID.AUTUMN_BEAST_DEER, Season.AUTUMN, Rank.BEAST, "Deer", 1);
        addCard(CardID.AUTUMN_RIBBON, Season.AUTUMN, Rank.RIBBON, "Ribbon", 3);
        addCard(CardID.AUTUMN_PETAL, Season.AUTUMN, Rank.PETAL, "Petal", 6);

        // === WINTER ===
        addCard(CardID.WINTER_HIKARI_POLAR_LIGHT, Season.WINTER, Rank.HIKARI, "Polar Light", 1);
        addCard(CardID.WINTER_BEAST_ARCTIC_FOX, Season.WINTER, Rank.BEAST, "Arctic Fox", 1);
        addCard(CardID.WINTER_BEAST_WHITE_OWL, Season.WINTER, Rank.BEAST, "White Owl", 1);
        addCard(CardID.WINTER_RIBBON, Season.WINTER, Rank.RIBBON, "Ribbon", 3);
        addCard(CardID.WINTER_PETAL, Season.WINTER, Rank.PETAL, "Petal", 6);
    }

    private void addCard(CardID id, Season season, Rank rank, String baseName, int count) {
        for (int i = 1; i <= count; i++) {
            cards.add(new Card(
                id,
                season,
                rank,
                season.toString().substring(0, 1) + season.toString().substring(1).toLowerCase() + " " + baseName + " #" + i,
                HankoEffect.NONE
            ));
        }
    }
    public List<Card> getCards() {
        return cards;
    }

    public boolean evolveCard(Card oldCard, Card newCard) {
        int index = cards.indexOf(oldCard);
        if (index != -1) {
            cards.set(index, newCard);
            return true;
        }
        return false;
    }
}
