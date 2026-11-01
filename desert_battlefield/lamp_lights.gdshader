shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
// The model's own lamp surface (headlights and tail lights in one material):
// lit from inside by its texture, front lamps (+X in the rig) by head_energy,
// rear ones by tail_energy (brighter when braking), so the glow sits exactly in
// the lenses instead of on quads laid over them.
uniform sampler2D albedo_texture : source_color, filter_linear_mipmap;
uniform float head_energy = 0.0;
uniform float tail_energy = 0.0;
uniform float reverse_energy = 0.0;
varying float forward;

void vertex() {
	forward = VERTEX.x;
}

void fragment() {
	vec3 lens = texture(albedo_texture, UV).rgb;
	ALBEDO = lens;
	ROUGHNESS = 0.15;
	SPECULAR = 0.6;
	// Only the bright part of each lens glows (reflector, bulb), not the trim.
	float lit = smoothstep(0.12, 0.45, max(lens.r, max(lens.g, lens.b)));
	if (forward > 0.0) {
		EMISSION = mix(lens, vec3(1.0, 0.95, 0.85), 0.6) * lit * head_energy;
	} else {
		// Red lenses glow red; the white reversing lenses in reverse gear.
		float red = smoothstep(0.1, 0.3, lens.r - max(lens.g, lens.b));
		float white = (1.0 - smoothstep(0.05, 0.15, lens.r - min(lens.g, lens.b))) * smoothstep(0.35, 0.6, min(lens.r, min(lens.g, lens.b)));
		EMISSION = vec3(1.0, 0.06, 0.02) * red * lit * tail_energy + vec3(1.0, 0.97, 0.9) * white * reverse_energy;
	}
}
