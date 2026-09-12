package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.fuku.FukuContext;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunSession implements FukuContext {

    private GameSeason currentSeason = GameSeason.SPRING; // Start immer im Frühling
    private int seasonEncounterStage = 1; // 1: Beast-Wahl, 2: Licht/Dunkel-Evo-Kampf, 3: Korrumpierter Kami-Boss

    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private int mon = 1000;
    private int voidDust = 0;
    private int maxInterestCap = 5;

    private int baseHands = 4;
    private int baseDiscards = 3;

    private final List<Yokai> yokaiBag = new ArrayList<>();
    private int maxYokaiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();

    private boolean shrineSummonedThisVisit = false;
    private final Set<Yokai> shrineEvolvedThisVisit = new HashSet<>();

    public RunSession() {
        resetShrineVisit();
        this.playerDeck = new Deck();
        this.playerDeck.initializeDeck();
    }

    // Zins-Berechnung am Ende der Runde
    public int applyEndRoundInterest() {
        int interestEarned = this.mon / 5;

        if (interestEarned > maxInterestCap) {
            interestEarned = maxInterestCap;
        }

        this.mon += interestEarned;
        return interestEarned;
    }

    // Shrines
    public boolean isShrineSummonedThisVisit() { return shrineSummonedThisVisit; }
    public void setShrineSummonedThisVisit(boolean summoned) { this.shrineSummonedThisVisit = summoned; }
    public Set<Yokai> getShrineEvolvedThisVisit() { return shrineEvolvedThisVisit; }

    public void resetShrineVisit() {
        this.shrineSummonedThisVisit = false;
        this.shrineEvolvedThisVisit.clear();
    }

    //Seasons
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
            case WINTER -> GameSeason.WINTER; // Hier später in den Final Boss State wechseln!
        };
    }
    // --- Fuku Context Implementierung ---
    @Override
    public int getMon() { return this.mon; }

    @Override
    public void addMon(int amount) { this.mon += amount; }

    @Override
    public void addMaxHands(int amount) { this.baseHands += amount; }

    @Override
    public void addMaxDiscards(int amount) { this.baseDiscards += amount; }

    @Override
    public void addMaxInterestCap(int amount) { this.maxInterestCap += amount; }

    // --- Getter & Setter ---
    public int getBaseHands() { return baseHands; }
    public int getBaseDiscards() { return baseDiscards; }
    public int getMaxInterestCap() { return maxInterestCap; }
    public Deck getPlayerDeck() {
        return playerDeck;
    }
    public List<HankoEffect> getPurchasedHankos() {
        return purchasedHankos;
    }
    public List<Omamori> getActiveOmamoris() { return activeOmamoris; }
    public int getVoidDust() { return voidDust; }
    public void addVoidDust(int amount) { this.voidDust += amount; }
    public List<Yokai> getYokaiBag() { return yokaiBag; }
    public int getMaxYokaiBag() { return maxYokaiBag; }
    public void addMaxYokaiBag(int amount) { this.maxYokaiBag += amount; }
    public GameSeason getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(GameSeason season) { this.currentSeason = season; }
    public int getSeasonEncounterStage() { return seasonEncounterStage; }
}
