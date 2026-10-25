shader_type spatial;
render_mode diffuse_burley, cull_disabled;
uniform sampler2DArray surface_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform sampler2D micro_height : filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 tint : source_color = vec4(0.47, 0.48, 0.42, 1.0);
varying vec3 position_world;
varying vec3 normal_world;
void vertex() {
	position_world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	normal_world = normalize(MODEL_NORMAL_MATRIX * NORMAL);
}
void fragment() {
	vec3 axis = abs(normal_world);
	vec2 p = axis.y > axis.x && axis.y > axis.z ? position_world.xz : (axis.x > axis.z ? position_world.zy : position_world.xy);
	vec2 uv = mix(vec2(0.004), vec2(0.996), abs(fract(p * 0.0625) * 2.0 - 1.0));
	vec3 stone = texture(surface_atlas, vec3(uv, 1.0)).rgb;
	stone = OUTPUT_IS_SRGB ? stone : pow(max(stone, vec3(0.001)), vec3(0.454545));
	float height = texture(micro_height, p * 0.125).g;
	float detail_fade = 1.0 - smoothstep(0.025, 0.16, length(fwidth(position_world)));
	vec3 color = tint.rgb * mix(vec3(1.0), clamp(stone / vec3(0.472, 0.418, 0.359), vec3(0.65), vec3(1.35)), 0.55 * detail_fade);
	color = mix(vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), color, 0.75);
	ALBEDO = pow(max(color, vec3(0.001)), vec3(2.2));
	ROUGHNESS = 0.82 + height * 0.16;
	SPECULAR = 0.17;
}
