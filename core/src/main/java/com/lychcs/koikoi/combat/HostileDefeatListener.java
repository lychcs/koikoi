package com.lychcs.koikoi.combat;

import com.badlogic.ashley.core.Entity;
import com.lychcs.koikoi.combat.component.HostileComponent;

/** Notified once when a hostile entity's health reaches zero. */
@FunctionalInterface
public interface HostileDefeatListener {

    void onHostileDefeated(Entity entity, HostileComponent hostile);
}
