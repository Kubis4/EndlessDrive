shader_type spatial;
render_mode cull_disabled, depth_draw_never, diffuse_lambert_wrap, specular_schlick_ggx;
uniform vec3 grain_color : source_color = vec3(0.57, 0.45, 0.30);
/// Procedural sprite thrown by the tyre for the surface under it:
/// 0 sand grain, 1 gravel stone, 2 mud clod, 3 water droplet, 4 snow flake,
/// 5 grass blade, 6 ice chip, 7 clay crumb, 8 soil clod, 9 leaf litter.
/// Sprites are lit like the scene (a rounded normal gives solid ones volume),
/// so thrown material darkens in shade instead of glowing.
uniform int sprite_kind = 0;
varying vec4 tint;
varying float seed;
varying float age;
void vertex() {
	tint = COLOR;
	float a = INSTANCE_CUSTOM.x;
	seed = fract(a * 7.31 + 0.5);
	// Life fraction (0 born .. 1 gone), to fade pieces out instead of popping.
	age = INSTANCE_CUSTOM.y;
	VERTEX.xy = mat2(vec2(cos(a), sin(a)), vec2(-sin(a), cos(a))) * VERTEX.xy;
	MODELVIEW_MATRIX = VIEW_MATRIX * mat4(INV_VIEW_MATRIX[0], INV_VIEW_MATRIX[1], INV_VIEW_MATRIX[2], MODEL_MATRIX[3]);
	MODELVIEW_MATRIX[0] *= length(MODEL_MATRIX[0].xyz);
	MODELVIEW_MATRIX[1] *= length(MODEL_MATRIX[1].xyz);
}
// Irregular outline: radius wobbles with angle (seeded per particle).
float lumpy(vec2 p, float amount, float lobes) {
	float ang = atan(p.y, p.x);
	float r = 1.0 + amount * (sin(ang * lobes + seed * 6.28) * 0.6 + sin(ang * (lobes + 2.0) - seed * 11.0) * 0.4);
	return length(p) / r;
}
// View-space normal of a rounded lump facing the camera (flatness 0..1).
vec3 dome(vec2 p, float flatness) {
	vec2 q = p * (1.0 - flatness * 0.6);
	return normalize(vec3(q.x, -q.y, sqrt(max(0.05, 1.0 - dot(q, q)))));
}
float hash1(float n) {
	return fract(sin(n) * 43758.5453);
}
// A spray of up to four fragments of different sizes inside the sprite, each
// with a wobbly outline (lobes, roughness). Returns the distance field (< 1
// inside a fragment), the fragment's local position (for its rounded normal)
// and a per-fragment random value (for its shade). Thrown material then reads
// as broken pieces and droplets, not one smooth grain per particle.
float cluster(vec2 p, int count, float spread, float lobes, float roughness, out vec2 local, out float piece) {
	float best = 9.0;
	local = p;
	piece = seed;
	for (int i = 0; i < 4; i++) {
		if (i >= count) { break; }
		float s = seed * 13.7 + float(i) * 3.1;
		vec2 c = (vec2(hash1(s), hash1(s + 1.7)) - 0.5) * spread * (i == 0 ? 0.25 : 1.0);
		float r = mix(0.16, 0.42, hash1(s + 4.2)) * (i == 0 ? 1.6 : 1.0);
		vec2 q = (p - c) / r;
		float ang = atan(q.y, q.x);
		float d = length(q) / (1.0 + roughness * (sin(ang * lobes + s) * 0.6 + sin(ang * (lobes + 2.0) - s * 1.7) * 0.4));
		if (d < best) {
			best = d;
			local = q;
			piece = hash1(s + 9.0);
		}
	}
	return best;
}
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	vec3 base = tint.rgb;
	vec3 col = base;
	float alpha = 1.0;
	float rough = 0.9;
	vec3 n = vec3(0.0, 0.0, 1.0);
	vec2 local;
	float piece;
	if (sprite_kind == 1) {
		// Gravel: a few angular stones of mixed size and tone.
		float d = cluster(p, 3, 1.3, 3.0, 0.22, local, piece);
		vec2 q = abs(local);
		d = max(d, max(q.x * 0.8 + q.y * 0.55, q.y) * 0.95);
		alpha = 1.0 - smoothstep(0.82, 1.0, d);
		float facet = step(0.0, local.x * (piece - 0.5) + local.y * 0.6);
		col = mix(vec3(0.26, 0.25, 0.23), vec3(0.47, 0.44, 0.39), piece) * mix(vec3(1.0), base, 0.25) * (0.8 + 0.3 * facet);
		n = normalize(dome(local * 0.8, 0.4) + vec3((facet - 0.5) * 0.6, 0.0, 0.0));
		rough = 0.75;
	} else if (sprite_kind == 2) {
		// Mud: wet splats and blobs with darker cores and glossy tops.
		float d = cluster(p, 4, 1.5, 3.0, 0.35, local, piece);
		alpha = 1.0 - smoothstep(0.7, 0.95, d);
		col = mix(vec3(0.1, 0.07, 0.045), vec3(0.22, 0.155, 0.1), piece) * mix(vec3(1.0), base, 0.25);
		col *= 0.75 + 0.35 * smoothstep(0.9, 0.2, d);
		n = dome(local * 0.9, 0.2);
		rough = 0.3;
	} else if (sprite_kind == 3) {
		// Water: a scatter of clear drops with bright rims inside a faint mist.
		float d = cluster(p, 4, 1.6, 2.0, 0.08, local, piece);
		float drop = (1.0 - smoothstep(0.65, 0.9, d)) * mix(0.3, 0.8, smoothstep(0.4, 0.85, d));
		float mist = (1.0 - smoothstep(0.2, 1.0, length(p))) * 0.18;
		alpha = max(drop, mist);
		col = mix(vec3(0.62, 0.7, 0.74), vec3(0.88, 0.92, 0.95), smoothstep(0.5, 0.85, d)) * mix(vec3(1.0), base, 0.35);
		n = dome(local * 0.9, 0.0);
		rough = 0.05;
	} else if (sprite_kind == 4) {
		// Snow: powder thrown up as a soft cloud of clumps, thinning as it spreads.
		float d = cluster(p, 4, 1.4, 4.0, 0.25, local, piece);
		alpha = (1.0 - smoothstep(0.3, 1.1, d)) * 0.85 * (1.0 - age * 0.6);
		col = vec3(0.93, 0.95, 1.0) * (0.9 + 0.1 * piece) * mix(vec3(1.0), base, 0.15);
		n = dome(local * 0.7, 0.4);
	} else if (sprite_kind == 5) {
		// Grass: a thin curved blade/torn leaf, green with a lighter midrib.
		float bend = p.x + (p.y * p.y) * (seed - 0.5) * 0.8;
		float width = 0.32 * (1.0 - smoothstep(0.3, 1.0, abs(p.y)));
		alpha = 1.0 - smoothstep(width * 0.75, width, abs(bend));
		float rib = 1.0 - smoothstep(0.0, 0.08, abs(bend));
		col = mix(vec3(0.2, 0.32, 0.09), vec3(0.34, 0.44, 0.16), seed) * (0.85 + 0.25 * rib) * mix(vec3(1.0), base, 0.3);
	} else if (sprite_kind == 6) {
		// Ice chip: sharp translucent shard.
		vec2 q = abs(p);
		float d = q.x / (0.45 + 0.2 * seed) + q.y;
		alpha = (1.0 - smoothstep(0.85, 0.95, d)) * 0.8;
		col = vec3(0.72, 0.82, 0.9);
		n = normalize(vec3(sign(p.x) * 0.4, 0.0, 1.0));
		rough = 0.08;
	} else if (sprite_kind == 7 || sprite_kind == 8) {
		// Clay or forest soil: crumbly clods of mixed size with grit specks.
		float d = cluster(p, 4, 1.4, 5.0, 0.28, local, piece);
		alpha = 1.0 - smoothstep(0.75, 0.95, d);
		float grit = step(0.8, fract(sin(dot(floor(local * 4.0 + seed * 9.0), vec2(12.9898, 78.233))) * 43758.5453));
		col = sprite_kind == 7 ? base * grain_color / vec3(0.57, 0.45, 0.30) * mix(0.7, 0.95, piece)
			: mix(vec3(0.15, 0.105, 0.07), vec3(0.28, 0.2, 0.13), piece);
		col = mix(col, vec3(0.42, 0.38, 0.32), grit * 0.5);
		n = dome(local * 0.85, 0.25);
	} else if (sprite_kind == 9) {
		// Forest litter: a small leaf or needle cluster, brown to olive.
		float bend = p.x * (1.0 + 0.3 * seed) + p.y * p.y * (seed - 0.5);
		float width = 0.55 * (1.0 - smoothstep(0.2, 0.95, abs(p.y)));
		alpha = 1.0 - smoothstep(width * 0.8, width, abs(bend));
		float vein = 1.0 - smoothstep(0.0, 0.06, abs(bend));
		col = mix(vec3(0.3, 0.2, 0.1), vec3(0.36, 0.33, 0.14), seed) * (0.85 + 0.2 * vein);
	} else {
		// Sand: a pinch of fine specks rather than one round grain.
		float d = cluster(p, 4, 1.6, 3.0, 0.1, local, piece);
		alpha = 1.0 - smoothstep(0.5, 0.95, d);
		col = base * (grain_color / vec3(0.57, 0.45, 0.30)) * mix(0.85, 1.1, piece);
		n = dome(local * 0.8, 0.0);
	}
	// Pieces thin out over their last quarter of life instead of popping.
	alpha *= 1.0 - smoothstep(0.75, 1.0, age);
	if (alpha < 0.02) discard;
	ALBEDO = col;
	ALPHA = alpha * tint.a;
	NORMAL = n;
	ROUGHNESS = rough;
	BACKLIGHT = (sprite_kind == 5 || sprite_kind == 9) ? col * 0.4 : vec3(0.0);
}
