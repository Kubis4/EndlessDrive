shader_type spatial;
render_mode cull_disabled;

uniform sampler2D plants_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform float snow_cover : hint_range(0.0, 1.0) = 0.0;
varying flat float atlas_layer;
varying float foliage;
varying float upward;

void vertex() {
	atlas_layer = round(COLOR.r * 255.0);
	foliage = COLOR.g;
	upward = max((MODEL_NORMAL_MATRIX * NORMAL).y, 0.0);
}

void fragment() {
	vec2 cell = vec2(mod(atlas_layer, 4.0), floor(atlas_layer / 4.0));
	vec2 atlas_uv = (cell * 256.0 + 8.0 + fract(UV) * 240.0) / 1024.0;
	vec4 texel = texture(plants_atlas, atlas_uv);
	vec3 tint = mix(vec3(1.0), vec3(0.84, 0.91, 0.81), foliage);
	float snow = snow_cover * foliage * smoothstep(0.05, 0.65, upward);
	ALBEDO = mix(texel.rgb * tint, vec3(0.66, 0.73, 0.74), snow * 0.85);
	ROUGHNESS = 0.94;
	ALPHA = texel.a;
	ALPHA_SCISSOR_THRESHOLD = 0.45;
}
