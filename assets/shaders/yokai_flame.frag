#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_time;

// Organische Wellenüberlagerung für aufsteigende Flammenzungen
float flameNoise(vec2 p) {
    float n = 0.0;
    n += 0.5000 * sin(p.x * 6.0 + sin(p.y * 4.0 + u_time * 3.5));
    n += 0.2500 * sin(p.x * 12.0 - p.y * 8.0 + u_time * 5.0);
    n += 0.1250 * sin(p.x * 24.0 + p.y * 16.0 - u_time * 7.0);
    return (n + 0.875) / 1.75;
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    if (texColor.a < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    vec2 uv = v_texCoords;

    // Aufwärtsbewegung der Flammen
    vec2 flameUv = vec2(uv.x * 1.8, uv.y * 1.6 - u_time * 1.4);
    float n = flameNoise(flameUv);

    float distFromCenter = abs(uv.x - 0.5) * 2.0;
    float flameMask = smoothstep(1.0 - uv.y, 0.0, distFromCenter);

    if (flameMask < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    // Farbverlauf: Schwarz/Violett -> Dunkellila -> Neon-Magenta -> Glühkern
    vec3 voidBlack = vec3(0.06, 0.01, 0.10);
    vec3 voidPurple = vec3(0.52, 0.05, 0.95);
    vec3 voidNeon = vec3(0.88, 0.25, 1.0);
    vec3 coreWhite = vec3(0.98, 0.88, 1.0);

    float intensity = n * (1.1 - uv.y * 0.4);
    vec3 fire = mix(voidBlack, voidPurple, smoothstep(0.15, 0.45, intensity));
    fire = mix(fire, voidNeon, smoothstep(0.45, 0.75, intensity));
    fire = mix(fire, coreWhite, smoothstep(0.75, 0.98, intensity));

    // Kombiniert Texturdetails mit den lila Void-Flammen
    vec3 finalRgb = mix(texColor.rgb, fire, 0.7) * 1.35;

    gl_FragColor = vec4(finalRgb, texColor.a) * v_color;
}
