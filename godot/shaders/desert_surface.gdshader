shader_type spatial;
render_mode diffuse_burley, specular_disabled;

uniform sampler2D atlas : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
varying vec3 world_position;

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
		mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}

void vertex() {
	world_position = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec2 p = world_position.xz;
	// Dynamic collision bands move vertices without rebuilding a whole mesh.
	// Light the walls of deep depressions from their actual displaced triangles;
	// preserve authored smooth normals on the ordinary shallow road.
	vec3 geometric_normal = normalize(cross(dFdx(VERTEX), dFdy(VERTEX)));
	if (dot(geometric_normal, NORMAL) < 0.0) geometric_normal = -geometric_normal;
	float changed_slope = smoothstep(0.12, 0.30, length(geometric_normal - NORMAL));
	NORMAL = normalize(mix(NORMAL, geometric_normal, changed_slope * 0.35));
	// Road coordinates and continuously blended terrain colour come from the SAME
	// profile as collision/tyres, including both branches. No duplicate road sine.
	float lateral = abs(UV2.x);
	float road = 1.0 - smoothstep(4.0, 6.5, lateral);
	float paved = UV2.y;
	// Understated, continuous ground detail stays stable at overview distance.
	vec3 color = COLOR.rgb;
	float ruts = exp(-pow((lateral - 1.05) * 6.0, 2.0)) + exp(-pow((lateral - 2.7) * 8.0, 2.0)) * 0.22;
	color *= 1.0 - ruts * road * 0.11 * (1.0 - paved);
	float center_line = (1.0 - smoothstep(0.08, 0.15, lateral)) * step(3.0, mod(p.x, 9.0));
	float edge_line = 1.0 - smoothstep(0.06, 0.15, abs(lateral - 3.65));
	color = mix(color, vec3(0.77, 0.72, 0.54), max(center_line, edge_line) * paved * 0.65);
	float broad = noise(p * 0.038) * 0.07 + noise(p * 0.15) * 0.035;
	// Palette / vertex colours are authored in sRGB; lighting expects linear
	// albedo. Without conversion blue-grey snow was rendered almost white.
	ALBEDO = pow(max(color, vec3(0.0)), vec3(2.2)) * (0.98 + broad);
	ROUGHNESS = 1.0;
}
