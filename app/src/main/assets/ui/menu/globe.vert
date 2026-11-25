attribute vec3 a_position;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
uniform vec3 u_globe;
uniform float u_turns;
varying mediump vec2 v_uv;
varying lowp vec4 v_ocean;
varying lowp vec3 v_landDelta;
varying lowp vec3 v_lines;

void main() {
    gl_Position = u_projTrans * vec4(u_globe.xy + a_position.xy * u_globe.z, 0.0, 1.0);
    v_uv = a_texCoord0 + vec2(u_turns, 0.0);
    float r = length(a_position.xy);
    vec3 halo = vec3(0.09, 0.27, 0.39);
    float haloAlpha = 0.19 * (1.0 - smoothstep(0.99, 1.105, r));
    if (a_position.z < -1.0) {
        v_ocean = vec4(halo, haloAlpha);
        v_landDelta = vec3(0.0);
        v_lines = vec3(0.0);
        return;
    }

    float light = smoothstep(-0.42, 0.88, dot(a_position, vec3(-0.52, 0.63, 0.57)));
    float twilight = smoothstep(0.25, 0.95, dot(a_position, vec3(-0.72, -0.69, 0.0)));
    vec3 sea = mix(vec3(0.013, 0.025, 0.043), vec3(0.037, 0.100, 0.153), light);
    vec3 land = mix(vec3(0.017, 0.035, 0.052), vec3(0.050, 0.119, 0.171), light);
    v_landDelta = land - sea;
    vec3 rim = mix(halo, vec3(0.28, 0.14, 0.073), twilight * 0.58)
            * smoothstep(0.95, 0.995, r) * mix(0.24, 0.49, light);
    v_ocean = vec4(sea + rim, mix(1.0, haloAlpha, smoothstep(0.994, 1.0, r)));
    vec3 lineColor = mix(vec3(0.34, 0.78, 0.94), vec3(0.53, 0.30, 0.17),
                         twilight * (1.0 - light) * 0.48);
    v_lines = lineColor * smoothstep(0.025, 0.38, a_position.z) * mix(0.40, 0.94, light);
}
