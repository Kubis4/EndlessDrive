shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;
// Dirt and snow on a car body, drawn as a next pass over its paint (vehicle.gd
// adds them up): mud thrown by the tyres splatters the sills and wheel arches
// first, then the lower doors and bumpers; dust settles as a film from the
// bottom up; snow lies on the roof, bonnet and other upward faces and grows
// into a layer while it falls. Positions are the rig's (+X forward, y from
// axle height).
uniform float mud = 0.0;
uniform float dust = 0.0;
uniform float snow = 0.0;
// Slush and snow thrown up by the tyres (lower body, like the mud).
uniform float slush = 0.0;
uniform vec3 mud_color : source_color = vec3(0.32, 0.23, 0.15);
uniform vec3 dust_color : source_color = vec3(0.58, 0.5, 0.39);
uniform float front_axle = 1.35;
uniform float rear_axle = -1.35;
varying vec3 local;
varying vec3 world_normal;

float hash(vec3 p) {
	p = fract(p * 0.3183099 + 0.1);
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float noise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
		mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float coat(float spot, float amount) {
	return smoothstep(-0.05, 0.05, amount * 0.9 - (1.0 - clamp(spot, 0.0, 1.0))) * smoothstep(0.0, 0.03, amount);
}

void vertex() {
	// Just off the paint, so the layer never flickers against it.
	VERTEX += NORMAL * 0.006;
	local = VERTEX;
	world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
	float low = 1.0 - smoothstep(-0.1, 1.0, local.y);
	// Behind each wheel the tyre throws hardest.
	float arch = max(exp(-pow(local.x - front_axle + 0.35, 2.0) / 0.35), exp(-pow(local.x - rear_axle + 0.35, 2.0) / 0.35)) * (1.0 - smoothstep(0.2, 0.9, local.y));
	float splats = noise(local * 9.0) * 0.55 + noise(local * 26.0) * 0.3 + noise(local * 70.0) * 0.15;
	float mud_cover = coat(low * 0.45 + arch * 0.35 + splats * 0.35 - 0.1, mud);
	float dust_cover = coat(low * 0.6 + splats * 0.25 + 0.1, dust) * 0.75;
	vec3 n = normalize(world_normal);
	float up = smoothstep(0.55, 0.9, n.y);
	float snow_cover = up * smoothstep(0.0, 0.12, snow - (1.0 - noise(local * 5.0)) * 0.25);
	vec3 colour = dust_color * (0.85 + 0.3 * splats);
	float alpha = dust_cover;
	colour = mix(colour, mud_color * (0.75 + 0.5 * noise(local * 40.0)), mud_cover);
	alpha = max(alpha, mud_cover);
	// Snow thrown by the tyres collects around the lower body and arches.
	float slush_cover = coat(low * 0.5 + arch * 0.4 + (1.0 - splats) * 0.4, slush) * (1.0 - smoothstep(0.45, 1.0, local.y));
	colour = mix(colour, mix(vec3(0.86, 0.89, 0.93), vec3(0.72, 0.76, 0.8), splats * 0.35), slush_cover);
	alpha = max(alpha, slush_cover);
	colour = mix(colour, vec3(0.92, 0.94, 0.97), snow_cover);
	alpha = max(alpha, snow_cover);
	if (alpha < 0.02) { discard; }
	ALBEDO = colour;
	ALPHA = alpha;
	ROUGHNESS = mix(0.9, 0.55, mud_cover * 0.6);
	SPECULAR = 0.25;
}
