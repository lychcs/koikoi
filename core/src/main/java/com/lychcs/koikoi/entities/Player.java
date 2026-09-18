package com.lychcs.koikoi.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/**
 * Spielerfigur der Overworld.
 *
 * <p>Der Physik-Koerper gehoert zur Box2D-Welt des Screens und wird mit dieser
 * freigegeben. Das Sprite-Sheet wird <b>nicht</b> von dieser Klasse besessen:
 * es wird von {@link com.lychcs.koikoi.graphics.GameAssets} verwaltet und hier
 * nur als geliehene Textur ausgewertet. Diese Klasse besitzt daher keine
 * GPU-Ressource, die sie freigeben muesste.</p>
 */
public class Player {

    public Body body;

    // Animationen und Frame-Arrays für die 4 Richtungen
    private Animation<TextureRegion> walkDown, walkUp, walkLeft, walkRight;
    private TextureRegion[] downFrames, upFrames, leftFrames, rightFrames;

    private float stateTime = 0f;
    private String currentDirection = "down";

    /** Wiederverwendete Rueckgabe von {@link #getRenderPosition()} (keine Allokation pro Frame). */
    private final Vector2 renderPosition = new Vector2();

    public final float SPEED = 120f;

    /**
     * @param sheet vom Aufrufer geliehenes Sprite-Sheet (Eigentum: GameAssets)
     */
    public Player(World world, float startX, float startY, Texture sheet) {
        if (sheet == null) {
            throw new IllegalArgumentException("sheet must not be null");
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

        // 2. Das horizontale Sprite-Sheet aufteilen (16 Frames gesamt: je 4 pro Richtung)
        int totalFrames = 16;
        int frameWidth = sheet.getWidth() / totalFrames;
        int frameHeight = sheet.getHeight();

        TextureRegion[][] tmp = TextureRegion.split(sheet, frameWidth, frameHeight);
        TextureRegion[] allFrames = tmp[0];

        // Frames blockweise den Richtungen zuordnen
        downFrames  = new TextureRegion[]{ allFrames[0], allFrames[1], allFrames[2], allFrames[3] };
        upFrames    = new TextureRegion[]{ allFrames[4], allFrames[5], allFrames[6], allFrames[7] };
        leftFrames  = new TextureRegion[]{ allFrames[8], allFrames[9], allFrames[10], allFrames[11] };
        rightFrames = new TextureRegion[]{ allFrames[12], allFrames[13], allFrames[14], allFrames[15] };

        walkDown = new Animation<>(0.15f, downFrames);
        walkUp = new Animation<>(0.15f, upFrames);
        walkLeft = new Animation<>(0.15f, leftFrames);
        walkRight = new Animation<>(0.15f, rightFrames);

        walkDown.setPlayMode(Animation.PlayMode.LOOP);
        walkUp.setPlayMode(Animation.PlayMode.LOOP);
        walkLeft.setPlayMode(Animation.PlayMode.LOOP);
        walkRight.setPlayMode(Animation.PlayMode.LOOP);
    }

    public void update(float delta, Vector2 velocity) {
        // Richtung und Animationen nur aktualisieren, wenn sich der Spieler bewegt
        if (velocity.len2() > 0) {
            stateTime += delta;
            if (Math.abs(velocity.x) > Math.abs(velocity.y)) {
                currentDirection = velocity.x > 0 ? "right" : "left";
            } else {
                currentDirection = velocity.y > 0 ? "up" : "down";
            }
        } else {
            stateTime = 0f; // Auf den Start zurücksetzen, wenn er steht
        }
    }

    public void render(SpriteBatch batch) {
        TextureRegion currentFrame;
        boolean isMoving = body.getLinearVelocity().len2() > 0.1f;

        switch (currentDirection) {
            case "up":
                // 1. Frame (Index 0) als Stand-Pose für Hoch
                currentFrame = isMoving ? walkUp.getKeyFrame(stateTime) : upFrames[0];
                break;
            case "left":
                // 3. Frame (Index 2) als Stand-Pose für Links
                currentFrame = isMoving ? walkLeft.getKeyFrame(stateTime) : leftFrames[2];
                break;
            case "right":
                // 3. Frame (Index 2) als Stand-Pose für Rechts
                currentFrame = isMoving ? walkRight.getKeyFrame(stateTime) : rightFrames[2];
                break;
            default:
                // 1. Frame (Index 0) als Stand-Pose für Runter (Default)
                currentFrame = isMoving ? walkDown.getKeyFrame(stateTime) : downFrames[0];
                break;
        }

        // Zeichnet das Sprite zentriert über dem Physik-Punkt (Fuß-Offset)
        batch.draw(currentFrame, body.getPosition().x - 32f, body.getPosition().y - 12f);
    }

    /**
     * Position, an der das Sprite gezeichnet wird (Y-Sorting im OverworldScreen).
     *
     * <p>Die Rueckgabe ist eine wiederverwendete Instanz: sie bleibt nur bis zum
     * naechsten Aufruf gueltig und darf nicht zwischengespeichert werden.</p>
     */
    public Vector2 getRenderPosition() {
        // Exakter Offset passend zum batch.draw() für das Y-Sorting im OverworldScreen
        return renderPosition.set(body.getPosition().x - 32f, body.getPosition().y - 12f);
    }
}
