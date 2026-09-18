package com.lychcs.koikoi.run;

public enum GameSeason {
    SPRING("Inari", "The Harvest Spirit"),
    SUMMER("Amaterasu", "The Sun Goddess"),
    AUTUMN("Tsukuyomi", "The Moon God"),
    WINTER("Susanoo", "The Storm God"),
    FINAL("Goddess of Light", "The Great Mother"); // Das finale End-Game

    private final String kamiName;
    private final String kamiTitle;

    GameSeason(String kamiName, String kamiTitle) {
        this.kamiName = kamiName;
        this.kamiTitle = kamiTitle;
    }

    public String getKamiName() {
        return kamiName;
    }

    public String getKamiTitle() {
        return kamiTitle;
    }
}
