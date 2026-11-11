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

    /** Minimal bridge to the animated overworld Yokai actor. */
    public interface HostileActor {
        void setSpeedScale(float scale);
    }
}
