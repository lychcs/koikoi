package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;

/** Current squash and stretch scales of the player sprite. */
public final class SquashStretchComponent implements Component {

    public float scaleX = 1f;
    public float scaleY = 1f;

    public SquashStretchComponent set(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        return this;
    }
}
