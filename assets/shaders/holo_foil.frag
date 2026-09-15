#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_time;

// Atlas-Region des Hanko-Icons (u, v, uWidth, vHeight)
uniform vec4 u_region;

// ========== Parameter mit Werten aus deinem Screenshot ==========
const float u_iridescenceScale     = 0.5;   // iridescence_scale
const float u_iridescenceIntensity = 0.24;  // iridescence_intensity
const float u_fresnelPower         = 1.0;   // fresnel_power
const float u_emissionStrength     = 0.73;  // emission_strength
const float u_flowSpeed            = 0.31;  // flow_speed
const float u_flowDirection        = 1.01;  // flow_direction
const vec3  u_baseMetalColor       = vec3(1.0, 1.0, 1.0); // base_metal_color (Weiss)

// HSV zu RGB Konvertierung
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 tex = texture2D(u_texture, v_texCoords);

    // Alpha-Maskierung: Transparente Bereiche des Stempels ignorieren
    if (tex.a < 0.02) {
        discard;
    }

    // Lokale UV-Koordinaten des Hankos (0.0 bis 1.0)
    vec2 local_uv = (v_texCoords - u_region.xy) / u_region.zw;

    // 2D-Fresnel-Approximation: Wölbung von der Mitte (1.0) zum Rand (0.0)
    float distFromCenter = clamp(length(local_uv - vec2(0.5)) * 1.414, 0.0, 1.0);
    float NdotV = 1.0 - distFromCenter;
    float fresnel = pow(1.0 - NdotV, u_fresnelPower);

    // Zeit- und richtungsgesteuerter Farbverlauf (Regenbogen)
    float flow = u_time * u_flowSpeed;
    float hue = fract(
        (1.0 - NdotV) * u_iridescenceScale * 0.7 +
        local_uv.x * u_flowDirection +
        local_uv.y * u_flowDirection * 0.6 +
        flow
    );

    vec3 rainbow = hsv2rgb(vec3(hue, 0.92, 1.0));

    // Metallische Grundfarbe mit Regenbogen und Fresnel mischen
    vec3 holo_color = mix(u_baseMetalColor, rainbow, u_iridescenceIntensity);
    holo_color = mix(holo_color, rainbow * 1.25, fresnel * 0.55);

    // Auf das Stempel-Sprite legen
    vec3 final_rgb = mix(tex.rgb, holo_color, u_iridescenceIntensity);
    final_rgb = mix(final_rgb, holo_color, fresnel * 0.4);

    // Leuchteffekt / Emission
    final_rgb += holo_color * u_emissionStrength * (0.35 + fresnel * 0.65);

    gl_FragColor = vec4(final_rgb * v_color.rgb, tex.a * v_color.a);
}
