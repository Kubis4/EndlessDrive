shader_type spatial;
render_mode cull_back, diffuse_burley, specular_disabled;
// The land beyond the streamed tiles (endless_world.gd): a coarse mesh coloured
// per vertex, so the mountains stand on the horizon. Where the real tiles lie it
// is cut away; weather whitens it with the snow cover.
uniform vec4 near_rect = vec4(0.0);
uniform float snow_cover = 0.0;
varying vec2 place;

void vertex() {
	place = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xz;
}

void fragment() {
	if (place.x > near_rect.x && place.y > near_rect.y && place.x < near_rect.z && place.y < near_rect.w) { discard; }
	ALBEDO = mix(COLOR.rgb, vec3(0.82, 0.85, 0.9), snow_cover * 0.8);
	ROUGHNESS = 0.95;
}
