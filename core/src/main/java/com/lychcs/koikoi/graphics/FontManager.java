package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public final class FontManager {

    private static BitmapFont pixelFont;
    private static BitmapFont hudFont;
    private static BitmapFont titleFont;

    public static final Color COLOR_TEXT_MAIN = Color.valueOf("000000");
    public static final Color COLOR_TEXT_MUTED = Color.valueOf("A89F91");
    public static final Color COLOR_TEXT_GOLD = Color.valueOf("E5B944");

    private FontManager() {}

    /**
     * Erzeugt die beiden globalen BitmapFonts genau einmal. Weitere Aufrufe sind
     * wirkungslos; der FreeType-Generator wird unmittelbar danach freigegeben.
     */
    public static void initialize() {
        if (pixelFont != null || titleFont != null) {
            return;
        }

        // 1. Standard-Schrift für Buttons, Beschreibungen & UI (z. B. 16 px)
        pixelFont = createFont(16);

        // 2. HUD-Schrift mit einem zusätzlichen Pixel Laufweite, damit die
        //    kleinen Status-/Hinweiszeilen auf 1280x720 nicht zusammenkleben.
        hudFont = createFont(16, 1);

        // 3. Größere Schrift für Titel, Boss-Namen & Sieges-Anzeigen (z. B. 24 px)
        titleFont = createFont(24);
    }

    /**
     * Erzeugt eine neue Pixel-Schrift in der angegebenen Groesse. Der Aufrufer
     * ist Eigentuemer der zurueckgegebenen Schrift und muss sie selbst freigeben.
     * Wird unter anderem fuer die ASCII-Karten-Glyphen der Overworld genutzt,
     * damit deren Skalierung die globalen UI-Schriften nicht beeinflusst.
     *
     * @param size Schriftgroesse in Pixeln (muss > 0 sein)
     */
    public static BitmapFont createFont(int size) {
        return createFont(size, 0);
    }

    /**
     * Creates a pixel font with extra horizontal tracking. Positive
     * {@code extraSpacing} keeps tiny HUD text legible without changing the
     * shared button/title fonts.
     *
     * @param size font size in pixels (must be &gt; 0)
     * @param extraSpacing additional pixels between glyphs (can be negative)
     */
    public static BitmapFont createFont(int size, int extraSpacing) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0 but was " + size);
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.ttf"));
        try {
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = size;
            parameter.spaceX = extraSpacing;
            parameter.minFilter = Texture.TextureFilter.Nearest;
            parameter.magFilter = Texture.TextureFilter.Nearest;
            BitmapFont font = generator.generateFont(parameter);
            // Integer glyph positions + nearest sampling keep small HUD text
            // razor-sharp while Scene2D scales the viewport.
            font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            font.setUseIntegerPositions(true);
            return font;
        } finally {
            // Der Generator wird danach nicht mehr gebraucht und muss freigegeben werden.
            generator.dispose();
        }
    }

    /**
     * Creates a monospace variant of the pixel font for the dense ASCII layers.
     *
     * <p>The generated font forces one advance width for every glyph, so a
     * glyph grid drawn cell-by-cell tiles seamlessly without horizontal gaps.
     * Callers still own the returned font and must dispose it.</p>
     *
     * @param size font size in pixels (must be &gt; 0)
     */
    public static BitmapFont createMonoFont(int size) {
        return createMonoFont("fonts/ascii.ttf", size);
    }

    /**
     * Monospace font from an explicit TTF path. The default overload uses the
     * bold display font ({@code fonts/ascii.ttf}); the thin UI font
     * ({@code fonts/font.ttf}) remains available for callers that need it.
     *
     * @param fontPath internal path of the TTF inside {@code assets/}
     * @param size     font size in pixels (must be &gt; 0)
     */
    public static BitmapFont createMonoFont(String fontPath, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0 but was " + size);
        }
        if (fontPath == null || fontPath.isBlank()) {
            throw new IllegalArgumentException("fontPath must not be blank");
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(fontPath));
        try {
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = size;
            // 1-bit glyphs with crisp edges suit the ASCII grid.
            parameter.mono = true;
            parameter.minFilter = Texture.TextureFilter.Nearest;
            parameter.magFilter = Texture.TextureFilter.Nearest;
            // ASCII + the shade blocks used by the generated ASCII assets and
            // the pale-cyan blossom spark (U+00B0) of the Japanese palette.
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "░▒▓█°";
            BitmapFont font = generator.generateFont(parameter);
            font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            font.setUseIntegerPositions(true);
            enforceMonospace(font.getData());
            return font;
        } finally {
            generator.dispose();
        }
    }

    /**
     * Forces a single advance width on every glyph so grid rendering has no
     * horizontal gaps; {@code spaceXadvance} gets the same width.
     */
    private static void enforceMonospace(BitmapFont.BitmapFontData data) {
        int advance = 0;
        for (BitmapFont.Glyph[] page : data.glyphs) {
            if (page == null) {
                continue;
            }
            for (BitmapFont.Glyph glyph : page) {
                if (glyph != null) {
                    advance = Math.max(advance, glyph.xadvance);
                }
            }
        }
        if (advance <= 0) {
            return;
        }

        for (BitmapFont.Glyph[] page : data.glyphs) {
            if (page == null) {
                continue;
            }
            for (BitmapFont.Glyph glyph : page) {
                if (glyph != null) {
                    glyph.xadvance = advance;
                }
            }
        }
        data.spaceXadvance = advance;
    }

    /**
     * Liefert die globale UI-Schrift.
     *
     * @throws IllegalStateException wenn {@link #initialize()} nicht gelaufen ist
     *         oder die Schrift bereits freigegeben wurde
     */
    public static BitmapFont getFont() {
        if (pixelFont == null) {
            throw new IllegalStateException(
                "FontManager is not initialized (or already disposed): call initialize() first.");
        }
        return pixelFont;
    }

    /**
     * Liefert die HUD-Schrift mit leicht erweiterter Glyphen-Laufweite.
     *
     * @throws IllegalStateException wenn {@link #initialize()} nicht gelaufen ist
     *         oder die Schrift bereits freigegeben wurde
     */
    public static BitmapFont getHudFont() {
        if (hudFont == null) {
            throw new IllegalStateException(
                "FontManager is not initialized (or already disposed): call initialize() first.");
        }
        return hudFont;
    }

    /**
     * Liefert die globale Titelschrift.
     *
     * @throws IllegalStateException wenn {@link #initialize()} nicht gelaufen ist
     *         oder die Schrift bereits freigegeben wurde
     */
    public static BitmapFont getTitleFont() {
        if (titleFont == null) {
            throw new IllegalStateException(
                "FontManager is not initialized (or already disposed): call initialize() first.");
        }
        return titleFont;
    }

    /**
     * Gibt beide globalen Schriften genau einmal frei und setzt die Felder
     * zurueck, damit ein zweiter Aufruf nichts erneut zerstoren kann.
     */
    public static void dispose() {
        if (pixelFont != null) {
            pixelFont.dispose();
            pixelFont = null;
        }
        if (hudFont != null) {
            hudFont.dispose();
            hudFont = null;
        }
        if (titleFont != null) {
            titleFont.dispose();
            titleFont = null;
        }
    }
}
