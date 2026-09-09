package com.lychcs.koikoi.model;

import java.util.ArrayList;
import java.util.List;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public void initializeDeck() {
        cards.clear();

        // === SPRING ===
        addCard(CardID.SPRING_HIKARI_CHERRY_BLOSSOM_STORM, Season.SPRING, Rank.HIKARI, "Cherry Blossom Storm");
        addCard(CardID.SPRING_BEAST_KOI, Season.SPRING, Rank.BEAST, "Koi");
        addCard(CardID.SPRING_BEAST_CRANE, Season.SPRING, Rank.BEAST, "Crane");
        // addCard(CardID.SPRING_OFUDA_GENKAKU, Season.SPRING, Rank.OFUDA, "Ofuda Genkaku"); // (Einkommentieren, falls Ofuda genutzt wird)
        addMultiple(CardID.SPRING_RIBBON_DEFAULT, Season.SPRING, Rank.RIBBON, "Spring Ribbon", 3);
        addMultiple(CardID.SPRING_PETAL_DEFAULT, Season.SPRING, Rank.PETAL, "Spring Petal", 6);

        // === SUMMER ===
        addCard(CardID.SUMMER_HIKARI_GLAZING_SUN, Season.SUMMER, Rank.HIKARI, "Glazing Sun");
        addCard(CardID.SUMMER_BEAST_BUTTERFLY, Season.SUMMER, Rank.BEAST, "Butterfly");
        addCard(CardID.SUMMER_BEAST_CICADA, Season.SUMMER, Rank.BEAST, "Cicada");
        addMultiple(CardID.SUMMER_RIBBON_DEFAULT, Season.SUMMER, Rank.RIBBON, "Summer Ribbon", 3);
        addMultiple(CardID.SUMMER_PETAL_DEFAULT, Season.SUMMER, Rank.PETAL, "Summer Petal", 6);

        // === AUTUMN ===
        addCard(CardID.AUTUMN_HIKARI_RED_MOON, Season.AUTUMN, Rank.HIKARI, "Red Moon");
        addCard(CardID.AUTUMN_BEAST_BOAR, Season.AUTUMN, Rank.BEAST, "Boar");
        addCard(CardID.AUTUMN_BEAST_DEER, Season.AUTUMN, Rank.BEAST, "Deer");
        addMultiple(CardID.AUTUMN_RIBBON_DEFAULT, Season.AUTUMN, Rank.RIBBON, "Autumn Ribbon", 3);
        addMultiple(CardID.AUTUMN_PETAL_DEFAULT, Season.AUTUMN, Rank.PETAL, "Autumn Petal", 6);

        // === WINTER ===
        // Wenn du YAMI als eigenen Rang im Rank-Enum hast:
        addCard(CardID.WINTER_YAMI_YUKI_ONNA, Season.WINTER, Rank.YAMI, "Yuki Onna");
        addCard(CardID.WINTER_HIKARI_POLAR_LIGHT, Season.WINTER, Rank.HIKARI, "Polar Light");
        addCard(CardID.WINTER_BEAST_ARCTIC_FOX, Season.WINTER, Rank.BEAST, "Arctic Fox");
        addCard(CardID.WINTER_BEAST_WHITE_OWL, Season.WINTER, Rank.BEAST, "White Owl");
        // Wir ziehen hier ein Ribbon ab, um Platz für die Yami-Karte zu machen (damit es 12 Karten bleiben)
        addMultiple(CardID.WINTER_RIBBON_DEFAULT, Season.WINTER, Rank.RIBBON, "Winter Ribbon", 2);
        addMultiple(CardID.WINTER_PETAL_DEFAULT, Season.WINTER, Rank.PETAL, "Winter Petal", 6);
    }

    // Hilfsmethode für einzigartige Einzelkarten (Hikari, Beast, Yami)
    private void addCard(CardID id, Season season, Rank rank, String name) {
        cards.add(new Card(id, season, rank, name));
    }

    // Hilfsmethode für Standardkarten (generiert das "#1", "#2" im Namen)
    private void addMultiple(CardID id, Season season, Rank rank, String baseName, int count) {
        for (int i = 1; i <= count; i++) {
            cards.add(new Card(id, season, rank, baseName + " #" + i));
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
