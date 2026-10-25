shader_type spatial;
render_mode unshaded, cull_back, depth_draw_never, depth_test_disabled, shadows_disabled;
uniform sampler2D scene_depth : hint_depth_texture, repeat_disable, filter_nearest;
uniform vec4 outline_color : source_color = vec4(1.0, 0.77, 0.35, 1.0);
void fragment() {
	float depth = textureLod(scene_depth, SCREEN_UV, 0.0).r;
	vec3 ndc = vec3(SCREEN_UV, depth) * 2.0 - 1.0;
	vec4 scene = INV_PROJECTION_MATRIX * vec4(ndc, 1.0);
	float occluder_distance = -scene.z / scene.w;
	float car_distance = -VERTEX.z;
	// Only the pixels actually behind terrain/trees/structures become visible.
	// This also handles foliage, which deliberately has no bulky collider.
	if (occluder_distance >= car_distance - 0.06) { discard; }
	float rim = pow(1.0 - abs(dot(normalize(NORMAL), normalize(VIEW))), 2.5);
	ALBEDO = outline_color.rgb;
	ALPHA = outline_color.a * (0.12 + rim * 0.85);
}
