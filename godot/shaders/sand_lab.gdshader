shader_type spatial;
render_mode diffuse_burley;
uniform vec4 sand_color : source_color = vec4(0.68, 0.65, 0.56, 1.0);
varying vec3 p;
void vertex() { p = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	float grain = fract(sin(dot(floor(p.xz * 45.0), vec2(12.9898, 78.233))) * 43758.5453);
	ALBEDO = sand_color.rgb * (0.985 + grain * 0.03);
	ROUGHNESS = 0.98;
	SPECULAR = 0.12;
}
