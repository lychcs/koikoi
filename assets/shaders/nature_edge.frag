#ifdef GL_ES
precision highp float;
#endif

// Alpha-aware normal/depth prepass for imported nature models. It matches
// ascii_edge.frag but discards cutout foliage so leaf cards do not become
// solid outline blobs.

varying vec3 v_world;
varying vec3 v_normal;
varying vec2 v_texCoord0;

uniform sampler2D u_diffuseTexture;
uniform float u_alphaTest;
uniform mat4 u_view;
uniform float u_far;

void main() {
    if (texture2D(u_diffuseTexture, v_texCoord0).a < u_alphaTest) {
        discard;
    }

    vec3 viewNormal = normalize((u_view * vec4(normalize(v_normal), 0.0)).xyz);
    vec4 viewPosition = u_view * vec4(v_world, 1.0);
    float depth = clamp(-viewPosition.z / max(u_far, 0.001), 0.0, 1.0);

    float packed = depth * 65535.0;
    float high = floor(packed / 256.0);
    float low = packed - high * 256.0;

    gl_FragColor = vec4(
        viewNormal.xy * 0.5 + 0.5,
        low / 255.0,
        high / 255.0);
}
