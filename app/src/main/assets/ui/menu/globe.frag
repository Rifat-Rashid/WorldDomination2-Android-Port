precision mediump float;
uniform sampler2D u_texture;
varying mediump vec2 v_uv;
varying lowp vec4 v_ocean;
varying lowp vec3 v_landDelta;
varying lowp vec3 v_lines;

void main() {
    vec3 map = texture2D(u_texture, v_uv).rgb;
    vec3 outline = v_lines * max(map.g, 0.86 * map.b);
    gl_FragColor = vec4(v_ocean.rgb + map.r * v_landDelta + outline, v_ocean.a);
}
