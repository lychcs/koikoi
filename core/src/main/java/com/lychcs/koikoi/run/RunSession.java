package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Oni;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.model.shikigami.ShikigamiStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunSession {

    public static final int BASE_HANDS = 4;
    public static final int BASE_DISCARDS = 3;
    public static final int MAX_ACTIVE_OMAMORIS = 3;

    /** Kämpfe, die in einem Ort absolviert werden. */
    public static final int ENCOUNTERS_PER_LOCATION = 3;

    /** Standard-Haendler, solange es nur einen Shop gibt. */
    public static final String DEFAULT_SHOP_ID = "tengu_shop";

    /** Season des aktuellen Ortes. Wird ausschliesslich ueber die Tiled-Map gesetzt. */
    private GameSeason currentSeason = GameSeason.SPRING;

    /** Id des aktuellen Ortes (aus den Map-Properties). */
    private String currentLocationId = "";

    /** Lokaler Encounter-Fortschritt des aktuellen Ortes. */
    private int locationEncounterStage = 1;

    /** Persistente Shopzustaende je Haendler-Id. */
    private final Map<String, ShopState> shopStates = new LinkedHashMap<>();

    /** Alle im aktuellen Run gekauften Omamori. */
    private final List<Omamori> ownedOmamoris = new ArrayList<>();

    /** Ausgeruestete Omamori. Ihre Reihenfolge ist fuer Yata Mirror relevant. */
    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private int mon = 10000;
    private int voidDust = 5000;

    private final List<Shikigami> shikigamiBag = new ArrayList<>();
    private int maxShikigamiBag = 5;
    private final Deck playerDeck;
    private final List<HankoEffect> purchasedHankos = new ArrayList<>();
    private final YakuProgression yakuProgression = new YakuProgression();

    private final Map<String, float[]> unlockedShrines = new LinkedHashMap<>();
    private String lastVisitedShrineName = "Dorf-Schrein";
    private float lastPlayerX = -1f;
    private float lastPlayerY = -1f;
    private boolean hasStoredPosition = false;
    private int shrineRank = 0;

    public RunSession() {
        playerDeck = new Deck();
        playerDeck.initializeDeck();
        shikigamiBag.add(new Oni(ShikigamiStage.LEVEL_1));
    }

    // ---------------------------------------------------------------------
    // Shrine und Position
    // ---------------------------------------------------------------------

    public void registerAndActivateShrine(String shrineName, float x, float y) {
        unlockedShrines.put(shrineName, new float[]{x, y});
        lastVisitedShrineName = shrineName;
        setLastPlayerPosition(x, y);
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
        shrineRank++;
        System.out.println("Schrein geläutert! Spirituelles Ansehen gestiegen auf Rang: " + shrineRank);
    }

    public boolean hasRequiredRank(int requiredRank) {
        return shrineRank >= requiredRank;
    }

    public void setLastPlayerPosition(float x, float y) {
        lastPlayerX = x;
        lastPlayerY = y;
        hasStoredPosition = true;
    }

    public boolean hasStoredPosition() {
        return hasStoredPosition;
    }

    public float getLastPlayerX() {
        return lastPlayerX;
    }

    public float getLastPlayerY() {
        return lastPlayerY;
    }

    // ---------------------------------------------------------------------
    // Locations und Seasons
    // ---------------------------------------------------------------------

    /**
     * Betritt einen Ort. Die Season wird ausschliesslich durch den geladenen Ort
     * (Tiled-Map) bestimmt. Ein Ortswechsel setzt den lokalen Encounter-Fortschritt
     * auf 1 zurueck, derselbe Ort behaelt seinen Fortschritt.
     */
    public void enterLocation(String locationId, GameSeason season) {
        if (locationId == null) {
            throw new IllegalArgumentException("locationId darf nicht null sein");
        }
        if (season == null) {
            throw new IllegalArgumentException("season darf nicht null sein");
        }

        if (!locationId.equals(currentLocationId)) {
            locationEncounterStage = 1;
        }

        currentLocationId = locationId;
        currentSeason = season;
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public int getLocationEncounterStage() {
        return locationEncounterStage;
    }

    /**
     * Erhoeht ausschliesslich den lokalen Encounter-Fortschritt.
     * Die Season wird hier niemals veraendert.
     */
    public void advanceEncounterStage() {
        locationEncounterStage++;
    }

    // ---------------------------------------------------------------------
    // Shop
    // ---------------------------------------------------------------------

    /**
     * Liefert den persistenten Zustand des Shops. Die Angebote werden beim ersten
     * Aufruf erzeugt und bleiben danach fuer diese RunSession unveraendert.
     * Ein neuer {@code ShopScreen} setzt den Zustand nicht zurueck.
     */
    public ShopState getOrCreateShopState(String shopId) {
        String key = (shopId == null || shopId.trim().isEmpty()) ? DEFAULT_SHOP_ID : shopId;

        ShopState state = shopStates.get(key);
        if (state == null) {
            state = ShopState.create(key, ownedOmamoris);
            shopStates.put(key, state);
        }
        return state;
    }

    // ---------------------------------------------------------------------
    // Omamori-Lager und Ausruestung
    // ---------------------------------------------------------------------

    public List<Omamori> getOwnedOmamoris() {
        return Collections.unmodifiableList(ownedOmamoris);
    }

    public List<Omamori> getActiveOmamoris() {
        return Collections.unmodifiableList(activeOmamoris);
    }

    public int getMaxActiveOmamoris() {
        return MAX_ACTIVE_OMAMORIS;
    }

    public boolean ownsOmamori(Class<? extends Omamori> type) {
        if (type == null) {
            return false;
        }

        for (Omamori omamori : ownedOmamoris) {
            if (omamori.getClass().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean addOwnedOmamori(Omamori omamori) {
        if (omamori == null || ownsOmamori(omamori.getClass())) {
            return false;
        }
        ownedOmamoris.add(omamori);
        return true;
    }

    public boolean isOmamoriActive(Omamori omamori) {
        return activeOmamoris.contains(omamori);
    }

    public boolean equipOmamori(Omamori omamori) {
        if (omamori == null
            || !ownedOmamoris.contains(omamori)
            || activeOmamoris.contains(omamori)
            || activeOmamoris.size() >= MAX_ACTIVE_OMAMORIS) {
            return false;
        }

        activeOmamoris.add(omamori);
        return true;
    }

    public boolean unequipOmamori(Omamori omamori) {
        return activeOmamoris.remove(omamori);
    }

    /**
     * Verschiebt ein aktives Omamori um einen Slot. Das ist besonders fuer
     * Yata Mirror wichtig, weil dessen linker Nachbar ausgewertet wird.
     */
    public boolean moveActiveOmamori(Omamori omamori, int direction) {
        int currentIndex = activeOmamoris.indexOf(omamori);
        if (currentIndex < 0 || direction == 0) {
            return false;
        }

        int newIndex = currentIndex + (direction < 0 ? -1 : 1);
        if (newIndex < 0 || newIndex >= activeOmamoris.size()) {
            return false;
        }

        Collections.swap(activeOmamoris, currentIndex, newIndex);
        return true;
    }

    // ---------------------------------------------------------------------
    // Progression und Deck
    // ---------------------------------------------------------------------

    public YakuProgression getYakuProgression() {
        return yakuProgression;
    }

    public List<Shikigami> getShikigamiBag() {
        return shikigamiBag;
    }

    public int getMaxShikigamiBag() {
        return maxShikigamiBag;
    }

    public Deck getPlayerDeck() {
        return playerDeck;
    }

    public List<HankoEffect> getPurchasedHankos() {
        return purchasedHankos;
    }

    // ---------------------------------------------------------------------
    // Waehrungen
    // ---------------------------------------------------------------------

    public int getMon() {
        return mon;
    }

    public void addMon(int amount) {
        long result = (long) mon + amount;
        mon = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, result));
    }

    public boolean spendMon(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount darf nicht negativ sein");
        }
        if (mon < amount) {
            return false;
        }
        mon -= amount;
        return true;
    }

    public int getVoidDust() {
        return voidDust;
    }

    public void addVoidDust(int amount) {
        long result = (long) voidDust + amount;
        voidDust = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, result));
    }

    // ---------------------------------------------------------------------
    // Kampfwerte und Season
    // ---------------------------------------------------------------------

    public int getBaseHands() {
        return BASE_HANDS;
    }

    public int getBaseDiscards() {
        return BASE_DISCARDS;
    }

    public GameSeason getCurrentSeason() {
        return currentSeason;
    }
}
