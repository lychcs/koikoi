package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.combat.CardDeckNames;
import com.lychcs.koikoi.combat.CombatStats;
import com.lychcs.koikoi.combat.HankoCastConfig;
import com.lychcs.koikoi.combat.OmamoriPassives;
import com.lychcs.koikoi.combat.OverworldEcs;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.YakuEffectApplier;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardBurstComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.ParticleComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;
import com.lychcs.koikoi.combat.system.Mappers;
import com.lychcs.koikoi.combat.system.PlayerControlSystem;
import com.lychcs.koikoi.entities.OverworldYokaiEntity;
import com.lychcs.koikoi.entities.Player;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.YokaiSpecies;
import com.lychcs.koikoi.render.AsciiPostProcessor;
import com.lychcs.koikoi.render.FirstPersonCameraController;
import com.lychcs.koikoi.render.Heightfield;
import com.lychcs.koikoi.render.HeightmapTerrainRenderer;
import com.lychcs.koikoi.render.JapaneseScenery;
import com.lychcs.koikoi.render.PathPlanner;
import com.lychcs.koikoi.render.SceneryPlan;
import com.lychcs.koikoi.render.TerrainFollow;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.YakuEffectKind;
import com.lychcs.koikoi.run.YakuEffects;
import com.lychcs.koikoi.run.YokaiEncounter;
import com.lychcs.koikoi.run.YokaiEncounterCatalog;
import com.lychcs.koikoi.scoring.YakuType;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.AsciiMapLoader;
import com.lychcs.koikoi.world.WallLayout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * First-person 3D overworld of the ASCII action engine.
 *
 * <p>The ASCII map still defines the gameplay world: walls ({@code #}) become
 * Box2D collision bodies, {@code @} is the player spawn, {@code Y} spawns wild
 * Yokai and {@code T}/{@code S}/{@code H} are the tavern, shop and shrine
 * interactions. The combat layer (Ashley ECS, Yaku payloads, Hanko and Omamori
 * passives) is unchanged.</p>
 *
 * <p>Rendering is now fully 3D: a {@link PerspectiveCamera} (70 degree FOV,
 * near 0.1, far 300) walks the player through a procedurally generated simplex
 * noise heightmap. The scene is drawn into a framebuffer and replayed through
 * {@link AsciiPostProcessor}, which turns the luminance image into the dark
 * moss-green ASCII glyph matrix. Mouse look + WASD move the player relative to
 * the view, and {@code Q} performs a Vayne-style dash in the held WASD
 * direction. The cycling W-card deck is drawn as a bottom-centre FPS HUD.</p>
 *
 * <p>Controls: mouse = look, WASD = camera-relative walk, Q = dash in the WASD
 * direction (crosshair direction when no key is held), LMB = throw card,
 * RMB = lock-on/chase the hostile under the crosshair, R = shuffle/lock the
 * W-card deck, F = Hanko cast, E = interact, I = inventory, ESC = release the
 * pointer, F11 = fullscreen, F12 = debug screenshot.</p>
 */
public class OverworldScreen extends ScreenAdapter {

    /** ASCII map of the currently loaded location. */
    private static final String LOCATION_MAP_PATH = AsciiMapLoader.DEFAULT_MAP_PATH;

    /** libGDX units per ASCII pixel: one 64 px tile becomes one world unit. */
    public static final float WORLD_SCALE = 1f / 64f;

    /** Vertical field of view of the first-person camera. */
    public static final float FIELD_OF_VIEW = 70f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 300f;

    /** Eye height above the procedural ground in world units. */
    private static final float EYE_HEIGHT = 1.65f;
    /** Subtle head bob amplitude while walking, in world units. */
    private static final float HEAD_BOB = 0.035f;

    /** Heightmap patch: map size multiplier plus a fixed horizon margin. */
    private static final float TERRAIN_SIZE_FACTOR = 1.6f;
    private static final float TERRAIN_MARGIN = 52f;
    private static final int TERRAIN_RESOLUTION = 150;
    /** Peak hill height in world units (1 unit = one 64 px tile). */
    private static final float TERRAIN_AMPLITUDE = 3.2f;

    /** Aim distance ahead of the player when no hostile is under the crosshair. */
    private static final float AIM_DISTANCE_TILES = 8f;
    /** Minimum alignment (cosine) for crosshair target picking (~30 degrees). */
    private static final float CROSSHAIR_MIN_DOT = 0.86f;
    /** Convergence rate of the camera / entity terrain following. */
    private static final float TERRAIN_FOLLOW_RATE = 14f;

    private static final float HOSTILE_MAX_HEALTH = 140f;
    private static final float HOSTILE_RADIUS = 34f;

    /** Aggro radius where a Yokai starts actively chasing the player. */
    private static final float HOSTILE_AGGRO_TILES = 7f;
    /** Beyond this leash the Yokai gives up and returns to roaming. */
    private static final float HOSTILE_LEASH_TILES = 14f;
    /** Seconds between two BFS repaths while chasing. */
    private static final float HOSTILE_REPATH_SECONDS = 0.35f;
    /** Waypoint arrival radius in tiles. */
    private static final float HOSTILE_WAYPOINT_TILES = 0.55f;

    private static final Family SEAL_ENTITIES =
        Family.all(SealComponent.class, TransformComponent.class).get();
    private static final Family PARTICLE_ENTITIES =
        Family.all(ParticleComponent.class, TransformComponent.class).get();
    private static final Family PROJECTILE_ENTITIES =
        Family.all(ProjectileComponent.class, TransformComponent.class).get();

    /** Terminal HUD accents. */
    private static final Color COLOR_HUD_GOLD = new Color(0.92f, 0.82f, 0.52f, 1f);
    private static final Color COLOR_CARD_ACTIVE = new Color(1.00f, 0.86f, 0.42f, 1f);
    private static final Color COLOR_CARD_IDLE = new Color(0.42f, 0.66f, 0.46f, 1f);
    private static final Color COLOR_CROSSHAIR = new Color(0.62f, 0.92f, 0.68f, 0.85f);
    private static final Color COLOR_YOKAI = new Color(0.78f, 0.88f, 0.62f, 1f);
    private static final Color COLOR_YOKAI_HIT = new Color(1.00f, 0.38f, 0.30f, 1f);
    private static final Color COLOR_YOKAI_WOUNDED = new Color(0.95f, 0.55f, 0.34f, 1f);
    private static final Color COLOR_TAVERN = new Color(0.86f, 0.70f, 0.38f, 1f);
    private static final Color COLOR_SHOP = new Color(0.60f, 0.66f, 0.58f, 1f);
    private static final Color COLOR_SHRINE = new Color(0.86f, 0.52f, 0.52f, 1f);

    private final RunSession runSession;
    /** Borrowed, globally owned assets (owner: {@link GameAssets}). */
    private final GameAssets assets;

    /** Loaded ASCII map (pure CPU data). */
    private AsciiMap map;

    private PerspectiveCamera camera;
    private FirstPersonCameraController look;
    private Heightfield heightfield;
    private HeightmapTerrainRenderer terrainRenderer;
    private AsciiPostProcessor asciiPost;

    private Player player;
    private World world;

    private Stage uiStage;
    private Skin skin;
    private Label promptLabel;
    private Label hudLabel;
    private Label cardHudLabel;
    private Label cardHudState;
    private Label crosshairLabel;
    private InventoryOverlay inventoryOverlay;
    private TextureAtlas gameAtlas;

    /** Ashley engine facade (player, hostiles, systems). */
    private OverworldEcs ecs;
    /** Omamori-buffed combat values of the current loadout. */
    private CombatStats combatStats = CombatStats.BASE;

    private HankoEffect activeHanko = HankoEffect.WHITE_SEAL;
    private int selectedHankoIndex;
    private String omamoriSignature = "";

    /** Screen time used by the shader phases and the head bob. */
    private float overworldTime;

    /** Smoothed ground height under the camera; NaN until the first frame. */
    private float cameraGroundHeight = Float.NaN;
    /** Smoothed ground height per hostile billboard (organic hill following). */
    private final Map<Entity, Float> hostileGroundHeights = new HashMap<>();

    /** Wild Yokai roaming actors. */
    private final List<OverworldYokaiEntity> yokaiEntities = new ArrayList<>();
    /** Entity-to-actor bridge for the roaming wild Yokai. */
    private final Map<Entity, OverworldYokaiEntity> hostileActors = new HashMap<>();

    /** Cached BFS route of every chasing Yokai (reused, no per-frame allocation). */
    private final Map<OverworldYokaiEntity, ChasePath> chasePaths = new HashMap<>();

    /** Reusable chase route state of one Yokai. */
    private static final class ChasePath {
        List<AsciiMap.Tile> waypoints = List.of();
        int waypointIndex;
        float repathTimer;
        int playerCol = -1;
        int playerRow = -1;
    }

    /** Interaction tiles of the ASCII map (built once while loading). */
    private final List<AsciiMap.Tile> tavernTiles = new ArrayList<>();
    private final List<AsciiMap.Tile> shopTiles = new ArrayList<>();
    private final List<AsciiMap.Tile> shrineTiles = new ArrayList<>();
    private final Rectangle triggerBounds = new Rectangle();
    /** Reused interaction box (no allocation per frame). */
    private final Rectangle interactionBox = new Rectangle();

    /** Collision rectangles of the map (same source as the Box2D walls). */
    private final List<Rectangle> collisionRects = new ArrayList<>();
    /** One-time method reference: no allocation per frame. */
    private final OverworldYokaiEntity.CollisionQuery collisionQuery = this::isBlockedInWorld;

    /** Tile size of the loaded ASCII map (Yokai roaming extents). */
    private int tileWidth;
    private int tileHeight;
    private float mapPixelWidth;
    private float mapPixelHeight;

    /** Crosshair aim point in 2D world pixels (card throw target). */
    private final Vector2 aimWorld = new Vector2();
    /** Camera-relative WASD direction in 2D world space. */
    private final Vector2 moveDirection = new Vector2();
    /** Ground-plane view forward in 2D world space. */
    private final Vector2 forwardGround = new Vector2();
    /** Reused billboard/decal colour. */
    private final Color effectColor = new Color();
    /** Camera-right axis in world XZ, reused for multi-part billboard offsets. */
    private float billboardRightX = 1f;
    private float billboardRightZ = 0f;

    /** Pointer capture state of the mouse look. */
    private boolean mouseLookEnabled = true;
    /** true on the frame where a click only re-captured the pointer. */
    private boolean mouseRecaptured;
    /** Ignores the first mouse delta after capture (the cursor warp jump). */
    private boolean skipMouseLook = true;

    /** Prevents an automatic combat zone from retriggering right after battle. */
    private boolean automaticCombatLocked;

    /** Two-way fullscreen state so F11 works in both directions. */
    private boolean fullscreen = Gdx.graphics.isFullscreen();
    private float fullscreenCooldown;

    /** Guards against a double dispose by the screen manager. */
    private boolean disposed;

    private static final Family CARD_ENTITIES =
        Family.all(CardProjectileComponent.class, TransformComponent.class).get();
    private static final Family BURST_ENTITIES =
        Family.all(CardBurstComponent.class, TransformComponent.class).get();


    public OverworldScreen(RunSession runSession, GameAssets assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }
        this.runSession = runSession;
        this.assets = assets;

        // 1. Load the ASCII map: gameplay cells, spawns and interaction tiles.
        map = AsciiMapLoader.load(LOCATION_MAP_PATH);
        applyLocationMetadata();

        tileWidth = map.getTileSize();
        tileHeight = map.getTileSize();
        mapPixelWidth = map.getWorldWidth();
        mapPixelHeight = map.getWorldHeight();

        // 2. Box2D world without gravity: the second dimension is the ground plane.
        world = new World(new Vector2(0f, 0f), true);
        createCollisionBodies();
        collectInteractionTiles();

        // 3. Procedural 3D terrain and the first-person camera rig.
        heightfield = createHeightfield();
        camera = new PerspectiveCamera(FIELD_OF_VIEW, 1280f, 720f);
        camera.near = NEAR_PLANE;
        camera.far = FAR_PLANE;
        look = new FirstPersonCameraController();

        // 4. Player at the '@' cell (or at the stored overworld position).
        float startX = map.tileCenterX(map.getPlayerSpawn().col());
        float startY = map.tileCenterY(map.getPlayerSpawn().row());

        automaticCombatLocked = runSession.hasStoredPosition();
        if (runSession.hasStoredPosition()) {
            startX = runSession.getLastPlayerX();
            startY = runSession.getLastPlayerY();
        }
        player = new Player(world, startX, startY);

        // 5. Scene renderer (heightmap mesh + procedural Japanese village) and
        // the ASCII post pass.
        terrainRenderer = new HeightmapTerrainRenderer(heightfield);
        SceneryPlan sceneryPlan = SceneryPlan.generate(map, heightfield, WORLD_SCALE,
            terrainSeed(map) ^ 0x51E4_2A11_9C3DL);
        terrainRenderer.setScenery(new JapaneseScenery(sceneryPlan, heightfield));
        // Every visible torii pillar, lantern and temple wall becomes solid now
        // that the scenery anchors exist.
        registerStructureCollision(sceneryPlan);
        asciiPost = new AsciiPostProcessor();

        // 6. Scene2D HUD and inventory overlay.
        uiStage = new Stage(new FitViewport(1280f, 720f));
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        promptLabel = new Label("", skin);
        promptLabel.setFontScale(0.62f);
        promptLabel.setColor(COLOR_HUD_GOLD);

        Table promptTable = new Table();
        promptTable.setFillParent(true);
        promptTable.bottom().padBottom(122f);
        promptTable.add(promptLabel);
        uiStage.addActor(promptTable);
        promptLabel.setVisible(false);

        gameAtlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // 7. Ashley ECS combat layer: player, Hanko, Omamori passives.
        initCombatLayer();

        // 8. Wild Yokai as roaming actors AND hostile entities.
        createYokaiEntities();

        // 9. Yaku payloads earned in the card battle are applied now.
        applyPendingYakuEffects();

        // 10. Buttons, inventory overlay and the FPS HUD.
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.up = assets.newButtonDrawable();
        btnStyle.down = ((NinePatchDrawable) btnStyle.up).tint(Color.LIGHT_GRAY);
        btnStyle.font = FontManager.getFont();
        btnStyle.fontColor = FontManager.COLOR_TEXT_MAIN;

        NinePatchDrawable panelBg = assets.newPanelDrawable();
        inventoryOverlay = new InventoryOverlay(runSession, skin, gameAtlas, panelBg, btnStyle);
        uiStage.addActor(inventoryOverlay);

        createHud();
        updateCamera(0f);
        updateHud();

        Gdx.app.log("OverworldScreen", "First-person ASCII overworld ready: map '"
            + map.getLocationId() + "' (" + map.getWidth() + "x" + map.getHeight()
            + " tiles), terrain " + Math.round(heightfield.getSize()) + " units @ "
            + heightfield.getVerticesPerSide() + "x" + heightfield.getVerticesPerSide()
            + " vertices, " + yokaiEntities.size() + " Yokai spawn(s), FOV "
            + Math.round(FIELD_OF_VIEW) + " near " + NEAR_PLANE + " far " + FAR_PLANE);
    }

    // ------------------------------------------------------------------
    // HUD construction
    // ------------------------------------------------------------------

    /** Builds the top-left combat readout, the crosshair and the W-card HUD. */
    private void createHud() {
        hudLabel = new Label("", skin);
        hudLabel.setFontScale(0.62f);
        hudLabel.setColor(COLOR_HUD_GOLD);

        Table hudTable = new Table();
        hudTable.setFillParent(true);
        hudTable.top().left().pad(14f);
        hudTable.add(hudLabel);
        uiStage.addActor(hudTable);

        // Bottom-centre FPS W-card deck (Hanafuda names of the cycling slots).
        cardHudLabel = new Label("", skin);
        cardHudLabel.setFontScale(0.62f);
        cardHudLabel.setColor(COLOR_CARD_IDLE);

        cardHudState = new Label("", skin);
        cardHudState.setFontScale(0.50f);
        cardHudState.setColor(COLOR_CARD_IDLE);

        Table cardTable = new Table();
        cardTable.setFillParent(true);
        cardTable.bottom().padBottom(34f);
        cardTable.add(cardHudLabel).row();
        cardTable.add(cardHudState).padTop(2f);
        uiStage.addActor(cardTable);

        // Fixed crosshair: cards and Hanko casts resolve toward this line.
        crosshairLabel = new Label("+", skin);
        crosshairLabel.setFontScale(1.15f);
        crosshairLabel.setColor(COLOR_CROSSHAIR);

        Table crosshairTable = new Table();
        crosshairTable.setFillParent(true);
        crosshairTable.center();
        crosshairTable.add(crosshairLabel);
        uiStage.addActor(crosshairTable);
    }



    // ------------------------------------------------------------------
    // 3D terrain
    // ------------------------------------------------------------------

    /**
     * Generates the procedural heightmap patch around the ASCII map. The patch
     * is centered on the map and stretched beyond its borders so the horizon
     * stays covered, while remaining inside the 300 unit far plane.
     */
    private Heightfield createHeightfield() {
        float mapWidthUnits = mapPixelWidth * WORLD_SCALE;
        float mapHeightUnits = mapPixelHeight * WORLD_SCALE;
        float size = Math.min(FAR_PLANE * 0.8f,
            Math.max(mapWidthUnits, mapHeightUnits) * TERRAIN_SIZE_FACTOR + TERRAIN_MARGIN);

        float centerX = mapWidthUnits * 0.5f;
        // 2D north (+y) maps to 3D -z, so the map spans z in [-height, 0].
        float centerZ = -mapHeightUnits * 0.5f;

        Heightfield field = Heightfield.generate(terrainSeed(map),
            centerX - size * 0.5f, centerZ - size * 0.5f,
            size, TERRAIN_RESOLUTION, TERRAIN_AMPLITUDE);

        // Flatten the shrine/temple, tavern, shop and spawn clearings and smooth
        // the dirt paths before the scenery samples the terrain heights.
        SceneryPlan.levelClearings(field, map, WORLD_SCALE);
        // Raise the mountain rim so the outer world edge is a visible cliff.
        field.raiseRim(Heightfield.DEFAULT_RIM_WIDTH, Heightfield.DEFAULT_RIM_HEIGHT);
        return field;
    }

    /** Stable seed so a location always grows the same hills. */
    private static long terrainSeed(AsciiMap map) {
        return 0x5EED_1A5F_0BADF00DL ^ map.getLocationId().hashCode()
            ^ (map.getTileSize() * 0x9E3779B1L);
    }

    // ------------------------------------------------------------------
    // Ashley ECS setup
    // ------------------------------------------------------------------

    /** Creates the manual-combat ECS of the overworld. */
    private void initCombatLayer() {
        combatStats = computeCombatStats();

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
        TransformComponent playerTransform = ecs.getPlayerTransform();
        if (playerTransform == null) {
            return null;
        }
        TransformComponent nearest = playerTransform;
        float bestDistance = Float.MAX_VALUE;
        for (Entity hostileEntity : ecs.getHostiles()) {
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            TransformComponent transform = Mappers.TRANSFORM.get(hostileEntity);
            if (health == null || transform == null || health.isDead()) {
                continue;
            }
            float dx = transform.x - playerTransform.x;
            float dy = transform.y - playerTransform.y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = transform;
            }
        }
        return nearest;
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
     * loadout changed.
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
    // Frame input and Box2D/ECS bridging
    // ------------------------------------------------------------------

    /**
     * Keeps the pointer captured for mouse look. ESC releases it, a left click
     * captures it again, and an open inventory always releases it.
     */
    private void updateMouseCapture(boolean paused) {
        mouseRecaptured = false;

        if (!paused && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            mouseLookEnabled = !mouseLookEnabled;
        }
        if (!paused && !mouseLookEnabled
            && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            mouseLookEnabled = true;
            mouseRecaptured = true;
        }

        boolean wantCapture = mouseLookEnabled && !paused;
        if (Gdx.input.isCursorCatched() != wantCapture) {
            Gdx.input.setCursorCatched(wantCapture);
            skipMouseLook = true;
        }
    }

    /**
     * Fills the ECS input snapshot: mouse look, camera-relative WASD, the
     * crosshair aim, the directional Q dash and the card/Hanko keys.
     */
    private void updateCombatInput(float delta) {
        overworldTime += delta;

        PlayerInput input = ecs.getInput();
        boolean paused = inventoryOverlay.isOpen();

        // --- mouse look -----------------------------------------------------
        if (!paused && Gdx.input.isCursorCatched()) {
            if (skipMouseLook) {
                // The first delta after capturing the pointer is the warp jump.
                skipMouseLook = false;
            } else {
                look.applyLook(Gdx.input.getDeltaX(), Gdx.input.getDeltaY());
            }
        }

        // --- camera-relative WASD ground movement ---------------------------
        float axisX = paused ? 0f : axis(Input.Keys.D, Input.Keys.A);
        float axisY = paused ? 0f : axis(Input.Keys.W, Input.Keys.S);
        look.moveDirection(axisX, axisY, moveDirection);

        boolean hasMoveDirection = !paused && moveDirection.len2() > 0.0001f;
        input.hasMoveDirection = hasMoveDirection;
        if (hasMoveDirection) {
            input.moveDirection.set(moveDirection);
        } else {
            input.moveDirection.setZero();
        }

        // The legacy MOBA axis fields are no longer used for walking.
        input.moveX = 0f;
        input.moveY = 0f;

        // WASD always wins over a stale lock-on chase order.
        if (hasMoveDirection) {
            input.hasMoveTarget = false;
        }

        // --- crosshair aim --------------------------------------------------
        look.getGroundForward(forwardGround);

        // 3D crosshair ray: locked cards visualise along this camera line.
        input.hasAimRay = true;
        input.aimRayOrigin.set(camera.position);
        look.getForward(input.aimRayDirection);

        Vector2 playerPosition = player.body.getPosition();
        float aimDistance = AIM_DISTANCE_TILES * tileWidth;
        aimWorld.set(
            playerPosition.x + forwardGround.x * aimDistance,
            playerPosition.y + forwardGround.y * aimDistance);

        Entity crosshairTarget = crosshairHostile(forwardGround);
        if (crosshairTarget != null) {
            TransformComponent target = Mappers.TRANSFORM.get(crosshairTarget);
            if (target != null) {
                aimWorld.set(target.x, target.y);
            }
        }
        input.aimX = aimWorld.x;
        input.aimY = aimWorld.y;

        // --- card throw and lock-on ----------------------------------------
        boolean attackRequested = !paused && !mouseRecaptured
            && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        // Right mouse = lock-on: attack the crosshair target when it is in card
        // range, otherwise chase it (MOBA target movement stays available).
        if (paused) {
            input.hasMoveTarget = false;
        } else if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)
            && crosshairTarget != null) {
            TransformComponent target = Mappers.TRANSFORM.get(crosshairTarget);
            if (target != null) {
                aimWorld.set(target.x, target.y);
                input.aimX = target.x;
                input.aimY = target.y;
                input.hasMoveTarget = false;
                if (isWithinCardRange(target)) {
                    attackRequested = true;
                    Gdx.app.log("OverworldScreen", "Crosshair card attack at world="
                        + Math.round(target.x) + "," + Math.round(target.y));
                } else {
                    input.moveTarget.set(target.x, target.y);
                    input.hasMoveTarget = true;
                    Gdx.app.log("OverworldScreen", "Target out of range; chasing world="
                        + Math.round(target.x) + "," + Math.round(target.y));
                }
            }
        }

        // Q = dash in the held WASD direction; R = W-card shuffle/lock;
        // F = Hanko cast; E = interact. S is backpedal, not stop, in FPS mode.
        input.dashRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.Q);
        if (input.dashRequested) {
            String direction = hasMoveDirection
                ? Math.round(moveDirection.x) + "," + Math.round(moveDirection.y)
                : "crosshair";
            Gdx.app.log("OverworldScreen", "Q dash direction=" + direction);
        }
        input.stopRequested = false;
        input.basicAttackRequested = attackRequested;
        if (input.basicAttackRequested) {
            Gdx.app.log("OverworldScreen", "Card throw toward aim=("
                + Math.round(input.aimX) + "," + Math.round(input.aimY) + ")");
        }
        input.cardSelectRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.R);
        input.castRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.F);
        input.time = overworldTime;
    }

    /**
     * Living hostile whose direction is closest to the crosshair within the
     * card range and inside the narrow aiming cone, or {@code null}.
     */
    private Entity crosshairHostile(Vector2 forward) {
        if (ecs == null) {
            return null;
        }
        Vector2 origin = player.body.getPosition();
        float range = PlayerControlSystem.CARD_RANGE_TILES * Math.max(1, tileWidth);
        float range2 = range * range;
        float bestScore = Float.NEGATIVE_INFINITY;
        Entity best = null;

        for (Entity hostileEntity : ecs.getHostiles()) {
            TransformComponent transform = Mappers.TRANSFORM.get(hostileEntity);
            HostileComponent hostile = Mappers.HOSTILE.get(hostileEntity);
            HealthComponent health = Mappers.HEALTH.get(hostileEntity);
            if (transform == null || hostile == null || health == null || health.isDead()) {
                continue;
            }

            float dx = transform.x - origin.x;
            float dy = transform.y - origin.y;
            float distance2 = dx * dx + dy * dy;
            if (distance2 <= 0.0001f || distance2 > range2) {
                continue;
            }

            float distance = (float) Math.sqrt(distance2);
            float alignment = (dx * forward.x + dy * forward.y) / distance;
            if (alignment < CROSSHAIR_MIN_DOT) {
                continue;
            }

            // Prefer the most centered target, breaking ties by proximity.
            float score = alignment - distance / (range * 6f);
            if (score > bestScore) {
                bestScore = score;
                best = hostileEntity;
            }
        }
        return best;
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
        hostileGroundHeights.remove(entity);
        if (actor != null) {
            chasePaths.remove(actor);
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

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

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

        int aggro = 0;
        for (int i = 0; i < yokaiEntities.size(); i++) {
            if (yokaiEntities.get(i).isAggro()) {
                aggro++;
            }
        }
        if (crosshairLabel != null) {
            crosshairLabel.setColor(aggro > 0 ? COLOR_YOKAI_HIT : COLOR_CROSSHAIR);
        }

        Vector2 position = player.body.getPosition();
        hudLabel.setText("HP [" + healthBar + "] " + healthValue
            + "   LIFESTEAL " + lifesteal
            + "   MANA [" + manaBar + "] " + (int) playerComponent.mana + "/"
            + (int) playerComponent.maxMana
            + "   DASH [Q] " + dash
            + "   CARD [LMB] " + card
            + "   LOCK-ON [RMB]"
            + "\nHANKO " + config.name + " [F]"
            + "   OMAMORI " + runSession.getActiveOmamoris().size() + "/"
            + runSession.getMaxActiveOmamoris()
            + "   [1-9] hanko   [I] inventory"
            + "\nFP " + Gdx.graphics.getFramesPerSecond() + " fps"
            + "   POS " + Math.round(position.x) + "," + Math.round(position.y)
            + "   YAW " + Math.round(look.getYawDegrees()) + "deg"
            + "   TERRAIN " + Math.round(heightfield.getSize()) + "u"
            + "   AGGRO " + aggro);

        updateCardHud(deck, runSession.getPlayerDeck());
    }

    /**
     * Bottom-centre FPS deck: the cycling W-card slots with their actual
     * Hanafuda names. The highlighted slot uses {@code < >} brackets.
     */
    private void updateCardHud(CardDeckComponent deck, Deck playerDeck) {
        if (cardHudLabel == null) {
            return;
        }
        cardHudLabel.setText(CardDeckNames.hudLine(deck, playerDeck));
        boolean cycling = deck != null && deck.showsBadge();
        cardHudLabel.setColor(cycling ? COLOR_CARD_ACTIVE : COLOR_CARD_IDLE);

        if (cardHudState != null) {
            String state = deck == null ? "READY" : deck.stateLabel();
            cardHudState.setText("W-CARD [" + state + "]"
                + "   R: SHUFFLE / LOCK   |   LMB: THROW   |   Q: DASH");
            cardHudState.setColor(cycling ? COLOR_CARD_ACTIVE : COLOR_CARD_IDLE);
        }
    }

    private static String asciiBar(float ratio, int width) {
        int filled = Math.round(Math.max(0f, Math.min(1f, ratio)) * width);
        return "=".repeat(filled) + "-".repeat(Math.max(0, width - filled));
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
    // Building the world from the ASCII map
    // ------------------------------------------------------------------

    /**
     * Creates one static Box2D body per solid run of the ASCII map. The same
     * {@link WallLayout} drives the visible bamboo fences and mossy stone
     * outcrops generated by {@link SceneryPlan}, so collision and visuals
     * cannot drift apart - the invisible ghost walls are gone.
     */
    private void createCollisionBodies() {
        collisionRects.clear();

        for (WallLayout.Run run : WallLayout.scan(map)) {
            collisionRects.add(new Rectangle(run.x(), run.y(), run.width(), run.height()));
            createStaticBody(run.x(), run.y(), run.width(), run.height());
        }
    }

    /**
     * Registers a rotated static body for every physical scenery solid (torii
     * pillars, lanterns, temple hall/torches/offering box) and mirrors its
     * axis-aligned bounds into the Yokai collision list.
     */
    private void registerStructureCollision(SceneryPlan plan) {
        if (plan == null) {
            return;
        }
        for (SceneryPlan.Solid solid : plan.getSolids()) {
            float centerX = solid.x() / WORLD_SCALE;
            float centerY = -solid.z() / WORLD_SCALE;
            float halfWidth = solid.halfWidth() / WORLD_SCALE;
            float halfDepth = solid.halfDepth() / WORLD_SCALE;
            float angle = solid.yaw();

            createSolidBody(centerX, centerY, halfWidth, halfDepth, angle);

            // Rotated rectangle -> axis-aligned bounds for the Yokai collision.
            float cos = Math.abs(MathUtils.cos(angle));
            float sin = Math.abs(MathUtils.sin(angle));
            float halfX = halfWidth * cos + halfDepth * sin;
            float halfY = halfWidth * sin + halfDepth * cos;
            collisionRects.add(new Rectangle(
                centerX - halfX, centerY - halfY, halfX * 2f, halfY * 2f));
        }

        Gdx.app.log("OverworldScreen", "Structure collision synced: "
            + plan.getSolids().size() + " solids, "
            + plan.getFenceAnchors().size() + " bamboo fence cells, "
            + plan.getStoneWallAnchors().size() + " mossy stone wall cells, "
            + "Box2D bodies=" + world.getBodyCount() + ".");
    }

    /** Static rotated box body used by the procedural scenery solids. */
    private void createSolidBody(float centerX, float centerY,
                                 float halfWidth, float halfHeight, float angle) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(centerX, centerY);
        bodyDef.angle = angle;

        Body body = world.createBody(bodyDef);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(halfWidth, halfHeight);
        body.createFixture(shape, 0.0f);
        shape.dispose();
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
        hostileGroundHeights.clear();
        chasePaths.clear();
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

    /** Bewegt alle wilden Yokai; aktive Aggro-Yokai verfolgen den Spieler ueber die Map. */
    private void updateYokaiEntities(float delta) {
        Vector2 playerPosition = player.body.getPosition();
        float aggroRadius = HOSTILE_AGGRO_TILES * tileWidth;
        float leashRadius = HOSTILE_LEASH_TILES * tileWidth;
        float aggroRadius2 = aggroRadius * aggroRadius;
        float leashRadius2 = leashRadius * leashRadius;

        for (int i = 0; i < yokaiEntities.size(); i++) {
            OverworldYokaiEntity actor = yokaiEntities.get(i);
            float dx = playerPosition.x - actor.getWorldX();
            float dy = playerPosition.y - actor.getWorldY();
            float distance2 = dx * dx + dy * dy;

            if (!actor.isAggro() && distance2 <= aggroRadius2) {
                actor.setAggro(true);
            }

            if (actor.isAggro()) {
                if (distance2 > leashRadius2) {
                    actor.setAggro(false);
                    chasePaths.remove(actor);
                } else {
                    updateChaseWaypoint(actor, playerPosition, delta);
                }
            }

            actor.update(delta, collisionQuery);
        }
    }

    /**
     * Feeds the next BFS waypoint towards the player into a chasing Yokai. The
     * route is recomputed when the repath timer expires, the player changed
     * tiles or the route was fully consumed; walls are always respected.
     */
    private void updateChaseWaypoint(OverworldYokaiEntity actor, Vector2 playerPosition,
                                     float delta) {
        ChasePath state = chasePaths.get(actor);
        if (state == null) {
            state = new ChasePath();
            chasePaths.put(actor, state);
        }

        int actorCol = MathUtils.clamp(
            (int) (actor.getWorldX() / tileWidth), 0, map.getWidth() - 1);
        int actorRow = MathUtils.clamp(
            map.getHeight() - 1 - (int) (actor.getWorldY() / tileHeight),
            0, map.getHeight() - 1);
        int playerCol = MathUtils.clamp(
            (int) (playerPosition.x / tileWidth), 0, map.getWidth() - 1);
        int playerRow = MathUtils.clamp(
            map.getHeight() - 1 - (int) (playerPosition.y / tileHeight),
            0, map.getHeight() - 1);

        state.repathTimer -= delta;
        boolean playerMovedTiles = state.playerCol != playerCol || state.playerRow != playerRow;
        boolean routeFinished = state.waypoints.isEmpty()
            || state.waypointIndex >= state.waypoints.size();

        if (state.repathTimer <= 0f || playerMovedTiles || routeFinished) {
            state.waypoints = PathPlanner.findPath(map,
                new AsciiMap.Tile(actorCol, actorRow),
                new AsciiMap.Tile(playerCol, playerRow));
            state.waypointIndex = 0;
            state.playerCol = playerCol;
            state.playerRow = playerRow;
            state.repathTimer = HOSTILE_REPATH_SECONDS;
        }

        float reach = HOSTILE_WAYPOINT_TILES * tileWidth;
        while (state.waypointIndex < state.waypoints.size()) {
            AsciiMap.Tile waypoint = state.waypoints.get(state.waypointIndex);
            float wx = map.tileCenterX(waypoint.col()) - actor.getWorldX();
            float wy = map.tileCenterY(waypoint.row()) - actor.getWorldY();
            if (wx * wx + wy * wy > reach * reach) {
                break;
            }
            state.waypointIndex++;
        }

        if (state.waypoints.isEmpty() || state.waypointIndex >= state.waypoints.size()) {
            // No route (or already on the player's tile): steer straight at them.
            actor.setChaseTarget(playerPosition.x, playerPosition.y);
            return;
        }

        AsciiMap.Tile waypoint = state.waypoints.get(state.waypointIndex);
        actor.setChaseTarget(map.tileCenterX(waypoint.col()),
            map.tileCenterY(waypoint.row()));
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

    // ------------------------------------------------------------------
    // Screen lifecycle
    // ------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(uiStage);
        mouseLookEnabled = true;
        skipMouseLook = true;
        Gdx.input.setCursorCatched(true);
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);

        if (width <= 0 || height <= 0) {
            return;
        }
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        asciiPost.resize(width, height);
    }

    @Override
    public void render(float delta) {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        if (!asciiPost.isReady(screenWidth, screenHeight)) {
            resize(screenWidth, screenHeight);
        }

        // Inventory toggle and pointer capture (FPS mouse look).
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            inventoryOverlay.toggle();
        }
        boolean paused = inventoryOverlay.isOpen();
        updateMouseCapture(paused);

        // Input snapshot and ability/hanko state.
        updateCombatInput(delta);
        updateHankoSelection();
        refreshOmamoriLoadout(false);

        // Box2D -> ECS -> systems -> Box2D.
        syncPlayerIntoEcs();
        syncHostilesIntoEcs();
        ecs.update(delta);
        applyPlayerMotionToBody();

        world.step(delta, 6, 2);

        player.update(delta, player.body.getLinearVelocity());
        updateYokaiEntities(delta);
        syncPlayerFromBody();
        syncHostilesIntoEcs();

        handleInteractions();
        updateHud();
        updateCamera(delta);

        // 3D heightmap scene -> framebuffer -> ASCII glyph matrix -> screen.
        asciiPost.setTime(overworldTime);
        asciiPost.begin();
        renderWorld();
        asciiPost.end();

        // Scene2D HUD sits on top of the ASCII matrix.
        uiStage.act(Math.min(delta, 0.1f));
        uiStage.draw();

        fullscreenCooldown = Math.max(0f, fullscreenCooldown - delta);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11) && fullscreenCooldown <= 0f) {
            toggleFullscreen();
            fullscreenCooldown = 0.35f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            saveDebugScreenshot();
        }
    }

    /**
     * Places the first-person camera on the heightmap above the player's feet.
     * Box2D X/Y becomes 3D X/-Z, so north in the ASCII map stays north in 3D.
     * The ground height is smoothed so walking up a hill feels organic instead
     * of stair-stepped, and clamped above the surface on steep slopes.
     */
    private void updateCamera(float delta) {
        camera.viewportWidth = Math.max(1, Gdx.graphics.getWidth());
        camera.viewportHeight = Math.max(1, Gdx.graphics.getHeight());

        Vector2 position = player.body.getPosition();
        float unitX = position.x * WORLD_SCALE;
        float unitZ = -position.y * WORLD_SCALE;
        float ground = heightfield.getHeight(unitX, unitZ);

        cameraGroundHeight = TerrainFollow.smooth(
            cameraGroundHeight, ground, delta, TERRAIN_FOLLOW_RATE);
        float bob = player.isMoving()
            ? MathUtils.sin(player.getWalkPhase() * 9f) * HEAD_BOB
            : 0f;
        float eyeY = TerrainFollow.clampAboveGround(
            cameraGroundHeight + EYE_HEIGHT + bob, ground, EYE_HEIGHT * 0.6f);

        look.applyTo(camera, unitX, eyeY, unitZ);
        asciiPost.setCamera(camera);
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
                + ", camera " + Math.round(camera.position.x) + ","
                + Math.round(camera.position.y) + "," + Math.round(camera.position.z) + ")");
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

    // ------------------------------------------------------------------
    // 3D scene contents
    // ------------------------------------------------------------------

    /** Draws the heightmap terrain, world markers, Yokai and combat effects. */
    private void renderWorld() {
        terrainRenderer.begin(camera, overworldTime);
        renderInteractionMarkers();
        renderHostiles();
        renderCombatEffects();
        terrainRenderer.end();
    }

    /** Glowing ground pads and pillars for the T/S/H interaction cells. */
    private void renderInteractionMarkers() {
        for (AsciiMap.Tile tile : tavernTiles) {
            drawMarker(tile, COLOR_TAVERN);
        }
        for (AsciiMap.Tile tile : shopTiles) {
            drawMarker(tile, COLOR_SHOP);
        }
        for (AsciiMap.Tile tile : shrineTiles) {
            drawMarker(tile, COLOR_SHRINE);
        }
    }

    private void drawMarker(AsciiMap.Tile tile, Color color) {
        float worldX = map.tileCenterX(tile.col()) * WORLD_SCALE;
        float worldZ = -map.tileCenterY(tile.row()) * WORLD_SCALE;
        float ground = heightfield.getHeight(worldX, worldZ);
        terrainRenderer.drawGroundQuad(worldX, worldZ, 0.55f, color);
        terrainRenderer.drawBillboard(worldX, ground, worldZ, 0.30f, 1.35f, color);
    }

    /** Camera-right axis used to offset the multi-part ASCII billboards. */
    private void updateBillboardAxes() {
        float dx = camera.direction.x;
        float dz = camera.direction.z;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length > 0.0001f) {
            billboardRightX = -dz / length;
            billboardRightZ = dx / length;
        } else {
            billboardRightX = 1f;
            billboardRightZ = 0f;
        }
    }

    /** Wild Yokai as high-contrast ASCII billboard assemblies on the hills. */
    private void renderHostiles() {
        float delta = Math.min(TerrainFollow.MAX_STEP_SECONDS, Gdx.graphics.getDeltaTime());
        updateBillboardAxes();

        for (Map.Entry<Entity, OverworldYokaiEntity> entry : hostileActors.entrySet()) {
            OverworldYokaiEntity actor = entry.getValue();
            HealthComponent health = Mappers.HEALTH.get(entry.getKey());

            float worldX = actor.getWorldX() * WORLD_SCALE;
            float worldZ = -actor.getWorldY() * WORLD_SCALE;
            float sampled = heightfield.getHeight(worldX, worldZ);

            Float previous = hostileGroundHeights.get(entry.getKey());
            float ground = TerrainFollow.smooth(previous == null ? sampled : previous,
                sampled, delta, TERRAIN_FOLLOW_RATE);
            hostileGroundHeights.put(entry.getKey(), ground);

            float flash = actor.isHitFlashing()
                ? MathUtils.clamp(actor.getHitFlashRatio(), 0f, 1f) : 0f;
            renderYokaiAssembly(worldX, worldZ, ground, actor, health, flash);
        }
    }

    /**
     * Draws one Yokai as a stack of camera-facing ink islands: shadow, body,
     * head, two white eyes, horns and a health band. The assembly reads as a
     * creature in the terminal matrix instead of a solid block.
     */
    private void renderYokaiAssembly(float worldX, float worldZ, float ground,
                                     OverworldYokaiEntity actor, HealthComponent health,
                                     float flash) {
        // Ground shadow keeps the silhouette anchored on the terrain.
        effectColor.set(0.035f, 0.055f, 0.040f, 1f);
        terrainRenderer.drawGroundQuad(worldX, worldZ, 0.46f, effectColor);

        // Red aggro ring while the Yokai is actively chasing the player.
        if (actor.isAggro()) {
            float pulse = 0.55f + 0.45f * MathUtils.sin(overworldTime * 6f);
            effectColor.set(0.55f * pulse, 0.12f * pulse, 0.12f * pulse, 1f);
            terrainRenderer.drawGroundQuad(worldX, worldZ, 0.68f, effectColor);
        }

        // Body: pale spirit ink, white-hot on a fresh hit.
        effectColor.set(0.78f, 0.88f, 0.62f, 1f);
        if (flash > 0f) {
            effectColor.lerp(Color.WHITE, flash);
        } else if (health != null && health.ratio() < 1f) {
            effectColor.lerp(COLOR_YOKAI_WOUNDED, (1f - health.ratio()) * 0.6f);
        }
        terrainRenderer.drawBillboard(worldX, ground + 0.12f, worldZ,
            0.92f, 0.92f, effectColor);

        // Head.
        effectColor.set(0.95f, 0.99f, 0.82f, 1f);
        if (flash > 0f) {
            effectColor.lerp(Color.WHITE, flash);
        }
        terrainRenderer.drawBillboard(worldX, ground + 1.02f, worldZ,
            0.64f, 0.50f, effectColor);

        // Eyes: two high-contrast dots offset along the camera-right axis.
        effectColor.set(1f, 1f, 1f, 1f);
        float eyeOffset = 0.15f;
        float rightX = billboardRightX;
        float rightZ = billboardRightZ;
        terrainRenderer.drawBillboard(worldX + rightX * eyeOffset,
            ground + 1.22f, worldZ + rightZ * eyeOffset, 0.11f, 0.11f, effectColor);
        terrainRenderer.drawBillboard(worldX - rightX * eyeOffset,
            ground + 1.22f, worldZ - rightZ * eyeOffset, 0.11f, 0.11f, effectColor);

        // Horns: two small tilted strokes above the head.
        effectColor.set(0.88f, 0.94f, 0.72f, 1f);
        if (flash > 0f) {
            effectColor.lerp(Color.WHITE, flash);
        }
        terrainRenderer.drawBillboard(worldX + rightX * 0.22f,
            ground + 1.48f, worldZ + rightZ * 0.22f, 0.10f, 0.30f, effectColor, 0.42f);
        terrainRenderer.drawBillboard(worldX - rightX * 0.22f,
            ground + 1.48f, worldZ - rightZ * 0.22f, 0.10f, 0.30f, effectColor, -0.42f);

        // Health band above the Yokai once it took damage.
        if (health != null && health.ratio() < 1f) {
            float ratio = MathUtils.clamp(health.ratio(), 0f, 1f);
            effectColor.set(0.95f, 0.30f, 0.28f, 1f);
            terrainRenderer.drawBillboard(worldX, ground + 1.78f, worldZ,
                0.80f * ratio + 0.06f, 0.06f, effectColor);
        }
    }

    /** Seals, cards, bursts, auras, sparks and particles in 3D. */
    private void renderCombatEffects() {
        renderSealDecals();
        renderFlyingCards();
        renderCardBursts();
        renderLifestealAura();
        renderProjectilesAndParticles();
    }

    // ------------------------------------------------------------------
    // Seals: Hanko ground decals + spinning Karma rune sigils
    // ------------------------------------------------------------------

    private void renderSealDecals() {
        for (Entity entity : ecs.getEngine().getEntitiesFor(SEAL_ENTITIES)) {
            SealComponent seal = Mappers.SEAL.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (seal == null || transform == null) {
                continue;
            }

            float fade = 1f - MathUtils.clamp((seal.progress() - 0.7f) / 0.3f, 0f, 1f);
            float pulse = 0.72f + 0.28f * MathUtils.sin(overworldTime * 10f - seal.age * 7f);
            float radiusUnits = seal.currentRadius() * WORLD_SCALE;
            float worldX = transform.x * WORLD_SCALE;
            float worldZ = -transform.y * WORLD_SCALE;
            float ground = heightfield.getHeight(worldX, worldZ);

            effectColor.set(seal.red * fade * pulse,
                seal.green * fade * pulse, seal.blue * fade * pulse, 1f);
            terrainRenderer.drawGroundQuad(worldX, worldZ,
                Math.max(0.12f, radiusUnits * 0.9f), effectColor);

            // Karma rune fields carry glyphs: float a spinning sigil billboard
            // over the ground decal so the seal reads in first person.
            if (fade > 0.05f && isRuneSeal(seal.label)) {
                float size = MathUtils.clamp(radiusUnits * 0.75f, 0.45f, 2.8f);
                terrainRenderer.drawBillboard(worldX, ground + size * 0.35f, worldZ,
                    size, size, effectColor, seal.age * 2.4f);
            }
        }
    }

    /** Karma fields use rotated rune ink; Hanko seals use plain names. */
    private static boolean isRuneSeal(String label) {
        if (label == null) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char glyph = label.charAt(i);
            if (glyph == '*' || glyph == '+' || glyph == 'x' || glyph == '/'
                || glyph == '\\' || glyph == '|') {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Flying cards: billboards travelling along the crosshair ray
    // ------------------------------------------------------------------

    private void renderFlyingCards() {
        for (Entity entity : ecs.getEngine().getEntitiesFor(CARD_ENTITIES)) {
            CardProjectileComponent card = Mappers.CARD_PROJECTILE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (card == null || transform == null) {
                continue;
            }

            float worldX = transform.x * WORLD_SCALE;
            float worldZ = -transform.y * WORLD_SCALE;
            float ground = heightfield.getHeight(worldX, worldZ);

            // Locked cards launched by R + LMB follow the camera ray; the
            // height comes from the ray pitch and is clamped above the hills.
            float height = card.hasWorldRay
                ? card.worldHeight(WORLD_SCALE)
                : ground + 0.9f;
            height = TerrainFollow.clampAboveGround(height, ground, 0.22f);

            float roll = overworldTime * 4.2f + card.travelled * 0.015f;
            effectColor.set(card.red, card.green, card.blue, 1f);
            terrainRenderer.drawBillboard(worldX, height - 0.36f, worldZ,
                0.58f, 0.72f, effectColor, roll);

            // Soft ground glow anchors the card on the hillside.
            effectColor.mul(0.42f, 0.42f, 0.42f, 1f);
            terrainRenderer.drawGroundQuad(worldX, worldZ, 0.24f, effectColor);
        }
    }

    // ------------------------------------------------------------------
    // Card impact bursts: petal rings, ribbon waves, beast strokes
    // ------------------------------------------------------------------

    private void renderCardBursts() {
        for (Entity entity : ecs.getEngine().getEntitiesFor(BURST_ENTITIES)) {
            CardBurstComponent burst = Mappers.CARD_BURST.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (burst == null || transform == null) {
                continue;
            }
            float alpha = burst.alpha();
            if (alpha <= 0.01f) {
                continue;
            }

            float progress = burst.progress();
            float radius = (burst.startRadius
                + (burst.endRadius - burst.startRadius) * progress) * WORLD_SCALE;
            float worldX = transform.x * WORLD_SCALE;
            float worldZ = -transform.y * WORLD_SCALE;
            float ground = heightfield.getHeight(worldX, worldZ);
            float glow = 0.35f + 0.65f * alpha;
            effectColor.set(burst.red * glow, burst.green * glow, burst.blue * glow, 1f);

            switch (burst.style) {
                case PETAL -> renderPetalBurst(burst, worldX, worldZ, radius);
                case RIBBON -> renderRibbonBurst(burst, worldX, worldZ, radius);
                case BEAST -> renderBeastBurst(burst, worldX, worldZ, radius);
                case FLASH -> renderFlashBurst(burst, worldX, worldZ, ground, radius);
                case RUNE -> renderRuneBurst(burst, worldX, worldZ, radius);
                default -> {
                    terrainRenderer.drawBillboard(worldX, ground + 0.35f, worldZ,
                        0.5f, 0.5f, effectColor, burst.spin);
                    terrainRenderer.drawGroundQuad(worldX, worldZ, radius, effectColor);
                }
            }
        }
    }

    /** Warm petal/Hikari burst: blossoms expanding in a ring around the hit. */
    private void renderPetalBurst(CardBurstComponent burst, float worldX, float worldZ,
                                  float radius) {
        int petals = 8;
        for (int i = 0; i < petals; i++) {
            double angle = burst.spin + i * (Math.PI * 2.0 / petals);
            float px = worldX + (float) Math.cos(angle) * radius;
            float pz = worldZ + (float) Math.sin(angle) * radius;
            float py = heightfield.getHeight(px, pz) + 0.18f;
            float size = 0.14f + 0.28f * burst.alpha();
            terrainRenderer.drawBillboard(px, py, pz, size, size,
                effectColor, (float) angle);
        }
        terrainRenderer.drawGroundQuad(worldX, worldZ, radius * 0.85f, effectColor);
    }

    /** Cool Ribbon burst: a horizontal wave of wide ink strokes. */
    private void renderRibbonBurst(CardBurstComponent burst, float worldX, float worldZ,
                                   float radius) {
        for (int i = -2; i <= 2; i++) {
            float px = worldX + i * radius * 0.35f;
            float py = heightfield.getHeight(px, worldZ) + 0.55f + Math.abs(i) * 0.06f;
            terrainRenderer.drawBillboard(px, py, worldZ, 0.55f, 0.20f,
                effectColor, burst.spin + i * 0.35f);
        }
        terrainRenderer.drawGroundQuad(worldX, worldZ, Math.max(0.2f, radius), effectColor);
    }

    /** Heavy Beast burst: three tall, tumbling horn/beast strokes. */
    private void renderBeastBurst(CardBurstComponent burst, float worldX, float worldZ,
                                  float radius) {
        for (int i = 0; i < 3; i++) {
            double angle = burst.spin + i * 2.0944;
            float px = worldX + (float) Math.cos(angle) * radius * 0.45f;
            float pz = worldZ + (float) Math.sin(angle) * radius * 0.45f;
            float py = heightfield.getHeight(px, pz) + 0.35f;
            terrainRenderer.drawBillboard(px, py, pz, 0.34f, 0.85f,
                effectColor, (float) angle + 1.25f);
        }
        terrainRenderer.drawGroundQuad(worldX, worldZ, Math.max(0.2f, radius), effectColor);
    }

    /** White-ink card hit flash: a hot cross of billboards at the enemy. */
    private void renderFlashBurst(CardBurstComponent burst, float worldX, float worldZ,
                                  float ground, float radius) {
        float size = 0.35f + radius * 0.45f;
        effectColor.set(1f, 1f, 1f, 1f);
        terrainRenderer.drawBillboard(worldX, ground + 0.35f, worldZ, size, size,
            effectColor, burst.spin);
        terrainRenderer.drawBillboard(worldX, ground + 0.35f, worldZ,
            size * 0.30f, size * 1.35f, effectColor, 0f);
        terrainRenderer.drawBillboard(worldX, ground + 0.35f, worldZ,
            size * 1.35f, size * 0.30f, effectColor, 0f);
        effectColor.mul(0.45f, 0.45f, 0.45f, 1f);
        terrainRenderer.drawGroundQuad(worldX, worldZ, radius * 0.7f + 0.12f, effectColor);
    }

    /** Karma rune ring: rotating green ink glyphs stamped on the ground. */
    private void renderRuneBurst(CardBurstComponent burst, float worldX, float worldZ,
                                 float radius) {
        float glyphSize = 0.14f + 0.18f * burst.alpha();
        int runes = 10;
        for (int i = 0; i < runes; i++) {
            double angle = burst.spin + i * (Math.PI * 2.0 / runes);
            float px = worldX + (float) Math.cos(angle) * radius;
            float pz = worldZ + (float) Math.sin(angle) * radius;
            float py = heightfield.getHeight(px, pz) + 0.06f;
            terrainRenderer.drawBillboard(px, py, pz, glyphSize, glyphSize,
                effectColor, (float) angle);
        }
        float ground = heightfield.getHeight(worldX, worldZ);
        float seal = Math.max(0.30f, radius * 0.5f);
        terrainRenderer.drawBillboard(worldX, ground + 0.10f, worldZ, seal, seal,
            effectColor, burst.spin * 1.6f);
        terrainRenderer.drawGroundQuad(worldX, worldZ, radius * 0.9f, effectColor);
    }

    // ------------------------------------------------------------------
    // Beast-Yaku lifesteal aura
    // ------------------------------------------------------------------

    /** Pulsing red aura drawn around the player while lifesteal is active. */
    private void renderLifestealAura() {
        PlayerComponent playerComponent = ecs.getPlayer();
        if (playerComponent == null || playerComponent.lifestealRemaining <= 0f) {
            return;
        }
        Vector2 position = player.body.getPosition();
        float worldX = position.x * WORLD_SCALE;
        float worldZ = -position.y * WORLD_SCALE;
        float ground = heightfield.getHeight(worldX, worldZ);

        float pulse = 0.55f + 0.45f * MathUtils.sin(overworldTime * 8f);
        float strength = MathUtils.clamp(
            playerComponent.lifestealPercent * 2.5f, 0.4f, 1f) * pulse;
        effectColor.set(0.92f * strength, 0.26f * strength, 0.30f * strength, 1f);

        terrainRenderer.drawGroundQuad(worldX, worldZ, 0.85f + pulse * 0.2f, effectColor);
        float auraHeight = ground + 0.35f + pulse * 0.55f;
        for (int i = 0; i < 4; i++) {
            double angle = overworldTime * 1.6 + i * (Math.PI * 0.5);
            float offsetX = (float) Math.cos(angle) * 0.7f;
            float offsetZ = (float) Math.sin(angle) * 0.7f;
            terrainRenderer.drawBillboard(worldX + offsetX, auraHeight, worldZ + offsetZ,
                0.26f, 0.26f, effectColor, (float) angle);
        }
    }

    // ------------------------------------------------------------------
    // Companion projectiles and particles
    // ------------------------------------------------------------------

    private void renderProjectilesAndParticles() {
        for (Entity entity : ecs.getEngine().getEntitiesFor(PROJECTILE_ENTITIES)) {
            ProjectileComponent projectile = Mappers.PROJECTILE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (projectile == null || transform == null) {
                continue;
            }
            effectColor.set(projectile.red, projectile.green, projectile.blue, 1f);
            drawEffectBillboard(transform, 0.85f, 0.28f, effectColor);
        }

        for (Entity entity : ecs.getEngine().getEntitiesFor(PARTICLE_ENTITIES)) {
            ParticleComponent particle = Mappers.PARTICLE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (particle == null || transform == null) {
                continue;
            }
            float alpha = particle.alpha();
            if (alpha <= 0f) {
                continue;
            }
            float glow = 0.25f + 0.75f * alpha;
            effectColor.set(particle.red * glow, particle.green * glow,
                particle.blue * glow, 1f);
            float size = Math.max(0.08f, 0.18f * particle.scale);

            // Ribbon refund ink is drawn as a wide, flat wave.
            boolean ribbon = particle.glyph != null && particle.glyph.indexOf('~') >= 0;
            float worldX = transform.x * WORLD_SCALE;
            float worldZ = -transform.y * WORLD_SCALE;
            float ground = heightfield.getHeight(worldX, worldZ) + 0.55f;
            if (ribbon) {
                terrainRenderer.drawBillboard(worldX, ground, worldZ,
                    size * 2.6f, size * 0.8f, effectColor, overworldTime * 2.1f);
            } else {
                terrainRenderer.drawBillboard(worldX, ground, worldZ,
                    size, size, effectColor);
            }
        }
    }

    private void drawEffectBillboard(TransformComponent transform, float heightAboveGround,
                                     float size, Color color) {
        float worldX = transform.x * WORLD_SCALE;
        float worldZ = -transform.y * WORLD_SCALE;
        float ground = heightfield.getHeight(worldX, worldZ) + heightAboveGround;
        terrainRenderer.drawBillboard(worldX, ground, worldZ, size, size, color);
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    /**
     * Loest Interaktionen ueber die Zellen der ASCII-Map aus: Yokai-Kontakt,
     * Tavernentuer ({@code T}), Shop ({@code S}) und Schrein ({@code H}).
     */
    private void handleInteractions() {
        if (inventoryOverlay.isOpen()) {
            promptLabel.setVisible(false);
            crosshairLabel.setVisible(false);
            return;
        }
        crosshairLabel.setVisible(true);

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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        // Release the pointer: a disposed screen must never trap the cursor.
        Gdx.input.setCursorCatched(false);

        // 1. Screen-local GPU resources (ASCII pass + 3D meshes/shader).
        if (asciiPost != null) {
            asciiPost.dispose();
            asciiPost = null;
        }
        if (terrainRenderer != null) {
            terrainRenderer.dispose();
            terrainRenderer = null;
        }
        heightfield = null;
        camera = null;
        look = null;

        // 2. Physics, ECS and Scene2D stage.
        if (world != null) {
            // Bodies are no longer used after this point.
            world.dispose();
            world = null;
        }
        if (ecs != null) {
            ecs.clear();
            ecs = null;
        }
        if (uiStage != null) {
            uiStage.dispose();
            uiStage = null;
        }

        // 3. Pure data of this screen setup.
        hostileActors.clear();
        chasePaths.clear();
        hostileGroundHeights.clear();
        yokaiEntities.clear();
        collisionRects.clear();
        tavernTiles.clear();
        shopTiles.clear();
        shrineTiles.clear();
        hudLabel = null;
        cardHudLabel = null;
        cardHudState = null;
        crosshairLabel = null;
        promptLabel = null;

        // Skin, atlas, panel/button textures are borrowed (owner: GameAssets)
        // and must never be disposed here.
    }
}


