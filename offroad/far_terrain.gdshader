shader_type spatial;
render_mode cull_back, diffuse_burley, specular_disabled;
// The land beyond the streamed tiles (endless_world.gd): a coarse mesh coloured
// per vertex, so the mountains stand on the horizon. Where the real tiles lie it
// is cut away (near_mask, one texel a tile, over near_rect: x, z, size); weather
// whitens it with the snow cover.
uniform sampler2D near_mask : filter_nearest, repeat_disable;
uniform vec3 near_rect = vec3(0.0, 0.0, 1.0);
uniform float snow_cover = 0.0;
varying vec2 place;

void vertex() {
	place = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xz;
}

void fragment() {
	vec2 q = (place - near_rect.xy) / near_rect.z;
	if (q.x > 0.0 && q.y > 0.0 && q.x < 1.0 && q.y < 1.0 && texture(near_mask, q).r > 0.5) { discard; }
	ALBEDO = mix(COLOR.rgb, vec3(0.82, 0.85, 0.9), snow_cover * 0.8);
	ROUGHNESS = 0.95;
}
