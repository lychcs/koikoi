#ifdef GL_ES
precision mediump float;
#endif

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;

varying vec3 v_world;
varying vec3 v_normal;
varying vec2 v_texCoord0;

void main() {
    vec4 world = u_worldTrans * vec4(a_position, 1.0);
    v_world = world.xyz;
    // Instances use uniform scale, so the upper 3x3 matrix is safe to use for
    // the normal; normalize() removes the scale factor.
    v_normal = normalize(mat3(u_worldTrans) * a_normal);
    v_texCoord0 = a_texCoord0;
    gl_Position = u_projViewTrans * world;
}
