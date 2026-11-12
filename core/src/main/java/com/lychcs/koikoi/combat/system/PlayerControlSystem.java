package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.lychcs.koikoi.combat.MovementVector;
import com.lychcs.koikoi.combat.ParticleBurst;
import com.lychcs.koikoi.combat.PlayerInput;
import com.lychcs.koikoi.combat.SquashStretch;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.SquashStretchComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

/**
 * MOBA-style target movement, mouse-directed Q dash with I-frames, an S stop
 * command, mana regeneration and the procedural squash &amp; stretch pose of
 * the player.
 *
 * <p>The system only writes the desired velocity into the player's
 * {@link MotionComponent}; the screen forwards it to the Box2D body. A
 * right-click destination in {@link PlayerInput#moveTarget} is followed until
 * the player is within {@link #ARRIVE_THRESHOLD} world units. The
 * first-person overworld fills {@link PlayerInput#moveDirection} with the
 * camera-relative WASD direction, which steers walks and gives the Q dash its
 * kiting direction (for example {@code S + Q} backpedals while the crosshair
 * stays on the target). A dash consumes that destination, and the S stop
 * command cancels it outright - exactly like League of Legends. The legacy
 * axis fields remain as a test/developer fallback when no target is active.</p>
 */
public final class PlayerControlSystem extends IteratingSystem {

    /** Extra invulnerability after the dash ends. */
    public static final float DASH_IFRAME_BONUS = 0.08f;

    /** Seconds between two dash dust bursts. */
    public static final float DASH_EMIT_INTERVAL = 0.055f;

    /** Distance in world units at which a right-click destination is reached. */
    public static final float ARRIVE_THRESHOLD = 0.15f;

    /** Card speed in tile-units per second (scaled by the map tile size). */
    public static final float CARD_SPEED_TILES_PER_SECOND = 18f;

    /** Card maximum range in tile-units (scaled by the map tile size). */
    public static final float CARD_RANGE_TILES = 12f;

    /** Vertical offset from the player's feet to the card throw origin. */
    public static final float PLAYER_CENTER_OFFSET = 64f;

    private final PlayerInput input;
    private final ParticleBurst particles;
    private final float gridSize;

    private final Vector2 direction = new Vector2();
    private final Vector2 squash = new Vector2();

    private float dashEmitTimer;

    public PlayerControlSystem(PlayerInput input, ParticleBurst particles) {
        this(input, particles, 64f);
    }

    public PlayerControlSystem(PlayerInput input, ParticleBurst particles, float gridSize) {
        super(Family.all(
            PlayerComponent.class,
            TransformComponent.class,
            MotionComponent.class,
            SquashStretchComponent.class).get(), 0);
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (!(gridSize > 0f)) {
            throw new IllegalArgumentException("gridSize must be > 0 but was " + gridSize);
        }
        this.input = input;
        this.particles = particles;
        this.gridSize = gridSize;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = Mappers.PLAYER.get(entity);
        TransformComponent transform = Mappers.TRANSFORM.get(entity);
        MotionComponent motion = Mappers.MOTION.get(entity);
        SquashStretchComponent stretch = Mappers.SQUASH.get(entity);

        // --- timers and mana -------------------------------------------------
        player.dashCooldownRemaining = Math.max(0f, player.dashCooldownRemaining - deltaTime);
        player.castCooldownRemaining = Math.max(0f, player.castCooldownRemaining - deltaTime);
        player.invulnerabilityRemaining = Math.max(0f, player.invulnerabilityRemaining - deltaTime);
        player.dashRemaining = Math.max(0f, player.dashRemaining - deltaTime);
        player.basicAttackCooldownRemaining =
            Math.max(0f, player.basicAttackCooldownRemaining - deltaTime);
        player.updateBuffs(deltaTime);
        player.addMana(player.manaRegenPerSecond * deltaTime);

        // --- W pick-a-card state machine -------------------------------------
        CardDeckComponent deck = Mappers.CARD_DECK.get(entity);
        if (deck != null) {
            if (input.cardSelectRequested) {
                deck.press();
                if (Gdx.app != null) {
                    Gdx.app.log("PlayerControlSystem", "W card state=" + deck.state
                        + " active=" + deck.activeCard().label);
                }
            }
            deck.update(deltaTime);
        }

        // --- S stop command ---------------------------------------------------
        // League-style Stop: cancel the pending right-click destination before
        // the movement direction is derived this frame.
        if (input.stopRequested) {
            input.hasMoveTarget = false;
        }

        // --- MOBA target / fallback movement direction ------------------------
        float targetDistance = 0f;
        boolean hasDirection = false;
        if (input.hasMoveTarget) {
            float targetDx = input.moveTarget.x - transform.x;
            float targetDy = input.moveTarget.y - transform.y;
            targetDistance = (float) Math.sqrt(targetDx * targetDx + targetDy * targetDy);
            if (targetDistance <= ARRIVE_THRESHOLD) {
                // Arrived: stop cleanly and drop the destination.
                input.hasMoveTarget = false;
                direction.setZero();
            } else {
                direction.set(targetDx / targetDistance, targetDy / targetDistance);
                hasDirection = true;
            }
        }

        if (!hasDirection) {
            // First-person camera-relative WASD direction from the screen.
            float planarLength2 = input.hasMoveDirection
                ? input.moveDirection.len2() : 0f;
            if (planarLength2 > 0.0001f) {
                direction.set(input.moveDirection).scl(1f / (float) Math.sqrt(planarLength2));
                hasDirection = true;
            }
        }

        if (!hasDirection) {
            // Legacy/dev fallback only (8-way axis input).
            MovementVector.fromAxis(input.moveX, input.moveY, direction);
            hasDirection = direction.len2() > 0f;
        }

        // S halts the current walk immediately, including any fallback axes.
        // An active dash still resolves its locked velocity further below.
        if (input.stopRequested) {
            direction.setZero();
            hasDirection = false;
        }

        // While dashing the direction is locked; the target must not steer it.
        if (!player.isDashing() && hasDirection) {
            player.facingX = direction.x;
            player.facingY = direction.y;
        }

        // --- Q dash: WASD direction when held, otherwise crosshair/aim --------
        if (input.dashRequested && !player.isDashing() && player.dashCooldownRemaining <= 0f) {
            player.dashRemaining = player.dashDuration;
            player.dashCooldownRemaining = player.dashCooldown;
            player.invulnerabilityRemaining = player.dashDuration + DASH_IFRAME_BONUS;

            // A dash is a committed move: consume the right-click order so the
            // player stops after the dash instead of resuming the old path.
            input.hasMoveTarget = false;

            // Vayne-style kiting: a held WASD direction decides where Q goes,
            // so holding S + Q backpedals while the crosshair keeps facing
            // forward. Without WASD the dash follows the aim (mouse/crosshair).
            float planarLength2 = input.hasMoveDirection
                ? input.moveDirection.len2() : 0f;
            if (planarLength2 > 0.0001f) {
                float inverseLength = 1f / (float) Math.sqrt(planarLength2);
                player.facingX = input.moveDirection.x * inverseLength;
                player.facingY = input.moveDirection.y * inverseLength;
            } else {
                float aimDx = input.aimX - transform.x;
                float aimDy = input.aimY - transform.y;
                float aimLength2 = aimDx * aimDx + aimDy * aimDy;
                if (aimLength2 > 0.0001f) {
                    float inverseLength = 1f / (float) Math.sqrt(aimLength2);
                    player.facingX = aimDx * inverseLength;
                    player.facingY = aimDy * inverseLength;
                } else if (hasDirection) {
                    player.facingX = direction.x;
                    player.facingY = direction.y;
                } else if (player.facingX == 0f && player.facingY == 0f) {
                    player.facingY = -1f;
                }
            }

            emitDashBurst(transform.x, transform.y);
            dashEmitTimer = DASH_EMIT_INTERVAL;
        }

        // --- basic left-click card throw -------------------------------------
        if (input.basicAttackRequested && player.basicAttackCooldownRemaining <= 0f) {
            spawnCardProjectile(transform, player, deck);
            player.basicAttackCooldownRemaining = player.basicAttackCooldown;
        }

        // --- velocity --------------------------------------------------------
        if (player.isDashing()) {
            motion.vx = player.facingX * player.dashSpeed;
            motion.vy = player.facingY * player.dashSpeed;

            dashEmitTimer -= deltaTime;
            if (dashEmitTimer <= 0f) {
                emitDashBurst(transform.x, transform.y);
                dashEmitTimer = DASH_EMIT_INTERVAL;
            }
        } else if (hasDirection) {
            float speed = motion.maxSpeed;
            if (input.hasMoveTarget && targetDistance > 0f) {
                // Do not overshoot the destination: the last step is clamped to
                // the remaining distance, so the 0.15-unit threshold is reachable.
                speed = Math.min(speed, targetDistance / Math.max(deltaTime, 0.0001f));
            }
            motion.vx = direction.x * speed;
            motion.vy = direction.y * speed;
        } else {
            motion.vx = 0f;
            motion.vy = 0f;
        }

        // --- procedural squash & stretch -------------------------------------
        float dashPulse = player.isDashing()
            ? SquashStretch.MAX_DASH_PULSE
                * Math.min(1f, player.dashRemaining / Math.max(0.01f, player.dashDuration))
            : 0f;
        SquashStretch.compute(motion.vx, motion.vy, motion.maxSpeed, dashPulse, squash);
        stretch.scaleX = squash.x;
        stretch.scaleY = squash.y;
    }

    /** Creates a cinematic little card entity flying toward the current mouse aim. */
    private void spawnCardProjectile(TransformComponent playerTransform, PlayerComponent player,
                                     CardDeckComponent deck) {
        float originX = playerTransform.x;
        float originY = playerTransform.y + PLAYER_CENTER_OFFSET;

        float aimDx = input.aimX - originX;
        float aimDy = input.aimY - originY;
        float length = (float) Math.sqrt(aimDx * aimDx + aimDy * aimDy);
        float directionX;
        float directionY;
        if (length > 0.0001f) {
            directionX = aimDx / length;
            directionY = aimDy / length;
        } else if (player.facingX != 0f || player.facingY != 0f) {
            directionX = player.facingX;
            directionY = player.facingY;
        } else {
            directionX = 1f;
            directionY = 0f;
        }

        float speed = CARD_SPEED_TILES_PER_SECOND * gridSize;
        CardProjectileComponent card = new CardProjectileComponent();
        card.vx = directionX * speed;
        card.vy = directionY * speed;
        card.speed = speed;
        card.maxRange = CARD_RANGE_TILES * gridSize;
        card.glyph = cardGlyph(directionX, directionY);

        // The 3D billboard follows the crosshair ray from the camera, so the
        // card visually leaves the centre of the screen and recedes along it.
        if (input.hasAimRay) {
            card.hasWorldRay = true;
            card.rayOriginX = input.aimRayOrigin.x;
            card.rayOriginY = input.aimRayOrigin.y;
            card.rayOriginZ = input.aimRayOrigin.z;
            card.rayDirX = input.aimRayDirection.x;
            card.rayDirY = input.aimRayDirection.y;
            card.rayDirZ = input.aimRayDirection.z;
        }

        // A locked W card is consumed by this throw and applies its archetype.
        if (deck != null && deck.state == CardDeckComponent.State.LOCKED) {
            CardDeckComponent.CardType type = deck.consumeLocked();
            applyDeckCard(card, type);
        }

        Entity projectile = getEngine().createEntity();
        projectile.add(new TransformComponent().set(
            originX + directionX * 10f,
            originY + directionY * 10f));
        projectile.add(card);
        getEngine().addEntity(projectile);
        if (Gdx.app != null) {
            Gdx.app.log("PlayerControlSystem", "Card spawned glyph=" + card.glyph
                + " type=" + (card.deckCard == null ? "BASIC" : card.deckCard.label)
                + " speed=" + Math.round(card.speed) + " range=" + Math.round(card.maxRange));
        }
    }

    /** Applies the locked card's unique projectile payload and colour. */
    private void applyDeckCard(CardProjectileComponent card, CardDeckComponent.CardType type) {
        card.deckCard = type;
        card.red = type.red;
        card.green = type.green;
        card.blue = type.blue;
        card.glyph = type.projectileGlyph;
        switch (type) {
            case RED -> {
                card.damage = 14f;
                card.areaRadius = 2.5f * gridSize;
                card.knockbackDistance = 26f;
            }
            case BLUE -> {
                card.damage = 8f;
                card.refundCooldowns = true;
                card.manaRefund = 30f;
            }
            case GOLD -> {
                card.damage = 36f;
                card.stunDuration = 1.25f;
                card.knockbackDistance = 34f;
            }
        }
    }

    /** Directional card glyph aligned to the travel vector. */
    static char cardGlyph(float directionX, float directionY) {
        if (Math.abs(directionX) >= Math.abs(directionY)) {
            return directionX >= 0f ? '>' : '<';
        }
        return directionY >= 0f ? '^' : 'v';
    }

    private void emitDashBurst(float x, float y) {
        if (particles != null) {
            particles.burst(x, y, 2, 1);
        }
    }
}
