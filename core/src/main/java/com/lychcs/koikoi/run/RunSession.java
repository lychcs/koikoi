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

    private GameSeason currentSeason = GameSeason.SPRING;
    private int seasonEncounterStage = 1;
    private final List<Omamori> activeOmamoris = new ArrayList<>();

    // --- START-WERTE ---
    private int mon = 10000;          // 10.000 Mon
    private int voidDust = 5000;      // 5.000 Void Dust

    private int baseHands = 4;
    private int baseDiscards = 3;
    private final List<Yokai> yokaiBag = new ArrayList<>();
    private int maxYokaiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();
    private final List<Card> banishedThisSeason = new ArrayList<>();
    private final YakuProgression yakuProgression = new YakuProgression();
    private float lastPlayerX = -1f;
    private float lastPlayerY = -1f;
    private boolean hasStoredPosition = false;
    private final Set<String> unlockedShrines = new HashSet<>();
    private String lastVisitedShrineId = "shrine_village";

    public RunSession() {
        this.playerDeck = new Deck();
        this.playerDeck.initializeDeck();

        // --- LEVEL 1 ONI (Ko-Oni) DIREKT IN DEN BEUTEL LEGEN ---
        this.yokaiBag.add(new Oni(YokaiStage.LEVEL_1));
    }

    // --- PLAYER POSITION ---

    public void setLastPlayerPosition(float x, float y) {
        this.lastPlayerX = x;
        this.lastPlayerY = y;
        this.hasStoredPosition = true;
    }
    public boolean hasStoredPosition() {
        return hasStoredPosition;
    }
    public float getLastPlayerX() { return lastPlayerX; }
    public float getLastPlayerY() { return lastPlayerY; }

    // --- SHRINE ---

    // --- SEASON STRUKTUR ---

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
            case WINTER -> GameSeason.FINAL; // Boss Stage!
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

    // --- YAKU LEVELING ---

    public YakuProgression getYakuProgression() {
        return yakuProgression;
    }

    // --- YOKAI ---

    public List<Yokai> getYokaiBag() { return yokaiBag; }
    public int getMaxYokaiBag() { return maxYokaiBag; }
    public void addMaxYokaiBag(int amount) { this.maxYokaiBag += amount; }

    // --- MONEY ---

    public int getMon() { return this.mon; }
    public void addMon(int amount) { this.mon += amount; }

    // --- HANDS -- DISCARDS -- DECK---

    public void addMaxHands(int amount) { this.baseHands += amount; }
    public void addMaxDiscards(int amount) { this.baseDiscards += amount; }
    public int getBaseHands() { return baseHands; }
    public int getBaseDiscards() { return baseDiscards; }
    public Deck getPlayerDeck() { return playerDeck; }

    // --- OMAMORI ---

    public List<Omamori> getActiveOmamoris() { return activeOmamoris; }

    // ---HANKO ---

    public List<HankoEffect> getPurchasedHankos() { return purchasedHankos; }

    // --- SHRINES ---

    public void unlockShrine(String shrineId, float x, float y) {
        unlockedShrines.add(shrineId);
        this.lastVisitedShrineId = shrineId;
        setLastPlayerPosition(x, y); // Setzt den Respawn-Punkt!
    }

    public Set<String> getUnlockedShrines() {
        return Collections.unmodifiableSet(unlockedShrines);
    }

    // --- VOID DUST ---

    public int getVoidDust() { return voidDust; }
    public void addVoidDust(int amount) { this.voidDust += amount; }

    // --- SEASONS ---

    public GameSeason getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(GameSeason season) { this.currentSeason = season; }
    public int getSeasonEncounterStage() { return seasonEncounterStage; }
}
