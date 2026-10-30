shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_disabled;
#include "res://desert_battlefield/soil.gdshaderinc"
uniform float trail_start_distance = 0.0;
void vertex() {
	VERTEX.y -= sand_depth(VERTEX.xz);
	NORMAL = sand_normal(VERTEX.xz, NORMAL);
}
void fragment() {
	float edge = smoothstep(0.0, 0.15, UV.x) * (1.0 - smoothstep(0.85, 1.0, UV.x));
	float phase = UV.y * 5.2 + abs(UV.x - 0.5) * 0.65;
	float aa = max(0.08, fwidth(phase) * 6.283185);
	float detail = 1.0 - smoothstep(0.25, 0.8, fwidth(phase));
	float tread = mix(0.5, smoothstep(-aa, aa, sin(phase * 6.283185)), detail);
	ALBEDO = mix(vec3(0.38, 0.29, 0.19), vec3(0.50, 0.39, 0.26), tread);
	ROUGHNESS = 1.0;
	ALPHA = edge * mix(0.19, 0.06, tread) * smoothstep(trail_start_distance, trail_start_distance + 4.0, UV.y);
}
