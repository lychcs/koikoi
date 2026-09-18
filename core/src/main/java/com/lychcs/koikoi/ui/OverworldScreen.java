package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.entities.OverworldYokaiEntity;
import com.lychcs.koikoi.entities.Player;
import com.lychcs.koikoi.graphics.AsepriteSheet;
import com.lychcs.koikoi.graphics.CorruptionEngine;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.model.yokai.YokaiSpecies;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.YokaiEncounter;
import com.lychcs.koikoi.run.YokaiEncounterCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OverworldScreen extends ScreenAdapter {

    /** Tiled-Map des aktuell geladenen Ortes. */
    private static final String LOCATION_MAP_PATH = "map/testmap.tmx";

    /** Map-Property mit der stabilen Orts-Id. */
    private static final String MAP_PROPERTY_LOCATION_ID = "locationId";

    /** Map-Property mit der Season des Ortes. */
    private static final String MAP_PROPERTY_SEASON = "season";

    /** Map-Property mit einer expliziten Encounter-Id eines Spawnobjekts. */
    private static final String MAP_PROPERTY_ENCOUNTER_ID = "encounterId";

    /** Trigger-Typ, der als Spawnmarkierung eines wilden Yokai dient. */
    private static final String TRIGGER_TYPE_YOKAI_SPAWN = "enemy1";

    /** Trigger-Typ einer wiederholbaren Kampfflaeche ohne Entity. */
    private static final String TRIGGER_TYPE_COMBAT_ZONE = "combat_zone";

    /**
     * Fallback-Id der Spawnmarkierung, falls die Testmap genau ein unbenanntes
     * {@code enemy1}-Objekt enthaelt (siehe {@link #createYokaiEntities()}).
     */
    private static final String FALLBACK_YOKAI_SPAWN_ID = "spring_village_kitsune_01";

    private final RunSession runSession;
    /** Geliehene, global verwaltete Assets (Eigentum: GameAssets). */
    private final GameAssets assets;
    private TiledMap map;
    private float mapPixelWidth;
    private float mapPixelHeight;
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    private Player player;
    private Stage uiStage;
    private Label promptLabel;
    private ShaderProgram windShader;
    private float overworldTime = 0f;
    private Skin skin; // Falls noch nicht vorhanden
    private InventoryOverlay inventoryOverlay;
    private TextureAtlas gameAtlas; // Falls noch nicht in OverworldScreen geladen

    // Post-Processing
    public boolean enableEdgeDetection = true;
    private FrameBuffer fbo;
    private SpriteBatch screenBatch;
    private TextureRegion fboRegion;
    private ShaderProgram edgeShader;
    private CorruptionEngine corruptionEngine;

    // Verhindert, dass eine Kampfzone direkt nach der Rückkehr aus
    // einem Kampf erneut ausgelöst wird. Die Sperre endet erst, wenn
    // der Spieler alle automatischen Kampfzonen verlassen hat.
    private boolean automaticCombatLocked;

    /** Schutz gegen Mehrfach-Dispose durch den Screen-Manager. */
    private boolean disposed;

    // Box2D Physik-Welt

    private World world;
    private Box2DDebugRenderer debugRenderer; // Optional, zeigt die roten Physik-Boxen an falls gewünscht

    private static class RenderNode implements Comparable<RenderNode> {
        float y;
        com.lychcs.koikoi.entities.Player playerEntity; // Eindeutiger Name
        TiledMapTileMapObject mapObject;
        OverworldYokaiEntity yokaiEntity;

        public RenderNode(float y, com.lychcs.koikoi.entities.Player playerEntity) {
            this.y = y;
            this.playerEntity = playerEntity;
            this.mapObject = null;
            this.yokaiEntity = null;
        }
        public RenderNode(float y, TiledMapTileMapObject mapObject) {
            this.y = y;
            this.mapObject = mapObject;
            this.playerEntity = null;
            this.yokaiEntity = null;
        }

        /** Wiederverwendung eines gepoolten Knotens ohne neue Allokation. */
        RenderNode setPlayer(float y, com.lychcs.koikoi.entities.Player playerEntity) {
            this.y = y;
            this.playerEntity = playerEntity;
            this.mapObject = null;
            this.yokaiEntity = null;
            return this;
        }

        /** Wiederverwendung eines gepoolten Knotens ohne neue Allokation. */
        RenderNode setMapObject(float y, TiledMapTileMapObject mapObject) {
            this.y = y;
            this.mapObject = mapObject;
            this.playerEntity = null;
            this.yokaiEntity = null;
            return this;
        }

        /** Wiederverwendung eines gepoolten Knotens fuer ein wildes Yokai. */
        RenderNode setYokai(float y, OverworldYokaiEntity yokaiEntity) {
            this.y = y;
            this.yokaiEntity = yokaiEntity;
            this.playerEntity = null;
            this.mapObject = null;
            return this;
        }

        @Override
        public int compareTo(RenderNode other) {
            return Float.compare(other.y, this.y);
        }
    }

    /** Wiederverwendete Y-Sortier-Liste des laufenden Frames (keine Allokation pro Frame). */
    private final List<RenderNode> renderList = new ArrayList<>();
    /** Pool der Sortierknoten; waechst einmalig bis zur Anzahl der Map-Objekte. */
    private final List<RenderNode> renderNodePool = new ArrayList<>();
    private int renderNodeCursor;
    /** Wiederverwendete Interaktionsbox (keine Allokation pro Frame). */
    private final Rectangle interactionBox = new Rectangle();

    /** Tilegroesse der geladenen Map (für Roaming-Bereiche der Yokai). */
    private int tileWidth;
    private int tileHeight;

    /** Kollisionsrechtecke der Map (dieselbe Quelle wie die Box2D-Waende). */
    private final List<Rectangle> collisionRects = new ArrayList<>();

    /** Wilde Yokai dieses Screenaufbaus (keine statische globale Liste). */
    private final List<OverworldYokaiEntity> yokaiEntities = new ArrayList<>();

    /** Einmalige Methodenreferenz: keine Allokation pro Frame. */
    private final OverworldYokaiEntity.CollisionQuery collisionQuery = this::isBlockedInWorld;

    public OverworldScreen(RunSession runSession, GameAssets assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }

        this.runSession = runSession;
        this.assets = assets;

        // 1. Box2D Welt ohne Schwerkraft (Top-Down RPG) erstellen
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();

        map = new TmxMapLoader().load(LOCATION_MAP_PATH);

        // Ort und Season werden unmittelbar nach dem Laden aus den Map-Properties gelesen.
        applyLocationMetadata(LOCATION_MAP_PATH);

        int mapWidthTiles = map.getProperties().get("width", Integer.class);
        int mapHeightTiles = map.getProperties().get("height", Integer.class);
        this.tileWidth = map.getProperties().get("tilewidth", Integer.class);
        this.tileHeight = map.getProperties().get("tileheight", Integer.class);

        mapPixelWidth = mapWidthTiles * this.tileWidth;
        mapPixelHeight = mapHeightTiles * this.tileHeight;

        // 2. Vollautomatische Erstellung der Physik-Wände aus der "collision"-Ebene von Tiled
        createCollisionBoxes();

        // 3. Wilde Yokai aus ihren Spawnmarkierungen erzeugen (genau eine je MapObject).
        createYokaiEntities();

        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f);

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 1280, 720);
        camera.zoom = 0.5f;

        float startX = 100f;
        float startY = 100f;

        // Eine gespeicherte Position kann noch innerhalb der Kampfzone liegen.
        automaticCombatLocked = runSession.hasStoredPosition();

        // --- Prüfen, ob eine gespeicherte Position aus einem Shop/Schrein existiert ---
        if (runSession.hasStoredPosition()) {
            startX = runSession.getLastPlayerX();
            startY = runSession.getLastPlayerY();
        } else {
            // Ansonsten den normalen Spawnpunkt von der Tiled-Map auslesen
            MapLayer entityLayer = map.getLayers().get("entities");
            if (entityLayer != null) {
                MapObject spawnObj = entityLayer.getObjects().get("player_spawn");
                if (spawnObj != null) {
                    startX = spawnObj.getProperties().get("x", Float.class);
                    startY = spawnObj.getProperties().get("y", Float.class);
                }
            }
        }

        // Sprite-Sheet ist geliehen (Eigentum: GameAssets).
        player = new Player(world, startX, startY, assets.getTexture(GameAssets.PLAYER_SHEET));

        // UI Stage für Overworld-Popups (z.B. "Press E to Shop")
        uiStage = new Stage(new FitViewport(1280, 720));
        // Geliehene, global verwaltete Skin: kein Dispose in diesem Screen.
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        promptLabel = new Label("", skin);
        promptLabel.setFontScale(0.8f);

        Table promptTable = new Table();
        promptTable.setFillParent(true);
        promptTable.bottom().padBottom(80); // Platziert es im unteren Bildschirmdrittel
        promptTable.add(promptLabel); // Nutzt deinen schicken Panel-Hintergrund!
        promptTable.pack();

        uiStage.addActor(promptTable);
        promptLabel.setVisible(false);

        windShader = new ShaderProgram(
            Gdx.files.internal("shaders/tree_wind.vert"),
            Gdx.files.internal("shaders/tree_wind.frag")
        );

        if (!windShader.isCompiled()) {
            String log = windShader.getLog();
            windShader.dispose();
            throw new GdxRuntimeException("Failed to compile Tree Wind shader:\n" + log);
        }

        // Geliehener, global geladener Atlas (kein Laden, kein Dispose).
        gameAtlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // NinePatch-Drawables verweisen nur auf die gemeinsamen Texturen und
        // besitzen selbst keine GPU-Ressource.
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.up = assets.newButtonDrawable();
        btnStyle.down = ((NinePatchDrawable) btnStyle.up).tint(Color.LIGHT_GRAY);
        btnStyle.font = FontManager.getFont();
        btnStyle.fontColor = FontManager.COLOR_TEXT_MAIN;

        NinePatchDrawable panelBg = assets.newPanelDrawable();

        inventoryOverlay = new InventoryOverlay(runSession, skin, gameAtlas, panelBg, btnStyle);
        uiStage.addActor(inventoryOverlay);
        // --- Edge Detection Post-Processing initialisieren ---
        screenBatch = new SpriteBatch();
        edgeShader = new ShaderProgram(
            Gdx.files.internal("shaders/edge_detection.vert"),
            Gdx.files.internal("shaders/edge_detection.frag")
        );

        if (!edgeShader.isCompiled()) {
            throw new GdxRuntimeException(
                "edge_detection shader could not be compiled:\n"
                    + edgeShader.getLog()
            );
        }

        corruptionEngine = new CorruptionEngine();
    }

    /**
     * Liest Orts-Id und Season aus den Properties der geladenen Tiled-Map und meldet
     * den Ort an die {@link RunSession}. Fehlende oder ungueltige Werte werden mit
     * verstaendlicher Meldung protokolliert und durch sichere Fallbacks ersetzt
     * (Map-Pfad als Orts-Id, bestehende Season als Season).
     */
    private void applyLocationMetadata(String fallbackLocationId) {
        MapProperties properties = map.getProperties();

        String rawLocationId = properties.get(MAP_PROPERTY_LOCATION_ID, String.class);
        String locationId = rawLocationId == null ? "" : rawLocationId.trim();
        if (locationId.isEmpty()) {
            locationId = fallbackLocationId;
        }

        String rawSeason = properties.get(MAP_PROPERTY_SEASON, String.class);
        GameSeason mapSeason = null;

        if (rawSeason == null || rawSeason.trim().isEmpty()) {
            Gdx.app.error(
                "OverworldScreen",
                "Season property '" + MAP_PROPERTY_SEASON + "' is missing in map " + fallbackLocationId
                    + ". Fallback: " + runSession.getCurrentSeason()
            );
        } else {
            try {
                mapSeason = GameSeason.valueOf(rawSeason.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                Gdx.app.error(
                    "OverworldScreen",
                    "Invalid Season '" + rawSeason + "' in map " + fallbackLocationId
                        + ". Allowed values: " + Arrays.toString(GameSeason.values())
                        + ". Fallback: " + runSession.getCurrentSeason()
                );
            }
        }

        if (mapSeason == null) {
            mapSeason = runSession.getCurrentSeason();
        }

        runSession.enterLocation(locationId, mapSeason);
    }

    private void createCollisionBoxes() {
        MapLayer collisionLayer = map.getLayers().get("collision");
        if (collisionLayer == null) return;

        for (MapObject object : collisionLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                // Dieselben Rechtecke dienen der Physik des Spielers UND der geometrischen
                // Kollision der Yokai: eine Quelle, keine zweite Kollisionskarte.
                collisionRects.add(rect);

                // Statischer Physik-Körper für jedes manuell gezeichnete Rechteck in Tiled
                BodyDef bdef = new BodyDef();
                bdef.type = BodyDef.BodyType.StaticBody;
                // Box2D platziert Rechtecke über ihr Zentrum:
                bdef.position.set(rect.x + rect.width / 2f, rect.y + rect.height / 2f);

                Body body = world.createBody(bdef);

                PolygonShape shape = new PolygonShape();
                shape.setAsBox(rect.width / 2f, rect.height / 2f);

                body.createFixture(shape, 0.0f);
                shape.dispose();
            }
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(uiStage);
    }

    /**
     * Erzeugt die wilden Yokai aus den Spawnmarkierungen der Map.
     *
     * <p>Gesucht wird in <b>allen</b> Objektlayern der Map (in der Testmap liegt die
     * Markierung in {@code Objektebene 1}): die Identitaet kommt ausschliesslich aus Typ,
     * Name und Properties, niemals aus der Ebene oder Bildschirmkoordinaten. Ein
     * {@code enemy1}-Objekt ist eine reine Spawnmarkierung und wird nicht mehr selbst
     * gezeichnet und auch nicht mehr als statische Kampfflaeche benutzt.</p>
     *
     * <p>Aufloesung der Identitaet: zuerst die Property {@code encounterId}, danach der
     * Objektname (oder dieselbe Property als Spawn-Id); die dokumentierte Fallback-Id der
     * Testmap ({@value #FALLBACK_YOKAI_SPAWN_ID}) greift nur, wenn die Map genau ein
     * unbenanntes {@code enemy1}-Objekt besitzt.</p>
     *
     * <p>Bereits besiegte, nicht wiederholbare Begegnungen erzeugen keine Entity mehr
     * (das Kitsune bleibt fuer den restlichen Run verschwunden).</p>
     */
    private void createYokaiEntities() {
        yokaiEntities.clear();

        List<MapObject> spawnObjects = new ArrayList<>();
        for (MapLayer layer : map.getLayers()) {
            for (MapObject object : layer.getObjects()) {
                if (TRIGGER_TYPE_YOKAI_SPAWN.equals(triggerTypeOf(object))) {
                    spawnObjects.add(object);
                }
            }
        }
        if (spawnObjects.isEmpty()) {
            return;
        }

        boolean singleUnnamedSpawn = spawnObjects.size() == 1 && explicitEncounterIdOf(spawnObjects.get(0)) == null;
        Set<String> usedEncounterIds = new LinkedHashSet<>();
        Rectangle spawnRect = new Rectangle();

        for (MapObject object : spawnObjects) {
            if (!spawnRectOf(object, spawnRect)) {
                Gdx.app.error("OverworldScreen", "Yokai spawn marker without usable bounds in map "
                    + LOCATION_MAP_PATH + ": object type '" + TRIGGER_TYPE_YOKAI_SPAWN
                    + "' needs a rectangle or a tile object. Spawn skipped.");
                continue;
            }

            String spawnKey = explicitEncounterIdOf(object);
            if (spawnKey == null) {
                if (!singleUnnamedSpawn) {
                    Gdx.app.error("OverworldScreen", "Yokai spawn without a stable id in map " + LOCATION_MAP_PATH
                        + " at (" + spawnRect.x + ", " + spawnRect.y + "): give the object a name or an '"
                        + MAP_PROPERTY_ENCOUNTER_ID + "' property. Spawn skipped.");
                    continue;
                }
                spawnKey = FALLBACK_YOKAI_SPAWN_ID;
            }

            YokaiEncounter encounter = null;
            String encounterIdProperty = stringProperty(object, MAP_PROPERTY_ENCOUNTER_ID);
            if (encounterIdProperty != null) {
                encounter = YokaiEncounterCatalog.findById(encounterIdProperty);
                if (encounter == null) {
                    Gdx.app.error("OverworldScreen", "Property '" + MAP_PROPERTY_ENCOUNTER_ID + "' = '"
                        + encounterIdProperty + "' of a Yokai spawn in map " + LOCATION_MAP_PATH + " at ("
                        + spawnRect.x + ", " + spawnRect.y + ") is not registered.");
                }
            }
            if (encounter == null) {
                encounter = YokaiEncounterCatalog.findBySpawnId(spawnKey);
            }
            if (encounter == null) {
                encounter = YokaiEncounterCatalog.findById(spawnKey);
            }
            if (encounter == null) {
                Gdx.app.error("OverworldScreen", "No encounter registered for Yokai spawn id '" + spawnKey
                    + "' in map " + LOCATION_MAP_PATH + " at (" + spawnRect.x + ", " + spawnRect.y
                    + "). Spawn skipped.");
                continue;
            }

            if (!usedEncounterIds.add(encounter.getId())) {
                Gdx.app.error("OverworldScreen", "Duplicate Yokai encounter '" + encounter.getId() + "' in map "
                    + LOCATION_MAP_PATH + " at (" + spawnRect.x + ", " + spawnRect.y + "). Spawn skipped.");
                continue;
            }

            if (!encounter.isRepeatable() && runSession.isEncounterDefeated(encounter.getId())) {
                // Besiegt und nicht wiederholbar: bleibt fuer diesen Run verschwunden.
                continue;
            }

            YokaiSpecies species = encounter.getSpecies();
            if (!species.hasWildSheet()) {
                Gdx.app.error("OverworldScreen", "Species '" + species.getId()
                    + "' has no animated wild form; spawn '" + spawnKey + "' skipped.");
                continue;
            }

            AsepriteSheet sheet = assets.getWildYokaiSheet(
                species.getWildSheetTexturePath(), species.getWildSheetJsonPath());

            yokaiEntities.add(new OverworldYokaiEntity(
                encounter.getId(),
                species.getId(),
                species.getWildName(),
                OverworldYokaiEntity.WalkAnimations.from(sheet),
                spawnRect.x + (spawnRect.width / 2f),
                spawnRect.y + (spawnRect.height / 2f),
                tileWidth,
                tileHeight));
        }
    }

    /**
     * Schreibt die Weltgrenzen einer Spawnmarkierung in die uebergebene Box. Rechteckobjekte
     * liefern ihre Flaeche, Kachelobjekte ihre Position samt Breite/Hoehe (aus den Properties
     * oder der Kachel selbst). Die Box wird wiederverwendet, damit kein Objekt pro Spawn
     * entsteht.
     */
    private static boolean spawnRectOf(MapObject object, Rectangle out) {
        if (object instanceof RectangleMapObject) {
            out.set(((RectangleMapObject) object).getRectangle());
            return true;
        }

        if (object instanceof TiledMapTileMapObject) {
            TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
            float width = floatProperty(tileObject, "width");
            float height = floatProperty(tileObject, "height");
            if (width <= 0f || height <= 0f) {
                TextureRegion region = tileObject.getTile().getTextureRegion();
                if (region == null) {
                    return false;
                }
                width = region.getRegionWidth();
                height = region.getRegionHeight();
            }
            out.set(tileObject.getX(), tileObject.getY(), width, height);
            return true;
        }

        return false;
    }

    /** Nicht-leerer String-Wert einer Map-Property oder {@code null}. */
    private static String stringProperty(MapObject object, String name) {
        String value = object.getProperties().get(name, String.class);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /** Zahlenwert einer Map-Property oder 0. */
    private static float floatProperty(MapObject object, String name) {
        Float value = object.getProperties().get(name, Float.class);
        return value == null ? 0f : value;
    }

    /** Trigger-Typ (oder Tiled-{@code class}) eines Map-Objekts, oder {@code null}. */
    private static String triggerTypeOf(MapObject object) {
        String type = object.getProperties().get("type", String.class);
        if (type == null) {
            type = object.getProperties().get("class", String.class);
        }
        return type;
    }

    /**
     * Explizite, stabile Spawn-Id eines Objekts: Objektname oder Property
     * {@code encounterId}. {@code null}, wenn beides fehlt.
     */
    private static String explicitEncounterIdOf(MapObject object) {
        String name = object.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String property = object.getProperties().get(MAP_PROPERTY_ENCOUNTER_ID, String.class);
        if (property != null && !property.trim().isEmpty()) {
            return property.trim();
        }
        return null;
    }

    /**
     * Kollisionsabfrage fuer die wilden Yokai: dieselben Tiles-Rechtecke wie die Physik
     * des Spielers plus die Mapgrenzen. Reines Praedikat ohne Allokation.
     */
    private boolean isBlockedInWorld(Rectangle box) {
        if (box.x < 0f || box.y < 0f
            || box.x + box.width > mapPixelWidth
            || box.y + box.height > mapPixelHeight) {
            return true;
        }

        for (int i = 0; i < collisionRects.size(); i++) {
            if (box.overlaps(collisionRects.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Bewegt alle wilden Yokai; laeuft unabhaengig vom Spieler-Input. */
    private void updateYokaiEntities(float delta) {
        for (int i = 0; i < yokaiEntities.size(); i++) {
            yokaiEntities.get(i).update(delta, collisionQuery);
        }
    }

    /** Erstes Yokai, dessen aktuelle Encounter-Flaeche den Spieler beruehrt (oder {@code null}). */
    private OverworldYokaiEntity findContactedYokai() {
        for (int i = 0; i < yokaiEntities.size(); i++) {
            OverworldYokaiEntity entity = yokaiEntities.get(i);
            if (interactionBox.overlaps(entity.getInteractionBounds())) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);

        // Kein neuer FBO ohne echte Groessenaenderung und nie mit Groesse 0.
        if (width <= 0 || height <= 0) {
            return;
        }
        if (fbo != null && fbo.getWidth() == width && fbo.getHeight() == height
            && screenBatch != null) {
            return;
        }

        // 1. FBO an neue Bildschirmauflösung anpassen
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, width, height, false);
        // Frische Region pro FBO: der Y-Flip wird dadurch nie mehrfach angewendet.
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        // 2. ENTSCHEIDEND: screenBatch auf die neue Auflösung eichen!
        if (screenBatch != null) {
            screenBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        }
    }

    @Override
    public void render(float delta) {
        if (fbo == null) {
            resize(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight()
            );
        }

        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        overworldTime += delta;

        handleMovement(delta);
        world.step(delta, 6, 2);

        player.update(
            delta,
            player.body.getLinearVelocity()
        );

        // Wilde Yokai bewegen sich vor der Kontaktpruefung.
        updateYokaiEntities(delta);

        handleInteractions();

        // ============================================================
        // KAMERA
        // ============================================================

        Vector2 renderPos = player.getRenderPosition();

        float targetX = renderPos.x + 32f;
        float targetY = renderPos.y + 32f;

        float camHalfWidth =
            camera.viewportWidth * 0.5f * camera.zoom;

        float camHalfHeight =
            camera.viewportHeight * 0.5f * camera.zoom;

        float clampedX;

        if (mapPixelWidth <= camHalfWidth * 2f) {
            clampedX = mapPixelWidth * 0.5f;
        } else {
            clampedX = com.badlogic.gdx.math.MathUtils.clamp(
                targetX,
                camHalfWidth,
                mapPixelWidth - camHalfWidth
            );
        }

        float clampedY;

        if (mapPixelHeight <= camHalfHeight * 2f) {
            clampedY = mapPixelHeight * 0.5f;
        } else {
            clampedY = com.badlogic.gdx.math.MathUtils.clamp(
                targetY,
                camHalfHeight,
                mapPixelHeight - camHalfHeight
            );
        }

        camera.position.set(
            clampedX,
            clampedY,
            0f
        );

        camera.update();

        // ============================================================
        // PHASE 1: WELT IN DEN FRAMEBUFFER RENDERN
        // ============================================================

        fbo.begin();

        Gdx.gl.glViewport(
            0,
            0,
            fbo.getWidth(),
            fbo.getHeight()
        );

        ScreenUtils.clear(
            0f,
            0f,
            0f,
            1f
        );

        mapRenderer.setView(camera);

        mapRenderer.getBatch().setShader(null);
        mapRenderer.getBatch().begin();

        // Kachelebenen zeichnen
        for (MapLayer layer : map.getLayers()) {
            if (layer instanceof TiledMapTileLayer) {
                mapRenderer.renderTileLayer(
                    (TiledMapTileLayer) layer
                );
            }
        }

        // Y-Sorting-Liste aufbauen (wiederverwendet, keine Allokation pro Frame)
        renderList.clear();
        renderNodeCursor = 0;

        renderList.add(
            obtainRenderNode().setPlayer(
                renderPos.y,
                player
            )
        );

        // Wilde Yokai mit ihrer Fuss-/Sortierposition in dasselbe Y-Sorting aufnehmen.
        for (int i = 0; i < yokaiEntities.size(); i++) {
            OverworldYokaiEntity entity = yokaiEntities.get(i);
            renderList.add(obtainRenderNode().setYokai(entity.getFootY(), entity));
        }

        for (MapLayer layer : map.getLayers()) {
            if (!(layer instanceof TiledMapTileLayer)) {
                for (MapObject obj : layer.getObjects()) {
                    if (obj instanceof TiledMapTileMapObject) {
                        TiledMapTileMapObject tiledObject =
                            (TiledMapTileMapObject) obj;

                        // Spawnmarkierungen wilder Yokai werden von der Entity gezeichnet,
                        // nicht zusaetzlich als Standbild.
                        if (TRIGGER_TYPE_YOKAI_SPAWN.equals(triggerTypeOf(tiledObject))) {
                            continue;
                        }

                        renderList.add(
                            obtainRenderNode().setMapObject(
                                tiledObject.getY(),
                                tiledObject
                            )
                        );
                    }
                }
            }
        }

        Collections.sort(renderList);

        // Sortierte Objekte zeichnen
        for (RenderNode node : renderList) {
            if (node.playerEntity != null) {
                node.playerEntity.render(
                    (SpriteBatch) mapRenderer.getBatch()
                );

                continue;
            }

            if (node.yokaiEntity != null) {
                // Yokai werden ohne Wind-Shader gezeichnet; der Batch-Shader ist hier
                // garantiert zurueckgesetzt (jeder Baum setzt ihn nach sich auf null).
                mapRenderer.getBatch().setShader(null);
                node.yokaiEntity.render((SpriteBatch) mapRenderer.getBatch());
                continue;
            }

            TiledMapTileMapObject tiledObject =
                node.mapObject;

            TextureRegion region =
                tiledObject
                    .getTile()
                    .getTextureRegion();

            String type =
                tiledObject
                    .getProperties()
                    .get(
                        "type",
                        String.class
                    );

            if (type == null) {
                type =
                    tiledObject
                        .getProperties()
                        .get(
                            "class",
                            String.class
                        );
            }

            boolean isTree =
                "tree".equalsIgnoreCase(type);

            if (
                isTree &&
                    windShader != null &&
                    windShader.isCompiled()
            ) {
                mapRenderer
                    .getBatch()
                    .setShader(windShader);

                windShader.setUniformf(
                    "u_time",
                    overworldTime
                );

                float u = region.getU();
                float v = region.getV();

                float uWidth =
                    region.getU2() - u;

                float vHeight =
                    region.getV2() - v;

                windShader.setUniformf(
                    "u_region",
                    u,
                    v,
                    uWidth,
                    vHeight
                );

                windShader.setUniformf(
                    "u_regionSizePx",
                    region.getRegionWidth(),
                    region.getRegionHeight()
                );

                windShader.setUniformf(
                    "u_swayStrength",
                    2.0f
                );

                windShader.setUniformf(
                    "u_swaySpeed",
                    1.6f
                );

                windShader.setUniformf(
                    "u_swayHeightStart",
                    0.55f
                );

                windShader.setUniformf(
                    "u_windStrength",
                    0.8f
                );

                windShader.setUniformf(
                    "u_windScale",
                    8.0f
                );

                windShader.setUniformf(
                    "u_windSpeed",
                    2.0f
                );

                windShader.setUniformf(
                    "u_windHeightStart",
                    0.35f
                );

                mapRenderer.getBatch().draw(
                    region,
                    tiledObject.getX(),
                    tiledObject.getY()
                );

                mapRenderer
                    .getBatch()
                    .setShader(null);
            } else {
                mapRenderer.getBatch().draw(
                    region,
                    tiledObject.getX(),
                    tiledObject.getY()
                );
            }
        }

        mapRenderer.getBatch().setShader(null);
        mapRenderer.getBatch().end();

        fbo.end();

        // ============================================================
// CORRUPTION-SIMULATION
// ============================================================

        corruptionEngine.update(delta);

// screenWidth und screenHeight wurden bereits oben deklariert
        Gdx.gl.glViewport(
            0,
            0,
            screenWidth,
            screenHeight
        );

        ScreenUtils.clear(0f, 0f, 0f, 1f);

        screenBatch.getProjectionMatrix().setToOrtho2D(
            0f,
            0f,
            screenWidth,
            screenHeight
        );

// Pink-Testshader aktivieren
        screenBatch.setShader(edgeShader);
        screenBatch.begin();

        screenBatch.draw(
            fboRegion,
            0f,
            0f,
            screenWidth,
            screenHeight
        );

        screenBatch.end();
        screenBatch.setShader(null);

// UI bleibt vom Shader unbeeinflusst
        uiStage.getViewport().apply();
        uiStage.act(delta);
        uiStage.draw();
    }

    private void handleMovement(float delta) {
        // Taste 'I' toggelt das Inventar
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            inventoryOverlay.toggle();
        }

        // Wenn das Inventar offen ist, stoppt die Spielfigur komplett!
        if (inventoryOverlay.isOpen()) {
            player.body.setLinearVelocity(0, 0);
            return;
        }
        float velX = 0;
        float velY = 0;
        float speed = player.SPEED;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) velY += speed;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) velY -= speed;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) velX -= speed;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) velX += speed;

        // Übergibt die gewünschte Geschwindigkeit direkt an die Physik-Engine
        player.body.setLinearVelocity(velX, velY);
    }

    /**
     * Stabile Welt-Id eines Triggers: bevorzugt der Objektname aus Tiled, danach der
     * Trigger-Typ. Bildschirmkoordinaten werden bewusst NICHT als Identitaet benutzt.
     *
     * <p>Anschlussstelle fuer echte Yokai-Entities: sobald Objekte in der Tiled-Map
     * einen eigenen {@code name} tragen (z. B. {@code yokai_oni_grove_01}), liefert
     * diese Methode automatisch die stabile Spawn-Id und
     * {@link YokaiEncounterCatalog#findBySpawnId(String)} bindet die zugehoerige
     * Begegnung - inklusive dauerhaftem Fernbleiben nach einem Sieg.</p>
     */
    private static String spawnIdentifierOf(MapObject object) {
        if (object == null) {
            return "";
        }
        String name = object.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String type = object.getProperties().get("type", String.class);
        if (type == null) {
            type = object.getProperties().get("class", String.class);
        }
        return type == null ? "" : type.trim();
    }

    /**
     * Loest die Begegnung einer Kampfzone auf. Zonen ohne eigene Definition nutzen den
     * Standard-Encounter und verhalten sich damit exakt wie bisher.
     */
    private YokaiEncounter resolveEncounter(MapObject object) {
        YokaiEncounter encounter = YokaiEncounterCatalog.findBySpawnId(spawnIdentifierOf(object));
        return encounter == null ? YokaiEncounterCatalog.defaultEncounter() : encounter;
    }

    private void handleInteractions() {
        if (inventoryOverlay.isOpen()) {
            promptLabel.setVisible(false);
            return;
        }

        MapLayer triggersLayer = map.getLayers().get("triggers");
        if (triggersLayer == null) {
            promptLabel.setVisible(false);
            automaticCombatLocked = false;
            return;
        }

        Vector2 playerPos = player.body.getPosition();
        interactionBox.set(playerPos.x - 24f, playerPos.y - 12f, 48f, 48f);

        boolean ePressed = Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean nearAnyManualTrigger = false;

        // --- 0. WILDES YOKAI: Kontakt ueber die AKTUELLE Entity-Position ---
        // Die alte statische Spawnflaeche loest keinen Kampf mehr aus; die Encounter-Flaeche
        // wandert mit dem Kitsune.
        OverworldYokaiEntity contactedYokai = findContactedYokai();
        boolean insideAutomaticCombatZone = contactedYokai != null;

        if (contactedYokai != null && !automaticCombatLocked) {
            YokaiEncounter contactedEncounter = YokaiEncounterCatalog.findById(contactedYokai.getEncounterId());
            if (contactedEncounter == null) {
                Gdx.app.error("OverworldScreen", "No encounter defined for contact with Yokai '"
                    + contactedYokai.getEncounterId() + "'. No battle started.");
            } else {
                // Sofort sperren, damit bis zum verzoegerten Screen-Wechsel kein zweiter
                // Kampf eingeplant werden kann. Die Sperre endet erst, wenn der Spieler das
                // Kitsune wieder verlassen hat (kein Sofort-Retrigger nach der Rueckkehr).
                automaticCombatLocked = true;

                runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                promptLabel.setVisible(false);

                ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new GameScreen(runSession, assets, contactedEncounter));
                return;
            }
        }

        for (MapObject object : triggersLayer.getObjects()) {
            if (!(object instanceof RectangleMapObject)) {
                continue;
            }

            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            if (!interactionBox.overlaps(rect)) {
                continue;
            }

            String type = object.getProperties().get("type", String.class);
            if (type == null) type = object.getProperties().get("class", String.class);
            if (type == null) continue;

            // --- 1. AUTOMATISCHE TRIGGER ---
            if (TRIGGER_TYPE_YOKAI_SPAWN.equals(type)) {
                // Reine Spawnmarkierung eines wilden Yokai: kein statischer Kampf mehr,
                // der Kontakt laeuft ausschliesslich ueber die Entity (siehe oben).
                continue;
            }

            if (TRIGGER_TYPE_COMBAT_ZONE.equals(type)) {
                insideAutomaticCombatZone = true;

                // Begegnung ueber eine stabile Inhalts-Id aufloesen (Tiled-Objektname oder
                // Trigger-Typ) - niemals ueber Bildschirmkoordinaten.
                YokaiEncounter yokaiEncounter = resolveEncounter(object);

                // Einmalig besiegte Begegnungen bleiben dauerhaft fern. Wiederholbare
                // Kampfzonen behalten die bestehende Orts-Fortschrittslogik.
                if (!yokaiEncounter.isRepeatable()
                    && runSession.isEncounterDefeated(yokaiEncounter.getId())) {
                    continue;
                }

                if (!automaticCombatLocked) {
                    // Sofort sperren, damit bis zum verzögerten Screen-Wechsel
                    // kein zweiter Kampf eingeplant werden kann.
                    automaticCombatLocked = true;

                    runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                    promptLabel.setVisible(false);

                    ((KoiKoiGame) Gdx.app.getApplicationListener())
                        .changeScreen(new GameScreen(runSession, assets, yokaiEncounter));
                    return;
                }

                continue;
            }

            // --- 2. MANUELLE TRIGGER (Shop / Schrein mit Prompt) ---
            if ("shop".equals(type)) {
                promptLabel.setText(" Press [E] : Open Shop ");
                promptLabel.setVisible(true);
                nearAnyManualTrigger = true;

                if (ePressed) {
                    runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                    ((KoiKoiGame) Gdx.app.getApplicationListener())
                        .changeScreen(new ShopScreen(runSession, assets));
                    return;
                }
            } else if ("shrine".equals(type)) {
                promptLabel.setText(" Press [E] : Visit Shrine ");
                promptLabel.setVisible(true);
                nearAnyManualTrigger = true;

                if (ePressed) {
                    runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                    ((KoiKoiGame) Gdx.app.getApplicationListener())
                        .changeScreen(new ShrineScreen(runSession, assets));
                    return;
                }
            }
        }

        // Erst nach dem vollständigen Verlassen aller Kampfzonen darf
        // ein späteres erneutes Betreten wieder einen Kampf starten.
        if (!insideAutomaticCombatZone) {
            automaticCombatLocked = false;
        }

        if (!nearAnyManualTrigger) {
            promptLabel.setVisible(false);
        }
    }

    /** Liefert einen wiederverwendeten Sortierknoten (Pool waechst nur einmalig). */
    private RenderNode obtainRenderNode() {
        if (renderNodeCursor == renderNodePool.size()) {
            renderNodePool.add(new RenderNode(0f, (TiledMapTileMapObject) null));
        }
        return renderNodePool.get(renderNodeCursor++);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        // Reihenfolge: erst die Welt-/Renderobjekte, dann Physik und Modelle.
        if (mapRenderer != null) {
            mapRenderer.dispose();
            mapRenderer = null;
        }
        if (map != null) {
            map.dispose();
            map = null;
        }
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (screenBatch != null) {
            screenBatch.dispose();
            screenBatch = null;
        }
        if (edgeShader != null) {
            edgeShader.dispose();
            edgeShader = null;
        }
        if (windShader != null) {
            windShader.dispose();
            windShader = null;
        }
        if (corruptionEngine != null) {
            corruptionEngine.dispose();
            corruptionEngine = null;
        }
        if (debugRenderer != null) {
            debugRenderer.dispose();
            debugRenderer = null;
        }
        if (world != null) {
            // Erst hier freigeben: vorher werden keine Bodies mehr verwendet.
            world.dispose();
            world = null;
        }
        if (uiStage != null) {
            uiStage.dispose();
            uiStage = null;
        }
        // Wilde Yokai besitzen keine GPU-Ressourcen (nur geliehene TextureRegions);
        // es genuegt, die Liste dieses Screenaufbaus zu leeren.
        yokaiEntities.clear();
        collisionRects.clear();
        // Skin, Atlas, Panel-/Button-Textur und das Spieler-Sheet sind geliehen
        // (Eigentum: GameAssets) und werden hier bewusst NICHT disposet.
    }
}
