package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.yokai.YokaiSpecies;

import java.util.List;

/**
 * Zentrale Registrierung aller Begegnungen.
 *
 * <p>Lookup-Schluessel sind die stabile Encounter-Id und die optionale Welt-/Spawn-Id.
 * Die Spawn-Id ist bewusst inhaltsbasiert (Tiled-Objektname oder Trigger-Typ) und
 * niemals eine Bildschirmkoordinate.</p>
 */
public final class YokaiEncounterCatalog {

    /**
     * Zielscore einer Begegnung ohne eigene Definition. Entspricht exakt dem
     * bisherigen festen Kampfziel, damit sich das Kampfverhalten nicht aendert.
     */
    public static final double DEFAULT_TARGET_SCORE = 200.0;

    /**
     * Wilde Oni-Begegnung als Fallback fuer Kampfzonen ohne eigene Definition.
     *
     * <p>Seit Phase 4.1 hat sie <b>keine</b> Weltbindung mehr: die Trigger-Typ-Id
     * {@code enemy1} dient jetzt als Spawnmarkierung des wilden Kitsune. Diese
     * Begegnung bleibt <b>wiederholbar</b>, damit der bestehende Fortschritt von
     * {@link RunSession#ENCOUNTERS_PER_LOCATION} Kaempfen pro Ort erhalten bleibt;
     * die Rekrutierungsbelohnung wird trotzdem nur genau einmal vergeben.</p>
     */
    private static final YokaiEncounter ONI_WILD = new YokaiEncounter(
        "encounter_oni_wild",
        YokaiSpecies.requireById(YokaiSpecies.ID_ONI),
        DEFAULT_TARGET_SCORE,
        null,
        true
    );

    /**
     * Wildes Kitsune des benannten Spawns in der Testmap.
     *
     * <p>Die Encounter-Id ist stabil und inhaltsbasiert; die Weltbindung erfolgt
     * ueber den Objektnamen {@code kitsune_spawn_01}. Die Begegnung ist
     * <b>nicht</b> wiederholbar: nach einem Sieg verschwindet dieses Kitsune fuer
     * den restlichen Run.</p>
     */
    private static final YokaiEncounter KITSUNE_SPRING_VILLAGE = new YokaiEncounter(
        "spring_village_kitsune_01",
        YokaiSpecies.requireById(YokaiSpecies.ID_KITSUNE),
        DEFAULT_TARGET_SCORE,
        "kitsune_spawn_01",
        false
    );

    private static final List<YokaiEncounter> ALL = List.of(ONI_WILD, KITSUNE_SPRING_VILLAGE);

    private YokaiEncounterCatalog() {}

    /** Alle registrierten Begegnungen (stabile Reihenfolge). */
    public static List<YokaiEncounter> all() {
        return ALL;
    }

    /** Begegnung zur stabilen Encounter-Id oder {@code null}. */
    public static YokaiEncounter findById(String encounterId) {
        if (encounterId == null) {
            return null;
        }
        for (YokaiEncounter encounter : ALL) {
            if (encounter.getId().equals(encounterId)) {
                return encounter;
            }
        }
        return null;
    }

    /**
     * Begegnung zur Welt-/Spawn-Id oder {@code null}, wenn die Zone keine eigene
     * Definition hat. Die Zuordnung erfolgt ohne Positionsvergleich.
     */
    public static YokaiEncounter findBySpawnId(String worldSpawnId) {
        if (worldSpawnId == null) {
            return null;
        }
        for (YokaiEncounter encounter : ALL) {
            if (encounter.hasWorldBinding() && encounter.getWorldSpawnId().equals(worldSpawnId)) {
                return encounter;
            }
        }
        return null;
    }

    /**
     * Fallback fuer Kampfzonen ohne eigene Begegnung: verhaelt sich wie bisher
     * (Standard-Zielscore) und vergibt die Oni-Belohnung genau einmal.
     */
    public static YokaiEncounter defaultEncounter() {
        return ONI_WILD;
    }
}
