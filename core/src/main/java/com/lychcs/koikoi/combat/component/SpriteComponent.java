package com.lychcs.koikoi.combat.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Billboarding data for companion sprites (borrowed atlas region). */
public final class SpriteComponent implements Component {

    /** Borrowed region from the shared game atlas (never disposed here). */
    public TextureRegion region;

    /** Uniform world scale applied to the region size. */
    public float scale = 0.6f;

    /** When true the sprite is mirrored horizontally. */
    public boolean flipX;

    public SpriteComponent set(TextureRegion region, float scale) {
        this.region = region;
        this.scale = scale;
        return this;
    }

    public float width() {
        return region == null ? 0f : region.getRegionWidth() * scale;
    }

    public float height() {
        return region == null ? 0f : region.getRegionHeight() * scale;
    }
}
