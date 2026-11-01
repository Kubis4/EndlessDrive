shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_disabled, diffuse_burley, specular_schlick_ggx;
// Snow lying on props (trees, bushes, rocks, buildings, cars): drawn as a
// next pass over the prop's own material while it snows (offroad/weather.gd).
// It settles on upward faces first, patchily, and on foliage a little less;
// alpha-scissored leaves keep their outline.
uniform sampler2D albedo_texture : source_color, filter_linear_mipmap;
uniform bool use_texture = false;
uniform float alpha_scissor = 0.0;
// Snow cover 0..1 and how much a surface holds (foliage less than stone).
uniform float amount = 0.0;
uniform float hold = 1.0;
varying vec3 world_normal;
varying vec3 world_position;

void vertex() {
	// Bias toward the camera on both sides of a leaf. Scale the bias with
	// distance so depth precision does not make the two passes fight far away.
	vec3 position_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vec3 to_camera = CAMERA_POSITION_WORLD - position_world;
	float bias = clamp(length(to_camera) * 0.0005, 0.012, 0.15);
	VERTEX += normalize((inverse(MODEL_MATRIX) * vec4(normalize(to_camera), 0.0)).xyz) * bias;
	world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	world_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void fragment() {
	if (use_texture && alpha_scissor > 0.0 && texture(albedo_texture, UV).a < alpha_scissor) { discard; }
	vec3 n = FRONT_FACING ? world_normal : -world_normal;
	float up = smoothstep(0.1, 0.75, n.y);
	// Clumps of about 15 cm: snow catches unevenly.
	vec2 cell = floor(world_position.xz * 6.0 + world_position.y * 3.0);
	float clump = hash(cell) * 0.3;
	float s = smoothstep(0.0, 0.35, amount * 1.25 * hold - (1.0 - up) * 1.6 - clump);
	if (s < 0.02) { discard; }
	ALBEDO = vec3(0.92, 0.94, 0.97);
	ROUGHNESS = 0.85;
	SPECULAR = 0.3;
	ALPHA = s;
}
