shader_type spatial;
render_mode diffuse_burley, cull_disabled, depth_draw_never;
varying vec2 route_position;
void vertex() { route_position = VERTEX.xz; }

void fragment() {
	float depth = max(UV.x, 0.0);
	float shore = smoothstep(0.008, 0.22, depth);
	float first = dot(route_position, vec2(2.8, 4.1)) + TIME * 0.9;
	float second = dot(route_position, vec2(-3.4, 1.7)) - TIME * 0.65;
	float ripple = sin(first) * cos(second) * 0.5 + 0.5;
	float glint = smoothstep(0.88, 1.0, sin(first * 3.0 + sin(second)) * 0.5 + 0.5);
	vec3 shallow = vec3(0.29, 0.28, 0.22);
	vec3 deep = mix(vec3(0.12, 0.23, 0.22), vec3(0.16, 0.28, 0.26), ripple);
	ALBEDO = mix(shallow, deep, smoothstep(0.06, 0.85, depth)) + vec3(0.008, 0.014, 0.012) * glint * shore;
	vec3 waves = vec3(cos(first) * 0.012, 0.0, sin(second) * 0.010) * shore;
	NORMAL = normalize(NORMAL + (VIEW_MATRIX * vec4(waves, 0.0)).xyz);
	ROUGHNESS = 0.32 + ripple * 0.12;
	SPECULAR = 0.45;
	ALPHA = smoothstep(0.003, 0.10, depth) * mix(0.24, 0.82, smoothstep(0.08, 1.2, depth));
}
