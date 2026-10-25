shader_type spatial;
render_mode diffuse_burley, cull_disabled;
uniform sampler2D stone_texture : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
varying vec3 local_position;
void vertex() { local_position = VERTEX; }
void fragment() {
	float strata = sin(local_position.y * 1.45 + sin(local_position.x * 0.025) * 0.65);
	vec3 sandstone = mix(vec3(0.36, 0.29, 0.23), vec3(0.58, 0.46, 0.34), smoothstep(-0.9, 0.9, strata));
	vec3 detail = texture(stone_texture, local_position.xy * 0.035).rgb;
	ALBEDO = sandstone * mix(vec3(0.85), vec3(1.12), dot(detail, vec3(0.333)));
	ROUGHNESS = 0.96;
	SPECULAR = 0.12;
}
