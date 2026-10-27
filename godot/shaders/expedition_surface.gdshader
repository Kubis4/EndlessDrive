shader_type spatial;
render_mode diffuse_burley, cull_disabled;
// Actual image-based albedo, projected in model space. Textures travel with
// the train/wheels and survive origin rebases; no UV seams or alpha noise.
uniform sampler2DArray model_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform bool texture_enabled = false;
uniform int material_layer = 0;
uniform vec4 texture_average : source_color = vec4(0.5, 0.5, 0.5, 1.0);
uniform vec4 snow_average : source_color = vec4(0.72, 0.77, 0.81, 1.0);
uniform float texture_scale = 0.125;
uniform float texture_strength : hint_range(0.0, 1.0) = 0.75;
uniform float texture_roughness = 0.92;
uniform vec4 tint : source_color = vec4(0.45, 0.48, 0.36, 1.0);
uniform float roughness_value = 0.92;
uniform float metallic_value = 0.0;
uniform float snow_cover : hint_range(0.0, 1.0) = 0.0;
varying vec3 local_position;
varying vec3 local_normal;
vec3 tile(vec2 p, float layer) {
	// Independent array layers prevent masonry/leaf/metal mip bleeding.
	// Mirror inside a small inset rather than assuming AI edge continuity.
	vec2 uv = mix(vec2(0.005), vec2(0.995), abs(fract(p) * 2.0 - 1.0));
	return texture(model_atlas, vec3(uv, layer)).rgb;
}
vec3 triplanar(vec3 p, vec3 weights, float layer) {
	return tile(p.zy, layer) * weights.x + tile(p.xz, layer) * weights.y + tile(p.xy, layer) * weights.z;
}
void vertex() {
	local_position = VERTEX;
	local_normal = NORMAL;
}
void fragment() {
	float broad = sin(local_position.x * 0.31 + local_position.y * 0.18) * sin(local_position.z * 0.27 - local_position.y * 0.11);
	vec3 color = tint.rgb * (1.0 + broad * 0.045);
	vec3 weights = pow(abs(normalize(local_normal)), vec3(4.0));
	weights /= max(dot(weights, vec3(1.0)), 0.001);
	// Leaves remain readable close up but their many small silhouettes must
	// not become one-pixel grain at the normal driving-camera distance.
	float fade_start = material_layer == 3 ? 0.04 : 0.18;
	float fade_end = material_layer == 3 ? 0.25 : 0.9;
	float texture_fade = 1.0 - smoothstep(fade_start, fade_end, length(fwidth(local_position)));
	vec3 snow_detail = vec3(1.0);
	float wear = 0.5;
	if (texture_enabled) {
		vec3 sampled = triplanar(local_position * texture_scale, weights, float(material_layer));
		vec3 detail = clamp(sampled / max(texture_average.rgb, vec3(0.001)), vec3(0.55), vec3(1.5));
		color *= mix(vec3(1.0), detail, texture_strength * texture_fade);
		wear = dot(sampled, vec3(0.2126, 0.7152, 0.0722));
		if (snow_cover > 0.01) {
			vec3 frost = triplanar(local_position * 0.13, weights, 7.0);
			snow_detail = mix(vec3(1.0), clamp(frost / max(snow_average.rgb, vec3(0.001)), vec3(0.8), vec3(1.2)), texture_fade * 0.5);
		}
	}
	float cap = smoothstep(0.32, 0.78, local_normal.y) * snow_cover;
	color = mix(color, vec3(0.60, 0.66, 0.65) * snow_detail, cap * 0.9);
	// Small material relief on the actual surface; no parallax or silhouette
	// displacement that could separate the visible rock from its collision.
	vec3 r1 = cross(dFdy(VERTEX), NORMAL);
	vec3 r2 = cross(NORMAL, dFdx(VERTEX));
	float det = dot(dFdx(VERTEX), r1);
	vec3 gradient = (r1 * dFdx(wear) + r2 * dFdy(wear)) * sign(det) / max(abs(det), 0.00001);
	float depth = material_layer == 3 || material_layer == 6 ? 0.006 : 0.045;
	vec3 relief = gradient * depth * texture_fade * (1.0 - cap * 0.7);
	relief /= max(1.0, length(relief) / 0.3);
	NORMAL = normalize(NORMAL - relief);
	ALBEDO = color;
	ROUGHNESS = texture_enabled ? clamp(mix(roughness_value, texture_roughness, 0.7) + (wear - 0.5) * 0.16, 0.3, 1.0) : roughness_value;
	METALLIC = metallic_value;
	SPECULAR = 0.18;
}
