package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reine CPU-Daten eines Aseprite-Exports (JSON + Angabe des Spritesheets).
 *
 * <p>Es wird bewusst <b>kein</b> zweites Schema erfunden: gelesen wird genau das
 * Format, das Aseprite exportiert und das im Projekt bereits verwendet wird
 * ({@code assets/entities/player.json}, {@code assets/entities/kitsune_wild.json}).
 * Unterstuetzt werden beide von Aseprite erzeugten Auspraegungen der
 * {@code frames}-Sektion: als Objekt (Dateiname -> Frame) und als Array; die
 * Reihenfolge der Frames bleibt in beiden Faellen erhalten.</p>
 *
 * <p>Diese Klasse ist frei von GPU-Ressourcen und ohne GL-Kontext pruefbar. Sie
 * liest keine Datei selbst: der Aufrufer uebergibt den JSON-Text genau einmal
 * (niemals pro Frame).</p>
 *
 * <p><b>Unterstuetzte Export-Einstellungen:</b> {@code rotated = false} und
 * {@code trimmed = false}; Tag-{@code direction} muss {@code forward} sein. Eine
 * gedrehte, beschnittene oder rueckwaerts laufende Ausgabe wuerde ohne zusaetzliche
 * Offset-/Reihenfolgelogik falsch gezeichnet, deshalb bricht
 * {@link #parse(String, String)} dort mit Klartext ab, statt still zu verfremden.</p>
 *
 * <p><b>Frame-Dauern:</b> werden pro Frame gelesen und geprueft. LibGDX'
 * {@code Animation} kennt nur eine gemeinsame Dauer je Animation, deshalb liefert
 * {@link #getFrameDurationSeconds(String)} die Dauer des ersten Frames eines Tags
 * (das ist der Wert, den die Animation nutzt) und
 * {@code AsepriteSheet} meldet Tags mit abweichenden Frame-Dauern ausdruecklich;
 * {@link #getTagTotalDurationSeconds(String)} liefert die Gesamtdauer.</p>
 */
public final class AsepriteSheetData {

    /** Frame-Rechteck im Spritesheet plus Einzeldauer in Sekunden. */
    public record Frame(int x, int y, int width, int height, float durationSeconds) {}

    /** Animationsbereich eines Aseprite-Tags (inklusiv). */
    public record Tag(String name, int from, int to, String direction) {}

    /** Fallback-Dauer, falls ein Frame keine {@code duration} angibt (100 ms). */
    public static final float DEFAULT_FRAME_DURATION_SECONDS = 0.1f;

    private final String sourcePath;
    private final String imageName;
    private final int sheetWidth;
    private final int sheetHeight;
    private final int frameWidth;
    private final int frameHeight;
    private final List<Frame> frames;
    private final Map<String, Tag> tags;

    private AsepriteSheetData(
        String sourcePath,
        String imageName,
        int sheetWidth,
        int sheetHeight,
        int frameWidth,
        int frameHeight,
        List<Frame> frames,
        Map<String, Tag> tags
    ) {
        this.sourcePath = sourcePath;
        this.imageName = imageName;
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frames = List.copyOf(frames);
        this.tags = Map.copyOf(tags);
    }

    /**
     * Parst einen Aseprite-JSON-Text.
     *
     * @param jsonText   vollstaendiger JSON-Inhalt (bereits gelesen)
     * @param sourcePath Pfad nur fuer Fehlermeldungen, z. B. "entities/kitsune_wild.json"
     */
    public static AsepriteSheetData parse(String jsonText, String sourcePath) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new GdxRuntimeException("Empty Aseprite JSON: " + sourcePath);
        }

        JsonValue root;
        try {
            root = new JsonReader().parse(jsonText);
        } catch (RuntimeException e) {
            throw new GdxRuntimeException("Invalid Aseprite JSON in " + sourcePath + ": " + e.getMessage(), e);
        }

        JsonValue framesValue = root.get("frames");
        if (framesValue == null) {
            throw new GdxRuntimeException("Aseprite JSON without 'frames' section: " + sourcePath);
        }

        List<Frame> frames = new ArrayList<>();
        for (JsonValue entry = framesValue.child; entry != null; entry = entry.next) {
            frames.add(parseFrame(entry, sourcePath, frames.size()));
        }

        if (frames.isEmpty()) {
            throw new GdxRuntimeException("Aseprite JSON without frames: " + sourcePath);
        }

        int frameWidth = frames.get(0).width();
        int frameHeight = frames.get(0).height();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (frame.width() != frameWidth || frame.height() != frameHeight) {
                throw new GdxRuntimeException("Aseprite frames with different sizes in " + sourcePath
                    + ": frame 0 is " + frameWidth + "x" + frameHeight
                    + ", frame " + i + " is " + frame.width() + "x" + frame.height() + ".");
            }
        }

        JsonValue meta = root.get("meta");
        String imageName = meta == null ? null : meta.getString("image", null);
        int sheetWidth = 0;
        int sheetHeight = 0;
        if (meta != null && meta.get("size") != null) {
            sheetWidth = meta.get("size").getInt("w", 0);
            sheetHeight = meta.get("size").getInt("h", 0);
        }

        Map<String, Tag> tags = parseTags(meta, sourcePath, frames.size());

        return new AsepriteSheetData(
            sourcePath, imageName, sheetWidth, sheetHeight, frameWidth, frameHeight, frames, tags);
    }

    private static Frame parseFrame(JsonValue entry, String sourcePath, int index) {
        JsonValue frame = entry.get("frame");
        if (frame == null) {
            throw new GdxRuntimeException("Aseprite frame " + index + " without 'frame' rectangle in " + sourcePath);
        }

        if (entry.getBoolean("rotated", false)) {
            throw new GdxRuntimeException("Aseprite frame " + index + " is rotated in " + sourcePath
                + ": export the sheet with rotation disabled.");
        }
        if (entry.getBoolean("trimmed", false)) {
            throw new GdxRuntimeException("Aseprite frame " + index + " is trimmed in " + sourcePath
                + ": export the sheet with trim disabled.");
        }

        int width = frame.getInt("w", 0);
        int height = frame.getInt("h", 0);
        if (width <= 0 || height <= 0) {
            throw new GdxRuntimeException("Aseprite frame " + index + " has an invalid size in " + sourcePath + ".");
        }

        float durationSeconds = entry.getFloat("duration", DEFAULT_FRAME_DURATION_SECONDS * 1000f) / 1000f;
        if (!(durationSeconds > 0f)) {
            durationSeconds = DEFAULT_FRAME_DURATION_SECONDS;
        }

        return new Frame(frame.getInt("x", 0), frame.getInt("y", 0), width, height, durationSeconds);
    }

    private static Map<String, Tag> parseTags(JsonValue meta, String sourcePath, int frameCount) {
        Map<String, Tag> tags = new LinkedHashMap<>();
        if (meta == null) {
            return tags;
        }

        JsonValue frameTags = meta.get("frameTags");
        if (frameTags == null) {
            return tags;
        }

        for (JsonValue entry = frameTags.child; entry != null; entry = entry.next) {
            String name = entry.getString("name", null);
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            int from = entry.getInt("from", 0);
            int to = entry.getInt("to", -1);
            String direction = entry.getString("direction", "forward");

            if (from < 0 || to < from || to >= frameCount) {
                throw new GdxRuntimeException("Aseprite tag '" + name + "' has an invalid frame range "
                    + from + ".." + to + " in " + sourcePath + " (" + frameCount + " frames).");
            }
            if (!"forward".equalsIgnoreCase(direction)) {
                throw new GdxRuntimeException("Aseprite tag '" + name + "' uses direction '" + direction
                    + "' in " + sourcePath + ": only 'forward' is supported.");
            }

            tags.put(name, new Tag(name, from, to, direction));
        }
        return tags;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    /** Name des im JSON genannten Spritesheets (nur zur Konsistenzpruefung). */
    public String getImageName() {
        return imageName;
    }

    public int getSheetWidth() {
        return sheetWidth;
    }

    public int getSheetHeight() {
        return sheetHeight;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public Frame getFrame(int index) {
        return frames.get(index);
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public Map<String, Tag> getTags() {
        return tags;
    }

    public Tag getTag(String name) {
        return tags.get(name);
    }

    public boolean hasTag(String name) {
        return tags.containsKey(name);
    }

    /**
     * Dauer EINES Frames des Tags in Sekunden (die Dauer des ersten Frames). Ein Tag aus
     * vier Frames mit je 0,1 s dauert insgesamt 0,4 s; libGDX-Animationen nutzen genau
     * diesen Wert als Frame-Dauer. Liefert 0, wenn der Tag fehlt.
     */
    public float getFrameDurationSeconds(String tagName) {
        Tag tag = tags.get(tagName);
        return tag == null ? 0f : frames.get(tag.from()).durationSeconds();
    }

    /** Gesamtdauer eines Tags in Sekunden (Summe aller Frame-Dauern); 0, wenn der Tag fehlt. */
    public float getTagTotalDurationSeconds(String tagName) {
        Tag tag = tags.get(tagName);
        if (tag == null) {
            return 0f;
        }
        float total = 0f;
        for (int i = tag.from(); i <= tag.to(); i++) {
            total += frames.get(i).durationSeconds();
        }
        return total;
    }

    /** Anzahl der Frames eines Tags; 0, wenn der Tag fehlt. */
    public int getTagFrameCount(String tagName) {
        Tag tag = tags.get(tagName);
        return tag == null ? 0 : tag.to() - tag.from() + 1;
    }

    /** true, wenn innerhalb eines Tags nicht alle Frames dieselbe Dauer haben. */
    public boolean hasMixedFrameDurations(String name) {
        Tag tag = tags.get(name);
        if (tag == null) {
            return false;
        }
        float first = frames.get(tag.from()).durationSeconds();
        for (int i = tag.from() + 1; i <= tag.to(); i++) {
            if (frames.get(i).durationSeconds() != first) {
                return true;
            }
        }
        return false;
    }
}
