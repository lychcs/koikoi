package com.lychcs.koikoi.model.yokai;

/**
 * Wilde, feindliche Kreatur einer Begegnung.
 *
 * <p>Ein {@code Yokai} ist bewusst <b>kein</b> Begleiter: es ist der Gegner einer
 * Begegnung und wird pro Kampf frisch erzeugt
 * ({@link com.lychcs.koikoi.run.YokaiEncounter#newYokai()}). Gereinigte, rekrutierte
 * Begleiter sind {@link com.lychcs.koikoi.model.shikigami.Shikigami}-Instanzen.</p>
 *
 * <p>Unveraenderlich und frei von UI-Zustand: keine Scene2D-Actors, keine Texturen,
 * keine Screens. Die optionale Atlas-Region nennt nur den Namen der wilden Form;
 * sie kann {@code null} sein, solange die wilde Grafik noch nicht existiert.</p>
 */
public final class Yokai {

    private final String speciesId;
    private final String displayName;
    private final String atlasRegionName;
    private final double targetScore;

    /**
     * @param speciesId      stabile Spezies-Id (entspricht {@code Shikigami.getId()})
     * @param displayName    Anzeigename der wilden Form (z. B. "Wild Oni")
     * @param atlasRegionName optionale Atlas-Region der wilden Form, darf {@code null} sein
     * @param targetScore    Zielscore des Kampfes gegen dieses Yokai (&gt; 0)
     */
    public Yokai(String speciesId, String displayName, String atlasRegionName, double targetScore) {
        if (speciesId == null || speciesId.trim().isEmpty()) {
            throw new IllegalArgumentException("speciesId must not be blank");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (!Double.isFinite(targetScore) || targetScore <= 0.0) {
            throw new IllegalArgumentException("targetScore must be finite and > 0: " + targetScore);
        }

        this.speciesId = speciesId;
        this.displayName = displayName;
        this.atlasRegionName = atlasRegionName;
        this.targetScore = targetScore;
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Name der Atlas-Region der wilden Form oder {@code null}, wenn noch keine existiert. */
    public String getAtlasRegionName() {
        return atlasRegionName;
    }

    public double getTargetScore() {
        return targetScore;
    }

    public boolean hasVisual() {
        return atlasRegionName != null && !atlasRegionName.isEmpty();
    }

    @Override
    public String toString() {
        return displayName + " (" + speciesId + ", target " + targetScore + ")";
    }
}
