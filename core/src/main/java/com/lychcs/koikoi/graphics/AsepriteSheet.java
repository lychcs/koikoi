package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laufzeit-Sicht eines Aseprite-Sheets: fertige {@link TextureRegion}s und je
 * {@code frameTags}-Eintrag eine {@link Animation} im Modus
 * {@link Animation.PlayMode#LOOP}.
 *
 * <p>Die Textur wird <b>nicht</b> besessen: sie ist geliehen (Eigentum:
 * {@link GameAssets}) und wird hier niemals disposed. Regionen teilen immer die
 * Textur des Sheets; es entstehen keine weiteren GPU-Ressourcen.</p>
 *
 * <p>Da libGDX pro Animation nur eine gemeinsame Frame-Dauer kennt, wird die Dauer
 * des ersten Frames des jeweiligen Tags verwendet. Weichen die Dauern innerhalb
 * eines Tags ab, wird das einmalig mit Klartext gemeldet (keine stille
 * Ungenauigkeit).</p>
 */
public final class AsepriteSheet {

    private final AsepriteSheetData data;
    private final Texture texture;
    private final TextureRegion[] frameRegions;
    private final Map<String, Animation<TextureRegion>> animations;

    private AsepriteSheet(AsepriteSheetData data, Texture texture, TextureRegion[] frameRegions,
                          Map<String, Animation<TextureRegion>> animations) {
        this.data = data;
        this.texture = texture;
        this.frameRegions = frameRegions;
        this.animations = Map.copyOf(animations);
    }

    /**
     * Erzeugt Regionen und Animationen aus geparsten Daten und einer geliehenen Textur.
     *
     * @param requiredTags Tags, die zwingend vorhanden sein muessen
     * @throws GdxRuntimeException wenn ein Tag fehlt (Meldung nennt Tag und Dateipfad)
     */
    public static AsepriteSheet create(AsepriteSheetData data, Texture texture, String... requiredTags) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (texture == null) {
            throw new IllegalArgumentException("texture must not be null");
        }

        for (String tag : requiredTags) {
            if (!data.hasTag(tag)) {
                throw new GdxRuntimeException("Missing animation tag '" + tag + "' in " + data.getSourcePath()
                    + " (available: " + data.getTags().keySet() + ").");
            }
        }

        TextureRegion[] regions = new TextureRegion[data.getFrameCount()];
        for (int i = 0; i < regions.length; i++) {
            AsepriteSheetData.Frame frame = data.getFrame(i);
            regions[i] = new TextureRegion(
                texture,
                frame.x(),
                frame.y(),
                frame.width(),
                frame.height()
            );
        }

        Map<String, Animation<TextureRegion>> animations = new LinkedHashMap<>();
        for (AsepriteSheetData.Tag tag : data.getTags().values()) {
            TextureRegion[] tagFrames = new TextureRegion[data.getTagFrameCount(tag.name())];
            System.arraycopy(regions, tag.from(), tagFrames, 0, tagFrames.length);

            float frameDuration = data.getFrameDurationSeconds(tag.name());
            Animation<TextureRegion> animation = new Animation<>(frameDuration, tagFrames);
            animation.setPlayMode(Animation.PlayMode.LOOP);
            animations.put(tag.name(), animation);

            if (data.hasMixedFrameDurations(tag.name())) {
                Gdx.app.error("AsepriteSheet", "Tag '" + tag.name() + "' in " + data.getSourcePath()
                    + " uses different frame durations; libGDX animations run with the first duration ("
                    + frameDuration + "s).");
            }
        }

        return new AsepriteSheet(data, texture, regions, animations);
    }

    public AsepriteSheetData getData() {
        return data;
    }

    /** Geliehene Textur (Eigentum: GameAssets) - niemals hier disposen. */
    public Texture getTexture() {
        return texture;
    }

    public int getFrameWidth() {
        return data.getFrameWidth();
    }

    public int getFrameHeight() {
        return data.getFrameHeight();
    }

    public int getFrameCount() {
        return frameRegions.length;
    }

    public TextureRegion getFrame(int index) {
        return frameRegions[index];
    }

    public boolean hasAnimation(String tag) {
        return animations.containsKey(tag);
    }

    /**
     * Animation eines Tags.
     *
     * @throws GdxRuntimeException wenn der Tag nicht existiert (Meldung nennt Tag und Pfad)
     */
    public Animation<TextureRegion> getAnimation(String tag) {
        Animation<TextureRegion> animation = animations.get(tag);
        if (animation == null) {
            throw new GdxRuntimeException("Missing animation tag '" + tag + "' in " + data.getSourcePath()
                + " (available: " + animations.keySet() + ").");
        }
        return animation;
    }
}
