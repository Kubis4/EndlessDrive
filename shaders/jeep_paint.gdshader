shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_disabled;

uniform sampler2D albedo_map : source_color, filter_linear_mipmap_anisotropic;
uniform sampler2D orm_map : filter_linear_mipmap;
uniform sampler2D normal_map : hint_normal, filter_linear_mipmap;
uniform vec4 tint : source_color = vec4(0.72549, 0.72941, 0.66667, 1.0);
uniform float body_damage : hint_range(0.0, 1.0) = 0.0;
varying float dent_mask;

void vertex() {
	vec3 d = (VERTEX - vec3(1.7, 0.5, 0.8)) * vec3(1.3, 1.5, 1.5);
	dent_mask = exp(-dot(d, d));
	VERTEX -= vec3(0.18, 0.12, 0.14) * dent_mask * body_damage;
}

void fragment() {
	vec3 source = texture(albedo_map, UV).rgb;
	// The source atlas is shared by paint, rubber, glass, lamps and rust. Only
	// the warm salmon paint is recoloured; windows/trim/tyres keep their detail.
	float warm = source.r - max(source.g, source.b);
	float paint = smoothstep(0.025, 0.10, warm) * smoothstep(0.52, 0.75, source.b / max(source.g, 0.001));
	float detail = clamp(dot(source, vec3(0.2126, 0.7152, 0.0722)) / 0.16, 0.0, 1.6);
	ALBEDO = mix(source, tint.rgb * detail * 0.60, paint);
	ALBEDO *= 1.0 - dent_mask * body_damage * 0.22;
	vec3 orm = texture(orm_map, UV).rgb;
	AO = orm.r;
	ROUGHNESS = clamp(orm.g, 0.65, 1.0);
	METALLIC = orm.b * 0.45;
	NORMAL_MAP = texture(normal_map, UV).rgb;
	NORMAL_MAP_DEPTH = 0.65;
}
