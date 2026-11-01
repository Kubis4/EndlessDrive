shader_type spatial;
render_mode cull_disabled, world_vertex_coords, diffuse_lambert, specular_disabled;
// Plants drawn in thousands (build_foliage.gd): they sway, lean away from the car
// and stay lying where it has driven over them (crush_map.gd).
uniform sampler2D plants : source_color, filter_linear_mipmap, repeat_enable;
uniform sampler2D crush_map : hint_default_black, filter_nearest, repeat_enable;
uniform float wind = 0.3;
uniform float snow = 0.0;
uniform vec3 car = vec3(0.0, -1000.0, 0.0);
varying float lifted;

vec3 turned(vec3 v, vec3 axis, float angle) {
	float c = cos(angle);
	float s = sin(angle);
	return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

void vertex() {
	vec3 base = MODEL_MATRIX[3].xyz;
	vec3 rel = VERTEX - base;
	vec2 crush = textureLod(crush_map, base.xz / 256.0, 0.0).rg;
	// Lying down in the way the car went, each a little differently.
	float jitter = fract(sin(dot(base.xz, vec2(12.9898, 78.233))) * 43758.5453);
	float lean = crush.r * (1.28 + 0.26 * jitter);
	float way = crush.g * TAU + (jitter - 0.5) * 0.9;
	vec2 from_car = base.xz - car.xz;
	float distance_to_car = length(from_car);
	float near = (1.0 - smoothstep(0.8, 3.0, distance_to_car)) * (1.0 - crush.r);
	if (near > 0.001 && distance_to_car > 0.01) {
		lean = max(lean, near * 1.0);
		way = atan(from_car.y, from_car.x);
	}
	if (lean > 0.001) {
		vec3 axis = normalize(cross(vec3(0.0, 1.0, 0.0), vec3(cos(way), 0.0, sin(way))));
		rel = turned(rel, axis, lean);
		NORMAL = turned(NORMAL, axis, lean);
	}
	float sway = sin(TIME * 1.7 + base.x * 0.31 + base.z * 0.27) + 0.45 * sin(TIME * 3.9 + base.x * 0.9 - base.z * 0.6);
	rel.xz += vec2(0.8, 0.6) * sway * (0.015 + wind * 0.06) * rel.y * rel.y * (1.0 - crush.r);
	VERTEX = base + rel;
	lifted = clamp(rel.y, 0.0, 1.0);
}

void fragment() {
	vec4 texel = texture(plants, UV);
	vec3 colour = texel.rgb * COLOR.rgb;
	ALBEDO = mix(colour, vec3(0.9, 0.92, 0.95), snow * 0.7);
	ALPHA = texel.a;
	ALPHA_SCISSOR_THRESHOLD = 0.45;
	// (Lit mostly from above, like the canopy it stands in.)
	NORMAL = normalize((VIEW_MATRIX * vec4(0.0, 1.0, 0.0, 0.0)).xyz + NORMAL * 0.45);
	ROUGHNESS = 0.9;
}
