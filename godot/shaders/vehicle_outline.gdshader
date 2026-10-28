shader_type spatial;
render_mode unshaded, cull_back, depth_draw_never, depth_test_disabled, shadows_disabled;
uniform sampler2D scene_depth : hint_depth_texture, repeat_disable, filter_nearest;
uniform vec4 outline_color : source_color = vec4(1.0, 0.77, 0.35, 1.0);
uniform float vehicle_near_depth = 0.0;
uniform vec2 body_probe_center = vec2(-1.0);
uniform vec2 body_probe_front = vec2(-1.0);
uniform vec2 body_probe_rear = vec2(-1.0);
float view_depth_at(vec2 uv, mat4 inverse_projection) {
	float depth = textureLod(scene_depth, uv, 0.0).r;
	#if CURRENT_RENDERER == RENDERER_COMPATIBILITY
	vec3 ndc = vec3(uv, depth) * 2.0 - 1.0;
	#else
	vec3 ndc = vec3(uv * 2.0 - 1.0, depth);
	#endif
	vec4 scene = inverse_projection * vec4(ndc, 1.0);
	return -scene.z / scene.w;
}
float body_hidden(vec2 uv, mat4 inverse_projection) {
	if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
	return view_depth_at(uv, inverse_projection) < vehicle_near_depth - 0.04 ? 1.0 : 0.0;
}
void fragment() {
	// A cutaway tunnel or low wall can hide only a tyre while the body remains
	// easy to see. The identification aid needs meaningful body occlusion.
	float hidden_body = body_hidden(body_probe_center, INV_PROJECTION_MATRIX) + body_hidden(body_probe_front, INV_PROJECTION_MATRIX) + body_hidden(body_probe_rear, INV_PROJECTION_MATRIX);
	if (hidden_body < 2.0) discard;
	float occluder_distance = view_depth_at(SCREEN_UV, INV_PROJECTION_MATRIX);
	float car_distance = -VERTEX.z;
	// Only the pixels actually behind terrain/trees/structures become visible.
	// This also handles foliage, which deliberately has no bulky collider.
	// The opaque depth includes the car itself. Never mistake its near-side
	// panels/wheels for an external occluder covering a farther car surface.
	if (occluder_distance >= min(car_distance - 0.06, vehicle_near_depth - 0.04)) { discard; }
	float rim = pow(1.0 - abs(dot(normalize(NORMAL), normalize(VIEW))), 2.5);
	ALBEDO = outline_color.rgb;
	ALPHA = outline_color.a * (0.12 + rim * 0.85);
}
