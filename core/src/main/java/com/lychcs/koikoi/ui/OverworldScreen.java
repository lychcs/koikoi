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
import com.lychcs.koikoi.entities.Player;
import com.lychcs.koikoi.graphics.CorruptionEngine;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.run.RunSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OverworldScreen extends ScreenAdapter {

    private final RunSession runSession;
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

    // Box2D Physik-Welt

    private World world;
    private Box2DDebugRenderer debugRenderer; // Optional, zeigt die roten Physik-Boxen an falls gewünscht

    private static class RenderNode implements Comparable<RenderNode> {
        float y;
        com.lychcs.koikoi.entities.Player playerEntity; // Eindeutiger Name
        TiledMapTileMapObject mapObject;

        public RenderNode(float y, com.lychcs.koikoi.entities.Player playerEntity) {
            this.y = y;
            this.playerEntity = playerEntity;
        }
        public RenderNode(float y, TiledMapTileMapObject mapObject) {
            this.y = y;
            this.mapObject = mapObject;
        }

        @Override
        public int compareTo(RenderNode other) {
            return Float.compare(other.y, this.y);
        }
    }

    public OverworldScreen(RunSession runSession) {
        this.runSession = runSession;

        // 1. Box2D Welt ohne Schwerkraft (Top-Down RPG) erstellen
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();

        map = new TmxMapLoader().load("map/testmap.tmx");

        int mapWidthTiles = map.getProperties().get("width", Integer.class);
        int mapHeightTiles = map.getProperties().get("height", Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", Integer.class);
        int tileHeight = map.getProperties().get("tileheight", Integer.class);

        mapPixelWidth = mapWidthTiles * tileWidth;
        mapPixelHeight = mapHeightTiles * tileHeight;

        // 2. Vollautomatische Erstellung der Physik-Wände aus der "collision"-Ebene von Tiled
        createCollisionBoxes();

        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f);

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 1280, 720);
        camera.zoom = 0.5f;

        float startX = 100f;
        float startY = 100f;

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

        player = new Player(world, startX, startY);

        // UI Stage für Overworld-Popups (z.B. "Press E to Shop")
        uiStage = new Stage(new FitViewport(1280, 720));
        skin = new Skin(Gdx.files.internal("uiskin.json"));
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
            Gdx.app.error("Shader", "Tree Wind Shader Fehler:\n" + windShader.getLog());
        }

        gameAtlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        btnStyle.down = ((NinePatchDrawable) btnStyle.up).tint(Color.LIGHT_GRAY);
        btnStyle.font = FontManager.getFont();
        btnStyle.fontColor = FontManager.COLOR_TEXT_MAIN;

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        NinePatchDrawable panelBg = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));

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
                "edge_detection Shader konnte nicht kompiliert werden:\n"
                    + edgeShader.getLog()
            );
        }

        corruptionEngine = new CorruptionEngine();
    }

    private void createCollisionBoxes() {
        MapLayer collisionLayer = map.getLayers().get("collision");
        if (collisionLayer == null) return;

        for (MapObject object : collisionLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

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

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);

        // 1. FBO an neue Bildschirmauflösung anpassen
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, width, height, false);
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

        world.step(delta, 6, 2);

        handleMovement(delta);
        handleInteractions();

        player.update(
            delta,
            player.body.getLinearVelocity()
        );

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

        // Y-Sorting-Liste aufbauen
        List<RenderNode> renderList = new ArrayList<>();

        renderList.add(
            new RenderNode(
                renderPos.y,
                player
            )
        );

        for (MapLayer layer : map.getLayers()) {
            if (!(layer instanceof TiledMapTileLayer)) {
                for (MapObject obj : layer.getObjects()) {
                    if (obj instanceof TiledMapTileMapObject) {
                        TiledMapTileMapObject tiledObject =
                            (TiledMapTileMapObject) obj;

                        renderList.add(
                            new RenderNode(
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

    private void handleInteractions() {
        if (inventoryOverlay.isOpen()) {
            promptLabel.setVisible(false);
            return;
        }

        MapLayer triggersLayer = map.getLayers().get("triggers");
        if (triggersLayer == null) {
            promptLabel.setVisible(false);
            return;
        }

        Vector2 playerPos = player.body.getPosition();
        Rectangle interactionBox = new Rectangle(playerPos.x - 24f, playerPos.y - 12f, 48f, 48f);

        boolean ePressed = Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean nearAnyTrigger = false;

        for (MapObject object : triggersLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                if (interactionBox.overlaps(rect)) {
                    String type = object.getProperties().get("type", String.class);
                    if (type == null) type = object.getProperties().get("class", String.class);
                    if (type == null) continue;

                    // --- 1. AUTOMATISCHE TRIGGER (Gegner / Kampf) ---
                    if ("enemy1".equals(type) || "combat_zone".equals(type)) {
                        Vector2 currentPos = player.body.getPosition();

                        // Position speichern (wie beim Shop mit Sprite-Offset -32f / -12f)
                        // Optional: Einen kleinen Schritt zurücksetzen (z. B. y - 10f),
                        // damit man nach dem Kampf nicht sofort wieder mitten im Trigger steht!
                        runSession.setLastPlayerPosition(currentPos.x, currentPos.y);

                        ((KoiKoiGame) Gdx.app.getApplicationListener()).changeScreen(new GameScreen(runSession));
                        return;
                    }

                    // --- 2. MANUELLE TRIGGER (Shop / Schrein mit Prompt) ---
                    if ("shop".equals(type)) {
                        promptLabel.setText(" Press [E] : Open Shop ");
                        promptLabel.setVisible(true);
                        nearAnyTrigger = true;

                        if (ePressed) {
                            Vector2 currentPos = player.body.getPosition();
                            runSession.setLastPlayerPosition(currentPos.x, currentPos.y);
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).changeScreen(new ShopScreen(runSession));
                            return;
                        }
                    } else if ("shrine".equals(type)) {
                        promptLabel.setText(" Press [E] : Visit Shrine ");
                        promptLabel.setVisible(true);
                        nearAnyTrigger = true;

                        if (ePressed) {
                            Vector2 currentPos = player.body.getPosition();
                            runSession.setLastPlayerPosition(currentPos.x, currentPos.y);
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).changeScreen(new ShrineScreen(runSession));
                            return;
                        }
                    }
                }
            }
        }

        // Wenn der Spieler weggeht, wird das Schild sofort ausgeblendet
        if (!nearAnyTrigger) {
            promptLabel.setVisible(false);
        }
    }

    @Override
    public void dispose() {
        if (gameAtlas != null) gameAtlas.dispose();
        map.dispose();
        // Post-Processing
        if (fbo != null) fbo.dispose();
        if (screenBatch != null) screenBatch.dispose();
        if (edgeShader != null) edgeShader.dispose();

        if (corruptionEngine != null) corruptionEngine.dispose(); // <--- DAS HIER FEHLTE NOCH

        mapRenderer.dispose();
        world.dispose(); // Physik-Welt sauber freigeben
        debugRenderer.dispose();
        player.dispose();
        if (uiStage != null) uiStage.dispose();
        if (skin != null) skin.dispose();
        if (windShader != null) windShader.dispose();
    }
}
