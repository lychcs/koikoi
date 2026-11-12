#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec3 v_world;

uniform float u_time;

float ring(float r, float radius, float width) {
    return 1.0 - smoothstep(width, width * 2.0, abs(r - radius));
}

/**
 * Procedural Kamon crest: concentric rotating rings, 24 radial ticks,
 * five-petal sakura and an eight-spoke wheel. The vertex colour selects the
 * motif (blue => wheel, red => sakura, gold => both) and its alpha carries a
 * per-seal phase offset.
 */
void main() {
    vec2 p = v_texCoords * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) {
        discard;
    }

    float angle = atan(p.y, p.x + 0.0001);
    float phase = v_color.a * 6.28318;
    float spin = u_time * (0.35 + 0.08 * sin(u_time * 0.6 + phase)) + phase;
    float a = angle + spin;

    float rings = ring(r, 0.92, 0.012)
        + ring(r, 0.74, 0.009)
        + ring(r, 0.56, 0.007);

    // Dashed ring: the angular phase makes the concentric crest visibly rotate.
    float dashSegment = fract(a / 6.28318 * 12.0);
    float dashDistance = min(dashSegment, 1.0 - dashSegment);
    rings += ring(r, 0.84, 0.012)
        * (1.0 - smoothstep(0.055, 0.110, dashDistance));

    float tickSegment = fract(a / 6.28318 * 24.0);
    float tickDistance = min(tickSegment, 1.0 - tickSegment);
    float ticks = (1.0 - smoothstep(0.020, 0.055, tickDistance))
        * step(0.57, r) * step(r, 0.73);

    // Five-petal sakura crest.
    float petalRadius = 0.36 + 0.085 * cos(a * 5.0);
    float sakura = ring(r, petalRadius, 0.010) * step(0.12, r);
    sakura = max(sakura, ring(r, 0.14, 0.008));

    // Eight-spoke wheel crest.
    float spokeSegment = fract(a / 6.28318 * 8.0);
    float spokeDistance = min(spokeSegment, 1.0 - spokeSegment);
    float wheel = (1.0 - smoothstep(0.025, 0.060, spokeDistance))
        * step(0.16, r) * step(r, 0.82);
    wheel = max(wheel, ring(r, 0.34, 0.008) + ring(r, 0.52, 0.008));

    float wheelMix = smoothstep(-0.04, 0.15, v_color.b - v_color.r);
    float gold = smoothstep(0.55, 0.75, v_color.r)
        * smoothstep(0.45, 0.65, v_color.g);
    wheelMix = max(wheelMix, gold * 0.75);
    float sakuraMask = 1.0 - wheelMix * 0.85;

    float crest = rings + ticks
        + max(sakura * sakuraMask, wheel * wheelMix);
    crest = max(crest, ring(r, 0.10, 0.012)
        + (1.0 - smoothstep(0.0, 0.06, r)));

    if (crest < 0.15) {
        discard;
    }

    vec3 ink = v_color.rgb * (1.0 + 0.22 * sin(u_time * 2.0 + phase));
    ink = mix(ink, vec3(1.0, 0.94, 0.78), 0.15 * rings);
    gl_FragColor = vec4(clamp(ink, 0.0, 1.0), 1.0);
}
