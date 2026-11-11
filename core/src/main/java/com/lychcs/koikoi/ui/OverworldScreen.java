package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.combat.CombatStats;
import com.lychcs.koikoi.combat.CardDeckNames;
import com.lychcs.koikoi.combat.HankoCastConfig;
import com.lychcs.koikoi.combat.OmamoriPassives;
import com.lychcs.koikoi.combat.OverworldEcs;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.Targeting;
import com.lychcs.koikoi.combat.YakuEffectApplier;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.ParticleComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SquashStretchComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import com.lychcs.koikoi.combat.system.Mappers;
import com.lychcs.koikoi.combat.system.PlayerControlSystem;
import com.lychcs.koikoi.entities.OverworldYokaiEntity;
import com.lychcs.koikoi.entities.Player;
import com.lychcs.koikoi.graphics.AsciiAsset;
import com.lychcs.koikoi.graphics.AsciiAssetLoader;
import com.lychcs.koikoi.graphics.AsciiFacing;
import com.lychcs.koikoi.graphics.AsciiGlyphRenderer;
import com.lychcs.koikoi.graphics.CorruptionEngine;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.YokaiSpecies;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.YakuEffectKind;
import com.lychcs.koikoi.run.YakuEffects;
import com.lychcs.koikoi.run.YokaiEncounter;
import com.lychcs.koikoi.run.YokaiEncounterCatalog;
import com.lychcs.koikoi.scoring.YakuType;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.AsciiMapLoader;
import com.lychcs.koikoi.world.GrassDisplacementField;
import com.lychcs.koikoi.world.ProcGenMapSystem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Overworld screen of the ASCII hybrid renderer.
 *
 * <p>The map is a plain-text ASCII map loaded by {@link AsciiMapLoader} -
 * there is no Tiled {@code .tmx} dependency any more. Walls ({@code #}) become
 * Box2D collision bodies, {@code @} defines the player spawn, {@code Y} spawns
 * wild Yokai, and {@code T}/{@code S}/{@code H} are the tavern, shop and shrine
 * interactions.</p>
 *
 * <p>The terrain is a dense Caves-of-Qud-style glyph carpet: every tile is
 * covered by {@link AsciiMap#DETAIL_SCALE} x {@link AsciiMap#DETAIL_SCALE}
 * meadow or forest glyphs, baked into per-chunk {@link BitmapFontCache}
 * instances with per-glyph vertex colours and replayed with one draw call per
 * chunk. Every glyph keeps its natural
 * monospace aspect and receives a deterministic moss/slate shade, so the
 * ground reads as typed terminal text rather than a flat tile texture. The
 * player is drawn as the downsampled multi-line ASCII matrix from
 * {@code assets/ascii/player.json} with a small bracketed nameplate, and an
 * ambient shader keeps the whole earth-toned map softly readable while adding
 * a warm lantern glow around the player. Walking feet bend the meadow blades
 * and flatten the ground below them; a spring settles the glyphs back into the
 * static carpet ({@link GrassDisplacementField}).</p>
 */
public class OverworldScreen extends ScreenAdapter {

    /** ASCII map of the currently loaded location. */
    private static final String LOCATION_MAP_PATH = AsciiMapLoader.DEFAULT_MAP_PATH;

    /** Base size of the monospace font used by the dense ASCII terrain grid. */
    private static final int ASCII_FONT_SIZE = 8;
    /** Base size of the player avatar's glyph font. */
    private static final int AVATAR_FONT_SIZE = 10;
    /** Base size of the overhead nameplate font. */
    private static final int NAMEPLATE_FONT_SIZE = 12;
    /** Small cell-sized font for the T/S/H markers above the dense glyph grid. */
    private static final int MARKER_FONT_SIZE = 16;

    /** World size of one terminal cell of the player's ASCII assembly. */
    private static final float PLAYER_GLYPH_SIZE = 10f;
    /** Matrix cells collapsed into one player glyph (keeps the footprint small). */
    private static final int PLAYER_GLYPH_STRIDE = 2;
    /** World size of one terminal cell for companions and wild Yokai. */
    private static final float ENTITY_GLYPH_SIZE = 8f;
    /** Matrix cells collapsed into one entity glyph. */
    private static final int ENTITY_GLYPH_STRIDE = 2;
    /** Player name shown on the ASCII nameplate above the avatar. */
    private static final String PLAYER_NAME = "Senje";
    /** Nameplate text in the bracketed ASCII style. */
    private static final String NAMEPLATE_TEXT = "[ " + PLAYER_NAME + " ]";

    /** Ambient light radius around the player in world pixels. */
    private static final float LIGHT_RADIUS = 520f;
    /** Readable global ambient floor; the lantern is only a soft warm boost. */
    private static final float AMBIENT_DARKNESS = 0.72f;
    /** Smallest/largest horizontal squash accepted by the avatar renderer. */
    private static final float MIN_AVATAR_SCALE = 0.35f;
    private static final float MAX_AVATAR_SCALE = 2.5f;
    /** Developer-only WASD fallback; MOBA right-click movement is authoritative. */
    private static final boolean DEV_WASD_FALLBACK = false;

    /** Glyph cells per side of one cached static terrain chunk. */
    private static final int TERRAIN_CHUNK_CELLS = 16;
    /** Semantic colour bucket indices used by the dense terrain. */
    private static final int BUCKET_MEADOW_DARK = 0;
    private static final int BUCKET_MEADOW_MID = 1;
    private static final int BUCKET_MEADOW_LIT = 2;
    private static final int BUCKET_MEADOW_HILITE = 3;
    private static final int BUCKET_BLOSSOM = 4;
    private static final int BUCKET_BAMBOO = 5;
    private static final int BUCKET_TORII = 6;
    private static final int BUCKET_SAKURA = 7;
    private static final int BUCKET_CANOPY_DARK = 8;
    private static final int BUCKET_CANOPY_MID = 9;
    private static final int BUCKET_ROCK = 10;

    /** Runtime palette texture generated by the PNG-to-ASCII converter. */
    private static final String PALETTE_TEXTURE_PATH = "palette/palette_128.png";
    private static final String PLAYER_DOWN_ASCII_PATH = "ascii/player_down.json";
    private static final String PLAYER_UP_ASCII_PATH = "ascii/player_up.json";
    private static final String PLAYER_SIDE_ASCII_PATH = "ascii/player_side.json";

    /** Soft charcoal-moss world canvas; glyph gaps stay part of the ground, not a quad. */
    private static final Color COLOR_BACKGROUND = new Color(0.025f, 0.050f, 0.030f, 1f);
    private static final Color COLOR_SHADOW_TINT = new Color(0.030f, 0.050f, 0.035f, 1f);

    // Frugal Japanese earth palette: moss, bamboo beige, slate, umber, terracotta.
    private static final Color COLOR_MEADOW_DARK = new Color(0.035f, 0.080f, 0.045f, 1f);
    private static final Color COLOR_MEADOW_MID = new Color(0.058f, 0.135f, 0.080f, 1f);
    private static final Color COLOR_MEADOW_LIT = new Color(0.085f, 0.180f, 0.105f, 1f);
    private static final Color COLOR_MEADOW_HILITE = new Color(0.115f, 0.220f, 0.135f, 1f);
    // Japanese decor: cream blossoms, bamboo beige, terracotta torii, muted sakura.
    private static final Color COLOR_BLOSSOM = new Color(0.280f, 0.260f, 0.160f, 1f);
    private static final Color COLOR_BAMBOO = new Color(0.065f, 0.130f, 0.075f, 1f);
    private static final Color COLOR_TORII = new Color(0.085f, 0.065f, 0.045f, 1f);
    private static final Color COLOR_SAKURA = new Color(0.100f, 0.055f, 0.065f, 1f);
    // Burnt umber canopy, mossy forest mid-tones and slate/teal rock.
    private static final Color COLOR_CANOPY_DARK = new Color(0.022f, 0.048f, 0.028f, 1f);
    private static final Color COLOR_CANOPY_MID = new Color(0.040f, 0.085f, 0.050f, 1f);
    private static final Color COLOR_ROCK = new Color(0.055f, 0.090f, 0.080f, 1f);
    // Interactive grass: pale moss and subdued umber.
    private static final Color COLOR_GRASS_BENT = new Color(0.140f, 0.220f, 0.110f, 1f);
    private static final Color COLOR_GRASS_TRAMPLED = new Color(0.060f, 0.050f, 0.035f, 1f);

    private static final Color COLOR_TAVERN = new Color(0.78f, 0.64f, 0.36f, 1f);
    private static final Color COLOR_SHOP = new Color(0.55f, 0.56f, 0.52f, 1f);
    private static final Color COLOR_SHRINE = new Color(0.74f, 0.48f, 0.48f, 1f);

    private final RunSession runSession;
    /** Geliehene, global verwaltete Assets (Eigentum: GameAssets). */
    private final GameAssets assets;

    /** Geladene ASCII-Karte (reine CPU-Daten). */
    private AsciiMap map;

    private OrthographicCamera camera;
    private SpriteBatch worldBatch;
    /** Screen-lokale Monospace-Schrift der dichten ASCII-Terrainmatrix. */
    private BitmapFont terrainFont;
    /** Monospace-Schrift der Spieler-Glyphmatrix. */
    private BitmapFont avatarFont;
    /** Kleine Sub-Schrift fuer das Namensschild ueber dem Spieler. */
    private BitmapFont nameFont;
    /** Tile-grosse Schrift fuer die T/S/H-Marker. */
    private BitmapFont markerFont;

    /** Chunked static terrain, one per-glyph-coloured cache per chunk. */
    private BitmapFontCache[] terrainChunkCaches;
    private int terrainChunksX;
    /** Zellen mit sichtbarem dynamischem Gras (nur Diagnose, keine statische Zelle wird geloescht). */
    private final Set<Long> activeGrassCells = new HashSet<>();
    /** Temporary diagnostics: prove the new render path is executed at runtime. */
    private static final boolean DEBUG_RENDER_LOG = true;
    private float debugLogTimer;
    private int grassDrawFrames;
    /** Expliziter Vollbild-Zustand, damit F11 zuverlaessig in beide Richtungen schaltet. */
    private boolean fullscreen = Gdx.graphics.isFullscreen();
    private float fullscreenCooldown;

    private BitmapFontCache tavernCache;
    private BitmapFontCache shopCache;
    private BitmapFontCache shrineCache;

    /** ASCII-Matrix des Spielers ({@code assets/ascii/player.json}). */
    private AsciiAsset playerAscii;
    /** Glyph-Renderer der Spielerfigur (keine PNG-Sprites mehr). */
    private AsciiGlyphRenderer playerAvatar;
    /** ASCII renderers for wild Yokai (PNG animation frames are retired). */
    private AsciiGlyphRenderer wildOniAvatar;
    private AsciiGlyphRenderer wildKitsuneAvatar;
    /** 128 Farben aus {@code palette/palette_128.png} (Index -> Farbe). */
    private Color[] paletteColors;
    /** Einmal gemessene Breite des Namensschilds. */
    private float nameplateWidth;
    /** Pre-measured badge layouts for the W-card indicator. */
    private GlyphLayout[] cardBadgeLayouts;

    /** Interaktives Gras: Federn, Fusspositionen und dynamische Glyph-Puffer. */
    private GrassDisplacementField grassField;
    private BitmapFontCache grassBentCache;
    private BitmapFontCache grassTrampledCache;
    /** Weltmasse einer dichten Glyph-Zelle und ihr Basislinien-Versatz. */
    private float terrainCellSize;
    private float terrainBaselineOffset;
    /** Horizontal centring of the natural glyph inside a square terrain cell. */
    private float terrainXInset;
    /** Reused per-cell tint while baking the static terrain caches. */
    private final Color terrainTint = new Color();
    /** Wiederverwendete Walker: Spieler plus drei Begleiter (keine Allokation pro Frame). */
    private final List<GrassDisplacementField.Walker> grassWalkers = new ArrayList<>(4);
    private final GrassDisplacementField.CellConsumer grassGlyphConsumer = this::addGrassGlyph;

    private Player player;
    private Stage uiStage;
    private Label promptLabel;
    private Skin skin;
    private InventoryOverlay inventoryOverlay;
    private TextureAtlas gameAtlas;

    // Post-Processing
    public boolean enableEdgeDetection = true;
    /** Selective soft bloom for markers, seals, particles and Yokai embers. */
    public boolean enableBloom = true;
    /** CRT phosphor scanlines and mask. */
    public boolean enableScanlines = true;

    private FrameBuffer fbo;
    private SpriteBatch screenBatch;
    private TextureRegion fboRegion;
    private ShaderProgram crtShader;
    /** Intermediate target of the ambient darkness / field-of-view pass. */
    private FrameBuffer shadowFbo;
    private TextureRegion shadowRegion;
    private ShaderProgram ambientShader;
    /** Reused world -> screen projection of the player lantern (no allocation per frame). */
    private final Vector3 lightProjection = new Vector3();
    /** Player lamp in normalized viewport space (0..1, bottom-left origin). */
    private float lightNdcX = 0.5f;
    private float lightNdcY = 0.5f;
    private CorruptionEngine corruptionEngine;

    // Verhindert, dass eine Kampfzone direkt nach der Rueckkehr aus einem Kampf
    // erneut ausgeloest wird. Die Sperre endet erst, wenn der Spieler alle
    // automatischen Kampfzonen verlassen hat.
    private boolean automaticCombatLocked;

    /** Schutz gegen Mehrfach-Dispose durch den Screen-Manager. */
    private boolean disposed;

    // Box2D Physik-Welt
    private World world;
    private Box2DDebugRenderer debugRenderer;

    /** Tilegroesse der geladenen ASCII-Map (Roaming-Bereiche der Yokai). */
    private int tileWidth;
    private int tileHeight;
    private float mapPixelWidth;
    private float mapPixelHeight;

    /** Kollisionsrechtecke der Map (dieselbe Quelle wie die Box2D-Waende). */
    private final List<Rectangle> collisionRects = new ArrayList<>();

    /** Wilde Yokai dieses Screenaufbaus (keine statische globale Liste). */
    private final List<OverworldYokaiEntity> yokaiEntities = new ArrayList<>();

    /** Interaktionszellen der ASCII-Map (einmalig beim Laden aufgebaut). */
    private final List<AsciiMap.Tile> tavernTiles = new ArrayList<>();
    private final List<AsciiMap.Tile> shopTiles = new ArrayList<>();
    private final List<AsciiMap.Tile> shrineTiles = new ArrayList<>();
    private final Rectangle triggerBounds = new Rectangle();

    /** Einmalige Methodenreferenz: keine Allokation pro Frame. */
    private final OverworldYokaiEntity.CollisionQuery collisionQuery = this::isBlockedInWorld;

    /** Wiederverwendeter Sortierknoten fuer die Y-Sortierung. */
    private static final class RenderNode implements Comparable<RenderNode> {
        float y;
        Player playerEntity;
        OverworldYokaiEntity yokaiEntity;

        RenderNode setPlayer(float y, Player playerEntity) {
            this.y = y;
            this.playerEntity = playerEntity;
            this.yokaiEntity = null;
            return this;
        }

        RenderNode setYokai(float y, OverworldYokaiEntity yokaiEntity) {
            this.y = y;
            this.yokaiEntity = yokaiEntity;
            this.playerEntity = null;
            return this;
        }

        @Override
        public int compareTo(RenderNode other) {
            return Float.compare(other.y, this.y);
        }
    }

    /** Wiederverwendete Y-Sortier-Liste des laufenden Frames (keine Allokation pro Frame). */
    private final List<RenderNode> renderList = new ArrayList<>();
    /** Pool der Sortierknoten; waechst einmalig bis zur Anzahl der Entities. */
    private final List<RenderNode> renderNodePool = new ArrayList<>();
    private int renderNodeCursor;
    /** Wiederverwendete Interaktionsbox (keine Allokation pro Frame). */
    private final Rectangle interactionBox = new Rectangle();

    // ------------------------------------------------------------------
    // Phase 2: Ashley ECS combat layer
    // ------------------------------------------------------------------

    private static final float HOSTILE_MAX_HEALTH = 140f;
    private static final float HOSTILE_RADIUS = 34f;

    /** Extra world-pixel tolerance around a hostile when right-click picking it. */
    private static final float TARGET_PICK_TOLERANCE = 12f;

    private static final Family SEAL_ENTITIES =
        Family.all(SealComponent.class, TransformComponent.class).get();
    private static final Family PARTICLE_ENTITIES =
        Family.all(ParticleComponent.class, TransformComponent.class).get();
    private static final Family PROJECTILE_ENTITIES =
        Family.all(ProjectileComponent.class, TransformComponent.class).get();
    private static final Family CARD_ENTITIES =
        Family.all(CardProjectileComponent.class, TransformComponent.class).get();

    // Omamori orbit glyphs are retired; passives remain active without a visual orb.

    /** Ashley engine facade (player, hostiles, systems). */
    private OverworldEcs ecs;
    /** Omamori-buffed combat values of the current loadout. */
    private CombatStats combatStats = CombatStats.BASE;

    /** Entity-to-actor bridge for the animated wild Yokai. */
    private final Map<Entity, OverworldYokaiEntity> hostileActors = new HashMap<>();

    /** Reused click-pick snapshots for right-click card targeting (no per-click garbage). */
    private final List<Targeting.CircleTarget> clickTargets = new ArrayList<>();
    private final List<Entity> clickTargetEntities = new ArrayList<>();

    private HankoEffect activeHanko = HankoEffect.WHITE_SEAL;
    private int selectedHankoIndex;
    private String omamoriSignature = "";

    /** Screen time used by the shader phases and the sine bobbing. */
    private float overworldTime;

    private BitmapFont combatFont;
    private BitmapFont particleFont;
    private ShaderProgram sealShader;
    private TextureRegion whitePixel;
    private Label hudLabel;

    private final Vector2 aimWorld = new Vector2();
    /** Reused screen-to-world projection for the mouse aim (no allocation per frame). */
    private final Vector3 aimProjection = new Vector3();

    public OverworldScreen(RunSession runSession, GameAssets assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }

        this.runSession = runSession;
        this.assets = assets;

        // 1. ASCII-Karte laden, mit der dichten Qud-Terrainmatrix dekorieren und Ort melden.
        AsciiMap loadedMap = AsciiMapLoader.load(LOCATION_MAP_PATH);
        map = ProcGenMapSystem.densify(loadedMap, terrainSeed(loadedMap));
        applyLocationMetadata();

        tileWidth = map.getTileSize();
        tileHeight = map.getTileSize();
        mapPixelWidth = map.getWorldWidth();
        mapPixelHeight = map.getWorldHeight();

        // 2. Box2D-Welt ohne Schwerkraft (Top-Down-RPG) erstellen.
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();

        // 3. Waende und Interaktionszellen aus der ASCII-Map ableiten.
        createCollisionBodies();
        collectInteractionTiles();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 1280f, 720f);
        camera.zoom = 1f;

        // 4. Spieler an der '@'-Zelle (oder an der gespeicherten Position) erzeugen.
        float startX = map.tileCenterX(map.getPlayerSpawn().col());
        float startY = map.tileCenterY(map.getPlayerSpawn().row());

        // Eine gespeicherte Position kann noch innerhalb einer Kampfzone liegen.
        automaticCombatLocked = runSession.hasStoredPosition();
        if (runSession.hasStoredPosition()) {
            startX = runSession.getLastPlayerX();
            startY = runSession.getLastPlayerY();
        }
        player = new Player(world, startX, startY);

        // 5. Dichte ASCII-Glyph-Ebenen und die ASCII-Spielermatrix einmalig aufbauen.
        worldBatch = new SpriteBatch();
        terrainFont = FontManager.createMonoFont(ASCII_FONT_SIZE);
        avatarFont = FontManager.createMonoFont(AVATAR_FONT_SIZE);
        nameFont = FontManager.createMonoFont(NAMEPLATE_FONT_SIZE);
        markerFont = FontManager.createMonoFont(MARKER_FONT_SIZE);
        // Keep the marker at its native 16 px glyph size; the 3x3 object
        // assemblies position each glyph explicitly.
        markerFont.getData().setScale(1f);
        Gdx.app.log("OverworldScreen", "Markers: font=" + MARKER_FONT_SIZE
            + "px scale=" + markerFont.getData().scaleX);
        buildDenseTerrainCaches();
        buildMarkerCaches();
        createPlayerAvatar();

        // 6. UI-Stage fuer Overworld-Popups und das Inventar.
        uiStage = new Stage(new FitViewport(1280f, 720f));
        // Geliehene, global verwaltete Skin: kein Dispose in diesem Screen.
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        promptLabel = new Label("", skin);
        promptLabel.setFontScale(0.8f);

        Table promptTable = new Table();
        promptTable.setFillParent(true);
        promptTable.bottom().padBottom(80f);
        promptTable.add(promptLabel);
        uiStage.addActor(promptTable);
        promptLabel.setVisible(false);

        // Geliehener, global geladener Atlas (kein Laden, kein Dispose).
        gameAtlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // 7. Ashley-ECS-Kampfschicht: Spieler, drei Yokai-Begleiter, Hanko, Omamori.
        initCombatLayer();

        // 8. Wilde Yokai als animierte Actors UND als Hostile-Entities erzeugen.
        createYokaiEntities();

        // 9. Yaku payloads earned in the card battle are applied now that both
        // the player and the hostiles exist.
        applyPendingYakuEffects();

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

        // Kompaktes Kampf-HUD (Mana, Dash, Hanko, Omamori).
        hudLabel = new Label("", skin);
        hudLabel.setFontScale(0.62f);
        hudLabel.setColor(FontManager.COLOR_TEXT_GOLD);

        Table hudTable = new Table();
        hudTable.setFillParent(true);
        hudTable.top().left().pad(14f);
        hudTable.add(hudLabel);
        uiStage.addActor(hudTable);

        // 7. CRT-Phosphor-Post-Processing, Sichtfeld-Schatten und Korruptions-Simulation.
        screenBatch = new SpriteBatch();
        crtShader = compileShader("crt_phosphor",
            "shaders/default.vert", "shaders/crt_phosphor.frag");
        ambientShader = compileShader("ambient_shadow",
            "shaders/default.vert", "shaders/ambient_shadow.frag");
        Gdx.app.log("OverworldScreen", "ambient_shadow compiled=" + ambientShader.isCompiled()
            + " u_lightRadius=" + ambientShader.getUniformLocation("u_lightRadius")
            + " u_lightCenter=" + ambientShader.getUniformLocation("u_lightCenter")
            + " sourceHasOrganic="
            + Gdx.files.internal("shaders/ambient_shadow.frag").readString().contains("organic"));

        corruptionEngine = new CorruptionEngine();

        Gdx.app.log("OverworldScreen", "Loaded ASCII map '" + map.getLocationId() + "' ("
            + map.getWidth() + "x" + map.getHeight() + ", tile " + tileWidth + "px) from "
            + map.getSource() + " with " + yokaiEntities.size() + " Yokai spawn(s).");
    }

    // ------------------------------------------------------------------
    // Phase 2: Ashley ECS setup
    // ------------------------------------------------------------------

    /** Creates fonts, shaders and the manual-combat ECS. */
    private void initCombatLayer() {
        combatStats = computeCombatStats();

        combatFont = FontManager.createFont(16);
        particleFont = FontManager.createFont(10);

        sealShader = compileShader("hanko_seal",
            "shaders/default.vert", "shaders/hanko_seal.frag");
        whitePixel = skin.getRegion("white");

        ecs = new OverworldEcs(
            player.body.getPosition().x,
            player.body.getPosition().y,
            List.of(),
            combatStats,
            tileWidth,
            this::onHostileDefeated,
            this::isBlockedAtWorldPoint);

        ecs.getHankoConfig().radius = combatStats.sealRadius();
        updateHankoSelection();
        refreshOmamoriLoadout(true);

        Gdx.app.log("OverworldScreen", "Ashley ECS ready: manual combat only ("
            + "floating companions/orbits retired), seal radius "
            + Math.round(combatStats.sealRadius()) + "px, mana regen "
            + Math.round(combatStats.manaRegenPerSecond()) + "/s.");
    }

    // ------------------------------------------------------------------
    // Yaku payloads
    // ------------------------------------------------------------------

    /**
     * Drains the Yaku payloads queued by the card battle and applies exactly one
     * unique effect per completed Yaku.
     */
    private void applyPendingYakuEffects() {
        if (ecs == null || runSession == null) {
            return;
        }
        List<YakuType> pending = runSession.drainPendingYakuEffects();
        YakuEffectApplier applier = new YakuEffectApplier(ecs);
        TransformComponent target = resolveYakuTarget();
        float targetX = target == null ? Float.NaN : target.x;
        float targetY = target == null ? Float.NaN : target.y;
        for (int i = 0; i < pending.size(); i++) {
            YakuType type = pending.get(i);
            YakuEffectKind kind = YakuEffects.kindFor(type);
            applier.apply(kind, targetX, targetY);
            Gdx.app.log("OverworldScreen", "Yaku payload " + type + " -> " + kind
                + " at " + Math.round(targetX) + "," + Math.round(targetY));
        }
    }

    /**
     * Yaku finishers resolve on the nearest living hostile so their rune/ink
     * bursts are visible at the combat target; without hostiles they fall back
     * to the player.
     */
    private TransformComponent resolveYakuTarget() {
        TransformComponent player = ecs.getPlayerTransform();
        if (player == null) {
            return null;
        }
        TransformComponent nearest = player;
        float bestDistance = Float.MAX_VALUE;
        for (Entity hostileEntity : ecs.getHostiles()) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            TransformComponent transform = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || transform == null || health.isDead()) {
                continue;
            }
            float dx = transform.x - player.x;
            float dy = transform.y - player.y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = transform;
            }
        }
        return nearest;
    }

    private ShaderProgram compileShader(String name, String vertexPath, String fragmentPath) {
        ShaderProgram shader = new ShaderProgram(
            Gdx.files.internal(vertexPath),
            Gdx.files.internal(fragmentPath));

        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new GdxRuntimeException("Failed to compile " + name + " shader:\n" + log);
        }
        return shader;
    }

    /** Neutral combat values scaled by the Omamori passives of the active slots. */
    private CombatStats computeCombatStats() {
        OmamoriPassives.Stats passives =
            OmamoriPassives.aggregate(runSession.getActiveOmamoris());
        return passives.applyTo(CombatStats.BASE);
    }

    // ------------------------------------------------------------------
    // Hanko selection and Omamori passives
    // ------------------------------------------------------------------

    /** Reads the 1-9 selection keys and mirrors the chosen Hanko into the config. */
    private void updateHankoSelection() {
        List<HankoEffect> purchased = runSession.getPurchasedHankos();

        if (purchased.isEmpty()) {
            activeHanko = HankoEffect.WHITE_SEAL;
            selectedHankoIndex = 0;
        } else {
            for (int i = 0; i < Math.min(9, purchased.size()); i++) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                    selectedHankoIndex = i;
                }
            }
            selectedHankoIndex = Math.floorMod(selectedHankoIndex, purchased.size());
            activeHanko = purchased.get(selectedHankoIndex);
        }

        applyHankoProfile(ecs.getHankoConfig(), activeHanko);
    }

    /** Per-Hanko damage, slow, radius factor and glow colour. */
    private void applyHankoProfile(HankoCastConfig config, HankoEffect effect) {
        config.hankoIndex = selectedHankoIndex;
        config.label = HankoCatalog.getName(effect).toUpperCase(Locale.ROOT);

        float radiusFactor;
        switch (effect) {
            case BLACK_SEAL -> {
                config.damage = 58f;
                config.slowFactor = 0.60f;
                radiusFactor = 0.90f;
                setSealColor(config, 0.62f, 0.42f, 0.95f);
            }
            case GOLDEN_SEAL -> {
                config.damage = 46f;
                config.slowFactor = 0.55f;
                radiusFactor = 1.10f;
                setSealColor(config, 1.00f, 0.82f, 0.32f);
            }
            case VOID_SEAL -> {
                config.damage = 64f;
                config.slowFactor = 0.40f;
                radiusFactor = 0.95f;
                setSealColor(config, 0.78f, 0.32f, 1.00f);
            }
            case STONE_SEAL -> {
                config.damage = 36f;
                config.slowFactor = 0.28f;
                radiusFactor = 0.85f;
                setSealColor(config, 0.70f, 0.76f, 0.82f);
            }
            case POLYCHROME_SEAL -> {
                config.damage = 50f;
                config.slowFactor = 0.50f;
                radiusFactor = 1.15f;
                setSealColor(config, 0.45f, 1.00f, 0.85f);
            }
            case BLOOD_SEAL -> {
                config.damage = 72f;
                config.slowFactor = 0.65f;
                radiusFactor = 1.00f;
                setSealColor(config, 1.00f, 0.28f, 0.34f);
            }
            default -> {
                config.damage = 42f;
                config.slowFactor = 0.50f;
                radiusFactor = 1.00f;
                setSealColor(config, 0.95f, 0.95f, 0.85f);
            }
        }

        config.radius = combatStats.sealRadius() * radiusFactor;
    }

    private static void setSealColor(HankoCastConfig config, float red, float green, float blue) {
        config.red = red;
        config.green = green;
        config.blue = blue;
    }

    /**
     * Recomputes the passive stats and Hanko profile when the active Omamori
     * loadout changed. Orbit glyph visuals are retired; the passives remain.
     */
    private void refreshOmamoriLoadout(boolean force) {
        List<Omamori> active = runSession.getActiveOmamoris();
        StringBuilder signature = new StringBuilder();
        for (Omamori omamori : active) {
            signature.append(omamori.getClass().getSimpleName()).append('|');
        }
        String signatureText = signature.toString();

        if (!force && signatureText.equals(omamoriSignature)) {
            return;
        }
        omamoriSignature = signatureText;

        combatStats = computeCombatStats();
        ecs.applyStats(combatStats);
        applyHankoProfile(ecs.getHankoConfig(), activeHanko);
    }

    // ------------------------------------------------------------------
    // Phase 2: frame input, ECS/Box2D bridging and HUD
    // ------------------------------------------------------------------

    /** Fills the ECS input snapshot from MOBA mouse controls and ability keys. */
    private void updateCombatInput(float delta) {
        overworldTime += delta;

        PlayerInput input = ecs.getInput();
        boolean paused = inventoryOverlay.isOpen();

        // WASD is intentionally disabled for movement. Flip this developer flag
        // only if a keyboard fallback is needed (target movement still wins).
        input.moveX = DEV_WASD_FALLBACK && !paused ? axis(Input.Keys.D, Input.Keys.A) : 0f;
        input.moveY = DEV_WASD_FALLBACK && !paused ? axis(Input.Keys.W, Input.Keys.S) : 0f;

        // Current mouse position in world coordinates, shared by right-click
        // navigation, card targeting and the Q dash direction.
        aimProjection.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(aimProjection);
        aimWorld.set(aimProjection.x, aimProjection.y);
        input.aimX = aimWorld.x;
        input.aimY = aimWorld.y;

        // Left mouse = manual card throw toward the cursor.
        boolean attackRequested = !paused
            && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        // Right mouse = MOBA target input. A living hostile under the cursor is
        // attacked with a card (Twisted Fate style); empty ground is a move order.
        if (paused) {
            input.hasMoveTarget = false;
        } else if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            Entity targetEntity = hostileAt(aimWorld.x, aimWorld.y);
            TransformComponent target = targetEntity == null
                ? null : Mappers.TRANSFORM.get(targetEntity);
            if (target != null) {
                // Aim exactly at the target centre instead of the cursor pixel.
                aimWorld.set(target.x, target.y);
                input.aimX = target.x;
                input.aimY = target.y;
                input.hasMoveTarget = false;
                if (isWithinCardRange(target)) {
                    attackRequested = true;
                    Gdx.app.log("OverworldScreen", "Right-click card attack at world="
                        + Math.round(target.x) + "," + Math.round(target.y));
                } else {
                    // Out of card range: walk toward the target until in range.
                    input.moveTarget.set(target.x, target.y);
                    input.hasMoveTarget = true;
                    Gdx.app.log("OverworldScreen", "Target out of range; chasing world="
                        + Math.round(target.x) + "," + Math.round(target.y));
                }
            } else {
                input.moveTarget.set(aimWorld);
                input.hasMoveTarget = true;
                Gdx.app.log("OverworldScreen", "Move target set: world="
                    + Math.round(aimWorld.x) + "," + Math.round(aimWorld.y));
            }
        }

        // Q = dash toward the mouse; S = stop; F = Hanko cast (E is interact).
        input.dashRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.Q);
        if (input.dashRequested) {
            Gdx.app.log("OverworldScreen", "Q dash toward aim=("
                + Math.round(aimWorld.x) + "," + Math.round(aimWorld.y) + ")");
        }
        input.stopRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.S);
        input.basicAttackRequested = attackRequested;
        if (input.basicAttackRequested) {
            Gdx.app.log("OverworldScreen", "Card throw toward aim=("
                + Math.round(input.aimX) + "," + Math.round(input.aimY) + ")");
        }
        input.cardSelectRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.W);
        input.castRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.F);
        input.time = overworldTime;
    }

    /**
     * Living hostile whose hit circle (plus a small cursor tolerance) contains
     * the world point, or {@code null}. Scratch lists are reused per call.
     */
    private Entity hostileAt(float worldX, float worldY) {
        if (ecs == null) {
            return null;
        }
        clickTargets.clear();
        clickTargetEntities.clear();

        for (Entity hostileEntity : ecs.getHostiles()) {
            TransformComponent transform = Mappers.TRANSFORM.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            if (transform == null || hostile == null || health == null || health.isDead()) {
                continue;
            }
            clickTargets.add(new Targeting.CircleTarget(
                clickTargets.size(), transform.x, transform.y, hostile.radius, true));
            clickTargetEntities.add(hostileEntity);
        }

        int index = Targeting.pickIndex(worldX, worldY, TARGET_PICK_TOLERANCE, clickTargets);
        return index < 0 ? null : clickTargetEntities.get(index);
    }

    /** True when the card's maximum travel distance covers the hostile centre. */
    private boolean isWithinCardRange(TransformComponent target) {
        TransformComponent origin = ecs.getPlayerTransform();
        if (origin == null) {
            return false;
        }
        float originY = origin.y + PlayerControlSystem.PLAYER_CENTER_OFFSET;
        float dx = target.x - origin.x;
        float dy = target.y - originY;
        float range = PlayerControlSystem.CARD_RANGE_TILES * Math.max(1, tileWidth);
        return dx * dx + dy * dy <= range * range;
    }

    private static float axis(int positiveKey, int negativeKey) {
        float value = 0f;
        if (Gdx.input.isKeyPressed(positiveKey)) {
            value += 1f;
        }
        if (Gdx.input.isKeyPressed(negativeKey)) {
            value -= 1f;
        }
        return value;
    }

    /** Copies the authoritative Box2D position into the ECS before systems run. */
    private void syncPlayerIntoEcs() {
        Vector2 position = player.body.getPosition();
        ecs.getPlayerTransform().teleport(position.x, position.y);
    }

    /** Forwards the ECS-determined velocity onto the Box2D body. */
    private void applyPlayerMotionToBody() {
        if (inventoryOverlay.isOpen()) {
            player.body.setLinearVelocity(0f, 0f);
            return;
        }
        MotionComponent motion = ecs.getPlayerMotion();
        player.body.setLinearVelocity(motion.vx, motion.vy);
    }

    /** Copies the post-physics Box2D position back into the ECS. */
    private void syncPlayerFromBody() {
        Vector2 position = player.body.getPosition();
        ecs.getPlayerTransform().teleport(position.x, position.y);
    }

    /** Copies the roaming actor positions into their hostile ECS entities. */
    private void syncHostilesIntoEcs() {
        for (Map.Entry<Entity, OverworldYokaiEntity> entry : hostileActors.entrySet()) {
            TransformComponent transform = Mappers.TRANSFORM.get(entry.getKey());
            if (transform != null) {
                transform.teleport(entry.getValue().getWorldX(), entry.getValue().getWorldY());
            }
        }
    }

    /** Called exactly once when an ECS hostile's health reaches zero. */
    private void onHostileDefeated(Entity entity, HostileComponent hostile) {
        OverworldYokaiEntity actor = hostileActors.remove(entity);
        if (actor != null) {
            yokaiEntities.remove(actor);
        }

        if (hostile.encounterId != null && !hostile.encounterId.isBlank()) {
            runSession.markEncounterDefeated(hostile.encounterId);

            // Overworld exorcism counts as a victory: grant the species reward
            // (idempotent, so a later duel cannot duplicate it).
            YokaiEncounter encounter = YokaiEncounterCatalog.findById(hostile.encounterId);
            if (encounter != null) {
                runSession.grantShikigamiReward(encounter);
            }
        }

        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        if (transform != null && ecs != null) {
            ecs.sparks(transform.x, transform.y, 20);
        }
    }

    private void updateHud() {
        if (hudLabel == null || ecs == null) {
            return;
        }

        PlayerComponent playerComponent = ecs.getPlayer();
        HankoCastConfig config = ecs.getHankoConfig();

        String manaBar = asciiBar(playerComponent.mana / playerComponent.maxMana, 12);
        HealthComponent playerHealth = Mappers.HEALTH.get(ecs.getPlayerEntity());
        String healthBar = playerHealth == null ? "------------"
            : asciiBar(playerHealth.ratio(), 12);
        String healthValue = playerHealth == null ? "--"
            : (int) playerHealth.current + "/" + (int) playerHealth.max;
        String lifesteal = playerComponent.lifestealRemaining <= 0f ? "READY"
            : String.format(Locale.ROOT, "%.0f%% %.1fs",
                playerComponent.lifestealPercent * 100f, playerComponent.lifestealRemaining);
        String dash = playerComponent.dashCooldownRemaining <= 0f
            ? "READY" : String.format(Locale.ROOT, "%.1fs", playerComponent.dashCooldownRemaining);
        String card = playerComponent.basicAttackCooldownRemaining <= 0f
            ? "READY" : String.format(Locale.ROOT, "%.1fs",
                playerComponent.basicAttackCooldownRemaining);
        CardDeckComponent deck = ecs.getPlayerDeck();
        String wState = deck == null ? "READY" : deck.stateLabel();
        String deckCards = cardDeckSummary(deck);
        String cardLine = "W-CARD [" + wState + "] " + deckCards;

        hudLabel.setText("HP [" + healthBar + "] " + healthValue
            + "   LIFESTEAL " + lifesteal
            + "   MANA [" + manaBar + "] " + (int) playerComponent.mana + "/"
            + (int) playerComponent.maxMana
            + "   DASH [Q] " + dash
            + "   STOP [S]"
            + "   CARD [LMB/RMB] " + card
            + "\n" + cardLine
            + "\nHANKO " + config.name + " [F]"
            + "   OMAMORI " + runSession.getActiveOmamoris().size() + "/"
            + runSession.getMaxActiveOmamoris()
            + "   [1-9] select hanko");
    }

    private static String asciiBar(float ratio, int width) {
        int filled = Math.round(Math.max(0f, Math.min(1f, ratio)) * width);
        return "=".repeat(filled) + "-".repeat(Math.max(0, width - filled));
    }

    /** Compact equipped-card line for the HUD, highlighting the active slot. */
    private String cardDeckSummary(CardDeckComponent deck) {
        if (deck == null) {
            return "RED/BLUE/GOLD";
        }
        Deck playerDeck = runSession == null ? null : runSession.getPlayerDeck();
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < deck.equipped.length; i++) {
            if (i > 0) {
                summary.append(' ');
            }
            boolean active = deck.showsBadge() && i == deck.activeIndex;
            String name = CardDeckNames.forType(deck.equipped[i], playerDeck);
            summary.append(active ? "*[" : "[").append(name).append(']');
        }
        return summary.toString();
    }

    /** Stable seed so a location always grows the same meadow/forest layout. */
    private static long terrainSeed(AsciiMap map) {
        return 0x5EED_1A5F_0BADF00DL ^ map.getLocationId().hashCode()
            ^ (map.getTileSize() * 0x9E3779B1L);
    }

    /**
     * Meldet Ort und Season der geladenen ASCII-Map an die {@link RunSession}.
     * Ungueltige Seasons werden protokolliert und durch die aktuelle Season
     * ersetzt.
     */
    private void applyLocationMetadata() {
        GameSeason mapSeason = runSession.getCurrentSeason();
        String rawSeason = map.getSeasonName();

        try {
            mapSeason = GameSeason.valueOf(rawSeason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Gdx.app.error(
                "OverworldScreen",
                "Invalid season '" + rawSeason + "' in map " + map.getSource()
                    + ". Allowed values: " + Arrays.toString(GameSeason.values())
                    + ". Fallback: " + mapSeason
            );
        }

        runSession.enterLocation(map.getLocationId(), mapSeason);
    }

    // ------------------------------------------------------------------
    // Aufbau aus der ASCII-Map
    // ------------------------------------------------------------------

    /**
     * Backt die dichte ASCII-Terrainmatrix in {@link BitmapFontCache}-Chunks.
     *
     * <p>Every map tile is covered by {@link AsciiMap#DETAIL_SCALE} x
     * {@link AsciiMap#DETAIL_SCALE} glyph cells. The monospace font is scaled so
     * one glyph advance equals one cell, which makes the landscape continuous
     * (no horizontal or vertical gaps). Each chunk is a single cache with
     * per-glyph vertex colours, so the finer grid stays at one call per chunk.</p>
     */
    private void buildDenseTerrainCaches() {
        if (!map.hasDenseTerrain()) {
            map = ProcGenMapSystem.densify(map, terrainSeed(map));
        }

        BitmapFont.BitmapFontData data = terrainFont.getData();
        BitmapFont.Glyph reference = data.getGlyph('#');
        float advance = reference == null ? 0f : reference.xadvance;
        // Use the actual ink height for the uniform scale so the glyph keeps
        // its natural terminal aspect instead of being stretched into a block.
        float inkHeight = reference == null ? 0f : reference.height;
        if (advance <= 0f || inkHeight <= 0f) {
            throw new GdxRuntimeException("Monospace terrain font has no usable glyph metrics");
        }

        float cell = tileWidth / (float) AsciiMap.DETAIL_SCALE;
        // Natural glyph metrics: fit one character into its cell without
        // independent X/Y stretching. The terrain reads as discrete typed
        // characters with a small gap instead of continuous curtain lines.
        float fitX = cell / advance;
        float fitY = cell / inkHeight;
        float glyphScale = Math.min(fitX, fitY);
        data.setScale(glyphScale, glyphScale);
        float baselineOffset = reference.yoffset * glyphScale;
        terrainCellSize = cell;
        terrainBaselineOffset = baselineOffset;
        terrainXInset = (cell - advance * glyphScale) * 0.5f;

        Gdx.app.log("OverworldScreen", "Dense ASCII grid: " + cell + "px cells ("
            + map.getDenseTerrainWidth() + "x" + map.getDenseTerrainHeight() + " glyphs), glyph "
            + reference.width + "x" + reference.height + "px, advance " + advance
            + ", natural scale " + String.format(Locale.ROOT, "%.2f", glyphScale));

        int denseWidth = map.getDenseTerrainWidth();
        int denseHeight = map.getDenseTerrainHeight();
        terrainChunksX = (denseWidth + TERRAIN_CHUNK_CELLS - 1) / TERRAIN_CHUNK_CELLS;
        int chunkCount = terrainChunksX
            * ((denseHeight + TERRAIN_CHUNK_CELLS - 1) / TERRAIN_CHUNK_CELLS);
        terrainChunkCaches = new BitmapFontCache[chunkCount];

        for (int chunk = 0; chunk < chunkCount; chunk++) {
            terrainChunkCaches[chunk] = new BitmapFontCache(terrainFont, true);
            buildTerrainChunk(chunk);
        }

        // Dynamic overlay for interactive grass; rebuilt each frame from the field.
        grassBentCache = new BitmapFontCache(terrainFont, true);
        grassTrampledCache = new BitmapFontCache(terrainFont, true);
        grassField = new GrassDisplacementField(map);
    }

    /**
     * Builds one static chunk exactly once. All glyphs share a single
     * per-glyph-coloured cache, so the finer 8 px terminal grid does not
     * multiply draw calls by the number of semantic colour buckets.
     */
    private void buildTerrainChunk(int chunkIndex) {
        int startCol = (chunkIndex % terrainChunksX) * TERRAIN_CHUNK_CELLS;
        int startRow = (chunkIndex / terrainChunksX) * TERRAIN_CHUNK_CELLS;
        int endCol = Math.min(startCol + TERRAIN_CHUNK_CELLS, map.getDenseTerrainWidth());
        int endRow = Math.min(startRow + TERRAIN_CHUNK_CELLS, map.getDenseTerrainHeight());

        BitmapFontCache cache = terrainChunkCaches[chunkIndex];
        cache.clear();
        int glyphCount = 0;

        for (int row = startRow; row < endRow; row++) {
            float baseline = mapPixelHeight - (row + 1) * terrainCellSize - terrainBaselineOffset;
            for (int col = startCol; col < endCol; col++) {
                char glyph = map.denseTerrainAt(col, row);
                int bucket = bucketFor(glyph);
                cache.addText(String.valueOf(glyph),
                    col * terrainCellSize + terrainXInset, baseline);
                // Per-cell shade variation turns the old flat colour fields into
                // an uneven, hand-typed ink carpet.
                cache.setColors(terrainTint(bucket, col, row), glyphCount, glyphCount + 1);
                glyphCount++;
            }
        }
    }

    /**
     * Deterministic per-cell tint of a dense terrain glyph. Small brightness
     * and warm/cool jitter keep moss and slate patches organic instead of
     * reading as one solid colour block.
     */
    private Color terrainTint(int bucket, int col, int row) {
        Color base = bucketColor(bucket);
        float hash = cellHash01(col, row);
        float shade = 0.92f + 0.10f * hash;
        if (bucket == BUCKET_BLOSSOM) {
            shade = 0.96f + 0.08f * hash;
        } else if (bucket == BUCKET_CANOPY_DARK) {
            shade = 0.94f + 0.08f * hash;
        }
        float warm = (hash - 0.5f) * 0.04f;
        terrainTint.set(
            clampChannel(base.r * shade * (1f + warm)),
            clampChannel(base.g * shade),
            clampChannel(base.b * shade * (1f - warm)),
            1f);
        return terrainTint;
    }

    /** Stable 0..1 hash of one dense terrain cell. */
    private static float cellHash01(int col, int row) {
        int hash = col * 0x27d4eb2d ^ row * 0x9e3779b9;
        hash ^= hash >>> 15;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }

    private static float clampChannel(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Draws the static terrain chunks (one per-glyph-coloured cache each). */
    private void drawTerrainChunks() {
        for (BitmapFontCache cache : terrainChunkCaches) {
            cache.draw(worldBatch);
        }
    }

    /** Colour bucket index of one dense terrain glyph. */
    private static int bucketFor(char glyph) {
        return switch (glyph) {
            case ',' -> BUCKET_MEADOW_DARK;
            case '.', '\'' -> BUCKET_MEADOW_MID;
            case ';' -> BUCKET_MEADOW_LIT;
            case '/', '\\' -> BUCKET_MEADOW_HILITE;
            case '|' -> BUCKET_BAMBOO;
            case '*', 'o', '°' -> BUCKET_BLOSSOM;
            case 'H', 'Y', '!' -> BUCKET_BAMBOO;
            case '-', '=', '#', 'T', '+' -> BUCKET_TORII;
            case '~', 'x' -> BUCKET_SAKURA;
            case '0' -> BUCKET_CANOPY_DARK;
            case 'Q', '@' -> BUCKET_CANOPY_MID;
            case '%' -> BUCKET_ROCK;
            default -> BUCKET_CANOPY_MID;
        };
    }

    private static Color bucketColor(int bucket) {
        return switch (bucket) {
            case BUCKET_MEADOW_DARK -> COLOR_MEADOW_DARK;
            case BUCKET_MEADOW_MID -> COLOR_MEADOW_MID;
            case BUCKET_MEADOW_LIT -> COLOR_MEADOW_LIT;
            case BUCKET_MEADOW_HILITE -> COLOR_MEADOW_HILITE;
            case BUCKET_BLOSSOM -> COLOR_BLOSSOM;
            case BUCKET_BAMBOO -> COLOR_BAMBOO;
            case BUCKET_TORII -> COLOR_TORII;
            case BUCKET_SAKURA -> COLOR_SAKURA;
            case BUCKET_CANOPY_DARK -> COLOR_CANOPY_DARK;
            case BUCKET_CANOPY_MID -> COLOR_CANOPY_MID;
            default -> COLOR_ROCK;
        };
    }

    private static long cellKey(int col, int row) {
        return ((long) row << 32) ^ (col & 0xFFFFFFFFL);
    }

    /**
     * Advances the foot-driven grass springs and rebuilds the two dynamic
     * glyph caches (bent blades, trampled ground).
     */
    private void updateGrassDisplacement(float delta) {
        if (grassField == null || player == null || ecs == null) {
            return;
        }

        List<Entity> companions = ecs.getCompanions();
        ensureGrassWalkerCount(1 + companions.size());

        Vector2 position = player.body.getPosition();
        Vector2 velocity = player.body.getLinearVelocity();
        GrassDisplacementField.Walker playerWalker = grassWalkers.get(0);
        playerWalker.setFootPosition(position.x, position.y);
        playerWalker.setVelocity(velocity.x, velocity.y);

        for (int i = 0; i < companions.size(); i++) {
            TransformComponent transform = Mappers.TRANSFORM.get(companions.get(i));
            GrassDisplacementField.Walker walker = grassWalkers.get(i + 1);
            if (transform == null) {
                walker.setVelocity(0f, 0f);
                continue;
            }
            walker.moveTo(transform.x, transform.y, delta);
        }

        grassField.update(delta, grassWalkers);

        grassBentCache.clear();
        grassTrampledCache.clear();
        activeGrassCells.clear();
        // Dynamic blades are pure alpha overlays on top of the intact static
        // carpet. No static cell is cleared, so no rectangular footprint can
        // appear around the player or companions.
        grassField.forEach(grassGlyphConsumer);

        grassBentCache.setColors(COLOR_GRASS_BENT);
        grassTrampledCache.setColors(COLOR_GRASS_TRAMPLED);
    }

    /** Grows/shrinks the reused walker list without per-frame allocations. */
    private void ensureGrassWalkerCount(int count) {
        while (grassWalkers.size() < count) {
            grassWalkers.add(new GrassDisplacementField.Walker());
        }
        while (grassWalkers.size() > count) {
            grassWalkers.remove(grassWalkers.size() - 1);
        }
    }

    /** Adds one dynamic glyph at its dense grid cell. */
    private void addGrassGlyph(int col, int row, char glyph, boolean trampled, float strength) {
        activeGrassCells.add(cellKey(col, row));

        float x = col * terrainCellSize + terrainXInset;
        float y = mapPixelHeight - (row + 1) * terrainCellSize - terrainBaselineOffset;
        if (trampled) {
            grassTrampledCache.addText(String.valueOf(glyph), x, y);
        } else {
            grassBentCache.addText(String.valueOf(glyph), x, y);
        }
    }

    /**
     * Draws the bent / trampled glyphs as a pure SRC_ALPHA overlay directly on
     * top of the intact static carpet. No static glyph is erased, so no masking
     * quad, no colour block and no rectangular footprint can appear.
     */
    private void drawGrassDisplacement() {
        if (activeGrassCells.isEmpty()) {
            return;
        }

        grassDrawFrames++;
        worldBatch.setShader(null);
        grassBentCache.draw(worldBatch);
        grassTrampledCache.draw(worldBatch);
    }

    /** Bakes the bright T/S/H trigger glyphs above the dense terrain. */
    /** Small ASCII assemblies that replace the old single-letter trigger markers. */
    private static final String[] TAVERN_MARKER = {
        "===",
        "|T|",
        "/ \\"
    };
    private static final String[] SHOP_MARKER = {
        "___",
        "|S|",
        "|_|"
    };
    private static final String[] SHRINE_MARKER = {
        "\\=/",
        "|H|",
        "/ \\"
    };

    private void buildMarkerCaches() {
        BitmapFont.BitmapFontData markerData = markerFont.getData();

        tavernCache = new BitmapFontCache(markerFont, true);
        shopCache = new BitmapFontCache(markerFont, true);
        shrineCache = new BitmapFontCache(markerFont, true);

        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                char glyph = map.glyphAt(col, row);
                float tileX = col * (float) tileWidth;
                float tileY = map.tileBottomY(row);

                switch (glyph) {
                    case AsciiMap.TAVERN_DOOR ->
                        addMarkerAssembly(tavernCache, markerData, TAVERN_MARKER,
                            tileX, tileY, tileWidth);
                    case AsciiMap.SHOP ->
                        addMarkerAssembly(shopCache, markerData, SHOP_MARKER,
                            tileX, tileY, tileWidth);
                    case AsciiMap.SHRINE ->
                        addMarkerAssembly(shrineCache, markerData, SHRINE_MARKER,
                            tileX, tileY, tileWidth);
                    default -> {
                        // Floor, wall and spawn cells are fully covered by the
                        // dense terrain layer; only triggers need a marker.
                    }
                }
            }
        }

        tavernCache.setColors(COLOR_TAVERN);
        shopCache.setColors(COLOR_SHOP);
        shrineCache.setColors(COLOR_SHRINE);
    }

    /**
     * Draws a small multi-glyph ASCII sign centred inside one map tile. The
     * tavern becomes a noren gate, the shop a stall and the shrine a torii, so
     * interactable objects are character assemblies like the entities.
     */
    private void addMarkerAssembly(BitmapFontCache cache, BitmapFont.BitmapFontData fontData,
                                   String[] pattern, float tileX, float tileY, float tileSize) {
        int rows = pattern.length;
        int columns = 0;
        for (String line : pattern) {
            columns = Math.max(columns, line.length());
        }
        if (rows == 0 || columns == 0) {
            return;
        }

        float cell = tileSize / Math.max(rows, columns);
        float originX = tileX + (tileSize - columns * cell) * 0.5f;
        float originY = tileY + (tileSize - rows * cell) * 0.5f;

        for (int row = 0; row < rows; row++) {
            String line = pattern[row];
            for (int col = 0; col < line.length(); col++) {
                char glyph = line.charAt(col);
                if (glyph == ' ') {
                    continue;
                }
                addGlyphInCell(cache, fontData, glyph,
                    originX + col * cell,
                    originY + (rows - 1 - row) * cell,
                    cell);
            }
        }
    }

    /** Loads the three directional ASCII matrices and prepares the glyph renderer. */
    private void createPlayerAvatar() {
        AsciiAsset down = AsciiAssetLoader.load(Gdx.files.internal(PLAYER_DOWN_ASCII_PATH));
        AsciiAsset up = AsciiAssetLoader.load(Gdx.files.internal(PLAYER_UP_ASCII_PATH));
        AsciiAsset side = AsciiAssetLoader.load(Gdx.files.internal(PLAYER_SIDE_ASCII_PATH));
        playerAscii = down;
        paletteColors = loadPaletteColors();
        playerAvatar = new AsciiGlyphRenderer(down, up, side, avatarFont, paletteColors,
            PLAYER_GLYPH_SIZE, PLAYER_GLYPH_STRIDE, false, true);
        wildOniAvatar = loadEntityAvatar("ascii/yokai_oni_wild.json");
        wildKitsuneAvatar = loadEntityAvatar("ascii/yokai_kitsune.json");
        Gdx.app.log("OverworldScreen",
            "ASCII entity renderers ready: floating companions=0 wild=2 (PNG entity draws retired)");
        nameplateWidth = new GlyphLayout(nameFont, NAMEPLATE_TEXT).width;
        CardDeckComponent.CardType[] cardTypes = CardDeckComponent.CardType.values();
        cardBadgeLayouts = new GlyphLayout[cardTypes.length];
        Deck badgeDeck = runSession.getPlayerDeck();
        for (CardDeckComponent.CardType type : cardTypes) {
            cardBadgeLayouts[type.ordinal()] = new GlyphLayout(nameFont,
                "[ " + CardDeckNames.forType(type, badgeDeck) + " ]");
        }
        Gdx.app.log("OverworldScreen", "Directional ASCII player ready: down="
            + down.getWidth() + "x" + down.getHeight() + ", up=" + up.getWidth() + "x"
            + up.getHeight() + ", side=" + side.getWidth() + "x" + side.getHeight()
            + " (world " + Math.round(playerAvatar.getWorldWidth()) + "x"
            + Math.round(playerAvatar.getWorldHeight()) + "px) renderer="
            + AsciiGlyphRenderer.BUILD_TAG);
    }

    /** Loads one static ASCII matrix as an entity avatar (no PNG/texture path). */
    private AsciiGlyphRenderer loadEntityAvatar(String path) {
        AsciiAsset asset = AsciiAssetLoader.load(Gdx.files.internal(path));
        return new AsciiGlyphRenderer(asset, avatarFont, paletteColors,
            ENTITY_GLYPH_SIZE, ENTITY_GLYPH_STRIDE);
    }

    /** Samples the shared 128-colour palette PNG into CPU colours. */
    private Color[] loadPaletteColors() {
        FileHandle file = Gdx.files.internal(PALETTE_TEXTURE_PATH);
        if (!file.exists()) {
            throw new GdxRuntimeException("Palette texture not found: " + PALETTE_TEXTURE_PATH);
        }

        Pixmap pixmap = new Pixmap(file);
        try {
            int count = Math.min(AsciiAsset.PALETTE_SIZE, pixmap.getWidth());
            if (count <= 0) {
                throw new GdxRuntimeException("Palette texture is empty: " + PALETTE_TEXTURE_PATH);
            }
            Color[] colors = new Color[AsciiAsset.PALETTE_SIZE];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = new Color(pixmap.getPixel(i % count, 0));
            }
            return colors;
        } finally {
            pixmap.dispose();
        }
    }

    /** Zentriert ein Glyph in eine Zelle (Kachel oder Sub-Zelle). */
    private void addGlyphInCell(BitmapFontCache cache, BitmapFont.BitmapFontData fontData,
                                char glyph, float cellX, float cellY, float cellSize) {
        BitmapFont.Glyph metrics = fontData.getGlyph(glyph);
        float glyphWidth = metrics == null ? 0f : metrics.width * fontData.scaleX;
        float glyphHeight = fontData.capHeight > 0
            ? fontData.capHeight * fontData.scaleY
            : cellSize * 0.75f;
        float left = cellX + (cellSize - glyphWidth) * 0.5f
            - (metrics == null ? 0f : metrics.xoffset * fontData.scaleX);
        float top = cellY + (cellSize + glyphHeight) * 0.5f;
        cache.addText(String.valueOf(glyph), left, top);
    }

    /**
     * Erzeugt fuer jede zusammenhaengende Wandzelle einen statischen Box2D-Koerper
     * und merkt sich dasselbe Rechteck fuer die Yokai-Kollision.
     */
    private void createCollisionBodies() {
        collisionRects.clear();

        for (int row = 0; row < map.getHeight(); row++) {
            int col = 0;
            while (col < map.getWidth()) {
                if (map.glyphAt(col, row) != AsciiMap.WALL) {
                    col++;
                    continue;
                }

                int runStart = col;
                while (col < map.getWidth() && map.glyphAt(col, row) == AsciiMap.WALL) {
                    col++;
                }
                int runLength = col - runStart;

                float x = runStart * (float) tileWidth;
                float y = map.tileBottomY(row);
                float width = runLength * (float) tileWidth;
                float height = tileHeight;

                collisionRects.add(new Rectangle(x, y, width, height));
                createStaticBody(x, y, width, height);
            }
        }
    }

    private void createStaticBody(float x, float y, float width, float height) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        // Box2D platziert Rechtecke ueber ihr Zentrum.
        bodyDef.position.set(x + width * 0.5f, y + height * 0.5f);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width * 0.5f, height * 0.5f);
        body.createFixture(shape, 0.0f);
        shape.dispose();
    }

    /** Sammelt die Interaktionszellen der Karte (einmalig, keine Suche pro Frame). */
    private void collectInteractionTiles() {
        tavernTiles.clear();
        shopTiles.clear();
        shrineTiles.clear();

        tavernTiles.addAll(map.tilesOf(AsciiMap.TAVERN_DOOR));
        shopTiles.addAll(map.tilesOf(AsciiMap.SHOP));
        shrineTiles.addAll(map.tilesOf(AsciiMap.SHRINE));
    }

    /**
     * Erzeugt die wilden Yokai aus den {@code Y}-Zellen. Die Identitaet kommt
     * ausschliesslich aus der Encounter-Registrierung (stabile Inhalts-Id),
     * niemals aus Koordinaten.
     */
    private void createYokaiEntities() {
        for (Entity hostile : new ArrayList<>(hostileActors.keySet())) {
            ecs.removeHostile(hostile);
        }
        hostileActors.clear();
        yokaiEntities.clear();

        for (AsciiMap.YokaiSpawn spawn : map.getYokaiSpawns()) {
            YokaiEncounter encounter = resolveEncounter(spawn);
            if (encounter == null) {
                continue;
            }

            if (!encounter.isRepeatable() && runSession.isEncounterDefeated(encounter.getId())) {
                // Besiegt und nicht wiederholbar: bleibt fuer diesen Run verschwunden.
                continue;
            }

            YokaiSpecies species = encounter.getSpecies();

            OverworldYokaiEntity actor = new OverworldYokaiEntity(
                encounter.getId(),
                species.getId(),
                species.getWildName(),
                OverworldYokaiEntity.WalkAnimations.placeholder(),
                map.tileCenterX(spawn.col()),
                map.tileCenterY(spawn.row()),
                tileWidth,
                tileHeight);
            yokaiEntities.add(actor);

            Entity hostile = ecs.addHostile(
                encounter.getId(),
                actor.getWorldX(),
                actor.getWorldY(),
                HOSTILE_RADIUS,
                HOSTILE_MAX_HEALTH,
                hostileBridge(actor));
            hostileActors.put(hostile, actor);
        }
    }

    /** Bridges ECS hostile effects onto the roaming ASCII actor. */
    private static HostileComponent.HostileActor hostileBridge(OverworldYokaiEntity actor) {
        return new HostileComponent.HostileActor() {
            @Override
            public void setSpeedScale(float scale) {
                actor.setSpeedScale(scale);
            }

            @Override
            public void flashHit(float seconds) {
                actor.flashHit(seconds);
            }

            @Override
            public void knockback(float directionX, float directionY, float distance) {
                actor.knockback(directionX, directionY, distance);
            }
        };
    }

    /** Loest die Encounter-Id einer {@code Y}-Zelle auf; Fallback ist der Standard-Encounter. */
    private YokaiEncounter resolveEncounter(AsciiMap.YokaiSpawn spawn) {
        String encounterId = spawn.encounterId();
        if (encounterId != null) {
            YokaiEncounter encounter = YokaiEncounterCatalog.findById(encounterId);
            if (encounter == null) {
                encounter = YokaiEncounterCatalog.findBySpawnId(encounterId);
            }
            if (encounter != null) {
                return encounter;
            }
            Gdx.app.error("OverworldScreen", "Unknown encounter id '" + encounterId
                + "' for Yokai spawn in map " + map.getSource() + "; using the default encounter.");
        }
        return YokaiEncounterCatalog.defaultEncounter();
    }

    /**
     * Kollisionsabfrage fuer die wilden Yokai: dieselben Wand-Rechtecke wie die
     * Physik des Spielers plus die Mapgrenzen. Reines Praedikat ohne Allokation.
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

    /** Point collision for card projectiles (map bounds + solid wall tile). */
    private boolean isBlockedAtWorldPoint(float x, float y) {
        if (x < 0f || y < 0f || x >= mapPixelWidth || y >= mapPixelHeight) {
            return true;
        }
        int col = (int) (x / tileWidth);
        int row = map.getHeight() - 1 - (int) (y / tileHeight);
        return map.isSolid(col, row);
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
    public void show() {
        Gdx.input.setInputProcessor(uiStage);
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);

        // Kein neuer FBO ohne echte Groessenaenderung und nie mit Groesse 0.
        if (width <= 0 || height <= 0) {
            return;
        }
        if (fbo != null && fbo.getWidth() == width && fbo.getHeight() == height
            && shadowFbo != null && shadowFbo.getWidth() == width
            && shadowFbo.getHeight() == height
            && screenBatch != null) {
            return;
        }

        // 1. Beide FBOs an die neue Bildschirmaufloesung anpassen.
        if (fbo != null) {
            fbo.dispose();
        }
        if (shadowFbo != null) {
            shadowFbo.dispose();
        }

        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        // Frische Region pro FBO: der Y-Flip wird dadurch nie mehrfach angewendet.
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        shadowFbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        shadowRegion = new TextureRegion(shadowFbo.getColorBufferTexture());
        shadowRegion.flip(false, true);

        // 2. Screen-Batch auf die neue Aufloesung eichen.
        if (screenBatch != null) {
            screenBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        }
    }

    @Override
    public void render(float delta) {
        if (fbo == null || shadowFbo == null) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }

        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();

        // Inventar-Toggle und ECS-Eingabe (Aim in Weltkoordinaten).
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            inventoryOverlay.toggle();
        }
        updateCombatInput(delta);
        updateHankoSelection();
        refreshOmamoriLoadout(false);

        // Box2D -> ECS -> Systeme -> Box2D.
        syncPlayerIntoEcs();
        syncHostilesIntoEcs();
        ecs.update(delta);
        applyPlayerMotionToBody();

        world.step(delta, 6, 2);

        player.update(delta, player.body.getLinearVelocity());
        updateYokaiEntities(delta);
        syncPlayerFromBody();
        syncHostilesIntoEcs();

        // Interaktives Gras: Fusspositionen und Bewegungsvektoren einsammeln,
        // Federn integrieren und die dynamischen Glyph-Puffer neu aufbauen.
        updateGrassDisplacement(delta);

        handleInteractions();
        updateHud();

        Vector2 renderPos = player.getRenderPosition();
        updateCamera(renderPos);
        updateLightCenter();

        debugLogTimer -= delta;
        if (DEBUG_RENDER_LOG && debugLogTimer <= 0f) {
            debugLogTimer = 1f;
            Vector2 debugPos = player.body.getPosition();
            System.out.println("[OverworldScreen] RENDER tag=" + AsciiGlyphRenderer.BUILD_TAG
                + " walkPhase=" + String.format(Locale.ROOT, "%.2f", player.getWalkPhase())
                + " moving=" + player.isMoving()
                + " avatarGlyphs=" + (playerAvatar == null ? -1 : playerAvatar.getLastGlyphCount())
                + " avatarFrame=" + (playerAvatar == null ? -1 : playerAvatar.getLastWalkFrame())
                + " matrix=" + (playerAvatar == null ? "-" : playerAvatar.getLastMatrixWidth()
                    + "x" + playerAvatar.getLastMatrixHeight())
                + " facing=" + player.getFacing()
                + " light=" + String.format(Locale.ROOT, "%.3f,%.3f", lightNdcX, lightNdcY)
                + " screen=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
                + " grassCells=" + activeGrassCells.size()
                + " grassDraws=" + grassDrawFrames
                + " cards=" + (ecs == null ? -1
                    : ecs.getEngine().getEntitiesFor(CARD_ENTITIES).size())
                + " pos=" + Math.round(debugPos.x) + "," + Math.round(debugPos.y));
        }

        // ============================================================
        // WELT IN DEN FRAMEBUFFER RENDERN
        // ============================================================
        renderWorld(renderPos);

        // ============================================================
        // CORRUPTION-SIMULATION UND POST-PROCESSING
        // ============================================================
        corruptionEngine.update(delta);

        // ============================================================
        // 1) AMBIENTE-DUNKELHEIT / SICHTFELD: Welt-FBO -> Schatten-FBO
        // ============================================================
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        shadowFbo.begin();
        ScreenUtils.clear(COLOR_BACKGROUND);

        screenBatch.getProjectionMatrix().setToOrtho2D(0f, 0f, screenWidth, screenHeight);
        screenBatch.begin();
        if (ambientShader != null && ambientShader.isCompiled()) {
            screenBatch.setShader(ambientShader);

            ambientShader.setUniformf("u_resolution", (float) screenWidth, (float) screenHeight);
            ambientShader.setUniformf("u_time", overworldTime);
            ambientShader.setUniformf("u_ambient", AMBIENT_DARKNESS);
            // Radius is normalized to the viewport height; the shader corrects x
            // by the screen aspect so the lamp stays circular in screen pixels.
            ambientShader.setUniformf("u_lightRadius", LIGHT_RADIUS / camera.viewportHeight);
            ambientShader.setUniformf("u_shadowColor",
                COLOR_SHADOW_TINT.r, COLOR_SHADOW_TINT.g, COLOR_SHADOW_TINT.b);
            // Normalized 0..1 viewport coordinates, independent of the FBO size.
            ambientShader.setUniformf("u_lightCenter", lightNdcX, lightNdcY);
        }
        screenBatch.draw(fboRegion, 0f, 0f, screenWidth, screenHeight);
        screenBatch.end();
        screenBatch.setShader(null);
        shadowFbo.end();

        // ============================================================
        // 2) CRT-PHOSPHOR: Schatten-FBO -> Bildschirm
        // ============================================================
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        ScreenUtils.clear(COLOR_BACKGROUND);

        screenBatch.getProjectionMatrix().setToOrtho2D(0f, 0f, screenWidth, screenHeight);
        screenBatch.begin();
        if (crtShader != null && crtShader.isCompiled()) {
            screenBatch.setShader(crtShader);
            crtShader.setUniformf("u_resolution", (float) screenWidth, (float) screenHeight);
            crtShader.setUniformf("u_time", overworldTime);
            crtShader.setUniformf("u_bloomStrength", enableBloom ? 0.30f : 0f);
            crtShader.setUniformf("u_scanlineStrength", enableScanlines ? 0.40f : 0f);
            crtShader.setUniformf("u_edgeStrength", enableEdgeDetection ? 0.08f : 0f);
        }
        screenBatch.draw(shadowRegion, 0f, 0f, screenWidth, screenHeight);
        screenBatch.end();
        screenBatch.setShader(null);

        // UI bleibt vom Shader unbeeinflusst.
        uiStage.getViewport().apply();
        uiStage.act(delta);
        uiStage.draw();

        // Dev-Hilfen: F11 toggles fullscreen, F12 writes the current frame.
        if (fullscreenCooldown > 0f) {
            fullscreenCooldown -= delta;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11) && fullscreenCooldown <= 0f) {
            toggleFullscreen();
            fullscreenCooldown = 0.35f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            saveDebugScreenshot();
        }
    }

    /** Two-way F11 toggle: fullscreen -> 1280x720 window, window -> desktop display mode. */
    private void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(1280, 720);
            fullscreen = false;
        } else {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            fullscreen = true;
        }
        Gdx.app.log("OverworldScreen", "Fullscreen=" + Gdx.graphics.isFullscreen()
            + " mode=" + (fullscreen ? "fullscreen" : "windowed")
            + " screen=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight());
    }

    /** Writes the current back buffer to {@code overworld-debug.png} (F12). */
    private void saveDebugScreenshot() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        Pixmap raw = null;
        Pixmap flipped = null;
        try {
            raw = Pixmap.createFromFrameBuffer(0, 0, width, height);
            flipped = new Pixmap(width, height, Pixmap.Format.RGBA8888);

            // glReadPixels returns bottom-up rows; PixmapIO writes top-down.
            ByteBuffer source = raw.getPixels();
            ByteBuffer target = flipped.getPixels();
            int stride = width * 4;
            byte[] row = new byte[stride];
            for (int y = 0; y < height; y++) {
                source.position((height - 1 - y) * stride);
                source.get(row);
                target.position(y * stride);
                target.put(row);
            }

            PixmapIO.writePNG(Gdx.files.local("overworld-debug.png"), flipped);
            Gdx.app.log("OverworldScreen", "Saved overworld-debug.png (" + width + "x" + height
                + ", grass cells " + (grassField == null ? 0 : grassField.activeCellCount()) + ")");
        } catch (RuntimeException e) {
            Gdx.app.error("OverworldScreen", "Could not save overworld-debug.png", e);
        } finally {
            if (raw != null) {
                raw.dispose();
            }
            if (flipped != null) {
                flipped.dispose();
            }
        }
    }

    /** Zentriert die Kamera auf den Spieler und klemmt sie an die Mapgrenzen. */
    private void updateCamera(Vector2 renderPos) {
        // The ASCII avatar is feet-anchored: centre horizontally, look slightly
        // above the feet so the head and the nameplate stay in view.
        float targetX = renderPos.x;
        float targetY = renderPos.y + 52f;

        float camHalfWidth = camera.viewportWidth * 0.5f * camera.zoom;
        float camHalfHeight = camera.viewportHeight * 0.5f * camera.zoom;

        float clampedX = mapPixelWidth <= camHalfWidth * 2f
            ? mapPixelWidth * 0.5f
            : MathUtils.clamp(targetX, camHalfWidth, mapPixelWidth - camHalfWidth);

        float clampedY = mapPixelHeight <= camHalfHeight * 2f
            ? mapPixelHeight * 0.5f
            : MathUtils.clamp(targetY, camHalfHeight, mapPixelHeight - camHalfHeight);

        camera.position.set(clampedX, clampedY, 0f);
        camera.update();
    }

    /**
     * Projects the avatar centre into normalized viewport space (0..1,
     * bottom-left origin). Normalizing by the camera viewport keeps the lamp
     * independent of the window/FBO pixel size, so fullscreen toggles and
     * resizes cannot shift it.
     */
    private void updateLightCenter() {
        float centerY = player.body.getPosition().y
            + playerAvatar.getWorldHeight() * 0.5f;
        lightProjection.set(player.body.getPosition().x, centerY, 0f);
        // Camera.project returns pixels in the current screen/FBO dimensions
        // (Gdx.graphics width/height), NOT camera-viewport pixels. Divide by the
        // exact screen size so fullscreen/resizes cannot scale or shift the lamp.
        camera.project(lightProjection);
        lightNdcX = MathUtils.clamp(lightProjection.x / Gdx.graphics.getWidth(), 0f, 1f);
        lightNdcY = MathUtils.clamp(lightProjection.y / Gdx.graphics.getHeight(), 0f, 1f);
    }

    /** Zeichnet Glyph-Ebenen, Hanko-Siegel und Y-sortierte Entities in den Framebuffer. */
    private void renderWorld(Vector2 renderPos) {
        fbo.begin();
        Gdx.gl.glViewport(0, 0, fbo.getWidth(), fbo.getHeight());
        ScreenUtils.clear(COLOR_BACKGROUND);

        worldBatch.setProjectionMatrix(camera.combined);
        worldBatch.begin();

        // Dichte ASCII-Terrainmatrix in Chunks; dynamisches Gras wird darueber geblendet.
        drawTerrainChunks();

        // Niedergetretenes / gebogenes Gras ueber den statischen Glyphen.
        drawGrassDisplacement();

        // Helle T/S/H-Marker ueber dem Terrain.
        tavernCache.draw(worldBatch);
        shopCache.draw(worldBatch);
        shrineCache.draw(worldBatch);

        // Hanko-Siegel liegen als leuchtende Projektion auf dem Boden.
        renderSeals(worldBatch);

        // Y-Sortier-Liste aufbauen (wiederverwendet, keine Allokation pro Frame).
        renderList.clear();
        renderNodeCursor = 0;
        renderList.add(obtainRenderNode().setPlayer(renderPos.y, player));

        for (int i = 0; i < yokaiEntities.size(); i++) {
            OverworldYokaiEntity entity = yokaiEntities.get(i);
            renderList.add(obtainRenderNode().setYokai(entity.getFootY(), entity));
        }

        Collections.sort(renderList);

        for (int i = 0; i < renderList.size(); i++) {
            RenderNode node = renderList.get(i);
            if (node.playerEntity != null) {
                renderPlayer(worldBatch, node.playerEntity);
            } else if (node.yokaiEntity != null) {
                renderWildYokai(worldBatch, node.yokaiEntity);
            }
        }

        // UI/nameplates sit above every entity glyph, never beneath a sprite.
        renderPlayerNameplate(worldBatch);

        // Zurueck auf den Standard-Shader fuer Bars, Projektile und Partikel.
        worldBatch.setShader(null);
        worldBatch.setColor(Color.WHITE);

        renderHostileHealthBars(worldBatch);
        renderProjectiles(worldBatch);
        renderCardProjectiles(worldBatch);
        renderParticles(worldBatch);

        worldBatch.end();
        fbo.end();
    }

    /** Draws a wild Yokai exclusively from its ASCII matrix (legacy PNG frames retired). */
    private void renderWildYokai(SpriteBatch batch, OverworldYokaiEntity yokai) {
        AsciiGlyphRenderer avatar = wildYokaiAvatar(yokai);
        if (avatar == null) {
            return;
        }
        batch.setShader(null);
        if (yokai.isHitFlashing()) {
            avatar.render(batch, yokai.getWorldX(), yokai.getFootY(), 1f, 1f,
                0f, false, AsciiFacing.DOWN, Color.WHITE,
                0.92f * yokai.getHitFlashRatio());
        } else {
            avatar.render(batch, yokai.getWorldX(), yokai.getFootY(), 1f, 1f,
                0f, false, AsciiFacing.DOWN);
        }
    }

    private AsciiGlyphRenderer wildYokaiAvatar(OverworldYokaiEntity yokai) {
        String species = yokai.getSpeciesId();
        if (species != null && species.toLowerCase(Locale.ROOT).contains("kitsune")) {
            return wildKitsuneAvatar;
        }
        return wildOniAvatar;
    }

    /**
     * Draws the player as the multi-line ASCII matrix from
     * {@code assets/ascii/player.json} plus the bracketed overhead nameplate.
     * The ECS squash &amp; stretch still applies, anchored at the feet.
     */
    private void renderPlayer(SpriteBatch batch, Player playerEntity) {
        // Glyphs are not palette-swapped; the ASCII matrix carries its palette colours.
        batch.setShader(null);

        SquashStretchComponent squash = ecs.getPlayerSquash();
        float scaleX = MathUtils.clamp(squash.scaleX, MIN_AVATAR_SCALE, MAX_AVATAR_SCALE);
        float scaleY = MathUtils.clamp(squash.scaleY, MIN_AVATAR_SCALE, MAX_AVATAR_SCALE);

        Vector2 feet = playerEntity.getRenderPosition();
        float bob = playerEntity.isMoving()
            ? MathUtils.sin(playerEntity.getWalkPhase() * 9f) * 1.5f
            : 0f;
        float bottomY = feet.y + bob;

        playerAvatar.render(batch, feet.x, bottomY, scaleX, scaleY,
            playerEntity.getWalkPhase(), playerEntity.isMoving(), playerEntity.getFacing());
    }

    /** Nameplates are drawn after every ASCII entity so no glyph can cover them. */
    private void renderPlayerNameplate(SpriteBatch batch) {
        if (playerAvatar == null || player == null) {
            return;
        }
        SquashStretchComponent squash = ecs.getPlayerSquash();
        float scaleY = MathUtils.clamp(squash.scaleY, MIN_AVATAR_SCALE, MAX_AVATAR_SCALE);
        Vector2 feet = player.getRenderPosition();
        float bob = player.isMoving()
            ? MathUtils.sin(player.getWalkPhase() * 9f) * 1.5f
            : 0f;
        float nameplateBaseline =
            feet.y + bob + playerAvatar.getWorldHeight() * scaleY + 12f;
        renderNameplate(batch, feet.x, nameplateBaseline);
        renderCardBadge(batch, feet.x, nameplateBaseline + 14f);
    }

    /** Overhead `[ RED ]` / `[ BLUE ]` / `[ GOLD ]` W-card indicator. */
    private void renderCardBadge(SpriteBatch batch, float centerX, float baselineY) {
        if (ecs == null || cardBadgeLayouts == null) {
            return;
        }
        CardDeckComponent deck = ecs.getPlayerDeck();
        if (deck == null || !deck.showsBadge()) {
            return;
        }
        CardDeckComponent.CardType type = deck.activeCard();
        GlyphLayout layout = cardBadgeLayouts[type.ordinal()];
        float x = centerX - layout.width * 0.5f;

        nameFont.setColor(0.01f, 0.015f, 0.012f, 0.9f);
        nameFont.draw(batch, layout, x + 1f, baselineY - 1f);
        nameFont.setColor(type.red, type.green, type.blue, 1f);
        nameFont.draw(batch, layout, x, baselineY);
        nameFont.setColor(Color.WHITE);
    }

    /** Small sub-font nameplate in ASCII bracket style above the avatar. */
    private void renderNameplate(SpriteBatch batch, float centerX, float baselineY) {
        float x = centerX - nameplateWidth * 0.5f;

        nameFont.setColor(0.01f, 0.02f, 0.01f, 0.9f);
        nameFont.draw(batch, NAMEPLATE_TEXT, x + 1f, baselineY - 1f);
        nameFont.setColor(0.94f, 0.90f, 0.68f, 1f);
        nameFont.draw(batch, NAMEPLATE_TEXT, x, baselineY);
        nameFont.setColor(Color.WHITE);
    }

    /** Additive ink: colour effects glow over the existing terminal grid. */
    private static void beginAdditive(SpriteBatch batch) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
    }

    /** Restores the normal transparent sprite blend. */
    private static void endAdditive(SpriteBatch batch) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Glowing Hanko seal quads plus the engraved ring of name letters. */
    private void renderSeals(SpriteBatch batch) {
        batch.setShader(sealShader);
        beginAdditive(batch);
        for (Entity entity : ecs.getEngine().getEntitiesFor(SEAL_ENTITIES)) {
            SealComponent seal = Mappers.SEAL.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (seal == null || transform == null) {
                continue;
            }

            float radius = seal.currentRadius();
            float fade = 1f - MathUtils.clamp((seal.progress() - 0.7f) / 0.3f, 0f, 1f);

            sealShader.setUniformf("u_time", overworldTime);
            sealShader.setUniformf("u_progress", fade);
            sealShader.setUniformf("u_sealColor", seal.red, seal.green, seal.blue);
            batch.setColor(1f, 1f, 1f, 1f);
            // Full-texture draw: the procedural shader needs 0..1 UVs (it ignores the sample).
            batch.draw(whitePixel.getTexture(),
                transform.x - radius, transform.y - radius,
                radius * 2f, radius * 2f);
        }
        batch.setShader(null);

        // Engraved letters rotate slowly around the seal ring.
        for (Entity entity : ecs.getEngine().getEntitiesFor(SEAL_ENTITIES)) {
            SealComponent seal = Mappers.SEAL.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (seal == null || transform == null || seal.label.isEmpty()) {
                continue;
            }

            float radius = seal.currentRadius() * 0.68f;
            float fade = 1f - MathUtils.clamp((seal.progress() - 0.7f) / 0.3f, 0f, 1f);
            String label = seal.label;

            combatFont.setColor(seal.red, seal.green, seal.blue, fade);
            for (int i = 0; i < label.length(); i++) {
                double angle = i * (Math.PI * 2.0 / label.length()) + seal.age * 1.4;
                float letterX = transform.x + (float) Math.cos(angle) * radius;
                float letterY = transform.y + (float) Math.sin(angle) * radius * 0.55f;
                combatFont.draw(batch, String.valueOf(label.charAt(i)), letterX - 4f, letterY + 5f);
            }
        }
        combatFont.setColor(Color.WHITE);
        endAdditive(batch);
        batch.setShader(null);
    }

    /** Small health bar above damaged wild Yokai. */
    private void renderHostileHealthBars(SpriteBatch batch) {
        for (Map.Entry<Entity, OverworldYokaiEntity> entry : hostileActors.entrySet()) {
            HealthComponent health = Mappers.HEALTH.get(entry.getKey());
            if (health == null || health.ratio() >= 1f) {
                continue;
            }

            OverworldYokaiEntity actor = entry.getValue();
            float width = 46f;
            float height = 5f;
            float x = actor.getWorldX() - width * 0.5f;
            float y = actor.getFootY() + 146f;

            batch.setColor(0.05f, 0.05f, 0.08f, 0.85f);
            batch.draw(whitePixel, x - 1f, y - 1f, width + 2f, height + 2f);
            batch.setColor(0.92f, 0.26f, 0.32f, 0.95f);
            batch.draw(whitePixel, x, y, width * health.ratio(), height);
        }
        batch.setColor(Color.WHITE);
    }

    /** Companion projectiles drawn as bright ASCII sparks. */
    private void renderProjectiles(SpriteBatch batch) {
        beginAdditive(batch);
        for (Entity entity : ecs.getEngine().getEntitiesFor(PROJECTILE_ENTITIES)) {
            ProjectileComponent projectile = Mappers.PROJECTILE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (projectile == null || transform == null) {
                continue;
            }

            combatFont.setColor(projectile.red, projectile.green, projectile.blue, 1f);
            combatFont.draw(batch, "+", transform.x - 4f, transform.y + 5f);
        }
        combatFont.setColor(Color.WHITE);
        endAdditive(batch);
    }

    /** Left-click cards: directional, high-contrast glowing earth-tone glyphs. */
    private void renderCardProjectiles(SpriteBatch batch) {
        beginAdditive(batch);
        for (Entity entity : ecs.getEngine().getEntitiesFor(CARD_ENTITIES)) {
            CardProjectileComponent card = Mappers.CARD_PROJECTILE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (card == null || transform == null) {
                continue;
            }

            combatFont.setColor(card.red, card.green, card.blue, 1f);
            combatFont.draw(batch, String.valueOf(card.glyph),
                transform.x - 4f, transform.y + 5f);
        }
        combatFont.setColor(Color.WHITE);
        endAdditive(batch);
    }

    /** Procedural dust and cherry-blossom particles as ASCII glyphs. */
    private void renderParticles(SpriteBatch batch) {
        beginAdditive(batch);
        for (Entity entity : ecs.getEngine().getEntitiesFor(PARTICLE_ENTITIES)) {
            ParticleComponent particle = Mappers.PARTICLE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (particle == null || transform == null) {
                continue;
            }

            particleFont.setColor(particle.red, particle.green, particle.blue, particle.alpha());
            particleFont.draw(batch, particle.glyph, transform.x, transform.y);
        }
        particleFont.setColor(Color.WHITE);
        endAdditive(batch);
    }

    /**
     * Loest Interaktionen ueber die Zellen der ASCII-Map aus: Yokai-Kontakt,
     * Tavernentuer ({@code T}), Shop ({@code S}) und Schrein ({@code H}).
     */
    private void handleInteractions() {
        if (inventoryOverlay.isOpen()) {
            promptLabel.setVisible(false);
            return;
        }

        Vector2 playerPos = player.body.getPosition();
        interactionBox.set(playerPos.x - 24f, playerPos.y - 12f, 48f, 48f);

        boolean ePressed = Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean nearAnyManualTrigger = false;

        // --- 1. WILDES YOKAI: Kontakt ueber die AKTUELLE Entity-Position. ---
        OverworldYokaiEntity contactedYokai = findContactedYokai();
        if (contactedYokai != null && !automaticCombatLocked) {
            YokaiEncounter contactedEncounter =
                YokaiEncounterCatalog.findById(contactedYokai.getEncounterId());
            if (contactedEncounter == null) {
                Gdx.app.error("OverworldScreen", "No encounter defined for contact with Yokai '"
                    + contactedYokai.getEncounterId() + "'. No battle started.");
            } else {
                // Sofort sperren, damit bis zum verzoegerten Screen-Wechsel kein zweiter
                // Kampf eingeplant werden kann. Die Sperre endet erst, wenn der Spieler das
                // Yokai wieder verlassen hat (kein Sofort-Retrigger nach der Rueckkehr).
                automaticCombatLocked = true;

                runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                promptLabel.setVisible(false);

                ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new GameScreen(runSession, assets, contactedEncounter));
                return;
            }
        }

        // --- 2. TAVERNENTUER: startet den bestehenden Kartenkampf (GameScreen). ---
        AsciiMap.Tile tavern = firstOverlapping(tavernTiles);
        if (tavern != null) {
            promptLabel.setText(" Press [E] : Enter Tavern ");
            promptLabel.setVisible(true);
            nearAnyManualTrigger = true;

            if (ePressed) {
                // Der Screen-Wechsel haelt die Overworld-ECS-Verarbeitung an; der
                // vorhandene GameScreen uebernimmt Duel, Belohnungen und Rueckkehr.
                runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                Gdx.app.log("OverworldScreen", "Entering the tavern at column "
                    + (tavern.col() + 1) + ", row " + (tavern.row() + 1)
                    + " (overworld ECS suspended).");

                ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new GameScreen(runSession, assets,
                        YokaiEncounterCatalog.defaultEncounter()));
                return;
            }
        }

        // --- 3. SHOP ---
        AsciiMap.Tile shop = firstOverlapping(shopTiles);
        if (shop != null) {
            promptLabel.setText(" Press [E] : Open Shop ");
            promptLabel.setVisible(true);
            nearAnyManualTrigger = true;

            if (ePressed) {
                runSession.setLastPlayerPosition(playerPos.x, playerPos.y);
                ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new ShopScreen(runSession, assets));
                return;
            }
        }

        // --- 4. SCHREIN ---
        AsciiMap.Tile shrine = firstOverlapping(shrineTiles);
        if (shrine != null) {
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

        // Erst nach dem vollstaendigen Verlassen aller Kampfzonen darf ein spaeteres
        // erneutes Betreten wieder einen Kampf starten.
        if (contactedYokai == null) {
            automaticCombatLocked = false;
        }

        if (!nearAnyManualTrigger) {
            promptLabel.setVisible(false);
        }
    }

    /** Erste Interaktionszelle, deren Kachel die Interaktionsbox beruehrt (oder {@code null}). */
    private AsciiMap.Tile firstOverlapping(List<AsciiMap.Tile> tiles) {
        for (int i = 0; i < tiles.size(); i++) {
            AsciiMap.Tile tile = tiles.get(i);
            map.tileBounds(tile.col(), tile.row(), triggerBounds);
            if (interactionBox.overlaps(triggerBounds)) {
                return tile;
            }
        }
        return null;
    }

    /** Liefert einen wiederverwendeten Sortierknoten (Pool waechst nur einmalig). */
    private RenderNode obtainRenderNode() {
        if (renderNodeCursor == renderNodePool.size()) {
            renderNodePool.add(new RenderNode());
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
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (shadowFbo != null) {
            shadowFbo.dispose();
            shadowFbo = null;
        }
        if (screenBatch != null) {
            screenBatch.dispose();
            screenBatch = null;
        }
        if (worldBatch != null) {
            worldBatch.dispose();
            worldBatch = null;
        }
        if (terrainFont != null) {
            // Screen-lokale Schrift: im Gegensatz zur globalen UI-Schrift wird sie
            // hier freigegeben.
            terrainFont.dispose();
            terrainFont = null;
        }
        if (avatarFont != null) {
            avatarFont.dispose();
            avatarFont = null;
        }
        if (nameFont != null) {
            nameFont.dispose();
            nameFont = null;
        }
        if (markerFont != null) {
            markerFont.dispose();
            markerFont = null;
        }
        terrainChunkCaches = null;
        activeGrassCells.clear();
        tavernCache = null;
        shopCache = null;
        shrineCache = null;
        playerAscii = null;
        playerAvatar = null;
        paletteColors = null;
        grassField = null;
        grassBentCache = null;
        grassTrampledCache = null;
        grassWalkers.clear();

        if (crtShader != null) {
            crtShader.dispose();
            crtShader = null;
        }
        if (ambientShader != null) {
            ambientShader.dispose();
            ambientShader = null;
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

        // Ashley-ECS: Entities freigeben (die Systeme besitzen keine GPU-Ressourcen).
        if (ecs != null) {
            ecs.clear();
            ecs = null;
        }
        hostileActors.clear();

        if (sealShader != null) {
            sealShader.dispose();
            sealShader = null;
        }
        if (combatFont != null) {
            combatFont.dispose();
            combatFont = null;
        }
        if (particleFont != null) {
            particleFont.dispose();
            particleFont = null;
        }
        hudLabel = null;

        // Wilde Yokai besitzen keine GPU-Ressourcen (nur geliehene TextureRegions);
        // es genuegt, die Listen dieses Screenaufbaus zu leeren.
        yokaiEntities.clear();
        collisionRects.clear();
        tavernTiles.clear();
        shopTiles.clear();
        shrineTiles.clear();
        renderList.clear();
        renderNodePool.clear();

        // Skin, Atlas, Panel-/Button-Textur und das Spieler-Sheet sind geliehen
        // (Eigentum: GameAssets) und werden hier bewusst NICHT disposet.
    }
}
