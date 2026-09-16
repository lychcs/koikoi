#version 100

precision mediump float;

uniform sampler2D u_texture;
uniform sampler2D u_corruptionMap;

uniform vec2  u_pixelSize;
uniform float u_threshold;
uniform vec4  u_lineColor;
uniform vec4  u_backgroundColor;
uniform float u_time;
uniform float u_enableEdges;

varying vec2 v_texCoords;


// ------------------------------------------------------------
// BASIC UTILITIES
// ------------------------------------------------------------

float luminance(vec3 c)
{
    return dot(c, vec3(0.299, 0.587, 0.114));
}


// ------------------------------------------------------------
// HASH / NOISE
// WebGL 1.0 / GLSL ES 1.00 compatible
// ------------------------------------------------------------

float hash21(vec2 p)
{
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p)
{
    vec2 i = floor(p);
    vec2 f = fract(p);

    f = f * f * (3.0 - 2.0 * f);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(
        mix(a, b, f.x),
        mix(c, d, f.x),
        f.y
    );
}

float fbm(vec2 p)
{
    float v = 0.0;

    v += valueNoise(p)        * 0.500;
    v += valueNoise(p * 2.03) * 0.250;
    v += valueNoise(p * 4.07) * 0.125;
    v += valueNoise(p * 8.11) * 0.0625;

    return v / 0.9375;
}


// ------------------------------------------------------------
// CORRUPTION MAP SAMPLING
// ------------------------------------------------------------

float corruptionAt(vec2 uv)
{
    return texture2D(u_corruptionMap, uv).g;
}


// ------------------------------------------------------------
// SOBEL EDGE DETECTION
// Uses the ORIGINAL scene texture so edges survive even when
// the underlying colour is heavily corrupted.
// ------------------------------------------------------------

float sceneLum(vec2 uv)
{
    return luminance(texture2D(u_texture, uv).rgb);
}

float sobelEdge(vec2 uv, vec2 px)
{
    float tl = sceneLum(uv + vec2(-px.x,  px.y));
    float tc = sceneLum(uv + vec2( 0.0,   px.y));
    float tr = sceneLum(uv + vec2( px.x,  px.y));

    float ml = sceneLum(uv + vec2(-px.x,  0.0));
    float mr = sceneLum(uv + vec2( px.x,  0.0));

    float bl = sceneLum(uv + vec2(-px.x, -px.y));
    float bc = sceneLum(uv + vec2( 0.0,  -px.y));
    float br = sceneLum(uv + vec2( px.x, -px.y));

    float gx =
    -tl - 2.0 * ml - bl +
    tr + 2.0 * mr + br;

    float gy =
    tl + 2.0 * tc + tr -
    bl - 2.0 * bc - br;

    return sqrt(gx * gx + gy * gy);
}


// ------------------------------------------------------------
// CORRUPTION BOUNDARY
// Gives extra ink around reaction-diffusion structures.
// ------------------------------------------------------------

float corruptionEdge(vec2 uv)
{
    float l = corruptionAt(uv - vec2(u_pixelSize.x, 0.0));
    float r = corruptionAt(uv + vec2(u_pixelSize.x, 0.0));
    float d = corruptionAt(uv - vec2(0.0, u_pixelSize.y));
    float u = corruptionAt(uv + vec2(0.0, u_pixelSize.y));

    float gx = r - l;
    float gy = u - d;

    return sqrt(gx * gx + gy * gy);
}


void main()
{
    vec2 uv = v_texCoords;

    vec4 original = texture2D(u_texture, uv);

    // --------------------------------------------------------
    // RAW CORRUPTION
    // --------------------------------------------------------

    float corruptionRaw = corruptionAt(uv);

    // u_threshold determines where visible corruption begins.
    float threshold = clamp(u_threshold, 0.0, 0.98);

    float corruption = smoothstep(
        threshold,
        min(threshold + 0.30, 1.0),
        corruptionRaw
    );

    float severeCorruption = smoothstep(
        min(threshold + 0.15, 0.95),
        1.0,
        corruptionRaw
    );


    // --------------------------------------------------------
    // ORGANIC NOISE
    //
    // Multiple different frequencies stop the dissolve from
    // looking like uniform TV/static noise.
    // --------------------------------------------------------

    vec2 pixelCoord = uv / max(u_pixelSize, vec2(0.00001));

    float slowTime = u_time * 0.035;

    float largeGrowth = fbm(
        pixelCoord * 0.018 +
        vec2(slowTime, -slowTime * 0.37)
    );

    float mediumDecay = fbm(
        pixelCoord * 0.055 +
        vec2(-slowTime * 0.27, slowTime * 0.19)
    );

    float fineRot = valueNoise(
        pixelCoord * 0.19 +
        vec2(slowTime * 0.11, slowTime * 0.07)
    );

    float organicNoise =
    largeGrowth * 0.55 +
    mediumDecay * 0.32 +
    fineRot * 0.13;


    // --------------------------------------------------------
    // DEAD / ASH COLOUR
    //
    // Almost all chroma is removed. The small tonal variation
    // makes the corrupted image feel dirty rather than simply
    // using a standard grayscale filter.
    // --------------------------------------------------------

    float gray = luminance(original.rgb);

    float ashNoise =
    valueNoise(pixelCoord * 0.08) * 0.10 - 0.05;

    vec3 ashColor =
    vec3(gray * 0.72 + ashNoise);

    // Cold/dead neutral tone. Deliberately low chroma.
    ashColor *= vec3(0.94, 0.94, 0.92);

    // Crushing contrast creates the harsh ink / dead-world look.
    ashColor = (ashColor - 0.5) * 1.28 + 0.5;
    ashColor = clamp(ashColor, 0.0, 1.0);

    // Corruption progressively destroys colour information.
    vec3 deadWorld = mix(
        original.rgb,
        ashColor,
        corruption
    );

    // At very high corruption the remaining surfaces become
    // darker and more lifeless.
    deadWorld *= mix(
        1.0,
        0.52,
        severeCorruption
    );


    // --------------------------------------------------------
    // ORGANIC DISSOLVE / EROSION
    //
    // corruption + noise controls which physical parts of the
    // rendered world are eaten by the void.
    //
    // Higher corruption lowers the amount of noise required to
    // erase a pixel.
    // --------------------------------------------------------

    float erosionField =
    organicNoise * 0.78 +
    corruptionRaw * 0.62;

    float erosionThreshold =
    1.08 - corruption * 0.48;

    float dissolve = smoothstep(
        erosionThreshold - 0.075,
        erosionThreshold + 0.075,
        erosionField
    );

    // Stronger destruction in deeply corrupted regions.
    dissolve = max(
        dissolve,
        severeCorruption *
        smoothstep(0.47, 0.72, organicNoise)
    );


    // --------------------------------------------------------
    // FRAYED / BURNT-PAPER BORDER
    //
    // The narrow band immediately before total disappearance
    // is heavily darkened, creating mold/burnt organic borders.
    // --------------------------------------------------------

    float dissolveCore = smoothstep(
        erosionThreshold + 0.025,
        erosionThreshold + 0.105,
        erosionField
    );

    float dissolveOuter = smoothstep(
        erosionThreshold - 0.13,
        erosionThreshold + 0.015,
        erosionField
    );

    float rotRim = clamp(
        dissolveOuter - dissolveCore,
        0.0,
        1.0
    );

    rotRim *= corruption;


    // Small speckled lesions around the advancing boundary.
    float speckThreshold = mix(
        0.83,
        0.43,
        corruption
    );

    float specks = smoothstep(
        speckThreshold,
        speckThreshold + 0.08,
        fineRot
    );

    specks *= corruption;
    specks *= 1.0 - dissolveCore;

    // Keep isolated damage less aggressive outside strongly
    // corrupted areas.
    specks *= smoothstep(0.15, 0.75, corruption);


    // --------------------------------------------------------
    // VOID COLOUR
    //
    // u_backgroundColor acts as the configured base, but the
    // result is forced into a nearly colourless black/void so
    // saturated backgrounds cannot turn this into neon.
    // --------------------------------------------------------

    float bgLum = luminance(u_backgroundColor.rgb);

    vec3 neutralBackground = vec3(bgLum);

    vec3 voidColor = mix(
        vec3(0.0),
        neutralBackground * 0.16,
        0.25
    );

    // Tiny, non-luminous variation inside the darkness.
    float voidTexture =
    fbm(pixelCoord * 0.035 + vec2(4.7, -8.2));

    voidColor += vec3(voidTexture * 0.018);
    voidColor = clamp(voidColor, 0.0, 0.075);


    // Burn / mold rim.
    deadWorld = mix(
        deadWorld,
        voidColor,
        rotRim * 0.86
    );

    // Fine holes appearing before the larger chunks disappear.
    deadWorld = mix(
        deadWorld,
        voidColor,
        specks * 0.50
    );

    // Fully eaten regions.
    deadWorld = mix(
        deadWorld,
        voidColor,
        dissolveCore
    );


    // --------------------------------------------------------
    // SOBEL INK EDGES
    //
    // In corrupted regions the edge lookup receives a tiny
    // irregular displacement. This does NOT create chromatic
    // aberration; every colour channel samples the same UV.
    // It merely makes the line work less mechanically stable.
    // --------------------------------------------------------

    float jitterNoise = valueNoise(
        pixelCoord * 0.12 +
        vec2(
        u_time * 0.21,
        -u_time * 0.17
        )
    );

    float jitterSign = jitterNoise * 2.0 - 1.0;

    vec2 jitterOffset = vec2(
    jitterSign,
    -jitterSign * 0.63
    );

    jitterOffset *=
    u_pixelSize *
    corruption *
    1.35;

    float edgeNormal = sobelEdge(
        uv,
        u_pixelSize
    );

    float edgeJittered = sobelEdge(
        uv + jitterOffset,
        u_pixelSize
    );

    float sceneEdge = mix(
        edgeNormal,
        edgeJittered,
        corruption
    );


    // --------------------------------------------------------
    // REACTION-DIFFUSION INK VEINS
    //
    // Makes the Gray-Scott shapes themselves acquire black
    // organic boundaries.
    // --------------------------------------------------------

    float mapEdge = corruptionEdge(uv);

    float inkThreshold = mix(
        0.23,
        0.12,
        corruption
    );

    float inkEdge = smoothstep(
        inkThreshold,
        inkThreshold + 0.22,
        sceneEdge
    );

    float corruptionInk = smoothstep(
        0.015,
        0.085,
        mapEdge
    );

    corruptionInk *= corruption;


    // Increase outline dominance as the environment dies.
    float combinedInk = max(
        inkEdge * mix(1.0, 1.45, corruption),
        corruptionInk * 0.90
    );

    combinedInk = clamp(combinedInk, 0.0, 1.0);


    // --------------------------------------------------------
    // LINE COLOUR
    //
    // Preserve u_lineColor, but force corrupted ink toward
    // black so brightly configured line colours cannot become
    // cyberpunk/neon.
    // --------------------------------------------------------

    float lineLum = luminance(u_lineColor.rgb);

    vec3 normalLine = u_lineColor.rgb;

    vec3 corruptedLine =
    vec3(lineLum) * 0.14;

    vec3 finalLineColor = mix(
        normalLine,
        corruptedLine,
        corruption
    );

    finalLineColor = mix(
        finalLineColor,
        vec3(0.0),
        severeCorruption * 0.85
    );


    // --------------------------------------------------------
    // EDGE PRESERVATION INSIDE DISSOLVE
    //
    // Allows black remnants / contour fragments to persist
    // briefly after surrounding surfaces have been eaten.
    // --------------------------------------------------------

    float survivingInk =
    combinedInk *
    (1.0 - dissolveCore * 0.42);

    float edgeAmount =
    survivingInk *
    clamp(u_enableEdges, 0.0, 1.0);

    vec3 finalColor = mix(
        deadWorld,
        finalLineColor,
        edgeAmount
    );


    // --------------------------------------------------------
    // FINAL DECAY
    //
    // Extremely corrupted areas receive uneven black stains,
    // giving the surviving material an infected / printed-ink
    // quality rather than a smooth digital fade.
    // --------------------------------------------------------

    float blackStainNoise = fbm(
        pixelCoord * 0.027 +
        vec2(17.31, -5.77)
    );

    float blackStains = smoothstep(
        0.50,
        0.79,
        blackStainNoise + severeCorruption * 0.32
    );

    blackStains *= severeCorruption;
    blackStains *= 1.0 - edgeAmount * 0.35;

    finalColor = mix(
        finalColor,
        voidColor,
        blackStains * 0.42
    );


    // Absolute final colour clamp. Prevent accidental bright
    // corrupted pixels from surviving.
    vec3 corruptedClamp = min(
        finalColor,
        vec3(0.72)
    );

    finalColor = mix(
        finalColor,
        corruptedClamp,
        corruption
    );


    gl_FragColor = vec4(
    finalColor,
    original.a
    );
}
