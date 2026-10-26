uniform float rain_wetness : hint_range(0.0, 1.0) = 0.0;
uniform bool surface_data_enabled = false;
uniform vec2 route_origin = vec2(0.0);
uniform sampler2DArray surface_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform sampler2D micro_height : filter_linear_mipmap_anisotropic, repeat_enable;
uniform float detail_strength : hint_range(0.0, 1.0) = 0.22;
varying vec3 world_position;
varying vec4 materials_a;
varying vec4 materials_b;
varying vec4 materials_c;
varying vec4 materials_d;
varying vec3 rest_view_position;

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}
vec3 surface_tile(vec2 p, vec2 tile, vec3 average) {
	// Mirror each tile locally: repeat cannot sample the neighbouring material
	// or introduce a hard seam at the edges of the generated swatch.
	vec2 uv = abs(fract(p * 0.0625) * 2.0 - 1.0);
	uv = mix(vec2(0.004), vec2(0.996), uv);
	vec3 sampled = max(texture(surface_atlas, vec3(uv, tile.x + tile.y * 2.0)).rgb, vec3(0.001));
	vec3 texel = OUTPUT_IS_SRGB ? sampled : pow(sampled, vec3(0.454545));
	return clamp(texel / average, vec3(0.65), vec3(1.35));
}
void vertex() {
	world_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	world_position.xz += route_origin;
	materials_a = CUSTOM0;
	materials_b = CUSTOM1;
	materials_c = CUSTOM2;
	materials_d = CUSTOM3;
	rest_view_position = (MODELVIEW_MATRIX * vec4(VERTEX.x, CUSTOM3.z, VERTEX.z, 1.0)).xyz;
}
void fragment() {
	vec2 p = world_position.xz;
	vec3 geometric_normal = normalize(cross(dFdx(VERTEX), dFdy(VERTEX)));
	if (dot(geometric_normal, NORMAL) < 0.0) geometric_normal = -geometric_normal;
	if (surface_data_enabled && materials_d.w > 0.5) {
		vec3 rest_normal = normalize(cross(dFdx(rest_view_position), dFdy(rest_view_position)));
		if (dot(rest_normal, NORMAL) < 0.0) rest_normal = -rest_normal;
		NORMAL = normalize(NORMAL + geometric_normal - rest_normal);
	}
	vec4 a = surface_data_enabled ? materials_a : vec4(1.0 - step(0.5, UV2.y), 0.0, 0.0, 0.0);
	vec4 b = surface_data_enabled ? materials_b : vec4(0.0, 0.0, 0.0, step(0.5, UV2.y));
	vec4 c = surface_data_enabled ? materials_c : vec4(0.0);
	vec4 d = surface_data_enabled ? materials_d : vec4(0.0);
	float sand = a.x;
	float gravel = a.y;
	float dirt = a.z;
	float mud = a.w + d.y;
	float grass = b.x;
	float rock = b.y;
	float snow = b.z;
	float asphalt = b.w;
	float concrete = c.x;
	float clay = c.y;
	float salt = c.z;
	float leaves = c.w;
	float ice = d.x;
	float wet = clamp(max(surface_data_enabled ? UV.y : 0.0, rain_wetness), 0.0, 1.0);
	// Signed displacement comes from the physical soil vertices. Excavated
	// vegetation exposes earth within the actual channel and raised banks.
	float soil_delta = surface_data_enabled ? d.w - 1.0 : 0.0;
	float exposure = smoothstep(0.008, 0.07, abs(soil_delta));
	float bare_earth = (grass + leaves) * exposure;
	dirt += bare_earth * (1.0 - wet);
	mud += bare_earth * wet;
	grass *= 1.0 - exposure;
	leaves *= 1.0 - exposure;
	float pixel = length(fwidth(p));
	vec4 detail_weights = vec4(sand + snow + salt, gravel + rock + asphalt + concrete + ice, dirt + mud + clay + leaves, grass);
	detail_weights /= max(dot(detail_weights, vec4(1.0)), 0.001);
	vec3 photographic = vec3(1.0);
	// Compile-time tiers remove unused work without divergent derivatives at
	// biome/soil transitions. Balanced retains photographic albedo.
	if (TERRAIN_DETAIL_TIER > 0) {
		photographic = surface_tile(p, vec2(0.0), vec3(0.730, 0.569, 0.396)) * detail_weights.x;
		photographic += surface_tile(p, vec2(1.0, 0.0), vec3(0.472, 0.418, 0.359)) * detail_weights.y;
		photographic += surface_tile(p, vec2(0.0, 1.0), vec3(0.346, 0.239, 0.161)) * detail_weights.z;
		photographic += surface_tile(p, vec2(1.0), vec3(0.351, 0.374, 0.151)) * detail_weights.w;
	}
	// Keep fine photographic contrast without multiplying the grass/earth
	// palette's saturation a second time into neon patches.
	float detail_luma = dot(photographic, vec3(0.299, 0.587, 0.114));
	photographic = mix(vec3(detail_luma), photographic, 0.55);
	float height_detail = 0.5;
	if (TERRAIN_DETAIL_TIER == 2) height_detail = dot(texture(micro_height, p * 0.125), detail_weights);
	float detail_fade = 1.0 - smoothstep(0.025, 0.16, pixel);
	float fine_fade = 1.0 - smoothstep(0.02, 0.10, pixel);
	float broad = noise(p * 0.12);
	float medium = broad;
	float grain = 0.5;
	if (TERRAIN_DETAIL_TIER == 2) {
		medium = noise(p * 0.8);
		grain = noise(p * 6.0);
	}
	float texture_value = 1.0 + (broad - 0.5) * 0.14;
	vec3 color = mix(COLOR.rgb, vec3(0.50, 0.44, 0.36), bare_earth);
	// Distant sand reads as a continuous dune, not a repeated crumpled sheet.
	// Keep the high-resolution source for close-up grains and other materials.
	// Sand is the style reference for every biome: quiet photographic
	// contrast and broad colour masses rather than shimmering grit.
	float sand_detail = dot(detail_weights, vec4(0.18, 0.28, 0.30, 0.22));
	color *= mix(vec3(1.0), photographic, detail_strength * detail_fade * sand_detail);
	color *= 1.0 + (height_detail - 0.5) * 0.06 * detail_fade * sand_detail;
	// Small wind ripples belong only to sand; dirt, grass and rock use their
	// own world-locked structure instead of sharing a diagonal stripe pattern.
	float ripple_phase = dot(p, vec2(1.8, 5.5));
	if (TERRAIN_DETAIL_TIER == 2) ripple_phase += noise(p * 0.35) * 2.5;
	texture_value += sand * (sin(ripple_phase) * 0.012 * fine_fade + (grain - 0.5) * 0.008 * fine_fade);
	float pebble = smoothstep(0.48, 0.77, medium);
	texture_value += (gravel + rock) * ((medium - 0.5) * 0.035 + pebble * 0.012 + (grain - 0.5) * 0.004 * fine_fade);
	texture_value += dirt * ((medium - 0.5) * 0.035 + (grain - 0.5) * 0.003 * fine_fade);
	texture_value += mud * ((medium - 0.5) * 0.035 + (broad - 0.5) * 0.055);
	float blades = 0.0;
	if (TERRAIN_DETAIL_TIER == 2) blades = sin(p.x * 12.0 + sin(p.y * 4.0)) * sin(p.y * 18.0);
	texture_value += grass * ((broad - 0.5) * 0.06 + blades * 0.002 * fine_fade);
	texture_value += leaves * ((medium - 0.5) * 0.035 + (grain - 0.5) * 0.003 * fine_fade);
	float cracks = 0.0;
	if (TERRAIN_DETAIL_TIER == 2) cracks = 1.0 - smoothstep(0.004, 0.018, abs(noise(p * 1.4) - 0.50));
	texture_value -= (clay * 0.08 + salt * 0.045) * cracks * fine_fade;
	texture_value += snow * ((broad - 0.5) * 0.035 + (grain - 0.5) * 0.008 * fine_fade);
	texture_value += asphalt * (grain - 0.5) * 0.008 * fine_fade;
	vec2 slab = abs(fract(p * 0.20) - 0.5);
	float joints = smoothstep(0.48, 0.495, max(slab.x, slab.y));
	texture_value -= concrete * joints * 0.13;
	texture_value += concrete * (medium - 0.5) * 0.05;
	float lateral = abs(UV2.x);
	// Follow the same signed perpendicular road coordinate used by physics.
	// A graded shoulder makes the road readable; tyre channels are geometry.
	float shoulder_edge = smoothstep(4.8, 5.4, lateral) * (1.0 - smoothstep(5.8, 6.8, lateral));
	// Tyre marks come from physical soil, not a painted repeated tread.
	color = mix(color, color * 1.05, shoulder_edge * (1.0 - snow));
	float center_line = (1.0 - smoothstep(0.08, 0.15, lateral)) * step(3.0, mod(p.x, 9.0));
	float edge_line = 1.0 - smoothstep(0.06, 0.15, abs(lateral - 3.65));
	color = mix(color, vec3(0.77, 0.72, 0.54), max(center_line, edge_line) * asphalt * 0.65);
	color *= texture_value;
	color *= 1.0 - smoothstep(0.01, 0.24, max(soil_delta, 0.0)) * 0.10;
	color *= 1.0 - wet * (0.12 * dirt + 0.15 * clay + 0.20 * asphalt + 0.13 * rock + 0.08 * sand + 0.18 * leaves + 0.15 * grass);
	float bump = (medium - 0.5) * (gravel * 0.008 + rock * 0.01 + mud * 0.008 + leaves * 0.006);
	vec3 relief = vec3(bump, 0.0, -bump * 0.7);
	relief += vec3(-cos(ripple_phase) * 0.004, 0.0, -cos(ripple_phase) * 0.009) * sand * fine_fade;
	NORMAL = normalize(NORMAL + (VIEW_MATRIX * vec4(relief, 0.0)).xyz);
	// Derivative-based bump works with the existing deformable mesh, which
	// intentionally has no tangent stream. Heights stay locked to world space.
	if (TERRAIN_DETAIL_TIER == 2) {
		vec3 r1 = cross(dFdy(VERTEX), NORMAL);
		vec3 r2 = cross(NORMAL, dFdx(VERTEX));
		float determinant = dot(dFdx(VERTEX), r1);
		vec3 gradient = (r1 * dFdx(height_detail) + r2 * dFdy(height_detail)) * sign(determinant) / max(abs(determinant), 0.00001);
		float relief_depth = dot(detail_weights, vec4(0.0005, 0.0006, 0.0004, 0.0002));
		NORMAL = normalize(NORMAL - gradient * relief_depth * detail_fade);
	}
	color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.68);
	ALBEDO = pow(max(color, vec3(0.0)), vec3(2.2));
	ROUGHNESS = clamp(0.96 - wet * (mud * 0.35 + asphalt * 0.42 + rock * 0.22 + leaves * 0.20 + grass * 0.15) - ice * 0.46, 0.35, 1.0);
	SPECULAR = 0.18 + ice * 0.20 + wet * 0.12;
}
