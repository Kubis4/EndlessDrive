shader_type spatial;
render_mode diffuse_burley;
uniform sampler2D atlas : source_color, filter_linear_mipmap, repeat_enable;
uniform vec4 tint : source_color = vec4(0.82, 0.84, 0.75, 1.0);
uniform vec2 tile = vec2(1.0, 1.0);
uniform float metalness = 0.15;
void fragment() {
	vec2 uv = fract(UV) * 0.49 + tile * 0.5 + vec2(0.005);
	vec3 sample_color = texture(atlas, uv).rgb;
	vec3 color = mix(vec3(0.86), sample_color, 0.48) * tint.rgb;
	ALBEDO = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.72);
	ROUGHNESS = 0.78;
	METALLIC = metalness;
}
