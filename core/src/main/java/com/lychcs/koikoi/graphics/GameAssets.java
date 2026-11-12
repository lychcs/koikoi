package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.HashMap;
import java.util.Map;

/**
 * Zentrale Verwaltung aller Dateiassets, die von mehr als einem Screen
 * benoetigt werden: UI-Skin, gemeinsamer Spielatlas, Button-/Panel-Texturen und
 * das Spieler-Sprite-Sheet.
 *
 * <p><b>Eigentum:</b> {@link com.lychcs.koikoi.KoiKoiGame} erzeugt genau eine
 * Instanz, laedt sie einmalig in {@code create()} und gibt sie in
 * {@code dispose()} genau einmal frei. Screens erhalten die Instanz per
 * Konstruktor und behandeln alles, was sie hier abholen, als <i>geliehen</i>:
 * kein Screen darf ein zurueckgegebenes {@link Texture}, {@link TextureAtlas},
 * {@link Skin} oder {@link Model} selbst disposen, aus dem Manager entfernen
 * oder {@code clear()} beziehungsweise {@link #dispose()} aufrufen.</p>
 *
 * <p>Alle Methoden laufen ausschliesslich auf dem LibGDX-Render-Thread. Es gibt
 * bewusst keinen statischen Zugriff und keine versteckte Lebensdauer.</p>
 *
 * <p>Screen-lokale Ressourcen (ortsabhaengige Hintergruende, Stage, Batches,
 * FrameBuffer, Shader, Physik) bleiben bewusst ausserhalb dieses Managers. Die
 * gemeinsam genutzten Nature-OBJs werden dagegen genau wie Texturen hier
 * geladen und von Screens nur geliehen.</p>
 */
public final class GameAssets implements Disposable {

    // ------------------------------------------------------------------
    // Zentrale Pfade aller Dateiassets des Spiels
    // ------------------------------------------------------------------

    /** Gemeinsame UI-Skin (von allen Screens verwendet). */
    public static final String UI_SKIN = "uiskin.json";

    /** Gemeinsamer Atlas fuer Karten, Hankos, Omamori und Shikigami. */
    public static final String GAME_ATLAS = "packed/game_assets.atlas";

    /** Gemeinsame Button-Quelltextur fuer alle NinePatch-Buttons. */
    public static final String BUTTON_TEXTURE = "backgrounds/BUTTONS_PLAYING_BOARD.9.png";

    /** Gemeinsame Panel-Quelltextur fuer alle NinePatch-Panels. */
    public static final String PANEL_TEXTURE = "backgrounds/PANEL_PLAYING_BOARD.9.png";

    /** Spritesheet der wilden Kitsune-Form (Aseprite-Export). */
    public static final String KITSUNE_WILD_SHEET = "entities/kitsune_wild.png";

    /** Aseprite-Metadaten der wilden Kitsune-Form (Frames und Animation-Tags). */
    public static final String KITSUNE_WILD_FRAMES = "entities/kitsune_wild.json";

    /** Basisverzeichnis der sechs nahtlosen Welttexturen. */
    public static final String WORLD_TEXTURE_DIR = "textures/";

    /** Top-down mossy forest grass with sakura petals (subtle flat terrain). */
    public static final String WORLD_GRASS_TEXTURE = WORLD_TEXTURE_DIR + "grass.png";
    /** Dark slate cliff rock for steep terrain walls. */
    public static final String WORLD_CLIFF_TEXTURE = WORLD_TEXTURE_DIR + "cliff_stone.png";
    /** Painted vermillion cedar grain for torii beams, posts and shrine wood. */
    public static final String WORLD_TORII_TEXTURE = WORLD_TEXTURE_DIR + "torii_wood.png";
    /** Kawara roof tiles of the temple. */
    public static final String WORLD_ROOF_TEXTURE = WORLD_TEXTURE_DIR + "roof_tiles.png";
    /** Mossy cobblestone shrine pathway. */
    public static final String WORLD_PATH_TEXTURE = WORLD_TEXTURE_DIR + "stone_path.png";
    /** Dense cherry-blossom canopy foliage. */
    public static final String WORLD_SAKURA_TEXTURE = WORLD_TEXTURE_DIR + "sakura_leaves.png";

    /** Material order shared verbatim with {@code ascii_world.frag}. */
    public static final String[] WORLD_TEXTURES = {
        WORLD_GRASS_TEXTURE,
        WORLD_CLIFF_TEXTURE,
        WORLD_TORII_TEXTURE,
        WORLD_ROOF_TEXTURE,
        WORLD_PATH_TEXTURE,
        WORLD_SAKURA_TEXTURE
    };

    /** Imported low-poly nature models (CC0, Ultimate Stylized Nature pack). */
    public static final String NATURE_MODEL_DIR = "models/nature/";

    /** Sakura maple with an alpha-cutout leaf canopy. */
    public static final String NATURE_MAPLE_TREE = NATURE_MODEL_DIR + "MapleTree_4.obj";
    /** Tall evergreen pine for grove silhouettes. */
    public static final String NATURE_PINE_TREE_TALL = NATURE_MODEL_DIR + "PineTree_1.obj";
    /** Squat pine shrub/tree. */
    public static final String NATURE_PINE_TREE_SMALL = NATURE_MODEL_DIR + "PineTree_3.obj";
    /** Rounded mossy boulder. */
    public static final String NATURE_ROCK_ROUND = NATURE_MODEL_DIR + "Rock_1.obj";
    /** Tall weathered boulder. */
    public static final String NATURE_ROCK_TALL = NATURE_MODEL_DIR + "Rock_3.obj";
    /** Compact alpha-cutout bush. */
    public static final String NATURE_BUSH_SMALL = NATURE_MODEL_DIR + "Bush_Small.obj";
    /** Wide alpha-cutout bush. */
    public static final String NATURE_BUSH_LARGE = NATURE_MODEL_DIR + "Bush_Large.obj";
    /** Short grass tuft. */
    public static final String NATURE_GRASS_SMALL = NATURE_MODEL_DIR + "Grass_Small.obj";
    /** Tall grass tuft. */
    public static final String NATURE_GRASS_LARGE = NATURE_MODEL_DIR + "Grass_Large_Extruded.obj";
    /** Small alpha-cutout flower clump. */
    public static final String NATURE_FLOWER_CLUMP = NATURE_MODEL_DIR + "Flower_1_Clump.obj";

    /** Every model loaded into the shared {@link AssetManager}. */
    public static final String[] NATURE_MODELS = {
        NATURE_MAPLE_TREE,
        NATURE_PINE_TREE_TALL,
        NATURE_PINE_TREE_SMALL,
        NATURE_ROCK_ROUND,
        NATURE_ROCK_TALL,
        NATURE_BUSH_SMALL,
        NATURE_BUSH_LARGE,
        NATURE_GRASS_SMALL,
        NATURE_GRASS_LARGE,
        NATURE_FLOWER_CLUMP
    };

    /** Texture dependencies referenced by the imported MTL files. */
    public static final String[] NATURE_TEXTURES = {
        NATURE_MODEL_DIR + "textures/MapleTree_Bark.png",
        NATURE_MODEL_DIR + "textures/MapleTree_Leaves.png",
        NATURE_MODEL_DIR + "textures/PineTree_Bark.png",
        NATURE_MODEL_DIR + "textures/PineTree_Leaves.png",
        NATURE_MODEL_DIR + "textures/Bush_Leaves.png",
        NATURE_MODEL_DIR + "textures/Grass.png",
        NATURE_MODEL_DIR + "textures/Flowers.png",
        NATURE_MODEL_DIR + "textures/Rocks.png"
    };


    /** Basisverzeichnis aller Hintergrundbilder. */
    public static final String BACKGROUND_DIR = "backgrounds/";

    /** Hintergrund des Hauptmenues (screen-lokal geladen, Pfad zentral definiert). */
    public static final String STARTING_SCREEN_BACKGROUND =
        BACKGROUND_DIR + "BACKGROUND_STARTING_SCREEN.png";

    /** Logo des Hauptmenues (screen-lokal geladen, Pfad zentral definiert). */
    public static final String LOGO_TEXTURE = BACKGROUND_DIR + "BACKGROUND_LOGO.png";

    /** Kampfhintergrund (screen-lokal geladen, Pfad zentral definiert). */
    public static final String PLAYING_BOARD_BACKGROUND =
        BACKGROUND_DIR + "BACKGROUND_PLAYING_BOARD.jpg";

    /** NinePatch-Rand der gemeinsamen Button-Textur (identisch in allen Screens). */
    private static final int BUTTON_PATCH_SPLIT = 15;

    /** NinePatch-Rand der gemeinsamen Panel-Textur (identisch in allen Screens). */
    private static final int PANEL_PATCH_SPLIT = 20;

    /** Animation-Tags, die eine Overworld-Yokai-Entity zwingend benoetigt. */
    private static final String[] YOKAI_WALK_TAGS = {
        "walk_down", "walk_up", "walk_left", "walk_right"
    };

    private final AssetManager manager = new AssetManager();

    /** Einmalig geparste Aseprite-Sheets je JSON-Pfad (reine CPU-Daten, kein zweites Parsen). */
    private final Map<String, AsepriteSheet> asepriteSheets = new HashMap<>();

    private boolean loaded;
    private boolean disposed;

    /**
     * Laedt alle gemeinsam verwendeten Assets genau einmal synchron. Weitere
     * Aufrufe sind wirkungslos; Aufrufe nach {@link #dispose()} schlagen mit
     * klarer Meldung fehl.
     */
    public void load() {
        requireNotDisposed("load()");
        if (loaded) {
            return;
        }

        manager.load(UI_SKIN, Skin.class);
        manager.load(GAME_ATLAS, TextureAtlas.class);
        manager.load(BUTTON_TEXTURE, Texture.class);
        manager.load(PANEL_TEXTURE, Texture.class);
        manager.load(KITSUNE_WILD_SHEET, Texture.class);
        for (String texturePath : WORLD_TEXTURES) {
            manager.load(texturePath, Texture.class);
        }

        // Nature models use LibGDX's Wavefront loader. Textures are explicit
        // dependencies with Nearest filtering and clamped edges so the painted
        // leaf cutouts never bleed into neighbouring atlas texels.
        manager.setLoader(Model.class, ".obj", new ObjLoader(manager.getFileHandleResolver()));
        ObjLoader.ObjLoaderParameters natureParameters = newNatureModelParameters();
        for (String texturePath : NATURE_TEXTURES) {
            manager.load(texturePath, Texture.class, natureParameters.textureParameter);
        }
        for (String modelPath : NATURE_MODELS) {
            manager.load(modelPath, Model.class, natureParameters);
        }

        // Fuer den aktuellen Projektumfang existiert bewusst kein LoadingScreen,
        // deshalb wird synchron ueber finishLoading() abgeschlossen.
        manager.finishLoading();

        applyNatureMaterialDepthState();
        applyPixelArtFilters();
        loaded = true;
    }

    /**
     * Nature foliage uses hard alpha-cutout in {@code nature_model.frag}; any
     * LibGDX blending attribute would classify it as translucent, reorder it
     * behind opaque scenery and suppress depth writes. Loaded models are
     * therefore forced into the opaque, depth-writing path once, before any
     * {@code ModelInstance} copies their materials.
     */
    private void applyNatureMaterialDepthState() {
        for (String modelPath : NATURE_MODELS) {
            Model model = manager.get(modelPath, Model.class);
            for (Material material : model.materials) {
                material.remove(BlendingAttribute.Type);
                material.set(new DepthTestAttribute(GL20.GL_LEQUAL, true));
            }
        }
    }

    /**
     * Setzt die Filter der Pixel-Art-Assets genau einmal beim Laden.
     * Nicht-pixelige Hintergrundbilder werden nicht angefasst.
     */
    private void applyPixelArtFilters() {
        for (Texture page : requireAtlas(GAME_ATLAS).getTextures()) {
            page.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        requireTexture(BUTTON_TEXTURE).setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        requireTexture(PANEL_TEXTURE).setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        requireTexture(KITSUNE_WILD_SHEET).setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (String texturePath : WORLD_TEXTURES) {
            Texture texture = requireTexture(texturePath);
            // Repeat keeps triplanar world-space mapping seamless across every
            // face; Nearest preserves each 64x64 texel at any viewing distance.
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        for (String texturePath : NATURE_TEXTURES) {
            Texture texture = requireTexture(texturePath);
            texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
    }

    /** OBJ import settings: flipped V (Blender export), nearest, clamped edges. */
    private static ObjLoader.ObjLoaderParameters newNatureModelParameters() {
        ObjLoader.ObjLoaderParameters parameters = new ObjLoader.ObjLoaderParameters(true);
        parameters.textureParameter.genMipMaps = false;
        parameters.textureParameter.minFilter = Texture.TextureFilter.Nearest;
        parameters.textureParameter.magFilter = Texture.TextureFilter.Nearest;
        parameters.textureParameter.wrapU = Texture.TextureWrap.ClampToEdge;
        parameters.textureParameter.wrapV = Texture.TextureWrap.ClampToEdge;
        return parameters;
    }

    /**
     * Liefert das Aseprite-Sheet (Regionen + Animationen) zu einem bereits geladenen
     * Spritesheet. Die JSON-Metadaten werden genau einmal gelesen und geparst und
     * danach als reine CPU-Daten gecacht - niemals pro Frame.
     *
     * @param texturePath  Pfad der Spritesheet-Textur (muss geladen sein)
     * @param jsonPath     Pfad der Aseprite-JSON-Metadaten
     * @param requiredTags Tags, die vorhanden sein muessen
     * @throws GdxRuntimeException wenn Textur/JSON fehlen oder ein Tag fehlt
     */
    public AsepriteSheet getAsepriteSheet(String texturePath, String jsonPath, String... requiredTags) {
        requireNotDisposed("getAsepriteSheet(" + jsonPath + ")");

        Texture texture = requireTexture(texturePath);

        AsepriteSheet cached = asepriteSheets.get(jsonPath);
        if (cached != null) {
            return cached;
        }

        if (!Gdx.files.internal(jsonPath).exists()) {
            throw new GdxRuntimeException("Required asset is not loaded: " + jsonPath);
        }

        AsepriteSheetData data = AsepriteSheetData.parse(Gdx.files.internal(jsonPath).readString(), jsonPath);

        String expectedImage = data.getImageName();
        if (expectedImage != null && !expectedImage.equals(fileNameOf(texturePath))) {
            Gdx.app.error("GameAssets", "Aseprite JSON " + jsonPath + " references image '" + expectedImage
                + "', but the loaded texture is '" + texturePath + "'.");
        }

        cached = AsepriteSheet.create(data, texture, requiredTags);
        asepriteSheets.put(jsonPath, cached);
        return cached;
    }

    /**
     * Spritesheet der wilden Form inklusive der vier Laufanimationen
     * ({@code walk_down/up/left/right}); einmalig geparst und gecacht.
     */
    public AsepriteSheet getWildYokaiSheet(String texturePath, String jsonPath) {
        return getAsepriteSheet(texturePath, jsonPath, YOKAI_WALK_TAGS);
    }

    private static String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    // ------------------------------------------------------------------
    // Zugriff auf geliehene Assets
    // ------------------------------------------------------------------

    /**
     * Liefert eine gemeinsam geladene Textur. Der Aufrufer behandelt sie als
     * geliehen und disposed sie nicht.
     *
     * @throws GdxRuntimeException wenn der Pfad nicht geladen ist (mit exaktem Pfad)
     */
    public Texture getTexture(String path) {
        return requireTexture(path);
    }

    /**
     * Liefert ein gemeinsam geladenes 3D-Modell (aktuell die importierten
     * Nature-OBJs). Der Aufrufer behandelt es als geliehen und disposed es nie.
     *
     * @throws GdxRuntimeException wenn das Modell nicht geladen ist
     */
    public Model getModel(String path) {
        requireNotDisposed("getModel(" + path + ")");
        if (!manager.isLoaded(path, Model.class)) {
            throw new GdxRuntimeException("Required asset is not loaded: " + path);
        }
        return manager.get(path, Model.class);
    }

    /**
     * Liefert den gemeinsam geladenen Atlas. Der Aufrufer disposed ihn nicht;
     * TextureRegions aus diesem Atlas besitzen die zugrunde liegende Textur nicht.
     *
     * @throws GdxRuntimeException wenn der Pfad nicht geladen ist (mit exaktem Pfad)
     */
    public TextureAtlas getAtlas(String path) {
        return requireAtlas(path);
    }

    /**
     * Liefert die gemeinsam geladene UI-Skin. Der Aufrufer disposed sie nicht.
     *
     * <p>Der globale Font aus {@link FontManager} wird in Screens ausschliesslich
     * per Zuweisung in einen bestehenden LabelStyle gesetzt und nie als
     * Skin-Ressource registriert; {@code Skin.dispose()} kann ihn daher nicht
     * mitzerstoeren.</p>
     *
     * @throws GdxRuntimeException wenn die Skin nicht geladen ist
     */
    public Skin getSkin() {
        requireNotDisposed("getSkin()");
        if (!manager.isLoaded(UI_SKIN, Skin.class)) {
            throw new GdxRuntimeException("Required asset is not loaded: " + UI_SKIN);
        }
        return manager.get(UI_SKIN, Skin.class);
    }

    /**
     * Neue Button-NinePatch auf der gemeinsamen Button-Textur. Die Drawable
     * besitzt keine eigene GPU-Ressource und darf pro Screen erzeugt werden.
     */
    public NinePatchDrawable newButtonDrawable() {
        return newNinePatchDrawable(BUTTON_TEXTURE, BUTTON_PATCH_SPLIT);
    }

    /**
     * Neue Panel-NinePatch auf der gemeinsamen Panel-Textur. Die Drawable
     * besitzt keine eigene GPU-Ressource und darf pro Screen erzeugt werden.
     */
    public NinePatchDrawable newPanelDrawable() {
        return newNinePatchDrawable(PANEL_TEXTURE, PANEL_PATCH_SPLIT);
    }

    private NinePatchDrawable newNinePatchDrawable(String texturePath, int split) {
        return new NinePatchDrawable(new NinePatch(requireTexture(texturePath), split, split, split, split));
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isDisposed() {
        return disposed;
    }

    // ------------------------------------------------------------------
    // Lebensdauer
    // ------------------------------------------------------------------

    /**
     * Gibt alle verwalteten Assets genau einmal frei. Idempotent: ein zweiter
     * Aufruf ist wirkungslos.
     *
     * <p>Ausschliesslich {@link com.lychcs.koikoi.KoiKoiGame} ruft diese Methode
     * auf, und zwar nachdem der aktive Screen freigegeben wurde. Kein Screen darf
     * sie aufrufen.</p>
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        // Ein einziger Aufruf entsorgt Skin, Atlas und Texturen, weil der
        // AssetManager ihr Eigentuemer ist. Screens halten nur geliehene
        // Referenzen.
        manager.dispose();
        loaded = false;
        disposed = true;
    }

    // ------------------------------------------------------------------
    // Interne Pruefungen (verstaendliche Meldungen mit exaktem Assetpfad)
    // ------------------------------------------------------------------

    private Texture requireTexture(String path) {
        requireNotDisposed("getTexture(" + path + ")");
        if (!manager.isLoaded(path, Texture.class)) {
            throw new GdxRuntimeException("Required asset is not loaded: " + path);
        }
        return manager.get(path, Texture.class);
    }

    private TextureAtlas requireAtlas(String path) {
        requireNotDisposed("getAtlas(" + path + ")");
        if (!manager.isLoaded(path, TextureAtlas.class)) {
            throw new GdxRuntimeException("Required asset is not loaded: " + path);
        }
        return manager.get(path, TextureAtlas.class);
    }

    private void requireNotDisposed(String action) {
        if (disposed) {
            throw new GdxRuntimeException("Attempted to use disposed GameAssets in " + action + ".");
        }
    }
}
