#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_cellSize;
uniform float u_time;
uniform vec3 u_ink;

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// Signed distance to a line segment, used to stamp procedural terminal glyphs.
float sdSegment(vec2 p, vec2 a, vec2 b, float width) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h) - width;
}

float stroke(vec2 p, vec2 a, vec2 b, float width) {
    return 1.0 - smoothstep(0.0, 0.06, sdSegment(p, a, b, width));
}

float dotInk(vec2 p, vec2 center, float radius) {
    return 1.0 - smoothstep(radius, radius + 0.07, length(p - center));
}

float ringInk(vec2 p, vec2 center, float radius, float width) {
    float distanceToRing = abs(length(p - center) - radius) - width;
    return 1.0 - smoothstep(0.0, 0.06, distanceToRing);
}

// --- Night sky uniforms -------------------------------------------------
uniform vec3 u_warmInk;
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

/** '~' cloud stroke: a soft sine wave across the terminal cell. */
float waveMask(vec2 p) {
    float y = 0.5 + 0.14 * sin(p.x * 6.28318);
    return 1.0 - smoothstep(0.035, 0.09, abs(p.y - y));
}

/** '-' cloud stroke. */
float dashMask(vec2 p) {
    return 1.0 - smoothstep(0.035, 0.09, abs(p.y - 0.5));
}

/** '.' cloud speck. */
float dotCellMask(vec2 p) {
    return 1.0 - smoothstep(0.07, 0.13, length(p - vec2(0.5, 0.32)));
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

/** Bilinear value noise for the drifting cloud bands. */
float skyNoise(vec2 p) {
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
 * Density ramp of the ASCII glyph matrix: blank, '.', ':', '-', '=', '+',
 * '*', '#', '@'. The input is the cell luminance in 0..1.
 */
float glyphMask(vec2 p, float level) {
    if (level < 0.13) {
        return 0.0;
    }
    if (level < 0.24) {
        return dotInk(p, vec2(0.5, 0.22), 0.07);
    }
    if (level < 0.34) {
        return max(dotInk(p, vec2(0.5, 0.20), 0.06),
            dotInk(p, vec2(0.5, 0.76), 0.06));
    }
    if (level < 0.44) {
        return stroke(p, vec2(0.14, 0.5), vec2(0.86, 0.5), 0.034);
    }
    if (level < 0.54) {
        return max(stroke(p, vec2(0.12, 0.36), vec2(0.88, 0.36), 0.030),
            stroke(p, vec2(0.12, 0.64), vec2(0.88, 0.64), 0.030));
    }
    if (level < 0.66) {
        return max(stroke(p, vec2(0.5, 0.16), vec2(0.5, 0.84), 0.034),
            stroke(p, vec2(0.14, 0.5), vec2(0.86, 0.5), 0.034));
    }
    if (level < 0.78) {
        return max(max(stroke(p, vec2(0.18, 0.2), vec2(0.82, 0.8), 0.030),
                stroke(p, vec2(0.18, 0.8), vec2(0.82, 0.2), 0.030)),
            stroke(p, vec2(0.5, 0.24), vec2(0.5, 0.76), 0.028));
    }
    if (level < 0.90) {
        return max(max(stroke(p, vec2(0.22, 0.2), vec2(0.78, 0.2), 0.028),
                stroke(p, vec2(0.22, 0.8), vec2(0.78, 0.8), 0.028)),
            max(stroke(p, vec2(0.24, 0.16), vec2(0.24, 0.84), 0.028),
                stroke(p, vec2(0.76, 0.16), vec2(0.76, 0.84), 0.028)));
    }
    return max(ringInk(p, vec2(0.5, 0.5), 0.30, 0.055),
        dotInk(p, vec2(0.5, 0.5), 0.09));
}

void main() {
    vec2 uv = v_texCoords;
    vec2 grid = u_resolution / max(u_cellSize, vec2(1.0));
    vec2 cell = uv * grid;
    vec2 id = floor(cell);
    vec2 glyphSpace = fract(cell);

    // One scene sample per terminal cell keeps the mosaic stable while moving.
    vec2 centerUv = clamp((id + 0.5) / grid, vec2(0.0015), vec2(0.9985));
    vec4 sceneSample = texture2D(u_texture, centerUv);
    vec3 scene = sceneSample.rgb;
    float sceneLuma = clamp(luma(scene), 0.0, 1.0);

    // Lift dark moss tones so valleys still read as sparse glyphs.
    float level = clamp(pow(sceneLuma, 0.78) * 1.08, 0.0, 1.0);
    float mask = glyphMask(glyphSpace, level);

    // Warm Tōrō/temple light turns the moss-green ink golden orange.
    float warmth = clamp((scene.r - scene.g * 0.80) * 3.0, 0.0, 1.0);
    vec3 inkBase = mix(u_ink, u_warmInk, warmth);

    float pulse = 0.97 + 0.03 * sin(u_time * 1.7 + id.x * 0.19 + id.y * 0.13);

    vec3 background = vec3(0.010, 0.026, 0.014);
    vec3 ink = mix(inkBase * 0.55, inkBase * 1.55, level);
    vec3 color = mix(background, ink * (0.50 + 0.80 * level), mask);

    // Faint phosphor halo on bright cells, dark cells stay untouched.
    color += inkBase * 0.09 * smoothstep(0.35, 1.0, level);
    color *= pulse;
    color *= 1.0 - 0.10 * smoothstep(0.92, 1.0, glyphSpace.y);

    // --- Night sky ------------------------------------------------------
    // The 3D framebuffer clears alpha to 0 and every 3D vertex writes 1, so
    // skyMask isolates the empty sky behind the terrain and scenery.
    float skyMask = 1.0 - smoothstep(0.35, 0.75, sceneSample.a);
    if (skyMask > 0.01) {
        vec2 screenUv = gl_FragCoord.xy / u_resolution;
        vec3 viewDir = normalize(u_skyForward
            + u_skyRight * (screenUv.x * 2.0 - 1.0) * u_tanHalfFovX
            + u_skyUp * (screenUv.y * 2.0 - 1.0) * u_tanHalfFovY);

        // ASCII moon: a fixed world direction, soft disc, cratered glyphs.
        float moonDot = dot(viewDir, u_moonDir);
        float moon = smoothstep(u_moonCosOuter, u_moonCosInner, moonDot);

        // Drifting cloud bands projected onto a virtual sky plane.
        float plane = max(viewDir.y, 0.07);
        vec2 cloudUv = viewDir.xz / plane * 1.6;
        vec2 drift = vec2(u_time * u_cloudDriftX, u_time * u_cloudDriftY);
        float clouds = skyNoise(cloudUv + drift) * 0.62
            + skyNoise(cloudUv * 2.3 - drift * 1.7) * 0.38;
        clouds = smoothstep(0.50, 0.86, clouds)
            * smoothstep(0.02, 0.16, viewDir.y)
            * (1.0 - moon);

        float moonGlyph = 0.0;
        if (moon > 0.02) {
            moonGlyph = max(ringInk(glyphSpace, vec2(0.5, 0.5), 0.27, 0.10),
                dotInk(glyphSpace, vec2(0.5, 0.5), 0.15));
        }

        float cloudGlyph = 0.0;
        if (clouds > 0.62) {
            cloudGlyph = waveMask(glyphSpace);
        } else if (clouds > 0.34) {
            cloudGlyph = dashMask(glyphSpace);
        } else if (clouds > 0.12) {
            cloudGlyph = dotCellMask(glyphSpace);
        }

        color += u_cloudInk * cloudGlyph * clouds * 0.85 * skyMask;
        color += u_moonInk * moonGlyph * moon * 1.15 * skyMask;
        // Soft phosphor halo around the moon.
        color += u_moonInk * 0.16 * smoothstep(0.985, 0.9997, moonDot) * skyMask;
    }

    // Keep the very edges readable: only a gentle terminal vignette.
    vec2 vignetteUv = uv * (1.0 - uv);
    float vignette = pow(clamp(vignetteUv.x * vignetteUv.y * 16.0, 0.0, 1.0), 0.25);
    color *= mix(0.88, 1.0, vignette);

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0) * v_color;
}
