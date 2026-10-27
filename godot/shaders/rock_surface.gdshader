shader_type spatial;
render_mode diffuse_burley, cull_disabled;
uniform sampler2DArray surface_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform sampler2D micro_height : filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 tint : source_color = vec4(0.59, 0.55, 0.46, 1.0);
varying vec3 position_world;
varying vec3 normal_world;
vec3 stone_tile(vec2 p) {
	vec2 uv = mix(vec2(0.004), vec2(0.996), abs(fract(p * 0.125) * 2.0 - 1.0));
	return texture(surface_atlas, vec3(uv, 1.0)).rgb;
}
void vertex() {
	position_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	normal_world = normalize(MODEL_NORMAL_MATRIX * NORMAL);
}
void fragment() {
	// Blend projections continuously around curved rock rather than switching
	// between three axes at a visible seam. Period divides the origin step.
	vec3 weights = pow(abs(normalize(normal_world)), vec3(4.0));
	weights /= max(dot(weights, vec3(1.0)), 0.001);
	vec3 stone = stone_tile(position_world.zy) * weights.x + stone_tile(position_world.xz) * weights.y + stone_tile(position_world.xy) * weights.z;
	stone = OUTPUT_IS_SRGB ? stone : pow(max(stone, vec3(0.001)), vec3(0.454545));
	float height = dot(stone, vec3(0.2126, 0.7152, 0.0722));
	float detail_fade = 1.0 - smoothstep(0.12, 0.65, length(fwidth(position_world)));
	vec3 base = OUTPUT_IS_SRGB ? tint.rgb : pow(max(tint.rgb, vec3(0.001)), vec3(0.454545));
	vec3 color = base * mix(vec3(1.0), clamp(stone / vec3(0.472, 0.418, 0.359), vec3(0.5), vec3(1.6)), 0.75 * detail_fade);
	vec3 r1 = cross(dFdy(VERTEX), NORMAL);
	vec3 r2 = cross(NORMAL, dFdx(VERTEX));
	float det = dot(dFdx(VERTEX), r1);
	vec3 relief = (r1 * dFdx(height) + r2 * dFdy(height)) * sign(det) / max(abs(det), 0.00001) * 0.035 * detail_fade;
	relief /= max(1.0, length(relief) / 0.3);
	NORMAL = normalize(NORMAL - relief);
	color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.75);
	ALBEDO = pow(max(color, vec3(0.001)), vec3(2.2));
	ROUGHNESS = 0.86 + height * 0.12;
	SPECULAR = 0.17;
}
