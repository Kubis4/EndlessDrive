// The ground on a phone: highlands_ground.gdshader with its look kept and its cost moved.
// The noise that ragged the grounds' edges and varied their tone comes from the vertices,
// there is no parallax, normal maps only close by, and rain, snow and frost cost nothing
// while they are not there.
#include "res://desert_battlefield/soil.gdshaderinc"
uniform sampler2D fine_tile_mask : filter_nearest, repeat_disable;
uniform bool fine_geometry = false;
instance uniform vec4 patch_bounds = vec4(-128.0, -128.0, 128.0, 128.0);
uniform sampler2D ground_map : filter_nearest, repeat_disable;
uniform float map_half = 180.0;
uniform float map_step = 0.5;
uniform float water_level = 0.0;
uniform float wetness = 0.0;
uniform float snow_cover = 0.0;
uniform float snow_depth = 0.0;
uniform float frost = 0.0;
uniform sampler2DArray ground_albedo : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2DArray ground_normal : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D asphalt_albedo : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D ground_heights : filter_nearest, repeat_disable;
uniform float heights_half = 360.0;
uniform float heights_step = 0.75;
instance uniform float grid_step = 0.0;
uniform bool wrap_maps = false;
varying vec3 world;
varying vec3 world_normal;
varying vec3 base_normal;
varying vec2 warp;
varying vec3 tone;
varying float mixing;

ivec2 map_texel(ivec2 texel, ivec2 size) {
	if (wrap_maps) { return texel - size * ivec2(floor(vec2(texel) / vec2(size))); }
	return clamp(texel, ivec2(0), size - 1);
}

float grid_height(ivec2 node) {
	return texelFetch(ground_heights, map_texel(node, textureSize(ground_heights, 0)), 0).r;
}

int kind_at(ivec2 texel) {
	return int(texelFetch(ground_map, map_texel(texel, textureSize(ground_map, 0)), 0).r * 255.0 + 0.5);
}

const int SAND = 0;
const int PACKED = 1;
const int ROAD = 2;
const int WET_ROAD = 11;
const int GRAVEL = 3;
const int MUD = 5;
const int WATER = 6;
const int ROCK = 7;
const int SNOW = 8;
const int ICE = 9;
const int GRASS = 10;
const int DIRT = 12;
const int FOREST = 13;

float hash12(vec2 p) {
	vec3 q = fract(vec3(p.xyx) * 0.1031);
	q += dot(q, q.yzx + 33.33);
	return fract((q.x + q.y) * q.z);
}
float vnoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	if (!fine_geometry && grid_step > 0.0) {
		ivec2 node = ivec2(round((world.xz + vec2(heights_half)) / heights_step));
		int s = int(round(grid_step / heights_step));
		float h = grid_height(node);
		VERTEX.y += h;
		world.y += h;
		NORMAL = normalize(vec3(grid_height(node - ivec2(s, 0)) - grid_height(node + ivec2(s, 0)), 2.0 * grid_step,
			grid_height(node - ivec2(0, s)) - grid_height(node + ivec2(0, s))));
	}
	base_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	// (Ruts only on the finest ground: further off they would not show.)
	if (fine_geometry || (soil_enabled && grid_step > 0.0 && grid_step < 1.0)) {
		VERTEX.y -= sand_depth(world.xz);
		NORMAL = sand_normal(world.xz, NORMAL);
	}
	if (snow_depth > 0.005 && snow_cover > 0.0) {
		vec2 g = (world.xz + vec2(map_half)) / map_step;
		ivec2 base = ivec2(floor(g));
		vec2 f = fract(g);
		float open_ground = 0.0;
		for (int i = 0; i < 4; i++) {
			ivec2 o = ivec2(i & 1, i >> 1);
			int k = kind_at(base + o) & 15;
			float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
			open_ground += w * (k == WATER ? 0.0 : (k == DIRT || k == GRAVEL || k == MUD ? 0.55 : 1.0));
		}
		VERTEX.y += snow_depth * snow_cover * open_ground * smoothstep(water_level, water_level + 0.4, world.y);
	}
	world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	vec2 w = world.xz;
	warp = (vec2(vnoise(w * 0.45), vnoise(w * 0.45 + 17.3)) - 0.5) * 1.2;
	mixing = smoothstep(0.42, 0.58, vnoise(w * 0.045) * 0.7 + vnoise(w * 0.093 + 7.1) * 0.3);
	float macro = vnoise(w * 0.013) * 0.65 + vnoise(w * 0.031 + 7.1) * 0.35;
	tone = mix(0.86, 1.12, macro) * mix(vec3(1.0), vec3(1.06, 1.0, 0.86), smoothstep(0.55, 0.8, vnoise(w * 0.006 + 3.0)) * 0.6);
}

int layer_of(int value) {
	int kind = value & 15;
	int look = value >> 4;
	if (kind == DIRT && look >= 2) { return 11; }
	if (kind == GRASS) { return 0; }
	if (kind == FOREST) { return 1; }
	if (kind == MUD) { return look == 1 ? 6 : 7; }
	if (kind == WATER) { return 3; }
	if (kind == ROAD || kind == WET_ROAD) { return 20; }
	if (kind == SAND || kind == PACKED) { return 10; }
	if (kind == GRAVEL) { return 4; }
	if (kind == ROCK) { return 5; }
	if (kind == SNOW) { return 8; }
	if (kind == ICE) { return 9; }
	return 2;
}

float tile(int layer) {
	return layer == 8 ? 4.0 : layer == 9 ? 3.5 : layer == 4 ? 2.2 : (layer == 5 ? 9.0 :(layer == 6 ? 4.2 : (layer == 7 ? 5.0 : (layer == 2 ? 3.0 : 3.6))));
}

// One layer at one of its two scales (both only along the seams between them), its normal
// (x, y, strength) only when `close` says it shows.
void sample_layer(int layer, vec2 p, float blend, float close, out vec4 albedo, out vec2 normal) {
	float scale = 1.0 / tile(layer);
	float index = float(layer);
	vec2 a = p * scale;
	vec2 b = mat2(vec2(0.8, 0.6), vec2(-0.6, 0.8)) * p * scale / 2.7 + 0.37;
	normal = vec2(0.0);
	if (blend < 0.01) {
		albedo = texture(ground_albedo, vec3(a, index));
		if (close > 0.0) { normal = (texture(ground_normal, vec3(a, index)).xy * 2.0 - 1.0) * close; }
		return;
	}
	if (blend > 0.99) {
		albedo = texture(ground_albedo, vec3(b, index));
		if (close > 0.0) { normal = mat2(vec2(0.8, -0.6), vec2(0.6, 0.8)) * (texture(ground_normal, vec3(b, index)).xy * 2.0 - 1.0) * close; }
		return;
	}
	albedo = mix(texture(ground_albedo, vec3(a, index)), texture(ground_albedo, vec3(b, index)), blend);
}

void fragment() {
	vec2 p = world.xz;
#ifdef FINE_PATCHES
	if (fine_geometry && (p.x < patch_bounds.x || p.y < patch_bounds.y || p.x >= patch_bounds.z || p.y >= patch_bounds.w)) { discard; }
	vec2 field_p = p - soil_center;
	if (soil_enabled && !fine_geometry && max(abs(field_p.x), abs(field_p.y)) < 127.0) {
		vec2 mask_uv = (field_p + vec2(128.0)) / 256.0;
		float inset = 0.7 / 256.0;
		if (texture(fine_tile_mask, mask_uv + vec2(inset, inset)).r > 0.5 && texture(fine_tile_mask, mask_uv - vec2(inset, inset)).r > 0.5
			&& texture(fine_tile_mask, mask_uv + vec2(inset, -inset)).r > 0.5 && texture(fine_tile_mask, mask_uv + vec2(-inset, inset)).r > 0.5) { discard; }
	}
#endif
	vec3 geometric = normalize(world_normal);
	float eye_distance = length(CAMERA_POSITION_WORLD - world);
	float close = 1.0 - smoothstep(22.0, 32.0, eye_distance);
	float rut_depth = eye_distance < 70.0 ? sand_depth(p) : 0.0;
	float rut = smoothstep(0.005, 0.10, rut_depth);
	float snow_rut = smoothstep(0.006, 0.06, rut_depth);
	vec2 g = (p + warp + vec2(map_half)) / map_step;
	ivec2 base = ivec2(floor(g));
	vec2 f = smoothstep(0.0, 1.0, fract(g));
	int kinds[4];
	float weights[4];
	int layers[4];
	bool wet[4];
	// Further off a texel is a pixel or two: its nearest ground will do.
	int count = eye_distance < 75.0 ? 4 : 1;
	if (count == 1) {
		kinds[0] = kind_at(ivec2(round(g)));
		weights[0] = 1.0;
	} else {
		kinds[0] = kind_at(base);
		kinds[1] = kind_at(base + ivec2(1, 0));
		kinds[2] = kind_at(base + ivec2(0, 1));
		kinds[3] = kind_at(base + ivec2(1, 1));
		weights[0] = (1.0 - f.x) * (1.0 - f.y);
		weights[1] = f.x * (1.0 - f.y);
		weights[2] = (1.0 - f.x) * f.y;
		weights[3] = f.x * f.y;
	}
	for (int i = 0; i < count; i++) {
		layers[i] = layer_of(kinds[i]);
		wet[i] = (kinds[i] & 15) == WATER;
	}
	for (int i = 1; i < count; i++) {
		for (int j = 0; j < i; j++) {
			if (layers[i] == layers[j] && wet[i] == wet[j] && (kinds[i] >> 4) == (kinds[j] >> 4) && weights[j] > 0.0) { weights[j] += weights[i]; weights[i] = 0.0; break; }
		}
	}
	vec4 albedo = vec4(0.0);
	vec3 normal_world = vec3(0.0);
	float soil_w = 0.0;
	float mud_w = 0.0;
	float churn_w = 0.0;
	float soak_w = 0.0;
	float settle_w = 0.0;
	float snow_w = 0.0;
	float ice_w = 0.0;
	float hard_w = 0.0;
	float turf_w = 0.0;
	float steep = smoothstep(0.45, 0.7, 1.0 - geometric.y);
	for (int i = 0; i < count; i++) {
		if (weights[i] <= 0.04) { continue; }
		int layer = layers[i];
		float w = weights[i];
		vec4 a;
		vec3 n = geometric;
		if (layer == 20) {
			a = vec4(texture(asphalt_albedo, p / 5.0).rgb, 0.85);
			if (eye_distance < 60.0) { a.rgb = mix(a.rgb, texture(asphalt_albedo, (p.yx + vec2(17.0, 9.0)) / 13.5).rgb, 0.32); }
		} else if (layer == 5 && steep > 0.0) {
			// Rock on a steep face: projected along its main direction, not stretched.
			vec3 across = abs(geometric);
			a = texture(ground_albedo, vec3((across.x > across.z ? world.zy : world.xy) / tile(5), 5.0));
		} else {
			vec2 t;
			sample_layer(layer, p, mixing, close, a, t);
			if (layer == 6) {
				float dry = smoothstep(0.15, 1.1, world.y - water_level + (vnoise(p * 0.5) - 0.5) * 0.5);
				if (dry < 0.99) {
					vec4 mud;
					vec2 mud_normal;
					sample_layer(3, p, mixing, 0.0, mud, mud_normal);
					a = mix(mud, a, dry);
				}
			}
			if (close > 0.0) { n = normalize(geometric + vec3(t.x, 0.0, t.y) * 1.25); }
		}
		int look = kinds[i] >> 4;
		if (layer == 11) {
			vec3 crop = look == 2 ? vec3(0.13, 0.25, 0.06) : look == 3 ? vec3(0.74, 0.62, 0.26) : look == 4 ? vec3(0.8, 0.6, 0.2) : look == 5 ? vec3(0.78, 0.69, 0.1) : look == 6 ? vec3(0.2, 0.28, 0.08) : a.rgb;
			float luma = dot(a.rgb, vec3(0.33)) * 3.2;
			a.rgb = mix(a.rgb, crop * (0.7 + 0.3 * luma), mix(0.55, 0.9, smoothstep(10.0, 60.0, eye_distance)));
		} else if (layer == 0 && look == 1) {
			a.rgb = mix(a.rgb, vec3(0.58, 0.48, 0.23) * (0.55 + 1.5 * dot(a.rgb, vec3(0.33))), 0.8);
		}
		if (wet[i]) {
			a.rgb *= 0.7;
			a.a = 0.15;
		}
		albedo += a * w;
		normal_world += n * w;
		bool soft = layer != 4 && layer != 5 && layer != 8 && layer != 9 && layer != 20;
		soil_w += soft ? w : 0.0;
		mud_w += (layer == 3 || layer == 6 || layer == 7) ? w : 0.0;
		churn_w += w * (layer == 2 || layer == 11 ? 0.8 : (layer == 1 ? 0.35 : (layer == 0 ? 0.15 : 0.0)));
		soak_w += w * (layer == 20 ? 0.45 : (layer >= 8 && layer != 11 ? 0.0 : (layer == 2 || layer == 11 ? 1.0 : (layer == 0 ? 0.6 : 0.75))));
		settle_w += w * (layer <= 1 ? 0.0 : (layer == 5 ? 0.3 : 0.18));
		snow_w += layer == 8 ? w : 0.0;
		ice_w += layer == 9 ? w : 0.0;
		hard_w += (layer == 4 || layer == 5 || layer == 2 || layer == 20) ? w : 0.0;
		turf_w += layer <= 1 ? w : 0.0;
	}
	float churn = max(rut * 0.8 * soil_w, wetness * churn_w);
	if (churn > 0.01) {
		vec4 churned;
		vec2 churned_normal;
		sample_layer(7, p, mixing, 0.0, churned, churned_normal);
		churned.a = max(churned.a, 0.6);
		albedo = mix(albedo, churned, churn);
	}
	albedo.rgb *= mix(vec3(1.0), tone, 1.0 - snow_w * 0.85);
	if (rut_depth > 0.0) {
		float pressed = mix(rut, snow_rut, snow_w);
		albedo.rgb = mix(albedo.rgb, albedo.rgb * mix(vec3(0.62, 0.56, 0.5), vec3(0.88, 0.91, 0.96), snow_w), pressed * 0.85);
		albedo.a = mix(albedo.a, albedo.a * 0.8, rut);
		normal_world = mix(normal_world, geometric, rut * 0.6 * (1.0 - snow_cover));
	}
	float puddle = 0.0;
	if (wetness > 0.001) {
		float damp = wetness * soak_w;
		albedo.rgb *= mix(1.0, 0.62, damp);
		albedo.a = mix(albedo.a, max(albedo.a * 0.7, 0.48), damp);
		float level = smoothstep(0.985, 0.996, base_normal.y);
		float hollow = smoothstep(0.6, 0.7, vnoise(p * 0.09 + 11.0)) * level;
		float near_water = 1.0 - smoothstep(0.4, 1.8, world.y - water_level);
		float holds = max(near_water, mud_w * 0.7);
		float rut_pool = smoothstep(0.07, 0.15, rut_depth) * level;
		puddle = smoothstep(0.35, 0.6, wetness * clamp(max(hollow, rut_pool) * holds, 0.0, 1.0)) * (1.0 - snow_w - ice_w);
		albedo.rgb = mix(albedo.rgb, albedo.rgb * 0.45 + vec3(0.02, 0.025, 0.03), puddle);
		albedo.a = mix(albedo.a, 0.06, puddle);
		normal_world = mix(normal_world, vec3(0.0, 1.0, 0.0), puddle);
	}
	float cover = 0.0;
	if (snow_cover > 0.001) {
		float settle = vnoise(p * 0.3) * 0.45 + vnoise(p * 1.6 + 4.0) * 0.35 + settle_w;
		cover = smoothstep(settle - 0.05, settle + 0.05, snow_cover * 1.25) * smoothstep(0.55, 0.85, base_normal.y) * (1.0 - snow_w);
		cover *= 1.0 - snow_rut * 0.55 * (1.0 - smoothstep(0.01, 0.10, snow_depth));
		if (cover > 0.001) {
			vec4 fresh;
			vec2 fresh_normal;
			sample_layer(8, p, mixing, close, fresh, fresh_normal);
			fresh.rgb *= mix(vec3(1.0), vec3(0.86, 0.90, 0.96), snow_rut);
			fresh_normal *= 1.0 + snow_rut * 2.0;
			albedo = mix(albedo, fresh, cover);
			normal_world = mix(normal_world, normalize(geometric + vec3(fresh_normal.x, 0.0, fresh_normal.y) * 0.6), cover);
		}
	}
	float glaze = 0.0;
	if (frost > 0.001) {
		float hoar = frost * (1.0 - cover) * mix(0.25, 0.45, turf_w);
		albedo.rgb = mix(albedo.rgb, vec3(0.82, 0.86, 0.9), hoar * (0.6 + 0.4 * vnoise(p * 0.7)));
		glaze = frost * clamp(wetness * 1.6, 0.0, 1.0) * (1.0 - cover) * hard_w;
		albedo.a = mix(albedo.a, 0.2, glaze * 0.7);
	}
	float polish = clamp(max(max(puddle, glaze * 0.6), ice_w), 0.0, 1.0);
	ALBEDO = albedo.rgb;
	ROUGHNESS = max(albedo.a, mix(0.5, 0.04, polish));
	SPECULAR = mix(0.2, 0.4, polish);
	NORMAL = normalize((VIEW_MATRIX * vec4(normal_world, 0.0)).xyz);
}
