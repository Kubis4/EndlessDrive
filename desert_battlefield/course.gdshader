shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
#include "res://desert_battlefield/soil.gdshaderinc"
// Procedural test-ground surfaces. Layout uniforms come from terrain.gd via
// test_course.gd, so physics and rendering share one definition.
instance uniform vec4 bounds = vec4(-1e6, -1e6, 1e6, 1e6);
uniform vec4 course_rect;
uniform vec4 pad_rect;
uniform vec3 pad_circle;
uniform float margin = 5.0;
uniform float lane_x0;
uniform float lane_x1;
uniform float lane_half;
uniform float lane_spacing;
uniform float lane_z[10];
uniform int lane_type[10];
uniform int moguls_lane = 9;
uniform vec2 water_x;
uniform vec2 jump_lip = vec2(83.6, 84.0);
// Mixed-surface circuit (terrain.gd CIRCUIT_*): ground per section of arc length.
uniform vec4 circuit_rect = vec4(1e6, 1e6, 1e6, 1e6);
uniform float circuit_half = 4.0;
uniform float circuit_length = 1.0;
uniform float circuit_start_line = 8.0;
uniform float section_start[12];
uniform int section_kind[12];
varying vec3 world;
varying vec3 world_normal;
// Rocky ground paint from test_course.gd: r = rock relief, g = trail, b = rocky ground.
varying vec4 ground_paint;
// Circuit vertices (CUSTOM0): arc length, the same shifted by half a lap,
// signed and absolute distance from the centreline.
varying vec4 circuit_data;

const int SAND = 0;
const int PACKED = 1;
const int ROAD = 2;
const int GRAVEL = 3;
const int CLAY = 4;
const int MUD = 5;
const int WATER = 6;
const int ROCK = 7;
const int SNOW = 8;
const int ICE = 9;
const int GRASS = 10;
const int WET_ROAD = 11;

float hash12(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * 0.1031);
	q += dot(q, q.yzx + 33.33);
	return fract((q.x + q.y) * q.z);
}
vec2 hash22(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
	q += dot(q, q.yzx + 33.33);
	return fract((q.xx + q.yz) * q.zy);
}
float vnoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
	return vnoise(p) * 0.55 + vnoise(p * 2.03 + 7.1) * 0.3 + vnoise(p * 4.1 - 3.7) * 0.15;
}
// x = nearest distance, y = second distance, z = nearest cell id.
vec3 voronoi(vec2 p) {
	vec2 cell = floor(p);
	vec2 f = fract(p);
	float d1 = 8.0;
	float d2 = 8.0;
	float id = 0.0;
	for (int y = -1; y <= 1; y++) {
		for (int x = -1; x <= 1; x++) {
			vec2 g = vec2(float(x), float(y));
			vec2 o = hash22(cell + g) * 0.8 + 0.1;
			float d = length(g + o - f);
			if (d < d1) { d2 = d1; d1 = d; id = hash12(cell + g); }
			else if (d < d2) { d2 = d; }
		}
	}
	return vec3(d1, d2, id);
}
// Screen-space surface-gradient bump mapping: one height evaluation per pixel.
vec3 bump(vec3 n, vec3 pos, float h) {
	vec3 dpdx = dFdx(pos);
	vec3 dpdy = dFdy(pos);
	vec3 r1 = cross(dpdy, n);
	vec3 r2 = cross(n, dpdx);
	float det = dot(dpdx, r1);
	vec3 grad = sign(det) * (dFdx(h) * r1 + dFdy(h) * r2);
	return normalize(abs(det) * n - grad);
}
float rect_edge(vec2 p, vec4 r) {
	return min(min(p.x - r.x, r.z - p.x), min(p.y - r.y, r.w - p.y));
}

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	VERTEX.y -= sand_depth(world.xz);
	NORMAL = sand_normal(world.xz, NORMAL);
	world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	ground_paint = COLOR;
	circuit_data = CUSTOM0;
}

// Weathered limestone/granite: slabs on the trail, rounded boulders around it,
// coarse grit in between. Rock relief comes from the mesh; colour from paint
// (trail_paint = trail mask, rock_paint = rock relief).
void rocky_ground(vec2 p, float detail, float fine, float trail_paint, float rock_paint, out vec3 albedo, out float rough, out float height) {
	// Expensive cell patterns only run where they are visible (zoomed-out views
	// get their mean colours), and only for the materials present at this pixel.
	float trail = smoothstep(0.35, 0.75, trail_paint);
	float rock = smoothstep(0.08, 0.35, rock_paint);
	float n = fbm(p * 0.6);
	vec3 grit = mix(vec3(0.38, 0.32, 0.25), vec3(0.43, 0.36, 0.28), n);
	float grit_height = 0.0;
	if (trail < 1.0 && fine > 0.0) {
		vec3 grit_cells = voronoi(p * 7.0);
		vec3 stones = mix(vec3(0.33, 0.27, 0.21), vec3(0.47, 0.39, 0.3), grit_cells.z) * (0.88 + 0.24 * n);
		stones = mix(stones, vec3(0.2, 0.16, 0.12), 1.0 - smoothstep(0.02, 0.12, grit_cells.y - grit_cells.x));
		grit = mix(grit, stones, fine);
		grit_height = (1.0 - grit_cells.x) * 0.006 * fine;
	}
	vec3 slab = vec3(0.36, 0.32, 0.27);
	float slab_height = 0.0;
	if (trail > 0.0) {
		vec2 q = p * 1.25 + 0.6 * vec2(vnoise(p * 0.9), vnoise(p * 0.9 + 4.0));
		vec3 slabs = voronoi(q);
		float joint = (1.0 - smoothstep(0.0, 0.06, slabs.y - slabs.x)) * max(detail, 0.35);
		slab = mix(vec3(0.3, 0.27, 0.23), vec3(0.42, 0.37, 0.31), slabs.z) * (0.86 + 0.28 * vnoise(p * 3.0));
		slab = mix(slab, vec3(0.13, 0.11, 0.09), joint * 0.9);
		// Pale sandy dust settled in the slab joints and a little lichen.
		slab = mix(slab, vec3(0.6, 0.48, 0.33), smoothstep(0.62, 0.8, fbm(p * 1.7 + 8.0)) * 0.5);
		slab = mix(slab, vec3(0.3, 0.32, 0.2), smoothstep(0.78, 0.86, vnoise(p * 5.0 + 3.0)) * 0.45 * detail);
		slab_height = (-joint * 0.02 + vnoise(p * 4.0) * 0.008) * detail;
	}
	vec3 boulder = vec3(0.37, 0.26, 0.18);
	float boulder_height = 0.0;
	if (rock > 0.0) {
		// Weathered red-brown sandstone boulders with darker desert varnish.
		float varnish = fbm(p * 2.2);
		boulder = mix(vec3(0.3, 0.2, 0.14), vec3(0.45, 0.31, 0.21), varnish);
		if (detail > 0.0) {
			vec3 facets = voronoi(p * 3.5);
			boulder *= mix(1.0, 0.85 + 0.3 * facets.z, detail);
			boulder_height = (facets.x * 0.04 + varnish * 0.015) * detail;
		}
		boulder = mix(boulder, vec3(0.16, 0.11, 0.08), smoothstep(0.55, 0.75, vnoise(p * 4.0 + 2.0)) * 0.5);
	}
	albedo = mix(grit, slab, trail);
	albedo = mix(albedo, boulder, rock * (1.0 - trail * 0.4));
	rough = mix(0.9, 0.78, max(trail, rock));
	height = mix(grit_height, slab_height, trail) + rock * boulder_height;
}

void sand_like(vec2 p, float packed, float detail, out vec3 albedo, out float rough, out float height) {
	float broad = 0.5 + 0.24 * sin(p.x * 0.035 + p.y * 0.023) + 0.15 * sin(p.y * 0.11 - p.x * 0.064);
	albedo = mix(vec3(0.64, 0.49, 0.32), vec3(0.70, 0.56, 0.38), broad);
	float grain = fbm(p * 2.3);
	vec3 compact = mix(vec3(0.56, 0.43, 0.29), vec3(0.62, 0.49, 0.34), grain);
	albedo = mix(albedo, compact, packed);
	rough = mix(0.91, 0.86, packed);
	float ripple = sin(p.x * 10.0 + p.y * 4.0 + 2.2 * sin(p.y * 0.7));
	height = (ripple * 0.004 * (1.0 - packed) + grain * 0.01 * packed) * detail;
}

void fragment() {
	vec2 p = world.xz;
	if (p.x < bounds.x || p.y < bounds.y || p.x >= bounds.z || p.y >= bounds.w) { discard; }
	float px = max(length(fwidth(p)), 0.0005);
	float detail = 1.0 - smoothstep(0.03, 0.09, px);
	// Centimetre-scale stones/grit resolve only in close views.
	float fine = 1.0 - smoothstep(0.012, 0.035, px);
	float depth_here = sand_depth(p);
	float rut = smoothstep(0.015, 0.22, depth_here);
	// Rubber left by skidding tyres on asphalt and rock (a few mm in the field).
	float rubber = smoothstep(0.0005, 0.003, depth_here) * (1.0 - smoothstep(0.006, 0.012, depth_here));
	// Ground between and around lanes: dune sand fading into compacted sand.
	bool in_course = p.x >= course_rect.x && p.y >= course_rect.y && p.x < course_rect.z && p.y < course_rect.w;
	bool rocky = ground_paint.b > 0.5;
	float packed = smoothstep(1.0, margin, rect_edge(p, bounds));
	float specular = 0.25;
	int kind = PACKED;
	float inner = 0.0;
	float across = 0.0;
	bool skid_pad = false;
	bool in_circuit = p.x >= circuit_rect.x && p.y >= circuit_rect.y && p.x < circuit_rect.z && p.y < circuit_rect.w;
	float along = 0.0;
	float section_s = 0.0;
	if (in_circuit) {
		along = circuit_data.x;
		// Arc length jumps from a full lap to zero at the start; only in the
		// triangles across that jump does it change by more than a few metres
		// per pixel, and there the half-lap copy interpolates smoothly.
		if (fwidth(circuit_data.x) > circuit_length * 0.25) {
			along = mod(circuit_data.y + circuit_length * 0.5, circuit_length);
		}
		across = circuit_data.z;
		inner = circuit_half - circuit_data.w;
		// Packed shoulders beside the track, looser rolling sand between runs.
		packed *= circuit_data.w < circuit_half + 1.0 ? 1.0 : 0.3;
		if (inner > 0.0) {
			int section = 0;
			for (int i = 1; i < 12; i++) { if (along >= section_start[i]) { section = i; } }
			kind = section_kind[section];
			section_s = along - section_start[section];
			// Painted jump lip, logs across the grass and the start line
			// (positions match terrain.gd circuit_relief).
			if (section == 1 && section_s > 11.6 && section_s < 12.0) { kind = -1; }
			if (kind == GRASS) {
				if (abs(section_s - 4.5) < 0.24 || abs(section_s - 11.0 - across * 0.28) < 0.24) { kind = -2; }
			}
			if (abs(along - circuit_start_line) < 0.3) { kind = -3; }
		}
	} else if (in_course) {
		if (p.x > lane_x0 && p.x < lane_x1) {
			int lane = int(round((lane_z[0] - p.y) / lane_spacing));
			if (lane >= 0 && lane < 10) {
				across = p.y - lane_z[lane];
				inner = min(lane_half - abs(across), min(p.x - lane_x0, lane_x1 - p.x));
				if (inner > 0.0) { kind = lane_type[lane]; }
				if (kind == CLAY && lane == moguls_lane && p.x > jump_lip.x && p.x < jump_lip.y) { kind = -1; }
			}
		}
	} else if (!rocky) {
		inner = pad_circle.z - distance(p, pad_circle.xy);
		if (inner > 0.0) { kind = ROAD; skid_pad = true; }
	}
	// Soft 35 cm border into the surrounding compacted sand.
	float blend = kind == PACKED ? 0.0 : smoothstep(0.0, 0.35, inner);
	// The sand material is only evaluated where it is actually visible.
	vec3 albedo = vec3(0.6, 0.47, 0.32);
	float rough = 0.88;
	float height = 0.0;
	if (blend < 1.0 && !(rocky && packed >= 1.0)) { sand_like(p, packed, detail, albedo, rough, height); }
	vec3 lane_albedo = albedo;
	float lane_rough = rough;
	float lane_height = height;
	if (kind == ROAD || kind == WET_ROAD) {
		float n = fbm(p * 0.55);
		float speck = mix(0.5, hash12(floor(p * 45.0)), detail);
		lane_albedo = vec3(0.105, 0.105, 0.11) * (0.82 + 0.32 * n) * (0.85 + 0.3 * speck);
		lane_albedo *= 1.0 - 0.1 * smoothstep(0.56, 0.64, vnoise(p * 0.7 + 4.0));
		float crack = 0.0;
		if (detail > 0.0) {
			vec3 v = voronoi(p * 0.6);
			crack = (1.0 - smoothstep(0.0, 0.03, v.y - v.x)) * smoothstep(0.35, 0.75, vnoise(p * 0.21)) * detail;
		}
		lane_albedo *= 1.0 - crack * 0.45;
		float paint = 0.0;
		if (skid_pad) {
			float r = distance(p, pad_circle.xy);
			paint = 1.0 - smoothstep(0.07, 0.1, abs(r - (pad_circle.z - 0.9)));
			paint = max(paint, (1.0 - smoothstep(0.06, 0.09, abs(r - 8.0))) * step(0.5, fract(atan(p.y - pad_circle.y, p.x - pad_circle.x) * 6.0 / 3.14159)));
			paint = max(paint, (1.0 - smoothstep(0.1, 0.13, abs(p.y - pad_circle.y))) * step(pad_circle.x + pad_circle.z - 6.5, p.x) * step(p.x, pad_circle.x + pad_circle.z - 1.4));
		} else if (in_circuit) {
			// Edge lines and a dashed centre line by arc length; red/white kerbs.
			paint = 1.0 - smoothstep(0.06, 0.09, abs(abs(across) - (circuit_half - 0.75)));
			paint = max(paint, (1.0 - smoothstep(0.05, 0.08, abs(across))) * step(fract(along / 6.0), 0.55));
			if (abs(across) > circuit_half - 0.5) {
				lane_albedo = mix(vec3(0.62, 0.1, 0.07), vec3(0.8, 0.79, 0.75), step(0.5, fract(along / 2.0)));
			}
		} else {
			paint = 1.0 - smoothstep(0.06, 0.09, abs(abs(across) - (lane_half - 0.55)));
			paint = max(paint, (1.0 - smoothstep(0.05, 0.08, abs(across))) * step(fract(p.x / 6.0), 0.55));
		}
		paint *= 0.85 + 0.15 * vnoise(p * 9.0);
		lane_albedo = mix(lane_albedo, vec3(0.78, 0.77, 0.72), paint);
		lane_rough = mix(0.74 - crack * 0.1, 0.55, paint);
		lane_height = (speck * 0.0015 - crack * 0.004) * detail;
		specular = 0.4;
		if (kind == WET_ROAD) {
			// Rain-soaked asphalt: darker, glossy, with standing puddles.
			float puddle = smoothstep(0.52, 0.62, fbm(p * 0.35 + 7.0));
			lane_albedo *= mix(0.62, 0.45, puddle);
			lane_rough = mix(0.22, 0.03, puddle) * (1.0 - paint * 0.3);
			lane_height *= 1.0 - puddle;
			specular = 0.55;
		}
	} else if (kind == SNOW) {
		// Wind-packed snow; tyres compact it into grey-blue, slightly dirty ruts.
		float n = fbm(p * 0.6);
		float drift = fbm(p * 2.5 + 3.0);
		lane_albedo = mix(vec3(0.8, 0.84, 0.9), vec3(0.93, 0.94, 0.96), n);
		lane_albedo = mix(lane_albedo, vec3(0.62, 0.64, 0.68), smoothstep(0.02, 0.2, depth_here));
		lane_albedo = mix(lane_albedo, vec3(0.5, 0.47, 0.42), smoothstep(0.25, 0.45, depth_here) * 0.5);
		float sparkle = step(0.985, hash12(floor(p * 60.0))) * detail * (1.0 - rut);
		lane_albedo += sparkle * 0.25;
		lane_rough = mix(0.62, 0.45, rut);
		lane_height = (drift * 0.012 + n * 0.01) * detail * (1.0 - rut);
		specular = 0.35;
	} else if (kind == ICE) {
		// Clear blue-grey ice with frosted patches, trapped bubbles and cracks;
		// sliding tyres leave pale scratch marks.
		float frost = smoothstep(0.45, 0.7, fbm(p * 0.5 + 1.0));
		float crack = 0.0;
		if (detail > 0.0) {
			vec3 v = voronoi(p * 0.9 + 0.4 * vec2(vnoise(p * 0.7), vnoise(p * 0.7 + 3.0)));
			crack = (1.0 - smoothstep(0.0, 0.025, v.y - v.x)) * detail;
		}
		lane_albedo = mix(vec3(0.42, 0.52, 0.6), vec3(0.78, 0.84, 0.9), frost * 0.8);
		lane_albedo = mix(lane_albedo, vec3(0.9, 0.94, 0.97), crack * 0.6);
		lane_albedo = mix(lane_albedo, vec3(0.85, 0.88, 0.92), rubber * 0.7);
		lane_rough = mix(0.04, 0.35, max(frost, rubber));
		lane_height = (crack * 0.004 + frost * 0.002) * detail;
		specular = 0.6;
	} else if (kind == GRASS) {
		// Short desert-edge grass with dry patches; ruts tear it open to dark soil.
		float n = fbm(p * 0.5);
		float dry = smoothstep(0.5, 0.7, fbm(p * 0.25 + 9.0));
		float blades = mix(0.5, hash12(floor(p * 38.0)), detail);
		lane_albedo = mix(vec3(0.17, 0.27, 0.08), vec3(0.3, 0.38, 0.12), n) * (0.8 + 0.4 * blades);
		lane_albedo = mix(lane_albedo, vec3(0.5, 0.45, 0.24), dry * 0.7);
		lane_albedo = mix(lane_albedo, vec3(0.2, 0.15, 0.1), smoothstep(0.01, 0.08, depth_here));
		lane_rough = 0.86;
		lane_height = (blades * 0.004 + n * 0.006) * detail * (1.0 - rut);
	} else if (kind == GRAVEL) {
		// Rounded 3–6 cm stones over finer grit; fades to its mean colour when tiny.
		float broad = 0.92 + 0.16 * fbm(p * 0.4);
		vec3 mean = vec3(0.44, 0.415, 0.38) * broad;
		lane_albedo = mean;
		lane_height = 0.0;
		float pebbles = max(fine, detail * 0.5);
		if (pebbles > 0.0) {
			vec2 q = p + 0.35 * vec2(vnoise(p * 1.7), vnoise(p * 1.7 + 9.0));
			vec3 v = voronoi(q * 17.0);
			float stone = smoothstep(0.02, 0.2, v.y - v.x);
			vec3 rock = mix(vec3(0.36, 0.35, 0.33), vec3(0.6, 0.57, 0.52), v.z);
			rock = mix(rock, vec3(0.55, 0.45, 0.35), step(0.8, fract(v.z * 7.3)));
			vec3 g = mix(vec3(0.24, 0.22, 0.2), rock, stone);
			float grit_height = 0.0;
			if (fine > 0.0) {
				vec3 grit = voronoi(p * 41.0);
				g *= mix(1.0, 0.9 + 0.2 * grit.z, fine);
				grit_height = (1.0 - grit.x) * 0.0015 * fine;
			}
			lane_albedo = mix(mean, g * broad, pebbles);
			lane_height = stone * 0.008 * pebbles + grit_height;
		}
		lane_albedo *= 1.0 - rut * 0.15;
		lane_rough = 0.88;
	} else if (kind == CLAY) {
		float n = fbm(p * 0.8);
		lane_albedo = mix(vec3(0.38, 0.24, 0.15), vec3(0.5, 0.33, 0.21), n);
		// Domain-warped crack network: irregular, thin, and only where dry.
		float crack = 0.0;
		if (detail > 0.0) {
			vec2 q = p * 2.4 + 1.1 * vec2(vnoise(p * 1.3), vnoise(p * 1.3 + 5.0));
			vec3 v = voronoi(q);
			crack = (1.0 - smoothstep(0.0, 0.03, v.y - v.x)) * detail * (1.0 - rut) * smoothstep(0.3, 0.6, vnoise(p * 0.35 + 3.0));
			lane_albedo = mix(lane_albedo * mix(1.0, 0.96 + 0.08 * v.z, detail), vec3(0.24, 0.15, 0.1), crack * 0.8);
		}
		lane_albedo *= 1.0 - rut * 0.22;
		lane_rough = mix(0.93, 0.8, rut);
		lane_height = (-crack * 0.01 + vnoise(p * 6.0) * 0.004) * detail;
	} else if (kind == MUD || kind == WATER) {
		float n = fbm(p * 0.7);
		float wet = smoothstep(0.44, 0.6, fbm(p * 0.45 + 2.0)) + rut * 1.3;
		if (kind == WATER) { wet = 1.0; }
		wet = clamp(wet, 0.0, 1.0);
		vec3 clods = detail > 0.0 ? voronoi(p * 3.2) : vec3(0.5, 0.5, 0.5);
		lane_albedo = mix(vec3(0.13, 0.095, 0.065), vec3(0.2, 0.15, 0.1), n) * (0.94 + 0.12 * clods.z);
		lane_albedo = mix(lane_albedo, vec3(0.075, 0.06, 0.045), wet * 0.8);
		lane_rough = mix(0.42, 0.05, wet);
		lane_height = (smoothstep(0.1, 0.5, clods.x) * 0.006 + n * 0.01) * (1.0 - wet) * detail;
		specular = mix(0.35, 0.55, wet);
	} else if (kind == -1) {
		// Painted lip of the jump.
		float stripe = step(0.5, fract((p.x + p.y) * 1.25));
		lane_albedo = mix(vec3(0.07, 0.07, 0.07), vec3(0.78, 0.6, 0.12), stripe);
		lane_rough = 0.6;
		lane_height = 0.0;
	} else if (kind == -2) {
		// Weathered log lying across the track.
		float grain = vnoise(vec2(across * 6.0, section_s * 40.0));
		lane_albedo = mix(vec3(0.2, 0.14, 0.09), vec3(0.34, 0.25, 0.16), grain);
		lane_rough = 0.85;
		lane_height = grain * 0.01 * detail;
	} else if (kind == -3) {
		// Chequered start/finish line.
		float check = mod(floor(across / 0.3) + floor((along - circuit_start_line) / 0.3), 2.0);
		lane_albedo = mix(vec3(0.06, 0.06, 0.06), vec3(0.82, 0.81, 0.77), check);
		lane_rough = 0.6;
		lane_height = 0.0;
	} else if (kind == ROCK) {
		// Circuit rock section: the trail's limestone slabs.
		rocky_ground(p, detail, fine, 1.0, 0.0, lane_albedo, lane_rough, lane_height);
		specular = 0.3;
	}
	albedo = mix(albedo, lane_albedo, blend);
	rough = mix(rough, lane_rough, blend);
	height = mix(height, lane_height, blend);
	if (kind != ROAD && kind != WET_ROAD && kind != ICE && kind != -1 && kind != -3) { albedo *= 1.0 - rut * 0.12; }
	if (kind == ROAD) {
		albedo = mix(albedo, vec3(0.035, 0.034, 0.033), rubber * 0.75 * blend);
		rough = mix(rough, 0.6, rubber * blend);
	}
	if (rocky) {
		vec3 rock_albedo;
		float rock_rough;
		float rock_height;
		rocky_ground(p, detail, fine, ground_paint.g, ground_paint.r, rock_albedo, rock_rough, rock_height);
		rock_albedo = mix(rock_albedo, rock_albedo * 0.45, rubber * smoothstep(0.35, 0.75, ground_paint.g) * 0.8);
		albedo = mix(albedo, rock_albedo * (1.0 - rut * 0.15), packed);
		rough = mix(rough, rock_rough, packed);
		height = mix(height, rock_height, packed);
		specular = 0.3;
	}
	ALBEDO = albedo;
	ROUGHNESS = rough;
	SPECULAR = specular;
	vec3 n = bump(normalize(world_normal), world, height);
	NORMAL = normalize((VIEW_MATRIX * vec4(n, 0.0)).xyz);
}
