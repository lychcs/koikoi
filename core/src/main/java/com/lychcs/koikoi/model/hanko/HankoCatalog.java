package com.lychcs.koikoi.model.hanko;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Zentrale Quelle fuer Hanko-Instanzen und Shop-Preise.
 */
public final class HankoCatalog {

    private record Entry(Supplier<Hanko> factory, int price) {}

    private static final Map<HankoEffect, Entry> ENTRIES = new EnumMap<>(HankoEffect.class);

    static {
        ENTRIES.put(HankoEffect.WHITE_SEAL, new Entry(WhiteSeal::new, 100));
        ENTRIES.put(HankoEffect.BLACK_SEAL, new Entry(BlackSeal::new, 100));
        ENTRIES.put(HankoEffect.STONE_SEAL, new Entry(StoneSeal::new, 150));
        ENTRIES.put(HankoEffect.GOLDEN_SEAL, new Entry(GoldenSeal::new, 200));
        ENTRIES.put(HankoEffect.VOID_SEAL, new Entry(VoidSeal::new, 200));
        ENTRIES.put(HankoEffect.POLYCHROME_SEAL, new Entry(PolychromeSeal::new, 350));
        ENTRIES.put(HankoEffect.BLOOD_SEAL, new Entry(BloodSeal::new, 350));
    }

    private HankoCatalog() {}

    public static Hanko create(HankoEffect effect) {
        Entry entry = ENTRIES.get(effect);
        if (entry == null) {
            throw new IllegalArgumentException("Kein kaufbares Hanko fuer Effekt: " + effect);
        }
        return entry.factory().get();
    }

    public static String getName(HankoEffect effect) {
        return create(effect).getName();
    }

    public static String getDescription(HankoEffect effect) {
        return create(effect).getDescription();
    }

    public static int getPrice(HankoEffect effect) {
        Entry entry = ENTRIES.get(effect);
        if (entry == null) {
            throw new IllegalArgumentException("Kein Shop-Preis fuer Effekt: " + effect);
        }
        return entry.price();
    }

    /**
     * Name der Atlas-Region fuer das Abzeichen dieses Hankos.
     *
     * @return Regionsname oder {@code null}, wenn der Effekt kein Abzeichen besitzt.
     */
    public static String getAtlasRegionName(HankoEffect effect) {
        if (effect == null || effect == HankoEffect.NONE) {
            return null;
        }
        if (effect == HankoEffect.GOLDEN_SEAL) {
            // Die Atlas-Region des Golden Seal heisst HANKO_GOLD_SEAL.
            return "HANKO_GOLD_SEAL";
        }
        return "HANKO_" + effect.name();
    }

    public static List<HankoEffect> drawDistinctOffers(int count) {
        if (count <= 0) {
            return List.of();
        }

        List<HankoEffect> candidates = new ArrayList<>(ENTRIES.keySet());
        Collections.shuffle(candidates);
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }
}
