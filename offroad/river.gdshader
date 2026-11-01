shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;
// Lesná vrchovina water: rivers and ponds in five looks (presets) blended per
// vertex. COLOR.r picks the look (0..4 over 0..1), UV carries the current
// (m/s, world X/Z). The bed is seen through the water by refraction and fades
// with depth by per-channel absorption; normals come from two noise maps
// advected along the current (flow mapping); the wake simulation
// (water_ripples.gd) displaces the surface. Foam gathers on the banks, in fast
// water, on breaking wakes; rain draws rings. Looks: 0 glacial mountain stream,
// 1 forest river, 2 muddy lowland river, 3 still forest pond, 4 flood water.
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;
uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_linear_mipmap;
uniform sampler2D ripple_map : filter_linear, repeat_disable;
uniform vec4 ripple_rect = vec4(0.0, 0.0, 1.0, 1.0);
uniform vec2 ripple_cells = vec2(2.0, 2.0);
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
varying float wake;

vec2 ripple_uv(vec2 p) {
	vec2 grid = (p - ripple_rect.xy) / ripple_rect.zw * (ripple_cells - 1.0);
	return (grid + 0.5) / ripple_cells;
}

bool in_ripples(vec2 p) {
	vec2 q = (p - ripple_rect.xy) / ripple_rect.zw;
	return q.x > 0.0 && q.y > 0.0 && q.x < 1.0 && q.y < 1.0;
}

void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	wake = in_ripples(w.xz) ? textureLod(ripple_map, ripple_uv(w.xz), 0.0).r : 0.0;
	VERTEX.y += wake;
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	look = COLOR.r * 4.0;
	current = UV;
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
// pattern flows without stretching (flow mapping).
vec2 flowing(sampler2D map, vec2 p, vec2 flow, float scale, float t) {
	float a = fract(t * 0.22);
	float b = fract(t * 0.22 + 0.5);
	vec2 na = texture(map, (p - flow * a * 4.5) * scale).xy * 2.0 - 1.0;
	vec2 nb = texture(map, (p - flow * b * 4.5) * scale + 0.5).xy * 2.0 - 1.0;
	return mix(na, nb, abs(a - 0.5) * 2.0);
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
	// Broad patches of darker and lighter water drifting with the current, so a
	// long reach never reads as one flat colour.
	float patches = texture(foam_noise, p * 0.025 - current * t * 0.01).r;
	body *= mix(0.7, 1.3, patches);
	vec3 tint = pick3(tint_colors, index);
	float speed = length(current);
	// Surface slopes: fine and broad flow-mapped noise, the wake simulation, rain.
	vec2 slope = flowing(wave_normal_a, p, current, 0.16, t) * 0.5 + flowing(wave_normal_b, p, current * 0.6, 0.05, t * 0.8) * 0.5;
	slope *= params.y * (0.35 + min(speed, 2.0) * 0.45);
	vec2 sim = vec2(0.0);
	if (in_ripples(p)) {
		vec2 cell = ripple_rect.zw / (ripple_cells - 1.0);
		float hl = texture(ripple_map, ripple_uv(p - vec2(cell.x, 0.0))).r;
		float hr = texture(ripple_map, ripple_uv(p + vec2(cell.x, 0.0))).r;
		float hd = texture(ripple_map, ripple_uv(p - vec2(0.0, cell.y))).r;
		float hu = texture(ripple_map, ripple_uv(p + vec2(0.0, cell.y))).r;
		slope += vec2(hr - hl, hu - hd) / (2.0 * cell) * 1.4;
		sim = texture(ripple_map, ripple_uv(p)).rg;
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
		vec2 c = p * 1.7 + n.xz * 1.5 - current * t * 0.4;
		float a = abs(sin(c.x * 2.1 + sin(c.y * 1.7 + t * 1.3) * 1.6) * sin(c.y * 2.3 + sin(c.x * 1.9 - t * 1.1) * 1.6));
		caustic = pow(1.0 - a, 6.0) * (1.0 - smoothstep(0.2, 1.6, refracted_thickness));
	}
	bed = bed * (1.0 + caustic * 0.6);
	// Absorption per channel: red fades first, so depth turns the bed green-blue.
	vec3 extinction = vec3(1.5, 0.75, 0.55) / max(params.x, 0.05);
	vec3 transmitted = exp(-extinction * refracted_thickness);
	float opacity = 1.0 - dot(transmitted, vec3(0.333));
	// Foam: banks, fast water, breaking wakes; broken up by a noise texture.
	float foam_tex = texture(foam_noise, p * 0.35 - current * t * 0.5).r * 0.6 + texture(foam_noise, p * 0.9 - current * t * 0.8 + 0.3).r * 0.4;
	float shore = 1.0 - smoothstep(0.0, 0.22, thickness);
	float rapids = smoothstep(0.9, 2.2, speed) * smoothstep(0.55, 0.8, foam_tex);
	float breaking = smoothstep(0.015, 0.05, abs(sim.y)) + smoothstep(0.05, 0.11, sim.x);
	float foam = clamp((shore * smoothstep(0.35, 0.6, foam_tex + shore * 0.3) + rapids + breaking * smoothstep(0.3, 0.6, foam_tex)) * params.z, 0.0, 1.0);
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
