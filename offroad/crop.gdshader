shader_type spatial;
render_mode cull_disabled, diffuse_lambert, specular_disabled;
// Field crops: long ribbons of plants (endless_world.gd). They sway in the wind,
// lie down around the car, and are lit from above, like a canopy.
uniform sampler2D plants : source_color, filter_linear_mipmap, repeat_enable;
uniform float tall = 1.0;
uniform float wind = 0.3;
uniform vec3 car = vec3(0.0, -1000.0, 0.0);
// Closer than this the real plants stand instead (0: none); far off the ribbons melt
// into the field's own colour, so no grid of them shows.
uniform float near_cut = 0.0;
uniform float far_cut = 0.0;
// Over how many metres each gives way, stalk by stalk: a wide band shows no edge.
uniform float near_band = 3.0;
uniform float far_band = 8.0;
uniform vec3 field_colour : source_color = vec3(0.5);
varying vec3 world_at;
uniform sampler2D crush_map : hint_default_black, filter_nearest, repeat_enable;
varying vec2 place;

void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	place = w.xz;
	world_at = w;
	float top = 1.0 - UV.y;
	float sway = sin(TIME * 1.7 + w.x * 0.31 + w.z * 0.27) + 0.45 * sin(TIME * 3.9 + w.x * 0.9 - w.z * 0.6);
	VERTEX.xz += vec2(0.8, 0.6) * sway * (0.03 + wind * 0.14) * top * tall;
	vec2 from_car = w.xz - car.xz;
	float distance_to_car = length(from_car);
	float bent = (1.0 - smoothstep(0.9, 2.9, distance_to_car)) * top;
	if (distance_to_car > 0.01) { VERTEX.xz += from_car / distance_to_car * bent * tall * 0.75; }
	VERTEX.y -= bent * tall * 0.55;
}

void fragment() {
	// Driven over: only stubble is left.
	if (UV.y < 0.88 && texture(crush_map, place / 256.0).r > 0.5) { discard; }
	float seen_from = distance(world_at, CAMERA_POSITION_WORLD);
	if (seen_from < near_cut + near_band * fract(sin(dot(floor(place * 6.0), vec2(12.9898, 78.233))) * 43758.5453)) { discard; }
	if (far_cut > 0.0 && seen_from > far_cut + far_band * fract(sin(dot(floor(place * 6.0), vec2(39.3, 11.7))) * 91.17)) { discard; }
	vec4 texel = texture(plants, UV);
	ALBEDO = mix(texel.rgb * COLOR.rgb, field_colour, smoothstep(45.0, 130.0, seen_from) * 0.7);
	ALPHA = texel.a;
	ALPHA_SCISSOR_THRESHOLD = 0.42;
	NORMAL = normalize((VIEW_MATRIX * vec4(0.0, 1.0, 0.0, 0.0)).xyz + NORMAL * 0.15);
	ROUGHNESS = 0.9;
}
