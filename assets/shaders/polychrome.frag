#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_time;

// Exakter Kosinus-Prismaverlauf für extrem satte Spektralfarben
vec3 spectral(float t) {
    vec3 a = vec3(0.5, 0.5, 0.5);
    vec3 b = vec3(0.5, 0.5, 0.5);
    vec3 c = vec3(1.0, 1.0, 1.0);
    vec3 d = vec3(0.00, 0.33, 0.67);
    return a + b * cos(6.28318 * (c * t + d));
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    if (texColor.a < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    vec2 uv = v_texCoords;

    // 1. Balatro-Style: Nicht-lineare Wellenverzerrung (Liquid Holo)
    float waveX = sin(uv.x * 7.0 + u_time * 1.8);
    float waveY = cos(uv.y * 7.0 - u_time * 1.4);
    float distortion = (waveX + waveY) * 0.12;

    // 2. Irisierender Farbverlauf
    float spectrumCoord = fract((uv.x + uv.y * 0.8) * 0.75 + distortion - (u_time * 0.3));
    vec3 holoRainbow = spectral(spectrumCoord);

    // 3. Wandernder Glanzstreifen (Holo-Foil-Reflexion)
    float sheenPos = fract((uv.x * 0.8 - uv.y * 1.2) - (u_time * 0.55));
    float sheen = smoothstep(0.40, 0.48, sheenPos) * (1.0 - smoothstep(0.48, 0.56, sheenPos));
    sheen = pow(sheen * 2.0, 4.0) * 1.6;

    // 4. Verschmelzung: Regenbogen auf die Textur + harter weißer Glanzpunkt
    vec3 result = (texColor.rgb * holoRainbow * 1.5) + vec3(sheen);

    gl_FragColor = vec4(result, texColor.a) * v_color;
}
