package com.lychcs.koikoi.run;

import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.model.yokai.YokaiSpecies;

/**
 * Zentrale, unveraenderliche Definition einer Begegnung.
 *
 * <p>Eine Begegnung verbindet eine Yokai-Spezies mit</p>
 * <ul>
 *   <li>einer stabilen Encounter-Id (Inhalts-Id, keine Bildschirmkoordinate),</li>
 *   <li>dem Zielscore des Kampfes,</li>
 *   <li>einer optionalen Welt-/Spawn-Id (Tiled-Objektname oder Trigger-Typ),</li>
 *   <li>der Factory fuer eine frische Gegnerinstanz ({@link #newYokai()}) und</li>
 *   <li>der Factory fuer eine frische Rekrutierungsbelohnung
 *       ({@link #newShikigamiReward()}).</li>
 * </ul>
 *
 * <p>Die Definition enthaelt bewusst keine Scene2D-Actors, Texturen, Screens oder
 * anderen UI-Zustand und niemals geteilte, veraenderliche Instanzen: Gegner und
 * Belohnung entstehen ausschliesslich als frische Objekte.</p>
 */
public final class YokaiEncounter {

    private final String id;
    private final YokaiSpecies species;
    private final double targetScore;
    private final String worldSpawnId;
    private final boolean repeatable;

    /**
     * @param id          stabile Encounter-Id (z. B. "encounter_oni_wild")
     * @param species     Spezies des wilden Yokai
     * @param targetScore Zielscore des Kampfes (&gt; 0)
     * @param worldSpawnId optionale Welt-/Spawn-Id; {@code null}, solange keine
     *                     Weltbindung existiert
     * @param repeatable  true: die Kampfzone bleibt wiederholbar (Erschoepfungs-/
     *                    Fortschrittslogik der Orte bleibt unveraendert)
     */
    public YokaiEncounter(
        String id,
        YokaiSpecies species,
        double targetScore,
        String worldSpawnId,
        boolean repeatable
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (species == null) {
            throw new IllegalArgumentException("species must not be null");
        }
        if (!Double.isFinite(targetScore) || targetScore <= 0.0) {
            throw new IllegalArgumentException("targetScore must be finite and > 0: " + targetScore);
        }

        this.id = id;
        this.species = species;
        this.targetScore = targetScore;
        this.worldSpawnId = worldSpawnId;
        this.repeatable = repeatable;
    }

    public String getId() {
        return id;
    }

    public YokaiSpecies getSpecies() {
        return species;
    }

    /** Stabile Spezies-Id (identisch mit {@code Shikigami.getId()}). */
    public String getSpeciesId() {
        return species.getId();
    }

    /** Anzeigename des wilden Yokai. */
    public String getWildName() {
        return species.getWildName();
    }

    public double getTargetScore() {
        return targetScore;
    }

    /** Optionale Welt-/Spawn-Id (Tiled-Name oder Trigger-Typ) oder {@code null}. */
    public String getWorldSpawnId() {
        return worldSpawnId;
    }

    public boolean hasWorldBinding() {
        return worldSpawnId != null && !worldSpawnId.trim().isEmpty();
    }

    /**
     * true, wenn die Begegnung wiederholt werden darf. Wiederholbare Kampfzonen
     * behalten die bestehende Orts-Fortschrittslogik; einmalige Begegnungen bleiben
     * nach einem Sieg dauerhaft fern.
     */
    public boolean isRepeatable() {
        return repeatable;
    }

    /** Frische Gegnerinstanz dieser Begegnung (niemals eine geteilte Instanz). */
    public Yokai newYokai() {
        return new Yokai(
            species.getId(),
            species.getWildName(),
            species.getWildAtlasRegionName(),
            targetScore
        );
    }

    /** Frische Rekrutierungsbelohnung in der ersten gereinigten Form. */
    public Shikigami newShikigamiReward() {
        return species.newShikigami();
    }

    /** true, wenn diese Spezies im uebergebenen Run bereits rekrutiert wurde. */
    public boolean isRewardGranted(RunSession session) {
        return session != null && session.ownsShikigamiSpecies(getSpeciesId());
    }

    @Override
    public String toString() {
        return id + " (" + getWildName() + " -> " + species.getDisplayName() + ", target " + targetScore + ")";
    }
}
