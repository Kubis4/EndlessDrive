uniform vec2 road_view_start = vec2(-2.0);
uniform vec2 road_view_end = vec2(-2.0);
uniform float road_view_radius = 0.0;
uniform float road_view_aspect = 1.0;
uniform float road_view_height = 0.0;
uniform float road_view_depth_start = 0.0;
uniform float road_view_depth_end = 0.0;

float road_view_progress(vec2 uv) {
	vec2 aspect = vec2(road_view_aspect, 1.0);
	vec2 p = uv * aspect;
	vec2 a = road_view_start * aspect;
	vec2 ab = (road_view_end - road_view_start) * aspect;
	return clamp(dot(p - a, ab) / max(dot(ab, ab), 0.00001), 0.0, 1.0);
}
bool road_view_in_front(vec2 uv, float view_z) {
	float depth = mix(road_view_depth_start, road_view_depth_end, road_view_progress(uv));
	return view_z > depth + 0.25;
}

float road_view_window(vec2 uv) {
	vec2 aspect = vec2(road_view_aspect, 1.0);
	vec2 p = uv * aspect;
	vec2 a = road_view_start * aspect;
	vec2 b = road_view_end * aspect;
	vec2 ab = b - a;
	float t = road_view_progress(uv);
	float distance_to_road = length(p - (a + ab * t));
	return 1.0 - smoothstep(road_view_radius * 0.75, max(road_view_radius, 0.0001), distance_to_road);
}
float road_view_dither(vec2 pixel) {
	vec2 cell = mod(floor(pixel), 4.0);
	return mod(cell.x * 2.0 + cell.y * 3.0, 4.0) / 4.0 + 0.125;
}
