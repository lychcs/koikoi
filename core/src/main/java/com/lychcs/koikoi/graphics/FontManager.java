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

    public static void initialize() {
        // Ersetze den Dateinamen mit dem genauen Namen deiner .ttf-Datei in assets/fonts/
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/font.ttf"));

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

        // Der Generator wird danach nicht mehr gebraucht und muss freigegeben werden
        generator.dispose();
    }

    public static BitmapFont getFont() {
        return pixelFont;
    }

    public static BitmapFont getTitleFont() {
        return titleFont;
    }

    public static void dispose() {
        if (pixelFont != null) pixelFont.dispose();
        if (titleFont != null) titleFont.dispose();
    }
}
