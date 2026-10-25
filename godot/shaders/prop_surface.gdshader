shader_type spatial;
render_mode diffuse_burley;
uniform sampler2DArray world_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform sampler2DArray terrain_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform vec4 tint : source_color = vec4(0.6, 0.5, 0.4, 1.0);
uniform int surface_kind = 0;
uniform float roughness_value = 0.85;
uniform float metallic_value = 0.0;
varying vec3 position_world;
varying vec3 normal_world;
vec3 tile(vec2 p, float layer) {
	vec2 uv = mix(vec2(0.004), vec2(0.996), abs(fract(p) * 2.0 - 1.0));
	vec3 value = surface_kind == 4 ? texture(terrain_atlas, vec3(uv, 1.0)).rgb : texture(world_atlas, vec3(uv, layer)).rgb;
	return OUTPUT_IS_SRGB ? value : pow(max(value, vec3(0.001)), vec3(0.454545));
}
void vertex() {
	position_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	normal_world = normalize(MODEL_NORMAL_MATRIX * NORMAL);
}
void fragment() {
	vec3 weights = pow(abs(normal_world), vec3(5.0));
	weights /= max(dot(weights, vec3(1.0)), 0.001);
	// Binary tile periods divide the 2048 m origin step exactly, so texture
	// patterns keep their phase when the scene moves near the origin.
	float scale = surface_kind == 3 ? 0.25 : (surface_kind == 4 ? 0.125 : 0.0625);
	vec3 p = position_world * scale;
	float layer = float(min(surface_kind, 3));
	vec3 sample_color = tile(p.zy, layer) * weights.x + tile(p.xz, layer) * weights.y + tile(p.xy, layer) * weights.z;
	vec3 average = surface_kind == 0 ? vec3(0.506, 0.399, 0.312) : (surface_kind == 1 ? vec3(0.720, 0.667, 0.614) : (surface_kind == 2 ? vec3(0.493, 0.476, 0.461) : (surface_kind == 3 ? vec3(0.259, 0.338, 0.139) : vec3(0.472, 0.418, 0.359))));
	vec3 color = OUTPUT_IS_SRGB ? tint.rgb : pow(max(tint.rgb, vec3(0.001)), vec3(0.454545));
	vec3 detail = clamp(sample_color / average, vec3(0.60), vec3(1.35));
	float footprint = length(fwidth(position_world));
	color *= mix(vec3(1.0), detail, 0.55 * (1.0 - smoothstep(0.025, 0.20, footprint)));
	float luma = dot(sample_color, vec3(0.2126, 0.7152, 0.0722));
	vec3 r1 = cross(dFdy(VERTEX), NORMAL);
	vec3 r2 = cross(NORMAL, dFdx(VERTEX));
	float det = dot(dFdx(VERTEX), r1);
	vec3 gradient = (r1 * dFdx(luma) + r2 * dFdy(luma)) * sign(det) / max(abs(det), 0.00001);
	float detail_fade = 1.0 - smoothstep(0.025, 0.16, footprint);
	NORMAL = normalize(NORMAL - gradient * 0.001 * detail_fade);
	color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.7);
	ALBEDO = pow(max(color, vec3(0.001)), vec3(2.2));
	ROUGHNESS = clamp(roughness_value + (luma - 0.5) * 0.12, 0.25, 1.0);
	METALLIC = metallic_value;
	SPECULAR = 0.25;
}
