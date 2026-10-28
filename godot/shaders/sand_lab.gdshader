shader_type spatial;
render_mode diffuse_burley;
uniform vec4 sand_color : source_color = vec4(0.72, 0.62, 0.46, 1.0);
varying vec3 p;
void vertex() { p = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	float grain = fract(sin(dot(floor(p.xz * 45.0), vec2(12.9898, 78.233))) * 43758.5453);
	float ripple = sin(p.x * 1.7 + p.z * 3.8 + sin(p.x * 0.17) * 1.5);
	float broad = sin(p.x * 0.12 + p.z * 0.07);
	ALBEDO = sand_color.rgb * (0.97 + grain * 0.018 + ripple * 0.018 + broad * 0.035);
	ROUGHNESS = 0.98;
	SPECULAR = 0.12;
}

