package com.lychcs.koikoi.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public class Player {
    public Body body;
    public final Rectangle hitbox = new Rectangle(); // Für Trigger-Abfragen (Shops/Gegner)

    private final float WIDTH = 64f;
    private final float HEIGHT = 128f;
    public final float SPEED = 128f;

    private Texture playerTexture;
    private float stateTime = 0f;

    public Player(World world, float startX, float startY) {
        // 1. Box2D Körper definieren (DynamicBody, damit er sich bewegen kann)
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(startX + WIDTH / 2f, startY + 24f / 2f); // Zentrum des Körpers auf die Füße legen
        bodyDef.fixedRotation = true; // Verhindert, dass die Spielfigur umkippt

        body = world.createBody(bodyDef);

        // 2. Kollisions-Box (Fixture) für die Füße erstellen
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(WIDTH / 4f, 12f); // Eine feine Box an den Füßen

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = 0f; // Keine Reibung an Wänden, damit man nicht kleben bleibt

        body.createFixture(fixtureDef);
        shape.dispose();

        this.playerTexture = new Texture(Gdx.files.internal("entities/HERO_1.png"));
    }

    public void update(float delta) {
        stateTime += delta;
        // Synchronisiere die Logik-Hitbox mit der aktuellen Physik-Position des Körpers
        Vector2 pos = body.getPosition();
        hitbox.set(pos.x - WIDTH / 2f, pos.y - 12f, WIDTH, 24f);
    }

    public Vector2 getRenderPosition() {
        // Gibt die untere linke Ecke für das Zeichnen der Grafik zurück (basierend auf der Physik-Position)
        Vector2 pos = body.getPosition();
        return new Vector2(pos.x - WIDTH / 2f, pos.y - 12f);
    }

    public void render(Batch batch) {
        Vector2 renderPos = getRenderPosition();
        batch.draw(playerTexture, renderPos.x, renderPos.y, WIDTH, HEIGHT);
    }

    public void dispose() {
        if (playerTexture != null) playerTexture.dispose();
    }
}
