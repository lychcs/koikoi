package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code AsciiAsset.json} matrices produced by
 * {@code tools/PngToAsciiConverter.java}.
 *
 * <p>Parsing uses the LibGDX JSON reader (no extra dependency) and validates the
 * complete schema: {@code assetId}, {@code width}, {@code height},
 * {@code glyphs} (exactly {@code height} rows of {@code width} characters) and
 * {@code paletteIndices} ({@code width * height} entries inside
 * {@code [0, 128)}). {@link #parse(String, String)} is pure CPU code and can be
 * unit-tested without a GL context.</p>
 */
public final class AsciiAssetLoader {

    /** Folder of the generated ASCII asset matrices inside {@code assets/}. */
    public static final String ASCII_ASSET_DIR = "ascii/";

    private AsciiAssetLoader() {
    }

    /** Loads an asset from the asset folder. */
    public static AsciiAsset load(FileHandle file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("ASCII asset not found: " + file.path());
        }
        return parse(file.readString("UTF-8"), file.path());
    }

    /**
     * Parses one ASCII asset JSON document.
     *
     * @param json   JSON text
     * @param source human-readable source used in error messages
     * @throws IllegalArgumentException if the JSON is malformed or violates the schema
     */
    public static AsciiAsset parse(String json, String source) {
        if (json == null) {
            throw new IllegalArgumentException("ASCII asset JSON must not be null (source: " + source + ")");
        }
        String assetSource = source == null || source.isBlank() ? "<inline>" : source;

        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw invalid(assetSource, "invalid JSON: " + e.getMessage());
        }
        if (root == null) {
            throw invalid(assetSource, "empty JSON document");
        }

        String assetId = requiredString(root, "assetId", assetSource);
        String category = root.getString("category", "unknown");
        String pngSource = root.getString("source", "");

        int width = root.getInt("width", -1);
        int height = root.getInt("height", -1);
        if (width <= 0 || height <= 0) {
            throw invalid(assetSource, "width and height must be > 0 (got " + width + "x" + height + ")");
        }

        JsonValue glyphRows = root.get("glyphs");
        if (glyphRows == null || !glyphRows.isArray()) {
            throw invalid(assetSource, "missing 'glyphs' array");
        }
        if (glyphRows.size != height) {
            throw invalid(assetSource, "'glyphs' has " + glyphRows.size + " rows but height is " + height);
        }

        List<String> glyphs = new ArrayList<>(height);
        int rowIndex = 0;
        for (JsonValue row = glyphRows.child; row != null; row = row.next) {
            String glyphRow = row.asString();
            if (glyphRow.length() != width) {
                throw invalid(assetSource, "glyph row " + rowIndex + " has " + glyphRow.length()
                    + " characters but width is " + width);
            }
            glyphs.add(glyphRow);
            rowIndex++;
        }

        JsonValue palette = root.get("paletteIndices");
        if (palette == null || !palette.isArray()) {
            throw invalid(assetSource, "missing 'paletteIndices' array");
        }
        int expectedIndices = width * height;
        if (palette.size != expectedIndices) {
            throw invalid(assetSource, "'paletteIndices' has " + palette.size
                + " entries but " + expectedIndices + " are required");
        }

        int[] paletteIndices = new int[expectedIndices];
        int index = 0;
        for (JsonValue value = palette.child; value != null; value = value.next) {
            int paletteIndex = value.asInt();
            if (paletteIndex < 0 || paletteIndex >= AsciiAsset.PALETTE_SIZE) {
                throw invalid(assetSource, "palette index " + paletteIndex + " at position " + index
                    + " is outside [0, " + AsciiAsset.PALETTE_SIZE + ")");
            }
            paletteIndices[index] = paletteIndex;
            index++;
        }

        return new AsciiAsset(assetId, category, pngSource, width, height, glyphs, paletteIndices);
    }

    private static String requiredString(JsonValue root, String field, String source) {
        String value = root.getString(field, null);
        if (value == null || value.isBlank()) {
            throw invalid(source, "missing or blank '" + field + "'");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException("ASCII asset " + source + ": " + message);
    }
}
