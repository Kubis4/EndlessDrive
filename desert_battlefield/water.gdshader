shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;
// Muddy ford water. Interactive waves from water_ripples.gd (R = height, G = vertical
// speed) displace the subdivided surface and drive normals and foam; small wind
// ripples are layered on top. Depth-tinted murk and Fresnel sky reflection.
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;
uniform sampler2D ripple_map : filter_linear, repeat_disable;
// World rectangle of the ripple grid: x0, z0, size x, size z.
uniform vec4 ripple_rect = vec4(74.0, -95.8, 36.0, 8.6);
uniform vec2 ripple_cells = vec2(104.0, 26.0);
uniform vec3 shallow_color : source_color = vec3(0.5, 0.45, 0.31);
uniform vec3 deep_color : source_color = vec3(0.24, 0.25, 0.17);
uniform vec3 sky_tint : source_color = vec3(0.72, 0.78, 0.8);
// Tuning per water body (defaults: the desert ford). Flow drifts the ripples
// downstream (m/s); wave_strength scales the wind ripples' normals; the water
// reaches full colour at murk_depth; min_alpha is its opacity over the shallows;
// reflection scales the Fresnel sky mirror; shore_foam_width is the depth (m)
// over which the bank foam fades.
uniform vec2 flow = vec2(0.0);
uniform float wave_strength = 1.0;
uniform float murk_depth = 0.7;
uniform float min_alpha = 0.3;
uniform float reflection = 0.6;
uniform float shore_foam_width = 0.07;
// Part of the sky mirrored even straight down (0 = Fresnel only), and drifting
// light streaks of the current, so the water reads as water from above too.
uniform float sky_floor = 0.0;
uniform float current_streaks = 0.0;
varying vec3 world;
varying float wave;

vec2 ripple_uv(vec2 p) {
	// Texel centres sit on grid nodes.
	vec2 grid = (p - ripple_rect.xy) / ripple_rect.zw * (ripple_cells - 1.0);
	return (grid + 0.5) / ripple_cells;
}

void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	wave = textureLod(ripple_map, ripple_uv(w.xz), 0.0).r;
	VERTEX.y += wave;
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

float wind(vec2 p, float t) {
	float h = 0.010 * sin(p.x * 1.9 + t * 1.3) + 0.008 * sin(p.y * 2.7 - t * 1.1 + p.x * 0.6);
	return h + 0.004 * sin((p.x + p.y) * 6.1 + t * 2.4) + 0.003 * sin((p.x - p.y) * 8.3 - t * 2.9);
}

void fragment() {
	vec2 p = world.xz;
	float t = TIME;
	float e = 0.04;
	// Ripples carried downstream with the current.
	vec2 q = p - flow * t;
	float h = wind(q, t);
	vec3 n = normalize(vec3((h - wind(q + vec2(e, 0.0), t)) * wave_strength, e, (h - wind(q + vec2(0.0, e), t)) * wave_strength));
	// Simulated waves: gradient of the ripple height field.
	vec2 cell = ripple_rect.zw / (ripple_cells - 1.0);
	float hl = texture(ripple_map, ripple_uv(p - vec2(cell.x, 0.0))).r;
	float hr = texture(ripple_map, ripple_uv(p + vec2(cell.x, 0.0))).r;
	float hd = texture(ripple_map, ripple_uv(p - vec2(0.0, cell.y))).r;
	float hu = texture(ripple_map, ripple_uv(p + vec2(0.0, cell.y))).r;
	vec2 slope = vec2(hr - hl, hu - hd) / (2.0 * cell);
	n = normalize(vec3(n.x - slope.x * 1.6, n.y, n.z - slope.y * 1.6));
	vec2 sim = texture(ripple_map, ripple_uv(p)).rg;
	NORMAL = normalize((VIEW_MATRIX * vec4(n, 0.0)).xyz);
	float depth = textureLod(depth_texture, SCREEN_UV, 0.0).r;
	#if CURRENT_RENDERER == RENDERER_COMPATIBILITY
		vec4 scene_view = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	#else
		vec4 scene_view = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth, 1.0);
	#endif
	float thickness = max(0.0, VERTEX.z - scene_view.z / scene_view.w);
	float murk = clamp(thickness / murk_depth, 0.0, 1.0);
	// Fresnel: grazing views mirror the pale sky, steep views see the murk.
	float fresnel = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 4.0);
	float shore_foam = (1.0 - smoothstep(0.0, shore_foam_width, thickness)) * (0.6 + 0.4 * sin(p.x * 13.0 + t * 2.0) * sin(p.y * 11.0 - t * 1.7));
	// Churned water: crests, fast-moving surface and steep slopes turn white-brown.
	float breaking = smoothstep(0.02, 0.06, abs(sim.y)) + smoothstep(0.06, 0.12, sim.x) + smoothstep(0.45, 0.9, length(slope));
	float streaks = 0.55 + 0.45 * sin(q.x * 9.0 + sin(q.y * 7.0 + t) * 2.0) * sin(q.y * 8.0 - t * 1.3);
	float foam = clamp(shore_foam + clamp(breaking, 0.0, 1.0) * streaks, 0.0, 1.0);
	// Stirred-up silt makes moving water muddier and more opaque.
	float silt = clamp(abs(sim.y) * 15.0 + abs(sim.x) * 4.0, 0.0, 0.5);
	vec3 water = mix(mix(shallow_color, deep_color, murk), vec3(0.42, 0.33, 0.22), silt);
	float glint = current_streaks * smoothstep(0.55, 1.0, 0.5 + 0.5 * sin(q.x * 1.3 + sin(q.y * 0.45 + t * 0.3) * 2.5) * sin(q.y * 0.9 - q.x * 0.4));
	water = mix(water, sky_tint, clamp(max(fresnel * reflection, sky_floor) + glint * 0.35, 0.0, 1.0));
	ALBEDO = mix(water, vec3(0.8, 0.76, 0.66), foam * 0.75);
	ALPHA = clamp(mix(min_alpha, 0.86, murk) + glint * 0.1 + silt * 0.4 + foam * 0.35 + fresnel * 0.3, 0.0, 1.0);
	ROUGHNESS = mix(0.06, 0.3, foam);
	SPECULAR = 0.55;
}
