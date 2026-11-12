#ifdef GL_ES
precision mediump float;
#endif

#define MAX_LIGHTS 12

varying vec4 v_color;
varying vec3 v_normal;
varying vec3 v_world;
varying float v_material;

uniform sampler2D u_grassTexture;
uniform sampler2D u_cliffTexture;
uniform sampler2D u_toriiTexture;
uniform sampler2D u_roofTexture;
uniform sampler2D u_pathTexture;
uniform sampler2D u_sakuraTexture;
uniform float u_textureWorldScale;

uniform vec3 u_cameraPos;
uniform vec3 u_lightDir;
uniform vec3 u_fogColor;
uniform vec3 u_valleyFogColor;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform float u_fogBaseHeight;
uniform float u_fogHeightSpan;
uniform float u_heightFogStrength;
uniform float u_time;

uniform int u_lightCount;
uniform vec3 u_lightPos[MAX_LIGHTS];
uniform vec3 u_lightColor[MAX_LIGHTS];
uniform float u_lightRange[MAX_LIGHTS];

// --- Four-season Hanafuda hub blend -------------------------------------
// World-space cardinal season weights. x = spring (east), y = summer (south),
// z = fall (west), w = winter (north); the neutral sanctuary owns the rest of
// the influence inside the hub until the four lobes overlap on the diagonal.
uniform vec2 u_seasonHub;
uniform float u_seasonStart;
uniform float u_seasonWidth;
uniform vec3 u_springGround;
uniform vec3 u_summerGround;
uniform vec3 u_fallGround;
uniform vec3 u_winterGround;
uniform vec3 u_springFoliage;
uniform vec3 u_summerFoliage;
uniform vec3 u_fallFoliage;
uniform vec3 u_winterFoliage;

vec4 seasonWeights(vec2 worldXZ, out float seasonal) {
    vec2 d = worldXZ - u_seasonHub;
    float edge = u_seasonStart + u_seasonWidth;
    float east = smoothstep(u_seasonStart, edge, d.x);
    float south = smoothstep(u_seasonStart, edge, d.y);
    float west = smoothstep(u_seasonStart, edge, -d.x);
    float north = smoothstep(u_seasonStart, edge, -d.y);
    float sum = east + south + west + north;
    if (sum > 1.0) {
        seasonal = 1.0;
        return vec4(east, south, west, north) / sum;
    }
    seasonal = sum;
    return vec4(east, south, west, north);
}


// Mokuhanga / Sumi-e title-screen palette.
const vec3 MIDNIGHT_INDIGO = vec3(0.043, 0.055, 0.114); // #0B0E1D
const vec3 TWILIGHT_VIOLET = vec3(0.231, 0.118, 0.329); // #3B1E54
const vec3 LANTERN_GOLD = vec3(0.949, 0.757, 0.306); // #F2C14E
const vec3 SKY_AMBIENT = vec3(0.060, 0.068, 0.155);
const vec3 GROUND_BOUNCE = vec3(0.085, 0.125, 0.080);
const vec3 LANTERN_TINT = LANTERN_GOLD;
const vec3 MOON_DIRECTION = vec3(0.42, 0.70, -0.58);
/** Tarnished kintsugi gold (#D4AF37). */
const vec3 KINTSUGI_GOLD = vec3(0.831, 0.686, 0.216);
/** Crisp deep sumi-ink outline / shadow colour (#0B0E1D). */
const vec3 SUMI_INK = MIDNIGHT_INDIGO;
const vec3 SLATE = vec3(0.72, 0.78, 0.84);
const vec3 CREVICE_MOSS = vec3(0.74, 0.90, 0.70);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

/** Smooth value noise for moss mottling and stone wash. */
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

/**
 * Blends the three world-space projections of one 64x64 texture. Repeat
 * wrapping and nearest filtering are set on the Java texture objects, so the
 * helper only needs the normal-blended projection weights.
 */
vec3 triplanarBlend(vec3 xProjection, vec3 yProjection, vec3 zProjection,
                    vec3 weights) {
    return xProjection * weights.x
        + yProjection * weights.y
        + zProjection * weights.z;
}

vec3 surfaceTexture(vec3 normal) {
    vec3 p = v_world * u_textureWorldScale;
    // Sharper than linear weights: flat ground stays grass, vertical walls
    // stay stone, without smearing either across the bevel between them.
    vec3 weights = pow(abs(normal), vec3(5.0));
    weights /= max(weights.x + weights.y + weights.z, 0.0001);

    vec3 grass = triplanarBlend(
        texture2D(u_grassTexture, p.zy).rgb,
        texture2D(u_grassTexture, p.xz).rgb,
        texture2D(u_grassTexture, p.xy).rgb, weights);
    vec3 cliff = triplanarBlend(
        texture2D(u_cliffTexture, p.zy).rgb,
        texture2D(u_cliffTexture, p.xz).rgb,
        texture2D(u_cliffTexture, p.xy).rgb, weights);
    vec3 torii = triplanarBlend(
        texture2D(u_toriiTexture, p.zy).rgb,
        texture2D(u_toriiTexture, p.xz).rgb,
        texture2D(u_toriiTexture, p.xy).rgb, weights);
    vec3 roof = triplanarBlend(
        texture2D(u_roofTexture, p.zy).rgb,
        texture2D(u_roofTexture, p.xz).rgb,
        texture2D(u_roofTexture, p.xy).rgb, weights);
    vec3 path = triplanarBlend(
        texture2D(u_pathTexture, p.zy).rgb,
        texture2D(u_pathTexture, p.xz).rgb,
        texture2D(u_pathTexture, p.xy).rgb, weights);
    vec3 sakura = triplanarBlend(
        texture2D(u_sakuraTexture, p.zy).rgb,
        texture2D(u_sakuraTexture, p.xz).rgb,
        texture2D(u_sakuraTexture, p.xy).rgb, weights);

    int material = int(v_material + 0.5);
    if (material == 0) {
        float rock = smoothstep(0.18, 0.62, 1.0 - abs(normal.y));
        return mix(grass, cliff, rock);
    }
    if (material == 1) {
        return cliff;
    }
    if (material == 2) {
        return torii;
    }
    if (material == 3) {
        return roof;
    }
    if (material == 4) {
        return path;
    }
    if (material == 5) {
        return sakura;
    }
    // Player avatar, shadows and other dynamic boxes keep their hand-painted
    // vertex palette; they must never acquire world texture UVs.
    return vec3(1.0);
}

/**
 * Mokuhanga / Sumi-e world pass.
 *
 * <p>Three quantized moonlight bands, hemispheric indigo/moss ambient, warm
 * lantern pools, world-space woodblock hatching, layered slate strata and
 * kintsugi/sumi silhouette ink. The shader never writes magic alpha: all solid
 * world geometry is exactly alpha 1.0, and magic is rendered as its own direct
 * 3D glyph layer.</p>
 */
void main() {
    vec3 normal = normalize(v_normal);
    vec3 toCamera = normalize(u_cameraPos - v_world);
    vec3 lightDir = normalize(u_lightDir);
    vec3 palette = v_color.rgb;

    // World-space triplanar pixel art. Dynamic avatar/shadow boxes carry
    // material 255 and keep their hand-painted vertex palette untouched.
    if (v_material < 128.0) {
        vec3 texel = surfaceTexture(normal);
        vec3 textured = texel * (0.74 + 0.68 * palette);
        palette = mix(palette, textured, 0.80);
    }

    // --- Three-band stylized cel shading ---------------------------------
    float lambert = max(dot(normal, lightDir), 0.0);
    float shadowStep = smoothstep(-0.06, 0.10, lambert);
    float midStep = smoothstep(0.30, 0.46, lambert);
    float highStep = smoothstep(0.64, 0.80, lambert);
    float cel = 0.30 + 0.26 * shadowStep + 0.24 * midStep + 0.20 * highStep;

    // --- Hemispheric ambient: sky indigo above, moss bounce below --------
    float hemi = clamp(normal.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 skyLight = SKY_AMBIENT * (0.82 + 0.18 * palette.g);
    vec3 groundLight = GROUND_BOUNCE * (0.70 + 0.30 * palette.r);
    vec3 ambient = mix(groundLight, skyLight, hemi);

    // --- Warm lantern pools ----------------------------------------------
    vec3 warm = vec3(0.0);
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= u_lightCount) break;
        vec3 toLight = u_lightPos[i] - v_world;
        float range = max(u_lightRange[i], 0.001);
        float attenuation = clamp(
            1.0 - dot(toLight, toLight) / (range * range), 0.0, 1.0);
        attenuation *= attenuation;
        warm += u_lightColor[i] * attenuation;
    }
    float warmLevel = clamp(max(warm.r, max(warm.g, warm.b)), 0.0, 1.0);

    vec3 color = palette * (0.24 + 0.76 * cel);
    color += ambient;
    color += palette * warm * 0.88 + LANTERN_TINT * warmLevel * 0.24;

    // --- Four-season hub blending (ground albedo + foliage) --------------
    float seasonal;
    vec4 season = seasonWeights(v_world.xz, seasonal);
    vec3 groundSeason = u_springGround * season.x + u_summerGround * season.y
        + u_fallGround * season.z + u_winterGround * season.w;
    vec3 foliageSeason = u_springFoliage * season.x + u_summerFoliage * season.y
        + u_fallFoliage * season.z + u_winterFoliage * season.w;
    vec3 seasonFogTint = foliageSeason;

    // The wash is multiplied with the cel light so winter snow stays soft
    // instead of blowing out the woodblock print.
    float groundMask = smoothstep(0.55, 0.90, normal.y);
    color = mix(color, color * 0.45 + groundSeason * (0.55 + 0.45 * cel),
        seasonal * groundMask * 0.55);

    // Sakura / maple canopy material picks up the realm's foliage ink.
    int materialId = int(v_material + 0.5);
    if (materialId == 5) {
        color = mix(color, color * foliageSeason, seasonal * 0.60);
    }

    // --- Organic surface variation ---------------------------------------

    float grass = smoothstep(0.55, 0.90, normal.y);
    float wind = sin(v_world.x * 0.75 + v_world.z * 0.55 + u_time * 0.80)
        * sin(v_world.z * 0.45 - v_world.x * 0.30 + u_time * 0.55);
    float ripple = wind * 0.045 * grass;
    float mottle = valueNoise(v_world.xz * 1.30) * 0.65
        + valueNoise(v_world.xz * 4.10) * 0.35;
    float moss = smoothstep(0.48, 0.82, mottle) * grass;
    float wood = smoothstep(0.06, 0.34, palette.r - palette.g) * (1.0 - grass);
    color *= 1.0 + ripple;
    color = mix(color, color * vec3(0.82, 1.05, 0.84), moss * 0.18);
    color = mix(color, color * vec3(1.08, 0.99, 0.95), wood * 0.25);

    // --- Mokuhanga woodblock hatching ------------------------------------
    // World-space phase anchored to the surface; nothing screen-space, so the
    // strokes cannot flicker while the camera turns.
    float slope = 1.0 - clamp(normal.y, 0.0, 1.0);
    float rock = smoothstep(0.30, 0.72, slope);
    vec2 wallDir = vec2(normal.z, -normal.x);
    float wallLen = length(wallDir);
    wallDir = wallLen > 0.15 ? wallDir / wallLen : vec2(0.78, 0.63);
    // Ground uses diagonal strokes; steep walls use horizontal woodblock lines.
    float groundPhase = dot(v_world.xz, vec2(0.78, 0.63)) * 26.0;
    float wallPhase = v_world.y * 24.0 + dot(v_world.xz, wallDir) * 1.5;
    float hatchPhase = mix(groundPhase, wallPhase, rock);
    hatchPhase += valueNoise(v_world.xz * 1.7 + vec2(v_world.y * 0.9)) * 2.4;
    float hatchWave = sin(hatchPhase) * 0.5 + 0.5;
    float hatchLine = smoothstep(0.56, 0.70, hatchWave);
    // Mid-tones and deep shadows receive the crisp brush lines; lit crests stay
    // clean so the print still reads as lit.
    float hatchZone = (1.0 - smoothstep(0.34, 1.0, cel))
        * mix(0.55, 1.0, rock);
    float hatch = hatchLine * hatchZone * 0.11;
    color *= 1.0 - hatch;
    color = mix(color, color * vec3(0.84, 0.88, 0.94), hatch * 0.40);

    // --- Layered cliff slate strata + moss crevices ----------------------
    float strataPhase = v_world.y * 9.0
        + valueNoise(v_world.xz * 0.90) * 4.0;
    float strata = smoothstep(0.52, 0.84, sin(strataPhase) * 0.5 + 0.5);
    float fineStrata = smoothstep(0.70, 0.92,
        sin(v_world.y * 21.0 + valueNoise(v_world.xz * 2.3) * 5.0) * 0.5 + 0.5);
    color = mix(color, color * SLATE, rock * strata * 0.20);
    color *= 1.0 - rock * (strata * 0.10 + fineStrata * 0.05);

    float creviceNoise = valueNoise(v_world.xz * 2.6 + vec2(v_world.y * 1.2));
    float crevice = rock * smoothstep(0.62, 0.90, creviceNoise);
    color = mix(color, color * CREVICE_MOSS,
        crevice * 0.32 + rock * moss * 0.10);

    // --- Kintsugi gold / deep sumi silhouette ink ------------------------
    float rim = pow(1.0 - clamp(dot(normal, toCamera), 0.0, 1.0), 2.6);
    float lanternRim = rim * smoothstep(0.05, 0.55, warmLevel);
    float moonSide = 0.35 + 0.65 * max(dot(normal, normalize(MOON_DIRECTION)), 0.0);
    float sumiRim = rim
        * (1.0 - smoothstep(0.02, 0.35, warmLevel)) * moonSide;
    color += KINTSUGI_GOLD * lanternRim * 0.42;
    color = mix(color, TWILIGHT_VIOLET * 0.62, sumiRim * 0.16);
    color = mix(color, SUMI_INK, sumiRim * 0.30);

    // Keep shadow sides rich and readable instead of crushing to black.
    color = max(color, palette * 0.24 + SKY_AMBIENT * 0.16);

    // --- Smooth exponential horizon / valley fog -------------------------
    float depth = distance(v_world, u_cameraPos);
    float valley = 1.0 - smoothstep(u_fogBaseHeight,
        u_fogBaseHeight + u_fogHeightSpan, v_world.y);
    float distanceFog = 1.0 - exp(-pow(max(depth - u_fogStart, 0.0) * 0.014, 1.25));
    float heightFog = valley * u_heightFogStrength
        * (1.0 - exp(-max(depth - 4.0, 0.0) * 0.06));
    float fog = clamp(distanceFog + heightFog, 0.0, 0.92);
    vec3 fogColor = mix(u_fogColor, u_valleyFogColor, valley * 0.85);
    // Ambient fog carries a faint seasonal breath so realm transitions read
    // as air, not as a painted texture seam.
    fogColor = mix(fogColor, fogColor * 0.55 + seasonFogTint * 0.30,
        seasonal * 0.22);
    color = mix(color, fogColor, fog);

    // All solid world geometry is strictly opaque.
    gl_FragColor = vec4(color, 1.0);
}
