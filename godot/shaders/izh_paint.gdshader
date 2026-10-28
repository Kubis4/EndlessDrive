shader_type spatial;
render_mode diffuse_burley;
uniform vec4 tint : source_color = vec4(0.73, 0.73, 0.67, 1.0);
void fragment() {
	ALBEDO = mix(COLOR.rgb, COLOR.rgb * tint.rgb, COLOR.a);
	ROUGHNESS = mix(0.32, 0.83, COLOR.a);
	METALLIC = 0.12;
}
