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


// ============================================================
// UTILITIES
// ============================================================

float luminance(vec3 c)
{
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash21(vec2 p)
{
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2D(vec2 p)
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

    v += noise2D(p)        * 0.5000;
    v += noise2D(p * 2.03) * 0.2500;
    v += noise2D(p * 4.07) * 0.1250;
    v += noise2D(p * 8.11) * 0.0625;

    return v / 0.9375;
}


// ============================================================
// CORRUPTION MAP
// ============================================================

float corruptionSample(vec2 uv)
{
    return texture2D(u_corruptionMap, uv).g;
}


// Soft sampling intentionally removes the obvious
// "Gray-Scott blob boundary".
float smoothCorruption(vec2 uv)
{
    float c = corruptionSample(uv) * 0.40;

    c += corruptionSample(
        uv + vec2( u_pixelSize.x * 3.0, 0.0)
    ) * 0.15;

    c += corruptionSample(
        uv + vec2(-u_pixelSize.x * 3.0, 0.0)
    ) * 0.15;

    c += corruptionSample(
        uv + vec2(0.0,  u_pixelSize.y * 3.0)
    ) * 0.15;

    c += corruptionSample(
        uv + vec2(0.0, -u_pixelSize.y * 3.0)
    ) * 0.15;

    return c;
}


// ============================================================
// SOBEL
// ============================================================

float sourceLuma(vec2 uv)
{
    return luminance(texture2D(u_texture, uv).rgb);
}

float sobel(vec2 uv)
{
    vec2 px = u_pixelSize;

    float tl = sourceLuma(uv + vec2(-px.x,  px.y));
    float tc = sourceLuma(uv + vec2( 0.0,   px.y));
    float tr = sourceLuma(uv + vec2( px.x,  px.y));

    float ml = sourceLuma(uv + vec2(-px.x, 0.0));
    float mr = sourceLuma(uv + vec2( px.x, 0.0));

    float bl = sourceLuma(uv + vec2(-px.x, -px.y));
    float bc = sourceLuma(uv + vec2( 0.0,  -px.y));
    float br = sourceLuma(uv + vec2( px.x, -px.y));

    float gx =
    -tl - 2.0 * ml - bl +
    tr + 2.0 * mr + br;

    float gy =
    tl + 2.0 * tc + tr -
    bl - 2.0 * bc - br;

    return sqrt(gx * gx + gy * gy);
}


// ============================================================
// MAIN
// ============================================================

void main()
{
    gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
    return;

    vec2 uv = v_texCoords;

    vec4 untouched = texture2D(u_texture, uv);


    // --------------------------------------------------------
    // 1. INVISIBLE CORRUPTION INFLUENCE
    // --------------------------------------------------------

    float corruptionRaw = smoothCorruption(uv);

    float threshold = clamp(
        u_threshold,
        0.0,
        0.9
    );

    // Extremely wide transition.
    //
    // This is important:
    // we do NOT want the viewer to see where the effect
    // geometrically begins.
    float corruption = smoothstep(
        threshold,
        min(threshold + 0.45, 1.0),
        corruptionRaw
    );


    // --------------------------------------------------------
    // 2. ORGANIC BREAKUP
    // --------------------------------------------------------

    vec2 pixelPos =
    uv / max(u_pixelSize, vec2(0.00001));

    // Time is deliberately quantised.
    //
    // Smooth sine-wave motion feels like water/heat.
    // Small temporal steps feel subtly "wrong" in pixel art.
    float steppedTime =
    floor(u_time * 8.0) / 8.0;

    float organicA = fbm(
        pixelPos * 0.025 +
        vec2(
        steppedTime * 0.15,
        -steppedTime * 0.09
        )
    );

    float organicB = fbm(
        pixelPos * 0.061 +
        vec2(
        -steppedTime * 0.07,
        steppedTime * 0.11
        )
    );

    float organic =
    organicA * 0.68 +
    organicB * 0.32;


    // Prevent the corruption from looking like one perfectly
    // uniform circular field.
    float breakup =
    smoothstep(
        0.18,
        0.82,
        organic + corruption * 0.36
    );

    float influence =
    corruption *
    mix(0.38, 1.0, breakup);


    // Very gentle breathing.
    //
    // Not enough to look like a magical pulse.
    float breathing =
    0.94 +
    0.06 *
    sin(
        u_time * 1.17 +
        organicA * 5.0
    );

    influence *= breathing;

    influence = clamp(
        influence,
        0.0,
        1.0
    );


    // --------------------------------------------------------
    // 3. READ LOCAL GRADIENT OF THE GRAY-SCOTT FIELD
    // --------------------------------------------------------

    float cL = corruptionSample(
        uv - vec2(u_pixelSize.x * 2.0, 0.0)
    );

    float cR = corruptionSample(
        uv + vec2(u_pixelSize.x * 2.0, 0.0)
    );

    float cD = corruptionSample(
        uv - vec2(0.0, u_pixelSize.y * 2.0)
    );

    float cU = corruptionSample(
        uv + vec2(0.0, u_pixelSize.y * 2.0)
    );

    vec2 gradient = vec2(
    cR - cL,
    cU - cD
    );


    // Rotate gradient 90 degrees.
    //
    // This causes textures to subtly crawl ALONG the
    // corruption structures instead of merely expanding
    // radially from the centre.
    vec2 tangent = vec2(
    -gradient.y,
    gradient.x
    );

    float tangentLength =
    length(tangent);

    if (tangentLength > 0.0001)
    {
        tangent /= tangentLength;
    }
    else
    {
        tangent = vec2(1.0, 0.0);
    }


    // --------------------------------------------------------
    // 4. PROCEDURAL PIXEL DISPLACEMENT
    // --------------------------------------------------------

    float directionNoise =
    noise2D(
        floor(pixelPos * 0.125) +
        steppedTime * 3.1
    );

    vec2 noiseDirection = vec2(
    directionNoise * 2.0 - 1.0,
    noise2D(
        floor(pixelPos * 0.125) +
        vec2(41.7, 13.1) +
        steppedTime * 2.3
    ) * 2.0 - 1.0
    );

    vec2 displacementDirection =
    tangent * 0.62 +
    noiseDirection * 0.38;


    // Maximum movement in PIXELS.
    //
    // This is intentionally small.
    float displacementPixels =
    influence *
    mix(
        0.35,
        1.80,
        organic
    );


    vec2 displacement =
    displacementDirection *
    displacementPixels;


    // --------------------------------------------------------
    // 5. HARD PIXEL QUANTISATION
    //
    // Important for your pixel-art aesthetic.
    //
    // Instead of smooth UV warping, pixels genuinely jump
    // to neighbouring texels.
    // --------------------------------------------------------

    vec2 quantizedDisplacement =
    sign(displacement) *
    floor(abs(displacement) + vec2(0.5));


    vec2 warpedUV =
    uv +
    quantizedDisplacement *
    u_pixelSize;


    // --------------------------------------------------------
    // 6. SMALL LOCAL TEXTURE "MISREGISTRATION"
    //
    // This is NOT a glitch line.
    //
    // Small irregular chunks of the texture occasionally
    // occupy a neighbouring pixel position.
    // --------------------------------------------------------

    vec2 cell =
    floor(pixelPos / 4.0);

    float cellNoise =
    hash21(
        cell +
        floor(steppedTime * 3.0)
    );

    float wrongCell =
    smoothstep(
        0.77,
        0.94,
        cellNoise
    );

    wrongCell *=
    influence *
    influence;


    vec2 microOffsetDirection = vec2(
    hash21(cell + 7.31) * 2.0 - 1.0,
    hash21(cell + 19.73) * 2.0 - 1.0
    );

    vec2 microOffset =
    sign(microOffsetDirection) *
    u_pixelSize *
    wrongCell;


    warpedUV += microOffset;


    // Keep sampling valid.
    warpedUV = clamp(
        warpedUV,
        vec2(0.001),
        vec2(0.999)
    );


    // --------------------------------------------------------
    // 7. SAMPLE THE SICK WORLD
    // --------------------------------------------------------

    vec4 warped =
    texture2D(
        u_texture,
        warpedUV
    );


    // --------------------------------------------------------
    // 8. VERY SMALL LOSS OF COLOUR STABILITY
    //
    // NOT grayscale.
    // NOT a visible desaturation filter.
    //
    // It merely makes corrupted pixels feel slightly less
    // alive than their neighbours.
    // --------------------------------------------------------

    float warpedLum =
    luminance(warped.rgb);

    vec3 neutral =
    vec3(warpedLum);


    // We still deliberately consume u_backgroundColor, but
    // only as a tiny tuning influence -- never as an overlay.
    float backgroundLum =
    luminance(u_backgroundColor.rgb);

    float sicknessAmount =
    influence *
    (
    0.035 +
    backgroundLum * 0.025
    );


    vec3 sickColor =
    mix(
        warped.rgb,
        neutral,
        sicknessAmount
    );


    // --------------------------------------------------------
    // 9. LOCAL CONTRAST INSTABILITY
    //
    // A tiny luminance error makes textures appear "infected"
    // without painting anything on top of them.
    // --------------------------------------------------------

    float textureDisease =
    noise2D(
        pixelPos * 0.18 +
        floor(steppedTime * 4.0)
    );

    float diseaseSignal =
    (textureDisease - 0.5) *
    influence;


    sickColor +=
    vec3(diseaseSignal * 0.035);


    // --------------------------------------------------------
    // 10. NEIGHBOUR PIXEL CONTAMINATION
    //
    // Some pixels borrow information from a neighbouring
    // texel. All RGB channels use THE SAME coordinates:
    // therefore no chromatic aberration.
    // --------------------------------------------------------

    float neighbourChoice =
    noise2D(
        floor(pixelPos * 0.5) +
        vec2(91.7, 14.2)
    );

    vec2 neighbourOffset;

    if (neighbourChoice < 0.25)
    neighbourOffset = vec2( 1.0,  0.0);
    else if (neighbourChoice < 0.50)
    neighbourOffset = vec2(-1.0,  0.0);
    else if (neighbourChoice < 0.75)
    neighbourOffset = vec2( 0.0,  1.0);
    else
    neighbourOffset = vec2( 0.0, -1.0);


    vec3 neighbour =
    texture2D(
        u_texture,
        clamp(
            warpedUV +
            neighbourOffset *
            u_pixelSize,
            vec2(0.001),
            vec2(0.999)
        )
    ).rgb;


    float contamination =
    influence *
    wrongCell *
    0.20;


    sickColor =
    mix(
        sickColor,
        neighbour,
        contamination
    );


    // --------------------------------------------------------
    // 11. SOBEL / INK
    //
    // Important distinction:
    //
    // We DO NOT detect edges in the corruption map.
    //
    // Therefore the aura itself can NEVER receive a black
    // outline.
    // --------------------------------------------------------

    float edge =
    sobel(warpedUV);

    float edgeMask =
    smoothstep(
        0.20,
        0.58,
        edge
    );


    // Existing normal ink rendering.
    float normalInk =
    edgeMask *
    u_enableEdges *
    0.16;


    // Corruption makes EXISTING texture edges slightly more
    // unstable / pronounced.
    //
    // It never outlines the corruption itself.
    float sickInk =
    edgeMask *
    influence *
    u_enableEdges *
    0.18;


    float inkAmount =
    clamp(
        normalInk + sickInk,
        0.0,
        0.38
    );


    sickColor =
    mix(
        sickColor,
        u_lineColor.rgb,
        inkAmount
    );


    // --------------------------------------------------------
    // 12. FINAL
    //
    // Outside corruption:
    // result ~= untouched original.
    //
    // Inside corruption:
    // the TEXTURE itself behaves incorrectly.
    // --------------------------------------------------------

    vec3 finalColor =
    mix(
        untouched.rgb,
        sickColor,
        influence
    );


    gl_FragColor = vec4(
    finalColor,
    untouched.a
    );
}
/*
