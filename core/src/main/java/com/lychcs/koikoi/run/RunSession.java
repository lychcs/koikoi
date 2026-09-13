package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.fuku.FukuContext;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai;

import java.util.*;

public class RunSession implements FukuContext {

    private GameSeason currentSeason = GameSeason.SPRING;
    private int seasonEncounterStage = 1; // 1: Beast-Wahl, 2: Licht/Dunkel, 3: Kami-Boss

    // Tracking der Pfad-Entscheidungen
    private CardID activeBeastChoice = null;
    private Rank activeAlignmentChoice = null;

    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private int mon = 0; // Zurückgesetzt auf echtes Startkapital
    private int voidDust = 0;
    private int maxInterestCap = 5;

    private int baseHands = 4;
    private int baseDiscards = 3;

    private final List<Yokai> yokaiBag = new ArrayList<>();
    private int maxYokaiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();

    private final List<Card> banishedThisSeason = new ArrayList<>();

    private boolean shrineSummonedThisVisit = false;
    private final Set<Yokai> shrineEvolvedThisVisit = new HashSet<>();

    public RunSession() {
        resetShrineVisit();
        this.playerDeck = new Deck();
        this.playerDeck.initializeDeck();
    }

    public int applyEndRoundInterest() {
        int interestEarned = this.mon / 5;
        if (interestEarned > maxInterestCap) interestEarned = maxInterestCap;
        this.mon += interestEarned;
        return interestEarned;
    }

    public boolean isShrineSummonedThisVisit() { return shrineSummonedThisVisit; }
    public void setShrineSummonedThisVisit(boolean summoned) { this.shrineSummonedThisVisit = summoned; }
    public Set<Yokai> getShrineEvolvedThisVisit() { return shrineEvolvedThisVisit; }

    public void resetShrineVisit() {
        this.shrineSummonedThisVisit = false;
        this.shrineEvolvedThisVisit.clear();
    }

    // --- LEVEL STRUKTUR & PATHING ---

    public void advanceEncounterStage() {
        seasonEncounterStage++;
        if (seasonEncounterStage > 3) {
            seasonEncounterStage = 1;
            activeBeastChoice = null;     // Reset für die neue Season
            activeAlignmentChoice = null; // Reset für die neue Season
            advanceSeason();
        }
    }

    private void advanceSeason() {
        restoreSeasonBanishedCards();
        currentSeason = switch (currentSeason) {
            case SPRING -> GameSeason.SUMMER;
            case SUMMER -> GameSeason.AUTUMN;
            case AUTUMN -> GameSeason.WINTER;
            case WINTER -> GameSeason.FINAL; // Boss Stage!
            case FINAL -> GameSeason.FINAL;
        };
    }

    public void setBeastChoice(CardID beastID) {
        this.activeBeastChoice = beastID;
    }

    public void setAlignmentChoiceAndEvolve(Rank alignment) {
        this.activeAlignmentChoice = alignment;

        if (activeBeastChoice == null) return;
        CardID evolvedId = EvolutionMapper.getEvolvedForm(activeBeastChoice, alignment);

        // Finde das Basis-Beast im Deck und werte es auf
        for (int i = 0; i < playerDeck.getCards().size(); i++) {
            Card c = playerDeck.getCards().get(i);
            if (c.id() == activeBeastChoice) {
                Card evolvedCard = new Card(evolvedId, c.season(), alignment, c.name() + " (Erwacht)", c.effect());
                playerDeck.getCards().set(i, evolvedCard);
                break;
            }
        }
    }

    public boolean needsBeastChoice() { return activeBeastChoice == null; }
    public boolean needsAlignmentChoice() { return activeAlignmentChoice == null; }

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

    @Override public int getMon() { return this.mon; }
    @Override public void addMon(int amount) { this.mon += amount; }
    @Override public void addMaxHands(int amount) { this.baseHands += amount; }
    @Override public void addMaxDiscards(int amount) { this.baseDiscards += amount; }
    @Override public void addMaxInterestCap(int amount) { this.maxInterestCap += amount; }

    public int getBaseHands() { return baseHands; }
    public int getBaseDiscards() { return baseDiscards; }
    public int getMaxInterestCap() { return maxInterestCap; }
    public Deck getPlayerDeck() { return playerDeck; }
    public List<HankoEffect> getPurchasedHankos() { return purchasedHankos; }
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
