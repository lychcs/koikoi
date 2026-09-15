package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Oni;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.model.yokai.YokaiStage;

import java.util.*;

public class RunSession {

    // Feste, unantastbare Basiswerte für jeden Kampf
    public static final int BASE_HANDS = 4;
    public static final int BASE_DISCARDS = 3;

    private GameSeason currentSeason = GameSeason.SPRING;
    private int seasonEncounterStage = 1;
    private final List<Omamori> activeOmamoris = new ArrayList<>();
    private int mon = 10000;
    private int voidDust = 5000;

    private final List<Yokai> yokaiBag = new ArrayList<>();
    private int maxYokaiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();
    private final List<Card> banishedThisSeason = new ArrayList<>();
    private final YakuProgression yakuProgression = new YakuProgression();

    // Checkpoint- & Schrine-Tracking (for Teleport & Respawn)
    private final Map<String, float[]> unlockedShrines = new LinkedHashMap<>();
    private String lastVisitedShrineName = "Dorf-Schrein";
    private float lastPlayerX = -1f;
    private float lastPlayerY = -1f;
    private boolean hasStoredPosition = false;
    private int shrineRank = 0;

    public RunSession() {
        this.playerDeck = new Deck();
        this.playerDeck.initializeDeck();
        this.yokaiBag.add(new Oni(YokaiStage.LEVEL_1));
    }

    // --- SHRINE & TELEPORT SYSTEM ---

    public void registerAndActivateShrine(String shrineName, float x, float y) {
        this.unlockedShrines.put(shrineName, new float[]{x, y});
        this.lastVisitedShrineName = shrineName;
        setLastPlayerPosition(x, y); // Neuer Respawn-Ort!
    }

    public Map<String, float[]> getUnlockedShrines() {
        return Collections.unmodifiableMap(unlockedShrines);
    }

    public String getLastVisitedShrineName() {
        return lastVisitedShrineName;
    }

    public int getShrineRank() {
        return shrineRank;
    }

    public void addClearedShrine() {
        this.shrineRank++;
        System.out.println("Schrein geläutert! Spirituelles Ansehen gestiegen auf Rang: " + shrineRank);
    }

    public boolean hasRequiredRank(int requiredRank) {
        return this.shrineRank >= requiredRank;
    }

    // --- PLAYER POSITION & RESPAWN ---

    public void setLastPlayerPosition(float x, float y) {
        this.lastPlayerX = x;
        this.lastPlayerY = y;
        this.hasStoredPosition = true;
    }

    public boolean hasStoredPosition() { return hasStoredPosition; }
    public float getLastPlayerX() { return lastPlayerX; }
    public float getLastPlayerY() { return lastPlayerY; }

    // --- SEASONS ---

    public void advanceEncounterStage() {
        seasonEncounterStage++;
        if (seasonEncounterStage > 3) {
            seasonEncounterStage = 1;
            advanceSeason();
        }
    }

    private void advanceSeason() {
        currentSeason = switch (currentSeason) {
            case SPRING -> GameSeason.SUMMER;
            case SUMMER -> GameSeason.AUTUMN;
            case AUTUMN -> GameSeason.WINTER;
            case WINTER -> GameSeason.FINAL;
            case FINAL -> GameSeason.FINAL;
        };
    }

    // --- BANISH LOGIK ---

    public void banishCardForSeason(Card card) {
        if (card != null && playerDeck != null) {
            playerDeck.getCards().remove(card);
            banishedThisSeason.add(card);
        }
    }

    public void restoreSeasonBanishedCards() {
        if (playerDeck != null && !banishedThisSeason.isEmpty()) {
            playerDeck.getCards().addAll(banishedThisSeason);
            banishedThisSeason.clear();
        }
    }

    public List<Card> getBanishedThisSeason() {
        return Collections.unmodifiableList(banishedThisSeason);
    }

    // --- PROGRESSION & DECK ---

    public YakuProgression getYakuProgression() { return yakuProgression; }
    public List<Yokai> getYokaiBag() { return yokaiBag; }
    public int getMaxYokaiBag() { return maxYokaiBag; }
    public Deck getPlayerDeck() { return playerDeck; }
    public List<Omamori> getActiveOmamoris() { return activeOmamoris; }
    public List<HankoEffect> getPurchasedHankos() { return purchasedHankos; }

    // --- WÄHRUNGEN ---

    public int getMon() { return this.mon; }
    public void addMon(int amount) { this.mon += amount; }
    public int getVoidDust() { return voidDust; }
    public void addVoidDust(int amount) { this.voidDust += amount; }

    // --- FIGHT-CONSTANTS GETTER ---

    public int getBaseHands() { return BASE_HANDS; }
    public int getBaseDiscards() { return BASE_DISCARDS; }

    public GameSeason getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(GameSeason season) { this.currentSeason = season; }
    public int getSeasonEncounterStage() { return seasonEncounterStage; }
}
