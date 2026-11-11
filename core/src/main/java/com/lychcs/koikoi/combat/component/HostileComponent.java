package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/**
 * Marks a hostile (wild Yokai) entity.
 *
 * <p>The optional {@link HostileActor} connects the ECS to the animated roaming
 * actor so status effects and defeat can be forwarded without the ECS knowing
 * about sprites or Box2D.</p>
 */
public final class HostileComponent implements Component {

    /** Stable encounter id used to mark the encounter defeated. */
    public String encounterId = "";

    /** Hit radius used by seals, projectiles and companionship targeting. */
    public float radius = 28f;

    /** Legacy roaming actor, or {@code null} for pure test entities. */
    public HostileActor actor;

    /** true once the defeat listener has been notified. */
    public boolean defeated;

    /** Forwards a speed multiplier to the legacy actor. */
    public void setSpeedScale(float scale) {
        if (actor != null) {
            actor.setSpeedScale(scale);
        }
    }

    /** Forwards a brief high-contrast ink flash to the legacy actor. */
    public void flashHit(float seconds) {
        if (actor != null) {
            actor.flashHit(seconds);
        }
    }

    /** Pushes the actor away from a normalized direction by {@code distance} px. */
    public void knockback(float directionX, float directionY, float distance) {
        if (actor != null) {
            actor.knockback(directionX, directionY, distance);
        }
    }

    /** Minimal bridge to the animated overworld Yokai actor. */
    public interface HostileActor {
        void setSpeedScale(float scale);

        /** Default no-op keeps lightweight test actors source-compatible. */
        default void flashHit(float seconds) {
        }

        /** Default no-op keeps lightweight test actors source-compatible. */
        default void knockback(float directionX, float directionY, float distance) {
        }
    }
}
