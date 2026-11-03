shader_type sky;
// The sky of the weather (weather.gd): a gradient, the sun and a layer of clouds on a plane
// overhead, drawn from one noise texture at two sizes.
//
// What changes as the weather changes comes in as global uniforms: setting those does not
// make the engine redraw the sky's reflection cubemap, as setting a material's uniform, using
// TIME or reading the lights would, every frame. The cubemap is brought up to date now and
// then, when radiance_tick changes, and takes only the sky's colours, without any clouds.
global uniform vec4 sky_zenith;
global uniform vec4 sky_horizon;
global uniform vec4 sky_cloud_lit;
global uniform vec4 sky_cloud_dark;
// Cloud cover (0..1), softness of their edges, how much sun gets through, lightning.
global uniform vec4 sky_cloud;
// How far the wind has carried the large clouds (xy) and the fine detail (zw).
global uniform vec4 sky_drift;
uniform sampler2D cloud_noise : filter_linear_mipmap, repeat_enable;
uniform vec3 sun_direction = vec3(0.0, 1.0, 0.0);
// 0: the lowest quality's plainer clouds, from one sample.
uniform float cloud_detail = 1.0;
uniform float radiance_tick = 0.0;

void sky() {
	vec3 dir = EYEDIR;
	float up = max(dir.y, 0.0);
	float towards_horizon = 1.0 - up;
	vec3 colour = mix(sky_zenith.rgb, sky_horizon.rgb, towards_horizon * towards_horizon * towards_horizon);
	float cover = sky_cloud.x;
	float sun_share = sky_cloud.z;
	if (AT_CUBEMAP_PASS) {
		// What is reflected: the colours, and a mean of the clouds; radiance_tick only says when.
		COLOR = mix(colour, mix(sky_cloud_dark.rgb, sky_cloud_lit.rgb, 0.5), cover * 0.75 * smoothstep(0.0, 0.1, up)) + vec3(radiance_tick * 0.0);
	} else {
		float towards_sun = max(dot(dir, sun_direction), 0.0);
		float near_sun = towards_sun * towards_sun;
		near_sun *= near_sun;
		near_sun *= near_sun;
		// A wide glow and a tight one around the disc.
		colour += sky_cloud_lit.rgb * sun_share * (near_sun * 0.22 + near_sun * near_sun * near_sun * near_sun * 0.8);
		float disc = smoothstep(0.9994, 0.9997, towards_sun) * sun_share;
		float cloud = 0.0;
		vec3 cloud_colour = vec3(0.0);
		if (dir.y > 0.002) {
			vec2 plane = dir.xz / (dir.y + 0.14);
			float density = texture(cloud_noise, plane * 0.045 + sky_drift.xy).r;
			if (cloud_detail > 0.5) density = density * 0.7 + texture(cloud_noise, plane * 0.19 + sky_drift.zw).r * 0.3;
			// Cover 0 leaves none of the noise's range above the edge, cover 1 all of it.
			float edge = mix(0.74, 0.22, cover);
			float soft = sky_cloud.y;
			cloud = smoothstep(edge - soft, edge + soft, density) * smoothstep(0.004, 0.11, dir.y);
			// Thin at the rims and in the sun's light, thick and dark at the heart.
			float thick = smoothstep(edge, edge + 0.3, density);
			cloud_colour = mix(sky_cloud_lit.rgb, sky_cloud_dark.rgb, thick);
			cloud_colour += sky_cloud_lit.rgb * sun_share * near_sun * (1.0 - thick) * 0.5;
		}
		colour = mix(colour + vec3(disc * 2.5), cloud_colour, cloud);
		// Lightning lights the clouds from within.
		COLOR = colour + sky_cloud_lit.rgb * sky_cloud.w * (0.4 + cloud * 2.4);
	}
}
