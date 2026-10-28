shader_type spatial;
render_mode cull_disabled, specular_disabled;
varying vec4 track_data;
varying vec3 track_world;
void vertex() {
	track_data = INSTANCE_CUSTOM;
	track_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}
void fragment() {
	float kind = floor(track_data.a * 8.0 + 0.5);
	float center = 1.0 - smoothstep(0.25, 0.35, abs(UV.y - 0.5));
	float tread = smoothstep(0.3, 0.7, sin(UV.x * 18.84956 + abs(UV.y - 0.5) * 16.0) * 0.5 + 0.5);
	float contrast = kind == 5.0 ? 0.03 : kind == 2.0 ? 0.06 : (kind == 0.0 ? 0.055 : 0.12);
	float grain = fract(sin(dot(floor(track_world.xz * 110.0), vec2(12.9898, 78.233))) * 43758.5453);
	float feather = smoothstep(0.0, 0.16, min(UV.y, 1.0 - UV.y));
	if (grain > feather) discard;
	vec3 pigment = OUTPUT_IS_SRGB ? pow(max(track_data.rgb, vec3(0.001)), vec3(0.454545)) : track_data.rgb;
	ALBEDO = pigment * (1.0 - center * (0.07 + tread * contrast) + (grain - 0.5) * 0.025);
	ROUGHNESS = kind == 4.0 ? 0.68 : 1.0;
}
