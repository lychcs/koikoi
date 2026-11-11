package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the PNG-to-ASCII pipeline output in {@code assets/ascii}: every
 * generated matrix must match the documented {@code AsciiAsset} schema, use the
 * glyph ramp and stay inside the 128-colour palette.
 */
class AsciiAssetLoaderTest {

    private static final String GLYPH_RAMP = ".:\u2591\u2592\u2593\u2588@";

    @Test
    void everyGeneratedAssetMatchesTheSchema() throws IOException {
        List<AsciiAsset> assets = loadAllGeneratedAssets();

        assertFalse(assets.isEmpty(), "assets/ascii contains no generated assets");

        for (AsciiAsset asset : assets) {
            assertFalse(asset.getAssetId().isBlank(), asset.getSource());
            assertTrue(asset.getWidth() > 0 && asset.getHeight() > 0, asset.getAssetId());
            assertEquals(asset.getHeight(), asset.getGlyphs().size(), asset.getAssetId());
            assertEquals(asset.getWidth() * asset.getHeight(),
                asset.getPaletteIndices().length, asset.getAssetId());

            for (int row = 0; row < asset.getHeight(); row++) {
                String glyphRow = asset.getGlyphs().get(row);
                assertEquals(asset.getWidth(), glyphRow.length(), asset.getAssetId() + " row " + row);

                for (int col = 0; col < glyphRow.length(); col++) {
                    char glyph = glyphRow.charAt(col);
                    assertTrue(GLYPH_RAMP.indexOf(glyph) >= 0,
                        asset.getAssetId() + ": glyph '" + glyph + "' is outside the documented ramp");

                    int paletteIndex = asset.getPaletteIndex(col, row);
                    assertTrue(paletteIndex >= 0 && paletteIndex < AsciiAsset.PALETTE_SIZE,
                        asset.getAssetId() + ": palette index " + paletteIndex + " is out of range");
                }
            }
        }
    }

    @Test
    void coversAllSpriteCategories() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AsciiAsset asset : loadAllGeneratedAssets()) {
            counts.merge(asset.getCategory(), 1, Integer::sum);
        }

        assertTrue(counts.getOrDefault("player", 0) >= 1, "missing player asset: " + counts);
        assertTrue(counts.getOrDefault("yokai", 0) >= 3, "missing yokai assets: " + counts);
        assertTrue(counts.getOrDefault("omamori", 0) >= 15, "missing omamori assets: " + counts);
        assertTrue(counts.getOrDefault("hanko", 0) >= 7, "missing hanko assets: " + counts);
        assertTrue(counts.getOrDefault("hanafuda", 0) >= 24, "missing hanafuda assets: " + counts);
    }

    @Test
    void parsesThePlayerAsset() throws IOException {
        AsciiAsset player = AsciiAssetLoader.parse(
            TestAssetFiles.read("ascii/player.json"), "ascii/player.json");

        assertEquals("player", player.getAssetId());
        assertEquals("player", player.getCategory());
        assertTrue(player.getWidth() > 0, "player width");
        assertTrue(player.getHeight() > 0, "player height");
        assertTrue(player.getSource().endsWith("entities/player.png"));
    }

    @Test
    void manifestCoversEveryGeneratedFile() throws IOException {
        int generatedFiles = 0;
        for (Path file : TestAssetFiles.filesIn("ascii", ".json")) {
            if (!file.getFileName().toString().equals("manifest.json")) {
                generatedFiles++;
            }
        }

        JsonValue manifest = new JsonReader().parse(TestAssetFiles.read("ascii/manifest.json"));

        assertEquals(generatedFiles, manifest.getInt("assetCount"));
        assertEquals(AsciiAsset.PALETTE_SIZE, manifest.getInt("paletteSize"));
    }

    @Test
    void rejectsOutOfRangePaletteIndex() {
        String json = """
            {
              "assetId": "broken_palette",
              "width": 2,
              "height": 1,
              "glyphs": ["@@"],
              "paletteIndices": [0, 999]
            }
            """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiAssetLoader.parse(json, "broken_palette.json"));
        assertTrue(error.getMessage().contains("palette index"), error.getMessage());
    }

    @Test
    void rejectsGlyphRowWidthMismatch() {
        String json = """
            {
              "assetId": "broken_width",
              "width": 2,
              "height": 1,
              "glyphs": ["@"],
              "paletteIndices": [0, 1]
            }
            """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiAssetLoader.parse(json, "broken_width.json"));
        assertTrue(error.getMessage().contains("characters but width is"), error.getMessage());
    }

    @Test
    void rejectsMissingPaletteIndices() {
        String json = """
            {
              "assetId": "broken_missing",
              "width": 2,
              "height": 1,
              "glyphs": ["@@"],
              "paletteIndices": [0]
            }
            """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            AsciiAssetLoader.parse(json, "broken_missing.json"));
        assertTrue(error.getMessage().contains("paletteIndices"), error.getMessage());
    }

    private static List<AsciiAsset> loadAllGeneratedAssets() throws IOException {
        List<AsciiAsset> assets = new ArrayList<>();
        for (Path file : TestAssetFiles.filesIn("ascii", ".json")) {
            if (file.getFileName().toString().equals("manifest.json")) {
                continue;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            assets.add(AsciiAssetLoader.parse(json, file.getFileName().toString()));
        }
        return assets;
    }
}
