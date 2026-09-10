package com.lychcs.koikoi.model.omamori;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;

public class OmamoriPool {

    // Alle Omamoris, die im Spiel existieren, werden hier 1x registriert
    private static final List<Omamori> ALL_OMAMORIS = List.of(
        new DarumaDoll(), new Furin(), new HanamiDango(), new InoshishiTusk(),
        new JohariMirror(), new KintsugiBowl(), new KitsuneMask(), new Koinobori(),
        new Kuroneko(), new MatchaWhisk(), new SakeCup(), new TenguFeather(),
        new TeruTeruBozu(), new ToriiGate(), new YataMirror()
    );

    /**
     * Zieht ein zufälliges Omamori basierend auf der Seltenheit.
     */
    public static Omamori getRandomOmamori() {
        int totalWeight = 0;
        for (Omamori o : ALL_OMAMORIS) {
            totalWeight += getWeight(o.getRarity());
        }

        int random = MathUtils.random(0, totalWeight - 1);
        int current = 0;

        for (Omamori o : ALL_OMAMORIS) {
            current += getWeight(o.getRarity());
            if (random < current) {
                return o;
            }
        }
        return ALL_OMAMORIS.get(0); // Fallback
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
