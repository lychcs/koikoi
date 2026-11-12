package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.combat.CardDeckNames;
import com.lychcs.koikoi.combat.CardSelectBinding;
import com.lychcs.koikoi.combat.CombatStats;
import com.lychcs.koikoi.combat.HankoCastConfig;
import com.lychcs.koikoi.combat.OmamoriPassives;
import com.lychcs.koikoi.combat.OverworldEcs;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.VelocitySmoothing;
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
import com.lychcs.koikoi.render.Heightfield;
import com.lychcs.koikoi.render.HeightmapTerrainRenderer;
import com.lychcs.koikoi.render.JapaneseScenery;
import com.lychcs.koikoi.render.NatureModelRenderer;
import com.lychcs.koikoi.render.PathPlanner;
import com.lychcs.koikoi.render.PlayerAvatarRenderer;
import com.lychcs.koikoi.render.SceneryPlan;
import com.lychcs.koikoi.render.TerrainFollow;
import com.lychcs.koikoi.render.ThirdPersonCameraController;
import com.lychcs.koikoi.render.VillageNpcRenderer;
import com.lychcs.koikoi.render.WorldSeasonMap;
import com.lychcs.koikoi.render.YokaiRigRenderer;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.YakuEffectKind;
import com.lychcs.koikoi.run.YakuEffects;
import com.lychcs.koikoi.run.YokaiEncounter;
import com.lychcs.koikoi.run.YokaiEncounterCatalog;
import com.lychcs.koikoi.scoring.YakuType;
import com.lychcs.koikoi.world.AsciiMap;
import com.lychcs.koikoi.world.PhysicsSteps;
import com.lychcs.koikoi.world.WallLayout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Third-person 3D overworld of the ASCII action engine.
 *
 * <p>The ASCII map still defines the gameplay world: walls ({@code #}) become
 * Box2D collision bodies, {@code @} is the player spawn, {@code Y} spawns wild
 * Yokai and {@code T}/{@code S}/{@code H} are the tavern, shop and shrine
 * interactions. The combat layer (Ashley ECS, Yaku payloads, Hanko and Omamori
 * passives) is unchanged.</p>
 *
 * <p>The overworld itself is the generated {@link WorldSeasonMap} hub world:
 * a large square map whose neutral Sanctuary Village crossroads is surrounded
 * by four cardinal seasonal realms (East = Haru, South = Natsu, West = Aki,
 * North = Fuyu). Monumental border torii gates and a mountain ring separate
 * the realms; a top compass banner announces the realm and a smooth shader
 * blend tints ground, foliage and fog across the transition zone. Terrain,
 * scenery, nature batches and static Box2D bodies are culled in 32-tile
 * spatial chunks so the large map stays fast.</p>
 *
 * <p>Rendering is fully 3D: a {@link PerspectiveCamera} (70 degree FOV, near
 * 0.1, far 300) orbits an over-the-shoulder rig around a visible ink-samurai
 * avatar on a procedurally generated simplex-noise heightmap. The forward
 * {@code ascii_world.frag} pass renders clean cel-shaded surfaces, normals and
 * warm lantern light into an off-screen framebuffer; {@link AsciiPostProcessor}
 * then turns that scene into a dense font-atlas terminal image with nine-tap
 * cell averaging, a temporal accumulation buffer, cel outlines and soft bloom.
 * Mouse look + WASD move the player relative to
 * the view, {@code Q} performs a Vayne-style tumble in the held WASD direction,
 * and thrown cards leave the shoulder toward the exact world point under the
 * centre crosshair. The cycling 3P card deck is drawn as a framed bottom-centre
 * HUD and mirrored by a coloured sigil above the avatar's head.</p>
 *
 * <p>Controls: mouse = look, WASD = camera-relative walk, Q = tumble/dash in the
 * WASD direction (crosshair direction when no key is held), LMB = throw card,
 * RMB = lock-on/chase the hostile under the crosshair (or shuffle/lock the deck
 * when no hostile is targeted), E/R = shuffle/lock the 3P deck, F = Hanko cast,
 * E = interact (tavern/shop/shrine), I = inventory, ESC = release the pointer,
 * F11 = fullscreen, F12 = debug screenshot.</p>
 */
public class OverworldScreen extends ScreenAdapter {

    /** Side length of the generated four-season overworld in tiles. */
    private static final int WORLD_TILES = WorldSeasonMap.DEFAULT_WORLD_TILES;

    /** libGDX units per ASCII pixel: one 64 px tile becomes one world unit. */
    public static final float WORLD_SCALE = 1f / 64f;

    /** Vertical field of view of the third-person camera. */
    public static final float FIELD_OF_VIEW = 70f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 300f;

    /** Movement weight: exponential acceleration and deceleration rates (1/s). */
    private static final float MOVE_ACCEL_RATE = 10f;
    private static final float MOVE_DECEL_RATE = 14f;

    /** Thin 3D Hanafuda projectile dimensions in world units. */
    private static final float CARD_WIDTH = 0.24f;
    private static final float CARD_LENGTH = 0.36f;
    /** Rapid tumble around the card's flight axis, radians per world pixel. */
    private static final float CARD_SPIN_PER_PIXEL = 0.032f;
    /** Coloured trail samples, in world pixels behind the card. */
    private static final float CARD_TRAIL_SPACING = 16f;
    private static final int CARD_TRAIL_STEPS = 3;

    /** Kamon archetype inks: vermillion sakura, azure ribbon, gold beast. */
    private static final Color KAMON_VERMILLION = new Color(0.95f, 0.30f, 0.22f, 1f);
    private static final Color KAMON_AZURE = new Color(0.34f, 0.68f, 0.96f, 1f);
    private static final Color KAMON_GOLD = new Color(0.95f, 0.72f, 0.26f, 1f);
    /** Subtle head bob amplitude while walking, in world units. */
    private static final float HEAD_BOB = 0.035f;

    /** Heightmap patch: map size multiplier plus a fixed horizon margin. */
    private static final float TERRAIN_SIZE_FACTOR = 1.6f;
    private static final float TERRAIN_MARGIN = 52f;
    private static final int TERRAIN_RESOLUTION = 150;
    /** Peak hill height in world units (1 unit = one 64 px tile). */
    private static final float TERRAIN_AMPLITUDE = 3.2f;

    /** Furthest distance at which the crosshair resolves a 3D world point. */
    private static final float CROSSHAIR_RAY_DISTANCE = 40f;
    /** Height of the Yokai billboard centre used for crosshair picking. */
    private static final float HOSTILE_CROSSHAIR_HEIGHT = 1.0f;
    /** Radius around the camera ray that still counts as a crosshair hit. */
    private static final float HOSTILE_CROSSHAIR_RADIUS = 0.85f;
    /** Top of the boundary bamboo fences above the ground (camera blocker). */
    private static final float FENCE_CAMERA_HEIGHT = 1.30f;
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

    /** Seconds the region banner stays bright after crossing a border torii. */
    private static final float SEASON_BANNER_SECONDS = 4.5f;

    /** Terminal HUD accents. */
    private static final Color COLOR_HUD_GOLD = new Color(0.92f, 0.82f, 0.52f, 1f);
    private static final Color COLOR_HUD_TEXT = new Color(0.88f, 0.90f, 0.84f, 1f);
    private static final Color COLOR_HUD_MUTED = new Color(0.60f, 0.68f, 0.60f, 1f);
    private static final Color COLOR_HUD_BACKING = new Color(0.020f, 0.028f, 0.050f, 0.82f);
    private static final Color COLOR_HUD_EDGE = new Color(0.34f, 0.46f, 0.48f, 0.92f);
    private static final Color COLOR_BAR_HEALTH = new Color(0.84f, 0.32f, 0.30f, 1f);
    private static final Color COLOR_BAR_MANA = new Color(0.36f, 0.62f, 0.88f, 1f);
    private static final Color COLOR_BAR_DASH = new Color(0.88f, 0.74f, 0.40f, 1f);
    private static final Color COLOR_CARD_ACTIVE = new Color(1.00f, 0.86f, 0.42f, 1f);
    private static final Color COLOR_CARD_IDLE = new Color(0.62f, 0.76f, 0.62f, 1f);
    private static final Color COLOR_CROSSHAIR = new Color(0.62f, 0.92f, 0.68f, 0.85f);
    private static final Color COLOR_YOKAI = new Color(0.78f, 0.88f, 0.62f, 1f);
    private static final Color COLOR_YOKAI_HIT = new Color(1.00f, 0.38f, 0.30f, 1f);
    private static final Color COLOR_TAVERN = new Color(0.86f, 0.70f, 0.38f, 1f);
    private static final Color COLOR_SHOP = new Color(0.60f, 0.66f, 0.58f, 1f);
    private static final Color COLOR_SHRINE = new Color(0.86f, 0.52f, 0.52f, 1f);

    private final RunSession runSession;
    /** Borrowed, globally owned assets (owner: {@link GameAssets}). */
    private final GameAssets assets;

    /** Loaded ASCII map (pure CPU data). */
    private AsciiMap map;
    /** Four-season hub model: geography, seasonal blend, border gates, chunks. */
    private WorldSeasonMap worldSeasonMap;

    private PerspectiveCamera camera;
    private ThirdPersonCameraController look;
    private PlayerAvatarRenderer avatarRenderer;
    private Heightfield heightfield;
    private HeightmapTerrainRenderer terrainRenderer;
    private AsciiPostProcessor asciiPost;
    private SceneryPlan sceneryPlan;

    private Player player;
    private World world;

    private Stage uiStage;
    private Skin skin;
    private Label promptLabel;
    private Label crosshairLabel;
    /** HUD-only style with one extra pixel of tracking on tiny status text. */
    private Label.LabelStyle hudLabelStyle;
    /** Screen-space floating [E] TALK prompt above the village NPC. */
    private Label npcTalkLabel;
    /** Shared 1x1 white pixel backing every flat HUD panel and bar. */
    private Texture hudPixelTexture;
    private TextureRegion hudPixelRegion;
    private HudBar healthBar;
    private HudBar manaBar;
    private HudBar dashBar;
    private Label healthValueLabel;
    private Label manaValueLabel;
    private Label dashValueLabel;
    private Label hudStatusLabel;
    /** Top-centre Sable-style compass rose and region banner. */
    private Label compassLabel;
    private Label seasonBannerLabel;
    private WorldSeasonMap.Season lastAnnouncedSeason;
    private float seasonBannerRemaining;
    private Label cardStateLabel;
    private HudCardSlot[] cardSlots;
    private InventoryOverlay inventoryOverlay;
    private DialogueOverlay dialogueOverlay;
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
    /** Procedural 3D Yokai rigs, hit frames and defeat bursts. */
    private final YokaiRigRenderer yokaiRigRenderer = new YokaiRigRenderer();
    /** Friendly shrine maiden / Hanafuda merchant near the temple. */
    private final VillageNpcRenderer villageNpcRenderer = new VillageNpcRenderer();
    private boolean villageNpcPlaced;
    private float villageNpcWorldX;
    private float villageNpcWorldZ;
    private float villageNpcGround;
    private float villageNpcYaw;
    /** Additional sanctuary villagers around the plaza houses. */
    private final List<VillageNpc> villagers = new ArrayList<>();
    /** World anchor of the NPC whose talk prompt is currently shown. */
    private float npcPromptX;
    private float npcPromptY;
    private float npcPromptZ;

    /** One friendly villager rig with a fixed post. */
    private static final class VillageNpc {
        final VillageNpcRenderer renderer = new VillageNpcRenderer();
        final float x;
        final float z;
        final float ground;
        final float yaw;

        VillageNpc(float x, float z, float ground, float yaw) {
            this.x = x;
            this.z = z;
            this.ground = ground;
            this.yaw = yaw;
        }
    }
    private final Vector3 npcScreen = new Vector3();
    /** Interaction radius of the friendly village NPC in world units. */
    private static final float NPC_INTERACT_RADIUS = 2.35f;

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
    /** Static Box2D bodies grouped by 32-tile map chunk for activation culling. */
    private final Map<CollisionChunk, List<Body>> chunkBodies = new HashMap<>();
    /** Chunks whose static bodies are currently active. */
    private final Set<CollisionChunk> activeCollisionChunks = new HashSet<>();
    /** Distance at which a collision chunk stays active, in world units. */
    private static final float COLLISION_CULL_RADIUS = 72f;

    /** One 32-tile collision chunk of the playable map. */
    private record CollisionChunk(int col, int row) {
    }
    /** Solid scenery footprints with their top height (camera boom blockers). */
    private final List<SceneryPlan.Solid> cameraSolids = new ArrayList<>();
    /** Wall runs used to keep the camera out of the woven boundary fences. */
    private final List<WallLayout.Run> cameraWallRuns = new ArrayList<>();
    /** One-time method references: no allocation per frame. */
    private final OverworldYokaiEntity.CollisionQuery collisionQuery = this::isBlockedInWorld;
    private final ThirdPersonCameraController.TerrainSampler terrainSampler = this::terrainHeight;
    private final ThirdPersonCameraController.ObstacleQuery cameraObstacleQuery =
        this::isCameraObstructed;

    /** Tile size of the loaded ASCII map (Yokai roaming extents). */
    private int tileWidth;
    private int tileHeight;
    private float mapPixelWidth;
    private float mapPixelHeight;

    /** Crosshair aim point in 2D world pixels (card throw target). */
    private final Vector2 aimWorld = new Vector2();
    /** Camera-relative WASD direction in 2D world space. */
    private final Vector2 moveDirection = new Vector2();
    /** Reused ground-plane forward used as the idle avatar facing. */
    private final Vector2 avatarForward2d = new Vector2();
    /** Smoothed body velocity for movement weight. */
    private float smoothedVx;
    private float smoothedVy;

    /** Reused third-person rig vectors (no allocation per frame). */
    private final Vector3 cameraTarget = new Vector3();
    private final Vector3 cameraPose = new Vector3();
    private final Vector3 aimDirection = new Vector3();
    private final Vector3 aimPoint3 = new Vector3();
    private final Vector3 handOrigin = new Vector3();
    private final Vector3 cardRayDirection = new Vector3();

    /** Reused 3D card basis and face colours (no allocation per card). */
    private final Vector3 cardCenter = new Vector3();
    private final Vector3 cardSide = new Vector3();
    private final Vector3 cardLong = new Vector3();
    /** Reused billboard/decal colour. */
    private final Color effectColor = new Color();
    /** Camera-right axis in world XZ, reused for multi-part billboard offsets. */
    private float billboardRightX = 1f;
    private float billboardRightZ = 0f;

    /** Pointer capture state of the mouse look. */
    private boolean mouseLookEnabled = true;
    /** true on the frame where a click only re-captured the pointer. */
    private boolean mouseRecaptured;

    /** Right-click locked hostile whose feet carry the azure Kamon lock ring. */
    private Entity lockOnTarget;
    private float lockOnRemaining;
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
        // The overworld is the generated 4-season hub world: one large square
        // map whose centre is the Sanctuary Village crossroads.
        worldSeasonMap = WorldSeasonMap.createHubWorld(WORLD_TILES);
        map = worldSeasonMap.getMap();
        applyLocationMetadata();

        tileWidth = map.getTileSize();
        tileHeight = map.getTileSize();
        mapPixelWidth = map.getWorldWidth();
        mapPixelHeight = map.getWorldHeight();

        // 2. Box2D world without gravity: the second dimension is the ground plane.
        world = new World(new Vector2(0f, 0f), true);
        createCollisionBodies();
        collectInteractionTiles();

        // 3. Procedural 3D terrain and the third-person camera rig.
        heightfield = createHeightfield();
        camera = new PerspectiveCamera(FIELD_OF_VIEW, 1280f, 720f);
        camera.near = NEAR_PLANE;
        camera.far = FAR_PLANE;
        look = new ThirdPersonCameraController();
        avatarRenderer = new PlayerAvatarRenderer();

        // 4. Player at the '@' cell (or at the stored overworld position).
        float startX = map.tileCenterX(map.getPlayerSpawn().col());
        float startY = map.tileCenterY(map.getPlayerSpawn().row());

        automaticCombatLocked = runSession.hasStoredPosition()
            && runSession.getLastPlayerX() >= 0f
            && runSession.getLastPlayerX() < mapPixelWidth
            && runSession.getLastPlayerY() >= 0f
            && runSession.getLastPlayerY() < mapPixelHeight;
        if (automaticCombatLocked) {
            startX = runSession.getLastPlayerX();
            startY = runSession.getLastPlayerY();
        } else if (runSession.hasStoredPosition()) {
            Gdx.app.log("OverworldScreen",
                "Stored overworld position lies outside the 4-season world; "
                    + "spawning at the Sanctuary Village crossroads.");
        }
        player = new Player(world, startX, startY);

        // 5. Scene renderer (heightmap mesh + procedural Japanese village) and
        // the atmospheric post pass.
        terrainRenderer = new HeightmapTerrainRenderer(heightfield, assets);
        terrainRenderer.setWorldSeasonMap(worldSeasonMap);
        SceneryPlan sceneryPlan = SceneryPlan.generate(map, heightfield, WORLD_SCALE,
            terrainSeed(map) ^ 0x51E4_2A11_9C3DL, worldSeasonMap);
        this.sceneryPlan = sceneryPlan;
        terrainRenderer.setScenery(new JapaneseScenery(sceneryPlan, heightfield));
        NatureModelRenderer natureRenderer =
            new NatureModelRenderer(sceneryPlan, assets, worldSeasonMap);
        terrainRenderer.setNatureRenderer(natureRenderer);
        Gdx.app.log("OverworldScreen", "Organic nature models placed: "
            + natureRenderer.getInstanceCount() + " instances from "
            + GameAssets.NATURE_MODELS.length + " OBJ assets, split over "
            + natureRenderer.getChunkCount() + " spatial chunks.");
        // Every visible torii pillar, lantern and temple wall becomes solid now
        // that the scenery anchors exist.
        registerStructureCollision(sceneryPlan);
        initializeVillageNpc();
        asciiPost = new AsciiPostProcessor();

        // 6. Scene2D HUD and inventory overlay.
        uiStage = new Stage(new FitViewport(1280f, 720f));
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();
        hudLabelStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        hudLabelStyle.font = FontManager.getHudFont();

        promptLabel = new Label("", hudLabelStyle);
        promptLabel.setFontScale(0.62f);
        promptLabel.setColor(COLOR_HUD_GOLD);

        Table promptTable = new Table();
        promptTable.setFillParent(true);
        promptTable.bottom().padBottom(122f);
        promptTable.add(promptLabel);
        uiStage.addActor(promptTable);
        promptLabel.setVisible(false);

        npcTalkLabel = new Label("[E] TALK", hudLabelStyle);
        npcTalkLabel.setFontScale(0.52f);
        npcTalkLabel.setColor(COLOR_HUD_GOLD);
        npcTalkLabel.setSize(140f, 26f);
        npcTalkLabel.setVisible(false);
        uiStage.addActor(npcTalkLabel);

        gameAtlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // 7. Ashley ECS combat layer: player, Hanko, Omamori passives.
        initCombatLayer();

        // 8. Wild Yokai as roaming actors AND hostile entities.
        createYokaiEntities();

        // 9. Yaku payloads earned in the card battle are applied now.
        applyPendingYakuEffects();

        // 10. Buttons, inventory overlay and the 3P HUD.
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.up = assets.newButtonDrawable();
        btnStyle.down = ((NinePatchDrawable) btnStyle.up).tint(Color.LIGHT_GRAY);
        btnStyle.font = FontManager.getFont();
        btnStyle.fontColor = FontManager.COLOR_TEXT_MAIN;

        NinePatchDrawable panelBg = assets.newPanelDrawable();
        inventoryOverlay = new InventoryOverlay(runSession, skin, gameAtlas, panelBg, btnStyle);
        uiStage.addActor(inventoryOverlay);
        dialogueOverlay = new DialogueOverlay(skin, panelBg, btnStyle,
            this::healSpiritFromDialogue, this::inspectDeckFromDialogue);
        uiStage.addActor(dialogueOverlay);

        createHud();
        if (npcTalkLabel != null) {
            npcTalkLabel.toFront();
        }
        updateCamera(0f);
        updateHud();

        // Prime the avatar so the first-frame card throw already starts from the
        // correct hand position (getHandPosition falls back to this pose).
        Vector2 avatarStart = player.body.getPosition();
        float avatarStartX = avatarStart.x * WORLD_SCALE;
        float avatarStartZ = -avatarStart.y * WORLD_SCALE;
        avatarRenderer.update(0f, avatarStartX, avatarStartZ,
            groundHeightOr(heightfield.getHeight(avatarStartX, avatarStartZ)),
            0f, -1f, false, 0f, false, 0f, -1f, 0f, 0f, 0f, 0f);

        Gdx.app.log("OverworldScreen", "Third-person ASCII overworld ready: map '"
            + map.getLocationId() + "' (" + map.getWidth() + "x" + map.getHeight()
            + " tiles), terrain " + Math.round(heightfield.getSize()) + " units @ "
            + heightfield.getVerticesPerSide() + "x" + heightfield.getVerticesPerSide()
            + " vertices, " + yokaiEntities.size() + " Yokai spawn(s), FOV "
            + Math.round(FIELD_OF_VIEW) + " near " + NEAR_PLANE + " far " + FAR_PLANE);
    }

    /**
     * Places the friendly NPC just beside the main temple's front steps. The
     * body faces outward toward the shrine approach; the head tracks the player.
     */
    private void initializeVillageNpc() {
        villageNpcPlaced = false;
        villagers.clear();
        if (sceneryPlan == null) {
            return;
        }
        // Sanctuary villagers stand in front of the plaza houses and share the
        // same [E] TALK interaction as the shrine maiden.
        float hubX = worldSeasonMap == null ? 0f : worldSeasonMap.getHubWorldX();
        float hubZ = worldSeasonMap == null ? 0f : worldSeasonMap.getHubWorldZ();
        for (Vector2 anchor : sceneryPlan.getVillagerAnchors()) {
            float ground = heightfield.getHeight(anchor.x, anchor.y);
            float yaw = (float) Math.atan2(anchor.x - hubX, anchor.y - hubZ);
            villagers.add(new VillageNpc(anchor.x, anchor.y, ground, yaw));
        }
        List<Vector2> entrances = sceneryPlan.getTempleEntranceAnchors();
        if (!entrances.isEmpty()) {
            Vector2 entrance = entrances.get(0);
            float approachX = 0f;
            float approachZ = 1f;
            if (!sceneryPlan.getTempleAnchors().isEmpty()) {
                Vector2 temple = sceneryPlan.getTempleAnchors().get(0);
                approachX = entrance.x - temple.x;
                approachZ = entrance.y - temple.y;
                float length = (float) Math.sqrt(
                    approachX * approachX + approachZ * approachZ);
                if (length > 0.0001f) {
                    approachX /= length;
                    approachZ /= length;
                }
            }
            float sideX = -approachZ;
            float sideZ = approachX;
            villageNpcWorldX = entrance.x + sideX * 1.55f + approachX * 0.55f;
            villageNpcWorldZ = entrance.y + sideZ * 1.55f + approachZ * 0.55f;
            villageNpcYaw = (float) Math.atan2(-approachX, -approachZ);
        } else if (!shrineTiles.isEmpty()) {
            AsciiMap.Tile shrine = shrineTiles.get(0);
            villageNpcWorldX = map.tileCenterX(shrine.col()) * WORLD_SCALE + 1.3f;
            villageNpcWorldZ = -map.tileCenterY(shrine.row()) * WORLD_SCALE;
            villageNpcYaw = 0f;
        } else {
            return;
        }
        villageNpcGround = heightfield.getHeight(villageNpcWorldX, villageNpcWorldZ);
        villageNpcPlaced = true;
        Gdx.app.log("OverworldScreen", "Village NPC (Shrine Maiden) at world="
            + Math.round(villageNpcWorldX) + "," + Math.round(villageNpcWorldZ));
    }

    // ------------------------------------------------------------------
    // HUD construction
    // ------------------------------------------------------------------

    /**
     * Builds the top-left HP/Mana/Tumble panel, the bottom-centre 3P Hanafuda
     * deck and the fixed crosshair. All panels use a dark translucent backing
     * box so the terminal world never bleeds through the text.
     */
    private void createHud() {
        createHudPixel();

        createStatsPanel();
        createCardPanel();
        createCompassBanner();

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

    /** One shared white pixel for every flat HUD drawable. */
    private void createHudPixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            hudPixelTexture = new Texture(pixmap);
        } finally {
            pixmap.dispose();
        }
        hudPixelRegion = new TextureRegion(hudPixelTexture);
    }

    /** Top-left combat readout: clear HP, Mana and Q-Tumble bars. */
    private void createStatsPanel() {
        healthBar = new HudBar(hudPixelRegion, COLOR_BAR_HEALTH);
        manaBar = new HudBar(hudPixelRegion, COLOR_BAR_MANA);
        dashBar = new HudBar(hudPixelRegion, COLOR_BAR_DASH);

        healthValueLabel = hudLabel("", 0.62f, COLOR_HUD_TEXT);
        manaValueLabel = hudLabel("", 0.62f, COLOR_HUD_TEXT);
        dashValueLabel = hudLabel("", 0.62f, COLOR_HUD_TEXT);

        Table inner = new Table();
        inner.setBackground(pixelDrawable(COLOR_HUD_BACKING));
        inner.pad(10f);
        inner.top().left();

        Label title = hudLabel("WANDERER", 0.66f, COLOR_HUD_GOLD);
        inner.add(title).left().colspan(3).padBottom(6f);
        inner.row();

        addStatRow(inner, "HP", healthBar, healthValueLabel);
        addStatRow(inner, "MANA", manaBar, manaValueLabel);
        addStatRow(inner, "TUMBLE [Q]", dashBar, dashValueLabel);

        hudStatusLabel = hudLabel("", 0.52f, COLOR_HUD_MUTED);
        inner.add(hudStatusLabel).left().colspan(3).padTop(6f);

        Table frame = new Table();
        frame.setBackground(pixelDrawable(COLOR_HUD_EDGE));
        frame.pad(1f);
        frame.add(inner).grow();

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(16f);
        root.add(frame);
        uiStage.addActor(root);
    }

    /** Adds one label + bar + value row to the stats panel. */
    private void addStatRow(Table table, String name, HudBar bar, Label value) {
        Label nameLabel = hudLabel(name, 0.60f, COLOR_HUD_MUTED);
        table.add(nameLabel).left().padRight(8f).padTop(4f);
        table.add(bar).width(168f).height(10f).padTop(4f);
        table.add(value).right().padLeft(8f).padTop(4f);
        table.row();
    }

    /**
     * Minimal Sable-style top compass: the four cardinal glyphs with the active
     * facing bracketed, followed by the current realm banner.
     */
    private void createCompassBanner() {
        compassLabel = hudLabel("N   E   S   W", 0.70f, COLOR_HUD_GOLD);
        seasonBannerLabel = hudLabel("SANCTUARY VILLAGE", 0.62f, COLOR_HUD_TEXT);

        Table plate = new Table();
        plate.setBackground(pixelDrawable(COLOR_HUD_BACKING));
        plate.pad(7f, 18f, 7f, 18f);
        plate.add(compassLabel).padRight(20f);
        plate.add(seasonBannerLabel);

        Table frame = new Table();
        frame.setBackground(pixelDrawable(COLOR_HUD_EDGE));
        frame.pad(1f);
        frame.add(plate);

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(14f);
        root.add(frame);
        uiStage.addActor(root);

        lastAnnouncedSeason = null;
        seasonBannerRemaining = 0f;
    }

    /**
     * Updates the compass rose from the camera facing and announces the realm
     * under the player. Crossing a border torii brightens the banner for a few
     * seconds; otherwise it stays as a quiet region indicator.
     */
    private void updateCompassBanner(float delta) {
        if (compassLabel == null || seasonBannerLabel == null
            || worldSeasonMap == null || player == null) {
            return;
        }
        WorldSeasonMap.Cardinal facing = WorldSeasonMap.Cardinal.fromDirection(
            camera.direction.x, camera.direction.z);
        StringBuilder rose = new StringBuilder();
        for (WorldSeasonMap.Cardinal cardinal : WorldSeasonMap.Cardinal.values()) {
            if (rose.length() > 0) {
                rose.append("   ");
            }
            if (cardinal == facing) {
                rose.append('[').append(cardinal.glyph()).append(']');
            } else {
                rose.append(cardinal.glyph());
            }
        }
        compassLabel.setText(rose);

        Vector2 position = player.body.getPosition();
        WorldSeasonMap.Season region = worldSeasonMap.regionAtWorld(
            position.x * WORLD_SCALE, -position.y * WORLD_SCALE);
        if (region != lastAnnouncedSeason) {
            lastAnnouncedSeason = region;
            seasonBannerRemaining = SEASON_BANNER_SECONDS;
            Gdx.app.log("OverworldScreen", "Entering " + region.banner());
        }
        seasonBannerRemaining = Math.max(0f, seasonBannerRemaining - delta);
        seasonBannerLabel.setText(region.banner());
        float alpha = seasonBannerRemaining > 0f
            ? MathUtils.clamp(0.40f + seasonBannerRemaining / SEASON_BANNER_SECONDS,
                0f, 1f)
            : 0.40f;
        seasonBannerLabel.setColor(COLOR_HUD_TEXT.r, COLOR_HUD_TEXT.g,
            COLOR_HUD_TEXT.b, alpha);
        seasonBannerLabel.setFontScale(seasonBannerRemaining > 0f ? 0.70f : 0.62f);
    }

    /** Bottom-centre 3P Hanafuda deck with framed, type-coloured card slots. */
    private void createCardPanel() {
        CardDeckComponent.CardType[] types = CardDeckComponent.CardType.values();
        cardSlots = new HudCardSlot[types.length];

        Table slots = new Table();
        for (int i = 0; i < types.length; i++) {
            CardDeckComponent.CardType type = types[i];
            TextureRegion cardArt = gameAtlas.findRegion(
                HudCardSlot.cardArtRegionName(type, runSession.getCurrentSeason()));
            cardSlots[i] = new HudCardSlot(skin, hudPixelRegion, cardArt,
                new Color(type.red, type.green, type.blue, 1f),
                String.valueOf(type.projectileGlyph), cardKind(type), "E");
            slots.add(cardSlots[i]).size(214f, 104f).pad(4f);
        }

        cardStateLabel = hudLabel("", 0.58f, COLOR_CARD_IDLE);

        Table inner = new Table();
        inner.setBackground(pixelDrawable(COLOR_HUD_BACKING));
        inner.pad(6f);
        inner.add(slots);
        inner.row();
        inner.add(cardStateLabel).padTop(3f);

        Table frame = new Table();
        frame.setBackground(pixelDrawable(COLOR_HUD_EDGE));
        frame.pad(1f);
        frame.add(inner).grow();

        Table root = new Table();
        root.setFillParent(true);
        root.bottom().padBottom(18f);
        root.add(frame);
        uiStage.addActor(root);
    }

    /** Atmospheric archetype caption of one Hanafuda card slot. */
    private static String cardKind(CardDeckComponent.CardType type) {
        return switch (type) {
            case RED -> "CRIMSON PETAL";
            case BLUE -> "AZURE RIBBON";
            case GOLD -> "GOLD BEAST";
        };
    }

    /** Creates a label in the shared HUD font scale and colour. */
    private Label hudLabel(String text, float fontScale, Color color) {
        Label label = new Label(text, hudLabelStyle);
        label.setFontScale(fontScale);
        label.setColor(color);
        return label;
    }

    /** Flat, tinted 1x1 drawable for HUD backings and borders. */
    private Drawable pixelDrawable(Color color) {
        return new TextureRegionDrawable(hudPixelRegion).tint(new Color(color));
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
            size, Math.max(TERRAIN_RESOLUTION, Math.round(size * 0.8f)),
            TERRAIN_AMPLITUDE);

        // Raise the mountain ring that separates the four seasonal realms; the
        // four cardinal passes stay open so the paved roads climb through.
        if (worldSeasonMap != null && worldSeasonMap.isFourSeasonWorld()) {
            field.raiseChebyshevRing(centerX, centerZ,
                WorldSeasonMap.MOUNTAIN_RING_DISTANCE_TILES,
                WorldSeasonMap.MOUNTAIN_RING_THICKNESS + 2.6f, 4.2f,
                WorldSeasonMap.ROAD_HALF_WIDTH_TILES + 2.5f);
        }

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
            this::isBlockedAtWorldPoint,
            (x, z) -> heightfield.getHeight(x, z),
            WORLD_SCALE);

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
        if (lockOnRemaining > 0f) {
            lockOnRemaining = Math.max(0f, lockOnRemaining - delta);
        }
        if (lockOnRemaining <= 0f) {
            lockOnTarget = null;
        }

        PlayerInput input = ecs.getInput();
        boolean paused = inventoryOverlay.isOpen()
            || (dialogueOverlay != null && dialogueOverlay.isOpen());

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

        // --- third-person crosshair aim --------------------------------------
        Vector2 playerPosition = player.body.getPosition();
        float aimUnitX = playerPosition.x * WORLD_SCALE;
        float aimUnitZ = -playerPosition.y * WORLD_SCALE;
        float aimGround = heightfield.getHeight(aimUnitX, aimUnitZ);

        // Rebuild the exact camera pose of updateCamera() so the centre '+'
        // and the rendered view always agree, even when the boom is clamped.
        look.getTarget(aimUnitX, groundHeightOr(aimGround), aimUnitZ, cameraTarget);
        if (player.isMoving()) {
            cameraTarget.y += MathUtils.sin(player.getWalkPhase() * 9f) * HEAD_BOB;
        }
        look.computeCameraPosition(cameraTarget, terrainSampler, cameraObstacleQuery, cameraPose);
        look.getForward(aimDirection);
        look.resolveAimPoint(cameraPose, aimDirection, terrainSampler, cameraObstacleQuery,
            CROSSHAIR_RAY_DISTANCE, aimPoint3);

        // Crosshair target: the hostile billboard the camera ray actually
        // crosses before terrain/walls, so no lock-on through hills.
        float aimDistance = cameraPose.dst(aimPoint3);
        Entity crosshairTarget = crosshairHostile(cameraPose, aimDirection, aimDistance);
        if (crosshairTarget != null) {
            TransformComponent target = Mappers.TRANSFORM.get(crosshairTarget);
            if (target != null) {
                aimWorld.set(target.x, target.y);
                float targetX = target.x * WORLD_SCALE;
                float targetZ = -target.y * WORLD_SCALE;
                aimPoint3.set(targetX,
                    heightfield.getHeight(targetX, targetZ) + HOSTILE_CROSSHAIR_HEIGHT,
                    targetZ);
            }
        } else {
            aimWorld.set(aimPoint3.x / WORLD_SCALE, -aimPoint3.z / WORLD_SCALE);
        }
        input.aimX = aimWorld.x;
        input.aimY = aimWorld.y;

        // Cards leave the avatar's right hand; the last rendered pose supplies
        // the world-space hand position (one frame of latency).
        avatarRenderer.getHandPosition(handOrigin);
        input.throwOrigin.set(handOrigin.x / WORLD_SCALE, -handOrigin.z / WORLD_SCALE);
        input.hasThrowOrigin = true;
        cardRayDirection.set(aimPoint3).sub(handOrigin);
        if (cardRayDirection.len2() < 0.0001f) {
            look.getForward(cardRayDirection);
        }
        input.hasAimRay = true;
        input.aimRayOrigin.set(handOrigin);
        input.aimRayDirection.set(cardRayDirection).nor();

        // --- card throw and lock-on ----------------------------------------
        boolean attackRequested = !paused && !mouseRecaptured
            && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean rightPressed = !paused
            && Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);

        // Right mouse = lock-on: attack the crosshair target when it is in card
        // range, otherwise chase it (MOBA target movement stays available). A
        // right-click without a target falls through to the card shuffle below.
        if (paused) {
            input.hasMoveTarget = false;
        } else if (rightPressed && crosshairTarget != null) {
            TransformComponent target = Mappers.TRANSFORM.get(crosshairTarget);
            if (target != null) {
                lockOnTarget = crosshairTarget;
                lockOnRemaining = 3.0f;
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

        // Q = tumble/dash in the held WASD direction; E/R/RMB = 3P card
        // shuffle-lock; F = Hanko cast; E = interact. S is backpedal, not stop,
        // in third person. E only shuffles when it is not standing on a tavern,
        // shop or shrine interaction cell.
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

        boolean ePressed = !paused && Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean rPressed = !paused && Gdx.input.isKeyJustPressed(Input.Keys.R);
        input.cardSelectRequested = CardSelectBinding.shouldShuffle(
            rPressed, ePressed, rightPressed,
            ePressed && isNearManualTrigger(),
            rightPressed && crosshairTarget != null);
        input.castRequested = !paused && Gdx.input.isKeyJustPressed(Input.Keys.F);
        input.time = overworldTime;
    }

    /**
     * Living hostile whose billboard the camera ray actually crosses first,
     * within card range and in front of the terrain/wall hit behind the
     * crosshair. The most centred target wins, with proximity as tie-breaker.
     */
    private Entity crosshairHostile(Vector3 rayOrigin, Vector3 rayDirection, float maxDistance) {
        if (ecs == null) {
            return null;
        }
        Vector2 origin = player.body.getPosition();
        float range = PlayerControlSystem.CARD_RANGE_TILES * Math.max(1, tileWidth);
        float range2 = range * range;
        float bestScore = Float.POSITIVE_INFINITY;
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
            if (dx * dx + dy * dy > range2) {
                continue;
            }

            float unitX = transform.x * WORLD_SCALE;
            float unitZ = -transform.y * WORLD_SCALE;
            float centerY = heightfield.getHeight(unitX, unitZ) + HOSTILE_CROSSHAIR_HEIGHT;
            float toX = unitX - rayOrigin.x;
            float toY = centerY - rayOrigin.y;
            float toZ = unitZ - rayOrigin.z;

            // Project the billboard centre onto the 3D camera ray.
            float along = toX * rayDirection.x + toY * rayDirection.y + toZ * rayDirection.z;
            if (along <= 0f || along > maxDistance) {
                continue;
            }
            float offsetX = toX - rayDirection.x * along;
            float offsetY = toY - rayDirection.y * along;
            float offsetZ = toZ - rayDirection.z * along;
            float miss = (float) Math.sqrt(
                offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
            if (miss > HOSTILE_CROSSHAIR_RADIUS) {
                continue;
            }

            float score = miss + along / (range * 8f);
            if (score < bestScore) {
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
    private void applyPlayerMotionToBody(float delta) {
        if (inventoryOverlay.isOpen()
            || (dialogueOverlay != null && dialogueOverlay.isOpen())) {
            smoothedVx = 0f;
            smoothedVy = 0f;
            player.body.setLinearVelocity(0f, 0f);
            return;
        }
        MotionComponent motion = ecs.getPlayerMotion();
        PlayerComponent playerComponent = ecs.getPlayer();
        if (playerComponent != null && playerComponent.isDashing()) {
            // The dash is a committed burst: snap to its velocity, then let the
            // deceleration curve carry the weight out of it.
            smoothedVx = motion.vx;
            smoothedVy = motion.vy;
        } else {
            float target2 = motion.vx * motion.vx + motion.vy * motion.vy;
            float current2 = smoothedVx * smoothedVx + smoothedVy * smoothedVy;
            float rate = target2 >= current2 ? MOVE_ACCEL_RATE : MOVE_DECEL_RATE;
            smoothedVx = VelocitySmoothing.approach(smoothedVx, motion.vx, delta, rate);
            smoothedVy = VelocitySmoothing.approach(smoothedVy, motion.vy, delta, rate);
        }
        player.body.setLinearVelocity(smoothedVx, smoothedVy);
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
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        if (actor != null) {
            if (transform != null) {
                float worldX = transform.x * WORLD_SCALE;
                float worldZ = -transform.y * WORLD_SCALE;
                yokaiRigRenderer.spawnDefeat(worldX, worldZ,
                    heightfield.getHeight(worldX, worldZ), actor.getSpeciesId());
            }
            yokaiRigRenderer.forget(actor);
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

        if (transform != null && ecs != null) {
            ecs.sparks(transform.x, transform.y, 20);
        }
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void updateHud() {
        if (healthBar == null || ecs == null) {
            return;
        }

        PlayerComponent playerComponent = ecs.getPlayer();
        HankoCastConfig config = ecs.getHankoConfig();
        HealthComponent playerHealth = Mappers.HEALTH.get(ecs.getPlayerEntity());

        float healthRatio = playerHealth == null ? 0f : playerHealth.ratio();
        healthBar.setRatio(healthRatio);
        healthValueLabel.setText(playerHealth == null ? "--/--"
            : Math.max(0, Math.round(playerHealth.current)) + "/"
                + Math.round(playerHealth.max));

        float manaRatio = playerComponent.maxMana <= 0f
            ? 0f : playerComponent.mana / playerComponent.maxMana;
        manaBar.setRatio(manaRatio);
        manaValueLabel.setText(Math.round(Math.max(0f, playerComponent.mana)) + "/"
            + Math.round(playerComponent.maxMana));

        float dashCooldown = Math.max(0.001f, playerComponent.dashCooldown);
        float dashReady = 1f - MathUtils.clamp(
            playerComponent.dashCooldownRemaining / dashCooldown, 0f, 1f);
        dashBar.setRatio(dashReady);
        dashValueLabel.setText(playerComponent.dashCooldownRemaining <= 0f
            ? "READY" : String.format(Locale.ROOT, "%.1fs",
                playerComponent.dashCooldownRemaining));

        String lifesteal = playerComponent.lifestealRemaining <= 0f ? "READY"
            : String.format(Locale.ROOT, "%.0f%% %.1fs",
                playerComponent.lifestealPercent * 100f,
                playerComponent.lifestealRemaining);
        hudStatusLabel.setText("HANKO " + config.name + " [F]   LIFESTEAL " + lifesteal
            + "   OMAMORI " + runSession.getActiveOmamoris().size() + "/"
            + runSession.getMaxActiveOmamoris() + "   [I] INVENTORY");

        int aggro = 0;
        for (int i = 0; i < yokaiEntities.size(); i++) {
            if (yokaiEntities.get(i).isAggro()) {
                aggro++;
            }
        }
        if (crosshairLabel != null) {
            crosshairLabel.setColor(aggro > 0 ? COLOR_YOKAI_HIT : COLOR_CROSSHAIR);
        }

        updateCardHud(ecs.getPlayerDeck(), runSession.getPlayerDeck());
    }

    /**
     * Bottom-centre 3P deck: three framed Hanafuda slots, colour-coded by card
     * archetype. The slot that is currently cycling (or holds the locked card)
     * gets the bright border and the {@code LOCKED} badge.
     */
    private void updateCardHud(CardDeckComponent deck, Deck playerDeck) {
        if (cardSlots == null || cardStateLabel == null) {
            return;
        }
        CardDeckComponent.CardType[] types = CardDeckComponent.CardType.values();
        boolean cycling = deck != null && deck.showsBadge();
        boolean locked = deck != null && deck.state == CardDeckComponent.State.LOCKED;
        int activeIndex = deck == null ? -1
            : Math.floorMod(deck.activeIndex, deck.equipped.length);

        for (int i = 0; i < cardSlots.length; i++) {
            CardDeckComponent.CardType type = deck == null
                ? types[i] : deck.equipped[i];
            boolean active = cycling && i == activeIndex;
            cardSlots[i].setState(CardDeckNames.forType(type, playerDeck),
                active, active && locked);
        }

        if (deck == null) {
            cardStateLabel.setText("3P DECK READY");
            cardStateLabel.setColor(COLOR_CARD_IDLE);
            return;
        }

        float remaining = switch (deck.state) {
            case SHUFFLING -> Math.max(0f,
                CardDeckComponent.SHUFFLE_MAX_SECONDS - deck.stateTimer);
            case LOCKED -> Math.max(0f, deck.lockedRemaining);
            case COOLDOWN -> Math.max(0f, deck.cooldownRemaining);
            default -> 0f;
        };
        String timer = remaining > 0f
            ? String.format(Locale.ROOT, " %.1fs", remaining) : "";
        cardStateLabel.setText("3P " + deck.stateLabel() + timer
            + "   [E]/[R]/[RMB] SHUFFLE-LOCK   [LMB] THROW   [Q] TUMBLE");
        cardStateLabel.setColor(cycling ? COLOR_CARD_ACTIVE : COLOR_CARD_IDLE);
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
        cameraWallRuns.clear();
        chunkBodies.clear();
        activeCollisionChunks.clear();

        int cellsPerChunk = WorldSeasonMap.CHUNK_SIZE_TILES;
        for (WallLayout.Run run : WallLayout.scan(map)) {
            collisionRects.add(new Rectangle(run.x(), run.y(), run.width(), run.height()));
            cameraWallRuns.add(run);

            // Split long border runs at chunk boundaries so activation culling
            // never disables the stretch of fence the player is standing next to.
            int start = 0;
            while (start < run.length()) {
                int length = Math.min(cellsPerChunk, run.length() - start);
                float x = (run.col() + start) * (float) map.getTileSize();
                createStaticBody(x, run.y(),
                    length * (float) map.getTileSize(), run.height());
                start += length;
            }
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

        // Keep a copy for the third-person camera boom: the same footprints with
        // their top heights, so the boom never clips a torii pillar or the hall.
        cameraSolids.clear();
        cameraSolids.addAll(plan.getSolids());

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
        registerChunkBody(centerX, centerY, body);
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
        registerChunkBody(bodyDef.position.x, bodyDef.position.y, body);
    }

    /** Groups a static body by the 32-tile collision chunk of its centre. */
    private void registerChunkBody(float pixelX, float pixelY, Body body) {
        float worldX = pixelX * WORLD_SCALE;
        float worldZ = -pixelY * WORLD_SCALE;
        int size = WorldSeasonMap.CHUNK_SIZE_TILES;
        CollisionChunk chunk = new CollisionChunk(
            (int) Math.floor(worldX / size), (int) Math.floor(worldZ / size));
        chunkBodies.computeIfAbsent(chunk, key -> new ArrayList<>()).add(body);
    }

    /**
     * Activates only the static collision bodies of the chunks near the player.
     * Bodies further away stay in the Box2D world but are inactive, so the
     * large world does not pay broad-phase cost for every fence post and torii
     * pillar at once.
     */
    private void updateCollisionChunkCulling() {
        if (player == null || chunkBodies.isEmpty()) {
            return;
        }
        Vector2 position = player.body.getPosition();
        float playerX = position.x * WORLD_SCALE;
        float playerZ = -position.y * WORLD_SCALE;
        float size = WorldSeasonMap.CHUNK_SIZE_TILES;
        float half = size * 0.5f;
        float radius2 = COLLISION_CULL_RADIUS * COLLISION_CULL_RADIUS;

        for (Map.Entry<CollisionChunk, List<Body>> entry : chunkBodies.entrySet()) {
            CollisionChunk chunk = entry.getKey();
            float centerX = (chunk.col() + 0.5f) * size;
            float centerZ = (chunk.row() + 0.5f) * size;
            float dx = Math.max(0f, Math.abs(playerX - centerX) - half);
            float dz = Math.max(0f, Math.abs(playerZ - centerZ) - half);
            boolean visible = dx * dx + dz * dz <= radius2;
            boolean active = activeCollisionChunks.contains(chunk);
            if (visible == active) {
                continue;
            }
            List<Body> bodies = entry.getValue();
            for (int i = 0; i < bodies.size(); i++) {
                bodies.get(i).setActive(visible);
            }
            if (visible) {
                activeCollisionChunks.add(chunk);
            } else {
                activeCollisionChunks.remove(chunk);
            }
        }
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
        yokaiRigRenderer.clear();

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

        // Two gentle ambient archetypes guarantee that the lantern spirit and
        // the heavy oni appear even on maps whose single Y cell is a kitsune.
        spawnAmbientYokai("encounter_oni_wild", "oni", "Oni-Gashira",
            map.getWidth() / 4, map.getHeight() / 2);
        spawnAmbientYokai("ambient_chochin_obake", "chochin_obake", "Chōchin-Obake",
            (map.getWidth() * 3) / 4, map.getHeight() / 3);
    }

    /** Spawns an extra hostile without an encounter-catalog reward. */
    private void spawnAmbientYokai(String encounterId, String speciesId,
                                   String displayName, int preferredCol, int preferredRow) {
        // The Oni encounter is repeatable; the lantern spirit is a one-off
        // overworld exorcism (it has no purified Shikigami reward).
        if (!"encounter_oni_wild".equals(encounterId)
            && runSession.isEncounterDefeated(encounterId)) {
            return;
        }
        AsciiMap.Tile tile = findOpenFloorTile(preferredCol, preferredRow);
        if (tile == null) {
            return;
        }
        OverworldYokaiEntity actor = new OverworldYokaiEntity(
            encounterId, speciesId, displayName,
            OverworldYokaiEntity.WalkAnimations.placeholder(),
            map.tileCenterX(tile.col()), map.tileCenterY(tile.row()),
            tileWidth, tileHeight);
        yokaiEntities.add(actor);
        Entity hostile = ecs.addHostile(encounterId,
            actor.getWorldX(), actor.getWorldY(), HOSTILE_RADIUS,
            HOSTILE_MAX_HEALTH, hostileBridge(actor));
        hostileActors.put(hostile, actor);
    }

    /** First plain floor tile in a square search around the preferred cell. */
    private AsciiMap.Tile findOpenFloorTile(int preferredCol, int preferredRow) {
        int maxRadius = Math.max(map.getWidth(), map.getHeight());
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int row = preferredRow - radius; row <= preferredRow + radius; row++) {
                for (int col = preferredCol - radius; col <= preferredCol + radius; col++) {
                    if (radius > 0
                        && Math.abs(row - preferredRow) != radius
                        && Math.abs(col - preferredCol) != radius) {
                        continue;
                    }
                    if (map.isWalkable(col, row)
                        && map.glyphAt(col, row) == AsciiMap.FLOOR) {
                        return new AsciiMap.Tile(col, row);
                    }
                }
            }
        }
        return null;
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

        // Inventory toggle and pointer capture (third-person mouse look).
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)
            && (dialogueOverlay == null || !dialogueOverlay.isOpen())) {
            inventoryOverlay.toggle();
        }
        boolean paused = inventoryOverlay.isOpen()
            || (dialogueOverlay != null && dialogueOverlay.isOpen());
        updateMouseCapture(paused);

        // Input snapshot and ability/hanko state.
        updateCombatInput(delta);
        updateHankoSelection();
        refreshOmamoriLoadout(false);

        // Box2D -> ECS -> systems -> Box2D.
        syncPlayerIntoEcs();
        syncHostilesIntoEcs();
        ecs.update(delta);
        applyPlayerMotionToBody(delta);

        // Box2D clamps translation to 2 world units per step; in this pixel-scale
        // world that would cap the 460 px/s dash at the 120 px/s walk speed.
        // Substep fast frames so Q actually out-runs walking and the ghost trail
        // spans real distance, while still resolving collisions every step.
        int substeps = PhysicsSteps.forFrame(player.body.getLinearVelocity().len(), delta);
        float stepDelta = delta / substeps;
        for (int i = 0; i < substeps; i++) {
            world.step(stepDelta, 6, 2);
        }

        player.update(delta, player.body.getLinearVelocity());
        updateYokaiEntities(delta);
        syncPlayerFromBody();
        syncHostilesIntoEcs();
        updateCollisionChunkCulling();

        handleInteractions();
        updateHud();
        updateCompassBanner(delta);
        updateCamera(delta);

        // 3D heightmap scene -> native-resolution framebuffer -> atmospheric
        // post (bloom/Washi/ink-wash outlines) + crisp ASCII magic -> screen.
        asciiPost.setTime(overworldTime);
        asciiPost.begin();
        renderWorld(delta);
        // Normal/depth prepass sampled by the cel outline in ascii_post.frag.
        terrainRenderer.renderEdge(asciiPost.getEdgeFrameBuffer(), camera);
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
     * Place the third-person rig behind the player: smooth the ground height so
     * hills do not stair-step, park the shoulder pivot 1.8 units above the feet
     * and let the controller sweep its collision-clamped boom. Box2D X/Y
     * becomes 3D X/-Z, so north in the ASCII map stays north in 3D.
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

        look.getTarget(unitX, cameraGroundHeight, unitZ, cameraTarget);
        cameraTarget.y += bob;
        look.applyTo(camera, cameraTarget, terrainSampler, cameraObstacleQuery);
        asciiPost.setCamera(camera);
    }

    /** Smoothed camera ground, or the fallback before the first frame. */
    private float groundHeightOr(float fallback) {
        return Float.isNaN(cameraGroundHeight) ? fallback : cameraGroundHeight;
    }

    /** Ground height sampler handed to the third-person rig. */
    private float terrainHeight(float x, float z) {
        return heightfield.getHeight(x, z);
    }

    /**
     * Camera-boom blocker: tall scenery solids (torii pillars, lantern spires,
     * temple hall/roof, torches, offering box) and the woven boundary fences.
     * The heightfield itself is handled by the controller's terrain lift.
     */
    private boolean isCameraObstructed(float x, float y, float z) {
        for (int i = 0; i < cameraSolids.size(); i++) {
            SceneryPlan.Solid solid = cameraSolids.get(i);
            if (y > solid.topY()) {
                continue;
            }
            // Inverse yaw rotation: world (dx,dz) -> structure local frame.
            float dx = x - solid.x();
            float dz = z - solid.z();
            float cos = MathUtils.cos(solid.yaw());
            float sin = MathUtils.sin(solid.yaw());
            float localX = dx * cos - dz * sin;
            float localZ = dx * sin + dz * cos;
            if (Math.abs(localX) <= solid.halfWidth()
                && Math.abs(localZ) <= solid.halfDepth()) {
                return true;
            }
        }

        if (y < heightfield.getHeight(x, z) + FENCE_CAMERA_HEIGHT) {
            float pixelX = x / WORLD_SCALE;
            float pixelY = -z / WORLD_SCALE;
            for (int i = 0; i < cameraWallRuns.size(); i++) {
                WallLayout.Run run = cameraWallRuns.get(i);
                if (pixelX >= run.x() && pixelX <= run.x() + run.width()
                    && pixelY >= run.y() && pixelY <= run.y() + run.height()) {
                    return true;
                }
            }
        }
        return false;
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

    /** Draws the heightmap terrain, world markers, player, Yokai and effects. */
    private void renderWorld(float delta) {
        terrainRenderer.begin(camera, overworldTime);
        updateBillboardAxes();
        renderInteractionMarkers();
        updateAndRenderPlayerAvatar(delta);
        renderHostiles();
        renderVillageNpc(delta);
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

    /** Renders the friendly temple NPC + villagers and positions the talk prompt. */
    private void renderVillageNpc(float delta) {
        if (player == null) {
            hideNpcPrompt();
            return;
        }
        Vector2 position = player.body.getPosition();
        float playerX = position.x * WORLD_SCALE;
        float playerZ = -position.y * WORLD_SCALE;
        float nearest = Float.MAX_VALUE;
        float nearestX = 0f;
        float nearestY = 0f;
        float nearestZ = 0f;

        if (villageNpcPlaced) {
            float dx = playerX - villageNpcWorldX;
            float dz = playerZ - villageNpcWorldZ;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            villageNpcRenderer.render(terrainRenderer, villageNpcWorldX, villageNpcWorldZ,
                villageNpcGround, villageNpcYaw, playerX, playerZ, distance,
                delta, overworldTime);
            if (distance < nearest) {
                nearest = distance;
                nearestX = villageNpcWorldX;
                nearestY = villageNpcGround;
                nearestZ = villageNpcWorldZ;
            }
        }

        for (int i = 0; i < villagers.size(); i++) {
            VillageNpc villager = villagers.get(i);
            float dx = playerX - villager.x;
            float dz = playerZ - villager.z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            villager.renderer.render(terrainRenderer, villager.x, villager.z,
                villager.ground, villager.yaw, playerX, playerZ, distance,
                delta, overworldTime);
            if (distance < nearest) {
                nearest = distance;
                nearestX = villager.x;
                nearestY = villager.ground;
                nearestZ = villager.z;
            }
        }

        npcPromptX = nearestX;
        npcPromptY = nearestY;
        npcPromptZ = nearestZ;
        updateNpcPrompt(nearest);
    }

    private void updateNpcPrompt(float distance) {
        if (npcTalkLabel == null
            || distance > NPC_INTERACT_RADIUS + 0.8f
            || (inventoryOverlay != null && inventoryOverlay.isOpen())
            || (dialogueOverlay != null && dialogueOverlay.isOpen())) {
            hideNpcPrompt();
            return;
        }
        npcScreen.set(npcPromptX, npcPromptY + 2.08f, npcPromptZ);
        camera.project(npcScreen);
        if (npcScreen.z < 0f || npcScreen.z > 1f) {
            hideNpcPrompt();
            return;
        }
        float screenWidth = Math.max(1f, camera.viewportWidth);
        float screenHeight = Math.max(1f, camera.viewportHeight);
        float stageWidth = uiStage.getViewport().getWorldWidth();
        float stageHeight = uiStage.getViewport().getWorldHeight();
        float stageX = npcScreen.x / screenWidth * stageWidth;
        float stageY = npcScreen.y / screenHeight * stageHeight;
        npcTalkLabel.setPosition(stageX - 70f, stageY);
        npcTalkLabel.setVisible(true);
    }

    private void hideNpcPrompt() {
        if (npcTalkLabel != null) {
            npcTalkLabel.setVisible(false);
        }
    }

    /** true when the player is within talking range of any village NPC. */
    private boolean isNearVillageNpc() {
        if (player == null) {
            return false;
        }
        Vector2 position = player.body.getPosition();
        float playerX = position.x * WORLD_SCALE;
        float playerZ = -position.y * WORLD_SCALE;
        float radius2 = NPC_INTERACT_RADIUS * NPC_INTERACT_RADIUS;
        if (villageNpcPlaced) {
            float dx = playerX - villageNpcWorldX;
            float dz = playerZ - villageNpcWorldZ;
            if (dx * dx + dz * dz <= radius2) {
                return true;
            }
        }
        for (int i = 0; i < villagers.size(); i++) {
            VillageNpc villager = villagers.get(i);
            float dx = playerX - villager.x;
            float dz = playerZ - villager.z;
            if (dx * dx + dz * dz <= radius2) {
                return true;
            }
        }
        return false;
    }

    /** Dialogue choice: restore a generous share of HP and spirit/mana. */
    private void healSpiritFromDialogue() {
        if (ecs == null) {
            return;
        }
        HealthComponent health = Mappers.HEALTH.get(ecs.getPlayerEntity());
        if (health != null) {
            health.heal(health.max * 0.35f);
        }
        PlayerComponent playerComponent = ecs.getPlayer();
        if (playerComponent != null) {
            playerComponent.mana = Math.min(playerComponent.maxMana,
                playerComponent.mana + playerComponent.maxMana * 0.25f);
        }
        updateHud();
    }

    /** Dialogue choice: close the conversation and open the deck/inventory. */
    private void inspectDeckFromDialogue() {
        if (inventoryOverlay != null && !inventoryOverlay.isOpen()) {
            inventoryOverlay.toggle();
            inventoryOverlay.toFront();
        }
    }

    /**
     * Updates and queues the procedural Y2K avatar. It faces the dash, WASD or
     * camera-aim direction, bounces with the walk phase, leans sharply into a
     * Q-dash and leaves a two-frame ghost trail behind the dash.
     */
    private void updateAndRenderPlayerAvatar(float delta) {
        if (ecs == null || player == null || avatarRenderer == null) {
            return;
        }
        Vector2 position = player.body.getPosition();
        float worldX = position.x * WORLD_SCALE;
        float worldZ = -position.y * WORLD_SCALE;
        float ground = groundHeightOr(heightfield.getHeight(worldX, worldZ));

        PlayerInput input = ecs.getInput();
        PlayerComponent avatar = ecs.getPlayer();
        boolean dashing = avatar != null && avatar.isDashing();

        float faceX;
        float faceZ;
        if (dashing) {
            // The dash system locked the 2D facing to the dash direction.
            faceX = avatar.facingX;
            faceZ = -avatar.facingY;
        } else if (input.hasMoveDirection) {
            faceX = input.moveDirection.x;
            faceZ = -input.moveDirection.y;
        } else {
            look.getGroundForward(avatarForward2d);
            faceX = avatarForward2d.x;
            faceZ = -avatarForward2d.y;
        }

        float dashProgress = dashing && avatar.dashDuration > 0f
            ? MathUtils.clamp(1f - avatar.dashRemaining / avatar.dashDuration, 0f, 1f)
            : 0f;
        float throwProgress = avatar == null ? 0f
            : MathUtils.clamp(1f - avatar.throwAnimationRemaining
                / PlayerComponent.THROW_ANIMATION_DURATION, 0f, 1f);

        float dashX = avatar == null ? faceX : avatar.facingX;
        float dashZ = avatar == null ? faceZ : -avatar.facingY;
        Vector2 velocity = player.body.getLinearVelocity();
        avatarRenderer.update(
            delta, worldX, worldZ, ground, faceX, faceZ,
            player.isMoving() || input.hasMoveDirection, player.getWalkPhase(),
            dashing, dashX, dashZ,
            velocity.x * WORLD_SCALE, -velocity.y * WORLD_SCALE,
            dashProgress, throwProgress);
        avatarRenderer.render(terrainRenderer);
        renderCardTypeIndicator(worldX, ground, worldZ);
    }

    /**
     * Coloured Hanafuda sigil bobbing above the wanderer's head while the 3P
     * deck cycles or holds a locked card. Kiting players read the next card
     * without taking their eyes off the enemies.
     */
    private void renderCardTypeIndicator(float worldX, float ground, float worldZ) {
        if (ecs == null) {
            return;
        }
        CardDeckComponent deck = ecs.getPlayerDeck();
        if (deck == null || !deck.showsBadge()) {
            return;
        }
        CardDeckComponent.CardType type = deck.activeCard();
        effectColor.set(type.red, type.green, type.blue, 1f);
        float bob = MathUtils.sin(overworldTime * 3.4f) * 0.055f;
        float pulse = deck.state == CardDeckComponent.State.SHUFFLING
            ? 0.34f + 0.025f * MathUtils.sin(overworldTime * 9f)
            : 0.40f;
        terrainRenderer.drawBillboard(worldX, ground + 2.16f + bob, worldZ,
            pulse, pulse, effectColor);
        effectColor.mul(0.55f, 0.55f, 0.55f, 1f);
        terrainRenderer.drawBillboard(worldX, ground + 1.98f + bob, worldZ,
            0.15f, 0.22f, effectColor);
    }

    /** Wild Yokai as procedural multi-part 3D rigs on the heightfield. */
    private void renderHostiles() {
        if (player == null) {
            return;
        }
        float delta = Math.min(TerrainFollow.MAX_STEP_SECONDS, Gdx.graphics.getDeltaTime());
        Vector2 playerPosition = player.body.getPosition();
        float playerX = playerPosition.x * WORLD_SCALE;
        float playerZ = -playerPosition.y * WORLD_SCALE;

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
            yokaiRigRenderer.render(terrainRenderer, actor, health,
                worldX, worldZ, ground, playerX, playerZ,
                billboardRightX, billboardRightZ, flash, delta, overworldTime);
        }
    }

    /** Seals, cards, bursts, auras, sparks and particles in 3D. */
    private void renderCombatEffects() {
        yokaiRigRenderer.renderDefeatBursts(terrainRenderer,
            Math.min(TerrainFollow.MAX_STEP_SECONDS, Gdx.graphics.getDeltaTime()));
        renderLockOnRing();
        renderSealDecals();
        renderFlyingCards();
        renderCardBursts();
        renderLifestealAura();
        renderProjectilesAndParticles();
    }

    // ------------------------------------------------------------------
    // Seals: procedural Kamon crests on ground zones and lock-on targets
    // ------------------------------------------------------------------

    /**
     * Azure Kamon lock-on ring under the right-click target. It shares the
     * polar crest shader with Karma zones, but always selects the eight-spoke
     * wheel motif via its cool colour.
     */
    private void renderLockOnRing() {
        if (lockOnTarget == null || lockOnRemaining <= 0f) {
            return;
        }
        HealthComponent health = Mappers.HEALTH.get(lockOnTarget);
        TransformComponent transform = Mappers.TRANSFORM.get(lockOnTarget);
        if ((health != null && health.isDead()) || transform == null) {
            lockOnTarget = null;
            return;
        }
        float fade = MathUtils.clamp(lockOnRemaining / 3.0f, 0f, 1f);
        float pulse = 0.75f + 0.25f * MathUtils.sin(overworldTime * 8f);
        effectColor.set(KAMON_AZURE.r * fade * pulse, KAMON_AZURE.g * fade * pulse,
            KAMON_AZURE.b * fade * pulse, 1f);
        float worldX = transform.x * WORLD_SCALE;
        float worldZ = -transform.y * WORLD_SCALE;
        terrainRenderer.drawKamonSeal(worldX, worldZ, 0.55f, effectColor,
            overworldTime * 0.25f);
    }

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

            // Karma rune fields read as vermillion sakura crests; Hanko seals
            // keep their configured ink colour.
            boolean karma = seal.label != null && seal.label.indexOf('*') >= 0;
            float sealRed = karma ? KAMON_VERMILLION.r : seal.red;
            float sealGreen = karma ? KAMON_VERMILLION.g : seal.green;
            float sealBlue = karma ? KAMON_VERMILLION.b : seal.blue;
            effectColor.set(sealRed * fade * pulse,
                sealGreen * fade * pulse, sealBlue * fade * pulse, 1f);
            // The procedural Kamon crest replaces the old glyph ring entirely:
            // concentric rings, radial ticks and a sakura/wheel motif are drawn
            // in polar space by kamon_seal.frag.
            float phase = seal.age * 0.22f + worldX * 0.07f + worldZ * 0.11f;
            terrainRenderer.drawKamonSeal(worldX, worldZ,
                Math.max(0.12f, radiusUnits * 0.9f), effectColor, phase);
        }
    }

    // ------------------------------------------------------------------
    // Flying cards: billboards travelling along the crosshair ray
    // ------------------------------------------------------------------

    /**
     * Thin, rapidly tumbling 3D Hanafuda cards with a type-coloured trail.
     * The simulation destroys cards that dip into the terrain or hit a wall,
     * so this renderer never clamps (slides) a card along a hillside.
     */
    private void renderFlyingCards() {
        for (Entity entity : ecs.getEngine().getEntitiesFor(CARD_ENTITIES)) {
            CardProjectileComponent card = Mappers.CARD_PROJECTILE.get(entity);
            TransformComponent transform = Mappers.TRANSFORM.get(entity);
            if (card == null || transform == null) {
                continue;
            }

            float worldX = transform.x * WORLD_SCALE;
            float worldZ = -transform.y * WORLD_SCALE;
            float height = card.hasWorldRay
                ? card.worldHeight(WORLD_SCALE)
                : heightfield.getHeight(worldX, worldZ) + 0.9f;

            float speed = (float) Math.sqrt(card.vx * card.vx + card.vy * card.vy);
            if (speed < 0.0001f) {
                continue;
            }
            float dirX = card.vx / speed;
            float dirY = card.vy / speed;

            effectColor.set(card.red, card.green, card.blue, 1f);
            drawCard(worldX, height, worldZ, dirX, dirY, card, 0, effectColor);

            // One continuous tapered sumi-brush ribbon behind the card. It is
            // a procedural shader strip, not a glyph afterimage.
            float ribbonBack = Math.min(CARD_TRAIL_SPACING * CARD_TRAIL_STEPS,
                card.travelled);
            if (ribbonBack > 1f) {
                float tailX = worldX - dirX * ribbonBack * WORLD_SCALE;
                float tailZ = worldZ + dirY * ribbonBack * WORLD_SCALE;
                float tailY = card.hasWorldRay
                    ? card.worldHeightAt(card.travelled - ribbonBack, WORLD_SCALE)
                    : height;
                effectColor.set(card.trailRed * 0.58f, card.trailGreen * 0.58f,
                    card.trailBlue * 0.58f, 1f);
                terrainRenderer.drawInkRibbon(tailX, tailY, tailZ,
                    worldX, height, worldZ, 0.13f, effectColor);
            }

            // Type-coloured afterimages behind the card.
            for (int i = 1; i <= CARD_TRAIL_STEPS; i++) {
                float back = CARD_TRAIL_SPACING * i;
                if (card.travelled <= back) {
                    break;
                }
                float trailX = worldX - dirX * back * WORLD_SCALE;
                float trailZ = worldZ + dirY * back * WORLD_SCALE;
                float trailY = card.hasWorldRay
                    ? card.worldHeightAt(card.travelled - back, WORLD_SCALE)
                    : height;
                float fade = 0.72f - i * 0.17f;
                effectColor.set(card.trailRed * fade, card.trailGreen * fade,
                    card.trailBlue * fade, fade);
                drawCard(trailX, trailY, trailZ, dirX, dirY, card, i, effectColor);
            }
        }
    }

    /**
     * Procedural Hanafuda card plate. Its long axis follows the flight and its
     * face tumbles end-over-end around the width axis (Twisted Fate style).
     */
    private void drawCard(float x, float y, float z, float dirX, float dirY,
                          CardProjectileComponent card, int trailIndex, Color color) {
        float flightX = dirX;
        float flightZ = -dirY;
        float flightY = card.hasWorldRay ? card.rayDirY : 0f;
        float flightLength = (float) Math.sqrt(
            flightX * flightX + flightY * flightY + flightZ * flightZ);
        if (flightLength < 0.0001f) {
            return;
        }
        flightX /= flightLength;
        flightY /= flightLength;
        flightZ /= flightLength;

        // Width axis = flight x worldUp; face normal = width x flight.
        float sideX = -flightZ;
        float sideZ = flightX;
        float sideLength = (float) Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (sideLength < 0.0001f) {
            sideX = 1f;
            sideZ = 0f;
        } else {
            sideX /= sideLength;
            sideZ /= sideLength;
        }
        float normalX = -sideZ * flightY;
        float normalY = sideZ * flightX - sideX * flightZ;
        float normalZ = sideX * flightY;
        float normalLength = (float) Math.sqrt(
            normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength < 0.0001f) {
            normalX = 0f;
            normalY = 1f;
            normalZ = 0f;
        } else {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        }

        float spin = (card.travelled - trailIndex * CARD_TRAIL_SPACING)
            * CARD_SPIN_PER_PIXEL;
        float cos = MathUtils.cos(spin);
        float sin = MathUtils.sin(spin);
        float longX = flightX * cos + normalX * sin;
        float longY = flightY * cos + normalY * sin;
        float longZ = flightZ * cos + normalZ * sin;
        float faceX = normalX * cos - flightX * sin;
        float faceY = normalY * cos - flightY * sin;
        float faceZ = normalZ * cos - flightZ * sin;

        // Secondary drill roll around the card's flight axis.
        float roll = spin * 0.35f;
        float rollCos = MathUtils.cos(roll);
        float rollSin = MathUtils.sin(roll);
        float rolledSideX = sideX * rollCos + faceX * rollSin;
        float rolledSideY = faceY * rollSin;
        float rolledSideZ = sideZ * rollCos + faceZ * rollSin;

        float scale = Math.max(0.55f, 1f - trailIndex * 0.14f);
        cardCenter.set(x, y, z);
        cardSide.set(rolledSideX, rolledSideY, rolledSideZ);
        cardLong.set(longX, longY, longZ);
        float width = CARD_WIDTH * scale;
        float length = CARD_LENGTH * scale;
        // Procedural Hanafuda face: sumi border, inner archetype motif and a
        // small ink signature, drawn directly in 3D without an atlas.
        terrainRenderer.drawHanafudaCard(cardCenter, cardSide, cardLong,
            width, length, color);
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
            switch (burst.style) {
                case PETAL -> effectColor.set(KAMON_VERMILLION.r * glow,
                    KAMON_VERMILLION.g * glow, KAMON_VERMILLION.b * glow, 1f);
                case RIBBON -> effectColor.set(KAMON_AZURE.r * glow,
                    KAMON_AZURE.g * glow, KAMON_AZURE.b * glow, 1f);
                case BEAST -> effectColor.set(KAMON_GOLD.r * glow,
                    KAMON_GOLD.g * glow, KAMON_GOLD.b * glow, 1f);
                default -> effectColor.set(burst.red * glow, burst.green * glow,
                    burst.blue * glow, 1f);
            }

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
        terrainRenderer.drawKamonSeal(worldX, worldZ, radius * 0.85f,
            effectColor, burst.spin / (float) (Math.PI * 2.0));
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
        terrainRenderer.drawKamonSeal(worldX, worldZ, Math.max(0.2f, radius),
            effectColor, burst.spin / (float) (Math.PI * 2.0));
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
        terrainRenderer.drawKamonSeal(worldX, worldZ, Math.max(0.2f, radius),
            effectColor, burst.spin / (float) (Math.PI * 2.0));
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

    /** Karma rune ring: a procedural Kamon crest stamped on the ground. */
    private void renderRuneBurst(CardBurstComponent burst, float worldX, float worldZ,
                                 float radius) {
        // Kamon geometry is generated in polar coordinates by the shader; the
        // burst's spin becomes the seal's per-instance phase offset.
        terrainRenderer.drawKamonSeal(worldX, worldZ,
            Math.max(0.20f, radius * 0.9f), effectColor,
            burst.spin / (float) (Math.PI * 2.0));
    }

    // ------------------------------------------------------------------
    // Beast-Yaku lifesteal aura
    // ------------------------------------------------------------------

    /** Pulsing gold Beast Kamon aura drawn around the player while lifesteal is active. */
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
        effectColor.set(KAMON_GOLD.r * strength, KAMON_GOLD.g * strength,
            KAMON_GOLD.b * strength, 1f);

        terrainRenderer.drawKamonSeal(worldX, worldZ, 0.85f + pulse * 0.2f,
            effectColor, overworldTime * 0.3f);
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
        if (inventoryOverlay.isOpen()
            || (dialogueOverlay != null && dialogueOverlay.isOpen())) {
            promptLabel.setVisible(false);
            crosshairLabel.setVisible(false);
            hideNpcPrompt();
            return;
        }
        crosshairLabel.setVisible(true);

        Vector2 playerPos = player.body.getPosition();
        interactionBox.set(playerPos.x - 24f, playerPos.y - 12f, 48f, 48f);

        boolean ePressed = Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean nearAnyManualTrigger = false;

        // --- 0. FRIENDLY TEMPLE NPC: opens the merchant conversation. ---
        if (isNearVillageNpc()) {
            promptLabel.setText(" Press [E] : Talk to Shrine Maiden ");
            promptLabel.setVisible(true);
            if (ePressed && dialogueOverlay != null && !dialogueOverlay.isOpen()) {
                dialogueOverlay.open();
                dialogueOverlay.toFront();
                promptLabel.setVisible(false);
                hideNpcPrompt();
                Gdx.app.log("OverworldScreen",
                    "Opening shrine-maiden dialogue at the temple entrance.");
            }
            return;
        }

        // --- 1. WILDES YOKAI: Kontakt ueber die AKTUELLE Entity-Position. ---
        OverworldYokaiEntity contactedYokai = findContactedYokai();
        YokaiEncounter contactedEncounter = contactedYokai == null ? null
            : YokaiEncounterCatalog.findById(contactedYokai.getEncounterId());
        if (contactedYokai != null && contactedEncounter != null
            && !automaticCombatLocked) {
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

    /** True when {@code E} would open the tavern, shop or shrine under the player. */
    private boolean isNearManualTrigger() {
        Vector2 position = player.body.getPosition();
        interactionBox.set(position.x - 24f, position.y - 12f, 48f, 48f);
        return firstOverlapping(tavernTiles) != null
            || firstOverlapping(shopTiles) != null
            || firstOverlapping(shrineTiles) != null
            || isNearVillageNpc();
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
        yokaiRigRenderer.clear();
        if (dialogueOverlay != null) {
            dialogueOverlay.dispose();
            dialogueOverlay = null;
        }
        villageNpcPlaced = false;
        sceneryPlan = null;
        if (uiStage != null) {
            uiStage.dispose();
            uiStage = null;
        }
        // The HUD owns its 1x1 backing pixel; the stage only borrowed it.
        if (hudPixelTexture != null) {
            hudPixelTexture.dispose();
            hudPixelTexture = null;
        }
        hudPixelRegion = null;

        // 3. Pure data of this screen setup.
        hostileActors.clear();
        chasePaths.clear();
        hostileGroundHeights.clear();
        yokaiEntities.clear();
        collisionRects.clear();
        tavernTiles.clear();
        shopTiles.clear();
        shrineTiles.clear();
        healthBar = null;
        manaBar = null;
        dashBar = null;
        healthValueLabel = null;
        manaValueLabel = null;
        dashValueLabel = null;
        hudStatusLabel = null;
        cardStateLabel = null;
        cardSlots = null;
        crosshairLabel = null;
        promptLabel = null;
        npcTalkLabel = null;
        hudLabelStyle = null;

        // Skin, atlas, panel/button textures are borrowed (owner: GameAssets)
        // and must never be disposed here.
    }
}


