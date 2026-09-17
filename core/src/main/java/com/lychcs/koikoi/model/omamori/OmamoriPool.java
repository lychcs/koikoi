package com.lychcs.koikoi.model.omamori;

import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class OmamoriPool {

    private record OmamoriEntry(
        Supplier<Omamori> factory,
        Class<? extends Omamori> type,
        Rarity rarity
    ) {}

    private static final List<OmamoriEntry> ENTRIES = List.of(
        new OmamoriEntry(DarumaDoll::new, DarumaDoll.class, Rarity.COMMON),
        new OmamoriEntry(Furin::new, Furin.class, Rarity.UNCOMMON),
        new OmamoriEntry(HanamiDango::new, HanamiDango.class, Rarity.COMMON),
        new OmamoriEntry(InoshishiTusk::new, InoshishiTusk.class, Rarity.UNCOMMON),
        new OmamoriEntry(JohariMirror::new, JohariMirror.class, Rarity.LEGENDARY),
        new OmamoriEntry(KintsugiBowl::new, KintsugiBowl.class, Rarity.RARE),
        new OmamoriEntry(KitsuneMask::new, KitsuneMask.class, Rarity.COMMON),
        new OmamoriEntry(Koinobori::new, Koinobori.class, Rarity.UNCOMMON),
        new OmamoriEntry(Kuroneko::new, Kuroneko.class, Rarity.COMMON),
        new OmamoriEntry(MatchaWhisk::new, MatchaWhisk.class, Rarity.COMMON),
        new OmamoriEntry(SakeCup::new, SakeCup.class, Rarity.COMMON),
        new OmamoriEntry(TenguFeather::new, TenguFeather.class, Rarity.COMMON),
        new OmamoriEntry(TeruTeruBozu::new, TeruTeruBozu.class, Rarity.UNCOMMON),
        new OmamoriEntry(ToriiGate::new, ToriiGate.class, Rarity.UNCOMMON),
        new OmamoriEntry(YataMirror::new, YataMirror.class, Rarity.EPIC)
    );

    private OmamoriPool() {}

    public static Omamori getRandomOmamori() {
        List<Omamori> result = getRandomDistinctOmamoris(1, List.of());
        if (result.isEmpty()) {
            throw new IllegalStateException("Der Omamori-Pool ist leer.");
        }
        return result.get(0);
    }

    /**
     * Zieht gewichtete, untereinander verschiedene Omamori und schliesst
     * bereits besessene Klassen aus. Jede Rueckgabe ist eine neue Instanz.
     */
    public static List<Omamori> getRandomDistinctOmamoris(
        int count,
        Collection<Omamori> excludedOmamoris
    ) {
        if (count <= 0) {
            return List.of();
        }

        Set<Class<? extends Omamori>> excludedTypes = new HashSet<>();
        if (excludedOmamoris != null) {
            for (Omamori omamori : excludedOmamoris) {
                if (omamori != null) {
                    excludedTypes.add(omamori.getClass());
                }
            }
        }

        List<OmamoriEntry> candidates = new ArrayList<>();
        for (OmamoriEntry entry : ENTRIES) {
            if (!excludedTypes.contains(entry.type())) {
                candidates.add(entry);
            }
        }

        List<Omamori> result = new ArrayList<>();
        while (!candidates.isEmpty() && result.size() < count) {
            OmamoriEntry selected = drawWeightedEntry(candidates);
            result.add(selected.factory().get());
            candidates.remove(selected);
        }

        return List.copyOf(result);
    }

    private static OmamoriEntry drawWeightedEntry(List<OmamoriEntry> candidates) {
        int totalWeight = 0;
        for (OmamoriEntry entry : candidates) {
            totalWeight += getWeight(entry.rarity());
        }

        int random = MathUtils.random(totalWeight - 1);
        int current = 0;

        for (OmamoriEntry entry : candidates) {
            current += getWeight(entry.rarity());
            if (random < current) {
                return entry;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private static int getWeight(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 550;
            case UNCOMMON -> 300;
            case RARE -> 100;
            case EPIC -> 40;
            case LEGENDARY -> 10;
        };
    }

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
