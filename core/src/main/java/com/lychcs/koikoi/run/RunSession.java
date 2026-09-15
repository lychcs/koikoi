package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai;

import java.util.*;

public class RunSession {

    private GameSeason currentSeason = GameSeason.SPRING;
    private int seasonEncounterStage = 1;
    private final List<Omamori> activeOmamoris = new ArrayList<>();
    private int mon = 0;
    private int voidDust = 0;
    private int maxInterestCap = 5;
    private int baseHands = 4;
    private int baseDiscards = 3;
    private final List<Yokai> yokaiBag = new ArrayList<>();
    private int maxYokaiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();
    private final List<Card> banishedThisSeason = new ArrayList<>();
    private final YakuProgression yakuProgression = new YakuProgression();
    private boolean shrineSummonedThisVisit = false;
    private final Set<Yokai> shrineEvolvedThisVisit = new HashSet<>();
    private float lastPlayerX = -1f;
    private float lastPlayerY = -1f;
    private boolean hasStoredPosition = false;

    public RunSession() {
        resetShrineVisit();
        this.playerDeck = new Deck();
        this.playerDeck.initializeDeck();
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

    public boolean isShrineSummonedThisVisit() { return shrineSummonedThisVisit; }
    public void setShrineSummonedThisVisit(boolean summoned) { this.shrineSummonedThisVisit = summoned; }
    public Set<Yokai> getShrineEvolvedThisVisit() { return shrineEvolvedThisVisit; }
    public void resetShrineVisit() {
        this.shrineSummonedThisVisit = false;
        this.shrineEvolvedThisVisit.clear();
    }

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
    public int getMaxInterestCap() { return maxInterestCap; }
    public void addMaxInterestCap(int amount) { this.maxInterestCap += amount; }
    public int applyEndRoundInterest() {
        int interestEarned = this.mon / 5;
        if (interestEarned > maxInterestCap) interestEarned = maxInterestCap;
        this.mon += interestEarned;
        return interestEarned;
    }

    // --- HANDS -- DISCARDS -- DECK---

    public void addMaxHands(int amount) { this.baseHands += amount; }
    public void addMaxDiscards(int amount) { this.baseDiscards += amount; }
    public int getBaseHands() { return baseHands; }
    public int getBaseDiscards() { return baseDiscards; }
    public Deck getPlayerDeck() { return playerDeck; }

    // --- OMAMORI ---

    public List<Omamori> getActiveOmamoris() { return activeOmamoris; }

    // ---HANKO

    public List<HankoEffect> getPurchasedHankos() { return purchasedHankos; }


    // --- VOID DUST ---

    public int getVoidDust() { return voidDust; }
    public void addVoidDust(int amount) { this.voidDust += amount; }

    // --- SEASONS ---

    public GameSeason getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(GameSeason season) { this.currentSeason = season; }
    public int getSeasonEncounterStage() { return seasonEncounterStage; }
}
