package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.graphics.Color;

/**
 * Terminal-style glyph vocabulary shared by the ASCII asset renderer and the
 * dense terrain layer.
 *
 * <p>The generated matrices use Unicode shade blocks
 * ({@code . : ░ ▒ ▓ █ @}) as a luminance ramp. Rendering those blocks directly
 * makes a sprite look like a solid pixel blob, so this class maps every block
 * onto a readable ASCII punctuation glyph chosen deterministically per cell.
 * A second job is the foreground lift: palette entries are warmed and raised
 * to a minimum luminance so even the faint dither strokes stay legible against
 * the dark moss terrain.</p>
 *
 * <p>Pure data and colour math - no GL and no LibGDX application lifecycle -
 * so the vocabulary can be unit-tested.</p>
 */
public final class AsciiGlyphStyle {

    /** Transparent matrix cell. Never painted. */
    public static final char EMPTY = 0;

    /** Light shade range: sparse ink, kept subtle. */
    private static final char[] LIGHT = {':', '+'};

    /** Medium shade range: blade / dot material. */
    private static final char[] MEDIUM = {'/', '\\', '|', 'o'};

    /** Dense shade range: leaf / spark material. */
    private static final char[] DENSE = {'*', '%', '#', 'Q'};

    /** Near-solid shade range: solid terminal ink. */
    private static final char[] SOLID = {'#', '@'};

    /** Warm parchment highlight mixed into the upper body. */
    private static final Color HIGHLIGHT = new Color(1f, 0.94f, 0.76f, 1f);

    /** Dark moss ink used to keep hue in the deep shade range. */
    private static final Color DEEP_INK = new Color(0.22f, 0.28f, 0.20f, 1f);

    /** Bright parchment used exclusively for the player's ASCII ink. */
    private static final Color PLAYER_PARCHMENT = new Color(1f, 0.95f, 0.78f, 1f);

    /** Near-white player rim that pops against the dark moss matrix. */
    private static final Color PLAYER_WHITE = new Color(1f, 0.99f, 0.90f, 1f);

    /** Warm gold base for low-density player cells. */
    private static final Color PLAYER_GOLD = new Color(1f, 0.86f, 0.44f, 1f);

    private AsciiGlyphStyle() {
    }

    /** 0..1 visual weight of one source matrix glyph. */
    public static float density(char glyph) {
        return switch (glyph) {
            case EMPTY, ' ', '.' -> 0f;
            case ':' -> 0.18f;
            case '\u2591' -> 0.32f; // ░
            case '\u2592' -> 0.52f; // ▒
            case '\u2593' -> 0.74f; // ▓
            case '\u2588' -> 0.90f; // █
            case '@' -> 1f;
            default -> glyph < 128 ? 0.62f : 0f;
        };
    }

    /** True for cells that carry visible ink. {@code '.'} and space stay empty. */
    public static boolean isInk(char glyph) {
        return density(glyph) > 0f;
    }

    /**
     * Maps one source glyph onto a readable ASCII terminal cell.
     *
     * <p>Shade blocks pick from a small material set using a deterministic
     * cell hash, so neighbouring cells vary ({@code / \ | o * % # Q}) instead
     * of repeating one block. Authored ASCII glyphs such as {@code @} are
     * preserved.</p>
     *
     * @return a printable ASCII glyph, or {@link #EMPTY}
     */
    public static char glyphFor(char source, int col, int row) {
        if (!isInk(source)) {
            return EMPTY;
        }
        int hash = hash(col, row);
        return switch (source) {
            case ':' -> (hash & 3) == 0 ? '+' : ':';
            case '\u2591' -> pick(LIGHT, hash);
            case '\u2592' -> pick(MEDIUM, hash);
            case '\u2593' -> pick(DENSE, hash);
            case '\u2588' -> pick(SOLID, hash);
            default -> source < 128 ? source : '*';
        };
    }

    /**
     * High-contrast foreground tint for one downsampled entity block.
     *
     * <p>The source palette colour keeps the sprite's identity, then dark
     * entries are lifted toward a readable floor, the strongest glyphs blends
     * toward warm parchment, and the upper body receives a small rim light.
     * The result is written into {@code out} to avoid per-glyph garbage.</p>
     *
     * @param source    averaged palette colour of the block (earth-toned)
     * @param level     block density in {@code [0, 1]}
     * @param verticalT {@code 0} at the top of the matrix, {@code 1} at the feet
     * @param out       destination colour
     */
    public static Color foreground(Color source, float level, float verticalT, Color out) {
        float weight = clamp01(level);
        float vertical = clamp01(verticalT);

        out.set(source);
        // Deep, low-density straps lean toward moss ink; dense body cells keep
        // their palette colour for entity recognition.
        out.lerp(DEEP_INK, 0.16f * (1f - weight));

        float minLuma = 0.27f + 0.62f * weight + 0.12f * (1f - vertical);
        float luma = 0.299f * out.r + 0.587f * out.g + 0.114f * out.b;
        if (luma < minLuma) {
            float lift = minLuma - luma;
            out.r = Math.min(1f, out.r + lift * 1.05f);
            out.g = Math.min(1f, out.g + lift * 0.95f);
            out.b = Math.min(1f, out.b + lift * 0.68f);
        }

        // Upper body and solid cells catch the lantern: a restrained warm rim.
        float rim = 0.10f + 0.36f * weight * (1f - vertical);
        out.lerp(HIGHLIGHT, Math.min(0.55f, rim));

        out.r = clamp01(out.r);
        out.g = clamp01(out.g);
        out.b = clamp01(out.b);
        out.a = 1f;
        return out;
    }

    /**
     * High-luminance parchment/white ink used only by the player avatar.
     * It deliberately ignores the moss palette so the Wanderer remains the
     * brightest object on the dark terrain matrix.
     */
    public static Color playerForeground(Color source, float level, float verticalT, Color out) {
        float weight = clamp01(level);
        float vertical = clamp01(verticalT);

        out.set(source);
        out.lerp(PLAYER_GOLD, 0.20f * (1f - weight));
        out.lerp(PLAYER_PARCHMENT, 0.72f + 0.18f * weight);

        float minLuma = 0.78f + 0.14f * weight + 0.05f * (1f - vertical);
        float luma = 0.299f * out.r + 0.587f * out.g + 0.114f * out.b;
        if (luma < minLuma) {
            float lift = minLuma - luma;
            out.r = Math.min(1f, out.r + lift * 1.02f);
            out.g = Math.min(1f, out.g + lift * 0.98f);
            out.b = Math.min(1f, out.b + lift * 0.80f);
        }

        float rim = 0.18f + 0.35f * weight * (1f - vertical);
        out.lerp(PLAYER_WHITE, Math.min(0.65f, rim));

        out.r = clamp01(out.r);
        out.g = clamp01(out.g);
        out.b = clamp01(out.b);
        out.a = 1f;
        return out;
    }

    /** Luminance helper shared by tests and colour diagnostics. */
    public static float luma(Color color) {
        return 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
    }

    private static char pick(char[] choices, int hash) {
        return choices[Math.floorMod(hash, choices.length)];
    }

    private static int hash(int col, int row) {
        int value = col * 0x1f1f1f1f ^ row * 0x45d9f3b;
        value ^= value >>> 16;
        value *= 0x45d9f3b;
        value ^= value >>> 16;
        return value;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
