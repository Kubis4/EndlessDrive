shader_type canvas_item;
// A sheet of old parchment: fibres, stains and browned edges, its right side torn, with
// a soft shadow on what lies beneath. `edge` is where it ends, as a share of the width.
uniform float edge = 0.9;
uniform vec2 area = vec2(800.0, 1080.0);

float hash(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * 0.1031);
	q += dot(q, q.yzx + 33.33);
	return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
	return noise(p) * 0.5 + noise(p * 2.1 + 3.7) * 0.27 + noise(p * 4.3 + 9.1) * 0.15 + noise(p * 8.7 + 1.3) * 0.08;
}

void fragment() {
	vec2 p = UV * area / 100.0;
	// The torn edge: wandering, with small bites.
	float line = edge + (fbm(vec2(p.y * 0.9, 2.0)) - 0.5) * 0.09 + (fbm(vec2(p.y * 6.0, 7.0)) - 0.5) * 0.028;
	float past = (UV.x - line) * area.x;
	if (past > 0.0) {
		// Shadow on the world below.
		COLOR = vec4(0.05, 0.03, 0.01, 0.42 * (1.0 - smoothstep(0.0, 34.0, past)));
	} else {
		float fibres = noise(vec2(p.x * 26.0, p.y * 3.0)) * 0.5 + noise(vec2(p.x * 3.0, p.y * 30.0)) * 0.5;
		float clouds = fbm(p * 0.9);
		vec3 paper = mix(vec3(0.88, 0.79, 0.6), vec3(0.94, 0.87, 0.7), smoothstep(0.2, 0.8, clouds));
		paper *= 0.98 + 0.04 * fibres;
		// Foxing: small brown spots, and larger stains.
		float spots = smoothstep(0.84, 0.95, fbm(p * 5.0 + 40.0)) * 0.1;
		float stains = smoothstep(0.62, 0.85, fbm(p * 0.45 + 11.0)) * 0.1;
		paper = mix(paper, vec3(0.5, 0.33, 0.16), spots + stains);
		// Browned towards every edge, darkest along the tear.
		float inward = min(min(UV.x * area.x, -past), min(UV.y, 1.0 - UV.y) * area.y);
		float browned = 1.0 - smoothstep(0.0, 70.0, inward + (clouds - 0.5) * 40.0);
		paper = mix(paper, vec3(0.45, 0.3, 0.14), browned * browned * 0.6);
		// The torn rim shows the lighter fibre inside the sheet.
		paper = mix(paper, vec3(0.97, 0.91, 0.76), (1.0 - smoothstep(0.0, 5.0, -past)) * 0.75);
		COLOR = vec4(paper, 1.0);
	}
}
