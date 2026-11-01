shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
// Tyres, rims and axles with what the ground leaves on them (vehicle.gd adds it
// up as the car drives): caked mud and dust in clumps that grow from the tread
// inwards, snow packed into the tread, and a wet film. The source textures are
// kept; rubber is never glossy unless wet.
uniform sampler2D albedo_texture : source_color, filter_linear_mipmap;
uniform sampler2D normal_texture : hint_normal, filter_linear_mipmap;
uniform sampler2D roughness_texture : hint_roughness_r, filter_linear_mipmap;
uniform vec4 albedo_color : source_color = vec4(1.0);
uniform bool has_albedo = false;
uniform bool has_normal = false;
uniform bool has_roughness = false;
uniform float metallic = 0.0;
// 0..1 coatings, and the colour of the mud (dirt brown .. clay .. black bog).
uniform float mud = 0.0;
uniform float dust = 0.0;
uniform float snow = 0.0;
uniform float wet = 0.0;
uniform vec3 mud_color : source_color = vec3(0.24, 0.17, 0.11);
uniform vec3 dust_color : source_color = vec3(0.55, 0.47, 0.36);
// 1 on the tyre (tread outermost), 0 on axles and other parts.
uniform float tread = 1.0;
// 0..1 how caked the sidewalls and rims are: only a car stuck and spinning
// in snow or mud packs them; the running tread takes the coats always.
uniform float side = 0.0;
varying vec3 local;
varying vec3 local_normal;

float hash(vec3 p) {
	p = fract(p * 0.3183099 + 0.1);
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float noise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
		mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// Cover 0..1 of a coat: `spot` (0..1) says where it settles first; none at
// amount 0, all of it near 1.
float coat(float spot, float amount) {
	// (Never all of it: some rubber always shows.)
	return smoothstep(-0.06, 0.06, amount * 0.85 - (1.0 - clamp(spot, 0.0, 1.0))) * smoothstep(0.0, 0.03, amount);
}

// Suspension parts (springs, shackles, shocks) bend towards their axle: a vertex
// moves with the wheel on its side by how close it is to the axle along the car
// and how low it hangs, so spring eyes stay on the frame and shock tops on
// their mounts. Offsets are the wheels' travel (m), left = rig +Z.
uniform bool deform = false;
uniform float axle_x = 0.0;
uniform float offset_left = 0.0;
uniform float offset_right = 0.0;

void vertex() {
	if (deform) {
		float along = 1.0 - smoothstep(0.05, 0.8, abs(VERTEX.x - axle_x));
		float low = 1.0 - smoothstep(0.1, 0.5, VERTEX.y);
		VERTEX.y += (VERTEX.z > 0.0 ? offset_left : offset_right) * along * low;
	}
	local = VERTEX;
	local_normal = NORMAL;
}

void fragment() {
	vec4 base = has_albedo ? texture(albedo_texture, UV) * albedo_color : albedo_color;
	vec3 albedo = base.rgb;
	float rough = has_roughness ? texture(roughness_texture, UV).r : 0.8;
	// Rubber and grimy steel: matte.
	rough = max(rough, 0.72);
	// Where grime sits first: the tread face (radial normal, away from the hub
	// axis = local Z after the rig turn), then the sidewall, last the rim centre.
	float radial = length(local.xy);
	float face = tread * smoothstep(0.25, 0.4, radial) + (1.0 - tread) * 0.6;
	float clumps = noise(local * 14.0) * 0.6 + noise(local * 37.0) * 0.4;
	// Mud and dust: patches that spread as the coat thickens.
	// The tread pattern: grooves (from the normal map's slopes)
	// hold what the tyre picks up, while the tops of the blocks on the running
	// face are scrubbed clean on the ground, so the pattern shows through.
	vec3 tread_normal = has_normal ? texture(normal_texture, UV).rgb * 2.0 - 1.0 : vec3(0.0, 0.0, 1.0);
	float edge = smoothstep(0.12, 0.45, length(tread_normal.xy));
	// (Rubber is dark all over, so only the relief tells grooves from blocks.)
	float groove = edge;
	float running = tread * smoothstep(0.33, 0.39, radial);
	float scrubbed = running * (1.0 - groove) * smoothstep(0.35, 0.65, noise(local * 6.0) * 0.4 + 0.6);
	float mud_cover = coat(clumps * 0.6 + face * 0.2 + groove * 0.2, mud) * (1.0 - 0.8 * scrubbed) * (1.0 - 0.95 * smoothstep(0.2, 0.6, snow) * scrubbed);
	float dust_cover = coat(clumps * 0.5 + face * 0.5, dust) * (1.0 - mud_cover) * (1.0 - 0.6 * scrubbed);
	// Snow packs the grooves; rolling sheds it off the blocks.
	// (On the sidewall it clings in the lettering and grooves only.)
	// Packed snow whitens the whole tyre; only the tread blocks rolling on the
	// ground stay black rubber.
	float snow_cover = coat(clumps * 0.25 + face * 0.2 + groove * 0.55 + 0.25, snow) * (1.0 - scrubbed * smoothstep(0.2, 0.5, snow)) * mix(0.35, 1.0, face);
	float sideways = tread * smoothstep(0.45, 0.8, abs(normalize(local_normal).z));
	float side_keep = mix(1.0, side, sideways);
	mud_cover *= side_keep;
	dust_cover *= side_keep;
	snow_cover *= side_keep;
	albedo = mix(albedo, dust_color * (0.85 + 0.3 * clumps), dust_cover * 0.85);
	// Wet mud in the middle of a clump, lighter dried crust at its edge.
	float crust = 1.0 - smoothstep(0.0, 0.5, mud_cover);
	albedo = mix(albedo, mud_color * (0.8 + 0.5 * noise(local * 60.0)) * (1.0 + crust * 0.8), smoothstep(0.0, 0.25, mud_cover));
	albedo = mix(albedo, vec3(0.9, 0.92, 0.96), snow_cover);
	rough = mix(rough, 0.95, dust_cover);
	rough = mix(rough, 0.55, mud_cover);
	rough = mix(rough, 0.8, snow_cover);
	// A wet film darkens and lifts the sheen (never a mirror).
	albedo *= 1.0 - 0.3 * wet * (1.0 - snow_cover);
	rough = mix(rough, 0.35, wet * (1.0 - snow_cover) * 0.8);
	ALBEDO = albedo;
	ROUGHNESS = rough;
	METALLIC = metallic * (1.0 - max(mud_cover, max(dust_cover, snow_cover)));
	SPECULAR = 0.35;
	if (has_normal) {
		// The tread relief stays legible under a thin coat.
		NORMAL_MAP = mix(texture(normal_texture, UV).rgb, vec3(0.5, 0.5, 1.0), max(mud_cover * 0.35, snow_cover * 0.45));
	}
}
