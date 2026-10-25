shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
uniform vec4 powder_tint = vec4(0.76, 0.70, 0.57, 1.0);
uniform float opacity = 1.0;
varying float seed;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
void vertex() {
	seed = float(INSTANCE_ID) * 7.13;
	mat4 facing = mat4(INV_VIEW_MATRIX[0], INV_VIEW_MATRIX[1], INV_VIEW_MATRIX[2], MODEL_MATRIX[3]);
	facing[0].xyz *= length(MODEL_MATRIX[0].xyz);
	facing[1].xyz *= length(MODEL_MATRIX[1].xyz);
	facing[2].xyz *= length(MODEL_MATRIX[2].xyz);
	MODELVIEW_MATRIX = VIEW_MATRIX * facing;
}
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float cloud = noise(p * 2.8 + vec2(seed));
	float rim = 1.0 - smoothstep(0.15, 1.0, length(p) + (cloud - 0.5) * 0.28);
	// Every surface bank owns an immutable tint, including airborne powder.
	ALBEDO = powder_tint.rgb * (0.93 + cloud * 0.14);
	if (!OUTPUT_IS_SRGB) {
		ALBEDO = mix(ALBEDO / 12.92, pow((ALBEDO + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), ALBEDO));
	}
	ALPHA = COLOR.a * rim * rim * opacity;
}
