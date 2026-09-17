package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;

import java.util.Collection;
import java.util.List;

/**
 * Persistenter Zustand eines einzelnen Haendlers innerhalb einer RunSession.
 *
 * <p>Die Angebote werden genau einmal pro Shop erzeugt und bleiben danach
 * unveraendert: beim Schliessen und erneuten Betreten desselben Shops zeigt
 * er exakt dieselben Angebote, gekaufte Slots bleiben ausverkauft. Die Angebote
 * werden zu keinem Zeitpunkt neu gezogen.</p>
 */
public final class ShopState {

    /** Anzahl der Omamori-Angebote eines Shops. */
    public static final int OMAMORI_OFFER_COUNT = 2;

    /** Anzahl der Hanko-Angebote eines Shops. */
    public static final int HANKO_OFFER_COUNT = 3;

    private final String shopId;
    private final List<Omamori> omamoriOffers;
    private final List<HankoEffect> hankoOffers;
    private final boolean[] omamoriSold = new boolean[OMAMORI_OFFER_COUNT];
    private final boolean[] hankoSold = new boolean[HANKO_OFFER_COUNT];

    private ShopState(String shopId, List<Omamori> omamoriOffers, List<HankoEffect> hankoOffers) {
        this.shopId = shopId;
        this.omamoriOffers = List.copyOf(omamoriOffers);
        this.hankoOffers = List.copyOf(hankoOffers);
    }

    /**
     * Erzeugt den Zustand eines Shops und zieht dabei die Angebote genau einmal
     * ueber die bestehenden Pool-/Katalog-Mechanismen.
     *
     * @param shopId            stabile Identitaet des Haendlers
     * @param excludedOmamoris  bereits besessene Omamori (werden nicht angeboten)
     */
    public static ShopState create(String shopId, Collection<Omamori> excludedOmamoris) {
        return new ShopState(
            shopId,
            OmamoriPool.getRandomDistinctOmamoris(OMAMORI_OFFER_COUNT, excludedOmamoris),
            HankoCatalog.drawDistinctOffers(HANKO_OFFER_COUNT)
        );
    }

    public String getShopId() {
        return shopId;
    }

    /** Omamori-Angebote in Slot-Reihenfolge (kann kuerzer als {@link #OMAMORI_OFFER_COUNT} sein). */
    public List<Omamori> getOmamoriOffers() {
        return omamoriOffers;
    }

    /** Hanko-Angebote in Slot-Reihenfolge (kann kuerzer als {@link #HANKO_OFFER_COUNT} sein). */
    public List<HankoEffect> getHankoOffers() {
        return hankoOffers;
    }

    public boolean isOmamoriSold(int slot) {
        return slot >= 0 && slot < omamoriSold.length && omamoriSold[slot];
    }

    public boolean isHankoSold(int slot) {
        return slot >= 0 && slot < hankoSold.length && hankoSold[slot];
    }

    public void markOmamoriSold(int slot) {
        if (slot >= 0 && slot < omamoriSold.length) {
            omamoriSold[slot] = true;
        }
    }

    public void markHankoSold(int slot) {
        if (slot >= 0 && slot < hankoSold.length) {
            hankoSold[slot] = true;
        }
    }
}
