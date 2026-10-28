extends RefCounted
## Seeded immutable placement shared by scenery clearance and actual obstacles.
const KINDS := ["fallen_tree", "wreck", "rubble", "barrier"]
const Trees = preload("res://scripts/tree_scatter.gd")
const MAX_BYPASS_WATER := 0.52

static func sites(profile, region: int) -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	if region < 0: return result
	# Tyre ruts belong to the journey, not to seeded obstacle placement. Revisiting
	# an excavated shoulder must not reroll or remove an already encountered tree.
	if not profile.deformation.cells.is_empty():
		var pristine = profile.get_script().new()
		pristine.seed_value = profile.seed_value
		pristine.route_choices = profile.route_choices.duplicate()
		profile = pristine
	for slot in 3:
		var rng := RandomNumberGenerator.new()
		var base_x: float = region * 960.0 + [154.0, 198.0, 926.0][slot]
		rng.seed = int(profile.seed_at(base_x)) * 8191 + region * 104729 + slot * 7919
		var jitter := 8.0 if slot == 1 else 14.0
		var x := base_x + rng.randf_range(-jitter, jitter)
		if result.any(func(site: Dictionary) -> bool: return absf(float(site.x) - x) < 100.0): continue
		if not _available(profile, x): continue
		var biome: int = profile.biome_at(x)
		var kind: String = KINDS[rng.randi_range(0, 3)]
		if kind == "fallen_tree" and biome in [0, 1]: kind = "wreck"
		if biome in [2, 3, 4, 5] and slot < 2: kind = "fallen_tree"
		var preferred := -1.0 if rng.randf() < 0.5 else 1.0
		var bypass: Array[Vector3] = []
		var side := preferred
		var best_score := -INF
		for attempt: float in [preferred, -preferred]:
			var candidate := bypass_path(profile, x, attempt)
			if candidate.is_empty(): continue
			var score := challenge_score(profile, candidate) + (0.04 if attempt == preferred else 0.0)
			if score > best_score:
				bypass = candidate
				side = attempt
				best_score = score
		if bypass.is_empty(): continue
		var center: float = profile.center_at(x)
		var frame: Dictionary = profile.road_frame(x, center)
		var forward := Vector3(1, float(frame.grade), float(frame.slope)).normalized()
		var across := Vector3(-float(frame.slope), 0, 1).normalized()
		var up := across.cross(forward).normalized()
		var basis := Basis(forward, up, forward.cross(up).normalized())
		var offset := -side * (0.8 if kind == "fallen_tree" else 1.4)
		var point := Vector3(x, 0, center) + across * offset
		point.y = profile.height_at(point.x, point.z)
		var site := {"id": region * 3 + slot, "kind": kind, "x": x,
			"position": point, "basis": basis, "bypass_side": side, "bypass": bypass,
			"challenge": best_score, "grove": []}
		if kind == "fallen_tree":
			var fitted := fit_tree(profile, site, rng)
			if fitted: site.grove = grove_points(profile, site)
			if not fitted or site.grove.size() < 2:
				site.kind = "wreck"
				site.grove = []
				var wreck_point := Vector3(x, 0, center) - across * side * 1.4
				wreck_point.y = profile.height_at(wreck_point.x, wreck_point.z)
				site.position = wreck_point
		result.append(site)
	return result

static func fit_tree(profile, site: Dictionary, rng: RandomNumberGenerator) -> bool:
	var variant := rng.randi_range(0, 2)
	var yaw := rng.randf_range(-0.28, 0.28)
	var size_value := rng.randf_range(0.92, 1.10)
	var coverage := "across"
	if variant > 0:
		var sign_value := -1.0 if rng.randf() < 0.5 else 1.0
		yaw = sign_value * rng.randf_range(0.38, 0.90 if variant == 1 else 1.15)
		coverage = "angled" if variant == 1 else "partial"
		if variant == 2: size_value = rng.randf_range(0.78, 0.98)
	var extent := (6.8 * absf(cos(yaw)) + 1.8 * absf(sin(yaw))) * size_value
	var offset := 0.8
	if variant == 1: offset = maxf(0.8, extent - 2.8)
	if variant == 2: offset = maxf(1.5, extent - 0.5)
	var road_basis: Basis = site.basis
	var across := Vector3(road_basis.z.x, 0, road_basis.z.z).normalized()
	var point := Vector3(float(site.x), 0, float(profile.center_at(float(site.x)))) - across * float(site.bypass_side) * offset
	if float(site.bypass_side) < 0: yaw += PI
	var axis := road_basis * Basis(Vector3.UP, yaw) * Vector3.BACK
	axis.y = 0
	axis = axis.normalized()
	var a := point - axis * 6.0 * size_value
	var b := point + axis * 5.8 * size_value
	a.y = profile.height_at(a.x, a.z)
	b.y = profile.height_at(b.x, b.z)
	var along := (b - a).normalized()
	if absf(along.y) > 0.32: return false
	var right := Vector3.UP.cross(along).normalized()
	var tree_basis := Basis(right, along.cross(right).normalized(), along)
	var maximum := -INF
	var minimum := INF
	for i in 9:
		var t := i / 8.0
		var local := Vector3(0.25 * t, lerpf(0.42, 0.60, t), lerpf(-6.0, 5.8, t)) * size_value
		var relative := tree_basis * local
		var floor_height: float = profile.height_at(point.x + relative.x, point.z + relative.z)
		var support := floor_height - relative.y + 0.40 * size_value + 0.015
		maximum = maxf(maximum, support)
		minimum = minf(minimum, support)
	if maximum - minimum > 0.85: return false
	point.y = maximum
	site.position = point
	site.tree_basis = tree_basis
	site.tree_scale = size_value
	site.tree_yaw = yaw
	site.tree_coverage = coverage
	return true

static func _available(profile, x: float) -> bool:
	if x < 130: return false
	for dx: float in [-32, 0, 32]:
		var px := x + dx
		if not profile.mountain_at(px).is_empty() or not profile.fork_at(px).is_empty(): return false
		if profile.road_centers(px).size() != 1 or not profile.feature_at(px).is_empty(): return false
		var event: Dictionary = profile.road_event(floori(px / 240.0))
		if not event.is_empty() and absf(float(event.x) - x) < 40: return false
	for chunk in range(floori((x - 45) / 96), floori((x + 45) / 96) + 1):
		var stop := DriveStops.for_chunk(chunk, int(profile.seed_at(chunk * 96.0)))
		if not stop.is_empty() and absf(float(stop.x) - x) < 45: return false
	for region in range(floori((x - 40) / 960), floori((x + 40) / 960) + 1):
		for shelter: Dictionary in profile.shelter_sites(region):
			if absf(float(shelter.x) - x) < 38: return false
	return true

static func bypass_path(profile, x: float, side: float) -> Array[Vector3]:
	var points: Array[Vector3] = []
	for step in 17:
		var t := step / 16.0
		var px := x - 26.0 + t * 52.0
		var lateral := side * 16.0 * pow(sin(t * PI), 1.2)
		var center: float = profile.center_at(px)
		var frame: Dictionary = profile.road_frame(px, center)
		var across := Vector3(-float(frame.slope), 0, 1).normalized()
		var point := Vector3(px, 0, center) + across * lateral
		point.y = profile.height_at(point.x, point.z)
		if profile.water_depth(point.x, point.z) > MAX_BYPASS_WATER: return []
		if absf(point.z - float(profile.corridor_center(point.x))) > float(profile.boundary_width(point.x)) - 6: return []
		for probe: Vector3 in [Vector3(1.2, 0, 0), Vector3(-1.2, 0, 0), across * 1.2, -across * 1.2]:
			var y: float = profile.height_at(point.x + probe.x, point.z + probe.z)
			if absf(y - point.y) > 0.55 or profile.water_depth(point.x + probe.x, point.z + probe.z) > 0.58: return []
		if not points.is_empty():
			var previous := points[-1]
			if absf(point.y - previous.y) > Vector2(point.x - previous.x, point.z - previous.z).length() * 0.35: return []
		points.append(point)
	return points

static func challenge_score(profile, points: Array[Vector3]) -> float:
	var score := 0.0
	for point: Vector3 in points:
		var data: Dictionary = profile.sample(point.x, point.z)
		score = maxf(score, float(data.water) * 2.0 + float(data.soil_softness) * 0.55 + float(data.rut_depth) * 0.35)
	return score

static func grove_points(profile, site: Dictionary) -> Array[Vector3]:
	var points: Array[Vector3] = []
	var cells := {}
	for along: float in [-22, -10, 2, 14, 26]:
		for lateral: float in [18, 28]:
			var x := float(site.x) + along
			var center: float = profile.center_at(x)
			var frame: Dictionary = profile.road_frame(x, center)
			var across := Vector3(-float(frame.slope), 0, 1).normalized()
			var candidate := Vector3(x, 0, center) - across * float(site.bypass_side) * lateral
			var cell := Trees.cell(candidate)
			if cells.has(cell): continue
			var point := Trees.point(int(profile.seed_value), cell)
			if absf(point.x - float(site.x)) > 34: continue
			if profile.biome_at(point.x) not in [2, 3, 4, 5]: continue
			if absf(float(profile.road_distance(point.x, point.z))) < 12: continue
			if absf(point.z - float(profile.corridor_center(point.x))) > float(profile.boundary_width(point.x)) - 7: continue
			if profile.water_depth(point.x, point.z) > (0.60 if profile.biome_at(point.x) == 5 else 0.10): continue
			if profile.is_offroad_trail(point.x, point.z) or reserves(profile, site, point, 5): continue
			point.y = profile.height_at(point.x, point.z)
			var spread := 0.0
			for foot: Vector2 in [Vector2(0.4, 0), Vector2(-0.4, 0), Vector2(0, 0.4), Vector2(0, -0.4)]:
				spread = maxf(spread, absf(float(profile.height_at(point.x + foot.x, point.z + foot.y)) - point.y))
			if spread > 0.22: continue
			points.append(point)
			cells[cell] = true
			if points.size() >= 5: return points
	return points

static func reserves(profile, site: Dictionary, point: Vector3, margin: float) -> bool:
	var delta := point - (site.position as Vector3)
	var local: Vector3 = (site.basis as Basis).inverse() * delta
	if site.kind == "fallen_tree" and site.has("tree_basis"):
		var tree_local: Vector3 = (site.tree_basis as Basis).inverse() * delta
		var scale_value := float(site.tree_scale)
		if absf(tree_local.x) < 2.0 * scale_value + margin and absf(tree_local.z) < 7.0 * scale_value + margin: return true
	elif absf(local.x) < 4.0 + margin and absf(local.z) < 7.0 + margin: return true
	if absf(local.x) > 37.0 + margin: return false
	var p := Vector2(point.x, point.z)
	var points: Array = site.bypass
	for i in points.size() - 1:
		var a := Vector2(points[i].x, points[i].z)
		var b := Vector2(points[i + 1].x, points[i + 1].z)
		var delta_path := b - a
		var closest := a + delta_path * clampf((p - a).dot(delta_path) / maxf(delta_path.length_squared(), 0.001), 0, 1)
		if p.distance_to(closest) < 2.5 + margin: return true
	return false
