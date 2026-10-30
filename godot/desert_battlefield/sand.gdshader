shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
#include "res://desert_battlefield/soil.gdshaderinc"
uniform sampler2D fine_tile_mask : filter_nearest, repeat_disable;
uniform bool fine_geometry = false;
instance uniform vec4 patch_bounds = vec4(-128.0, -128.0, 128.0, 128.0);
// Test grounds (x0, z0, x1, z1) render their own surfaces; set from terrain.gd.
uniform vec4 test_hole_a = vec4(62.0, -112.0, 120.0, -44.0);
uniform vec4 test_hole_b = vec4(-102.0, -99.0, -54.0, -51.0);
uniform vec4 test_hole_c = vec4(40.0, 68.0, 118.0, 120.0);
varying vec3 world;

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	// The replaced coarse surface never needs displacement texture fetches.
	if (fine_geometry) {
		VERTEX.y -= sand_depth(world.xz);
		NORMAL = sand_normal(world.xz, NORMAL);
	}
}
void fragment() {
	vec2 p = world.xz;
	if ((p.x >= test_hole_a.x && p.y >= test_hole_a.y && p.x < test_hole_a.z && p.y < test_hole_a.w)
		|| (p.x >= test_hole_b.x && p.y >= test_hole_b.y && p.x < test_hole_b.z && p.y < test_hole_b.w)
		|| (p.x >= test_hole_c.x && p.y >= test_hole_c.y && p.x < test_hole_c.z && p.y < test_hole_c.w)) { discard; }
	if (fine_geometry && (p.x < patch_bounds.x || p.y < patch_bounds.y || p.x >= patch_bounds.z || p.y >= patch_bounds.w)) { discard; }
	if (soil_enabled && !fine_geometry && max(abs(p.x), abs(p.y)) < 128.0 && texture(fine_tile_mask, (p + vec2(128.0)) / 256.0).r > 0.5) { discard; }
	float broad = 0.5 + 0.24 * sin(p.x * 0.035 + p.y * 0.023) + 0.15 * sin(p.y * 0.11 - p.x * 0.064);
	float phase = p.x * 10.0 + p.y * 4.0 + 2.2 * sin(p.y * 0.7) + sin(p.x * 0.28 + p.y * 0.21) * 2.0;
	float ripple = sin(phase);
	float visibility = 1.0 - smoothstep(1.0, 4.0, length(vec2(dFdx(phase), dFdy(phase))));
	ALBEDO = mix(vec3(0.64, 0.49, 0.32), vec3(0.70, 0.56, 0.38), broad);
	ALBEDO *= 1.0 + ripple * 0.012 * visibility;
	float rut = fine_geometry ? smoothstep(0.015, 0.20, sand_depth(p)) : 0.0;
	// Freshly turned, slightly moist sand from below the dry crust.
	ALBEDO = mix(ALBEDO, ALBEDO * vec3(0.8, 0.74, 0.68), rut * 0.85);
	ROUGHNESS = 0.91; SPECULAR = 0.22;
	// World-space ripple perturbation also works on fine patches without tangents.
	NORMAL = normalize(NORMAL + vec3(cos(phase) * 0.008 * visibility * (1.0 - rut), 0, 0));
}
