package com.lychcs.koikoi.render;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the curated OBJ import: self-contained MTLs, alpha-cutout foliage,
 * nearest-filtered textures and the matching ModelBatch shader contract.
 */
class NatureModelAssetsTest {

    @Test
    void everyCuratedObjAndMtlExists() throws IOException {
        assertEquals(10, GameAssets.NATURE_MODELS.length);
        for (String modelPath : GameAssets.NATURE_MODELS) {
            Path obj = TestAssetFiles.assetRoot().resolve(modelPath);
            assertTrue(Files.isRegularFile(obj), "missing nature model " + obj);
            assertTrue(modelPath.endsWith(".obj"), "model must be OBJ: " + modelPath);

            String mtlName = obj.getFileName().toString()
                .replaceFirst("\\.obj$", ".mtl");
            Path mtl = obj.resolveSibling(mtlName);
            assertTrue(Files.isRegularFile(mtl), "missing MTL " + mtl);

            String objText = Files.readString(obj);
            String mtlText = Files.readString(mtl);
            Set<String> used = materialNames(objText, "usemtl ");
            Set<String> declared = materialNames(mtlText, "newmtl ");
            assertEquals(declared, used,
                obj.getFileName() + " materials must be declared in its MTL");
            assertFalse(used.isEmpty(), "every model needs at least one material");
            assertTrue(mtlText.contains("map_Kd textures/"),
                mtl + " must link its painted texture with map_Kd");
        }
    }

    @Test
    void texturesArePixelSizedAndFoliageHasAlphaCutouts() throws IOException {
        for (String texturePath : GameAssets.NATURE_TEXTURES) {
            Path file = TestAssetFiles.assetRoot().resolve(texturePath);
            assertTrue(Files.isRegularFile(file), "missing nature texture " + file);

            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(512, image.getWidth(), file + " width");
            assertEquals(512, image.getHeight(), file + " height");

            if (!texturePath.contains("Leaves.png")) {
                continue;
            }
            assertTrue(image.getColorModel().hasAlpha(),
                "leaf texture must preserve its alpha cutout: " + file);

            Set<Integer> colours = new HashSet<>();
            int transparent = 0;
            int opaque = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    colours.add(argb);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha == 0) {
                        transparent++;
                    } else if (alpha == 255) {
                        opaque++;
                    }
                }
            }
            assertTrue(transparent > 0, "leaf cutout needs transparent texels: " + file);
            assertTrue(opaque > 0, "leaf cutout needs opaque leaves: " + file);
            assertTrue(colours.size() >= 16,
                "leaf texture must retain painted variation: " + file);
        }
    }


    @Test
    void modelShadersUseAlphaCutoutAndMokuhangaLighting() throws IOException {
        String vertex = TestAssetFiles.read(NatureModelRenderer.VERTEX_SHADER);
        String model = TestAssetFiles.read(NatureModelRenderer.FRAGMENT_SHADER);
        String edge = TestAssetFiles.read(NatureModelRenderer.EDGE_FRAGMENT_SHADER);

        assertTrue(vertex.contains("u_worldTrans"), "instances need a world transform");
        assertTrue(vertex.contains("a_texCoord0"), "OBJ UVs must reach the shader");
        for (String uniform : new String[]{
            "u_diffuseTexture", "u_diffuseColor", "u_alphaTest",
            "u_lightPos", "u_lightColor", "u_lightRange", "u_fogColor"}) {
            assertTrue(model.contains(uniform), "nature_model.frag must use " + uniform);
        }
        assertTrue(model.contains("discard"), "alpha-cutout foliage must discard texels");
        assertTrue(model.contains("gl_FrontFacing"),
            "double-sided leaves need flipped backface normals");
        assertTrue(model.contains("GL_ES"), "kiosk build must stay GLES2 compatible");
        assertTrue(edge.contains("discard"),
            "the outline prepass must ignore transparent leaf cards");
        assertTrue(edge.contains("u_view") && edge.contains("u_far"),
            "edge prepass must pack view-space normal and depth");
    }

    @Test
    void javaLoadsObjsThroughAssetManagerWithNearestClamp() throws IOException {
        String assets = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/graphics/GameAssets.java"));
        String renderer = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/render/NatureModelRenderer.java"));

        assertTrue(assets.contains("new ObjLoader("),
            "nature models must load through LibGDX ObjLoader");
        assertTrue(assets.contains("Model.class"), "AssetManager must own the models");
        assertTrue(assets.contains("TextureWrap.ClampToEdge"),
            "leaf cutouts must not bleed across UV seams");
        assertTrue(assets.contains("Texture.TextureFilter.Nearest"),
            "nature textures must stay pixel sharp");
        assertTrue(renderer.contains("new ModelBatch("), "nature must render as ModelInstances");
        assertTrue(renderer.contains("renderEdge"), "nature must join the ink-outline prepass");
    }

    @Test
    void objLoaderResolvesEveryMaterialTexture() throws IOException {
        FileHandleResolver resolver = fileName -> new FileHandle(fileName);
        for (String modelPath : GameAssets.NATURE_MODELS) {
            Path obj = TestAssetFiles.assetRoot().resolve(modelPath);
            ModelData data = new ObjLoader(resolver)
                .loadModelData(new FileHandle(obj.toFile()),
                    new ObjLoader.ObjLoaderParameters(true));
            assertTrue(data != null && !data.meshes.isEmpty(),
                "ObjLoader produced no mesh for " + obj);

            for (ModelMaterial material : data.materials) {
                assertTrue(material.textures != null && material.textures.notEmpty(),
                    modelPath + " material " + material.id + " has no texture");
                boolean diffuse = false;
                for (ModelTexture texture : material.textures) {
                    if (texture.usage == ModelTexture.USAGE_DIFFUSE) {
                        diffuse = true;
                        Path file = Path.of(texture.fileName);
                        assertTrue(Files.isRegularFile(file),
                            "unresolved MTL texture " + texture.fileName);
                    }
                }
                assertTrue(diffuse, modelPath + " material " + material.id
                    + " is missing USAGE_DIFFUSE");
            }
        }
    }

    @Test
    void natureMaterialsAreOpaqueAndDepthWriting() {
        Material material = new Material("sakura_leaves",
            new BlendingAttribute(true, 0.35f),
            new ColorAttribute(ColorAttribute.Diffuse, 1f, 1f, 1f, 1f));
        assertTrue(material.has(BlendingAttribute.Type),
            "test fixture must start with a translucent attribute");

        NatureModelRenderer.configureMaterial(material);

        assertFalse(material.has(BlendingAttribute.Type),
            "alpha-cutout foliage must not be treated as translucent");
        DepthTestAttribute depth =
            (DepthTestAttribute) material.get(DepthTestAttribute.Type);
        assertTrue(depth != null, "nature materials need explicit depth testing");
        assertEquals(GL20.GL_LEQUAL, depth.depthFunc);
        assertTrue(depth.depthMask, "foliage and trunks must write depth");
        IntAttribute cull = (IntAttribute) material.get(IntAttribute.CullFace);
        assertTrue(cull != null && cull.value == 0,
            "double-sided foliage keeps culling disabled");
    }

    @Test
    void shaderPipelineRestoresSharedDepthState() throws IOException {
        String nature = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/render/NatureModelRenderer.java"));
        String terrain = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/render/HeightmapTerrainRenderer.java"));
        String post = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/render/AsciiPostProcessor.java"));
        String assets = Files.readString(TestAssetFiles.assetRoot().getParent()
            .resolve("core/src/main/java/com/lychcs/koikoi/graphics/GameAssets.java"));

        assertTrue(nature.contains("context.setDepthTest(GL20.GL_LEQUAL"),
            "ModelBatch resets depth; nature shader must re-enable GL_LEQUAL");
        assertTrue(nature.contains("context.setDepthMask(true)"),
            "nature shader must enable depth writes");
        assertTrue(assets.contains("material.remove(BlendingAttribute.Type)"),
            "loaded nature materials must not remain translucent");
        assertTrue(assets.contains("new DepthTestAttribute(GL20.GL_LEQUAL, true)"),
            "loaded nature materials must declare depth testing and writes");
        assertTrue(terrain.contains("useOpaqueDepthState();"),
            "terrain renderer must restore depth state after ModelBatch");
        assertTrue(post.contains("glDepthMask(true)"),
            "scene FBO begin must start with depth writes enabled");
    }

    private static Set<String> materialNames(String text, String prefix) {
        Set<String> names = new HashSet<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                names.add(trimmed.substring(prefix.length()).trim());
            }
        }
        return names;
    }
}
