class_name DriveTerrain
extends RefCounted
## One deterministic source for road geometry, rendering, hazards and tyre physics.
## Visible junction arms remain connected; choices affect the distant world.

const Junctions = preload("res://scripts/route_junctions.gd")
const Mountains = preload("res://scripts/mountain_route.gd")
const SECTION := 240.0
const REGION := 960.0
const VARIED_SEED_MIN := 1000001
const BIOMES := ["Prašná kotlina", "Medený kaňon", "Vidiek", "Živý les", "Horský priesmyk", "Mokrade"]
const SURFACES := ["Piesok", "Štrk", "Asfalt", "Hlina", "Sneh", "Štrková hrádza"]
const GROUND := [Color("b7ad97"), Color("a38b73"), Color("7b8970"), Color("64775c"), Color("929b90"), Color("6f7b65")]
const ROAD := [Color("9c927f"), Color("afa18b"), Color("777c79"), Color("9d8a73"), Color("a19e92"), Color("969b95")]
const GRIP := [0.88, 0.92, 1.0, 0.94, 0.84, 0.99]
const DRAG := [0.55, 0.45, 0.0, 0.75, 0.65, 0.12]
enum SurfaceKind { SAND, GRAVEL, DIRT, MUD, GRASS, ROCK, SNOW, ASPHALT, CONCRETE, CLAY, SALT, LEAVES, ICE, PEAT }
const MATERIAL_NAMES := ["Piesok", "Štrk", "Utlačená hlina", "Blato", "Tráva", "Kamenistý podklad", "Sneh", "Asfalt", "Betón", "Červený íl", "Soľná krusta", "Lesná hrabanka", "Zľadovatený sneh", "Rašelinisko"]
const GROUND_MATERIALS := [SurfaceKind.SAND, SurfaceKind.ROCK, SurfaceKind.GRASS, SurfaceKind.LEAVES, SurfaceKind.SNOW, SurfaceKind.MUD]
const ROAD_MATERIALS := [SurfaceKind.SAND, SurfaceKind.GRAVEL, SurfaceKind.ASPHALT, SurfaceKind.DIRT, SurfaceKind.GRAVEL, SurfaceKind.GRAVEL]
var seed_value := 24601
var route_choices: Dictionary = {}
var _centers: Dictionary = {}
var _junction_paths: Dictionary = {}
var _junction_styles: Dictionary = {}
var _junction_rows: Dictionary = {}
var _junction_centers: Dictionary = {}
var _heights: Dictionary = {}
var _seeds: Dictionary = {}
var _pools: Dictionary = {}
var _blends: Dictionary = {}
var _section_shapes: Dictionary = {}
var _route_offsets: Dictionary = {}
var deformation := DriveDeformation.new()
var soil_moisture := 0.0
var _road_geometry: Dictionary = {}
var _offroad_tracks: Dictionary = {}
var _events: Dictionary = {}
var _shelter_sites: Dictionary = {}
var _mountain_paths: Dictionary = {}
var _mountain_rows: Dictionary = {}


func clear_caches() -> void:
	_centers.clear()
	_junction_paths.clear()
	_junction_styles.clear()
	_junction_rows.clear()
	_junction_centers.clear()
	_heights.clear()
	_seeds.clear()
	_pools.clear()
	_blends.clear()
	_section_shapes.clear()
	_route_offsets.clear()
	_road_geometry.clear()
	_offroad_tracks.clear()
	_events.clear()
	_shelter_sites.clear()
	_mountain_paths.clear()
	_mountain_rows.clear()


func shelter_sites(region: int) -> Array[Dictionary]:
	if _shelter_sites.has(region): return _shelter_sites[region]
	var sites: Array[Dictionary] = []
	if region >= 0:
		for slot in 1:
			var x: float = region * REGION + (320.0 if region % 2 == 0 else 820.0)
			if not mountain_at(x).is_empty(): continue
			var side := -1.0 if posmod(seed_value + region + slot, 2) == 0 else 1.0
			var z := center_at(x) + side * 14.0
			sites.append({"x": x, "offset": side * 14.0, "z": z, "level": base_height(x) - 0.1, "id": region * 4 + slot})
	if _shelter_sites.size() > 128: _shelter_sites.clear()
	_shelter_sites[region] = sites
	return sites


func expedition_difficulty(x: float) -> float:
	# Equipment matters over a long expedition; the opening kilometres stay fair.
	return smoothstep(1500.0, 100000.0, maxf(x, 0.0))


func mountain_at(x: float) -> Dictionary:
	var region := floori(x / REGION)
	if region < 3 or posmod(region - 3, 5) != 0: return {}
	var start := region * REGION + 100.0
	return {"region": region, "start": start, "end": start + 740.0} if x >= start and x <= start + 740.0 else {}


func mountain_path(region: int) -> PackedVector3Array:
	if _mountain_paths.has(region): return _mountain_paths[region]
	var start := region * REGION + 100.0
	var path := Mountains.local_path()
	for i in path.size():
		path[i].x += start
		path[i].z *= -1.0 if posmod(seed_value + region, 2) == 0 else 1.0
		path[i].y *= 0.90 + posmod(seed_value + region * 7, 4) * 0.07
		path[i].z += _base_center(path[i].x) + route_offset(path[i].x)
		path[i].y += base_height(path[i].x)
	if _mountain_paths.size() > 16: _mountain_paths.clear()
	_mountain_paths[region] = path
	return path


func mountain_frame(x: float, z: float) -> Dictionary:
	var stage := mountain_at(x)
	if stage.is_empty(): return {}
	var row := floori(x)
	if not _mountain_rows.has(row):
		var path := mountain_path(int(stage.region))
		var segments: Array = []
		# Perpendicular shoulder probes can project behind the stage entrance
		# even when their X is inside it. Include the adjoining road, so the
		# nearest-point solver does not clamp that shoulder to an endpoint.
		if x < float(stage.start) + 20:
			var before := float(stage.start) - 20.0
			segments.append([Vector3(before, base_height(before), _base_center(before) + route_offset(before)), path[0]])
		if x > float(stage.end) - 20:
			var after := float(stage.end) + 20.0
			segments.append([path[-1], Vector3(after, base_height(after), _base_center(after) + route_offset(after))])
		for i in path.size() - 1:
			if maxf(path[i].x, path[i + 1].x) < row - 16 or minf(path[i].x, path[i + 1].x) > row + 17: continue
			segments.append([path[i], path[i + 1]])
		if _mountain_rows.size() > 1024: _mountain_rows.clear()
		_mountain_rows[row] = segments
	return Mountains.closest(_mountain_rows[row], Vector2(x, z))


func junction_style(index: int) -> Dictionary:
	if _junction_styles.has(index): return _junction_styles[index]
	var value := posmod(seed_value * 17 + index * 7919, 7)
	var pairs := [[7, 1], [2, 7], [1, 2], [7, 2], [2, 1], [1, 7], [2, 2]]
	var roll := seed_value
	if index > 0:
		roll = seed_value * 104729 + index * 7919 + index * index * 31
		roll = roll ^ (roll >> 11)
	if _junction_styles.size() > 512: _junction_styles.clear()
	var result := {"upper_material": pairs[value][0], "lower_material": pairs[value][1],
		"hill_side": -1 if value % 2 == 0 else 1,
		"ford_side": 1 if value % 2 == 0 else -1,
		"shape": float(value % 3) * 0.18,
		"layout": ["y", "x", "switchback"][posmod(roll, 3)]}
	_junction_styles[index] = result
	return result


func road_event(section: int) -> Dictionary:
	if _events.has(section): return _events[section]
	var result := {}
	if section > 0:
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value * 31337 + section * 104729
		var x := section * SECTION + rng.randf_range(95.0, 160.0)
		if mountain_at(x).is_empty() and fork_at(x).is_empty() and road_centers(x).size() == 1 and feature_at(x).is_empty():
			var protected := false
			for chunk in range(floori((x - 24) / 96), floori((x + 24) / 96) + 1):
				var stop := DriveStops.for_chunk(chunk, seed_at(chunk * 96.0))
				if not stop.is_empty() and absf(float(stop.x) - x) < 32: protected = true
			for site: Dictionary in shelter_sites(floori(x / REGION)):
				if absf(float(site.x) - x) < 22: protected = true
			if not protected:
				var kinds := ["mud", "rock_garden", "jump", "ditch", "potholes", "washout"]
				result = {"x": x, "z": center_at(x) + rng.randf_range(-1.5, 1.5),
					"kind": kinds[rng.randi_range(0, kinds.size() - 1)], "section": section,
					"strength": lerpf(0.22, 1.0, expedition_difficulty(x)), "length": rng.randf_range(32.0, 58.0), "seed": rng.seed}
	if _events.size() > 1024: _events.clear()
	_events[section] = result
	return result


func event_influence(event: Dictionary, x: float, z: float) -> float:
	if event.is_empty(): return 0.0
	var mud := str(event.kind) == "mud"
	var half_length := float(event.get("length", 12.0)) if mud else 12.0
	var lateral := road_distance(x, z) if mud else z - float(event.z)
	var radius := Vector2((x - float(event.x)) / half_length, lateral / (4.7 if mud else 4.4)).length()
	return 1.0 - smoothstep(0.1, 1.0, radius)


func center_at(x: float) -> float:
	if _centers.has(x): return float(_centers[x])
	var mountain := mountain_at(x)
	if not mountain.is_empty():
		var centers := Mountains.intersections(mountain_path(int(mountain.region)), x)
		if not centers.is_empty():
			_centers[x] = float(centers[0])
			return float(centers[0])
	var center := _base_center(x) + route_offset(x)
	var fork := fork_at(x)
	if not fork.is_empty():
		center += float(route_choices.get(str(fork.id), -1)) * float(fork.spread)
	else:
		var exit_id := floori((x - 600.0) / REGION)
		var end := 600.0 + exit_id * REGION
		if junction_enabled(exit_id) and x < end + 112.0:
			center += float(route_choices.get(str(exit_id), -1)) * 42.0 * (1.0 - smoothstep(end, end + 112.0, x))
	if _centers.size() > 4096: _centers.clear()
	_centers[x] = center
	return center


func _base_center(x: float) -> float:
	var phase := float(posmod(seed_value, 1000)) * 0.01
	var base := (sin(x * 0.012 + phase) - sin(phase)) * 10.0 + sin(x * 0.003) * 18.0
	var t := fposmod(maxf(x, 0.0), SECTION) / SECTION
	var kind := posmod(int(floor(maxf(x, 0.0) / SECTION)), 6)
	# Zero value and derivative at every section boundary, including streamed seams.
	var bend := sin(t * PI) * sin(t * PI) * sin(t * TAU)
	var amplitude := 18.0 if kind in [1, 4] else 7.0
	if seed_value >= VARIED_SEED_MIN:
		var shape := section_shape(floori(maxf(x, 0) / SECTION))
		amplitude = float(shape.bend)
		bend = sin(t * PI) * sin(t * PI) * (sin(t * TAU) + sin(t * 2.0 * TAU) * float(shape.detail))
	return base + bend * amplitude * smoothstep(100.0, 240.0, x)


func section_shape(index: int) -> Dictionary:
	if not _section_shapes.has(index):
		if _section_shapes.size() > 1024: _section_shapes.clear()
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value * 7919 + index * 104729
		_section_shapes[index] = {"bend": rng.randf_range(7, 20), "hill": rng.randf_range(0.5, 3.8), "detail": rng.randf_range(-0.10, 0.10)}
	return _section_shapes[index]


func route_offset(x: float) -> float:
	if _route_offsets.has(x): return float(_route_offsets[x])
	var offset := 0.0
	var count := maxi(0, int(floor((x - 600.0) / REGION)) + 1)
	for index in count:
		if not junction_enabled(index): continue
		var end := 600.0 + index * REGION
		offset += float(route_choices.get(str(index), -1)) * 138.0 * smoothstep(end + 240.0, end + 600.0, x)
	if _route_offsets.size() > 4096: _route_offsets.clear()
	_route_offsets[x] = offset
	return offset


func corridor_center(x: float) -> float:
	var mountain := mountain_at(x)
	if not mountain.is_empty():
		# A hairpin introduces several road crossings at the same X. Their
		# extrema jump as loops appear: never warp the mesh rows to that midpoint.
		var envelope := smoothstep(float(mountain.start), float(mountain.start) + 70, x) * (1.0 - smoothstep(float(mountain.end) - 70, float(mountain.end), x))
		var side := -1.0 if posmod(seed_value + int(mountain.region), 2) == 0 else 1.0
		return _base_center(x) + route_offset(x) + 24.0 * side * envelope
	if not fork_at(x).is_empty(): return _base_center(x) + route_offset(x)
	var exit_id := floori((x - 600.0) / REGION)
	var end := 600.0 + exit_id * REGION
	if junction_enabled(exit_id) and x < end + 112.0:
		var side := float(route_choices.get(str(exit_id), -1))
		return center_at(x) - side * 42.0 * (1.0 - smoothstep(end, end + 112.0, x))
	return center_at(x)


static func route_seed(seed: int, choices: Dictionary, x: float) -> int:
	var result := seed
	var keys := choices.keys()
	keys.sort_custom(func(a: Variant, b: Variant) -> bool: return int(a) < int(b))
	for key: Variant in keys:
		if x >= 840.0 + int(key) * REGION:
			result = int(posmod(result * 31 + int(key) * 7919 + (17 if int(choices[key]) < 0 else 58), 1000000007))
	return result


func seed_at(x: float) -> int:
	var key := int(floor(x))
	if not _seeds.has(key):
		if _seeds.size() > 4096: _seeds.clear()
		_seeds[key] = route_seed(seed_value, route_choices, x)
	return int(_seeds[key])


func feature_at(x: float) -> Dictionary:
	if not mountain_at(x).is_empty(): return {}
	var region := maxi(0, int(floor(x / REGION)))
	var center := region * REGION + 744.0
	if absf(x - center) > 42.0: return {}
	return {"kind": ["bridge", "rail", "tunnel"][posmod(seed_at(center) + region, 3)], "x": center, "id": region}


func junction_enabled(index: int) -> bool:
	return index >= 0 and (index == 0 or route_choices.has(str(index)) or posmod(index + seed_value, 3) == 0)


func fork_at(x: float) -> Dictionary:
	var index := maxi(0, int(floor((x - 360.0) / REGION)))
	var start := 360.0 + index * REGION
	if not junction_enabled(index) or not mountain_at(x).is_empty(): return {}
	if x < start or x >= start + SECTION:
		return {}
	var t := (x - start) / SECTION
	var style := junction_style(index)
	var spread := 42.0 * smoothstep(0.0, 1.0, t)
	# Different arcs share end positions and tangents with the saved exits.
	spread += float(style.shape) * 8.0 * pow(sin(t * PI), 2.0) * sin(t * TAU)
	return {"id": index, "start": start, "end": start + SECTION,
		"spread": spread}


func junction_ahead(x: float) -> Dictionary:
	var index := maxi(0, int(ceil((x - 360.0) / REGION)))
	var active := fork_at(x)
	if not active.is_empty():
		index = int(active.id)
	while not junction_enabled(index) or not mountain_at(360.0 + index * REGION).is_empty(): index += 1
	var start := 360.0 + index * REGION
	var style := junction_style(index)
	return {"distance": maxf(0.0, start - x), "active": not active.is_empty(),
		"upper": MATERIAL_NAMES[int(style.upper_material)], "lower": MATERIAL_NAMES[int(style.lower_material)]}


func junction_paths(index: int) -> Array[PackedVector2Array]:
	if _junction_paths.has(index): return _junction_paths[index]
	var paths: Array[PackedVector2Array] = []
	var start := 360.0 + index * REGION
	for local: PackedVector2Array in Junctions.local_paths(str(junction_style(index).layout)):
		var path := PackedVector2Array()
		for p: Vector2 in local:
			var x := start + p.x
			# The selected branch only affects terrain after this network.
			var offset := route_offset(minf(x, start + SECTION - 0.001))
			path.append(Vector2(x, _base_center(x) + offset + p.y))
		paths.append(path)
	if _junction_paths.size() > 32: _junction_paths.clear()
	_junction_paths[index] = paths
	return paths


func junction_segments(x: float) -> Array[Vector4]:
	var row := floori(x)
	if _junction_rows.has(row): return _junction_rows[row]
	var result: Array[Vector4] = []
	var fork := fork_at(x)
	if not fork.is_empty() and str(junction_style(int(fork.id)).layout) != "y":
		for path: PackedVector2Array in junction_paths(int(fork.id)):
			for i in path.size() - 1:
				var a := path[i]
				var b := path[i + 1]
				if maxf(a.x, b.x) < row - 10.0 or minf(a.x, b.x) > row + 11.0: continue
				result.append(Vector4(a.x, a.y, b.x, b.y))
	if _junction_rows.size() > 1024: _junction_rows.clear()
	_junction_rows[row] = result
	return result


func road_centers(x: float) -> PackedFloat32Array:
	if _junction_centers.has(x): return _junction_centers[x]
	var center := center_at(x)
	var fork := fork_at(x)
	var result := PackedFloat32Array()
	var mountain := mountain_at(x)
	if not mountain.is_empty():
		result = Mountains.intersections(mountain_path(int(mountain.region)), x)
		if not result.is_empty(): return result
	if not fork.is_empty():
		if str(junction_style(int(fork.id)).layout) != "y":
			result = Junctions.intersections(junction_paths(int(fork.id)), x)
		if result.is_empty():
			var spread := float(fork.spread)
			center -= float(route_choices.get(str(fork.id), -1)) * spread
			result = PackedFloat32Array([center - spread, center + spread])
	else:
		var exit_id := floori((x - 600.0) / REGION)
		var end := 600.0 + exit_id * REGION
		if junction_enabled(exit_id) and x < end + 112.0:
			var side := float(route_choices.get(str(exit_id), -1))
			var other := center - side * 84.0 * (1.0 - smoothstep(end, end + 112.0, x))
			result = PackedFloat32Array([minf(center, other), maxf(center, other)])
		else: result = PackedFloat32Array([center])
	if _junction_centers.size() > 4096: _junction_centers.clear()
	_junction_centers[x] = result
	return result


func road_distance(x: float, z: float) -> float:
	var mountain := mountain_frame(x, z)
	if not mountain.is_empty(): return float(mountain.distance)
	var segments := junction_segments(x)
	if not segments.is_empty(): return float(Junctions.closest(segments, Vector2(x, z)).distance)
	var geometry := road_geometry_at(x)
	var nearest := INF
	for lane in geometry.centers.size():
		var distance := (z - float(geometry.centers[lane])) / float(geometry.scales[lane])
		if absf(distance) < absf(nearest): nearest = distance
	return nearest


func road_geometry_at(x: float) -> Dictionary:
	if _road_geometry.has(x): return _road_geometry[x]
	var centers := road_centers(x)
	var before := road_centers(x - 0.5)
	var after := road_centers(x + 0.5)
	var slopes := PackedFloat32Array()
	var scales := PackedFloat32Array()
	for lane in centers.size():
		# At the split/exit use the same physical branch, never the other arm.
		var slope := float(after[mini(lane, after.size() - 1)]) - float(before[mini(lane, before.size() - 1)])
		if before.size() != centers.size() or after.size() != centers.size():
			slope = center_at(x + 0.5) - center_at(x - 0.5)
		slopes.append(slope)
		scales.append(sqrt(1.0 + slope * slope))
	var result := {"centers": centers, "slopes": slopes, "scales": scales, "grade": base_height(x + 0.5) - base_height(x - 0.5)}
	if _road_geometry.size() > 4096: _road_geometry.clear()
	_road_geometry[x] = result
	return result


func road_frame(x: float, z: float) -> Dictionary:
	var mountain := mountain_frame(x, z)
	if not mountain.is_empty():
		var tangent: Vector2 = mountain.tangent
		return {"center": mountain.point.y, "slope": tangent.y / maxf(absf(tangent.x), 0.15) * signf(tangent.x), "lateral": mountain.distance, "grade": 0.0}
	var segments := junction_segments(x)
	if not segments.is_empty():
		var nearest := Junctions.closest(segments, Vector2(x, z))
		var tangent: Vector2 = nearest.tangent
		return {"center": float(nearest.point.y), "slope": tangent.y / maxf(absf(tangent.x), 0.15) * signf(tangent.x), "lateral": float(nearest.distance), "grade": base_height(x + 0.5) - base_height(x - 0.5)}
	var geometry := road_geometry_at(x)
	var lane := 0
	var nearest := INF
	for i in geometry.centers.size():
		var distance := (z - float(geometry.centers[i])) / float(geometry.scales[i])
		if absf(distance) < absf(nearest):
			nearest = distance
			lane = i
	return {"center": float(geometry.centers[lane]), "slope": float(geometry.slopes[lane]), "lateral": nearest, "grade": float(geometry.grade)}


func boundary_width(x: float) -> float:
	# Follow both forks, leaving wide but bounded driveable shoulders.
	var mountain := mountain_at(x)
	if not mountain.is_empty():
		return 36.0 + 60.0 * smoothstep(float(mountain.start), float(mountain.start) + 70, x) * (1.0 - smoothstep(float(mountain.end) - 70, float(mountain.end), x))
	var fork := fork_at(x)
	if not fork.is_empty(): return 36.0 + maxf(float(fork.spread), 48.0 * smoothstep(float(fork.start), float(fork.start) + 64.0, x) * (1.0 - smoothstep(float(fork.end) - 48.0, float(fork.end), x)))
	var lanes := road_centers(x)
	return 36.0 + (absf(lanes[-1] - lanes[0]) * 0.5 if lanes.size() > 1 else 0.0)


func blend_at(x: float) -> Dictionary:
	if _blends.has(x): return _blends[x]
	var region := int(floor((maxf(0.0, x) + 180.0) / REGION))
	var amount := smoothstep(region * REGION - 180.0, region * REGION + 180.0, x)
	var result := {"from": _region_biome(maxi(0, region - 1)), "to": _region_biome(region), "amount": amount}
	if _blends.size() > 4096: _blends.clear()
	_blends[x] = result
	return result


func _region_biome(region: int) -> int:
	if route_choices.is_empty(): return posmod(region + starting_biome(seed_value), 6)
	return starting_biome(seed_value) if region == 0 else posmod(seed_at(region * REGION) + region * 7, 6)


static func starting_biome(seed: int) -> int:
	# Older game versions generated seeds <= 1,000,000 and always began in
	# desert. Reserve a fresh range so Continue does not reshuffle those worlds.
	return 0 if seed < VARIED_SEED_MIN else posmod(seed - VARIED_SEED_MIN, BIOMES.size())


static func new_journey_seed(previous: int = -1) -> int:
	var value := randi_range(VARIED_SEED_MIN, 2000000)
	# Alpine snow is a later expedition challenge, after winter equipment can
	# be found. Preserve explicit/saved seeds, filter only newly rolled rides.
	while starting_biome(value) == 4 or (previous >= 0 and starting_biome(value) == starting_biome(previous)):
		value = randi_range(VARIED_SEED_MIN, 2000000)
	return value


func biome_at(x: float) -> int:
	var blend := blend_at(x)
	return int(blend.to) if float(blend.amount) >= 0.5 else int(blend.from)


func base_height(x: float) -> float:
	if _heights.has(x): return float(_heights[x])
	var t := fposmod(maxf(x, 0.0), SECTION) / SECTION
	var kind := posmod(int(floor(maxf(x, 0.0) / SECTION)), 6)
	var hills := sin(t * PI) * sin(t * PI) * sin(t * TAU)
	var amplitude := [0.6, 2.5, 3.8, 1.0, 4.2, 2.8][kind] as float
	if seed_value >= VARIED_SEED_MIN: amplitude = float(section_shape(floori(maxf(x, 0) / SECTION)).hill) * smoothstep(100.0, 240.0, x)
	var elevation := 0.0
	for key: Variant in route_choices:
		var end := 600.0 + int(key) * REGION
		elevation += (24.0 if int(route_choices[key]) < 0 else -8.0) * smoothstep(end + 240.0, end + 720.0, x)
	var result := sin(x * 0.017) * 0.45 + sin(x * 0.0045) * 1.8 + hills * amplitude + elevation
	if _heights.size() > 4096: _heights.clear()
	_heights[x] = result
	return result


func dry_height(x: float, z: float) -> float:
	var lateral := absf(road_distance(x, z))
	var shoulder := smoothstep(6.0, 25.0, lateral)
	var dunes := sin(x * 0.037 + z * 0.019) * 1.7 + cos(z * 0.045 - x * 0.017) * 0.9
	var height := base_height(x) + shoulder * dunes
	# Broad wind-sculpted dunes with a rounded windward face and a steeper lee.
	# Fade them into the established road corridor, and blend out by biome.
	var blend := blend_at(x)
	var sand := lerpf(1.0 if int(blend.from) == 0 else 0.0, 1.0 if int(blend.to) == 0 else 0.0, float(blend.amount))
	var alpine := lerpf(1.0 if int(blend.from) == 4 else 0.0, 1.0 if int(blend.to) == 4 else 0.0, float(blend.amount))
	var canyon := lerpf(1.0 if int(blend.from) == 1 else 0.0, 1.0 if int(blend.to) == 1 else 0.0, float(blend.amount))
	# Eroded sandstone benches replace the old flat floor between block walls.
	# The graded road remains continuous; shelves and dry washes live outside it.
	var terrace := smoothstep(8.0, 25.0, lateral) * (7.0 + sin(x * 0.019) * 2.0)
	terrace += smoothstep(22.0, 36.0, lateral) * (12.0 + sin(x * 0.011 + z * 0.025) * 3.0)
	var wash := (1.0 - smoothstep(2.0, 5.5, absf(lateral - 17.0 - sin(x * 0.025) * 3.0))) * 3.0
	height += canyon * (terrace - wash)
	# A road bench winds through an asymmetric rocky valley. Long ridges live
	# outside the corridor; the driveable pass does not copy their steep slope.
	var ridge := 8.0 + sin(x * 0.013 + z * 0.021) * 3.0 + cos(x * 0.007 - z * 0.036) * 2.0
	height += alpine * smoothstep(8.0, 34.0, lateral) * ridge
	var phase := x * 0.075 + z * 0.105 + sin(x * 0.022 - z * 0.031) * 1.2
	var crest := sin(phase) * 0.72 + sin(phase * 2.0 + 0.8) * 0.22
	height += sand * smoothstep(5.5, 18.0, lateral) * (crest * 3.5 + sin(x * 0.031 - z * 0.052) * 1.6)
	if lateral < 10.0:
		var frame := road_frame(x, z)
		var slope := float(frame.slope)
		var station := clampf((z - float(frame.center)) * slope / (1.0 + slope * slope), -10, 10)
		var road_bench := 1.0 - smoothstep(5.0, 10.0, lateral)
		height += float(frame.grade) * station * road_bench
		height -= minf(lateral, 5.0) * 0.018 * road_bench
	var fork := fork_at(x)
	if not fork.is_empty() and str(junction_style(int(fork.id)).layout) == "y":
		var t := (x - float(fork.start)) / SECTION
		var style := junction_style(int(fork.id))
		var upper := smoothstep(-4.0, 10.0, (z - _base_center(x) - route_offset(x)) * float(style.hill_side))
		height += pow(sin(t * PI), 4.0) * upper * 3.5
	var event := road_event(floori(x / SECTION))
	var influence := event_influence(event, x, z)
	if influence > 0:
		var severity := float(event.strength)
		match str(event.kind):
			"jump": height += influence * lerpf(0.18, 1.15, severity)
			"ditch": height -= influence * lerpf(0.16, 0.85, severity)
			"washout": height -= influence * lerpf(0.20, 1.10, severity) * smoothstep(-2.0, 2.0, z - float(event.z))
			"rock_garden": height += influence * (sin(x * 0.85 + z * 0.65) * 0.5 + 0.5) * lerpf(0.12, 0.42, severity)
			"potholes":
				for i in 3:
					var hole := Vector2((x - float(event.x) - (i - 1) * 4.5) / 3.3, (z - float(event.z) - (-1.0 if i % 2 == 0 else 1.0)) / 2.0).length()
					height -= (1.0 - smoothstep(0.0, 1.0, hole)) * lerpf(0.16, 0.85, severity)
	# Broad paired wheel channels have real geometry, with tapered entrances and
	# exits. Keep the tutorial, forks and access yards clear; no vertical rut lips.
	var mountain := mountain_at(x)
	if not mountain.is_empty():
		var stage_t := (x - float(mountain.start)) / 740.0
		height += 66.0 * pow(sin(stage_t * PI), 2.0)
		var frame := mountain_frame(x, z)
		if not frame.is_empty():
			height = lerpf(height, float(frame.height) - minf(absf(float(frame.distance)), 5.0) * 0.018, 1.0 - smoothstep(5.0, 9.5, absf(float(frame.distance))))
	height -= authored_rut_depth(x, z)
	height -= offroad_rut_depth(x, z)
	var feature := feature_at(x)
	if not feature.is_empty() and feature.kind == "bridge":
		var river := 1.0 - smoothstep(12.0, 22.0, absf(x - float(feature.x)))
		var deck := 1.0 - smoothstep(5.2, 7.0, lateral)
		height -= river * 6.0 * (1.0 - deck)
	elif not feature.is_empty() and feature.kind == "rail":
		var approach := 1.0 - smoothstep(3.0, 12.0, absf(x - float(feature.x)))
		var crossing := 1.0 - smoothstep(5.0, 7.5, lateral)
		height = lerpf(height, base_height(float(feature.x)) + lerpf(-0.12, 0.06, crossing), approach)
	for site: Dictionary in shelter_sites(floori(x / REGION)):
		var dx := absf(x - float(site.x))
		if dx > 10: continue
		var side := signf(float(site.offset))
		var road_side := (z - center_at(x)) * side
		var bay := (1.0 - smoothstep(4.8, 9.0, dx)) * (1.0 - smoothstep(5.2, 8.0, absf(z - float(site.z))))
		var entrance := (1.0 - smoothstep(4.5, 8.0, dx)) * smoothstep(5.5, 10.0, road_side) * (1.0 - smoothstep(17.0, 21.0, road_side))
		height = lerpf(height, float(site.level), maxf(bay, entrance))
	return height


func authored_rut_depth(x: float, z: float) -> float:
	var lateral := road_distance(x, z)
	var section := floori(x / SECTION)
	var local_x := x - section * SECTION
	if x < 130.0 or not fork_at(x).is_empty() or not feature_at(x).is_empty() or road_centers(x).size() > 1: return 0.0
	var envelope := smoothstep(12.0, 30.0, local_x) * (1.0 - smoothstep(218.0, 238.0, local_x))
	if envelope <= 0.0: return 0.0
	var blend := blend_at(x)
	var a := int(blend.from)
	var b := int(blend.to)
	var t := float(blend.amount)
	var loose := lerpf(0.0 if a in [2, 5] else 1.0, 0.0 if b in [2, 5] else 1.0, t)
	if not mountain_at(x).is_empty(): loose = 1.0
	if loose < 0.05: return 0.0
	var drift := sin(x * 0.075 + section) * 0.35
	var channel := 1.0 - smoothstep(0.30, 0.95, absf(absf(lateral - drift) - 1.15))
	return channel * envelope * loose * (0.08 + 0.02 * posmod(section + seed_value, 3))


func offroad_tracks_at(x: float) -> PackedVector3Array:
	if _offroad_tracks.has(x): return _offroad_tracks[x]
	var tracks := PackedVector3Array()
	if x >= 150 and mountain_at(x).is_empty() and fork_at(x).is_empty() and road_centers(x).size() == 1 and feature_at(x).is_empty():
		var section := floori(x / SECTION)
		var local_x := x - section * SECTION
		var envelope := smoothstep(48, 82, local_x) * (1.0 - smoothstep(196, 232, local_x))
		# Service yards stay level and usable; these tracks are optional side
		# trails, not pits cut across the main route or a building entrance.
		if envelope > 0:
			for site: Dictionary in shelter_sites(floori(x / REGION)):
				if absf(x - float(site.x)) < 20: envelope = 0
			for chunk in range(floori((x - 26) / 96), floori((x + 26) / 96) + 1):
				var stop := DriveStops.for_chunk(chunk, seed_at(chunk * 96.0))
				if not stop.is_empty() and absf(x - float(stop.x)) < 26: envelope = 0
		if envelope > 0:
			var side := -1.0 if posmod(section + seed_value, 2) == 0 else 1.0
			var center := side * (18.0 + sin(x * 0.026 + section * 1.3) * 2.5)
			var depth := (0.45 + posmod(section + seed_value, 3) * 0.10) * envelope
			if biome_at(x) == 4: depth *= 0.65
			tracks.append(Vector3(center - 1.15, depth, 1.55))
			tracks.append(Vector3(center + 1.15, depth, 1.55))
	if _offroad_tracks.size() > 4096: _offroad_tracks.clear()
	_offroad_tracks[x] = tracks
	return tracks


func offroad_rut_depth(x: float, z: float) -> float:
	var tracks := offroad_tracks_at(x)
	if tracks.is_empty(): return 0.0
	var lateral := road_distance(x, z)
	var depth := 0.0
	for track: Vector3 in tracks:
		depth = maxf(depth, track.y * (1.0 - smoothstep(0.50, track.z, absf(lateral - track.x))))
	return depth


func is_offroad_trail(x: float, z: float) -> bool:
	var lateral := road_distance(x, z)
	for track: Vector3 in offroad_tracks_at(x):
		if absf(lateral - track.x) < 2.4: return true
	return false


func pools_near(x: float) -> Array[Dictionary]:
	if _pools.has(x): return _pools[x]
	var result: Array[Dictionary] = []
	if mountain_at(x).is_empty(): result = _find_pools(x)
	if _pools.size() > 4096: _pools.clear()
	_pools[x] = result
	return result


func _find_pools(x: float) -> Array[Dictionary]:
	var pools: Array[Dictionary] = []
	var feature := feature_at(x)
	if not feature.is_empty() and feature.kind == "bridge" and absf(x - float(feature.x)) < 24.0:
		pools.append({"x": feature.x, "z": center_at(float(feature.x)), "rx": 22.0, "rz": 120.0, "level": base_height(float(feature.x)) - 3.0, "depth": 3.0, "river": true})
	if absf(x - 207.0) < 24.0:
		pools.append(_pool(207.0, center_at(207.0) + 1.0, 12.0, 4.2))
	var section := int(floor(x / SECTION))
	for index in range(maxi(0, section - 1), section + 2):
		var wetland := biome_at(index * SECTION + 120.0) == 5
		if wetland and absf(x - (index * SECTION + 120.0)) < 72.0:
			for side: float in [-1.0, 1.0]:
				var px := index * SECTION + 120.0 + side * 9.0
				var backwater := _pool(px, center_at(px) + side * 23.0, 42.0, 10.0)
				backwater.depth = 0.48
				backwater.deep = false
				pools.append(backwater)
		if index % 6 == 3 or wetland:
			var px := index * SECTION + 120.0
			if absf(x - px) < 32.0:
				pools.append(_pool(px, road_centers(px)[-1], 19.0, 5.5))
	var fork := fork_at(x)
	if not fork.is_empty() and str(junction_style(int(fork.id)).layout) == "y":
		var px := float(fork.start) + SECTION * 0.5
		if absf(x - px) < 32.0:
			var lane := 0 if int(junction_style(int(fork.id)).ford_side) < 0 else 1
			pools.append(_pool(px, road_centers(px)[lane], 18.0, 5.0))
	return pools


func _pool(x: float, z: float, rx: float, rz: float) -> Dictionary:
	return {"x": x, "z": z, "rx": rx, "rz": rz, "level": base_height(x) - 0.05, "depth": (1.12 + 0.30 * expedition_difficulty(x)) if x > 1500 and posmod(floori(x / SECTION) + seed_value, 3) == 0 else 0.62, "deep": x > 1500 and posmod(floori(x / SECTION) + seed_value, 3) == 0}


func water_level(pool: Dictionary, x: float, _z: float) -> float:
	# Rivers have a horizontal water table. Shallow road fords flow down the
	# longitudinal grade instead of cutting a metre-high basin into a hillside.
	return float(pool.level) if pool.get("river", false) else base_height(x) - 0.05


func height_at(x: float, z: float) -> float:
	var height := dry_height(x, z)
	for pool: Dictionary in pools_near(x):
		if pool.get("river", false): continue
		var radius := Vector2((x - float(pool.x)) / float(pool.rx), (z - float(pool.z)) / float(pool.rz)).length()
		var bowl := 1.0 - smoothstep(0.15, 1.0, radius)
		var depth := float(pool.depth)
		if bool(pool.get("deep", false)): depth *= lerpf(0.42, 1.0, smoothstep(-2.5, 1.5, z - float(pool.z)))
		# The bed meets a real low bank above the waterline. A clipped ellipse
		# alone left water hovering above low roadside terrain at its edge.
		var bed := water_level(pool, x, z) + 0.10 - (depth + 0.10) * bowl
		height = lerpf(height, bed, 1.0 - smoothstep(1.1, 1.65, radius))
	return height - deformation.depth_at(x, z)


func water_depth(x: float, z: float) -> float:
	var depth := 0.0
	for pool: Dictionary in pools_near(x):
		if Vector2((x - float(pool.x)) / float(pool.rx), (z - float(pool.z)) / float(pool.rz)).length() < 1.0:
			depth = maxf(depth, water_level(pool, x, z) - height_at(x, z))
	return depth


func ground_height_at(x: float, z: float) -> float:
	var height := height_at(x, z)
	var feature := feature_at(x)
	if not feature.is_empty() and feature.kind == "bridge":
		var river := 1.0 - smoothstep(12.0, 22.0, absf(x - float(feature.x)))
		var deck := 1.0 - smoothstep(5.2, 7.0, absf(road_distance(x, z)))
		height -= river * 6.0 * deck
	return height


func sample(x: float, z: float, include_water: bool = true) -> Dictionary:
	var blend := blend_at(x)
	var a := int(blend.from)
	var b := int(blend.to)
	var t := float(blend.amount)
	var lateral := road_distance(x, z)
	var road := 1.0 - smoothstep(4.0, 6.5, absf(lateral))
	var ground: Color = GROUND[a].lerp(GROUND[b], t)
	var color: Color = ROAD[a].lerp(ROAD[b], t)
	var grip := lerpf(GRIP[a], GRIP[b], t)
	var drag := lerpf(DRAG[a], DRAG[b], t)
	var paved := lerpf(1.0 if a == 2 else 0.0, 1.0 if b == 2 else 0.0, t)
	var surface: String = SURFACES[biome_at(x)]
	var patch := 0.5 + 0.5 * sin(x * 0.071 + sin(z * 0.13) * 2.0) * cos(z * 0.093 - x * 0.017)
	var fork := fork_at(x)
	var branch_material := -1
	var branch_amount := 0.0
	var branch_layers := PackedFloat32Array()
	branch_layers.resize(14)
	var style := {}
	var upper_amount := 1.0
	if not fork.is_empty():
		branch_amount = smoothstep(0.0, 8.0, float(fork.spread))
		style = junction_style(int(fork.id))
		upper_amount = 1.0 - smoothstep(-10.0, 10.0, z - _base_center(x) - route_offset(x))
	else:
		var exit_id := int(floor((x - 600.0) / REGION))
		var end := 600.0 + exit_id * REGION
		if junction_enabled(exit_id) and x < end + 160.0:
			branch_amount = 1.0 - smoothstep(end, end + 160.0, x)
			style = junction_style(exit_id)
			var lanes := road_centers(x)
			var middle := (float(lanes[0]) + float(lanes[-1])) * 0.5
			upper_amount = 1.0 - smoothstep(-10.0, 10.0, z - middle) if lanes.size() > 1 else (1.0 if int(route_choices.get(str(exit_id), -1)) < 0 else 0.0)
	if not style.is_empty():
		branch_layers[int(style.upper_material)] += upper_amount
		branch_layers[int(style.lower_material)] += 1.0 - upper_amount
		var branch_color := Color(0, 0, 0, 0)
		var branch_paved := 0.0
		var strongest := -1.0
		for layer in branch_layers.size():
			var amount := branch_layers[layer]
			if amount > strongest: strongest = amount; branch_material = layer
			branch_color += _road_color(layer) * amount
			if layer == SurfaceKind.ASPHALT: branch_paved += amount
		color = color.lerp(branch_color, branch_amount)
		grip = lerpf(grip, lerpf(0.88, 1.0, branch_paved), branch_amount)
		drag = lerpf(drag, lerpf(0.55, 0.0, branch_paved), branch_amount)
		paved = lerpf(paved, branch_paved, branch_amount)
		if branch_amount > 0.5: surface = MATERIAL_NAMES[branch_material]
	if not mountain_at(x).is_empty():
		branch_material = SurfaceKind.GRAVEL
		branch_amount = 1.0
		branch_layers.fill(0.0)
		branch_layers[SurfaceKind.GRAVEL] = 1.0
		paved = 0.0
		color = ROAD[1]
		grip = 0.92
		drag = 0.45
		surface = "Horská šotolina"
	var water := water_depth(x, z) if include_water else 0.0
	var alpine := lerpf(1.0 if a == 4 else 0.0, 1.0 if b == 4 else 0.0, t)
	# Ploughed/compacted gravel follows the valley. Snow builds up on high
	# shoulders and in occasional shaded road patches, not as a uniform sheet.
	var snowy := alpine * maxf(smoothstep(11.0, 28.0, absf(lateral)), smoothstep(0.63, 0.78, patch) * 0.75)
	if alpine > 0:
		ground = ground.lerp(Color("aebbb9"), snowy)
		color = color.lerp(Color("b9c4c2"), smoothstep(0.3, 0.75, snowy))
		grip = lerpf(grip, 0.58, snowy)
		if biome_at(x) == 4: surface = "Sneh / posypaná cesta" if snowy > 0.45 else "Horský štrk"
	# Four-wheel-drive shoulders are traversable; snow still has less grip than
	# its packed road. Saturated peat is a local hazard, not the entire biome.
	grip = lerpf(lerpf(0.72, 0.30, snowy), grip, road)
	drag = lerpf(1.25, drag, road)
	drag += snowy * (1.0 - road) * 0.85
	if water > 0.03:
		grip *= 0.72
		drag += 1.2 + water * 1.5
		surface = "Voda · %.0f cm" % (water * 100.0)
	var side_rut := offroad_rut_depth(x, z)
	var rut_depth := maxf(0.0, deformation.depth_at(x, z)) + side_rut
	drag += rut_depth * 1.5
	# Continuous patches, not a binary off-road trap. Deep snow, wetlands and
	# rain-soaked soil carry less load than dry gravel or the compacted road.
	var marsh := lerpf(1.0 if a == 5 else 0.0, 1.0 if b == 5 else 0.0, t)
	var moisture := maxf(soil_moisture, maxf(marsh * 0.85, smoothstep(0.02, 0.25, water)))
	for pool: Dictionary in pools_near(x):
		var shore_radius := Vector2((x - float(pool.x)) / float(pool.rx), (z - float(pool.z)) / float(pool.rz)).length()
		moisture = maxf(moisture, (1.0 - smoothstep(0.85, 1.3, shore_radius)) * 0.48)
	var soft := (1.0 - road) * clampf(0.16 + patch * 0.26 + snowy * 0.14 + moisture * 0.22, 0.0, 0.78)
	var bearing := lerpf(2.1, 0.95, clampf(soft + moisture * (1.0 - road) * 0.10, 0.0, 1.0))
	var soil_depth := (1.0 - road) * clampf(0.10 + patch * 0.16 + snowy * 0.16 + moisture * 0.22, 0.0, 0.60)
	var dominant := b if t > 0.5 else a
	var material: int = ROAD_MATERIALS[dominant] if road > 0.5 else GROUND_MATERIALS[dominant]
	if dominant == 4 and road > 0.5 and snowy > 0.45: material = SurfaceKind.SNOW
	if road > 0.5 and paved < 0.5 and material in [SurfaceKind.ASPHALT, SurfaceKind.CONCRETE]: material = SurfaceKind.GRAVEL
	if road > 0.5 and branch_material >= 0 and branch_amount > 0.5: material = branch_material
	var soil_color := ground.darkened(moisture * (0.12 + patch * 0.15) * (1.0 - snowy))
	if road < 0.5:
		match dominant:
			0:
				if patch > 0.74:
					material = SurfaceKind.SALT
					soil_color = GROUND[0].lerp(Color("b9b7a9"), smoothstep(0.66, 0.90, patch))
					soft *= 0.35
					soil_depth *= 0.35
					grip = 0.86
			1:
				material = SurfaceKind.CLAY if patch > 0.56 else SurfaceKind.ROCK
				soil_color = Color("a47961") if material == SurfaceKind.CLAY else Color("8e8980")
				if material == SurfaceKind.ROCK:
					soft = 0.0
					soil_depth = 0.0
					grip = 0.88
			2:
				if patch < 0.32:
					material = SurfaceKind.DIRT
					soil_color = Color("93816b")
			3:
				material = SurfaceKind.MUD if moisture > 0.65 else (SurfaceKind.LEAVES if patch > 0.35 else SurfaceKind.GRASS)
				soil_color = Color("665e49") if material == SurfaceKind.MUD else (Color("796444") if material == SurfaceKind.LEAVES else ground)
			4:
				material = SurfaceKind.SNOW if snowy > 0.45 else SurfaceKind.ROCK
				soil_color = Color("858d80").lerp(Color("aebbb9"), snowy)
				if snowy > 0.75 and patch > 0.76:
					material = SurfaceKind.ICE
					soil_color = Color("91a6b4")
					grip = 0.24
					soft *= 0.2
					soil_depth *= 0.2
			5:
				soil_color = Color("736951")
				if patch > 0.80 and absf(lateral) > 22.0:
					material = SurfaceKind.PEAT
					soil_color = Color("4c4938")
					soft = 0.98
					bearing = 0.48
					soil_depth = 0.90
					grip = 0.46
					drag += 0.8
	if side_rut > 0.06 and road < 0.5 and material not in [SurfaceKind.ROCK, SurfaceKind.ICE, SurfaceKind.SNOW, SurfaceKind.PEAT]:
		material = SurfaceKind.SAND if dominant == 0 else (SurfaceKind.MUD if moisture > 0.6 else SurfaceKind.DIRT)
		soil_color = soil_color.lerp(Color("675741"), minf(side_rut * 0.85, 0.5))
	# Carry both biome materials through the entire transition. Choosing one
	# material at t=0.5 created a visible cut despite the blended base colour.
	var palette_a := _ground_palette(a, patch, moisture, snowy)
	var palette_b := _ground_palette(b, patch, moisture, snowy)
	soil_color = (palette_a.color as Color).lerp(palette_b.color, t).darkened(moisture * (0.12 + patch * 0.15) * (1.0 - snowy))
	soil_color = soil_color.lerp(Color("675741"), minf(side_rut * 0.85, 0.5))
	var weights := PackedFloat32Array()
	weights.resize(14)
	weights[int(palette_a.material)] += (1.0 - t) * (1.0 - road)
	weights[int(palette_b.material)] += t * (1.0 - road)
	weights[int(ROAD_MATERIALS[a])] += (1.0 - t) * road * (1.0 - branch_amount)
	weights[int(ROAD_MATERIALS[b])] += t * road * (1.0 - branch_amount)
	if branch_material >= 0:
		for layer in branch_layers.size(): weights[layer] += branch_layers[layer] * road * branch_amount
	var event := road_event(floori(x / SECTION))
	var event_amount := event_influence(event, x, z)
	if event_amount > 0 and str(event.kind) in ["mud", "rock_garden", "washout"]:
		var event_material := SurfaceKind.ROCK if str(event.kind) == "rock_garden" else SurfaceKind.MUD
		for layer in weights.size(): weights[layer] *= 1.0 - event_amount
		weights[event_material] += event_amount
		soil_color = soil_color.lerp(Color("655741") if event_material == SurfaceKind.MUD else Color("908c80"), event_amount)
		color = color.lerp(Color("655741") if event_material == SurfaceKind.MUD else Color("908c80"), event_amount)
		if event_material == SurfaceKind.MUD:
			grip = lerpf(grip, 0.55, event_amount)
			drag += event_amount * lerpf(0.8, 2.5, float(event.strength))
			soft = lerpf(soft, lerpf(0.62, 0.98, float(event.strength)), event_amount)
			soil_depth = lerpf(soil_depth, lerpf(0.32, 0.85, float(event.strength)), event_amount)
			bearing = lerpf(bearing, lerpf(1.30, 0.48, float(event.strength)), event_amount)
			moisture = maxf(moisture, event_amount * 0.9)
			paved *= 1.0 - event_amount
		if event_amount > 0.5: material = event_material
	var structure := feature_at(x)
	var solid_deck := not structure.is_empty() and str(structure.kind) == "bridge" and absf(x - float(structure.x)) <= 28.0 and absf(lateral) < 5.4
	var solid_rail := not structure.is_empty() and str(structure.kind) == "rail" and absf(x - float(structure.x)) <= 2.6
	if solid_deck or solid_rail:
		soft = 0.0
		soil_depth = 0.0
	var soil_label: String = MATERIAL_NAMES[material]
	return {"color": soil_color.lerp(color, road), "road": road, "lateral": lateral, "paved": paved * road,
		"material": material,
		"material_weights": weights, "event": str(event.get("kind", "")),
		"deformable": not (solid_deck or solid_rail) and material not in [SurfaceKind.ROCK, SurfaceKind.ICE, SurfaceKind.ASPHALT, SurfaceKind.CONCRETE],
		"soil_softness": soft, "soil_bearing": bearing, "soil_depth": soil_depth, "soil_moisture": moisture,
		"snow": snowy,
		"rut_depth": rut_depth,
		"grip": grip, "drag": drag, "water": water, "label": surface if road > 0.5 else soil_label,
		"roughness": (0.40 if material in [SurfaceKind.ROCK, SurfaceKind.GRAVEL] else 0.16) * (1.0 - paved) + (0.4 if water > 0.03 else 0.0)}


func _ground_palette(biome: int, patch: float, moisture: float, snowy: float) -> Dictionary:
	var material: int = GROUND_MATERIALS[biome]
	var color: Color = GROUND[biome]
	match biome:
		0:
			if patch > 0.74: material = SurfaceKind.SALT; color = GROUND[0].lerp(Color("b9b7a9"), smoothstep(0.66, 0.90, patch))
		1:
			material = SurfaceKind.CLAY if patch > 0.56 else SurfaceKind.ROCK
			color = Color("a98b70") if material == SurfaceKind.CLAY else Color("a79a85")
		2:
			if patch < 0.32: material = SurfaceKind.DIRT; color = Color("93816b")
		3:
			material = SurfaceKind.MUD if moisture > 0.65 else (SurfaceKind.LEAVES if patch > 0.35 else SurfaceKind.GRASS)
			# Physical material selection stays intact, but colour crosses patches
			# gradually instead of drawing hard brown/green pixel-like borders.
			color = GROUND[biome].lerp(Color("817961"), smoothstep(0.25, 0.62, patch))
			color = color.lerp(Color("716b59"), smoothstep(0.50, 0.85, moisture))
		4:
			material = SurfaceKind.SNOW if snowy > 0.45 else SurfaceKind.ROCK
			color = Color("858d80").lerp(Color("aebbb9"), snowy)
			if snowy > 0.75 and patch > 0.76: material = SurfaceKind.ICE; color = Color("91a6b4")
		5:
			color = Color("736951")
			if patch > 0.80: material = SurfaceKind.PEAT; color = Color("4c4938")
	return {"material": material, "color": color}


static func _road_color(material: int) -> Color:
	match material:
		SurfaceKind.ASPHALT: return Color("777c79")
		SurfaceKind.DIRT: return Color("9e8d76")
		_: return Color("aaa28c")
