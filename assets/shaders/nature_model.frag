#ifdef GL_ES
precision mediump float;
#endif

#define MAX_LIGHTS 12

varying vec3 v_world;
varying vec3 v_normal;
varying vec2 v_texCoord0;

uniform sampler2D u_diffuseTexture;
uniform vec3 u_diffuseColor;
uniform float u_alphaTest;

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
// Same cardinal weights as ascii_world.frag: x = spring (east), y = summer
// (south), z = fall (west), w = winter (north).
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

// Mokuhanga / Sumi-e title-screen palette (#0B0E1D, #3B1E54, #F2C14E).
const vec3 MIDNIGHT_INDIGO = vec3(0.043, 0.055, 0.114);
const vec3 TWILIGHT_VIOLET = vec3(0.231, 0.118, 0.329);
const vec3 LANTERN_GOLD = vec3(0.949, 0.757, 0.306);
const vec3 SKY_AMBIENT = vec3(0.060, 0.068, 0.155);
const vec3 GROUND_BOUNCE = vec3(0.085, 0.125, 0.080);
const vec3 MOON_DIRECTION = vec3(0.42, 0.70, -0.58);
const vec3 KINTSUGI_GOLD = vec3(0.831, 0.686, 0.216);
const vec3 SUMI_INK = MIDNIGHT_INDIGO;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

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

void main() {
    vec4 texel = texture2D(u_diffuseTexture, v_texCoord0);
    if (texel.a < u_alphaTest) {
        discard;
    }

    // Double-sided foliage: back faces are lit with the flipped normal so the
    // leaf cards never collapse into black silhouettes.
    vec3 normal = normalize(v_normal);
    if (!gl_FrontFacing) {
        normal = -normal;
    }
    vec3 toCamera = normalize(u_cameraPos - v_world);
    vec3 lightDir = normalize(u_lightDir);
    vec3 palette = texel.rgb * u_diffuseColor;

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
    color += palette * warm * 0.88 + LANTERN_GOLD * warmLevel * 0.24;

    // --- Four-season foliage tint + winter frost --------------------------
    float seasonal;
    vec4 season = seasonWeights(v_world.xz, seasonal);
    vec3 foliageSeason = u_springFoliage * season.x + u_summerFoliage * season.y
        + u_fallFoliage * season.z + u_winterFoliage * season.w;
    color = mix(color, color * foliageSeason, seasonal * 0.58);
    // Winter dusting: upward faces catch the most snow, but even foliage cards
    // lighten so frosted pines read as a distinct northern realm.
    float frost = season.w * (0.28 + 0.52 * smoothstep(0.25, 0.95, normal.y));
    color = mix(color, vec3(0.86, 0.90, 0.95), frost * 0.55);

    // --- Organic surface variation ---------------------------------------
    float leaf = smoothstep(0.40, 0.86, palette.g - palette.b);
    float wind = sin(v_world.x * 0.75 + v_world.z * 0.55 + u_time * 0.80)
        * sin(v_world.z * 0.45 - v_world.x * 0.30 + u_time * 0.55);
    color *= 1.0 + wind * 0.025 * leaf;
    float mottle = valueNoise(v_world.xz * 1.30) * 0.65
        + valueNoise(v_world.xz * 4.10) * 0.35;
    color = mix(color, color * vec3(0.84, 1.04, 0.86),
        smoothstep(0.50, 0.84, mottle) * leaf * 0.16);

    // --- Mokuhanga woodblock hatching ------------------------------------
    float slope = 1.0 - clamp(normal.y, 0.0, 1.0);
    vec2 wallDir = vec2(normal.z, -normal.x);
    float wallLen = length(wallDir);
    wallDir = wallLen > 0.15 ? wallDir / wallLen : vec2(0.78, 0.63);
    float hatchPhase = mix(dot(v_world.xz, vec2(0.78, 0.63)) * 26.0,
        v_world.y * 24.0 + dot(v_world.xz, wallDir) * 1.5,
        smoothstep(0.30, 0.72, slope));
    hatchPhase += valueNoise(v_world.xz * 1.7 + vec2(v_world.y * 0.9)) * 2.4;
    float hatchLine = smoothstep(0.56, 0.70, sin(hatchPhase) * 0.5 + 0.5);
    float hatch = hatchLine * (1.0 - smoothstep(0.34, 1.0, cel)) * 0.10;
    color *= 1.0 - hatch;

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
    fogColor = mix(fogColor, fogColor * 0.55 + foliageSeason * 0.30,
        seasonal * 0.22);
    color = mix(color, fogColor, fog);

    gl_FragColor = vec4(color, 1.0);
}
