package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Twisted-Fate-style pick-a-card state for the player's 3P card ability.
 *
 * <p>Press E (or R, or right-click away from a target) in {@link State#IDLE} to
 * start {@link State#SHUFFLING}; the active slot cycles every
 * {@link #SHUFFLE_INTERVAL} seconds for at most {@link #SHUFFLE_MAX_SECONDS}.
 * Press the shuffle key again to {@link State#LOCKED} the active card for
 * {@link #LOCKED_SECONDS}, or let the shuffle time out. A locked card is
 * consumed by the next left-click card throw and always ends in a
 * {@link State#COOLDOWN} of {@link #COOLDOWN_SECONDS} seconds.</p>
 */
public final class CardDeckComponent implements Component {

    public enum State {
        IDLE,
        SHUFFLING,
        LOCKED,
        COOLDOWN
    }

    /** The three equipped card archetypes. Colours are muted earth tones. */
    public enum CardType {
        // RED is the Hikari/Cherry-Blossom slot: petal ink with AoE damage.
        RED("RED", 0.80f, 0.30f, 0.20f, '*'),
        // BLUE is the Ribbon slot: wave/ribbon ink with mana refund.
        BLUE("BLUE", 0.30f, 0.52f, 0.64f, '~'),
        // GOLD is the Beast slot: horn/beast ink with heavy damage and stun.
        GOLD("GOLD", 0.88f, 0.70f, 0.36f, 'Y');

        public final String label;
        public final float red;
        public final float green;
        public final float blue;
        /** Hanafuda-flavoured ASCII projectile sigil. */
        public final char projectileGlyph;

        CardType(String label, float red, float green, float blue, char projectileGlyph) {
            this.label = label;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.projectileGlyph = projectileGlyph;
        }
    }

    public static final float SHUFFLE_INTERVAL = 0.4f;
    public static final float SHUFFLE_MAX_SECONDS = 3.5f;
    public static final float LOCKED_SECONDS = 6.0f;
    public static final float COOLDOWN_SECONDS = 5.0f;

    /** Equipped cards in slot order. */
    public final CardType[] equipped = {
        CardType.RED, CardType.BLUE, CardType.GOLD
    };

    public State state = State.IDLE;

    /** Index of the currently highlighted equipped card. */
    public int activeIndex;

    /** Time spent in the current state. */
    public float stateTimer;

    /** Fractional time until the shuffling highlight advances. */
    public float shuffleTimer;

    /** Remaining locked-buff time. */
    public float lockedRemaining;

    /** Remaining cooldown time. */
    public float cooldownRemaining;

    public CardType activeCard() {
        return equipped[Math.floorMod(activeIndex, equipped.length)];
    }

    public boolean showsBadge() {
        return state == State.SHUFFLING || state == State.LOCKED;
    }

    /** Shuffle key: IDLE starts a shuffle, SHUFFLING locks the highlighted card. */
    public void press() {
        if (state == State.IDLE) {
            state = State.SHUFFLING;
            stateTimer = 0f;
            shuffleTimer = 0f;
            activeIndex = 0;
        } else if (state == State.SHUFFLING) {
            state = State.LOCKED;
            stateTimer = 0f;
            lockedRemaining = LOCKED_SECONDS;
        }
    }

    /** Advances timers and performs the automatic state transitions. */
    public void update(float deltaTime) {
        float step = Math.max(0f, deltaTime);
        stateTimer += step;

        if (state == State.SHUFFLING) {
            shuffleTimer += step;
            while (shuffleTimer >= SHUFFLE_INTERVAL) {
                shuffleTimer -= SHUFFLE_INTERVAL;
                activeIndex = (activeIndex + 1) % equipped.length;
            }
            if (stateTimer >= SHUFFLE_MAX_SECONDS) {
                beginCooldown();
            }
        } else if (state == State.LOCKED) {
            lockedRemaining -= step;
            if (lockedRemaining <= 0f) {
                beginCooldown();
            }
        } else if (state == State.COOLDOWN) {
            cooldownRemaining -= step;
            if (cooldownRemaining <= 0f) {
                state = State.IDLE;
                stateTimer = 0f;
                cooldownRemaining = 0f;
            }
        }
    }

    /** Consumes the locked card and starts the full W cooldown. */
    public CardType consumeLocked() {
        CardType card = activeCard();
        beginCooldown();
        return card;
    }

    public void beginCooldown() {
        state = State.COOLDOWN;
        stateTimer = 0f;
        cooldownRemaining = COOLDOWN_SECONDS;
        lockedRemaining = 0f;
    }

    /** Ribbon-Yaku refund: next W press is immediately available again. */
    public void resetCooldown() {
        state = State.IDLE;
        stateTimer = 0f;
        shuffleTimer = 0f;
        lockedRemaining = 0f;
        cooldownRemaining = 0f;
    }

    public String stateLabel() {
        return switch (state) {
            case IDLE -> "READY";
            case SHUFFLING -> "SHUFFLING";
            case LOCKED -> "LOCKED";
            case COOLDOWN -> "CD";
        };
    }
}
