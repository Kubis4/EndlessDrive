shader_type spatial;
render_mode diffuse_burley;
uniform vec4 tint : source_color = vec4(0.73, 0.73, 0.67, 1.0);
uniform float body_damage : hint_range(0.0, 1.0) = 0.0;
uniform sampler2D albedo_texture : source_color, filter_linear_mipmap, repeat_enable;
uniform sampler2D roughness_texture : filter_linear_mipmap, repeat_enable;
uniform sampler2D metalness_texture : filter_linear_mipmap, repeat_enable;
varying float dent_mask;
void vertex() {
	vec3 p = VERTEX;
	dent_mask = exp(-dot((p - vec3(1.65, 0.48, 0.75)) * vec3(1.3, 1.5, 1.5), (p - vec3(1.65, 0.48, 0.75)) * vec3(1.3, 1.5, 1.5)));
	// Shared vertices at UV/material seams must move identically.
	VERTEX -= vec3(0.18, 0.12, 0.14) * dent_mask * body_damage;
}
// The original atlas contains several copies of the registration number.
// Keep the plate itself, but replace the printed area with a neutral surface.
bool in_rect(vec2 uv, vec2 min_uv, vec2 max_uv) {
	return all(greaterThanEqual(uv, min_uv)) && all(lessThanEqual(uv, max_uv));
}
void fragment() {
	vec3 base = texture(albedo_texture, UV).rgb;
	bool plate = in_rect(UV, vec2(0.322, 0.078), vec2(0.338, 0.162))
		|| in_rect(UV, vec2(0.241, 0.124), vec2(0.256, 0.207))
		|| in_rect(UV, vec2(0.404, 0.422), vec2(0.510, 0.443))
		|| in_rect(UV, vec2(0.404, 0.458), vec2(0.510, 0.477));
	if (plate) base = vec3(0.57, 0.56, 0.52);
	base *= 1.0 - dent_mask * body_damage * 0.22;
	ALBEDO = mix(base, base * tint.rgb, COLOR.a);
	ROUGHNESS = texture(roughness_texture, UV).r;
	METALLIC = texture(metalness_texture, UV).r;
}
