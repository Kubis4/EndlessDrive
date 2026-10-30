shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
uniform vec3 grain_color : source_color = vec3(0.57, 0.45, 0.30);
varying vec4 tint;
void vertex() {
	tint = COLOR;
	float a = INSTANCE_CUSTOM.x;
	VERTEX.xy = mat2(vec2(cos(a), sin(a)), vec2(-sin(a), cos(a))) * VERTEX.xy;
	MODELVIEW_MATRIX = VIEW_MATRIX * mat4(INV_VIEW_MATRIX[0], INV_VIEW_MATRIX[1], INV_VIEW_MATRIX[2], MODEL_MATRIX[3]);
	MODELVIEW_MATRIX[0] *= length(MODEL_MATRIX[0].xyz);
	MODELVIEW_MATRIX[1] *= length(MODEL_MATRIX[1].xyz);
}
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float d = dot(p, p);
	float edge = 1.0 - smoothstep(0.2, 1.0, d);
	// The per-surface particle colour (sand, gravel, clods, water spray) arrives
	// through COLOR; grain_color only tints the default dune sand ramp slightly.
	ALBEDO = tint.rgb * (grain_color / vec3(0.57, 0.45, 0.30)) * (0.88 + UV.y * 0.24);
	ALPHA = edge * tint.a;
}
