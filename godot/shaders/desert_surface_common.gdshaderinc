uniform float rain_wetness : hint_range(0.0, 1.0) = 0.0;
uniform bool surface_data_enabled = false;
uniform vec2 route_origin = vec2(0.0);
uniform sampler2DArray surface_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D micro_height : filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D weathered_asphalt : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D road_weathering : filter_linear_mipmap_anisotropic, repeat_enable;
uniform float detail_strength : hint_range(0.0, 1.0) = 0.85;
varying vec3 world_position;
varying vec4 materials_a;
varying vec4 materials_b;
varying vec4 materials_c;
varying vec4 materials_d;
varying vec3 rest_view_position;

float hash(vec2 p) {
	// Integer arithmetic returns the same corner from either neighbouring
	// cell. The old sin/dot hash varied with floating-point contraction in
	// Compatibility, drawing large diagonal panels across quiet terrain.
	uvec2 cell = uvec2(ivec2(p));
	uint value = (cell.x * 1597334677u) ^ (cell.y * 3812015801u);
	value = (value ^ (value >> 16u)) * 2246822519u;
	value = (value ^ (value >> 13u)) * 3266489917u;
	value = value ^ (value >> 16u);
	return float(value & 0x00ffffffu) / 16777216.0;
}
float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}
vec3 surface_tile(vec2 uv, float layer, float average, vec2 gradient_x, vec2 gradient_y) {
	// New scanned-style source detail is independent of the biome hue.
	// Explicit gradients keep mipmaps stable across conditional layer samples.
	vec3 sampled = max(textureGrad(surface_atlas, vec3(uv, layer), gradient_x, gradient_y).rgb, vec3(0.001));
	vec3 texel = OUTPUT_IS_SRGB ? sampled : pow(sampled, vec3(0.454545));
	return vec3(clamp(dot(texel, vec3(0.299, 0.587, 0.114)) / average, 0.62, 1.38));
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
	float road_cover = surface_data_enabled ? COLOR.a : 1.0 - smoothstep(3.5, 7.0, abs(UV2.x));
	// Two mipmapped data samples replace repeating lane stripes and granular
	// road albedo. The rotated 137 m field warps the 32 m field, so its small
	// islands do not repeat at a fixed interval along the driving direction.
	vec2 rotated = mat2(vec2(0.8, 0.6), vec2(-0.6, 0.8)) * p;
	vec4 deposits = texture(road_weathering, rotated / 137.0 + vec2(0.37, 0.19));
	// Only the broad channel may warp coordinates. Using clustered green
	// here folds the mapping into stretched veins, especially on gravel.
	vec2 wear_position = p + (deposits.rr - vec2(0.5)) * vec2(1.25, -0.90);
	vec4 wear = texture(road_weathering, wear_position / 32.0);
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
	// biome/soil transitions. Balanced retains the crisp clustered albedo.
	if (TERRAIN_DETAIL_TIER > 0) {
		vec2 ground_position = p + (deposits.rr - vec2(0.5)) * vec2(0.8, -0.6);
		vec2 ground_uv = mat2(vec2(0.8, 0.6), vec2(-0.6, 0.8)) * ground_position / 6.0;
		vec2 gradient_x = dFdx(ground_uv);
		vec2 gradient_y = dFdy(ground_uv);
		photographic = vec3(0.0);
		// Most fragments need one or two layers, rather than four array reads.
		if (detail_weights.x > 0.001) photographic += surface_tile(ground_uv, 0.0, 0.67326, gradient_x, gradient_y) * detail_weights.x;
		// Larger stones and lower contrast avoid a field of tiny uniform grains.
		if (detail_weights.y > 0.001) {
			vec3 stone = surface_tile(ground_uv * 0.5, 1.0, 0.49579, gradient_x * 0.5, gradient_y * 0.5);
			photographic += mix(vec3(1.0), stone, 0.62) * detail_weights.y;
		}
		if (detail_weights.z > 0.001) photographic += surface_tile(ground_uv, 2.0, 0.45871, gradient_x, gradient_y) * detail_weights.z;
		if (detail_weights.w > 0.001) photographic += surface_tile(ground_uv, 3.0, 0.38662, gradient_x, gradient_y) * detail_weights.w;
	}
	// Neutral source layers retain cluster definition without multiplying the
	// grass/earth palette's saturation. Coloured replacement tiles stay muted.
	float detail_luma = dot(photographic, vec3(0.299, 0.587, 0.114));
	photographic = mix(vec3(detail_luma), photographic, 0.55);
	float height_detail = 0.5;
	if (TERRAIN_DETAIL_TIER == 2) height_detail = dot(texture(micro_height, p * 0.125), detail_weights);
	// Mipmaps retain material structure at the driving camera distance.
	// Fade only sub-pixel relief, not the baked cluster structure.
	float detail_fade = 1.0 - smoothstep(0.12, 0.65, pixel);
	float fine_fade = 1.0 - smoothstep(0.02, 0.10, pixel);
	// Reuse already sampled material data instead of evaluating multiple
	// procedural noise fields for every screen pixel and shadow variant.
	float broad = deposits.r;
	float medium = wear.r;
	float grain = wear.b;
	float texture_value = 1.0 + (broad - 0.5) * 0.055;
	vec3 color = mix(COLOR.rgb, vec3(0.50, 0.44, 0.36), bare_earth);
	// Distant sand reads as a continuous dune, not a repeated crumpled sheet.
	// Keep the high-resolution source for close-up grains and other materials.
	// Smooth asphalt/ice and snow must not inherit gravel sparkle or sand
	// ripples merely because they share a packed source texture channel.
	float sand_detail = sand * 0.42 + snow * 0.10 + salt * 0.24
		+ gravel * 0.64 + rock * 0.62 + dirt * 0.64 + mud * 0.50
		+ grass * 0.46 + concrete * 0.24
		+ clay * 0.50 + leaves * 0.50 + ice * 0.06;
	float natural_detail = 1.0 - smoothstep(0.0, 0.65, road_cover);
	// Dirt roads need the new fine earth structure too. Previously their
	// source texture was completely masked, leaving only blurry dark stains.
	float earth_detail = max(natural_detail, road_cover * clamp(dirt + mud + gravel + clay + leaves, 0.0, 1.0));
	color *= mix(vec3(1.0), photographic, detail_strength * detail_fade * sand_detail * earth_detail);
	color *= 1.0 + (height_detail - 0.5) * 0.015 * fine_fade * sand_detail * natural_detail;
	float islands = smoothstep(0.32, 0.69, wear.g * 0.65 + deposits.r * 0.35);
	float aggregate = clamp((wear.b - 0.72) * 1.8, 0.0, 0.35);
	float loose = gravel + dirt + mud + sand + snow + concrete + ice;
	// Every road family has its own quiet wear: blown sand, stony gravel,
	// earth patches, wet mud and dirty blue-grey snow.
	color *= 1.0 + road_cover * ((deposits.g - 0.5) * 0.04 + (wear.r - 0.5) * 0.035) * loose;
	float blown_sand = smoothstep(0.35, 0.72, wear.r * 0.65 + deposits.b * 0.35);
	color = mix(color, color * vec3(1.12, 1.11, 1.08), road_cover * sand * blown_sand * 0.25);
	color = mix(color, color * vec3(1.06, 1.02, 0.91), road_cover * gravel * islands * 0.45);
	color = mix(color, color * vec3(0.95, 0.95, 0.93), road_cover * (dirt * 0.22 + mud * 0.40) * islands);
	color = mix(color, color * vec3(0.76, 0.83, 0.87), road_cover * snow * islands * 0.50);
	color *= 1.0 + road_cover * (gravel * 0.08 + dirt * 0.025 + concrete * 0.020) * aggregate * detail_fade;
	color *= 1.0 - road_cover * (mud * 0.09 + concrete * 0.17 + ice * 0.06) * wear.a * detail_fade;
	// Small wind ripples belong only to sand; dirt, grass and rock use their
	// own world-locked structure instead of sharing a diagonal stripe pattern.
	float ripple_phase = dot(p, vec2(1.8, 5.5));
	if (TERRAIN_DETAIL_TIER == 2) ripple_phase += wear.r * 2.5;
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
	if (TERRAIN_DETAIL_TIER == 2) cracks = wear.a * 0.5;
	texture_value -= (clay * 0.08 + salt * 0.045) * cracks * fine_fade;
	texture_value += snow * ((broad - 0.5) * 0.035 + (grain - 0.5) * 0.008 * fine_fade);
	// A baked, mipmapped 24 m swatch replaces asphalt's gravel grain with
	// quiet oxidised patches and occasional branching cracks. One sample in
	// every quality tier; it does not add procedural noise loops or geometry.
	vec4 aged = texture(weathered_asphalt, wear_position / 24.0);
	vec3 asphalt_texel = OUTPUT_IS_SRGB ? aged.rgb : pow(max(aged.rgb, vec3(0.001)), vec3(0.454545));
	color *= mix(vec3(1.0), clamp(asphalt_texel / vec3(0.455, 0.467, 0.463), vec3(0.25), vec3(1.18)), asphalt);
	// Concrete breaks into branching fractures instead of a perfect slab grid.
	texture_value -= concrete * wear.a * 0.13;
	texture_value += concrete * (medium - 0.5) * 0.05;
	float lateral = abs(UV2.x);
	// Weathered traces of earlier traffic follow the signed road coordinate,
	// including bends and junction arms. They are a surface cue only; the
	// player's own tyre ruts still deform the shared mesh and collision.
	float track_drift = (deposits.r - 0.5) * 0.30;
	float track_distance = abs(abs(UV2.x - track_drift) - 1.10);
	float outer_distance = abs(abs(UV2.x + track_drift * 0.7) - 3.0);
	float old_tracks = 1.0 - smoothstep(0.12, 0.72, track_distance);
	float outer_tracks = 1.0 - smoothstep(0.12, 0.78, outer_distance);
	float loose_road = clamp(sand + gravel + dirt + mud + clay + leaves + snow, 0.0, 1.0);
	float worn_sections = mix(0.65, 1.0, smoothstep(0.18, 0.75, wear.r));
	color *= 1.0 - (old_tracks * 0.11 + outer_tracks * 0.075) * road_cover * loose_road * worn_sections;
	// Irregular verges use the same coverage as the physical material blend.
	// Remove the old pair of perfectly parallel bright shoulder bands.
	float margin = 4.0 * road_cover * (1.0 - road_cover);
	color = mix(color, color * vec3(0.94, 0.93, 0.87), margin * islands * (1.0 - snow) * 0.45);
	float line_drift = (deposits.b - 0.5) * 0.10;
	float center_line = (1.0 - smoothstep(0.07, 0.14, abs(UV2.x + line_drift))) * step(3.0, mod(p.x, 9.0));
	float edge_line = 1.0 - smoothstep(0.04, 0.12, abs(lateral - 3.65 + line_drift));
	float paint_survival = aged.a * smoothstep(0.35, 0.72, wear.g) * smoothstep(0.25, 0.60, deposits.r);
	color = mix(color, vec3(0.68, 0.65, 0.51), max(center_line, edge_line) * asphalt * 0.52 * paint_survival);
	// Fine deposits veil asphalt in patches, especially its crumbling banks.
	float dust_cover = asphalt * smoothstep(0.48, 0.78, wear.r * 0.6 + deposits.g * 0.4) * (0.10 + margin * 0.25);
	color = mix(color, color * vec3(1.12, 1.07, 0.94), dust_cover);
	color *= texture_value;
	// Darken actual compressed channels, never an independent painted track.
	color *= 1.0 - smoothstep(0.01, 0.24, max(soil_delta, 0.0)) * 0.22;
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
		float relief_depth = sand * 0.003 + snow * 0.001 + salt * 0.002
			+ gravel * 0.006 + rock * 0.006 + dirt * 0.004 + mud * 0.004
			+ grass * 0.002 + concrete * 0.001
			+ clay * 0.003 + leaves * 0.003;
		// Cosmetic millimetre detail is applied AFTER the physical rut normal.
		// Never displace vertices here: render and collision share one soil mesh.
		// Distant terrain keeps its baked cluster edges, not sub-pixel normal
		// sparkle. Only the close camera sees millimetre grains; ruts stay real.
		vec3 micro_relief = gradient * relief_depth * natural_detail * (1.0 - smoothstep(0.012, 0.05, pixel));
		micro_relief /= max(1.0, length(micro_relief) / 0.12);
		NORMAL = normalize(NORMAL - micro_relief);
	}
	color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.68);
	ALBEDO = pow(max(color, vec3(0.0)), vec3(2.2));
	ROUGHNESS = clamp(0.94 + (height_detail - 0.5) * 0.12 - wet * (mud * 0.35 + asphalt * 0.42 + rock * 0.22 + leaves * 0.20 + grass * 0.15) - ice * 0.46, 0.35, 1.0);
	SPECULAR = 0.18 + ice * 0.20 + wet * 0.12;
}
