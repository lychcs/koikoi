#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_lightCenter;
uniform float u_lightRadius;
uniform float u_ambient;
uniform vec3 u_shadowColor;
uniform float u_time;

void main() {
    vec4 texel = texture2D(u_texture, v_texCoords);

    // u_lightCenter is in normalized viewport space (0..1, bottom-left).
    // Convert x to vertical units so the lamp stays a circle in screen pixels.
    vec2 delta = v_texCoords - u_lightCenter;
    delta.x *= u_resolution.x / u_resolution.y;
    float distanceToLight = length(delta);

    // Very gentle lantern breathing. There is deliberately no per-pixel grain:
    // the shadow edge is a smooth gradient, not a dithered band.
    float flicker = 1.0 + 0.018 * sin(u_time * 3.7) + 0.010 * sin(u_time * 9.1 + 1.7);
    float radius = u_lightRadius * flicker;

    // Organic but soft edge: a low-frequency angular wobble perturbs the
    // radius, then a wide smoothstep feathers lit grass into shadow.
    float angle = atan(delta.y, delta.x);
    float organic = 0.94
        + 0.045 * sin(angle * 3.0 + u_time * 0.31)
        + 0.025 * sin(angle * 5.0 - u_time * 0.23);
    float edge = distanceToLight / max(0.0001, radius * organic);

    // Soft, wide lantern boost on top of a readable ambient map. The edge is
    // deliberately gentle: this is a warm local lift, not a spotlight in a void.
    float falloff = 1.0 - smoothstep(0.10, 1.15, edge);
    falloff = falloff * falloff * (3.0 - 2.0 * falloff);
    float core = 1.0 - smoothstep(0.0, 0.55, edge);
    float light = clamp(falloff * 0.82 + core * 0.18, 0.0, 1.0);

    // The global floor stays softly readable everywhere; the lantern adds a
    // warm ochre boost, never a harsh neon or pitch-black contrast.
    // Keep the hue in the shade: desaturating the shadow made the glyph
    // carpet collapse into flat grey pixels.
    vec3 shadowed = texel.rgb * (0.30 + 0.20 * u_ambient) + u_shadowColor * 0.08;

    vec3 lit = texel.rgb * mix(0.95, 1.12, light);
    lit += vec3(0.085, 0.065, 0.035) * light;

    vec3 color = mix(shadowed, lit, light);
    // Slight saturation shoulder so moss and entity ink stay distinct.
    float finalLuma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(finalLuma), color, 1.10);
    color = max(color, vec3(0.0));
    gl_FragColor = vec4(color, texel.a) * v_color;
}
