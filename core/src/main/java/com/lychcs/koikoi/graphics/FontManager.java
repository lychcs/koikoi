package com.lychcs.koikoi.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public final class FontManager {

    private static BitmapFont pixelFont;
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

        // Ersetze den Dateinamen mit dem genauen Namen deiner .ttf-Datei in assets/fonts/
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.ttf"));

        try {
            // 1. Standard-Schrift für Buttons, Beschreibungen & UI (z. B. 16 px)
            FreeTypeFontParameter paramNormal = new FreeTypeFontParameter();
            paramNormal.size = 16;
            paramNormal.minFilter = Texture.TextureFilter.Nearest;
            paramNormal.magFilter = Texture.TextureFilter.Nearest;
            pixelFont = generator.generateFont(paramNormal);

            // 2. Größere Schrift für Titel, Boss-Namen & Sieges-Anzeigen (z. B. 24 px)
            FreeTypeFontParameter paramTitle = new FreeTypeFontParameter();
            paramTitle.size = 24;
            paramTitle.minFilter = Texture.TextureFilter.Nearest;
            paramTitle.magFilter = Texture.TextureFilter.Nearest;
            titleFont = generator.generateFont(paramTitle);
        } finally {
            // Der Generator wird danach nicht mehr gebraucht und muss freigegeben werden
            generator.dispose();
        }
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
        if (titleFont != null) {
            titleFont.dispose();
            titleFont = null;
        }
    }
}
