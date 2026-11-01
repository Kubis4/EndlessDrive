shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never, shadows_disabled, fog_disabled;
// The floating box that marks a place to loot (endless_expedition.gd): a faint
// body and corner brackets (their alpha in the vertex colour), turning slowly and
// bobbing, brighter when the car is close enough.
uniform vec3 tint : source_color = vec3(0.9, 0.72, 0.35);
instance uniform float near = 0.0;

void vertex() {
	float angle = TIME * 0.5 + MODEL_MATRIX[3].x;
	float c = cos(angle);
	float s = sin(angle);
	VERTEX = vec3(VERTEX.x * c - VERTEX.z * s, VERTEX.y + sin(TIME * 1.3 + MODEL_MATRIX[3].z) * 0.1, VERTEX.x * s + VERTEX.z * c);
}

void fragment() {
	ALBEDO = tint * (1.0 + 0.3 * near);
	float pulse = 0.8 + 0.2 * sin(TIME * 2.4);
	ALPHA = clamp(COLOR.a * pulse * (1.0 + 0.5 * near), 0.0, 1.0);
}
