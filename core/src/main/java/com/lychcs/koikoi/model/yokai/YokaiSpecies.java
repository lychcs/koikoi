package com.lychcs.koikoi.model.yokai;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.lychcs.koikoi.model.shikigami.Kitsune;
import com.lychcs.koikoi.model.shikigami.Oni;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.model.shikigami.ShikigamiStage;

import java.util.List;
import java.util.function.Supplier;

/**
 * Datengetriebene Definition einer Yokai-Spezies: wilde Form, Anzeigenamen und
 * die Factory ihrer gereinigten Shikigami-Form.
 *
 * <p><b>Visuelle Formen (Konvention):</b></p>
 * <ul>
 *   <li><b>Wilde Form:</b> Atlas-Region {@link #getWildAtlasRegionName()}
 *       (Konvention {@code YOKAI_<FORM>}, z. B. {@code YOKAI_ONI}). Existiert sie
 *       noch nicht, faellt {@link #resolveWildRegion(TextureAtlas)} auf die erste
 *       gereinigte Form zurueck; ist auch die nicht vorhanden, liefert die Methode
 *       {@code null} und der Aufrufer meldet das mit vollstaendigem Regionsnamen.</li>
 *   <li><b>Drei gereinigte Formen:</b> die Regionen liefert ausschliesslich die
 *       jeweilige {@link Shikigami}-Implementierung
 *       ({@code getAtlasRegionName()} je {@link ShikigamiStage}), z. B.
 *       {@code SHIKIGAMI_KO_ONI} / {@code SHIKIGAMI_ONI} / {@code SHIKIGAMI_DAI_ONI}.
 *       Damit gibt es genau eine Quelle und keine doppelten Regionsnamen.</li>
 * </ul>
 *
 * <p><b>Animierte wilde Formen</b> liegen unter {@code assets/entities} im
 * Aseprite-JSON-Format (Beispiel und erster Einsatz:
 * {@code assets/entities/kitsune_wild.png} + {@code .json}). Geladen und geparst wird
 * das ueber {@link com.lychcs.koikoi.graphics.GameAssets} (einmalig, gecacht) mit
 * {@code AsepriteSheetData}/{@code AsepriteSheet}; die vier Lauf-Tags
 * {@code walk_down}, {@code walk_up}, {@code walk_left}, {@code walk_right} sind
 * Pflicht und werden mit Dateipfad gemeldet, wenn sie fehlen. Die Frame-Groesse kommt
 * aus dem JSON (keine feste Annahme); {@code rotated}/{@code trimmed} muessen
 * deaktiviert sein. Eine wilde Form darf entweder als Aseprite-Sheet (bevorzugt,
 * animiert) oder als Atlas-Region vorliegen - nicht als gereinigtes Begleiterbild.</p>
 */
public final class YokaiSpecies {

    /** Stabile Spezies-Id der Oni-Linie (identisch mit {@code Shikigami.getId()}). */
    public static final String ID_ONI = "oni";

    /** Stabile Spezies-Id der Kitsune-Linie (identisch mit der kuenftigen {@code Shikigami.getId()}). */
    public static final String ID_KITSUNE = "kitsune";

    /** Fallback-Region, solange die wilde Form noch nicht als Asset existiert. */
    private static final String ONI_FIRST_PURIFIED_REGION = "SHIKIGAMI_KO_ONI";

    /**
     * Erste gereinigte Form der Kitsune-Linie. Sie dient nur als dokumentierte
     * Regionskonvention samt Fallback; die Region je Stufe liefert
     * {@code Kitsune.getAtlasRegionName()}. Das wilde Aseprite-Sheet
     * ({@code kitsune_wild.png}) ist bewusst kein Begleiterbild.
     */
    private static final String KITSUNE_FIRST_PURIFIED_REGION = "SHIKIGAMI_KO_KITSUNE";

    /** Alle registrierten Spezies. Reihenfolge ist stabil (Inhaltsreihenfolge). */
    private static final List<YokaiSpecies> ALL = List.of(
        new YokaiSpecies(
            ID_ONI,
            "Oni",
            "Wild Oni",
            "YOKAI_ONI",
            ONI_FIRST_PURIFIED_REGION,
            () -> new Oni(ShikigamiStage.LEVEL_1)
        ),
        withWildSheet(
            ID_KITSUNE,
            "Kitsune",
            "Wild Kitsune",
            com.lychcs.koikoi.graphics.GameAssets.KITSUNE_WILD_SHEET,
            com.lychcs.koikoi.graphics.GameAssets.KITSUNE_WILD_FRAMES,
            KITSUNE_FIRST_PURIFIED_REGION,
            () -> new Kitsune(ShikigamiStage.LEVEL_1)
        )
    );

    private final String id;
    private final String displayName;
    private final String wildName;
    private final String wildAtlasRegionName;
    private final String firstPurifiedAtlasRegionName;
    private final String wildSheetTexturePath;
    private final String wildSheetJsonPath;
    private final Supplier<Shikigami> shikigamiFactory;
    private final String rewardBlockerReason;

    public YokaiSpecies(
        String id,
        String displayName,
        String wildName,
        String wildAtlasRegionName,
        String firstPurifiedAtlasRegionName,
        Supplier<Shikigami> shikigamiFactory
    ) {
        this(id, displayName, wildName, wildAtlasRegionName, firstPurifiedAtlasRegionName,
            null, null, shikigamiFactory, null);
    }

    private YokaiSpecies(
        String id,
        String displayName,
        String wildName,
        String wildAtlasRegionName,
        String firstPurifiedAtlasRegionName,
        String wildSheetTexturePath,
        String wildSheetJsonPath,
        Supplier<Shikigami> shikigamiFactory,
        String rewardBlockerReason
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (shikigamiFactory == null && (rewardBlockerReason == null || rewardBlockerReason.trim().isEmpty())) {
            throw new IllegalArgumentException(
                "species '" + id + "' needs either a shikigami factory or a documented blocker reason");
        }

        this.id = id;
        this.displayName = displayName;
        this.wildName = wildName == null ? displayName : wildName;
        this.wildAtlasRegionName = wildAtlasRegionName;
        this.firstPurifiedAtlasRegionName = firstPurifiedAtlasRegionName;
        this.wildSheetTexturePath = wildSheetTexturePath;
        this.wildSheetJsonPath = wildSheetJsonPath;
        this.shikigamiFactory = shikigamiFactory;
        this.rewardBlockerReason = rewardBlockerReason;
    }

    /**
     * Spezies mit animierter wilder Form (Aseprite-Sheet), aber noch fehlender
     * Shikigami-Klasse. Die Rekrutierungsbelohnung ist blockiert und meldet einen
     * dokumentierten Klartextgrund, statt eine falsche Faehigkeit zu erfinden.
     */
    public static YokaiSpecies pendingReward(
        String id,
        String displayName,
        String wildName,
        String wildSheetTexturePath,
        String wildSheetJsonPath,
        String blockerReason
    ) {
        return new YokaiSpecies(id, displayName, wildName, null, null,
            wildSheetTexturePath, wildSheetJsonPath, null, blockerReason);
    }

    /**
     * Spezies mit animierter wilder Form (Aseprite-Sheet) und vorhandener
     * Shikigami-Klasse: die Rekrutierungsbelohnung erzeugt eine frische Instanz in
     * der ersten gereinigten Form (Level 1).
     *
     * @param firstPurifiedAtlasRegionName Konvention/Fallback der ersten gereinigten
     *                                     Form (z. B. {@code SHIKIGAMI_KO_KITSUNE});
     *                                     die Region je Stufe liefert die
     *                                     Shikigami-Klasse selbst
     */
    public static YokaiSpecies withWildSheet(
        String id,
        String displayName,
        String wildName,
        String wildSheetTexturePath,
        String wildSheetJsonPath,
        String firstPurifiedAtlasRegionName,
        Supplier<Shikigami> shikigamiFactory
    ) {
        return new YokaiSpecies(id, displayName, wildName, null, firstPurifiedAtlasRegionName,
            wildSheetTexturePath, wildSheetJsonPath, shikigamiFactory, null);
    }

    /** Alle registrierten Spezies. */
    public static List<YokaiSpecies> all() {
        return ALL;
    }

    /** Spezies zur stabilen Id oder {@code null}, wenn sie nicht registriert ist. */
    public static YokaiSpecies findById(String speciesId) {
        if (speciesId == null) {
            return null;
        }
        for (YokaiSpecies species : ALL) {
            if (species.id.equals(speciesId)) {
                return species;
            }
        }
        return null;
    }

    /** Spezies zur stabilen Id; wirft bei unbekannter Id eine verstaendliche Meldung. */
    public static YokaiSpecies requireById(String speciesId) {
        YokaiSpecies species = findById(speciesId);
        if (species == null) {
            throw new IllegalArgumentException("Unknown Yokai species id: " + speciesId);
        }
        return species;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWildName() {
        return wildName;
    }

    /** Name der Atlas-Region der wilden Form (kann {@code null} sein, bis das Asset existiert). */
    public String getWildAtlasRegionName() {
        return wildAtlasRegionName;
    }

    /** Region der ersten gereinigten Form; dient als Fallback der wilden Form. */
    public String getFirstPurifiedAtlasRegionName() {
        return firstPurifiedAtlasRegionName;
    }

    /** true, wenn die wilde Form als animiertes Aseprite-Sheet vorliegt. */
    public boolean hasWildSheet() {
        return wildSheetJsonPath != null && !wildSheetJsonPath.trim().isEmpty();
    }

    /** Pfad des Spritesheets der wilden Form oder {@code null}. */
    public String getWildSheetTexturePath() {
        return wildSheetTexturePath;
    }

    /** Pfad der Aseprite-Metadaten der wilden Form oder {@code null}. */
    public String getWildSheetJsonPath() {
        return wildSheetJsonPath;
    }

    /** true, wenn eine rekrutierbare Shikigami-Form existiert. */
    public boolean isRewardAvailable() {
        return shikigamiFactory != null;
    }

    /** Klartextgrund der fehlenden Belohnung oder {@code null}, wenn sie verfuegbar ist. */
    public String getRewardBlockerReason() {
        return rewardBlockerReason;
    }

    /**
     * Erzeugt eine frische, noch nicht erschoepfte Belohnungsinstanz in der ersten
     * gereinigten Form (Level 1, 0 XP). Es wird niemals eine geteilte Instanz
     * zurueckgegeben.
     *
     * <p>Erzwingt die entscheidende Invariante fuer den Duplikatschutz: die Id der
     * erzeugten Instanz muss der Spezies-Id entsprechen, sonst koennte dieselbe
     * Spezies mehrfach rekrutiert werden.</p>
     *
     * @throws IllegalStateException wenn die Factory keine oder eine Instanz mit
     *         abweichender Id liefert
     */
    public Shikigami newShikigami() {
        if (shikigamiFactory == null) {
            throw new IllegalStateException("No Shikigami reward available for Yokai species '" + id
                + "': " + rewardBlockerReason);
        }

        Shikigami shikigami = shikigamiFactory.get();
        if (shikigami == null) {
            throw new IllegalStateException("Yokai species '" + id + "' produced no Shikigami instance.");
        }
        if (!id.equals(shikigami.getId())) {
            throw new IllegalStateException("Yokai species '" + id + "' produced a Shikigami with id '"
                + shikigami.getId() + "': the recruitment duplicate check requires identical ids.");
        }
        return shikigami;
    }

    /**
     * Loest die visuelle Region der wilden Form auf: zuerst die wilde Form, danach
     * die erste gereinigte Form. Fehlt beides, wird {@code null} geliefert (kein Crash);
     * der Aufrufer meldet den vollstaendigen Regionsnamen.
     */
    public TextureRegion resolveWildRegion(TextureAtlas atlas) {
        if (atlas == null) {
            return null;
        }
        if (wildAtlasRegionName != null) {
            TextureRegion wild = atlas.findRegion(wildAtlasRegionName);
            if (wild != null) {
                return wild;
            }
        }
        if (firstPurifiedAtlasRegionName != null) {
            return atlas.findRegion(firstPurifiedAtlasRegionName);
        }
        return null;
    }
}
