package com.lychcs.koikoi.render;

import com.lychcs.koikoi.TestAssetFiles;
import com.lychcs.koikoi.graphics.GameAssets;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the six locally generated triplanar world textures and the shader
 * contract that consumes them.
 */
class WorldTextureAssetsTest {

    private static final String[] EXPECTED = {
        "grass.png", "cliff_stone.png", "torii_wood.png",
        "roof_tiles.png", "stone_path.png", "sakura_leaves.png"
    };

    @Test
    void sixSeamlessTexturesExistAt64x64() throws IOException {
        assertEquals(EXPECTED.length, GameAssets.WORLD_TEXTURES.length);
        Path textureDir = TestAssetFiles.assetRoot().resolve("textures");
        for (int i = 0; i < EXPECTED.length; i++) {
            Path file = textureDir.resolve(EXPECTED[i]);
            assertTrue(Files.isRegularFile(file), "missing world texture " + file);
            assertTrue(GameAssets.WORLD_TEXTURES[i].endsWith(EXPECTED[i]),
                "GameAssets material order must match shader order: " + EXPECTED[i]);

            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(64, image.getWidth(), file + " width");
            assertEquals(64, image.getHeight(), file + " height");

            Set<Integer> colours = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    colours.add(image.getRGB(x, y));
                }
            }
            assertTrue(colours.size() >= 8,
                file + " must contain real pixel-art variation, not a flat fill");
        }
    }

    @Test
    void shaderDeclaresTriplanarMaterialsAndTitlePalette() throws IOException {
        String vertex = TestAssetFiles.read(HeightmapTerrainRenderer.VERTEX_SHADER);
        String world = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);
        String post = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        assertTrue(vertex.contains("a_material"), "world vertices need a material id");
        for (String uniform : new String[]{
            "u_grassTexture", "u_cliffTexture", "u_toriiTexture",
            "u_roofTexture", "u_pathTexture", "u_sakuraTexture"}) {
            assertTrue(world.contains(uniform), "ascii_world.frag must sample " + uniform);
        }
        assertTrue(world.contains("surfaceTexture"), "triplanar material selection required");
        assertTrue(world.contains("u_textureWorldScale"), "world-space UV scale required");
        assertTrue(world.contains("0.043, 0.055, 0.114"), "#0B0E1D midnight indigo");
        assertTrue(world.contains("0.949, 0.757, 0.306"), "#F2C14E lantern gold");
        assertTrue(post.contains("TWILIGHT_VIOLET"), "post sky needs #3B1E54 twilight violet");
        assertTrue(post.contains("LANTERN_GOLD"), "post sky needs warm lantern gold");
    }

    @Test
    void javaLoadsWorldTexturesWithNearestAndRepeat() throws IOException {
        String assets = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/graphics/GameAssets.java"));
        String renderer = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java"));

        assertTrue(assets.contains("TextureWrap.Repeat"),
            "triplanar textures must repeat seamlessly");
        assertTrue(assets.contains("Texture.TextureFilter.Nearest"),
            "world textures must stay pixel sharp");
        assertTrue(renderer.contains("worldTextures[i].bind(i)"),
            "renderer must upload all six material textures");
        assertTrue(renderer.contains("u_textureWorldScale"),
            "renderer must set the triplanar UV scale");
        assertTrue(renderer.contains("0.86f"),
            "finer grass tiling keeps fallen petals delicate");
    }
}
