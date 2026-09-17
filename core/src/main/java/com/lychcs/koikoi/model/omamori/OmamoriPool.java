package com.lychcs.koikoi.model.omamori;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;
import java.util.function.Supplier;

public class OmamoriPool {

    private record OmamoriEntry(Supplier<Omamori> factory, Rarity rarity) {}

    // Registrierung aller Omamoris als Constructor-Factorys
    private static final List<OmamoriEntry> ENTRIES = List.of(
        new OmamoriEntry(DarumaDoll::new, Rarity.COMMON),
        new OmamoriEntry(Furin::new, Rarity.UNCOMMON),
        new OmamoriEntry(HanamiDango::new, Rarity.COMMON),
        new OmamoriEntry(InoshishiTusk::new, Rarity.UNCOMMON),
        new OmamoriEntry(JohariMirror::new, Rarity.LEGENDARY),
        new OmamoriEntry(KintsugiBowl::new, Rarity.RARE),
        new OmamoriEntry(KitsuneMask::new, Rarity.COMMON),
        new OmamoriEntry(Koinobori::new, Rarity.UNCOMMON),
        new OmamoriEntry(Kuroneko::new, Rarity.COMMON),
        new OmamoriEntry(MatchaWhisk::new, Rarity.COMMON),
        new OmamoriEntry(SakeCup::new, Rarity.COMMON),
        new OmamoriEntry(TenguFeather::new, Rarity.COMMON),
        new OmamoriEntry(TeruTeruBozu::new, Rarity.UNCOMMON),
        new OmamoriEntry(ToriiGate::new, Rarity.UNCOMMON),
        new OmamoriEntry(YataMirror::new, Rarity.EPIC)
    );

    /**
     * Zieht ein zufälliges Omamori basierend auf der Seltenheit als neue Instanz.
     */
    public static Omamori getRandomOmamori() {
        int totalWeight = 0;
        for (OmamoriEntry entry : ENTRIES) {
            totalWeight += getWeight(entry.rarity());
        }

        int random = MathUtils.random(0, totalWeight - 1);
        int current = 0;

        for (OmamoriEntry entry : ENTRIES) {
            current += getWeight(entry.rarity());
            if (random < current) {
                return entry.factory().get();
            }
        }
        return ENTRIES.get(0).factory().get(); // Fallback als neue Instanz
    }

    /**
     * Die "Drop-Chance". Höher = Taucht öfter auf.
     */
    private static int getWeight(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 550;    // 55.0% Chance
            case UNCOMMON -> 300;  // 30.0% Chance
            case RARE -> 100;      // 10.0% Chance
            case EPIC -> 40;       //  4.0% Chance
            case LEGENDARY -> 10;  //  1.0% Chance
        };
    }

    /**
     * Der Preis im Shop, abhängig von der Seltenheit.
     */
    public static int getCost(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 100;
            case UNCOMMON -> 200;
            case RARE -> 350;
            case EPIC -> 600;
            case LEGENDARY -> 1000;
        };
    }
}
