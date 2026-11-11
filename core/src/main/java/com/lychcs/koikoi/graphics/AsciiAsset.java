package com.lychcs.koikoi.graphics;

import java.util.Collections;
import java.util.List;

/**
 * In-memory form of one generated ASCII asset matrix
 * ({@code assets/ascii/<assetId>.json}).
 *
 * <p>An asset consists of a glyph grid (one string per row, each glyph is one
 * map cell) and a flat array of 128-colour palette indices in the same
 * row-major order. Instances are immutable and hold no GPU resource; the raster
 * layer of a later phase turns them into textures.</p>
 */
public final class AsciiAsset {

    /** Number of palette entries used by the ASCII pipeline. */
    public static final int PALETTE_SIZE = 128;

    private final String assetId;
    private final String category;
    private final String source;
    private final int width;
    private final int height;
    private final List<String> glyphs;
    private final int[] paletteIndices;

    AsciiAsset(String assetId, String category, String source, int width, int height,
               List<String> glyphs, int[] paletteIndices) {
        this.assetId = assetId;
        this.category = category;
        this.source = source;
        this.width = width;
        this.height = height;
        this.glyphs = List.copyOf(glyphs);
        this.paletteIndices = paletteIndices.clone();
    }

    /** Stable asset id (also the JSON file name without extension). */
    public String getAssetId() {
        return assetId;
    }

    /** Sprite category: {@code player}, {@code yokai}, {@code omamori}, {@code hanko} or {@code hanafuda}. */
    public String getCategory() {
        return category;
    }

    /** Project-relative path of the PNG this matrix was generated from. */
    public String getSource() {
        return source;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Glyph rows (top row first), one character per grid cell. */
    public List<String> getGlyphs() {
        return Collections.unmodifiableList(glyphs);
    }

    /**
     * Glyph at the given cell.
     *
     * @throws IndexOutOfBoundsException if the cell is outside the matrix
     */
    public char getGlyph(int col, int row) {
        checkCell(col, row);
        return glyphs.get(row).charAt(col);
    }

    /**
     * Palette index at the given cell.
     *
     * @throws IndexOutOfBoundsException if the cell is outside the matrix
     */
    public int getPaletteIndex(int col, int row) {
        checkCell(col, row);
        return paletteIndices[row * width + col];
    }

    /** Flat copy of the row-major palette index array ({@code width * height} entries). */
    public int[] getPaletteIndices() {
        return paletteIndices.clone();
    }

    private void checkCell(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) {
            throw new IndexOutOfBoundsException("Cell (" + col + ", " + row + ") is outside ASCII asset '"
                + assetId + "' (" + width + "x" + height + ")");
        }
    }
}
