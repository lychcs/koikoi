package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Renders an {@link AsciiAsset} glyph matrix as a true monospace ASCII
 * assembly.
 *
 * <p>The generated matrices store one character per cell but are 32 cells
 * tall. Drawing all 32 rows into the old 3-4 px entity footprint squeezed each
 * glyph below the readable pixel size, which produced the blocky sprite look.
 * This renderer collapses {@link #cellStride} x {@link #cellStride} matrix
 * cells into one terminal cell: it keeps the densest glyph of the block,
 * averages the palette colour, and maps the shade block onto readable ASCII
 * punctuation (see {@link AsciiGlyphStyle}). Every painted glyph is therefore
 * at least {@code cellSize} world pixels tall, so the player, companions and
 * Yokai read as distinct character assemblies.</p>
 *
 * <p>Only the inked bounding box is rendered (empty top and side margins are
 * trimmed), the side matrix is mirrored for RIGHT, and the two-frame leg
 * animation still overrides the lower-body glyphs while walking. Sparse
 * {@code ':'} background dither is dropped at block level, and the player
 * renderer uses a core-only mode that trims the bounds to solid body shades,
 * so a transparent entity matrix never paints a gray rectangular backdrop
 * behind the figure.</p>
 */
public final class AsciiGlyphRenderer {

    /** Build marker used by the overworld log to prove this renderer is active. */
    public static final String BUILD_TAG = "ascii-terminal-6";

    /** Transparent/background cells of the matrix are not painted. */
    private static final char EMPTY = AsciiGlyphStyle.EMPTY;

    private static final String[] ASCII_TEXT = new String[128];

    /**
     * Density at or above which a source cell counts as solid body ink
     * ({@code ░} and brighter). Sparse {@code ':'} dither below this level is
     * background texture and must not turn a whole 2x2 block into a gray cell.
     */
    private static final float CORE_INK_LEVEL = 0.30f;

    /** Minimum weak-cell coverage for a dither-only block to remain visible. */
    private static int minimumWeakCells(int cellStride) {
        return Math.max(1, (cellStride * cellStride + 1) / 2);
    }

    static {
        for (int i = 0; i < ASCII_TEXT.length; i++) {
            ASCII_TEXT[i] = String.valueOf((char) i);
        }
    }

    /** Inclusive ink bounds of one matrix (all transparent margins removed). */
    private record Bounds(int minCol, int maxCol, int minRow, int maxRow) {
        int columns() {
            return maxCol - minCol + 1;
        }

        int rows() {
            return maxRow - minRow + 1;
        }
    }

    /** One terminal cell after the stride collapse. */
    private static final class Block {
        static final Block EMPTY_CELL = new Block(EMPTY, Color.WHITE, 0f);

        final char glyph;
        final Color color;
        final float level;

        Block(char glyph, Color color, float level) {
            this.glyph = glyph;
            this.color = color;
            this.level = level;
        }
    }

    /** Downsampled terminal matrix with one {@link Block} per cell. */
    private static final class Matrix {
        final Block[][] blocks;
        final int columns;
        final int rows;

        Matrix(Block[][] blocks, int columns, int rows) {
            this.blocks = blocks;
            this.columns = columns;
            this.rows = rows;
        }

        Block at(int col, int row) {
            return blocks[row][col];
        }
    }

    private final AsciiAsset down;
    private final AsciiAsset up;
    private final AsciiAsset side;
    private final BitmapFont font;
    private final Color[] glyphColors;
    private final float cellSize;
    private final int cellStride;
    /** False for the player: ':' background dither is never painted. */
    private final boolean keepWeakInk;
    /** True only for the player: bright parchment/white ink. */
    private final boolean brightInk;

    private final Matrix downMatrix;
    private final Matrix upMatrix;
    private final Matrix sideMatrix;
    private final int columns;
    private final int rows;

    private final float baseScaleX;
    private final float baseScaleY;
    /** Distance from the cell bottom to the baseline, at base scale. */
    private final float baselineOffset;
    /** Horizontal centring of the natural glyph inside its square cell. */
    private final float xInset;

    private final float worldWidth;
    private final float worldHeight;

    /** Number of glyphs written into the colour pass by the last render call. */
    private int lastGlyphCount;
    /** Two-frame walk index used by the last render (diagnostics). */
    private int lastWalkFrame;
    /** Dimensions of the terminal matrix used by the last render. */
    private int lastMatrixWidth;
    private int lastMatrixHeight;

    /**
     * Single-matrix constructor for entities that do not ship directional
     * frames (companions and wild Yokai). The same matrix is used for every
     * facing.
     */
    public AsciiGlyphRenderer(AsciiAsset asset, BitmapFont font, Color[] palette,
                              float cellSize) {
        this(asset, asset, asset, font, palette, cellSize, 1, true);
    }

    /** Single-matrix constructor with a cell stride (matrix cells per glyph). */
    public AsciiGlyphRenderer(AsciiAsset asset, BitmapFont font, Color[] palette,
                              float cellSize, int cellStride) {
        this(asset, asset, asset, font, palette, cellSize, cellStride, true);
    }

    /** Directional constructor with the default one-to-one cell mapping. */
    public AsciiGlyphRenderer(AsciiAsset down, AsciiAsset up, AsciiAsset side,
                              BitmapFont font, Color[] palette, float cellSize) {
        this(down, up, side, font, palette, cellSize, 1, true);
    }

    /** Directional constructor with a cell stride (matrix cells per glyph). */
    public AsciiGlyphRenderer(AsciiAsset down, AsciiAsset up, AsciiAsset side,
                              BitmapFont font, Color[] palette, float cellSize,
                              int cellStride) {
        this(down, up, side, font, palette, cellSize, cellStride, true);
    }

    /**
     * @param down         front matrix (walk_down frame)
     * @param up           back matrix (walk_up frame)
     * @param side         side matrix (walk_left frame; mirrored for RIGHT)
     * @param font         monospace font lent by the caller
     * @param palette      128 colours sampled from {@code palette/palette_128.png}
     * @param cellSize     world size of one terminal cell in pixels
     * @param cellStride   matrix cells collapsed into one terminal glyph
     * @param keepWeakInk  when false, {@code ':'} cells are transparent
     *                     background and the ink bounds are trimmed to solid
     *                     shade cells. Used by the player so no dither
     *                     rectangle is painted around the avatar.
     */
    public AsciiGlyphRenderer(AsciiAsset down, AsciiAsset up, AsciiAsset side,
                              BitmapFont font, Color[] palette, float cellSize,
                              int cellStride, boolean keepWeakInk) {
        this(down, up, side, font, palette, cellSize, cellStride, keepWeakInk, false);
    }

    /**
     * Full constructor including the bright player-ink switch.
     *
     * @param brightInk true only for the player: high-luminance parchment ink
     */
    public AsciiGlyphRenderer(AsciiAsset down, AsciiAsset up, AsciiAsset side,
                              BitmapFont font, Color[] palette, float cellSize,
                              int cellStride, boolean keepWeakInk, boolean brightInk) {
        if (down == null || up == null || side == null) {
            throw new IllegalArgumentException("all directional matrices are required");
        }
        if (font == null) {
            throw new IllegalArgumentException("font must not be null");
        }
        if (palette == null || palette.length < AsciiAsset.PALETTE_SIZE) {
            throw new IllegalArgumentException("palette must contain at least "
                + AsciiAsset.PALETTE_SIZE + " colours");
        }
        if (cellSize <= 0f) {
            throw new IllegalArgumentException("cellSize must be > 0 but was " + cellSize);
        }

        this.down = down;
        this.up = up;
        this.side = side;
        this.font = font;
        this.glyphColors = EarthTonePalette.transform(palette);
        this.cellSize = cellSize;
        this.cellStride = Math.max(1, cellStride);
        this.keepWeakInk = keepWeakInk;
        this.brightInk = brightInk;

        Bounds union = unionBounds(down, up, side, keepWeakInk);
        this.downMatrix = downsample(down, union);
        this.upMatrix = downsample(up, union);
        this.sideMatrix = downsample(side, union);
        this.columns = downMatrix.columns;
        this.rows = downMatrix.rows;

        BitmapFont.BitmapFontData data = font.getData();
        BitmapFont.Glyph reference = data.getGlyph('#');
        float advance = reference == null ? 0f : reference.xadvance;
        float inkHeight = reference == null ? 0f : reference.height;
        if (advance <= 0f || inkHeight <= 0f) {
            throw new IllegalArgumentException("font has no usable glyph metrics");
        }

        // Uniform scale keeps the font's natural aspect: stretching glyphs to
        // fill a square cell is what made the terminal grid look blocky.
        float scale = Math.min(cellSize / advance, cellSize / inkHeight);
        this.baseScaleX = scale;
        this.baseScaleY = scale;
        this.baselineOffset = reference.yoffset * scale;
        this.xInset = (cellSize - advance * scale) * 0.5f;

        this.worldWidth = columns * cellSize;
        this.worldHeight = rows * cellSize;
    }

    /** Width of the widest directional matrix in world pixels. */
    public float getWorldWidth() {
        return worldWidth;
    }

    /** Height of the tallest directional matrix in world pixels. */
    public float getWorldHeight() {
        return worldHeight;
    }

    /** Glyphs written by the last render call (diagnostics). */
    public int getLastGlyphCount() {
        return lastGlyphCount;
    }

    /** Two-frame walk index of the last render (diagnostics). */
    public int getLastWalkFrame() {
        return lastWalkFrame;
    }

    /** Width of the terminal matrix used by the last render (diagnostics). */
    public int getLastMatrixWidth() {
        return lastMatrixWidth;
    }

    /** Height of the terminal matrix used by the last render (diagnostics). */
    public int getLastMatrixHeight() {
        return lastMatrixHeight;
    }

    /** Static render (facing down). */
    public void render(SpriteBatch batch, float centerX, float bottomY,
                       float scaleX, float scaleY) {
        render(batch, centerX, bottomY, scaleX, scaleY, 0f, false, AsciiFacing.DOWN);
    }

    /** Direction-less animated render (facing down). */
    public void render(SpriteBatch batch, float centerX, float bottomY,
                       float scaleX, float scaleY, float walkPhase, boolean moving) {
        render(batch, centerX, bottomY, scaleX, scaleY, walkPhase, moving, AsciiFacing.DOWN);
    }

    /**
     * Renders the downsampled directional matrix selected by {@code facing}.
     * The side matrix is mirrored for RIGHT; every cell is drawn at its
     * terminal size with a uniform font scale, so the glyphs keep their natural
     * text proportions. No drop-shadow or background ink is emitted.
     */
    public void render(SpriteBatch batch, float centerX, float bottomY,
                       float scaleX, float scaleY, float walkPhase, boolean moving,
                       AsciiFacing facing) {
        AsciiFacing view = facing == null ? AsciiFacing.DOWN : facing;
        Matrix matrix = switch (view) {
            case DOWN -> downMatrix;
            case UP -> upMatrix;
            case LEFT, RIGHT -> sideMatrix;
        };

        BitmapFont.BitmapFontData data = font.getData();
        data.setScale(baseScaleX * scaleX, baseScaleY * scaleY);

        float cellWidth = cellSize * scaleX;
        float cellHeight = cellSize * scaleY;

        int matrixRows = matrix.rows;
        int matrixColumns = matrix.columns;
        float originX = centerX - matrixColumns * cellWidth * 0.5f;
        boolean horizontal = view.isHorizontal();
        float forward = view == AsciiFacing.RIGHT ? 1f
            : (view == AsciiFacing.LEFT ? -1f : 0f);
        int walkFrame = moving ? (int) Math.floor(walkPhase * 8f) : 0;
        boolean legFrameA = (walkFrame & 1) == 0;

        float glyphInset = xInset * scaleX;

        int glyphCount = 0;
        for (int row = 0; row < matrixRows; row++) {
            float rowT = matrixRows <= 1 ? 0f : row / (float) (matrixRows - 1);
            float baseline = bottomY + (matrixRows - row - 1) * cellHeight
                - baselineOffset * scaleY;

            for (int destination = 0; destination < matrixColumns; destination++) {
                int source = spriteColumn(destination, matrixColumns, view);
                Block block = matrix.at(source, row);
                if (block.glyph == EMPTY) {
                    continue;
                }

                char drawGlyph = animatedGlyph(block.glyph, destination, rowT,
                    matrixColumns, horizontal, forward, moving, legFrameA);
                float x = originX + destination * cellWidth + glyphInset;

                font.setColor(block.color);
                font.draw(batch, ASCII_TEXT[drawGlyph], x, baseline);
                glyphCount++;
            }
        }

        font.setColor(Color.WHITE);
        lastGlyphCount = glyphCount;
        lastWalkFrame = walkFrame;
        lastMatrixWidth = matrixColumns;
        lastMatrixHeight = matrixRows;
    }

    // ------------------------------------------------------------------
    // Matrix preparation (construction time only)
    // ------------------------------------------------------------------

    /**
     * Collapses {@link #cellStride} x {@link #cellStride} source cells into one
     * terminal block. The densest source glyph decides the ASCII material; the
     * palette colours of all inked cells are weighted by their density so the
     * block keeps the sprite's colour identity.
     */
    private Matrix downsample(AsciiAsset asset, Bounds bounds) {
        int matrixColumns = ceilDiv(bounds.columns(), cellStride);
        int matrixRows = ceilDiv(bounds.rows(), cellStride);
        Block[][] blocks = new Block[matrixRows][matrixColumns];

        Color average = new Color();
        Color foreground = new Color();

        for (int row = 0; row < matrixRows; row++) {
            for (int col = 0; col < matrixColumns; col++) {
                char densest = EMPTY;
                float densestLevel = -1f;
                int weakCells = 0;
                float weight = 0f;
                float red = 0f;
                float green = 0f;
                float blue = 0f;

                for (int dy = 0; dy < cellStride; dy++) {
                    int sourceRow = bounds.minRow() + row * cellStride + dy;
                    if (sourceRow > bounds.maxRow() || sourceRow >= asset.getHeight()) {
                        break;
                    }
                    for (int dx = 0; dx < cellStride; dx++) {
                        int sourceCol = bounds.minCol() + col * cellStride + dx;
                        if (sourceCol > bounds.maxCol() || sourceCol >= asset.getWidth()) {
                            break;
                        }

                        char glyph = asset.getGlyph(sourceCol, sourceRow);
                        float level = AsciiGlyphStyle.density(glyph);
                        if (level <= 0f) {
                            continue;
                        }
                        if (!keepWeakInk && level < CORE_INK_LEVEL) {
                            // Player core-only mode: ':' is background dither.
                            continue;
                        }
                        if (level < CORE_INK_LEVEL) {
                            weakCells++;
                        }
                        if (level > densestLevel) {
                            densestLevel = level;
                            densest = glyph;
                        }

                        Color color = glyphColors[asset.getPaletteIndex(sourceCol, sourceRow)];
                        red += color.r * level;
                        green += color.g * level;
                        blue += color.b * level;
                        weight += level;
                    }
                }

                if (!blockIsVisible(densestLevel, weakCells, cellStride)) {
                    blocks[row][col] = Block.EMPTY_CELL;
                    continue;
                }

                int globalCol = bounds.minCol() + col * cellStride;
                int globalRow = bounds.minRow() + row * cellStride;
                char drawGlyph = AsciiGlyphStyle.glyphFor(densest, globalCol, globalRow);
                if (drawGlyph == EMPTY) {
                    blocks[row][col] = Block.EMPTY_CELL;
                    continue;
                }

                average.set(red / weight, green / weight, blue / weight, 1f);
                float vertical = matrixRows <= 1 ? 0f : row / (float) (matrixRows - 1);
                if (brightInk) {
                    AsciiGlyphStyle.playerForeground(average, densestLevel, vertical, foreground);
                } else {
                    AsciiGlyphStyle.foreground(average, densestLevel, vertical, foreground);
                }
                blocks[row][col] = new Block(drawGlyph, new Color(foreground), densestLevel);
            }
        }

        return new Matrix(blocks, matrixColumns, matrixRows);
    }

    /**
     * Visibility rule for one downsampled block. Every block carrying any ink
     * is drawn: entity matrices must read as dense, solid contours rather than
     * sparse outlines. Only fully transparent blocks are skipped.
     */
    static boolean blockIsVisible(float densestLevel, int weakCells, int cellStride) {
        return densestLevel > 0f;
    }

    /** Union of the inked bounds so all directions share the same footprint. */
    private static Bounds unionBounds(AsciiAsset down, AsciiAsset up, AsciiAsset side,
                                      boolean keepWeakInk) {
        Bounds first = inkBounds(down, keepWeakInk);
        Bounds second = inkBounds(up, keepWeakInk);
        Bounds third = inkBounds(side, keepWeakInk);
        return new Bounds(
            Math.min(first.minCol(), Math.min(second.minCol(), third.minCol())),
            Math.max(first.maxCol(), Math.max(second.maxCol(), third.maxCol())),
            Math.min(first.minRow(), Math.min(second.minRow(), third.minRow())),
            Math.max(first.maxRow(), Math.max(second.maxRow(), third.maxRow())));
    }

    /** Inclusive bounds of every cell that carries ink; full matrix as fallback. */
    private static Bounds inkBounds(AsciiAsset asset, boolean keepWeakInk) {
        int minCol = asset.getWidth();
        int maxCol = -1;
        int minRow = asset.getHeight();
        int maxRow = -1;

        for (int row = 0; row < asset.getHeight(); row++) {
            for (int col = 0; col < asset.getWidth(); col++) {
                char glyph = asset.getGlyph(col, row);
                boolean ink = keepWeakInk
                    ? AsciiGlyphStyle.isInk(glyph)
                    : isCoreInk(glyph);
                if (!ink) {
                    continue;
                }
                minCol = Math.min(minCol, col);
                maxCol = Math.max(maxCol, col);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
            }
        }

        if (maxCol < 0 || maxRow < 0) {
            return new Bounds(0, asset.getWidth() - 1, 0, asset.getHeight() - 1);
        }
        return new Bounds(minCol, maxCol, minRow, maxRow);
    }

    /** True for solid body ink (light shade and brighter), false for ':'. */
    static boolean isCoreInk(char glyph) {
        return AsciiGlyphStyle.density(glyph) >= CORE_INK_LEVEL;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    /** Mirrors the left-facing side matrix when walking right. */
    static int spriteColumn(int destination, int columns, AsciiFacing facing) {
        return facing == AsciiFacing.RIGHT ? columns - 1 - destination : destination;
    }

    /** Two-frame leg toggle: front/back alternate sides, side leans with travel. */
    static char animatedGlyph(char glyph, int column, float rowT, int columns,
                              boolean horizontal, float forward, boolean moving,
                              boolean legFrameA) {
        if (!moving || rowT <= 0.62f) {
            return glyph;
        }
        if (!horizontal) {
            boolean leftSide = column < columns * 0.5f;
            return (leftSide == legFrameA) ? '/' : '\\';
        }
        boolean leanForward = legFrameA;
        if (forward < 0f) {
            leanForward = !leanForward;
        }
        return leanForward ? '/' : '\\';
    }

    /**
     * True for every source matrix cell that carries ink. This is a source-level
     * check only: {@link #blockIsVisible} may still drop a dither-only block
     * when its coverage is too sparse to be part of the silhouette.
     */
    static boolean isVisibleGlyph(AsciiAsset matrix, int col, int row) {
        return AsciiGlyphStyle.isInk(matrix.getGlyph(col, row));
    }
}




