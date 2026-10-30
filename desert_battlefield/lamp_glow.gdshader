shader_type spatial;
render_mode unshaded, blend_add, depth_draw_never, cull_disabled, shadows_disabled;
// Lit headlight lens over the painted lamp: additive, soft-edged, so it reads
// as light in daylight without covering the model's own lamp texture.
uniform vec3 glow_color : source_color = vec3(1.0, 0.93, 0.78);
uniform float energy = 1.6;
// Round lenses (IZH) or rectangular ones (Jeep).
uniform bool round_lens = false;

void fragment() {
	vec2 q = abs(UV - 0.5) * 2.0;
	float d = round_lens ? length(q) : max(q.x, q.y);
	float glow = 1.0 - smoothstep(0.45, 1.0, d);
	ALBEDO = glow_color * energy * glow * glow;
}
