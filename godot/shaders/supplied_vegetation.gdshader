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
	vec2 gradient_x = dFdx(UV) * (240.0 / 1024.0);
	vec2 gradient_y = dFdy(UV) * (240.0 / 1024.0);
	vec4 texel = textureGrad(plants_atlas, atlas_uv, gradient_x, gradient_y);
	vec3 tint = mix(vec3(1.0), vec3(0.84, 0.91, 0.81), foliage);
	float snow = snow_cover * foliage * smoothstep(0.05, 0.65, upward);
	ALBEDO = mix(texel.rgb * tint, vec3(0.66, 0.73, 0.74), snow * 0.85);
	ROUGHNESS = 0.94;
	ALPHA = texel.a;
	// Mipmapped leaf cards otherwise lose most of their crown at the overview
	// distance. Keep near edges crisp while retaining distant leaf coverage.
	float footprint = max(length(gradient_x), length(gradient_y)) * 1024.0;
	ALPHA_SCISSOR_THRESHOLD = mix(0.45, 0.22, smoothstep(1.0, 3.5, footprint) * foliage);
}
