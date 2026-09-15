package com.lychcs.koikoi.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch; // WICHTIG: Batch statt SpriteBatch
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Player {
    public final Vector2 position = new Vector2();
    public final Rectangle hitbox = new Rectangle();

    private final float WIDTH = 64f;
    private final float HEIGHT = 128f;
    public final float SPEED = 128;

    private Texture placeholderTexture;
    private float stateTime = 0f; // Vorbereitung für Animationen

    public Player(float startX, float startY) {
        this.position.set(startX, startY);
        // Hitbox am Boden der Figur (z.B. die unteren 24 Pixel für die Füße)
        this.hitbox.set(startX, startY, WIDTH, 24f);

        this.placeholderTexture = new Texture(Gdx.files.internal("entities/HERO_1.png"));
    }

    public void update(float delta) {
        // Hier kommt später die Animations-Logik rein (z.B. Frame-Wechsel beim Laufen)
        stateTime += delta;

        // Hinweis: Die Bewegungs-Logik (Input) bleibt im OverworldScreen,
        // da wir dort den Zugriff auf die Map für die Kollisionsprüfung brauchen.
    }

    // Geändert auf "Batch", um mit mapRenderer.getBatch() kompatibel zu sein
    public void render(Batch batch) {
        batch.draw(placeholderTexture, position.x, position.y, WIDTH, HEIGHT);
    }

    public void dispose() {
        if (placeholderTexture != null) placeholderTexture.dispose();
    }
}
