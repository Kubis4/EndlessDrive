shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
uniform vec3 grain_color : source_color = vec3(0.57, 0.45, 0.30);
/// Procedural sprite thrown by the tyre for the surface under it:
/// 0 sand grain, 1 gravel stone, 2 mud clod, 3 water droplet, 4 snow flake,
/// 5 grass blade, 6 ice chip, 7 clay crumb.
uniform int sprite_kind = 0;
varying vec4 tint;
varying float seed;
void vertex() {
	tint = COLOR;
	float a = INSTANCE_CUSTOM.x;
	seed = fract(a * 7.31 + 0.5);
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
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	vec3 base = tint.rgb;
	vec3 col = base;
	float alpha = 1.0;
	// Light from the upper left for a little volume on solid sprites.
	float shade = 0.82 + 0.3 * clamp(dot(normalize(vec2(-0.6, -0.8)), p) * 0.5 + 0.5, 0.0, 1.0);
	if (sprite_kind == 1) {
		// Gravel: angular, faceted stone with a crisp edge.
		vec2 q = abs(p);
		float d = max(q.x * 0.86 + q.y * 0.5, q.y) / (0.8 + 0.15 * seed);
		d = max(d, lumpy(p, 0.08, 3.0) * 0.95);
		alpha = 1.0 - smoothstep(0.85, 1.0, d);
		float facet = step(0.0, p.x * (seed - 0.5) + p.y * 0.6);
		col = mix(vec3(0.45, 0.43, 0.4), vec3(0.62, 0.58, 0.52), seed) * base * (0.8 + 0.3 * facet) * shade;
	} else if (sprite_kind == 2) {
		// Mud clod: dark, blobby and wet with a glossy highlight.
		float d = lumpy(p, 0.22, 3.0);
		alpha = 1.0 - smoothstep(0.75, 0.95, d);
		float gloss = 1.0 - smoothstep(0.0, 0.3, length(p - vec2(-0.3, -0.35)));
		col = mix(vec3(0.15, 0.1, 0.065), vec3(0.25, 0.18, 0.11), seed) * shade * mix(vec3(1.0), base, 0.3) + vec3(0.3, 0.27, 0.22) * gloss * 0.5;
	} else if (sprite_kind == 3) {
		// Water droplet: clear, bright rim and highlight, partly transparent.
		float d = length(p * vec2(1.0, 0.85) + vec2(0.0, 0.1));
		alpha = (1.0 - smoothstep(0.75, 0.95, d)) * mix(0.45, 0.85, smoothstep(0.45, 0.9, d));
		float glint = 1.0 - smoothstep(0.0, 0.22, length(p - vec2(-0.25, -0.3)));
		col = mix(vec3(0.62, 0.72, 0.78), vec3(0.92, 0.96, 1.0), smoothstep(0.5, 0.9, d)) * mix(vec3(1.0), base, 0.35);
		col += vec3(glint);
		alpha = max(alpha, glint * 0.9);
	} else if (sprite_kind == 4) {
		// Snow: soft, bright puff made of a few overlapping lobes.
		float d = min(length(p - vec2(0.18, 0.1)) * 1.3, min(length(p + vec2(0.2, 0.05)) * 1.25, length(p - vec2(-0.05, -0.25)) * 1.4));
		alpha = (1.0 - smoothstep(0.35, 1.0, d)) * 0.92;
		col = vec3(0.93, 0.95, 1.0) * mix(vec3(1.0), base, 0.15) * (0.92 + 0.1 * shade);
	} else if (sprite_kind == 5) {
		// Grass: a thin curved blade/torn leaf, green with a lighter midrib.
		float bend = p.x + (p.y * p.y) * (seed - 0.5) * 0.8;
		float width = 0.32 * (1.0 - smoothstep(0.3, 1.0, abs(p.y)));
		alpha = 1.0 - smoothstep(width * 0.75, width, abs(bend));
		float rib = 1.0 - smoothstep(0.0, 0.08, abs(bend));
		col = mix(vec3(0.28, 0.42, 0.14), vec3(0.45, 0.55, 0.22), seed) * (0.85 + 0.25 * rib) * mix(vec3(1.0), base, 0.3);
	} else if (sprite_kind == 6) {
		// Ice chip: sharp translucent shard with a white glint.
		vec2 q = abs(p);
		float d = q.x / (0.45 + 0.2 * seed) + q.y;
		alpha = (1.0 - smoothstep(0.85, 0.95, d)) * 0.8;
		float glint = step(0.0, p.x - p.y * 0.5) * 0.3;
		col = vec3(0.78, 0.88, 0.95) * (0.9 + glint) + vec3(1.0) * (1.0 - smoothstep(0.0, 0.25, abs(p.x + p.y * 0.3))) * 0.25;
	} else if (sprite_kind == 7) {
		// Clay crumb: rounded chunk, matte, darker underside.
		float d = lumpy(p, 0.14, 4.0);
		alpha = 1.0 - smoothstep(0.78, 0.95, d);
		col = base * grain_color / vec3(0.57, 0.45, 0.30) * shade;
	} else {
		// Sand grain: soft round speck tinted by the dune colour ramp.
		float d = dot(p, p);
		alpha = 1.0 - smoothstep(0.2, 1.0, d);
		col = base * (grain_color / vec3(0.57, 0.45, 0.30)) * (0.88 + UV.y * 0.24);
	}
	if (alpha < 0.02) discard;
	ALBEDO = col;
	ALPHA = alpha * tint.a;
}
