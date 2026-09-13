package com.lychcs.koikoi.run;

public enum GameSeason {
    SPRING("Inari", "Der Geist der Ernte"),
    SUMMER("Amaterasu", "Die Sonnengöttin"),
    AUTUMN("Tsukuyomi", "Der Mondgott"),
    WINTER("Susanoo", "Der Sturmgott"),
    FINAL("Göttin des Lichts", "Die Urmutter"); // Das finale End-Game

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
