package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import com.badlogic.gdx.utils.ScreenUtils;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.entities.Player;
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

    // Box2D Physik-Welt
    private World world;
    private Box2DDebugRenderer debugRenderer; // Optional, zeigt die roten Physik-Boxen an falls gewünscht

    private static class RenderNode implements Comparable<RenderNode> {
        float y;
        Player player;
        TiledMapTileMapObject mapObject;

        public RenderNode(float y, Player player) { this.y = y; this.player = player; }
        public RenderNode(float y, TiledMapTileMapObject mapObject) { this.y = y; this.mapObject = mapObject; }

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
        MapLayer entityLayer = map.getLayers().get("entities");
        if (entityLayer != null) {
            MapObject spawnObj = entityLayer.getObjects().get("player_spawn");
            if (spawnObj != null) {
                startX = spawnObj.getProperties().get("x", Float.class);
                startY = spawnObj.getProperties().get("y", Float.class);
            }
        }

        // Spieler direkt in die Box2D-Welt spawnen
        player = new Player(world, startX, startY);
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
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);

        // Physik-Welt vorwärtsrechnen (Step)
        world.step(delta, 6, 2);

        handleMovement(delta);
        handleInteractions();

        // Spieler-Logik (Hitbox synchronisieren) aktualisieren
        player.update(delta);

        // Kamera folgt dem Spieler (basiert auf der echten Physik-Position)
        Vector2 renderPos = player.getRenderPosition();
        float targetX = renderPos.x + 32f;
        float targetY = renderPos.y + 64f;

        float camHalfWidth = camera.viewportWidth * 0.5f * camera.zoom;
        float camHalfHeight = camera.viewportHeight * 0.5f * camera.zoom;

        float clampedX = com.badlogic.gdx.math.MathUtils.clamp(targetX, camHalfWidth, mapPixelWidth - camHalfWidth);
        float clampedY = com.badlogic.gdx.math.MathUtils.clamp(targetY, camHalfHeight, mapPixelHeight - camHalfHeight);

        camera.position.set(clampedX, clampedY, 0);
        camera.update();

        mapRenderer.setView(camera);
        mapRenderer.getBatch().begin();

        // 1. Kachelebenen zeichnen
        for (MapLayer layer : map.getLayers()) {
            if (layer instanceof TiledMapTileLayer) {
                mapRenderer.renderTileLayer((TiledMapTileLayer) layer);
            }
        }

        // 2. Y-Sorting Liste füllen (Nutzt jetzt die reibungsfreie Physik-Position des Spielers!)
        List<RenderNode> renderList = new ArrayList<>();
        renderList.add(new RenderNode(renderPos.y, player));

        for (MapLayer layer : map.getLayers()) {
            if (!(layer instanceof TiledMapTileLayer)) {
                for (MapObject obj : layer.getObjects()) {
                    if (obj instanceof TiledMapTileMapObject) {
                        TiledMapTileMapObject tObj = (TiledMapTileMapObject) obj;
                        renderList.add(new RenderNode(tObj.getY(), tObj));
                    }
                }
            }
        }

        Collections.sort(renderList);

        for (RenderNode node : renderList) {
            if (node.player != null) {
                node.player.render(mapRenderer.getBatch());
            } else {
                TiledMapTileMapObject tObj = node.mapObject;
                mapRenderer.getBatch().draw(
                    tObj.getTile().getTextureRegion(),
                    tObj.getX(),
                    tObj.getY()
                );
            }
        }

        mapRenderer.getBatch().end();

        // Optional zum Testen: debugRenderer.render(world, camera.combined); (Zeigt rote Kollisionsboxen)
    }

    private void handleMovement(float delta) {
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
        MapLayer triggersLayer = map.getLayers().get("triggers");
        if (triggersLayer == null) return;

        // Hol dir die exakte Physik-Position der Spielerfüße
        Vector2 playerPos = player.body.getPosition();

        // Erstelle eine Interaktions-Box um den Spieler herum (z.B. 48x48 Pixel)
        // Wir ziehen etwas ab, damit die Box zentriert ist
        Rectangle interactionBox = new Rectangle(playerPos.x - 24f, playerPos.y - 12f, 48f, 48f);

        boolean ePressed = Gdx.input.isKeyJustPressed(Input.Keys.E);

        for (MapObject object : triggersLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                if (interactionBox.overlaps(rect)) {
                    String type = object.getProperties().get("type", String.class);
                    if (type == null) type = object.getProperties().get("class", String.class);
                    if (type == null) continue;

                    // --- 1. AUTOMATISCHE TRIGGER ---
                    if ("enemy1".equals(type) || "combat_zone".equals(type)) {
                        System.out.println("Gegner berührt! Lade Kampf...");
                        ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
                        return;
                    }

                    // --- 2. MANUELLE TRIGGER (Mit E) ---
                    if (ePressed) {
                        System.out.println("E gedrückt bei Trigger: " + type); // Zum Debuggen in der Konsole
                        if ("shop".equals(type)) {
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShopScreen(runSession));
                            return;
                        } else if ("shrine".equals(type)) {
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShrineScreen(runSession));
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        map.dispose();
        mapRenderer.dispose();
        world.dispose(); // Physik-Welt sauber freigeben
        debugRenderer.dispose();
        player.dispose();
    }
}
