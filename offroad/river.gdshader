shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;
// Lesná vrchovina water: rivers and ponds in five looks (presets) blended per
// vertex. COLOR.r picks the look (0..4 over 0..1), UV carries the current
// (m/s, world X/Z: the same that pushes a car, water_flow_at), UV2 the place across
// and along the river. The bed is seen through the water by refraction and fades
// with depth by per-channel absorption; normals come from noise advected along the
// current (flow mapping), long streaks showing which way it runs even from afar; the
// wake simulation (water_ripples.gd) displaces the surface, and the water parts around
// a car moving through it. Foam gathers on the banks, at the bumper and on the
// crests of waves; rain draws rings. Looks: 0 glacial mountain stream,
// 1 forest river, 2 muddy lowland river, 3 still forest pond, 4 flood water.
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;
uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_linear_mipmap;
uniform sampler2D ripple_map : filter_linear, repeat_disable;
uniform vec4 ripple_rect = vec4(0.0, 0.0, 1.0, 1.0);
uniform vec2 ripple_cells = vec2(2.0, 2.0);
// The wake simulation sleeps when it has run out: nothing to read then.
uniform bool ripple_awake = false;
// 0: the lowest quality's plainer water (the car feels the same current).
uniform float water_detail = 1.0;
// The lead car in the water: how far in (0..1), where it is, its half length and width,
// its motion through the water.
uniform float hull_wet = 0.0;
uniform vec2 hull_centre = vec2(0.0);
uniform vec2 hull_forward = vec2(0.0, -1.0);
uniform vec2 hull_half = vec2(2.0, 1.0);
uniform vec2 hull_motion = vec2(0.0);
uniform sampler2D wave_normal_a : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D wave_normal_b : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D foam_noise : filter_linear_mipmap, repeat_enable;
// Per look: colour of light scattered back by the water body, the tint light
// picks up crossing it, and (clarity in m, wave strength, foam, reflection).
uniform vec3 body_colors[5];
uniform vec3 tint_colors[5];
uniform vec4 looks[5];
uniform vec3 sky_tint : source_color = vec3(0.68, 0.78, 0.84);
// Part of the sky mirrored even straight down (the overview camera).
uniform float sky_floor = 0.2;
// Colour of the banks and forest mirrored near the horizon.
uniform vec3 horizon_tint : source_color = vec3(0.12, 0.16, 0.1);
// Weather: swollen, silty water after rain; rain drops on the surface.
uniform float turbidity = 0.0;
uniform float rain = 0.0;
// Ice over still water (ponds in a lasting frost): 0 open .. 1 solid.
uniform float ice = 0.0;
varying vec3 world;
varying float look;
varying vec2 current;
varying vec2 river_place;
varying float wake;

vec2 ripple_uv(vec2 p) {
	vec2 grid = (p - ripple_rect.xy) / ripple_rect.zw * (ripple_cells - 1.0);
	return (grid + 0.5) / ripple_cells;
}

// 1 inside the simulated square, fading out at its rim (0: outside).
float ripple_share(vec2 p) {
	if (!ripple_awake) return 0.0;
	vec2 q = (p - ripple_rect.xy) / ripple_rect.zw;
	vec2 edge = smoothstep(vec2(0.0), vec2(0.1), q) * (1.0 - smoothstep(vec2(0.9), vec2(1.0), q));
	return edge.x * edge.y;
}

void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	float inside = ripple_share(w.xz);
	wake = inside > 0.0 ? textureLod(ripple_map, ripple_uv(w.xz), 0.0).r * inside : 0.0;
	VERTEX.y += wake;
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	look = COLOR.r * 4.0;
	current = UV;
	river_place = UV2;
}

// Preset blend for a continuous look index.
vec3 pick3(vec3 values[5], float index) {
	int i = int(floor(index));
	int j = min(i + 1, 4);
	return mix(values[i], values[j], fract(index));
}
vec4 pick4(vec4 values[5], float index) {
	int i = int(floor(index));
	int j = min(i + 1, 4);
	return mix(values[i], values[j], fract(index));
}

// Two noise normals advected along the current in staggered phases, so the
// pattern flows without stretching (flow mapping). One cycle takes 1 / 0.22 s and
// carries it 4.5 s' worth of the flow: it moves as fast as the water.
vec2 flowing(sampler2D map, vec2 p, vec2 flow, float scale, float t) {
	float a = fract(t * 0.22);
	float b = fract(t * 0.22 + 0.5);
	vec2 na = texture(map, (p - flow * a * 4.5) * scale).xy * 2.0 - 1.0;
	vec2 nb = texture(map, (p - flow * b * 4.5) * scale + 0.5).xy * 2.0 - 1.0;
	return mix(na, nb, abs(a - 0.5) * 2.0);
}

// Long streaks lying along the river and running with it, in the river's own coordinates (across,
// along), so bends do not tear them. `flow` is the current there, `side` what the hull adds across.
vec2 streaks(vec2 place, vec2 flow, float side, float t) {
	float a = fract(t * 0.22);
	float b = fract(t * 0.22 + 0.5);
	const vec2 SCALE = vec2(0.09, 0.012);
	vec2 na = texture(wave_normal_b, (place - vec2(side, flow.y) * a * 4.5) * SCALE).xy * 2.0 - 1.0;
	vec2 nb = texture(wave_normal_b, (place - vec2(side, flow.y) * b * 4.5) * SCALE + 0.5).xy * 2.0 - 1.0;
	return mix(na, nb, abs(a - 0.5) * 2.0);
}

// Round the hull the water parts: flow past a cylinder, in the hull's own axes stretched to its size.
// Returns what that adds to the current (m/s, world); `bow` is 1 against the part meeting the water.
vec2 parted(vec2 p, out float bow) {
	bow = 0.0;
	vec2 d = p - hull_centre;
	vec2 side = vec2(-hull_forward.y, hull_forward.x);
	vec2 q = vec2(dot(d, hull_forward), dot(d, side)) / hull_half;
	float rr = dot(q, q);
	if (rr > 16.0) return vec2(0.0);
	vec2 motion = vec2(dot(hull_motion, hull_forward), dot(hull_motion, side));
	float moving = length(motion);
	if (moving < 0.05) return vec2(0.0);
	float r2 = max(rr, 1.0);
	vec2 induced = (-motion + 2.0 * dot(motion, q) * q / r2) / r2 * (1.0 - smoothstep(9.0, 16.0, rr));
	float size = length(induced);
	if (size > 2.5) induced *= 2.5 / size;
	float r = sqrt(r2);
	bow = (1.0 - smoothstep(1.0, 1.75, r)) * smoothstep(-0.1, 0.8, dot(q / r, motion / moving)) * smoothstep(0.3, 1.8, moving) * hull_wet;
	return (hull_forward * induced.x + side * induced.y) * hull_wet;
}

// Expanding rings from raindrops on a jittered grid; returns a slope.
vec2 raindrops(vec2 p, float t) {
	vec2 slope = vec2(0.0);
	for (int layer = 0; layer < 2; layer++) {
		vec2 q = p * (1.6 + float(layer) * 0.9) + float(layer) * 7.3;
		vec2 cell = floor(q);
		vec2 f = fract(q);
		float seed = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
		vec2 centre = vec2(fract(seed * 13.1), fract(seed * 7.7)) * 0.6 + 0.2;
		float age = fract(t * 0.9 + seed);
		vec2 d = f - centre;
		float r = length(d);
		float ring = sin((r - age * 0.45) * 60.0) * exp(-abs(r - age * 0.45) * 30.0) * (1.0 - age);
		slope += normalize(d + 0.0001) * ring;
	}
	return slope * 0.25;
}

void fragment() {
	vec2 p = world.xz;
	float t = TIME;
	float index = clamp(mix(look, 4.0, turbidity * smoothstep(0.0, 1.0, (4.0 - look) / 4.0) * 0.8), 0.0, 4.0);
	vec4 params = pick4(looks, index);
	vec3 body = pick3(body_colors, index);
	float speed = length(current);
	// How the water runs here: the current, parted around a car in it.
	float bow = 0.0;
	vec2 parting = vec2(0.0);
	if (hull_wet > 0.0 && water_detail > 0.5) parting = parted(p, bow);
	// (The fine pattern keeps to the current: the parting would tear it into rays.)
	vec2 flow = current;
	float running = smoothstep(0.02, 0.3, speed);
	float phase_a = fract(t * 0.22);
	float phase_b = fract(t * 0.22 + 0.5);
	float phase_mix = abs(phase_a - 0.5) * 2.0;
	// Patches of darker and lighter water, elongated along the river and running with it,
	// so a long reach never reads as one flat colour; the brightest are rafts of froth.
	vec2 raft_scale = vec2(0.05, 0.02);
	float patches = mix(texture(foam_noise, (river_place - vec2(0.0, flow.y) * phase_a * 4.5) * raft_scale).r, texture(foam_noise, (river_place - vec2(0.0, flow.y) * phase_b * 4.5) * raft_scale + 0.5).r, phase_mix);
	body *= mix(0.7, 1.3, patches);
	vec3 tint = pick3(tint_colors, index);
	// Surface slopes: fine flow-mapped noise and the river's long streaks, the wake simulation, rain.
	vec2 fine = flowing(wave_normal_a, p, flow, 0.16, t);
	vec2 streak = streaks(river_place, flow + parting * 0.5, parting.x * 0.5, t);
	vec2 slope = (fine * 0.5 + streak * vec2(1.2, 0.3) * running) * params.y * (0.35 + min(speed, 2.0) * 0.45);
	// What the eye follows from the bank: the streaks show in the water's own brightness.
	float band = streak.x * running;
	body *= 1.0 + band * 0.5;
	tint *= 1.0 + band * 0.25;
	// (Still water has no streaks to run.)
	if (running <= 0.0) slope = (fine * 0.5 + flowing(wave_normal_b, p, vec2(0.0), 0.05, t * 0.8) * 0.5) * params.y * 0.35;
	float sim_height = 0.0;
	float sim_steep = 0.0;
	float inside = ripple_share(p);
	if (inside > 0.0) {
		vec2 cell = ripple_rect.zw / (ripple_cells - 1.0);
		float hl = texture(ripple_map, ripple_uv(p - vec2(cell.x, 0.0))).r;
		float hr = texture(ripple_map, ripple_uv(p + vec2(cell.x, 0.0))).r;
		float hd = texture(ripple_map, ripple_uv(p - vec2(0.0, cell.y))).r;
		float hu = texture(ripple_map, ripple_uv(p + vec2(0.0, cell.y))).r;
		vec2 wave_slope = vec2(hr - hl, hu - hd) / (2.0 * cell) * inside;
		slope += wave_slope * 2.6;
		sim_height = texture(ripple_map, ripple_uv(p)).r * inside;
		sim_steep = length(wave_slope);
		// Crests catch the light, hollows lie dark: a wave reads at any angle.
		body *= 1.0 + clamp(sim_height * 5.0, -0.35, 0.6);
	}
	slope += raindrops(p, t) * rain;
	vec3 n = normalize(vec3(-slope.x, 1.0, -slope.y));
	NORMAL = normalize((VIEW_MATRIX * vec4(n, 0.0)).xyz);
	// Water thickness along the view ray, from the scene depth behind it.
	float depth = textureLod(depth_texture, SCREEN_UV, 0.0).r;
	vec4 behind_view = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth, 1.0);
	float thickness = max(0.0, VERTEX.z - behind_view.z / behind_view.w);
	// Refraction: offset by the surface slope, more in deeper water; never pick
	// up anything in front of the water.
	vec2 offset = n.xz * 0.06 * clamp(thickness, 0.0, 1.0);
	vec2 refracted = SCREEN_UV + offset;
	float refracted_depth = textureLod(depth_texture, refracted, 0.0).r;
	vec4 refracted_view = INV_PROJECTION_MATRIX * vec4(refracted * 2.0 - 1.0, refracted_depth, 1.0);
	float refracted_thickness = VERTEX.z - refracted_view.z / refracted_view.w;
	if (refracted_thickness < 0.0) { refracted = SCREEN_UV; refracted_thickness = thickness; }
	refracted_thickness = max(refracted_thickness, 0.0);
	vec3 bed = textureLod(screen_texture, refracted, 0.0).rgb;
	// Sun caustics dancing on a shallow bed.
	float caustic = 0.0;
	if (refracted_thickness < 1.6) {
		vec2 c = p * 1.7 + n.xz * 1.5;
		float a = abs(sin(c.x * 2.1 + sin(c.y * 1.7 + t * 1.3) * 1.6) * sin(c.y * 2.3 + sin(c.x * 1.9 - t * 1.1) * 1.6));
		caustic = pow(1.0 - a, 6.0) * (1.0 - smoothstep(0.2, 1.6, refracted_thickness));
	}
	bed = bed * (1.0 + caustic * 0.6);
	// Absorption per channel: red fades first, so depth turns the bed green-blue.
	vec3 extinction = vec3(1.5, 0.75, 0.55) / max(params.x, 0.05);
	vec3 transmitted = exp(-extinction * refracted_thickness);
	float opacity = 1.0 - dot(transmitted, vec3(0.333));
	// Foam: banks, the bumper and the crests of waves, carried along and broken up by a noise
	// texture; open water only flecks of it, where it runs fast, so its motion stays what you read.
	vec2 foam_flow = current + parting;
	float foam_tex = mix(texture(foam_noise, (p - foam_flow * phase_a * 1.5) * 0.45).r, texture(foam_noise, (p - foam_flow * phase_b * 1.5) * 0.45 + 0.3).r, phase_mix);
	float shore = 1.0 - smoothstep(0.0, 0.14, thickness);
	// (Rafts of froth are for reading the river from afar: up close it is the surface that moves.)
	float rafts = smoothstep(0.62, 0.72, patches) * running * smoothstep(14.0, 45.0, -VERTEX.z) * 0.3;
	float rapids = (smoothstep(1.1, 2.4, speed) * smoothstep(0.62, 0.85, foam_tex) * 0.45 + rafts) * water_detail;
	float crests = smoothstep(0.05, 0.12, sim_height) + smoothstep(0.12, 0.3, sim_steep) * 0.7 * water_detail + bow * 1.2;
	float foam = clamp((shore * smoothstep(0.35, 0.6, foam_tex + shore * 0.3) + rapids + crests * smoothstep(0.25, 0.55, foam_tex)) * params.z, 0.0, 1.0);
	// Fresnel: grazing views mirror the sky; a little of it even from above.
	float fresnel = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 5.0);
	float mirror = clamp(max(fresnel * 0.8, sky_floor * 0.6) * params.w, 0.0, 0.8);
	// Light through the water (emission, already lit) and light scattered back
	// by it (albedo); foam is a white albedo.
	vec3 through = bed * tint * transmitted;
	ALBEDO = mix(body * opacity, vec3(0.86, 0.88, 0.86), foam);
	// What the mirror shows: trees and banks for rays leaving near the horizon,
	// the sky above them.
	vec3 eye = normalize(world - CAMERA_POSITION_WORLD);
	vec3 ray = reflect(eye, n);
	vec3 mirrored = mix(horizon_tint, sky_tint, smoothstep(0.05, 0.4, ray.y));
	EMISSION = through * (1.0 - foam) * (1.0 - mirror) + mirrored * mirror * (1.0 - foam) * 0.55;
	ROUGHNESS = mix(0.04, 0.6, foam) + rain * 0.08;
	// The mirror term above already shows sky and banks; the engine's own sky
	// reflection stays faint (it would wash every view pale blue).
	SPECULAR = 0.12;
	// Soft edge where the water meets the bank.
	ALPHA = smoothstep(0.0, 0.04, thickness);
	if (ice > 0.0) {
		// Ice: a milky blue-grey sheet with white cracks and frost, opaque once
		// thick; the water below shows through while it is still forming.
		vec2 c = p * 0.45;
		vec2 cell = floor(c);
		vec2 local = fract(c);
		float edge = 1.0;
		for (int j = -1; j <= 1; j++) {
			for (int i = -1; i <= 1; i++) {
				vec2 o = vec2(float(i), float(j));
				vec2 seed = fract(sin(vec2(dot(cell + o, vec2(127.1, 311.7)), dot(cell + o, vec2(269.5, 183.3)))) * 43758.5453);
				edge = min(edge, length(o + seed - local));
			}
		}
		float crack = 1.0 - smoothstep(0.02, 0.05, abs(edge - 0.45));
		float frostiness = texture(foam_noise, p * 0.2).r;
		vec3 sheet = mix(vec3(0.55, 0.66, 0.72), vec3(0.85, 0.9, 0.93), frostiness * 0.6 + crack * 0.5);
		float solid = smoothstep(0.3, 0.9, ice);
		ALBEDO = mix(ALBEDO, sheet, solid);
		EMISSION *= 1.0 - solid;
		ROUGHNESS = mix(ROUGHNESS, mix(0.12, 0.6, frostiness), solid);
		NORMAL = mix(NORMAL, normalize((VIEW_MATRIX * vec4(0.0, 1.0, 0.0, 0.0)).xyz), solid);
		ALPHA = max(ALPHA, solid);
	}
}
