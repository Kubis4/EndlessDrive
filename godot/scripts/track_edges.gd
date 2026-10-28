extends RefCounted
## Deterministic rock clusters; no continuous ribbon or flat wall geometry.
const KINDS := ["weathered_boulder", "split_outcrop", "layered_slab", "talus_cluster", "jagged_crag"]

static func rail_opening(profile: DriveTerrain, x: float, hazards: bool) -> bool:
	if not hazards: return false
	var feature := profile.feature_at(x)
	return feature.get("kind", "") == "rail" and absf(x - float(feature.x)) < 8.0

static func edge(profile: DriveTerrain, x: float, side: float, inset := -5.0) -> Vector3:
	var z := profile.corridor_center(x) + (profile.boundary_width(x) + inset) * side
	return Vector3(x, profile.ground_height_at(x, z), z)

static func site(profile: DriveTerrain, start: float, side: float, hazards: bool) -> Dictionary:
	var x := start + 4.0
	if rail_opening(profile, x, hazards): return {}
	var rng := RandomNumberGenerator.new()
	rng.seed = profile.seed_at(x) * 104729 + floori(start / 8.0) * 7919 + (31 if side < 0 else 83)
	var feature := profile.feature_at(x)
	var near_rail: bool = hazards and feature.get("kind", "") == "rail" and absf(x - float(feature.x)) < 28.0
	if not near_rail: x += rng.randf_range(-0.65, 0.65)
	var a := edge(profile, x - 4.0, side)
	var b := edge(profile, x + 4.0, side)
	var forward := (b - a).normalized()
	var outward := Vector3(-forward.z, 0, forward.x).normalized()
	var up := outward.cross(forward).normalized()
	var width := clampf(a.distance_to(b) * rng.randf_range(0.88, 1.06), 7.2, 12.0)
	if near_rail:
		forward = Vector3.RIGHT
		outward = Vector3.BACK
		up = Vector3.UP
		width = minf(width, absf(x - float(feature.x)) - 3.1)
	var height := rng.randf_range(6.0, 11.0)
	var depth := rng.randf_range(5.0, 8.5)
	var kind: String = KINDS[rng.randi_range(0, KINDS.size() - 1)]
	if kind == "layered_slab": height *= 0.75
	if kind == "jagged_crag": height *= 1.20
	var basis := Basis(forward * side, up, outward * side)
	if not near_rail: basis = basis * Basis(Vector3.UP, rng.randf_range(-0.13, 0.13))
	basis = basis * Basis.from_scale(Vector3(width, height, depth))
	var position := edge(profile, x, side, -5.0 + rng.randf_range(0.0, 1.3)) - Vector3.UP * 0.55
	var blend := profile.blend_at(x)
	var snow := lerpf(1.0 if int(blend.from) == 4 else 0.0, 1.0 if int(blend.to) == 4 else 0.0, float(blend.amount))
	return {"kind": kind, "snowy": rng.randf() < snow, "transform": Transform3D(basis, position), "side": side, "id": "%d/%d" % [roundi(start / 8.0), int(side)]}
