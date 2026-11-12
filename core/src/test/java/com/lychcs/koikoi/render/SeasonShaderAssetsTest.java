package com.lychcs.koikoi.render;

import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the four-season shader contract and the spatial chunk culling wiring
 * of the overworld renderers.
 */
class SeasonShaderAssetsTest {

    @Test
    void worldAndNatureShadersShareTheSeasonalBlendUniforms() throws IOException {
        String world = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);
        String nature = TestAssetFiles.read(NatureModelRenderer.FRAGMENT_SHADER);

        for (String shader : new String[]{world, nature}) {
            assertTrue(shader.contains(SeasonShaderUniforms.UNIFORM_HUB),
                "the shaders must receive the hub centre");
            assertTrue(shader.contains(SeasonShaderUniforms.UNIFORM_START),
                "the shaders must receive the transition start");
            assertTrue(shader.contains(SeasonShaderUniforms.UNIFORM_WIDTH),
                "the shaders must receive the transition width");
            for (String uniform : new String[]{
                SeasonShaderUniforms.UNIFORM_SPRING_GROUND,
                SeasonShaderUniforms.UNIFORM_SUMMER_GROUND,
                SeasonShaderUniforms.UNIFORM_FALL_GROUND,
                SeasonShaderUniforms.UNIFORM_WINTER_GROUND,
                SeasonShaderUniforms.UNIFORM_SPRING_FOLIAGE,
                SeasonShaderUniforms.UNIFORM_SUMMER_FOLIAGE,
                SeasonShaderUniforms.UNIFORM_FALL_FOLIAGE,
                SeasonShaderUniforms.UNIFORM_WINTER_FOLIAGE}) {
                assertTrue(shader.contains(uniform),
                    "seasonal palette uniform missing: " + uniform);
            }
            assertTrue(shader.contains("seasonWeights("),
                "the GPU must recompute the smooth cardinal weights per fragment");
            assertTrue(shader.contains("smoothstep(u_seasonStart, edge"),
                "the transition zone must be a smoothstep, not a hard switch");
        }

        assertTrue(nature.contains("frost"),
            "winter must frost the pine needles in the nature shader");
        assertTrue(nature.contains("foliageSeason"),
            "nature foliage must blend across the realm boundaries");
    }

    @Test
    void javaUploadsSeasonPaletteAndChunkCullsTheWorld() throws IOException {
        Path root = TestAssetFiles.assetRoot().getParent();
        String terrain = Files.readString(root.resolve(
            "core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java"),
            StandardCharsets.UTF_8);
        String nature = Files.readString(root.resolve(
            "core/src/main/java/com/lychcs/koikoi/render/NatureModelRenderer.java"),
            StandardCharsets.UTF_8);
        String scenery = Files.readString(root.resolve(
            "core/src/main/java/com/lychcs/koikoi/render/JapaneseScenery.java"),
            StandardCharsets.UTF_8);
        String screen = Files.readString(root.resolve(
            "core/src/main/java/com/lychcs/koikoi/ui/OverworldScreen.java"),
            StandardCharsets.UTF_8);

        assertTrue(terrain.contains("SeasonShaderUniforms.upload(shader, seasonMap)"),
            "the terrain pass must upload the seasonal blend uniforms");
        assertTrue(terrain.contains("renderTerrain("),
            "terrain must be drawn through the chunk renderer");
        assertTrue(terrain.contains("terrainChunkGrid"),
            "terrain meshes must be binned in 32-tile chunks");
        assertTrue(terrain.contains("scenery.render(shader, camera)"),
            "scenery must receive the camera for chunk culling");

        assertTrue(nature.contains("selectVisible(camera)"),
            "nature ModelInstance batches must be culled per frame");
        assertTrue(nature.contains("CHUNK_CULL_RADIUS"),
            "nature culling must use the spatial chunk radius");
        assertTrue(nature.contains("boundsInFrustum"),
            "nature chunk culling must respect the camera frustum");
        assertTrue(nature.contains("SeasonShaderUniforms.upload(program, lighting.seasonMap)"),
            "the nature shader must receive the same seasonal uniforms");

        assertTrue(scenery.contains("chunkMeshes"),
            "static scenery must be split into chunk meshes");
        assertTrue(scenery.contains("chunkGrid.isVisible"),
            "static scenery chunks must be frustum/distance culled");

        assertTrue(screen.contains("WorldSeasonMap.createHubWorld(WORLD_TILES)"),
            "the overworld must load the generated 4-season hub world");
        assertTrue(screen.contains("updateCollisionChunkCulling()"),
            "Box2D bodies must be activated per spatial chunk");
        assertTrue(screen.contains("body.setActive") || screen.contains(".setActive(visible)"),
            "chunk culling must toggle Box2D body activation");
        assertTrue(screen.contains("createCompassBanner()"),
            "the HUD needs the compass / region banner");
        assertTrue(screen.contains("regionAtWorld("),
            "the banner must read the cardinal season at the player position");
    }
}
