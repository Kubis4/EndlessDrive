shader_type spatial;
render_mode diffuse_burley, cull_disabled;
uniform sampler2DArray stone_texture : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
varying vec3 local_position;
void vertex() { local_position = VERTEX; }
void fragment() {
	float strata = sin(local_position.y * 0.8 + sin(local_position.x * 0.025) * 0.65);
	vec3 sandstone = mix(vec3(0.40, 0.33, 0.25), vec3(0.49, 0.40, 0.30), smoothstep(-0.9, 0.9, strata));
	vec2 uv = mix(vec2(0.004), vec2(0.996), abs(fract(local_position.xy * 0.03125) * 2.0 - 1.0));
	vec3 detail = texture(stone_texture, vec3(uv, 1.0)).rgb;
	ALBEDO = sandstone * (0.98 + dot(detail, vec3(0.333)) * 0.04);
	ROUGHNESS = 0.96;
	SPECULAR = 0.12;
}
