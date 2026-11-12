package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * The terminal glyph atlas used by the screen-space ASCII pass.
 *
 * <p>Sixteen glyphs in density order, from empty space to solid {@code @}, are
 * rasterised into one 7x11 monospace strip. {@link HeightmapTerrainRenderer}
 * uses this atlas for the direct 3D magic glyph layer: billboards, cards, rune
 * seals, particles and dash ghosts are drawn as glyph-textured quads. No
 * full-screen or post-process quantization ever touches the world.</p>
 *
 * <p>All mask data is pure Java and unit-testable; {@link #createTexture()} is
 * the only GL-facing helper and is called by {@link AsciiPostProcessor}.</p>
 */
public final class AsciiGlyphAtlas {

    /** Width of one glyph cell in the atlas and at the 720p reference size. */
    public static final int CELL_WIDTH = 7;

    /** Height of one glyph cell in the atlas and at the 720p reference size. */
    public static final int CELL_HEIGHT = 11;

    /** Glyphs ordered by ink coverage: space, . , ' " : ; - / \ + o m M # @. */
    public static final char[] GLYPHS = {
        ' ', '.', ',', '\'', '"', ':', ';', '-',
        '/', '\\', '+', 'o', 'm', 'M', '#', '@'
    };

    /** '#' marks an opaque glyph pixel, '.' an empty one. */
    private static final String[][] MASKS = {
        { // space
            ".......", ".......", ".......", ".......", ".......",
            ".......", ".......", ".......", ".......", ".......", "......."
        },
        { // .
            ".......", ".......", ".......", ".......", ".......",
            ".......", ".......", ".......", ".......", "...##..", "...##.."
        },
        { // ,
            ".......", ".......", ".......", ".......", ".......",
            ".......", ".......", ".......", "...##..", "...##..", "..#...."
        },
        { // '
            "...##..", "...##..", "...##..", ".......", ".......",
            ".......", ".......", ".......", ".......", ".......", "......."
        },
        { // "
            ".#...#.", ".#...#.", ".#...#.", ".#...#.", ".......",
            ".......", ".......", ".......", ".......", ".......", "......."
        },
        { // :
            ".......", ".......", "...##..", "...##..", ".......",
            ".......", "...##..", "...##..", ".......", ".......", "......."
        },
        { // ;
            ".......", ".......", "...##..", "...##..", ".......",
            ".......", "...##..", "...##..", "..#....", ".......", "......."
        },
        { // -
            ".......", ".......", ".......", ".......", ".......",
            ".#####.", ".#####.", ".......", ".......", ".......", "......."
        },
        { // /
            "......#", ".....##", ".....#.", "....##.", "...##..",
            "..##...", ".##....", ".#.....", "#......", ".......", "......."
        },
        { // \
            "#......", "##.....", ".#.....", ".##....", "..##...",
            "...##..", "....##.", ".....#.", "......#", ".......", "......."
        },
        { // +
            ".......", ".......", "...##..", "...##..", "...##..",
            ".#####.", ".#####.", "...##..", "...##..", ".......", "......."
        },
        { // o
            ".......", "..###..", ".##.##.", "##...##", "##...##",
            "##...##", ".##.##.", "..###..", ".......", ".......", "......."
        },
        { // m
            ".......", ".#.#.#.", "##.#.##", "##.#.##", "##.#.##",
            "##.#.##", "##.#.##", "##.#.##", ".......", ".......", "......."
        },
        { // M
            "##...##", "###.###", "##.#.##", "##.#.##", "##...##",
            "##...##", "##...##", "##...##", "##...##", ".......", "......."
        },
        { // #
            ".......", ".##.##.", ".##.##.", "#######", "#######",
            ".##.##.", "#######", "#######", ".##.##.", ".##.##.", "......."
        },
        { // @
            ".#####.", "#######", "##...##", "##.#.##", "##.#.##",
            "##...##", "##....#", "#######", ".#####.", "....##.", "...###."
        }
    };

    private AsciiGlyphAtlas() {
    }

    static {
        for (int glyph = 0; glyph < MASKS.length; glyph++) {
            if (MASKS[glyph].length != CELL_HEIGHT) {
                throw new IllegalStateException(
                    "glyph " + glyph + " must have " + CELL_HEIGHT + " rows");
            }
            for (String row : MASKS[glyph]) {
                if (row.length() != CELL_WIDTH) {
                    throw new IllegalStateException(
                        "glyph " + glyph + " row must be " + CELL_WIDTH + " wide");
                }
            }
        }
    }

    public static int glyphCount() {
        return GLYPHS.length;
    }

    public static char glyphAt(int index) {
        return GLYPHS[clampIndex(index)];
    }

    /** Number of opaque pixels of one glyph. */
    public static int inkPixels(int index) {
        int ink = 0;
        String[] mask = MASKS[clampIndex(index)];
        for (String row : mask) {
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') {
                    ink++;
                }
            }
        }
        return ink;
    }

    public static boolean isInk(int index, int x, int y) {
        if (x < 0 || x >= CELL_WIDTH || y < 0 || y >= CELL_HEIGHT) {
            return false;
        }
        return MASKS[clampIndex(index)][y].charAt(x) == '#';
    }

    /** Maps a 0..1 cell tone onto the density-ordered glyph range. */
    public static int indexForTone(float tone) {
        int last = GLYPHS.length - 1;
        int index = Math.round(Math.max(0f, Math.min(1f, tone)) * last);
        return Math.max(0, Math.min(last, index));
    }

    /** Builds the white-on-transparent atlas pixmap (7 * 16 by 11 pixels). */
    public static Pixmap createPixmap() {
        Pixmap pixmap = new Pixmap(
            CELL_WIDTH * GLYPHS.length, CELL_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(1f, 1f, 1f, 1f);
        for (int glyph = 0; glyph < GLYPHS.length; glyph++) {
            for (int y = 0; y < CELL_HEIGHT; y++) {
                for (int x = 0; x < CELL_WIDTH; x++) {
                    if (isInk(glyph, x, y)) {
                        pixmap.drawPixel(glyph * CELL_WIDTH + x, y);
                    }
                }
            }
        }
        return pixmap;
    }

    /** Uploads the atlas as a crisp nearest-filtered texture (caller owns it). */
    public static Texture createTexture() {
        Pixmap pixmap = createPixmap();
        try {
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(GLYPHS.length - 1, index));
    }
}
