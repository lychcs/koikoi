#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;
varying vec3 v_world;

uniform sampler2D u_glyphAtlas;
uniform vec3 u_cameraPos;
uniform vec3 u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;

/**
 * Direct 3D ASCII magic glyphs.
 *
 * <p>Magic billboards and dash ghosts are queued as glyph atlas quads, so this
 * pass is the only place ASCII magic exists. Cards, Kamon seals and Ofuda use
 * their own procedural shaders. Nothing is post-quantized, which makes false
 * glyph overlays on scenery impossible.</p>
 */
void main() {
    float mark = texture2D(u_glyphAtlas, v_texCoords).a;
    if (mark < 0.5) {
        discard;
    }

    vec3 ink = v_color.rgb * 1.18;
    float depth = distance(v_world, u_cameraPos);
    float fog = 1.0 - exp(-pow(max(depth - u_fogStart, 0.0) * 0.014, 1.25));
    ink = mix(ink, u_fogColor, clamp(fog, 0.0, 0.82));

    // Opaque glyph pixels: no alpha tag, no blending, no post ambiguity.
    gl_FragColor = vec4(ink, 1.0);
}
