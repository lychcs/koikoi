package com.lychcs.koikoi.entities;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.graphics.AsepriteSheet;

/**
 * Wildes Yokai in der Overworld: laeuft animiert innerhalb einer 3x3-Kachel-Flaeche
 * um seinen Spawnpunkt, bleibt gelegentlich stehen und verlaesst den Bereich nie.
 *
 * <p><b>Besitz:</b> diese Klasse besitzt <b>keine</b> GPU-Ressource. Die vier
 * Laufanimationen und ihre Frames sind geliehen (Eigentum: {@link AsepriteSheet},
 * das wiederum auf der von {@code GameAssets} verwalteten Textur aufsetzt) und
 * werden hier niemals disposed.</p>
 *
 * <p><b>Kollision:</b> es wird keine zweite Kollisionskarte erzeugt. Der Aufrufer
 * uebergibt eine {@link CollisionQuery}, die dieselben Tiles-Kollisionsrechtecke
 * und Mapgrenzen prueft, die die Overworld bereits verwendet.</p>
 *
 * <p><b>Allokationen:</b> alle Hilfsobjekte (Position, Ziel, Boxen) sind
 * wiederverwendete Felder; {@link #update} und {@link #render} allozieren nichts.</p>
 *
 * <p><b>Anschlussstelle der naechsten Phase (Corruption):</b> die aktuelle
 * Weltposition steht jederzeit ueber {@link #getWorldX()} und {@link #getWorldY()}
 * bereit ({@link #getFootY()} ist die Fuss-/Sortierposition). Eine spaetere Phase
 * kann diesen Wert in {@code CorruptionEngine.injectDisturbance(...)} uebergeben;
 * in dieser Phase wird bewusst nichts injiziert.</p>
 */
public final class OverworldYokaiEntity {

    /** Roaming-Radius in Kacheln: 1 links/rechts/oben/unten um die Spawnkachel (3x3). */
    public static final int ROAM_HALF_EXTENT_TILES = 1;

    /** Ruhige Pixel-RPG-Laufgeschwindigkeit in Pixeln pro Sekunde. */
    public static final float WALK_SPEED = 42f;

    /** Pausendauer nach Erreichen eines Ziels. */
    public static final float PAUSE_MIN_SECONDS = 0.4f;
    public static final float PAUSE_MAX_SECONDS = 1.4f;

    /** Pause, die nach einer Blockade eingelegt wird. */
    public static final float BLOCKED_PAUSE_SECONDS = 0.5f;

    /** Ankunftstoleranz in Pixeln. */
    public static final float ARRIVE_EPSILON = 1.5f;

    /** Obergrenze fuer delta, damit ein Frameeinbruch die Entity nicht springen laesst. */
    public static final float MAX_STEP_DELTA = 0.1f;

    /** Maximale Zielversuche pro Zustandswechsel (keine Endlosschleife). */
    public static final int MAX_TARGET_ATTEMPTS = 8;

    /** Fuss-Kollisionsbox (kleiner als die Interaktionsflaeche). */
    public static final float COLLISION_WIDTH = 34f;
    public static final float COLLISION_HEIGHT = 20f;

    /** Interaktions-/Encounter-Flaeche am Fuss. */
    public static final float INTERACTION_WIDTH = 56f;
    public static final float INTERACTION_HEIGHT = 40f;

    /** Offset zwischen Fuss-Position und Sprite-Unterkante (wie beim Spieler-Sprite). */
    public static final float FOOT_OFFSET_Y = 12f;

    /** Bewegungsrichtung der Entity (unabhaengig von der Animationsbenennung). */
    public enum Direction { DOWN, UP, LEFT, RIGHT }

    /** Kollisionsabfrage der Overworld (reines Praedikat, keine zweite Kollisionskarte). */
    public interface CollisionQuery {
        /** true, wenn die uebergebene Weltbox blockiert ist (Mapkollision oder Mapgrenze). */
        boolean isBlocked(Rectangle worldBox);
    }

    // ------------------------------------------------------------------
    // Reine Bereichs-/Richtungsmathematik (zustandslos und damit pruefbar)
    // ------------------------------------------------------------------

    /** Halbe Breite des Roamingbereichs in Pixeln aus der Tilebreite der Map. */
    public static float roamHalfExtentX(float tileWidth) {
        return (ROAM_HALF_EXTENT_TILES + 0.5f) * tileWidth;
    }

    /** Halbe Hoehe des Roamingbereichs in Pixeln aus der Tilehoehe der Map. */
    public static float roamHalfExtentY(float tileHeight) {
        return (ROAM_HALF_EXTENT_TILES + 0.5f) * tileHeight;
    }

    /** X-Mitte der Spawnkachel (Zentrum des Roamingbereichs). */
    public static float roamCenterX(float spawnX, float tileWidth) {
        return MathUtils.floor(spawnX / tileWidth) * tileWidth + (tileWidth * 0.5f);
    }

    /** Y-Mitte der Spawnkachel (Zentrum des Roamingbereichs). */
    public static float roamCenterY(float spawnY, float tileHeight) {
        return MathUtils.floor(spawnY / tileHeight) * tileHeight + (tileHeight * 0.5f);
    }

    /** Begrenzt den Simulationsschritt, damit ein Frameeinbruch nichts springen laesst. */
    public static float clampStepDelta(float delta) {
        return MathUtils.clamp(delta, 0f, MAX_STEP_DELTA);
    }

    /** Richtung aus der tatsaechlichen Bewegung; Betrag X schlaegt Betrag Y. */
    public static Direction directionFor(float moveX, float moveY) {
        if (Math.abs(moveX) > Math.abs(moveY)) {
            return moveX < 0f ? Direction.LEFT : Direction.RIGHT;
        }
        return moveY < 0f ? Direction.DOWN : Direction.UP;
    }

    /** Schreibt die Fuss-Kollisionsbox einer Position in die uebergebene Box. */
    public static Rectangle collisionBoxAt(float posX, float posY, Rectangle out) {
        return out.set(
            posX - (COLLISION_WIDTH * 0.5f),
            posY - (COLLISION_HEIGHT * 0.5f),
            COLLISION_WIDTH,
            COLLISION_HEIGHT);
    }

    /** Schreibt die Interaktions-/Encounter-Flaeche einer Position in die uebergebene Box. */
    public static Rectangle interactionBoundsAt(float posX, float posY, Rectangle out) {
        return out.set(
            posX - (INTERACTION_WIDTH * 0.5f),
            posY - (INTERACTION_HEIGHT * 0.5f),
            INTERACTION_WIDTH,
            INTERACTION_HEIGHT);
    }

    private final String encounterId;
    private final String speciesId;
    private final String displayName;

    private final WalkAnimations animations;

    private final float spawnX;
    private final float spawnY;
    private final float roamMinX;
    private final float roamMaxX;
    private final float roamMinY;
    private final float roamMaxY;

    /** Fuss-Position in Weltkoordinaten (auch Sortierwert). */
    private float x;
    private float y;

    private float targetX;
    private float targetY;

    private Direction direction = Direction.DOWN;
    private Direction lastWalkDirection = Direction.DOWN;

    private float stateTime;
    private float pauseRemaining;
    private boolean paused;

    private final Rectangle collisionBox = new Rectangle();
    private final Rectangle interactionBounds = new Rectangle();
    private final Vector2 worldPosition = new Vector2();

    /**
     * Geliehene Laufanimationen der wilden Form (Eigentum: {@link AsepriteSheet}).
     *
     * <p>Enthaelt genau die vier Richtungsanimationen und die Frame-Groesse der wilden
     * Form; die Entity besitzt damit keine Textur und keine eigene GPU-Ressource.
     * {@link #from(AsepriteSheet)} ist der Produktionsweg (fehlende Tags werden dort mit
     * Dateipfad gemeldet), {@link #of} erlaubt dieselbe Logik ohne GL-Kontext.</p>
     */
    public static final class WalkAnimations {

        private final Animation<TextureRegion> down;
        private final Animation<TextureRegion> up;
        private final Animation<TextureRegion> left;
        private final Animation<TextureRegion> right;
        private final int frameWidth;
        private final int frameHeight;

        private WalkAnimations(
            Animation<TextureRegion> down,
            Animation<TextureRegion> up,
            Animation<TextureRegion> left,
            Animation<TextureRegion> right,
            int frameWidth,
            int frameHeight
        ) {
            this.down = down;
            this.up = up;
            this.left = left;
            this.right = right;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        /** Baut die Animationssicht aus einem geladenen Aseprite-Sheet. */
        public static WalkAnimations from(AsepriteSheet sheet) {
            if (sheet == null) {
                throw new IllegalArgumentException("sheet must not be null");
            }
            return of(
                sheet.getAnimation("walk_down"),
                sheet.getAnimation("walk_up"),
                sheet.getAnimation("walk_left"),
                sheet.getAnimation("walk_right"),
                sheet.getFrameWidth(),
                sheet.getFrameHeight());
        }

        /** Baut die Animationssicht aus vier bereits vorhandenen Animationen. */
        public static WalkAnimations of(
            Animation<TextureRegion> down,
            Animation<TextureRegion> up,
            Animation<TextureRegion> left,
            Animation<TextureRegion> right,
            int frameWidth,
            int frameHeight
        ) {
            if (down == null || up == null || left == null || right == null) {
                throw new IllegalArgumentException("all four walk animations are required");
            }
            if (frameWidth <= 0 || frameHeight <= 0) {
                throw new IllegalArgumentException("invalid frame size: " + frameWidth + "x" + frameHeight);
            }
            return new WalkAnimations(down, up, left, right, frameWidth, frameHeight);
        }

        public Animation<TextureRegion> animation(Direction dir) {
            switch (dir) {
                case UP: return up;
                case LEFT: return left;
                case RIGHT: return right;
                case DOWN:
                default: return down;
            }
        }

        /**
         * Erster Frame einer Richtung. Waehrend einer Pause bleibt dieser Frame stehen,
         * weil es (noch) keine Idle-Animation gibt.
         */
        public TextureRegion firstFrame(Direction dir) {
            return animation(dir).getKeyFrame(0f);
        }

        /** Animationszustand eines Frames: Pause zeigt den ersten Frame, sonst die Animation. */
        public TextureRegion frameFor(boolean paused, Direction direction, Direction lastWalkDirection,
                                      float stateTime) {
            return paused
                ? firstFrame(lastWalkDirection)
                : animation(direction).getKeyFrame(stateTime);
        }

        public int getFrameWidth() {
            return frameWidth;
        }

        public int getFrameHeight() {
            return frameHeight;
        }
    }

    /**
     * @param encounterId  stabile Encounter-Id (Inhalts-Id, keine Bildschirmkoordinate)
     * @param speciesId    stabile Spezies-Id
     * @param displayName  Anzeigename der wilden Form
     * @param animations   geliehene Laufanimationen der wilden Form
     * @param spawnX       Spawnposition X in Weltkoordinaten
     * @param spawnY       Spawnposition Y in Weltkoordinaten
     * @param tileWidth    Tilebreite aus der geladenen TiledMap
     * @param tileHeight   Tilehoehe aus der geladenen TiledMap
     */
    public OverworldYokaiEntity(
        String encounterId,
        String speciesId,
        String displayName,
        WalkAnimations animations,
        float spawnX,
        float spawnY,
        float tileWidth,
        float tileHeight
    ) {
        if (animations == null) {
            throw new IllegalArgumentException("animations must not be null");
        }
        if (!(tileWidth > 0f) || !(tileHeight > 0f)) {
            throw new IllegalArgumentException(
                "tileWidth and tileHeight must be > 0 (got " + tileWidth + "x" + tileHeight + ")");
        }

        this.encounterId = encounterId;
        this.speciesId = speciesId;
        this.displayName = displayName;
        this.animations = animations;

        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.x = spawnX;
        this.y = spawnY;

        // 3x3-Kachel-Flaeche um die Spawnkachel: die Kachelgroesse kommt ausschliesslich
        // aus der geladenen Map, es wird keine Pixelgroesse angenommen.
        float centerTileX = roamCenterX(spawnX, tileWidth);
        float centerTileY = roamCenterY(spawnY, tileHeight);
        float halfExtentX = roamHalfExtentX(tileWidth);
        float halfExtentY = roamHalfExtentY(tileHeight);

        this.roamMinX = centerTileX - halfExtentX;
        this.roamMaxX = centerTileX + halfExtentX;
        this.roamMinY = centerTileY - halfExtentY;
        this.roamMaxY = centerTileY + halfExtentY;

        this.targetX = MathUtils.clamp(spawnX, roamMinX, roamMaxX);
        this.targetY = MathUtils.clamp(spawnY, roamMinY, roamMaxY);

        // Startet mit einer kurzen Pause, damit die Entity nicht sofort losrennt.
        this.paused = true;
        this.pauseRemaining = MathUtils.random(PAUSE_MIN_SECONDS, PAUSE_MAX_SECONDS);
    }

    /**
     * Ein Simulationsschritt. {@code delta} wird begrenzt, damit ein Frameeinbruch die
     * Entity nicht aus ihrem Bereich springen laesst.
     */
    public void update(float delta, CollisionQuery collisionQuery) {
        float step = clampStepDelta(delta);
        if (step <= 0f) {
            return;
        }

        stateTime += step;

        if (paused) {
            pauseRemaining -= step;
            if (pauseRemaining <= 0f) {
                paused = false;
                chooseNewTarget(collisionQuery);
            }
            return;
        }

        moveTowardsTarget(step, collisionQuery);
    }

    private void moveTowardsTarget(float step, CollisionQuery collisionQuery) {
        float dx = targetX - x;
        float dy = targetY - y;
        float distance = (float) Math.sqrt((dx * dx) + (dy * dy));

        if (distance <= ARRIVE_EPSILON) {
            paused = true;
            pauseRemaining = MathUtils.random(PAUSE_MIN_SECONDS, PAUSE_MAX_SECONDS);
            return;
        }

        float stepLength = Math.min(WALK_SPEED * step, distance);
        float moveX = (dx / distance) * stepLength;
        float moveY = (dy / distance) * stepLength;

        // Achsengetrennt pruefen (wie die vorhandene Rechteck-Kollision der Map).
        if (moveX != 0f) {
            if (isBlockedAt(x + moveX, y, collisionQuery)) {
                // Blockade: anhalten und ein neues Ziel waehlen.
                paused = true;
                pauseRemaining = BLOCKED_PAUSE_SECONDS;
                return;
            }
            x += moveX;
        }

        if (moveY != 0f) {
            if (isBlockedAt(x, y + moveY, collisionQuery)) {
                paused = true;
                pauseRemaining = BLOCKED_PAUSE_SECONDS;
                return;
            }
            y += moveY;
        }

        // Sicherheitsnetz: der Bereich wird nie dauerhaft verlassen.
        x = MathUtils.clamp(x, roamMinX, roamMaxX);
        y = MathUtils.clamp(y, roamMinY, roamMaxY);

        updateDirection(moveX, moveY);
    }

    /** Richtung folgt der tatsaechlichen Bewegung; Betrag X schlaegt Betrag Y. */
    private void updateDirection(float moveX, float moveY) {
        direction = directionFor(moveX, moveY);
        lastWalkDirection = direction;
    }

    /** Waehlt ein freies Ziel im Roamingbereich; begrenzte Versuche, keine Endlosschleife. */
    private void chooseNewTarget(CollisionQuery collisionQuery) {
        for (int attempt = 0; attempt < MAX_TARGET_ATTEMPTS; attempt++) {
            float candidateX = MathUtils.random(roamMinX, roamMaxX);
            float candidateY = MathUtils.random(roamMinY, roamMaxY);
            if (!isBlockedAt(candidateX, candidateY, collisionQuery)) {
                targetX = candidateX;
                targetY = candidateY;
                return;
            }
        }

        // Komplett blockiert: am Spawn bleiben und spaeter erneut versuchen.
        targetX = MathUtils.clamp(spawnX, roamMinX, roamMaxX);
        targetY = MathUtils.clamp(spawnY, roamMinY, roamMaxY);
    }

    private boolean isBlockedAt(float posX, float posY, CollisionQuery collisionQuery) {
        if (collisionQuery == null) {
            return false;
        }
        return collisionQuery.isBlocked(collisionBoxAt(posX, posY, collisionBox));
    }

    /**
     * Zeichnet den aktuellen Frame mit dem bestehenden MapRenderer-Batch. Waehrend einer
     * Pause bleibt der erste Frame der zuletzt gelaufenen Richtung stehen; eine
     * Idle-Animation existiert (noch) nicht. Keine Allokation, kein Shaderwechsel.
     */
    public void render(SpriteBatch batch) {
        TextureRegion frame = animations.frameFor(paused, direction, lastWalkDirection, stateTime);
        batch.draw(
            frame,
            x - (animations.getFrameWidth() * 0.5f),
            y - FOOT_OFFSET_Y);
    }

    // ------------------------------------------------------------------
    // Zugriff fuer Overworld und spaetere Systeme
    // ------------------------------------------------------------------

    public String getEncounterId() {
        return encounterId;
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Aktuelle Weltposition X (Anschlussstelle fuer die spaetere Corruption-Phase). */
    public float getWorldX() {
        return x;
    }

    /** Aktuelle Weltposition Y (Anschlussstelle fuer die spaetere Corruption-Phase). */
    public float getWorldY() {
        return y;
    }

    /** Wiederverwendete Sicht auf die aktuelle Weltposition (nicht zwischenspeichern). */
    public Vector2 getWorldPosition() {
        return worldPosition.set(x, y);
    }

    /** Fuss-/Sortierposition (Y-Wert des Y-Sortings). */
    public float getFootY() {
        return y;
    }

    public float getRoamMinX() {
        return roamMinX;
    }

    public float getRoamMaxX() {
        return roamMaxX;
    }

    public float getRoamMinY() {
        return roamMinY;
    }

    public float getRoamMaxY() {
        return roamMaxY;
    }

    public float getSpawnX() {
        return spawnX;
    }

    public float getSpawnY() {
        return spawnY;
    }

    public boolean isPaused() {
        return paused;
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getLastWalkDirection() {
        return lastWalkDirection;
    }

    /**
     * Aktuelle Interaktions-/Encounter-Flaeche am Fuss der Entity. Die zurueckgegebene
     * Instanz wird wiederverwendet: sie bleibt nur bis zum naechsten Aufruf gueltig.
     */
    public Rectangle getInteractionBounds() {
        return interactionBoundsAt(x, y, interactionBounds);
    }
}
