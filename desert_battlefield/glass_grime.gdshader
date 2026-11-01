shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;
// Dirt, snow and rain on a car's glass, drawn as a next pass over it
// (vehicle.gd sets the amounts). On the windscreen each wiper keeps an arc
// cleaner: inside it the coat is the wiped amount (mud left as smears along
// the blade's path), the dirt it pushes aside piles up along the arc's edges,
// and raindrops gather again behind the moving blade. Positions are the rig's.
uniform float mud = 0.0;
uniform float dust = 0.0;
uniform float snow = 0.0;
// The same coats inside the wiped arcs.
uniform float wiped_mud = 0.0;
uniform float wiped_dust = 0.0;
uniform float wiped_snow = 0.0;
// The arcs' coats before the current wipe (ahead of the blades).
uniform float prev_mud = 0.0;
uniform float prev_dust = 0.0;
uniform float prev_snow = 0.0;
uniform float rain = 0.0;
uniform vec3 mud_color : source_color = vec3(0.32, 0.23, 0.15);
uniform vec3 dust_color : source_color = vec3(0.6, 0.53, 0.42);
// Wipers: pivot, sweep plane (axis = screen normal, rest = blade direction at
// rest, sign = sweep direction), reach, sweep angle and the current angles.
uniform vec3 wiper_pivot_a = vec3(0.0);
uniform vec3 wiper_pivot_b = vec3(0.0);
uniform vec3 wiper_rest_a = vec3(0.0, 0.0, -1.0);
uniform vec3 wiper_rest_b = vec3(0.0, 0.0, -1.0);
uniform vec3 wiper_axis = vec3(0.7, 0.7, 0.0);
// Each wiper's own sweep axis (the glass under it).
uniform vec3 wiper_axis_a = vec3(0.7, 0.7, 0.0);
uniform vec3 wiper_axis_b = vec3(0.7, 0.7, 0.0);
uniform float wiper_sign = 1.0;
uniform float wiper_reach = 0.7;
uniform float wiper_sweep = 1.75;
uniform float wiper_angle_a = 0.0;
uniform float wiper_angle_b = 0.0;
uniform bool has_wipers = false;
// How far (angle) the blades have reached in the current wipe: the cleaner
// coat shows only behind them.
uniform float wiper_reached = 10.0;
varying vec3 local;
varying vec3 local_normal;
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

// Where p lies in a wiper's sweep: x = 1 inside the arc (soft rim), y = angle
// of p along the sweep (0 at rest), z = distance from the pivot, w = 1 where
// the blades have already passed in this wipe.
vec4 arc(vec3 p, vec3 pivot, vec3 rest, vec3 axis) {
	vec3 d = p - pivot;
	d -= axis * dot(d, axis);
	float r = length(d);
	vec3 side = normalize(cross(axis, rest)) * wiper_sign;
	float angle = atan(dot(d, side), dot(d, rest));
	float inside = smoothstep(0.12, 0.17, r) * (1.0 - smoothstep(wiper_reach - 0.03, wiper_reach, r))
		* smoothstep(-0.12, -0.02, angle) * (1.0 - smoothstep(wiper_sweep + 0.02, wiper_sweep + 0.12, angle));
	float passed = 1.0 - smoothstep(min(wiper_sweep, wiper_reached) - 0.02, min(wiper_sweep, wiper_reached) + 0.04, angle);
	return vec4(inside, angle, r, passed);
}

void vertex() {
	VERTEX += NORMAL * 0.004;
	local = VERTEX;
	local_normal = NORMAL;
	world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
	vec3 n = normalize(world_normal);
	float splats = noise(local * 11.0) * 0.55 + noise(local * 30.0) * 0.3 + noise(local * 80.0) * 0.15;
	// Thrown mud reaches the lower glass first; dust films it evenly.
	float lower = 1.0 - smoothstep(0.9, 1.5, local.y);
	float wipe = 0.0;
	float rim = 0.0;
	float behind = 1.0;
	float passed = 1.0;
	float r = 0.0;
	if (has_wipers && dot(local_normal, wiper_axis) > 0.5) {
		vec4 a = arc(local, wiper_pivot_a, wiper_rest_a, wiper_axis_a);
		vec4 b = arc(local, wiper_pivot_b, wiper_rest_b, wiper_axis_b);
		passed = a.x > b.x ? a.w : b.w;
		wipe = max(a.x, b.x);
		r = a.x > b.x ? a.z : b.z;
		// The pushed-aside dirt banks up just outside the arcs.
		float edge_a = smoothstep(0.0, 0.5, a.x) * (1.0 - smoothstep(0.5, 1.0, a.x));
		float edge_b = smoothstep(0.0, 0.5, b.x) * (1.0 - smoothstep(0.5, 1.0, b.x));
		rim = max(edge_a, edge_b);
		// Raindrops return behind the blade: fewer where it has just passed.
		float since_a = abs(a.y - wiper_angle_a);
		float since_b = abs(b.y - wiper_angle_b);
		behind = mix(1.0, smoothstep(0.0, 1.2, a.x > b.x ? since_a : since_b), wipe);
	}
	// Coats: outside and inside the arcs, smeared in arcs along the blade.
	float smear = 0.8 + 0.2 * sin(r * 90.0 + noise(local * 4.0) * 4.0) * noise(local * 7.0);
	float mud_amount = mix(mud, mix(prev_mud, wiped_mud * smear, passed), wipe) + rim * mud * 0.6;
	float dust_amount = mix(dust, mix(prev_dust, wiped_dust * smear, passed), wipe) + rim * dust * 0.4;
	float snow_amount = mix(snow, mix(prev_snow, wiped_snow, passed), wipe) + rim * snow * 0.8;
	// Side and rear panes stay clearer: a little thrown mud, no settled snow.
	float pane = smoothstep(0.35, 0.6, n.y);
	float mud_cover = coat(lower * 0.45 + splats * 0.55, mud_amount * mix(0.5, 1.0, pane)) * 0.48;
	float dust_cover = coat(0.35 + splats * 0.4 + lower * 0.25, dust_amount) * 0.35;
	float up = smoothstep(0.2, 0.7, n.y);
	float snow_cover = coat(0.3 + up * 0.5 + splats * 0.2, snow_amount) * pane;
	// Raindrops: small bright beads on a jittered grid.
	vec3 cell = floor(local * 34.0);
	vec3 f = fract(local * 34.0);
	vec3 centre = vec3(hash(cell), hash(cell + 7.1), hash(cell + 3.3)) * 0.6 + 0.2;
	float drop = (1.0 - smoothstep(0.1, 0.22, length(f - centre))) * step(1.0 - rain * 0.7, hash(cell + 11.0)) * behind;
	vec3 colour = dust_color * (0.85 + 0.3 * splats);
	float alpha = dust_cover;
	colour = mix(colour, mud_color * (0.75 + 0.5 * noise(local * 45.0)), mud_cover);
	alpha = max(alpha, mud_cover);
	colour = mix(colour, vec3(0.92, 0.94, 0.97), snow_cover);
	alpha = max(alpha, snow_cover);
	colour = mix(colour, vec3(0.85, 0.9, 0.95), drop * (1.0 - alpha));
	alpha = max(alpha, drop * 0.45);
	if (alpha < 0.02) { discard; }
	ALBEDO = colour;
	ALPHA = alpha;
	ROUGHNESS = mix(0.85, 0.05, drop);
	SPECULAR = mix(0.25, 0.7, drop);
}
