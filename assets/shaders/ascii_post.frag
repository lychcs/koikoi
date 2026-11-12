#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform sampler2D u_edgeTexture;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_grain;
uniform float u_bloomRadius;
uniform vec3 u_warmInk;
uniform vec3 u_outlineInk;
uniform float u_edgeEnabled;
uniform float u_far;
uniform float u_outlineFadeStart;
uniform float u_outlineFadeEnd;

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// 16-bit Ukiyo-e title-screen palette.
const vec3 MIDNIGHT_INDIGO = vec3(0.043, 0.055, 0.114); // #0B0E1D
const vec3 TWILIGHT_VIOLET = vec3(0.231, 0.118, 0.329); // #3B1E54
const vec3 LANTERN_GOLD = vec3(0.949, 0.757, 0.306); // #F2C14E

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

/** Bilinear value noise: Washi grain, cloud bands and ink irregularity. */
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

/** One bright sample for the soft warm bloom halo. */
vec3 bloomSample(vec3 color) {
    float maxC = max(color.r, max(color.g, color.b));
    float minC = min(color.r, min(color.g, color.b));
    // Desaturated whites/creams (the player's crop top) bloom far less than
    // saturated lantern gold and coloured card magic.
    float saturation = clamp((maxC - minC) / max(maxC, 0.001), 0.0, 1.0);
    return color * smoothstep(0.68, 0.97, luma(color))
        * mix(0.55, 1.0, saturation);
}

/** Eight wide taps around the pixel: bloom over lanterns, moon and magic. */
vec3 bloomAround(vec2 uv, vec2 stepUv) {
    vec3 sum = vec3(0.0);
    sum += bloomSample(texture2D(u_texture, uv + vec2(stepUv.x * 1.35, 0.0)).rgb);
    sum += bloomSample(texture2D(u_texture, uv - vec2(stepUv.x * 1.35, 0.0)).rgb);
    sum += bloomSample(texture2D(u_texture, uv + vec2(0.0, stepUv.y * 1.35)).rgb);
    sum += bloomSample(texture2D(u_texture, uv - vec2(0.0, stepUv.y * 1.35)).rgb);
    sum += bloomSample(texture2D(u_texture,
        uv + vec2(stepUv.x * 0.95, stepUv.y * 0.95)).rgb);
    sum += bloomSample(texture2D(u_texture,
        uv + vec2(-stepUv.x * 0.95, stepUv.y * 0.95)).rgb);
    sum += bloomSample(texture2D(u_texture,
        uv + vec2(stepUv.x * 0.95, -stepUv.y * 0.95)).rgb);
    sum += bloomSample(texture2D(u_texture,
        uv + vec2(-stepUv.x * 0.95, -stepUv.y * 0.95)).rgb);
    return sum * 0.125;
}

// --- Night sky uniforms --------------------------------------------------
uniform vec3 u_moonDir;
uniform vec3 u_moonInk;
uniform vec3 u_cloudInk;
uniform vec3 u_skyForward;
uniform vec3 u_skyRight;
uniform vec3 u_skyUp;
uniform float u_tanHalfFovX;
uniform float u_tanHalfFovY;
uniform float u_moonCosOuter;
uniform float u_moonCosInner;
uniform float u_cloudDriftX;
uniform float u_cloudDriftY;

// --- Depth / normal ink-wash outline ------------------------------------
bool isSkyDepth(vec4 edge) {
    return edge.b < (0.5 / 255.0) && edge.a < (0.5 / 255.0);
}

float linearDepth(vec4 edge) {
    if (isSkyDepth(edge)) {
        return 0.0;
    }
    float packed = floor(edge.a * 255.0 + 0.5) * 256.0
        + floor(edge.b * 255.0 + 0.5);
    return packed / 65535.0 * u_far;
}

vec3 viewNormal(vec4 edge) {
    vec2 xy = edge.rg * 2.0 - 1.0;
    return vec3(xy, sqrt(max(0.0, 1.0 - dot(xy, xy))));
}

float edgeStrength(vec4 center, vec4 other) {
    bool centerSky = isSkyDepth(center);
    bool otherSky = isSkyDepth(other);
    if (centerSky && otherSky) {
        return 0.0;
    }
    if (centerSky != otherSky) {
        return 1.0;
    }
    float d0 = linearDepth(center);
    float d1 = linearDepth(other);
    float relative = abs(d0 - d1) / max(1.0, min(d0, d1));
    float depthEdge = smoothstep(0.018, 0.070, relative);
    float normalDot = dot(viewNormal(center), viewNormal(other));
    float normalEdge = 1.0 - smoothstep(0.90, 0.985, normalDot);
    return max(depthEdge, normalEdge);
}

float outlineMask(vec2 uv) {
    vec2 texel = 1.0 / max(u_resolution, vec2(1.0));
    vec4 c = texture2D(u_edgeTexture, uv);
    vec4 l = texture2D(u_edgeTexture, uv - vec2(texel.x * 1.35, 0.0));
    vec4 r = texture2D(u_edgeTexture, uv + vec2(texel.x * 1.35, 0.0));
    vec4 d = texture2D(u_edgeTexture, uv - vec2(0.0, texel.y * 1.35));
    vec4 u = texture2D(u_edgeTexture, uv + vec2(0.0, texel.y * 1.35));
    float strong = max(max(edgeStrength(c, l), edgeStrength(c, r)),
        max(edgeStrength(c, d), edgeStrength(c, u)));

    vec4 l2 = texture2D(u_edgeTexture, uv - vec2(texel.x * 2.6, 0.0));
    vec4 r2 = texture2D(u_edgeTexture, uv + vec2(texel.x * 2.6, 0.0));
    vec4 d2 = texture2D(u_edgeTexture, uv - vec2(0.0, texel.y * 2.6));
    vec4 u2 = texture2D(u_edgeTexture, uv + vec2(0.0, texel.y * 2.6));
    float wide = max(max(edgeStrength(c, l2), edgeStrength(c, r2)),
        max(edgeStrength(c, d2), edgeStrength(c, u2)));

    return smoothstep(0.25, 0.95, max(strong, wide * 0.70));
}

void main() {
    vec2 uv = v_texCoords;
    vec4 sceneSample = texture2D(u_texture, uv);
    vec3 scene = sceneSample.rgb;
    // Alpha 0 = sky; alpha 1 = opaque world and direct glyph magic.
    float skyMask = 1.0 - smoothstep(0.06, 0.20, sceneSample.a);

    vec3 color = scene;

    // --- Smooth Ukiyo-e night sky ---------------------------------------
    if (skyMask > 0.001) {
        vec2 screenUv = gl_FragCoord.xy / u_resolution;
        float skyHeight = clamp(screenUv.y, 0.0, 1.0);
        vec3 sky = mix(TWILIGHT_VIOLET, MIDNIGHT_INDIGO, skyHeight);
        sky += LANTERN_GOLD * 0.070
            * (1.0 - smoothstep(0.0, 0.20, skyHeight));

        vec3 viewDir = normalize(u_skyForward
            + u_skyRight * (screenUv.x * 2.0 - 1.0) * u_tanHalfFovX
            + u_skyUp * (screenUv.y * 2.0 - 1.0) * u_tanHalfFovY);

        float moonDot = dot(viewDir, u_moonDir);
        float moon = smoothstep(u_moonCosOuter, u_moonCosInner, moonDot);
        sky = mix(sky, u_moonInk, moon * 0.92);
        float moonHalo = smoothstep(u_moonCosOuter - 0.16,
            u_moonCosOuter + 0.02, moonDot) * (1.0 - moon);
        sky += mix(u_moonInk, u_warmInk, 0.35) * moonHalo * 0.13;

        float plane = max(viewDir.y, 0.07);
        vec2 cloudUv = viewDir.xz / plane * 1.6;
        vec2 drift = vec2(u_time * u_cloudDriftX, u_time * u_cloudDriftY);
        float clouds = valueNoise(cloudUv + drift) * 0.62
            + valueNoise(cloudUv * 2.3 - drift * 1.7) * 0.38;
        clouds = smoothstep(0.48, 0.84, clouds)
            * smoothstep(0.02, 0.16, viewDir.y)
            * (1.0 - moon);
        sky = mix(sky, u_cloudInk * 0.55, clouds * 0.45);

        color = mix(color, sky, skyMask);
    }

    // --- Distance-attenuated ink-wash outline ---------------------------
    if (u_edgeEnabled > 0.5 && skyMask < 0.55) {
        float outline = outlineMask(uv);
        float depth = linearDepth(texture2D(u_edgeTexture, uv));
        float distanceFade = 1.0 - smoothstep(u_outlineFadeStart,
            u_outlineFadeEnd, depth);
        float irregular = 0.82 + 0.18 * valueNoise(gl_FragCoord.xy * 0.045);
        float ink = outline * mix(0.10, 1.0, distanceFade) * irregular;
        color = mix(color, u_outlineInk, clamp(ink, 0.0, 0.82));
    }

    // --- Soft warm bloom: lanterns, moon, card magic --------------------
    // Restrained so the player's cream crop top stays cel-shaded and crisp.
    vec2 bloomStep = vec2(max(u_bloomRadius, 1.0)) / u_resolution;
    vec3 bloom = bloomAround(uv, bloomStep);
    float bloomLuma = luma(bloom);
    vec3 bloomTint = mix(bloom, u_warmInk * bloomLuma, 0.55);
    color += bloomTint * 0.30;

    // --- Stationary Washi-paper / watercolor grain (~6%) ----------------
    float paper = valueNoise(gl_FragCoord.xy * 0.035) * 0.65
        + valueNoise(gl_FragCoord.xy * 0.11 + vec2(13.0, 7.0)) * 0.35;
    float grain = (paper - 0.5) * 2.0;
    color *= 1.0 + grain * u_grain;
    color.r += grain * u_grain * 0.10;
    color.b -= grain * u_grain * 0.06;

    // Gentle terminal/paper vignette.
    vec2 vignetteUv = uv * (1.0 - uv);
    float vignette = pow(clamp(vignetteUv.x * vignetteUv.y * 16.0, 0.0, 1.0), 0.25);
    color *= mix(0.90, 1.0, vignette);

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0) * v_color;
}
