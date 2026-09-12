package com.lychcs.koikoi.lwjgl3.view;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class AssetPacker {
    public static void main(String[] args) {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 4096;
        settings.maxHeight = 4096;
        settings.stripWhitespaceX = false;
        settings.stripWhitespaceY = false;
        settings.filterMin = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest;
        settings.filterMag = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest;

        settings.flattenPaths = true;

        System.out.println("Packe Texturen aus assets_solo...");
        TexturePacker.process(settings, "assets_solo", "assets/packed", "game_assets");
        System.out.println("Packen abgeschlossen!");
    }
}
