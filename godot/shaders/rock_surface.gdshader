shader_type spatial;
render_mode diffuse_burley, cull_disabled;
uniform sampler2D rock_albedo : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D micro_height : filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 tint : source_color = vec4(0.59, 0.55, 0.46, 1.0);
uniform float snow_cover : hint_range(0.0, 1.0) = 0.0;
uniform float cutaway_amount = 0.0;
uniform vec2 cutaway_center = vec2(0.5);
uniform vec2 cutaway_radius = vec2(0.23, 0.30);
varying vec3 position_world;
varying vec3 normal_world;
vec3 stone_tile(vec2 p) {
	// Eight metres divides the floating-origin step: rebasing cannot slide
	// the geological strata across a rock or the mountain above a tunnel.
	vec2 uv = p / 8.0;
	return texture(rock_albedo, uv).rgb;
}
void vertex() {
	position_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	normal_world = normalize(MODEL_NORMAL_MATRIX * NORMAL);
}
void fragment() {
	float window = (1.0 - smoothstep(0.75, 1.0, length((SCREEN_UV - cutaway_center) / cutaway_radius))) * cutaway_amount;
	// Ordered screen dither keeps the surviving mountain opaque, with no
	// transparent sorting or world-space hole misplaced behind a tall roof.
	vec2 cell = mod(floor(FRAGCOORD.xy), 4.0);
	float threshold = mod(cell.x * 2.0 + cell.y * 3.0, 4.0) / 4.0 + 0.125;
	if (window > threshold) { discard; }
	// Blend projections continuously around curved rock rather than switching
	// between three axes at a visible seam. Period divides the origin step.
	vec3 weights = pow(abs(normalize(normal_world)), vec3(4.0));
	weights /= max(dot(weights, vec3(1.0)), 0.001);
	vec3 stone = stone_tile(position_world.zy) * weights.x + stone_tile(position_world.xz) * weights.y + stone_tile(position_world.xy) * weights.z;
	stone = OUTPUT_IS_SRGB ? stone : pow(max(stone, vec3(0.001)), vec3(0.454545));
	float height = dot(stone, vec3(0.2126, 0.7152, 0.0722));
	float detail_fade = 1.0 - smoothstep(0.12, 0.65, length(fwidth(position_world)));
	vec3 base = OUTPUT_IS_SRGB ? tint.rgb : pow(max(tint.rgb, vec3(0.001)), vec3(0.454545));
	vec3 color = base * mix(vec3(1.0), vec3(clamp(height / 0.470045, 0.65, 1.35)), 0.75 * detail_fade);
	color = mix(color, vec3(0.61, 0.67, 0.68), snow_cover * smoothstep(0.08, 0.75, normal_world.y) * 0.75);
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
