shader_type canvas_item;
// The picture through the camera, road-film style (road_fx.gd): speed blur and
// colour fringes towards the edges, drops running down the lens, frost creeping
// in from the corners, heat shimmer over the road, blowing sand, sun flare,
// vignette. One full-screen pass of about ten texture reads.
uniform sampler2D screen : hint_screen_texture, filter_linear, repeat_disable;
uniform float speed_blur = 0.0;
uniform float lens_rain = 0.0;
uniform float frost = 0.0;
uniform float heat = 0.0;
uniform float sand = 0.0;
uniform float vignette = 0.3;
uniform vec2 sun_at = vec2(0.5);
uniform float flare = 0.0;
uniform vec3 flare_tint = vec3(1.0, 0.9, 0.7);

float hash(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * 0.1031);
	q += dot(q, q.yzx + 33.33);
	return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

// A layer of drops sliding down the lens: the offset each bends the picture by.
vec2 drops(vec2 uv, float size, float time) {
	vec2 grid = vec2(size, size * 0.5);
	vec2 p = uv * grid;
	float column = floor(p.x);
	p.y -= time * (0.25 + 0.5 * hash(vec2(column, 3.1)));
	vec2 cell = floor(p);
	vec2 f = fract(p) - 0.5;
	float seed = hash(cell + 11.0);
	if (seed > 0.32) { return vec2(0.0); }
	vec2 centre = vec2((hash(cell + 3.0) - 0.5) * 0.6, (hash(cell + 7.0) - 0.5) * 0.6);
	vec2 d = (f - centre) * vec2(1.0, 0.5);
	float r = 0.09 + 0.1 * seed;
	float drop = smoothstep(r, r * 0.45, length(d));
	return -d / r * drop * 0.022;
}

void fragment() {
	vec2 uv = SCREEN_UV;
	vec2 from_centre = uv - 0.5;
	float aspect = SCREEN_PIXEL_SIZE.y / SCREEN_PIXEL_SIZE.x;
	float radius = length(from_centre * vec2(aspect, 1.0)) / length(vec2(aspect, 1.0) * 0.5);
	if (heat > 0.0) {
		// Over the road and the horizon, not in the sky.
		float band = smoothstep(0.36, 0.46, uv.y) * (1.0 - smoothstep(0.5, 0.62, uv.y));
		uv.x += sin(uv.y * 240.0 + TIME * 4.0 + sin(uv.x * 30.0) * 1.5) * 0.0007 * heat * band;
	}
	if (lens_rain > 0.0) {
		vec2 bend = drops(uv, 6.0, TIME) + drops(uv + 0.37, 10.0, TIME * 1.3) * 0.7;
		uv += bend * lens_rain;
	}
	vec3 colour;
	float blur = speed_blur * smoothstep(0.35, 1.0, radius);
	if (blur > 0.002) {
		colour = vec3(0.0);
		// (Colour fringes with it: red further out than blue.)
		for (int i = 0; i < 6; i++) {
			float t = float(i) / 5.0;
			vec2 offset = from_centre * blur * 0.1 * t;
			colour.r += texture(screen, uv - offset * 1.12).r;
			colour.g += texture(screen, uv - offset).g;
			colour.b += texture(screen, uv - offset * 0.88).b;
		}
		colour /= 6.0;
	} else {
		colour = texture(screen, uv).rgb;
	}
	if (sand > 0.0) {
		// Streaks of blowing sand across the picture.
		float streak = noise(vec2(uv.x * 5.0 - TIME * 1.6, uv.y * 60.0)) * noise(vec2(uv.x * 2.0 - TIME * 0.7, uv.y * 8.0));
		colour = mix(colour, vec3(0.72, 0.52, 0.3), sand * (0.16 + 0.42 * streak));
	}
	if (frost > 0.0) {
		float crystals = noise(uv * vec2(aspect, 1.0) * 22.0) * 0.55 + noise(uv * vec2(aspect, 1.0) * 70.0) * 0.3 + noise(uv * vec2(aspect, 1.0) * 6.0) * 0.5;
		float edge = smoothstep(1.02 - frost * 0.42, 1.34 - frost * 0.42, radius + crystals * 0.42);
		colour = mix(colour, vec3(0.82, 0.9, 0.97), edge * 0.35 * frost);
	}
	if (flare > 0.0) {
		vec2 at = (SCREEN_UV - sun_at) * vec2(aspect, 1.0);
		float glow = 0.05 / (length(at) + 0.08) - 0.05;
		float streak = exp(-abs(at.y) * 70.0) * exp(-abs(at.x) * 1.6) * 0.42;
		vec2 ghost_centre = (vec2(0.5) - (sun_at - vec2(0.5)) * 0.6 - SCREEN_UV) * vec2(aspect, 1.0);
		float ghost = smoothstep(0.075, 0.06, length(ghost_centre)) * 0.025 + smoothstep(0.03, 0.02, length(ghost_centre - (sun_at - 0.5) * 0.5)) * 0.04;
		colour += flare_tint * flare * (max(glow, 0.0) * 0.7 + streak + ghost);
	}
	colour *= 1.0 - vignette * smoothstep(0.55, 1.15, radius);
	COLOR = vec4(colour, 1.0);
}
