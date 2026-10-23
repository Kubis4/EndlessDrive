shader_type spatial;
render_mode diffuse_burley, cull_disabled;

void fragment() {
	float ripple = sin(UV.x * 0.85 + UV.y * 0.42 + TIME * 0.65) * 0.5 + 0.5;
	ALBEDO = mix(vec3(0.17, 0.30, 0.30), vec3(0.26, 0.40, 0.40), ripple * 0.20);
	ROUGHNESS = 0.48 + ripple * 0.08;
	SPECULAR = 0.30;
}
