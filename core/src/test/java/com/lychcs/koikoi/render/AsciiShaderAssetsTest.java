package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.Color;
import com.lychcs.koikoi.TestAssetFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Mokuhanga/Sumi-e pipeline without a GL context.
 *
 * <p>Solid world geometry is opaque, the post pass contains no ASCII glyph
 * quantization, and magic is rendered through a dedicated 3D glyph mesh with
 * its own shader. Scenery can therefore never acquire a false character grid.</p>
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
            "u_resolution", "u_time", "u_grain", "u_bloomRadius",
            "u_warmInk", "u_edgeTexture", "u_edgeEnabled", "u_outlineInk",
            "u_far", "u_outlineFadeStart", "u_outlineFadeEnd"}) {
            assertTrue(fragment.contains("uniform") && fragment.contains(uniform),
                "ascii_post.frag must declare " + uniform
                    + " because AsciiPostProcessor writes it");
        }
    }

    @Test
    void magicGlyphShadersExist() throws IOException {
        String vertex = TestAssetFiles.read(HeightmapTerrainRenderer.MAGIC_VERTEX_SHADER);
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.MAGIC_FRAGMENT_SHADER);

        assertTrue(vertex.contains("u_projView"));
        assertTrue(vertex.contains("a_texCoord0"));
        assertTrue(fragment.contains("uniform sampler2D u_glyphAtlas"));
        assertTrue(fragment.contains("texture2D(u_glyphAtlas"));
        assertTrue(fragment.contains("discard"),
            "transparent atlas pixels must discard, not blend a magic alpha");
        assertTrue(fragment.contains("gl_FragColor = vec4(ink, 1.0)"),
            "magic glyph pixels are opaque and untagged");
    }

    @Test
    void magicGlyphLayerIsSeparateFromThePostPass() throws IOException {
        String post = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/AsciiPostProcessor.java");
        String renderer = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java");

        assertFalse(post.contains("u_glyphAtlas"),
            "the post pass must not sample the glyph atlas");
        assertFalse(post.contains("magicTag"),
            "the post pass must not contain an alpha magic tag");
        assertTrue(post.contains("fbo.getColorBufferTexture().setFilter("),
            "the scene FBO must be nearest-filtered");

        assertTrue(renderer.contains("MAGIC_VERTEX_SHADER"),
            "magic glyphs need a dedicated 3D shader");
        assertTrue(renderer.contains("AsciiGlyphAtlas.createTexture()"),
            "the renderer owns the direct magic glyph atlas");
        assertTrue(renderer.contains("magicMesh"),
            "magic glyphs must use their own mesh, not the solid world mesh");
    }

    @Test
    void worldShaderBuildsMokuhangaLighting() throws IOException {
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        assertTrue(fragment.contains("shadowStep") && fragment.contains("midStep")
            && fragment.contains("highStep"), "three quantized moonlight bands are required");
        assertTrue(fragment.contains("SKY_AMBIENT"),
            "cool indigo sky light must feed the hemisphere ambient");
        assertTrue(fragment.contains("GROUND_BOUNCE"),
            "warm moss bounce must light the lower hemisphere");
        assertTrue(fragment.contains("LANTERN_TINT"),
            "lanterns keep their warm golden pools");
        assertTrue(fragment.contains("KINTSUGI_GOLD"),
            "silhouettes near lanterns need a tarnished-gold rim accent");
        assertTrue(fragment.contains("SUMI_INK"),
            "moonlit silhouettes need deep sumi ink");
        assertTrue(fragment.contains("u_time"),
            "the grass wind ripple needs a slow time uniform");
    }

    @Test
    void worldShaderAddsWoodblockHatchingAndSlateStrata() throws IOException {
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        assertTrue(fragment.contains("hatchPhase") && fragment.contains("hatchLine"),
            "directional woodblock hatching must be world-space and crisp");
        assertTrue(fragment.contains("hatchZone"),
            "hatching belongs to mid-tone and deep-shadow zones");
        assertTrue(fragment.contains("strata") && fragment.contains("fineStrata"),
            "cliffs need layered horizontal slate strata");
        assertTrue(fragment.contains("crevice"),
            "steep rock needs moss crevices");
        assertTrue(fragment.contains("sin(hatchPhase)"),
            "hatching must be a parallel sine stroke pattern");
    }

    @Test
    void worldShaderHasOrganicVariationAndSmoothFog() throws IOException {
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        assertTrue(fragment.contains("valueNoise"), "moss/stone mottling needs value noise");
        assertTrue(fragment.contains("grass") && fragment.contains("ripple"),
            "grass wind-ripple gradients must vary the ground");
        assertTrue(fragment.contains("moss") && fragment.contains("wood"),
            "mossy stone and cedar warmth must tint surfaces");
        assertTrue(fragment.contains("1.0 - exp("),
            "valley/horizon fog must use a smooth exponential falloff");
        assertTrue(fragment.contains("color = max(color, palette * 0.24"),
            "shadow sides must stay readable, not crushed black");
    }

    @Test
    void worldShadersUseOnlyOpaqueAlphaAndNoMagicTag() throws IOException {
        String fragment = TestAssetFiles.read(HeightmapTerrainRenderer.FRAGMENT_SHADER);

        assertTrue(fragment.contains("gl_FragColor = vec4(color, 1.0);"),
            "solid world geometry must force alpha 1.0 after lighting and fog");
        assertFalse(fragment.contains("MAGIC_ALPHA"),
            "the world shader must not contain any magic alpha tag");
        assertFalse(fragment.contains("v_color.a"),
            "world shading must never read or write a fractional vertex alpha");
        assertFalse(fragment.contains("gl_FragColor = vec4(color, v_color.a)"));
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
    void postShaderKeepsTheWorldCrispAndHasNoGlyphQuantization() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        assertTrue(fragment.contains("vec3 color = scene;"),
            "the post pass must start from the native-resolution scene");
        assertFalse(fragment.contains("u_glyphAtlas"),
            "the post pass must not contain the ASCII glyph atlas");
        assertFalse(fragment.contains("sampleCell"),
            "there is no full-screen/cell ASCII quantization any more");
        assertFalse(fragment.contains("magicTag") || fragment.contains("magicMask"),
            "there is no alpha-tag magic detection in the post pass");
        assertFalse(fragment.contains("hotInk"),
            "magic ink classification belongs to the direct glyph layer");
        assertFalse(fragment.contains("u_previous"),
            "the temporal accumulation buffer must stay removed");
    }

    @Test
    void postShaderAddsBloomGrainAndDistanceInkOutlines() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        assertTrue(fragment.contains("bloomAround"),
            "soft warm bloom over bright sources is required");
        assertTrue(fragment.contains("smoothstep(0.68, 0.97, luma(color))"),
            "the bloom threshold must sit above the player's cream top");
        assertTrue(fragment.contains("color += bloomTint * 0.30;"),
            "bloom intensity must stay restrained (~50% of the old value)");
        assertTrue(fragment.contains("mix(0.55, 1.0, saturation)"),
            "desaturated cream/white highlights must bloom less than colored magic");
        assertTrue(fragment.contains("u_grain"), "Washi grain strength must be a uniform");
        assertTrue(fragment.contains("valueNoise(gl_FragCoord.xy"),
            "the paper grain must be stationary in screen space");
        assertTrue(fragment.contains("u_outlineFadeStart")
            && fragment.contains("u_outlineFadeEnd")
            && fragment.contains("distanceFade"),
            "ink-wash outlines must attenuate with distance");
        assertTrue(fragment.contains("u_outlineInk"));
    }

    @Test
    void postShaderDrawsASmoothMoonAndCloudSky() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        for (String uniform : new String[]{
            "u_moonDir", "u_skyForward", "u_skyRight", "u_skyUp",
            "u_moonInk", "u_cloudInk", "u_moonCosOuter", "u_cloudDriftX"}) {
            assertTrue(fragment.contains(uniform),
                "ascii_post.frag must declare " + uniform);
        }
        assertTrue(fragment.contains("clouds") && fragment.contains("moon"),
            "the sky must render a smooth moon and cloud wash");
        assertFalse(fragment.contains("waveMask"));
        assertFalse(fragment.contains("dotCellMask"));
        assertFalse(fragment.contains("ringInk"));
    }

    @Test
    void postShaderDrawsInkOutlinesFromDepthAndNormals() throws IOException {
        String fragment = TestAssetFiles.read(AsciiPostProcessor.FRAGMENT_SHADER);

        for (String token : new String[]{
            "u_edgeTexture", "edgeStrength", "outlineMask", "linearDepth", "viewNormal"}) {
            assertTrue(fragment.contains(token),
                "ascii_post.frag must derive the outline from " + token);
        }
    }

    @Test
    void edgeShaderIsSolidGeometryOnly() throws IOException {
        String edge = TestAssetFiles.read(HeightmapTerrainRenderer.EDGE_FRAGMENT_SHADER);

        assertTrue(edge.contains("u_view"), "the edge pass needs the view matrix");
        assertTrue(edge.contains("u_far"), "the edge pass packs linear depth");
        assertTrue(edge.contains("v_normal"), "the edge pass stores normals");
        assertTrue(edge.contains("gl_FragColor"), "the edge pass writes a texture");
        assertFalse(edge.contains("v_color"),
            "magic glyphs use their own mesh and never enter the outline prepass");
        assertFalse(edge.contains("discard"));
    }

    @Test
    void processorConstantsMatchTheAtmosphericPipeline() {
        assertEquals(0.06f, AsciiPostProcessor.GRAIN_STRENGTH, 0.0001f,
            "Washi grain stays at ~6%");
        assertEquals(10f, AsciiPostProcessor.BLOOM_RADIUS_PIXELS, 0.0001f);
        assertEquals(26f, AsciiPostProcessor.OUTLINE_FADE_START, 0.0001f);
        assertEquals(110f, AsciiPostProcessor.OUTLINE_FADE_END, 0.0001f);
    }

    @Test
    void processorInkTiersReserveSumiAndKintsugi() {
        assertTrue(luma(AsciiPostProcessor.BACKGROUND) < 0.06f,
            "background stays deep midnight indigo (#0B0E1D)");
        assertTrue(luma(AsciiPostProcessor.WARM_INK) > 0.7f,
            "lantern gold must read as a warm highlight");
        assertTrue(luma(AsciiPostProcessor.OUTLINE_INK) < 0.10f,
            "ink-wash outlines stay deep indigo/charcoal");
    }

    @Test
    void sceneFramebufferIsNearestFilteredToPreventFalseMagic() throws IOException {
        String source = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/AsciiPostProcessor.java");

        assertTrue(source.contains("fbo.getColorBufferTexture().setFilter("),
            "the scene FBO must not linearly interpolate silhouette alpha");
        assertTrue(source.contains("Texture.TextureFilter.Nearest"));
    }

    @Test
    void staticSceneryNeverCarriesTheMagicAlpha() throws IOException {
        String source = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/JapaneseScenery.java");

        assertFalse(source.contains("MAGIC_ALPHA"),
            "torii, fences, temple and trees are opaque world geometry");
        assertTrue(source.contains("opaqueColor("),
            "scenery colours must use the opaque alpha-1 packer");
    }

    @Test
    void solidWorldRenderingDisablesBlending() throws IOException {
        String source = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java");

        assertTrue(countOccurrences(source, "Gdx.gl.glDisable(GL20.GL_BLEND)") >= 2,
            "both the colour pass and the edge pass must disable blending");
    }

    @Test
    void kamonSealShaderDrawsProceduralPolarCrestGeometry() throws IOException {
        String shader = TestAssetFiles.read(HeightmapTerrainRenderer.KAMON_FRAGMENT_SHADER);

        assertTrue(shader.contains("atan("), "the crest must use polar coordinates");
        assertTrue(shader.contains("length("), "the crest needs a radial distance");
        assertTrue(shader.contains("fract("), "radial ticks/spokes need angular repetition");
        assertTrue(shader.contains("cos(a * 5.0)"),
            "the sakura crest needs five petals");
        assertTrue(shader.contains("* 8.0"),
            "the wheel crest needs eight spokes");
        assertTrue(shader.contains("24.0"),
            "the outer ring needs crisp radial tick marks");
        assertTrue(shader.contains("u_time"),
            "the crest needs a gentle rotational pulse");
        assertTrue(shader.contains("v_color.a"),
            "vertex alpha carries the per-seal phase offset");
        assertTrue(shader.contains("discard"),
            "non-crest pixels must be discarded, not alpha-tagged");
    }

    @Test
    void ofudaPaperShaderDrawsRedKanjiOnIvoryParchment() throws IOException {
        String shader = TestAssetFiles.read(HeightmapTerrainRenderer.OFUDA_FRAGMENT_SHADER);

        assertTrue(shader.contains("paper"), "Ofuda strips need parchment paper");
        assertTrue(shader.contains("red"), "Ofuda ink must be vermillion red");
        assertTrue(shader.contains("sumi"), "the paper edge needs a sumi border");
        assertTrue(shader.contains("segment("),
            "the kanji must be drawn as procedural ink strokes");
        assertTrue(shader.contains("vec3(0.95, 0.90, 0.76)"),
            "the parchment base must stay warm ivory");
    }

    @Test
    void hanafudaCardShaderHasInkBorderMotifAndSignature() throws IOException {
        String shader = TestAssetFiles.read(HeightmapTerrainRenderer.CARD_FRAGMENT_SHADER);

        assertTrue(shader.contains("border"), "cards need a crisp sumi border");
        assertTrue(shader.contains("motif"), "cards need an inner motif accent");
        assertTrue(shader.contains("diamond") && shader.contains("wheel"),
            "the inner motif must combine diamond, petal and wheel geometry");
        assertTrue(shader.contains("paper") && shader.contains("sumi"),
            "the card face must read as paper and ink");
        assertTrue(shader.contains("signature"),
            "a small ink signature keeps the card hand-painted");
    }

    @Test
    void inkRibbonShaderTapersAndTexturesTheBrushTrail() throws IOException {
        String shader = TestAssetFiles.read(HeightmapTerrainRenderer.RIBBON_FRAGMENT_SHADER);

        assertTrue(shader.contains("taper"), "the trail must taper like a brush");
        assertTrue(shader.contains("bristle"), "the trail needs visible bristle streaks");
        assertTrue(shader.contains("discard"), "the ribbon must discard its transparent edges");
        assertTrue(shader.contains("v_color.rgb"), "the trail inherits the card archetype ink");
    }

    @Test
    void rendererBatchesKamonOfudaCardsAndRibbonsSeparately() throws IOException {
        String renderer = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java");

        for (String token : new String[]{
            "KAMON_FRAGMENT_SHADER", "OFUDA_FRAGMENT_SHADER",
            "CARD_FRAGMENT_SHADER", "RIBBON_FRAGMENT_SHADER",
            "drawKamonSeal", "drawOfuda", "drawHanafudaCard", "drawInkRibbon",
            "sealBatch", "ofudaBatch", "cardBatch", "ribbonBatch"}) {
            assertTrue(renderer.contains(token),
                "the renderer must own the procedural effect " + token);
        }
    }

    @Test
    void gameplayUsesProceduralSealsOfudaCardsAndRibbons() throws IOException {
        String screen = readJava(
            "core/src/main/java/com/lychcs/koikoi/ui/OverworldScreen.java");
        String avatar = readJava(
            "core/src/main/java/com/lychcs/koikoi/render/PlayerAvatarRenderer.java");

        assertTrue(screen.contains("drawKamonSeal("),
            "Karma zones and the lock-on ring must use Kamon seals");
        assertTrue(screen.contains("drawHanafudaCard("),
            "thrown cards must use the procedural card shader");
        assertTrue(screen.contains("drawInkRibbon("),
            "flying cards need a sumi brush ribbon trail");
        assertTrue(screen.contains("lockOnTarget"), "the lock-on target needs a crest");

        assertTrue(avatar.contains("renderOfudaTalismans"),
            "the Q dash must spawn Ofuda talismans");
        assertTrue(avatar.contains("drawOfuda("),
            "Ofuda talismans must be queued as procedural plates");
        assertTrue(avatar.contains("for (int i = 0; i < 5; i++)"),
            "the dash should scatter five Ofuda strips");

        int cardStart = screen.indexOf("private void drawCard(");
        int cardEnd = screen.indexOf("private void renderCardBursts", cardStart);
        assertTrue(cardStart >= 0 && cardEnd > cardStart);
        String drawCard = screen.substring(cardStart, cardEnd);
        assertFalse(drawCard.contains("drawMagicBox"),
            "cards must no longer use the generic glyph magic plate");
        assertTrue(drawCard.contains("drawHanafudaCard"));
    }

    private static String readJava(String relativePath) throws IOException {
        Path root = TestAssetFiles.assetRoot().getParent();
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    private static float luma(Color color) {
        return 0.2126f * color.r + 0.7152f * color.g + 0.0722f * color.b;
    }
}
