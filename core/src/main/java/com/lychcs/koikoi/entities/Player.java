package com.lychcs.koikoi.entities;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.lychcs.koikoi.graphics.AsciiFacing;

/**
 * Spielerfigur der Overworld.
 *
 * <p>Der Physik-Koerper gehoert zur Box2D-Welt des Screens und wird mit dieser
 * freigegeben. Die Darstellung ist reines ASCII: der {@code OverworldScreen}
 * zeichnet die Glyph-Matrix aus {@code assets/ascii/player.json}; diese Klasse
 * besitzt daher weder eine Textur noch eine andere GPU-Ressource.</p>
 */
public class Player {

    public Body body;

    private float stateTime = 0f;
    private AsciiFacing facing = AsciiFacing.DOWN;

    /** Wiederverwendete Rueckgabe von {@link #getRenderPosition()} (keine Allokation pro Frame). */
    private final Vector2 renderPosition = new Vector2();

    public final float SPEED = 120f;

    /**
     * Creates the player's Box2D foot collider.
     *
     * @param world  physics world of the screen
     * @param startX foot centre X in world pixels
     * @param startY foot centre Y in world pixels
     */
    public Player(World world, float startX, float startY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }

        // 1. Box2D Physik-Körper (Dynamischer Körper für den Spieler)
        BodyDef bdef = new BodyDef();
        bdef.type = BodyDef.BodyType.DynamicBody;
        bdef.position.set(startX, startY);
        body = world.createBody(bdef);
        body.setFixedRotation(true); // Verhindert, dass der Spieler umkippt

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(12f, 6f); // Kleine Box für die Füße (Kollisionsbox)

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.density = 1.0f;
        body.createFixture(fdef);
        shape.dispose();
    }


    public void update(float delta, Vector2 velocity) {
        // Richtung und Animationsphase nur aktualisieren, wenn sich der Spieler bewegt
        facing = AsciiFacing.fromVelocity(velocity.x, velocity.y, facing);
        if (velocity.len2() > 0) {
            stateTime += delta;
        } else {
            stateTime = 0f; // Auf den Start zurücksetzen, wenn er steht
        }
    }

    /** Last walked cardinal facing (retained while idle). */
    public AsciiFacing getFacing() {
        return facing;
    }

    /** Walk-cycle phase in seconds; used for the ASCII avatar's bobbing. */
    public float getWalkPhase() {
        return stateTime;
    }

    /** true while the Box2D body is moving (used to gate the bob). */
    public boolean isMoving() {
        return body.getLinearVelocity().len2() > 0.1f;
    }

    /**
     * Feet anchor of the ASCII avatar (Y-sorting, camera target and nameplate).
     *
     * <p>The avatar is drawn upward from this point, so the physics body stays
     * exactly on the ground while the glyph matrix is squashed or stretched.</p>
     *
     * <p>Die Rueckgabe ist eine wiederverwendete Instanz: sie bleibt nur bis zum
     * naechsten Aufruf gueltig und darf nicht zwischengespeichert werden.</p>
     */
    public Vector2 getRenderPosition() {
        return renderPosition.set(body.getPosition().x, body.getPosition().y);
    }
}
