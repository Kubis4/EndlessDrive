class_name DriveTerrain
extends RefCounted
## One deterministic source for road geometry, rendering, hazards and tyre physics.
## Forks diverge permanently; a saved physical choice drives the continuing world.

const SECTION := 240.0
const REGION := 960.0
const BIOMES := ["Prašná kotlina", "Medený kaňon", "Vidiek", "Živý les", "Horské sedlo", "Mokrade"]
const SURFACES := ["Piesok", "Štrk", "Asfalt", "Hlina", "Sneh", "Betón"]
const GROUND := [Color("b5a17c"), Color("a47352"), Color("869365"), Color("627956"), Color("afc3dc"), Color("6e806b")]
const ROAD := [Color("c3ae88"), Color("938b7b"), Color("44494a"), Color("7c7258"), Color("99b1cc"), Color("969d98")]
const GRIP := [0.88, 0.92, 1.0, 0.94, 0.38, 0.99]
const DRAG := [0.55, 0.45, 0.0, 0.75, 0.65, 0.12]
var seed_value := 24601
var route_choices: Dictionary = {}
var _centers: Dictionary = {}
var _heights: Dictionary = {}
var _seeds: Dictionary = {}
var _pools: Dictionary = {}
var _blends: Dictionary = {}
var deformation := DriveDeformation.new()
var soil_moisture := 0.0


func clear_caches() -> void:
	_centers.clear()
	_heights.clear()
	_seeds.clear()
	_pools.clear()
	_blends.clear()


func center_at(x: float) -> float:
	if _centers.has(x): return float(_centers[x])
	var center := _base_center(x) + route_offset(x)
	var fork := fork_at(x)
	if not fork.is_empty():
		center += float(route_choices.get(str(fork.id), -1)) * float(fork.spread)
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
	return base + bend * (34.0 if kind in [1, 4] else 5.0) * smoothstep(100.0, 240.0, x)


func route_offset(x: float) -> float:
	var offset := 0.0
	var count := maxi(0, int(floor((x - 600.0) / REGION)) + 1)
	for index in count:
		var end := 600.0 + index * REGION
		offset += float(route_choices.get(str(index), -1)) * (42.0 + 96.0 * smoothstep(end, end + REGION, x))
	return offset


func corridor_center(x: float) -> float:
	return _base_center(x) + route_offset(x) if not fork_at(x).is_empty() else center_at(x)


static func route_seed(seed: int, choices: Dictionary, x: float) -> int:
	var result := seed
	var keys := choices.keys()
	keys.sort_custom(func(a: Variant, b: Variant) -> bool: return int(a) < int(b))
	for key: Variant in keys:
		if x >= 600.0 + int(key) * REGION:
			result = int(posmod(result * 31 + int(key) * 7919 + (17 if int(choices[key]) < 0 else 58), 1000000007))
	return result


func seed_at(x: float) -> int:
	var key := int(floor(x))
	if not _seeds.has(key):
		if _seeds.size() > 4096: _seeds.clear()
		_seeds[key] = route_seed(seed_value, route_choices, x)
	return int(_seeds[key])


func feature_at(x: float) -> Dictionary:
	var region := maxi(0, int(floor(x / REGION)))
	var center := region * REGION + 744.0
	if absf(x - center) > 42.0: return {}
	return {"kind": ["bridge", "rail", "tunnel"][posmod(seed_at(center) + region, 3)], "x": center, "id": region}


func fork_at(x: float) -> Dictionary:
	var index := maxi(0, int(floor((x - 360.0) / REGION)))
	var start := 360.0 + index * REGION
	if x < start or x >= start + SECTION:
		return {}
	var t := (x - start) / SECTION
	return {"id": index, "start": start, "end": start + SECTION,
		"spread": 42.0 * smoothstep(0.0, 1.0, t)}


func junction_ahead(x: float) -> Dictionary:
	var index := maxi(0, int(ceil((x - 360.0) / REGION)))
	var active := fork_at(x)
	if not active.is_empty():
		index = int(active.id)
	var start := 360.0 + index * REGION
	return {"distance": maxf(0.0, start - x), "active": not active.is_empty(),
		"upper": "HORNÁ · asfalt / kopce", "lower": "DOLNÁ · štrk / brod"}


func road_centers(x: float) -> PackedFloat32Array:
	var center := center_at(x)
	var fork := fork_at(x)
	if fork.is_empty():
		return PackedFloat32Array([center])
	var spread := float(fork.spread)
	center -= float(route_choices.get(str(fork.id), -1)) * spread
	return PackedFloat32Array([center - spread, center + spread])


func road_distance(x: float, z: float) -> float:
	var nearest := INF
	for center: float in road_centers(x):
		if absf(z - center) < absf(nearest):
			nearest = z - center
	return nearest


func boundary_width(x: float) -> float:
	# Follow both forks, leaving wide but bounded driveable shoulders.
	var fork := fork_at(x)
	return 36.0 + (float(fork.spread) if not fork.is_empty() else 0.0)


func blend_at(x: float) -> Dictionary:
	if _blends.has(x): return _blends[x]
	var region := int(floor((maxf(0.0, x) + 180.0) / REGION))
	var amount := smoothstep(region * REGION - 180.0, region * REGION + 180.0, x)
	var result := {"from": _region_biome(maxi(0, region - 1)), "to": _region_biome(region), "amount": amount}
	if _blends.size() > 4096: _blends.clear()
	_blends[x] = result
	return result


func _region_biome(region: int) -> int:
	if route_choices.is_empty(): return posmod(region, 6)
	return 0 if region == 0 else posmod(seed_at(region * REGION) + region * 7, 6)


func biome_at(x: float) -> int:
	var blend := blend_at(x)
	return int(blend.to) if float(blend.amount) >= 0.5 else int(blend.from)


func base_height(x: float) -> float:
	if _heights.has(x): return float(_heights[x])
	var t := fposmod(maxf(x, 0.0), SECTION) / SECTION
	var kind := posmod(int(floor(maxf(x, 0.0) / SECTION)), 6)
	var hills := sin(t * PI) * sin(t * PI) * sin(t * TAU)
	var amplitude := [0.6, 4.0, 9.0, 1.0, 12.0, 5.0][kind] as float
	var elevation := 0.0
	for key: Variant in route_choices:
		var end := 600.0 + int(key) * REGION
		elevation += (24.0 if int(route_choices[key]) < 0 else -8.0) * smoothstep(end, end + 480.0, x)
	var result := sin(x * 0.017) * 0.45 + sin(x * 0.0045) * 1.8 + hills * amplitude + elevation
	if _heights.size() > 4096: _heights.clear()
	_heights[x] = result
	return result


func dry_height(x: float, z: float) -> float:
	var lateral := absf(road_distance(x, z))
	var shoulder := smoothstep(6.0, 25.0, lateral)
	var dunes := sin(x * 0.037 + z * 0.019) * 1.7 + cos(z * 0.045 - x * 0.017) * 0.9
	var height := base_height(x) + shoulder * dunes
	var fork := fork_at(x)
	if not fork.is_empty():
		var t := (x - float(fork.start)) / SECTION
		var upper := 1.0 - smoothstep(-10.0, 4.0, z - _base_center(x) - route_offset(x))
		height += pow(sin(t * PI), 4.0) * upper * 3.5
	# Broad, shallow potholes are geometry, not painted circles. Leave the first stop calm.
	if x > 125.0:
		var section := int(floor(x / SECTION))
		for i in 3:
			var hx := section * SECTION + 150.0 + i * 24.0
			var lane := road_centers(hx)[0]
			var hz := lane + (-1.5 if i % 2 == 0 else 1.5)
			var radius := Vector2((x - hx) / 3.8, (z - hz) / 2.3).length()
			height -= (1.0 - smoothstep(0.0, 1.0, radius)) * (0.30 if section % 3 == 0 else 0.46)
	var feature := feature_at(x)
	if not feature.is_empty() and feature.kind == "bridge":
		var river := 1.0 - smoothstep(12.0, 22.0, absf(x - float(feature.x)))
		var deck := 1.0 - smoothstep(5.2, 7.0, lateral)
		height -= river * 6.0 * (1.0 - deck)
	elif not feature.is_empty() and feature.kind == "rail":
		var approach := 1.0 - smoothstep(3.0, 12.0, absf(x - float(feature.x)))
		height = lerpf(height, base_height(float(feature.x)), approach)
	return height


func pools_near(x: float) -> Array[Dictionary]:
	if _pools.has(x): return _pools[x]
	var result := _find_pools(x)
	if _pools.size() > 4096: _pools.clear()
	_pools[x] = result
	return result


func _find_pools(x: float) -> Array[Dictionary]:
	var pools: Array[Dictionary] = []
	var feature := feature_at(x)
	if not feature.is_empty() and feature.kind == "bridge" and absf(x - float(feature.x)) < 24.0:
		pools.append({"x": feature.x, "z": center_at(float(feature.x)), "rx": 22.0, "rz": 120.0, "level": base_height(float(feature.x)) - 3.0, "depth": 3.0, "river": true})
	if absf(x - 207.0) < 18.0:
		pools.append(_pool(207.0, center_at(207.0) + 1.0, 12.0, 4.2))
	var section := int(floor(x / SECTION))
	for index in range(maxi(0, section - 1), section + 2):
		if index % 6 == 3 or biome_at(index * SECTION + 120.0) == 5:
			var px := index * SECTION + 120.0
			if absf(x - px) < 25.0:
				pools.append(_pool(px, road_centers(px)[-1], 19.0, 5.5))
	var fork := fork_at(x)
	if not fork.is_empty():
		var px := float(fork.start) + SECTION * 0.5
		if absf(x - px) < 25.0:
			pools.append(_pool(px, road_centers(px)[-1], 18.0, 5.0))
	return pools


func _pool(x: float, z: float, rx: float, rz: float) -> Dictionary:
	return {"x": x, "z": z, "rx": rx, "rz": rz, "level": base_height(x) - 0.05, "depth": 0.62}


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
		height = lerpf(height, minf(height, water_level(pool, x, z) - float(pool.depth)), bowl)
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
	var paved := lerpf(1.0 if a in [2, 5] else 0.0, 1.0 if b in [2, 5] else 0.0, t)
	var surface: String = SURFACES[biome_at(x)]
	var fork := fork_at(x)
	if not fork.is_empty():
		var ft := (x - float(fork.start)) / SECTION
		var choice := smoothstep(0.0, 8.0, float(fork.spread))
		var upper := z < _base_center(x) + route_offset(x)
		color = color.lerp(Color("484d4b") if upper else Color("968671"), choice)
		grip = lerpf(grip, 1.0 if upper else 0.84, choice)
		drag = lerpf(drag, 0.0 if upper else 0.45, choice)
		paved = lerpf(paved, 1.0 if upper else 0.0, choice)
		if ft > 0.1 and ft < 0.9: surface = "Asfalt / kopce" if upper else "Štrk / brod"
	else:
		var exit_id := int(floor((x - 600.0) / REGION))
		var end := 600.0 + exit_id * REGION
		if exit_id >= 0 and x < end + 160.0:
			var choice := 1.0 - smoothstep(end, end + 160.0, x)
			var upper := int(route_choices.get(str(exit_id), -1)) < 0
			color = color.lerp(Color("484d4b") if upper else Color("968671"), choice)
			grip = lerpf(grip, 1.0 if upper else 0.84, choice)
			drag = lerpf(drag, 0.0 if upper else 0.45, choice)
			paved = lerpf(paved, 1.0 if upper else 0.0, choice)
	var water := water_depth(x, z) if include_water else 0.0
	var snowy := lerpf(1.0 if a == 4 else 0.0, 1.0 if b == 4 else 0.0, t)
	# Deep shoulder snow is NEVER grippier than packed snow on the road.
	grip = lerpf(lerpf(0.52, 0.20, snowy), grip, road)
	drag = lerpf(2.2, drag, road)
	drag += snowy * (1.0 - road) * 2.0
	if water > 0.03:
		grip *= 0.72
		drag += 1.2 + water * 1.5
		surface = "Voda · %.0f cm" % (water * 100.0)
	var rut_depth := deformation.depth_at(x, z)
	drag += rut_depth * 1.5
	# Continuous patches, not a binary off-road trap. Deep snow, wetlands and
	# rain-soaked soil carry less load than dry gravel or the compacted road.
	var patch := 0.5 + 0.5 * sin(x * 0.071 + sin(z * 0.13) * 2.0) * cos(z * 0.093 - x * 0.017)
	var marsh := lerpf(1.0 if a == 5 else 0.0, 1.0 if b == 5 else 0.0, t)
	var moisture := maxf(soil_moisture, maxf(marsh * 0.85, smoothstep(0.02, 0.25, water)))
	var soft := (1.0 - road) * clampf(0.23 + patch * 0.42 + snowy * 0.30 + moisture * 0.45, 0.0, 1.0)
	var bearing := lerpf(1.7, 0.40, clampf(soft * 0.9 + moisture * (1.0 - road) * 0.25, 0.0, 1.0))
	var soil_depth := (1.0 - road) * clampf(0.18 + patch * 0.34 + snowy * 0.40 + moisture * 0.60, 0.0, DriveDeformation.MAX_DEPTH)
	var soil_color := ground.darkened(moisture * (0.12 + patch * 0.15) * (1.0 - snowy))
	var soil_label := "Hlboký sneh" if snowy > 0.5 else ("Mäkké blato" if moisture > 0.5 else ("Sypký podklad" if soft > 0.5 else "Mimo cesty"))
	return {"color": soil_color.lerp(color, road).darkened(minf(rut_depth * 0.45, 0.42)), "road": road, "lateral": lateral, "paved": paved * road,
		"soil_softness": soft, "soil_bearing": bearing, "soil_depth": soil_depth, "soil_moisture": moisture,
		"snow": snowy,
		"rut_depth": rut_depth,
		"grip": grip, "drag": drag, "water": water, "label": surface if road > 0.5 else soil_label,
		"roughness": (1.0 - paved) * 0.22 + (0.4 if water > 0.03 else 0.0)}
