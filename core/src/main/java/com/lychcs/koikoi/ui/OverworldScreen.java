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
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;
    private Player player;

    // Hilfsklasse für das Y-Sorting
    private static class RenderNode implements Comparable<RenderNode> {
        float y;
        Player player;
        TiledMapTileMapObject mapObject;

        public RenderNode(float y, Player player) { this.y = y; this.player = player; }
        public RenderNode(float y, TiledMapTileMapObject mapObject) { this.y = y; this.mapObject = mapObject; }

        @Override
        public int compareTo(RenderNode other) {
            // Höchster Y-Wert (weiter oben/hinten auf der Map) wird zuerst gezeichnet
            return Float.compare(other.y, this.y);
        }
    }

    public OverworldScreen(RunSession runSession) {
        this.runSession = runSession;

        map = new TmxMapLoader().load("map/testmap.tmx");
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f);

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 1280, 720);
        // ZOOM: Macht die Welt optisch größer und gemütlicher!
        camera.zoom = 0.5f;

        // Spawnpunkt auslesen
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
        player = new Player(startX, startY);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);

        handleMovement(delta);
        handleInteractions();

        // Kamera folgt dem Spieler sanft
        camera.position.set(player.position.x + 24f, player.position.y + 32f, 0);
        camera.update();

        mapRenderer.setView(camera);
        mapRenderer.getBatch().begin();

        // 1. Zuerst alle Kachelebenen (Gras, Wege) zeichnen
        for (MapLayer layer : map.getLayers()) {
            if (layer instanceof TiledMapTileLayer) {
                mapRenderer.renderTileLayer((TiledMapTileLayer) layer);
            }
        }

        // 2. Sammle Objekte & Spieler für Y-Sorting
        List<RenderNode> renderList = new ArrayList<>();

        // Füge den Spieler hinzu (Y-Wert seiner Füße)
        renderList.add(new RenderNode(player.position.y, player));

        // Dynamisch: Gehe durch ALLE Ebenen der Map
        for (MapLayer layer : map.getLayers()) {
            // Wir ignorieren die Gras-Ebenen (Kachelebenen wurden in Schritt 1 schon gezeichnet)
            if (!(layer instanceof TiledMapTileLayer)) {
                for (MapObject obj : layer.getObjects()) {
                    // Wenn das Objekt eine Grafik aus einem Kachelsatz ist (Baum, Wagen etc.)
                    if (obj instanceof TiledMapTileMapObject) {
                        TiledMapTileMapObject tObj = (TiledMapTileMapObject) obj;
                        // Y-Koordinate auslesen und in die Sortierliste werfen
                        renderList.add(new RenderNode(tObj.getY(), tObj));
                    }
                }
            }
        }

        // 3. Liste nach Y-Höhe sortieren (Objekte weiter oben im Bild werden zuerst gezeichnet)
        Collections.sort(renderList);

        // 4. In der sortierten Reihenfolge zeichnen
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
    }

    private void handleMovement(float delta) {
        float moveX = 0;
        float moveY = 0;

        float speed = player.SPEED * delta;
        player.update(delta);

        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveY += speed;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveY -= speed;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveX -= speed;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveX += speed;

        if (moveX != 0) {
            player.hitbox.x += moveX;
            if (isColliding(player.hitbox)) {
                player.hitbox.x -= moveX;
            } else {
                player.position.x += moveX;
            }
        }

        if (moveY != 0) {
            player.hitbox.y += moveY;
            if (isColliding(player.hitbox)) {
                player.hitbox.y -= moveY;
            } else {
                player.position.y += moveY;
            }
        }
    }

    private boolean isColliding(Rectangle playerHitbox) {
        MapLayer collisionLayer = map.getLayers().get("collision");
        if (collisionLayer == null) return false;

        for (MapObject object : collisionLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                String type = object.getProperties().get("type", String.class);
                if (type == null) type = object.getProperties().get("class", String.class);

                if ("solid".equals(type) && playerHitbox.overlaps(rect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleInteractions() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            MapLayer triggersLayer = map.getLayers().get("triggers");
            if (triggersLayer == null) return;

            Rectangle interactionBox = new Rectangle(player.position.x, player.position.y, 48f, 64f);

            for (MapObject object : triggersLayer.getObjects()) {
                if (object instanceof RectangleMapObject) {
                    Rectangle rect = ((RectangleMapObject) object).getRectangle();

                    if (interactionBox.overlaps(rect)) {
                        String type = object.getProperties().get("type", String.class);
                        if (type == null) type = object.getProperties().get("class", String.class);

                        if ("shop".equals(type)) {
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShopScreen(runSession));
                            return;
                        } else if ("shrine".equals(type)) {
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShrineScreen(runSession));
                            return;
                        } else if ("combat_zone".equals(type)) {
                            ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
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
        player.dispose();
    }
}
