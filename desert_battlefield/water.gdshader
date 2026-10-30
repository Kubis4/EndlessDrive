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
	float h = wind(p, t);
	vec3 n = normalize(vec3(h - wind(p + vec2(e, 0.0), t), e, h - wind(p + vec2(0.0, e), t)));
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
	float murk = clamp(thickness / 0.7, 0.0, 1.0);
	// Fresnel: grazing views mirror the pale sky, steep views see the murk.
	float fresnel = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 4.0);
	float shore_foam = (1.0 - smoothstep(0.0, 0.07, thickness)) * (0.6 + 0.4 * sin(p.x * 13.0 + t * 2.0) * sin(p.y * 11.0 - t * 1.7));
	// Churned water: crests, fast-moving surface and steep slopes turn white-brown.
	float breaking = smoothstep(0.02, 0.06, abs(sim.y)) + smoothstep(0.06, 0.12, sim.x) + smoothstep(0.45, 0.9, length(slope));
	float streaks = 0.55 + 0.45 * sin(p.x * 9.0 + sin(p.y * 7.0 + t) * 2.0) * sin(p.y * 8.0 - t * 1.3);
	float foam = clamp(shore_foam + clamp(breaking, 0.0, 1.0) * streaks, 0.0, 1.0);
	// Stirred-up silt makes moving water muddier and more opaque.
	float silt = clamp(abs(sim.y) * 15.0 + abs(sim.x) * 4.0, 0.0, 0.5);
	vec3 water = mix(mix(shallow_color, deep_color, murk), vec3(0.42, 0.33, 0.22), silt);
	water = mix(water, sky_tint, fresnel * 0.6);
	ALBEDO = mix(water, vec3(0.8, 0.76, 0.66), foam * 0.75);
	ALPHA = clamp(mix(0.3, 0.86, murk) + silt * 0.4 + foam * 0.35 + fresnel * 0.3, 0.0, 1.0);
	ROUGHNESS = mix(0.06, 0.3, foam);
	SPECULAR = 0.55;
}
