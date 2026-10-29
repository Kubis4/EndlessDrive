uniform sampler2D soil_map : filter_linear, repeat_disable;
uniform bool soil_enabled = false;

vec4 sand_spline(vec2 p) {
	vec2 f = fract((p + vec2(128.0)) * 4.0);
	vec2 f2 = f * f;
	vec2 f3 = f2 * f;
	vec2 w0 = pow(vec2(1.0) - f, vec2(3.0)) / 6.0;
	vec2 w1 = (3.0 * f3 - 6.0 * f2 + 4.0) / 6.0;
	vec2 w2 = (-3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0) / 6.0;
	vec2 w3 = f3 / 6.0;
	return vec4(w0 + w1, w2 + w3);
}

float sand_depth(vec2 p) {
	if (!soil_enabled || max(abs(p.x), abs(p.y)) >= 128.0) { return 0.0; }
	// Cubic B-spline reconstruction: sixteen samples collapsed into four bilinear
	// fetches. The CPU uses the same weights; convex weights cannot overshoot.
	vec2 grid = (p + vec2(128.0)) * 4.0;
	vec2 f = fract(grid);
	vec4 g = sand_spline(p);
	vec2 w1 = (3.0 * f * f * f - 6.0 * f * f + 4.0) / 6.0;
	vec2 w3 = f * f * f / 6.0;
	vec2 lo = (floor(grid) - vec2(1.0) + w1 / g.xy + vec2(0.5)) / 1025.0;
	vec2 hi = (floor(grid) + vec2(1.0) + w3 / g.zw + vec2(0.5)) / 1025.0;
	float a = textureLod(soil_map, lo, 0.0).r;
	float b = textureLod(soil_map, vec2(hi.x, lo.y), 0.0).r;
	float c = textureLod(soil_map, vec2(lo.x, hi.y), 0.0).r;
	float d = textureLod(soil_map, hi, 0.0).r;
	return mix(mix(a, b, g.z), mix(c, d, g.z), g.w);
}

vec3 sand_normal(vec2 p, vec3 base) {
	float dx = (sand_depth(p + vec2(0.25, 0)) - sand_depth(p - vec2(0.25, 0))) * 2.0;
	float dz = (sand_depth(p + vec2(0, 0.25)) - sand_depth(p - vec2(0, 0.25))) * 2.0;
	return normalize(vec3(base.x / max(base.y, 0.05) + dx, 1.0, base.z / max(base.y, 0.05) + dz));
}
