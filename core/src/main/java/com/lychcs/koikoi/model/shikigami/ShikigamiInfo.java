package com.lychcs.koikoi.model.shikigami;

import com.lychcs.koikoi.model.yokai.YokaiSpecies;

/**
 * Reine Formatierung aller Shikigami-Anzeigen.
 *
 * <p>Beide UIs (Inventar und Kampf-Infopanel) lesen dieselben Texte aus dieser
 * Klasse: keine zweite XP-, Level- oder Erschoepfungslogik in der UI und keine
 * doppelten Faehigkeitstexte. Alle Werte stammen ausschliesslich aus dem
 * {@link Shikigami}-Modell.</p>
 */
public final class ShikigamiInfo {

    private ShikigamiInfo() {}

    /** Anzeigename der Spezies (aus dem Spezies-Katalog; Fallback: technische Id). */
    public static String speciesName(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        YokaiSpecies species = YokaiSpecies.findById(shikigami.getId());
        return species == null ? shikigami.getId() : species.getDisplayName();
    }

    /** Anzeigename der aktuellen Entwicklungsform. */
    public static String stageName(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        switch (shikigami.getStage()) {
            case EGG: return "Egg";
            case LEVEL_1: return "Level 1";
            case LEVEL_2: return "Level 2";
            case LEVEL_3: return "Level 3 (Final Form)";
            default: return shikigami.getStage().name();
        }
    }

    /** "Ready" oder "Exhausted". */
    public static String statusName(Shikigami shikigami) {
        return shikigami != null && shikigami.isExhausted() ? "Exhausted" : "Ready";
    }

    /** XP-Zeile: "XP 30 / 65" oder "XP 0 (max level)". */
    public static String xpLine(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        int required = shikigami.getXpToNextLevel();
        if (required <= 0) {
            return "XP " + shikigami.getCurrentXp() + " (max level)";
        }
        return "XP " + shikigami.getCurrentXp() + " / " + required;
    }

    /** Vollstaendiger Faehigkeitstext (Zahlen und Bedingungen stehen im Modell). */
    public static String abilityLine(Shikigami shikigami) {
        return shikigami == null ? "-" : shikigami.getDescription();
    }

    /** Kompakte Zeile fuer das Kampf-Infopanel: "Level 1 | XP 30 / 65 | Ready". */
    public static String battleSummary(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        return "Level " + shikigami.getLevel() + " | " + xpLine(shikigami) + " | " + statusName(shikigami);
    }

    /** Kopfzeile des Kampf-Infopanels: "Ko-Oni (Oni)". */
    public static String battleTitle(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        return shikigami.getName() + " (" + speciesName(shikigami) + ")";
    }

    /** Typzeile des Inventardetails: "Level 1 | LEVEL 1 | READY". */
    public static String inventoryTypeLine(Shikigami shikigami) {
        if (shikigami == null) {
            return "-";
        }
        return stageName(shikigami) + " | LEVEL " + shikigami.getLevel()
            + " | " + statusName(shikigami).toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Klartextgrund, warum ein Shikigami aktuell nicht im Altar einsetzbar ist,
     * oder {@code null}, wenn es einsetzbar ist.
     *
     * @param reserve true, wenn der Eintrag in der Reserve liegt (nicht im aktiven Beutel)
     */
    public static String unusableReason(Shikigami shikigami, boolean reserve) {
        if (shikigami == null) {
            return null;
        }
        if (reserve) {
            return "In reserve: move it to the bag before it can be used in battle.";
        }
        if (shikigami.isExhausted()) {
            return "Exhausted: rest at a Shrine to recover.";
        }
        return null;
    }
}
