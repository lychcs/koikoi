package com.lychcs.koikoi.render;

import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ASCII shader pipeline assets without needing a GL context: every
 * shader path used by the renderers must ship, and the uniforms the Java code
 * writes must be declared by the GLSL sources.
 */
class AsciiShaderAssetsTest {

    @Test
    void postProcessShadersExist() throws IOException {
        String vertex = TestAssetFiles.read(AsciiPostProcessor.VERTEX_SHADER);
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        assertTrue(vertex.contains("u_projTrans"), "the SpriteBatch quad needs u_projTrans");
        assertTrue(vertex.contains("a_position"));
        assertTrue(fragment.contains("u_texture"));
    }

    @Test
    void postProcessDeclaresEveryUniformTheProcessorSets() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        for (String uniform : new String[]{
            "u_resolution", "u_cellSize", "u_time", "u_ink"}) {
            assertTrue(fragment.contains("uniform") && fragment.contains(uniform),
                "ascii_post.frag must declare " + uniform
                    + " because AsciiPostProcessor writes it");
        }
    }

    @Test
    void postProcessStampsADensityGlyphRamp() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        assertTrue(fragment.contains("glyphMask"), "the shader must build glyph masks");
        assertTrue(fragment.contains("fract(cell)"), "glyphs must live inside one cell");
        assertTrue(fragment.contains("luma("), "glyph density comes from scene luminance");
    }

    @Test
    void worldShadersExistAndMatchTheMeshAttributes() throws IOException {
        String vertex = TestAssetFiles.read(HeightmapTerrainRenderer.VERTEX_SHADER);
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        assertTrue(vertex.contains("a_position"));
        assertTrue(vertex.contains("a_normal"));
        assertTrue(vertex.contains("a_color"));
        assertTrue(vertex.contains("u_projView"), "the terrain mesh needs the camera matrix");

        assertTrue(fragment.contains("u_lightDir"));
        assertTrue(fragment.contains("u_fogStart"));
        assertTrue(fragment.contains("u_cameraPos"));
    }

    @Test
    void asciiInkStaysMossGreen() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        // The terminal background is dark and the ink is a muted green.
        assertTrue(fragment.contains("vec3 background"));
        assertTrue(fragment.contains("0.010") && fragment.contains("0.026"));
        assertTrue(fragment.contains("u_ink"), "ink colour is a uniform set by the screen");
    }

    @Test
    void worldShaderSupportsWarmPointLightsAndHeightFog() throws IOException {
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        for (String uniform : new String[]{
            "u_lightCount", "u_lightPos", "u_lightColor", "u_lightRange",
            "u_fogBaseHeight", "u_fogHeightSpan", "u_valleyFogColor"}) {
            assertTrue(fragment.contains(uniform),
                "asus_world.frag must declare " + uniform);
        }
        assertTrue(fragment.contains("MAX_LIGHTS"));
    }

    @Test
    void postShaderDrawsTheAsciiMoonAndCloudGlyphs() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        for (String uniform : new String[]{
            "u_moonDir", "u_skyForward", "u_skyRight", "u_skyUp",
            "u_moonInk", "u_cloudInk", "u_warmInk", "u_moonCosOuter"}) {
            assertTrue(fragment.contains(uniform),
                "ascii_post.frag must declare " + uniform);
        }
        assertTrue(fragment.contains("waveMask"), "clouds need '~' glyphs");
        assertTrue(fragment.contains("dashMask"), "clouds need '-' glyphs");
        assertTrue(fragment.contains("dotCellMask"), "clouds need '.' glyphs");
        assertTrue(fragment.contains("skyMask"), "the sky must be masked from geometry");
    }

}
