shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;
varying vec4 tint;
varying float phase;
varying float evolution;
void vertex() {
	tint = COLOR;
	phase = INSTANCE_CUSTOM.x;
	evolution = INSTANCE_CUSTOM.y;
	MODELVIEW_MATRIX = VIEW_MATRIX * mat4(INV_VIEW_MATRIX[0], INV_VIEW_MATRIX[1], INV_VIEW_MATRIX[2], MODEL_MATRIX[3]);
	MODELVIEW_MATRIX[0] *= length(MODEL_MATRIX[0].xyz);
	MODELVIEW_MATRIX[1] *= length(MODEL_MATRIX[1].xyz);
}
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float angle = phase + evolution * 1.2;
	vec2 q = mat2(vec2(cos(angle), sin(angle)), vec2(-sin(angle), cos(angle))) * p;
	float billow = sin(q.x * 7.0 + sin(q.y * 5.0)) * sin(q.y * 6.0 - q.x * 2.0);
	float detail = sin(q.x * 19.0 + q.y * 11.0) * sin(q.y * 17.0 - q.x * 8.0);
	float density = 1.0 - smoothstep(0.05, 0.93 + billow * 0.09, length(p));
	density *= 0.78 + billow * 0.18 + detail * 0.04;
	float depth = textureLod(depth_texture, SCREEN_UV, 0.0).r;
	#if CURRENT_RENDERER == RENDERER_COMPATIBILITY
		vec4 scene_view = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	#else
		vec4 scene_view = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth, 1.0);
	#endif
	float soft_contact = clamp((-scene_view.z / scene_view.w + VERTEX.z) / 0.65, 0.0, 1.0);
	float light_wrap = 0.83 + 0.18 * UV.y + 0.08 * p.x;
	ALBEDO = tint.rgb * light_wrap;
	ALPHA = density * tint.a * soft_contact;
}
