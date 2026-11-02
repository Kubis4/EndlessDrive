extends RefCounted
## An endless highland built as the car drives: one road heads north for ever
## through valleys, fords, passes and forest, the land around it made in 60 m
## tiles as they come within reach and dropped again behind (endless_world.gd
## streams them). Answers the same queries as highlands.gd.
##
## The land is a function of position (layered noise, massifs placed per
## 1.5 km band, the river's valley), so any tile can be made on its own, on a
## worker thread. The road is planned ahead of the car in 200–280 m stretches
## (A* over that land with a grade limit, as generated_terrain.gd does for a
## whole map) together with its side roads, sites and ponds; a tile then
## stamps whatever roads pass it. Finished tiles go into ring maps 720 m
## across that physics, the ground shader and the dressing read (texel x, z
## of the world at x, z modulo the ring), so nothing ever has to move.
const Surface = preload("res://desert_battlefield/terrain.gd")
const STEP := 0.75
## Tile: 80 × 80 texels (60 m); the ring holds 12 × 12 tiles (720 m).
const TILE := 80
const TILE_M := 60.0
const RING := 12
const N := 960
## Map origin for the dressing and shader (rings wrap, so none).
const HALF := 0.0
const DRIVABLE := 1.0e6
## No one level here (see river_level): this keeps the ground shader's and the
## snow pack's waterline out of the way.
const WATER_LEVEL := -10000.0
const MAX_FLOOD := 0.45
const FROZEN := 0.8
const LOOK_BANK := 1
const POND_DEPTH := 1.4
const SNOWLINE := 1900.0
## Above this no forest grows, and the mountains turn to rock.
const TREELINE := 1450.0
## From this height sheets of ice lie across the mountain road.
const ICE_FROM := 1550.0
## Road planning: how far ahead of the car, stretch length, route grid cell.
const PLAN_AHEAD := 900.0
const ROUTE := 8.0
const ROUTE_GRADE := 0.11
const ROAD_GRADE := 0.12
const SHOULDER := 1.5
## Massifs and ponds are placed per band of this length along z.
const BAND := 1500.0
const QUARRY := Vector3(1.0e7, 1.0e7, 0.0)
## Version of how the world is made, kept in saves: a run saved in another
## version starts afresh (the same seed gives other land and roads).
const CACHE_VERSION := 9

static var seed_value := 0
static var river := PackedFloat32Array([0, 20, 0.013, 0, 7, 0.037, 0, 2.5, 0.08, 0])
static var noise_seeds := PackedInt32Array()
static var noises := {}
static var flood := 0.0
static var pond_ice := 0.0
static var CAMP := Vector3(0, 0, 15.0)

## Ring maps (N × N) and which tile each ring slot holds (Vector2i, or one
## with x = 1 << 30 when empty).
static var heights := PackedFloat32Array()
static var ground := PackedByteArray()
static var slots: Array[Vector2i] = []
const EMPTY := Vector2i(1 << 30, 0)

## Roads: the main one (index 0) and side roads, each a dictionary with name,
## kind, half width, its 2 m polyline (points), levels and arc lengths.
static var roads: Array = []
## The main road's control points (A* routes, simplified), smoothed into its
## polyline; how far north it is planned, and the bank it keeps to (±1).
static var road_controls: Array = []
static var planned_z := 0.0
static var bank := 1.0
static var stretches := 0
## Spatial index of road samples: 16 m cell → [road, sample, road, sample, …].
static var road_index := {}
## Massifs (x, z, radius, height) and ponds (x, z, radius, level) per band.
static var massifs: Array = []
static var ponds: Array = []
## The same by band of z: band → massifs, band → indices into ponds.
static var massifs_in := {}
static var ponds_in := {}
static var bands := {}
static var massif_bands := {}
## Sites along the way (name, kind "supply" / "camp", point, along, reward).
static var sites: Array = []
## For the dressing (highlands_world.gd asks for these).
static var trails: Array = []
static var paths: Array = []
static var spots: Array = []

static func initial_seed() -> int:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): return int(arg.trim_prefix("--seed="))
	var path := "user://endless_expedition.json"
	if FileAccess.file_exists(path):
		var saved = JSON.parse_string(FileAccess.get_file_as_string(path))
		if saved is Dictionary and saved.has("seed"): return int(saved.seed)
	var random := RandomNumberGenerator.new()
	random.randomize()
	return random.randi()

## A new world: river course, noise seeds, empty rings; the first stretches of
## road (camp at the origin) are planned at once.
static func generate(new_seed: int, start: int = -1) -> void:
	var started := Time.get_ticks_msec()
	seed_value = new_seed
	start_biome = start if start >= 0 else _chosen_start()
	flood = 0.0
	pond_ice = 0.0
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	river = PackedFloat32Array([rng.randf_range(-40.0, 40.0),
		rng.randf_range(16.0, 28.0), rng.randf_range(0.009, 0.015), rng.randf_range(-PI, PI),
		rng.randf_range(5.0, 10.0), rng.randf_range(0.03, 0.045), rng.randf_range(-PI, PI),
		rng.randf_range(1.5, 3.5), rng.randf_range(0.07, 0.1), rng.randf_range(-PI, PI)])
	noise_seeds = PackedInt32Array()
	for i in 7: noise_seeds.append(rng.randi())
	zones.clear()
	altitude.clear()
	bag.clear()
	mountains_z = 0.0
	walls.clear()
	wall_spans.clear()
	summit = Vector3.ZERO
	river_gap = Vector2(1.0e9, 1.0e9)
	next_station = 900.0
	noises = make_noises(noise_seeds)
	noises.zones = zones
	noises.altitude = altitude
	noises.walls = wall_spans
	heights = PackedFloat32Array()
	heights.resize(N * N)
	ground = PackedByteArray()
	ground.resize(N * N)
	slots.clear()
	for i in RING * RING: slots.append(EMPTY)
	massifs.clear()
	ponds.clear()
	massifs_in.clear()
	ponds_in.clear()
	bands.clear()
	massif_bands.clear()
	sites.clear()
	roads.clear()
	road_controls.clear()
	road_index.clear()
	old_end_control = Vector2(INF, INF)
	settled_samples = 0
	stretches = 0
	# Camp beside the river on its east bank, the road heading north.
	bank = 1.0 if rng.randf() < 0.5 else -1.0
	var camp := Vector2(river_x(0.0) + bank * 70.0, 0.0)
	CAMP = Vector3(camp.x, camp.y, 15.0)
	road_controls = [camp, camp + Vector2(0, 24)]
	roads.append({"name": "Výprava", "kind": Surface.GRAVEL, "half": 3.1, "points": PackedVector2Array(), "levels": PackedFloat32Array(), "along": PackedFloat32Array(), "ford": 0.4})
	planned_z = camp.y
	# (The whole journey's landscapes are known from the start.)
	_ensure_zones(JOURNEY_KM * 2000.0)
	plan_to(PLAN_AHEAD)
	_refresh_lists()
	print("ENDLESS seed=%d planned to %d m in %d ms" % [seed_value, roundi(planned_z), Time.get_ticks_msec() - started])

## Where a run starts: --start=N, else where the saved run started, else what the
## menu drew for a new run (fields or desert, never asked).
static func _chosen_start() -> int:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--start="): return int(arg.trim_prefix("--start="))
	var path := "user://endless_expedition.json"
	if FileAccess.file_exists(path):
		var saved = JSON.parse_string(FileAccess.get_file_as_string(path))
		if saved is Dictionary and saved.has("start"): return int(saved.start)
	return int(Engine.get_meta("endless_start", FIELDS if hash([seed_value, 5]) % 2 == 0 else DESERT))

static func make_noises(seeds: PackedInt32Array) -> Dictionary:
	return {"base": _noise(seeds[0], 1.0 / 260.0, 5), "detail": _noise(seeds[1], 1.0 / 55.0, 3),
		"ridges": _noise(seeds[2], 1.0 / 150.0, 4, true), "warp": _noise(seeds[3], 1.0 / 90.0, 2),
		"woods": _noise(seeds[4], 1.0 / 120.0, 3), "bog": _noise(seeds[5], 1.0 / 70.0, 2),
		"hills": _noise(seeds[6], 1.0 / 95.0, 3), "zones": [], "altitude": [], "walls": [], "summit": Vector3.ZERO, "gap": Vector2(1.0e9, 1.0e9)}

static func _noise(seed: int, frequency: float, octaves: int, ridged: bool = false) -> FastNoiseLite:
	var noise := FastNoiseLite.new()
	noise.seed = seed
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.frequency = frequency
	noise.fractal_type = FastNoiseLite.FRACTAL_RIDGED if ridged else FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = octaves
	return noise

static func river_x(z: float) -> float:
	return river_x_of(river, z)

static func river_x_of(r: PackedFloat32Array, z: float) -> float:
	return r[0] + r[1] * sin(z * r[2] + r[3]) + r[4] * sin(z * r[5] + r[6]) + r[7] * sin(z * r[8] + r[9])

# ---------------------------------------------------------------- the land

## Landscapes the road passes through, in turn (every one before any comes
## again), their borders wandering with the warp noise and blending over 400 m.
## Height along the way: every landscape of the journey lies higher than the one
## before, up to the last stage, the great range with its serpentines and the summit
## (_add_final_range). Past it the land goes on for whoever wants to explore. Everything,
## the river included, rides on this height.
enum { FOREST, MEADOW, DESERT, MOUNTAINS, BOG, FIELDS, STEPPE, TAIGA, FOOTHILLS }
const BIOME_NAMES := ["Les", "Lúky", "Púšť", "Hory", "Močiare", "Polia", "Step", "Tajga", "Podhorie"]
## The journey to the mountains, from either start: landscape, its share of the
## way, its height (m). Stretched to about JOURNEY_KM.
const JOURNEY_KM := 56.0
const FROM_DESERT := [DESERT, STEPPE, FIELDS, MEADOW, BOG, FOREST, TAIGA, FOOTHILLS]
const FROM_FIELDS := [FIELDS, MEADOW, STEPPE, FIELDS, BOG, FOREST, TAIGA, FOOTHILLS]
const STAGE_KM := {DESERT: [6.0, 9.0], STEPPE: [4.0, 6.0], FIELDS: [6.0, 9.0], MEADOW: [3.0, 5.0], BOG: [5.0, 7.0], FOREST: [6.0, 9.0], TAIGA: [5.0, 7.0], FOOTHILLS: [5.0, 7.0]}
const STAGE_HEIGHT := {DESERT: [20.0, 60.0], FIELDS: [90.0, 140.0], STEPPE: [160.0, 210.0], MEADOW: [230.0, 280.0], BOG: [300.0, 340.0], FOREST: [420.0, 520.0], TAIGA: [680.0, 800.0], FOOTHILLS: [1000.0, 1150.0]}
## After the summit: on among the high lands, for ever.
const HIGH_LANDS := [TAIGA, FOOTHILLS, MOUNTAINS, FOOTHILLS, MOUNTAINS]
## Where the first range starts (m along z): the journey's end.
static var mountains_z := 0.0
## The serpentines. A wall is a steep slope (about 42 %) the road cannot go straight up: it
## zigzags across it, a hairpin every WALL_LEG metres of z. Their road is planned as one
## piece. `walls`: z0, z1, x, legs, built, then entry, exit, s0, s1 (along the road).
## `wall_spans`: the same for land() on the workers: Vector4(z0, z1, x, how far it lasts on).
static var walls: Array = []
static var wall_spans: Array = []
const WALL_HALF := 110.0
const WALL_LEG := 60.0
const HAIRPIN := 13.0
const WALL_GRADE := 0.42
## The transmitter on the summit: Vector3(x, height, z). Past the top the ground beyond
## stays below it (land()). Where there is no river: from the first wall to well past the top.
static var summit := Vector3.ZERO
static var river_gap := Vector2(1.0e9, 1.0e9)
## From the top of the last wall to the transmitter, its levelled lot.
const SUMMIT_AHEAD := 130.0
const SUMMIT_LOT := 34.0
## Radius of the curves onto a wall's first leg and off its last (m).
const TURN_IN := 16.0
const BLEND := 200.0
## (Less than ROAD_GRADE: a road that cannot keep up ends in a trench or on a dyke.)
const CLIMB := 0.09
## Where this run starts (the start menu, --start=, or a save).
static var start_biome := FOREST
## Zones: Vector2(start z, biome), in order; the first reaches back for ever.
static var zones: Array = []
## Height control points: Vector2(z, height), in order.
static var altitude: Array = []
## Biomes still to come before the next round.
static var bag: Array = []

static func _lowland(biome: int, rng: RandomNumberGenerator) -> float:
	match biome:
		MEADOW: return rng.randf_range(30.0, 90.0)
		DESERT: return rng.randf_range(15.0, 70.0)
		BOG: return rng.randf_range(4.0, 25.0)
	return rng.randf_range(40.0, 120.0)

static func _ensure_zones(z: float) -> void:
	if zones.is_empty():
		var first := RandomNumberGenerator.new()
		first.seed = hash([seed_value, 3])
		bag = _journey(first)
		# (--start= for a test can begin anywhere on the way.)
		var biome: int = bag.pop_front()
		zones.append(Vector2(-1.0e7, biome))
		var level := first.randf_range(STAGE_HEIGHT[biome][0], STAGE_HEIGHT[biome][1]) if STAGE_HEIGHT.has(biome) else 300.0
		altitude.append(Vector2(-1.0e7, level))
		altitude.append(Vector2(_stage_length(biome, first), level))
		if biome == MOUNTAINS:
			mountains_z = maxf(1.0, (altitude[altitude.size() - 1] as Vector2).x)
			_add_final_range(first)
	while (altitude[altitude.size() - 1] as Vector2).x < z + 2500.0:
		var rng := RandomNumberGenerator.new()
		rng.seed = hash([seed_value, zones.size(), 11])
		var from: Vector2 = altitude[altitude.size() - 1]
		var biome: int
		if not bag.is_empty(): biome = bag.pop_front()
		elif mountains_z == 0.0: biome = MOUNTAINS
		else:
			biome = HIGH_LANDS[zones.size() % HIGH_LANDS.size()]
			# (Down from the summit first: no range past it stands as high.)
			if biome == MOUNTAINS and from.y > 1500.0: biome = FOOTHILLS
		if biome == MOUNTAINS:
			zones.append(Vector2(from.x + 150.0, MOUNTAINS))
			if mountains_z == 0.0:
				mountains_z = from.x
				_add_final_range(rng)
			else: _add_range(rng)
		else:
			var level := rng.randf_range(STAGE_HEIGHT[biome][0], STAGE_HEIGHT[biome][1])
			# On the journey the way only ever goes up.
			if mountains_z == 0.0: level = maxf(level, from.y + rng.randf_range(25.0, 60.0))
			# Long, even climbs and descents on the way there.
			var way := maxf(500.0, absf(level - from.y) / (CLIMB * 0.6))
			var length := maxf(way + 400.0, _stage_length(biome, rng))
			# Coming down from high up, the mountains last nearly to the bottom.
			zones.append(Vector2(from.x + way * (0.85 if from.y > level + 200.0 else 0.5), biome))
			altitude.append(Vector2(from.x + way, level))
			altitude.append(Vector2(from.x + length, level))

## The landscapes in the order of this run (the seed varies it a little).
static func _journey(rng: RandomNumberGenerator) -> Array:
	var list: Array = (FROM_DESERT if start_biome == DESERT else FROM_FIELDS).duplicate()
	# Now and then the meadows and the steppe change places, or the forest comes
	# before the marsh.
	if rng.randf() < 0.5:
		var a := list.find(MEADOW)
		var b := list.find(STEPPE)
		if a >= 0 and b >= 0:
			list[a] = STEPPE
			list[b] = MEADOW
	if rng.randf() < 0.3:
		var a := list.find(BOG)
		var b := list.find(FOREST)
		list[a] = FOREST
		list[b] = BOG
	var at := list.find(start_biome)
	if at > 0: list = list.slice(at)
	elif at < 0: list = [start_biome]
	# Stretch the stages so that the mountains are JOURNEY_KM away.
	var total := 0.0
	for biome: int in list:
		total += (STAGE_KM[biome][0] + STAGE_KM[biome][1]) * 0.5 if STAGE_KM.has(biome) else 0.0
	stage_scale = JOURNEY_KM / total if total > 0.0 else 1.0
	return list

static var stage_scale := 1.0

static func _stage_length(biome: int, rng: RandomNumberGenerator) -> float:
	if not STAGE_KM.has(biome): return 1000.0
	return rng.randf_range(STAGE_KM[biome][0], STAGE_KM[biome][1]) * 1000.0 * stage_scale

## The last stage: a long climb, the first wall of serpentines, a high saddle, the second
## wall, and along the top to the transmitter (about 2300 m).
static func _add_final_range(rng: RandomNumberGenerator) -> void:
	var from: Vector2 = altitude[altitude.size() - 1]
	var base := maxf(from.y + 250.0, rng.randf_range(1380.0, 1480.0))
	var foot := from.x + (base - from.y) / CLIMB
	altitude.append(Vector2(foot, base))
	altitude.append(Vector2(foot + 150.0, base))
	var first := _add_wall(foot + 150.0, base, 12, 140.0)
	var saddle := Vector2(first.x + rng.randf_range(1200.0, 1700.0), first.y + rng.randf_range(50.0, 100.0))
	altitude.append(saddle)
	altitude.append(Vector2(saddle.x + 100.0, saddle.y))
	var top := _add_wall(saddle.x + 100.0, saddle.y, 19, 560.0)
	summit = Vector3((walls[1] as Dictionary).x, top.y, top.x + SUMMIT_AHEAD)
	altitude.append(Vector2(top.x + 420.0, top.y))
	river_gap = Vector2((walls[0] as Dictionary).z0 - 100.0, top.x + 2400.0)
	noises.summit = summit
	noises.gap = river_gap

## A wall from (z0, level): up WALL_GRADE over `legs` legs. Returns its top (z, height).
static func _add_wall(z0: float, level: float, legs: int, lasts: float) -> Vector2:
	var z1 := z0 + legs * WALL_LEG
	var top := level + legs * WALL_LEG * WALL_GRADE
	# (Straight up the valley's middle: no river runs here.)
	var x := river_x(z0 + (z1 - z0) * 0.5)
	walls.append({"z0": z0, "z1": z1, "x": x, "legs": legs, "built": false})
	wall_spans.append(Vector4(z0, z1, x, lasts))
	altitude.append(Vector2(z1, top))
	return Vector2(z1, top)

## How much of a wall's slope z is on (0..1).
static func wall_share(z: float, spans: Array) -> float:
	for span: Vector4 in spans:
		if z > span.x - 140.0 and z < span.y + span.w: return smoothstep(span.x - 140.0, span.x, z) * (1.0 - smoothstep(span.y + span.w - 140.0, span.y + span.w, z))
	return 0.0

## Whether the river has given out here (0..1).
static func river_off(z: float, gap: Vector2) -> float:
	return smoothstep(gap.x - 100.0, gap.x, z) * (1.0 - smoothstep(gap.y - 100.0, gap.y, z))

## Up to a summit and along its saddle.
static func _add_range(rng: RandomNumberGenerator) -> void:
	var from: Vector2 = altitude[altitude.size() - 1]
	var peak := maxf(from.y + 150.0, rng.randf_range(1500.0, 1900.0))
	var top := from.x + (peak - from.y) / CLIMB
	altitude.append(Vector2(top, peak))
	altitude.append(Vector2(top + rng.randf_range(300.0, 700.0), peak - rng.randf_range(0.0, 40.0)))

## Height of the way at z (straight between control points, corners eased).
static func altitude_of(z: float, list: Array) -> float:
	var low := 0
	var high := list.size() - 1
	if z >= (list[high] as Vector2).x: return (list[high] as Vector2).y
	while high - low > 1:
		var mid := (low + high) / 2
		if (list[mid] as Vector2).x <= z: low = mid
		else: high = mid
	var a: Vector2 = list[low]
	var b: Vector2 = list[high]
	var t := (z - a.x) / (b.x - a.x)
	const E := 0.12
	var v := 1.0 / (1.0 - E)
	var eased := v * t * t / (2.0 * E) if t < E else (1.0 - v * (1.0 - t) * (1.0 - t) / (2.0 * E) if t > 1.0 - E else v * (t - E * 0.5))
	return lerpf(a.y, b.y, eased)

## The river's surface at z (it runs at the height of the way).
static func river_level(z: float) -> float:
	_ensure_zones(z)
	return altitude_of(z, altitude)

## Biomes at a point: Vector3(from, to, share of `to`).
static func zone_mix(z: float, warp: float, list: Array) -> Vector3:
	var u := z + warp * 160.0
	var i := list.size() - 1
	while i > 0 and (list[i] as Vector2).x > u: i -= 1
	var here: Vector2 = list[i]
	if i > 0 and u < here.x + BLEND:
		return Vector3((list[i - 1] as Vector2).y, here.y, smoothstep(here.x - BLEND, here.x + BLEND, u))
	if i + 1 < list.size() and u > (list[i + 1] as Vector2).x - BLEND:
		var next: Vector2 = list[i + 1]
		return Vector3(here.y, next.y, smoothstep(next.x - BLEND, next.x + BLEND, u))
	return Vector3(here.y, here.y, 0.0)

static func biome_at(x: float, z: float) -> int:
	_ensure_zones(z)
	var mix := zone_mix(z, noises.warp.get_noise_2d(x, z), zones)
	return int(mix.y) if mix.z > 0.5 else int(mix.x)

## One biome's relief (m, before massifs and the river valley).
static func _relief(biome: int, x: float, z: float, base: float, detail: float, ridge: float, hills: float, high: float) -> float:
	match biome:
		MEADOW: return 5.0 + 5.0 * base + 1.0 * detail + 6.0 * hills
		DESERT:
			# Long dune ridges across the wind (about 140 m apart), steeper on
			# their lee side, on a rolling plain.
			var across := x * 0.8 + z * 0.6
			var dune := 1.0 - absf(sin(across * 0.0225 + hills * 2.5))
			return 4.5 + 5.0 * base + 7.5 * dune * dune * (0.65 + 0.35 * detail) + 1.5 * hills
		# (Long slopes first; the ridges give the crests. Steeper would be walls.)
		MOUNTAINS: return (14.0 + 22.0 * base + 26.0 * ridge * ridge + 6.0 * hills) * (1.0 + minf(high / 1000.0, 0.7))
		BOG: return 2.6 + 1.8 * base + 0.7 * detail + 1.0 * hills
		FIELDS: return 7.0 + 6.5 * base + 0.25 * detail + 1.6 * hills
		STEPPE: return 6.0 + 5.5 * base + 1.2 * detail + 4.0 * hills
		TAIGA: return 9.0 + 10.0 * base + 2.0 * detail + 8.0 * hills
		FOOTHILLS: return (12.0 + 17.0 * base + 10.0 * ridge * ridge + 10.0 * hills) * (1.0 + minf(high / 1500.0, 0.45))
	return 6.5 + 7.0 * base + 1.5 * detail + 4.0 * hills

## The land before roads: the biomes' relief, massifs, the river valley. Pure
## function of its arguments (nz carries the noises and the zone list), so
## worker threads can call it with their own copies.
static func land(x: float, z: float, nz: Dictionary, r: PackedFloat32Array, peaks: Array) -> float:
	var base: float = nz.base.get_noise_2d(x, z)
	var detail: float = nz.detail.get_noise_2d(x, z)
	var ridge: float = 0.5 + 0.5 * nz.ridges.get_noise_2d(x, z)
	var hills: float = nz.hills.get_noise_2d(x, z)
	var w: float = nz.warp.get_noise_2d(x, z)
	var mix := zone_mix(z, w, nz.zones)
	var way := altitude_of(z, nz.altitude)
	var h := _relief(int(mix.x), x, z, base, detail, ridge, hills, way)
	if mix.z > 0.0: h = lerpf(h, _relief(int(mix.y), x, z, base, detail, ridge, hills, way), mix.z)
	var range_share := (1.0 - mix.z if int(mix.x) == MOUNTAINS else 0.0) + (mix.z if int(mix.y) == MOUNTAINS else 0.0)
	var desert := (1.0 - mix.z if int(mix.x) == DESERT else 0.0) + (mix.z if int(mix.y) == DESERT else 0.0)
	var tilled := (1.0 - mix.z if int(mix.x) == FIELDS else 0.0) + (mix.z if int(mix.y) == FIELDS else 0.0)
	var foothill := (1.0 - mix.z if int(mix.x) == FOOTHILLS else 0.0) + (mix.z if int(mix.y) == FOOTHILLS else 0.0)
	for m: Vector4 in peaks:
		var d := Vector2(x - m.x, z - m.y).length() / m.z
		if d < 2.2: h += exp(-d * d * 1.8) * (1.0 + 0.15 * w) * (m.w + 30.0 * ridge * ridge) * (1.0 - 0.7 * desert) * (1.0 - 0.8 * tilled)
	var rx := river_x_of(r, z)
	var dv := absf(x - rx)
	# (Wide among the mountains: their sides would be walls beside the road.)
	var valley_width := (48.0 + 18.0 * sin(z * 0.011 + r[3])) * (1.0 + 0.5 * desert + 2.2 * range_share + 1.2 * foothill)
	h = lerpf(1.0 + 0.06 * maxf(0.0, dv - 6.0), h, smoothstep(10.0, valley_width + 12.0 * w, dv))
	if h < 1.2: h = 1.2 - (1.2 - h) * 0.25
	# Valley sides climb away from the river among the mountains.
	h += (range_share + 0.5 * foothill) * 0.12 * maxf(0.0, dv - 140.0)
	# On a wall: an even slope (the way itself rises) for the zigzag, shut in on both sides
	# by rising ground. On the last wall that ground falls away towards the top instead.
	for span: Vector4 in nz.walls:
		if z <= span.x - 140.0 or z >= span.y + span.w: continue
		var share := smoothstep(span.x - 140.0, span.x, z) * (1.0 - smoothstep(span.y + span.w - 140.0, span.y + span.w, z))
		# (Where the sides begin wanders, and they begin gently: a straight crease up the slope
		# would show from the road as a line drawn across the mountain.)
		var dx := absf(x - span.z) - 34.0 * (0.5 + 0.5 * w) - 22.0 * (0.5 + 0.5 * hills)
		var rise := maxf(0.0, dx - WALL_HALF - 35.0)
		var side := 240.0 * (1.0 - exp(-rise * rise / (rise + 60.0) / 240.0)) * (0.75 + 0.5 * ridge)
		if span.w > 200.0:
			var fall := maxf(0.0, dx - WALL_HALF - 25.0)
			side = lerpf(side, -0.33 * fall * fall / (fall + 40.0), smoothstep(span.y - 500.0, span.y, z))
		h = lerpf(h, 3.0 + 2.5 * base + 0.5 * detail + side, share)
	var top: Vector3 = nz.summit
	var height := way + h
	# Nothing around stands higher than the summit: a cone over it, steep to the south and
	# the sides, gentle to the north, and never below the way itself.
	if top != Vector3.ZERO:
		var offset := Vector2(x - top.x, z - top.z)
		var off := offset.length()
		if off < 2600.0:
			var fall := lerpf(0.25, 0.06, smoothstep(-0.3, 0.7, offset.y / maxf(off, 1.0)))
			var cone := maxf(top.y + 6.0 - fall * maxf(0.0, off - 90.0), way + lerpf(4.0, 12.0, smoothstep(0.0, 400.0, top.y - way)))
			height = lerpf(minf(height, cone), height, smoothstep(2000.0, 2600.0, off))
	return height

## Share of desert at a point (0..1): there the river runs dry.
static func desert_share(z: float, warp: float, list: Array) -> float:
	var mix := zone_mix(z, warp, list)
	return (1.0 - mix.z if int(mix.x) == DESERT else 0.0) + (mix.z if int(mix.y) == DESERT else 0.0)

static func dry_at(z: float) -> bool:
	_ensure_zones(z)
	return desert_share(z, 0.0, zones) > 0.5 or river_off(z, river_gap) > 0.5

## Per landscape: temperature offset (°C, clear skies being 14), how often rain
## comes (1 normal), whether snow may fall.
const CLIMATES := [[0.0, 1.0, true], [4.0, 0.8, true], [24.0, 0.04, false], [-3.0, 1.0, true], [2.0, 1.6, true], [8.0, 0.6, false], [14.0, 0.2, false], [-3.0, 1.1, true], [-2.0, 1.0, true]]

## The climate for weather.gd: biome name, temperature offset (°C, falling
## 0.85 °C for every 100 m of height), how often rain comes (1 normal), whether
## snow may fall, and how high the car is.
static func climate_at(x: float, z: float, height: float = 0.0) -> Dictionary:
	_ensure_zones(z)
	var mix := zone_mix(z, noises.warp.get_noise_2d(x, z), zones)
	var a: Array = CLIMATES[int(mix.x)]
	var b: Array = CLIMATES[int(mix.y)]
	var dominant := int(mix.y) if mix.z > 0.5 else int(mix.x)
	# High up, a cloud layer the road climbs through, clearing near the tops.
	var mist := smoothstep(1000.0, 1120.0, height) * (1.0 - smoothstep(1240.0, 1360.0, height))
	return {"name": BIOME_NAMES[dominant], "biome": dominant, "temperature": lerpf(a[0], b[0], mix.z) - 0.0085 * maxf(0.0, height), "height": height, "mist": mist, "wet": lerpf(a[1], b[1], mix.z), "snow": a[2] if mix.z < 0.5 else b[2]}

# ---------------------------------------------------------------- fields

const LOOK_DRY := 1
## Field crops, in the ground map's look bits from LOOK_CROP (also highlands_ground.gdshader).
enum { CORN, BARLEY, WHEAT, RAPESEED, SUNFLOWER, FALLOW }
const LOOK_CROP := 2
const CROP_COLOURS := [Color(0.13, 0.25, 0.06), Color(0.74, 0.62, 0.26), Color(0.8, 0.6, 0.2), Color(0.78, 0.69, 0.1), Color(0.2, 0.28, 0.08), Color(0.2, 0.14, 0.09)]
const FIELD_SIZE := Vector2(130.0, 95.0)

## Which crop a field grows here (-1: the grassy headland between fields).
static func crop_cell(x: float, z: float, s: int) -> int:
	var u := x + 9.0 * sin(z * 0.017)
	var v := z + 9.0 * sin(x * 0.013)
	var cu := floorf(u / FIELD_SIZE.x)
	var cv := floorf(v / FIELD_SIZE.y)
	var fu := u - cu * FIELD_SIZE.x
	var fv := v - cv * FIELD_SIZE.y
	if fu < 3.5 or fu > FIELD_SIZE.x - 3.5 or fv < 3.5 or fv > FIELD_SIZE.y - 3.5: return -1
	var roll := hash([s, int(cu), int(cv)]) % 100
	if roll < 26: return CORN
	if roll < 46: return BARLEY
	if roll < 66: return WHEAT
	if roll < 84: return RAPESEED
	if roll < 94: return SUNFLOWER
	return FALLOW

## Whether a field can be worked at all: only a whole parcel within the fields
## landscape and on level ground is sown, or none of it. (Pure, so tiles agree.)
static func parcel_fit(x: float, z: float, nz: Dictionary, r: PackedFloat32Array, peaks: Array, fits: Dictionary) -> bool:
	var cell := Vector2i(floori((x + 9.0 * sin(z * 0.017)) / FIELD_SIZE.x), floori((z + 9.0 * sin(x * 0.013)) / FIELD_SIZE.y))
	if fits.has(cell): return fits[cell]
	var low := INF
	var high := -INF
	var fit := true
	for a in 3:
		for b in 3:
			var px := (cell.x + 0.15 + a * 0.35) * FIELD_SIZE.x
			var pz := (cell.y + 0.15 + b * 0.35) * FIELD_SIZE.y
			var mix := zone_mix(pz, nz.warp.get_noise_2d(px, pz), nz.zones)
			if (1.0 - mix.z if int(mix.x) == FIELDS else 0.0) + (mix.z if int(mix.y) == FIELDS else 0.0) < 0.9: fit = false
			var h := land(px, pz, nz, r, peaks)
			low = minf(low, h)
			high = maxf(high, h)
	fit = fit and high - low < PARCEL_ROUGHNESS
	fits[cell] = fit
	return fit

## Most a parcel may rise and fall (m) and still be ploughed.
const PARCEL_ROUGHNESS := 9.0

## The crop growing at a point of a made tile (-1: none).
static func crop_at(x: float, z: float) -> int:
	var value := ground[posmod(roundi(z / STEP), N) * N + posmod(roundi(x / STEP), N)]
	return (value >> 4) - LOOK_CROP if value & 15 == Surface.DIRT and value >> 4 >= LOOK_CROP else -1

## The way rows run in that field (0: along x, 1: along z).
static func field_axis(x: float, z: float) -> int:
	var u := x + 9.0 * sin(z * 0.017)
	var v := z + 9.0 * sin(x * 0.013)
	return (floori(u / FIELD_SIZE.x) + floori(v / FIELD_SIZE.y)) & 1

# ---------------------------------------------------------------- the far land

## The coarse land beyond the tiles (endless_world.gd): half its side, its grid.
const FAR_REACH := 2400.0
const FAR_GRID := 40.0

## A copy of what far_bake needs (main thread, under plan_lock), for around `at`.
static func far_job(at: Vector2) -> Dictionary:
	_ensure_zones(at.y + FAR_REACH)
	for z in range(floori(at.y - FAR_REACH), ceili(at.y + FAR_REACH), int(BAND)): _ensure_bands(z)
	var nz := noises.duplicate()
	nz.zones = zones.duplicate()
	nz.altitude = altitude.duplicate()
	nz.walls = wall_spans.duplicate()
	nz.summit = summit
	nz.gap = river_gap
	nz.field_seed = seed_value
	var area := Rect2(at - Vector2.ONE * FAR_REACH, Vector2.ONE * FAR_REACH * 2.0)
	return {"at": at, "nz": nz, "river": river, "peaks": _peaks_near(area)}

## The far land's arrays (worker thread): heights from land(), coloured by biome,
## height and steepness.
static func far_bake(job: Dictionary) -> Array:
	var count := roundi(FAR_REACH * 2.0 / FAR_GRID) + 1
	var at: Vector2 = job.at
	var nz: Dictionary = job.nz
	var grid := PackedFloat32Array()
	grid.resize(count * count)
	for j in count:
		for i in count:
			grid[j * count + i] = land(at.x - FAR_REACH + i * FAR_GRID, at.y - FAR_REACH + j * FAR_GRID, nz, job.river, job.peaks)
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var colours := PackedColorArray()
	vertices.resize(count * count)
	normals.resize(count * count)
	colours.resize(count * count)
	var indices := PackedInt32Array()
	indices.resize((count - 1) * (count - 1) * 6)
	var fits := {}
	var n := 0
	for j in count:
		for i in count:
			var x := at.x - FAR_REACH + i * FAR_GRID
			var z := at.y - FAR_REACH + j * FAR_GRID
			var h := grid[j * count + i]
			var dx := grid[j * count + mini(i + 1, count - 1)] - grid[j * count + maxi(i - 1, 0)]
			var dz := grid[mini(j + 1, count - 1) * count + i] - grid[maxi(j - 1, 0) * count + i]
			var normal := Vector3(-dx, FAR_GRID * 2.0, -dz).normalized()
			var mix := zone_mix(z, nz.warp.get_noise_2d(x, z), nz.zones)
			var colour := _far_colour(int(mix.x), h, normal.y).lerp(_far_colour(int(mix.y), h, normal.y), mix.z)
			var field := (1.0 - mix.z if int(mix.x) == FIELDS else 0.0) + (mix.z if int(mix.y) == FIELDS else 0.0)
			if field > 0.2:
				var crop := crop_cell(x, z, nz.field_seed)
				if crop >= 0 and parcel_fit(x, z, nz, job.river, job.peaks, fits): colour = colour.lerp(CROP_COLOURS[crop], field)
			var at_vertex := j * count + i
			# (A little under the real ground, which overlaps it at the seam.)
			vertices[at_vertex] = Vector3(x, h - 3.0, z)
			normals[at_vertex] = normal
			colours[at_vertex] = colour
			if i < count - 1 and j < count - 1:
				var a := at_vertex
				var b := a + 1
				var c := a + count
				var d := c + 1
				for v: int in [a, b, c, b, d, c]:
					indices[n] = v
					n += 1
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colours
	arrays[Mesh.ARRAY_INDEX] = indices
	return arrays

static func _far_colour(biome: int, h: float, up: float) -> Color:
	const FOREST_GREEN := Color(0.07, 0.12, 0.05)
	const ROCK_GREY := Color(0.24, 0.23, 0.22)
	const SNOW_WHITE := Color(0.82, 0.85, 0.9)
	var colour := FOREST_GREEN
	match biome:
		MEADOW: colour = Color(0.2, 0.28, 0.09)
		DESERT: colour = Color(0.58, 0.44, 0.26)
		BOG: colour = Color(0.15, 0.17, 0.08)
		FIELDS: colour = Color(0.27, 0.3, 0.1)
		STEPPE: colour = Color(0.46, 0.39, 0.18)
		TAIGA: colour = Color(0.045, 0.085, 0.05)
		FOOTHILLS: colour = FOREST_GREEN.lerp(Color(0.18, 0.25, 0.09), 0.6)
		MOUNTAINS: colour = FOREST_GREEN.lerp(ROCK_GREY, smoothstep(TREELINE, TREELINE + 170.0, h))
	colour = colour.lerp(ROCK_GREY, smoothstep(0.8, 0.62, up) * (0.3 if biome == DESERT else 1.0))
	return colour.lerp(SNOW_WHITE, smoothstep(SNOWLINE - 40.0, SNOWLINE + 30.0, h) * (0.45 if up < 0.6 else 1.0))

## Main-thread land query (massifs near z).
static func land_at(x: float, z: float) -> float:
	_ensure_zones(z)
	_ensure_bands(z)
	return land(x, z, noises, river, _peaks_near(Rect2(x - 1, z - 1, 2, 2)))

static func _slope(x: float, z: float) -> float:
	return Vector2(land_at(x + 3.0, z) - land_at(x - 3.0, z), land_at(x, z + 3.0) - land_at(x, z - 3.0)).length() / 6.0

static func _peaks_near(area: Rect2) -> Array:
	var result := []
	# (A massif's radius is under 190 m, so its reach under 420 m.)
	for b in range(floori((area.position.y - 420.0) / BAND), floori((area.end.y + 420.0) / BAND) + 1):
		for m: Vector4 in massifs_in.get(b, []):
			var reach := m.z * 2.2
			if m.x + reach > area.position.x and m.x - reach < area.end.x and m.y + reach > area.position.y and m.y - reach < area.end.y: result.append(m)
	return result

## Massifs (one or two per band, on either side of the river, now and then
## astride the road's way so it must climb a pass) and ponds. Everything here comes
## from the band's number alone, so the land is the same whatever the order it is
## asked for in: a saved run finds its world again.
static func _ensure_bands(z: float) -> void:
	for b in range(floori((z - 450.0) / BAND), floori((z + 450.0) / BAND) + 1):
		if bands.has(b): continue
		bands[b] = true
		# A pond's level comes from the land, massifs of the bands around included.
		for near in [b - 1, b, b + 1]: _ensure_massifs(near)
		var rng := _band_rng(b)
		_draw_massifs(rng, b)
		# Ponds: none in the desert, many in the bogs.
		_ensure_zones(b * BAND + BAND)
		for i in rng.randi_range(2, 5):
			var pz := b * BAND + rng.randf_range(100.0, BAND - 100.0)
			var px := river_x(pz) + (1.0 if rng.randf() < 0.5 else -1.0) * rng.randf_range(60.0, 220.0)
			var biome := int(zone_mix(pz, 0.0, zones).x)
			var radius := rng.randf_range(11.0, 16.0)
			if absf(pz) < 120.0 or biome == DESERT or biome == MOUNTAINS or (biome != BOG and i >= 2): continue
			ponds.append(Vector4(px, pz, radius, land(px, pz, noises, river, _peaks_near(Rect2(px - 1, pz - 1, 2, 2))) - 0.3))
			var in_band: Array = ponds_in.get_or_add(floori(pz / BAND), [])
			in_band.append(ponds.size() - 1)

static func _band_rng(b: int) -> RandomNumberGenerator:
	var rng := RandomNumberGenerator.new()
	rng.seed = hash([seed_value, b])
	return rng

static func _ensure_massifs(b: int) -> void:
	if massif_bands.has(b): return
	massif_bands[b] = true
	var drawn := _draw_massifs(_band_rng(b), b)
	massifs.append_array(drawn)
	massifs_in[b] = drawn

static func _draw_massifs(rng: RandomNumberGenerator, b: int) -> Array:
	var found := []
	for i in rng.randi_range(1, 2):
		var mz := b * BAND + rng.randf_range(200.0, BAND - 200.0)
		var side := 1.0 if rng.randf() < 0.5 else -1.0
		var across := rng.randf_range(40.0, 130.0) if rng.randf() < 0.5 else rng.randf_range(140.0, 260.0)
		var radius := rng.randf_range(130.0, 190.0)
		var high := rng.randf_range(16.0, 30.0)
		if absf(mz) < 300.0: continue
		found.append(Vector4(river_x(mz) + side * across, mz, radius, high))
	return found

# ---------------------------------------------------------------- roads

## Held while the planner changes the roads (it runs on a worker thread while
## driving; the streaming reads the roads only when it gets the lock).
static var plan_lock := Mutex.new()

static func plan_async(z: float) -> void:
	plan_lock.lock()
	plan_to(z)
	_refresh_lists()
	plan_lock.unlock()

## Plans the main road (and its side roads and sites) on to `z`.
static func plan_to(z: float) -> void:
	while planned_z < z:
		_plan_stretch()

static func _plan_stretch() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = hash([seed_value, stretches, 7])
	var from: Vector2 = road_controls[road_controls.size() - 1]
	for wall: Dictionary in walls:
		if not wall.built and from.y + 480.0 > wall.z0:
			_plan_wall(wall, from, rng)
			return
	var tz := from.y + rng.randf_range(200.0, 280.0)
	_ensure_bands(tz + 200.0)
	# Keep to this bank, or now and then cross the river (one ford).
	if stretches > 0 and rng.randf() < 0.35: bank = -bank
	var best := Vector2.ZERO
	var best_score := INF
	for i in 10:
		# (Among mountains the road keeps to the valley floor.)
		var p := Vector2(river_x(tz) + bank * (rng.randf_range(26.0, 60.0) if biome_at(river_x(tz), tz) in [MOUNTAINS, FOOTHILLS] else rng.randf_range(45.0, 210.0)), tz + rng.randf_range(-25.0, 25.0))
		var score := _slope(p.x, p.y) * 60.0 + (land_at(p.x, p.y) - river_level(p.y)) * 0.4 + (300.0 if pond_at(p.x, p.y) >= 0 else 0.0)
		if score < best_score:
			best_score = score
			best = p
	var route := _route(from, best, 0, _road_heading())
	var controls := _simplify(route)
	for i in range(1, controls.size()): road_controls.append(controls[i])
	_rebuild_main()
	# Along the way: buildings by the road, now and then one up a side road, a gas
	# station every few kilometres, supplies between, a camp every fifth stretch.
	var main: Dictionary = roads[0]
	var points: PackedVector2Array = main.points
	var along: PackedFloat32Array = main.along
	var at := _sample_near(points, from.lerp(best, 0.5))
	var here_biome := biome_at(points[at].x, points[at].y)
	# (Nothing leaves the road just below or above a wall.)
	var by_wall := wall_share(points[at].y + 180.0, wall_spans) > 0.0 or wall_share(points[at].y - 180.0, wall_spans) > 0.0
	if by_wall: pass
	elif points[at].y > next_station and here_biome != MOUNTAINS:
		if _roadside_site(rng, at, "gas_station", "Benzínka", here_biome):
			next_station = points[at].y + rng.randf_range(5000.0, 8000.0)
	elif stretches % 4 == 1:
		var start := points[at]
		var tangent := (points[mini(at + 3, points.size() - 1)] - points[maxi(at - 3, 0)]).normalized()
		var normal := Vector2(-tangent.y, tangent.x) * (1.0 if rng.randf() < 0.5 else -1.0)
		var target := start
		var target_score := INF
		for i in 12:
			var p := start + normal.rotated(rng.randf_range(-0.6, 0.6)) * rng.randf_range(55.0, 90.0)
			var same_bank := signf(p.x - river_x(p.y)) == signf(start.x - river_x(start.y)) and absf(p.x - river_x(p.y)) > 30.0
			var score := _slope(p.x, p.y) * 60.0 + (0.0 if same_bank else 300.0) + (300.0 if pond_at(p.x, p.y) >= 0 else 0.0)
			if score < target_score:
				target_score = score
				target = p
		var line := _route(start, target, 1)
		var names := ["Odbočka · les", "Odbočka · sklad", "Odbočka · lúka", "Odbočka · chata"]
		_add_side_road(_simplify(line), names[rng.randi() % names.size()], start)
		var side: Dictionary = roads[roads.size() - 1]
		var ending: PackedVector2Array = side.points
		var end: Vector2 = ending[ending.size() - 1]
		var biome := biome_at(end.x, end.y)
		var facing := (end - ending[maxi(0, ending.size() - 6)]).normalized()
		var levels: PackedFloat32Array = side.levels
		var model: String = (ROADSIDE[biome] as Array)[rng.randi() % (ROADSIDE[biome] as Array).size()]
		var lot: float = LOTS.get(model, 9.0)
		var spot := end + facing * (lot + 2.0)
		# (Where the road ends too near water, only supplies wait there.)
		if absf(spot.x - river_x(spot.y)) > lot + 20.0 and pond_at(spot.x, spot.y, lot + 6.0) < 0:
			sites.append({"name": _site_name(model, biome), "kind": "building", "biome": biome, "point": spot - facing * lot * 0.8, "at": spot, "facing": facing, "lot": lot, "level": levels[levels.size() - 1], "model": model, "along": along[at], "loot": _loot(rng, true, biome)})
		else:
			sites.append({"name": "Zásoby na odbočke", "kind": "supply", "biome": biome, "point": end, "along": along[at], "loot": _loot(rng, false, biome)})
	elif stretches % 4 == 3:
		var model: String = (ROADSIDE[here_biome] as Array)[rng.randi() % (ROADSIDE[here_biome] as Array).size()]
		_roadside_site(rng, at, model, _site_name(model, here_biome), here_biome)
	else:
		var p := points[mini(at + 20, points.size() - 1)]
		sites.append({"name": "Zásoby pri ceste", "kind": "supply", "biome": biome_at(p.x, p.y), "point": p, "along": along[mini(at + 20, points.size() - 1)], "loot": _loot(rng, false, biome_at(p.x, p.y))})
	if stretches % 5 == 4:
		var c := mini(at + 45, points.size() - 1)
		sites.append({"name": "Tábor", "kind": "camp", "biome": biome_at(points[c].x, points[c].y), "point": points[c], "along": along[c], "loot": []})
	stretches += 1
	planned_z = maxf(planned_z, best.y)

## A wall's road, all in one: from where the road is to the wall's foot, then leg by leg
## across the slope to a hairpin on each side, supplies every few legs, and on the last
## wall out along the top to the transmitter.
static func _plan_wall(wall: Dictionary, from: Vector2, rng: RandomNumberGenerator) -> void:
	_ensure_bands(wall.z1 + 200.0)
	wall.built = true
	var side := 1.0 if from.x >= wall.x else -1.0
	var entry := Vector2(wall.x + side * WALL_HALF, wall.z0)
	# Up to the wall's foot heading north, and round onto the first leg.
	var into := entry + Vector2(side * TURN_IN, -TURN_IN)
	var approach := _simplify(_route(from, into, 0, _road_heading()))
	# (The route's last grid cells would leave a kink just before the curve: it heads straight
	# for a point well below it, and comes up to it from there.)
	while approach.size() > 2 and (approach[approach.size() - 2] as Vector2).distance_to(into) < 40.0: approach.remove_at(approach.size() - 2)
	var below := into - Vector2(0.0, 22.0)
	if (approach[approach.size() - 2] as Vector2).distance_to(below) > 8.0: approach.insert(approach.size() - 1, below)
	for i in range(1, approach.size()): road_controls.append(approach[i])
	road_controls.append(entry + Vector2(side * TURN_IN * 0.7071, -TURN_IN * (1.0 - 0.7071)))
	road_controls.append(entry)
	var bends := PackedVector2Array()
	for leg: int in wall.legs:
		side = -side
		var radius := rng.randf_range(11.0, 17.0)
		var reach := WALL_HALF * rng.randf_range(0.76, 1.04)
		var centre := Vector2(wall.x + side * reach, wall.z0 + (leg + 1) * WALL_LEG - radius)
		var last: Vector2 = road_controls.back()
		var approach_end := centre + Vector2(0.0, -radius)
		var wave := rng.randf_range(7.0, 13.0) * (-1.0 if rng.randf() < 0.5 else 1.0)
		road_controls.append(last.lerp(approach_end, rng.randf_range(0.23, 0.34)) + Vector2(0.0, wave))
		road_controls.append(last.lerp(approach_end, rng.randf_range(0.64, 0.77)) - Vector2(0.0, wave))
		for degrees: float in [-100.0, -60.0, -20.0, 20.0, 60.0, 100.0]:
			var turn := deg_to_rad(degrees)
			road_controls.append(centre + Vector2(side * cos(turn), sin(turn)) * radius)
		bends.append(centre)
	var last_wall: bool = wall == walls[walls.size() - 1]
	var out := Vector2(wall.x - side * (SUMMIT_LOT - 4.0), summit.z) if last_wall else Vector2(wall.x - side * WALL_HALF * 0.4, wall.z1 + 30.0)
	# Round from the last leg onto a heading north, for what comes after.
	var back := -side
	road_controls.append(out + Vector2(-back * TURN_IN, -TURN_IN))
	road_controls.append(out + Vector2(-back * TURN_IN * (1.0 - 0.7071), -TURN_IN * 0.7071))
	road_controls.append(out)
	road_controls.append(out + Vector2(0.0, 70.0 if last_wall else 24.0))
	wall.entry = entry
	wall.exit = out
	var old_size: int = (roads[0].points as PackedVector2Array).size()
	_rebuild_main()
	var main: Dictionary = roads[0]
	var points: PackedVector2Array = main.points
	var along: PackedFloat32Array = main.along
	var levels: PackedFloat32Array = main.levels
	for leg in range(3, bends.size() - 1, 5):
		var k := _nearest_from(points, bends[leg] - Vector2(signf(bends[leg].x - wall.x) * WALL_HALF * 0.8, 0.0), old_size - 150)
		sites.append({"name": "Zásoby pri serpentíne", "kind": "supply", "biome": MOUNTAINS, "point": points[k], "along": along[k], "loot": _loot(rng, false, MOUNTAINS)})
	if last_wall:
		var k := _nearest_from(points, out, old_size - 150)
		var at := Vector2(summit.x, summit.z)
		sites.append({"name": "Vysielač", "kind": "summit", "biome": MOUNTAINS, "point": at, "at": at, "facing": Vector2(0.0, 1.0), "lot": SUMMIT_LOT, "level": levels[k], "model": "", "along": along[k], "loot": []})
	stretches += 1
	var end: Vector2 = road_controls[road_controls.size() - 1]
	planned_z = maxf(planned_z, end.y)

## Which way the road's end is heading.
static func _road_heading() -> Vector2:
	if road_controls.size() < 2: return Vector2.ZERO
	return (road_controls[road_controls.size() - 1] as Vector2) - (road_controls[road_controls.size() - 2] as Vector2)

static func _nearest_from(points: PackedVector2Array, p: Vector2, from: int) -> int:
	var best := maxi(0, from)
	for k in range(best, points.size()):
		if points[k].distance_squared_to(p) < points[best].distance_squared_to(p): best = k
	return best

## A building beside the main road at sample k, on a levelled lot: on whichever
## side is clear of the road's bends, the river and ponds. False if neither is.
static func _roadside_site(rng: RandomNumberGenerator, k: int, model: String, title: String, biome: int) -> bool:
	var main: Dictionary = roads[0]
	var points: PackedVector2Array = main.points
	var levels: PackedFloat32Array = main.levels
	var tangent := (points[mini(k + 4, points.size() - 1)] - points[maxi(k - 4, 0)]).normalized()
	var lot: float = LOTS.get(model, 9.0)
	var first := 1.0 if rng.randf() < 0.5 else -1.0
	for side: float in [first, -first]:
		var away := Vector2(-tangent.y, tangent.x) * side
		var offset: float = lot + (main.half as float) + 1.5
		var spot := points[k] + away * offset
		var clear := absf(spot.x - river_x(spot.y)) > lot + 20.0 and pond_at(spot.x, spot.y, lot + 6.0) < 0
		for j in range(maxi(0, k - 50), mini(points.size(), k + 50)):
			if points[j].distance_to(spot) < lot + 2.5: clear = false
		if not clear: continue
		# (The station's forecourt faces the road, the rest stand a little back.)
		sites.append({"name": title, "kind": "building", "biome": biome, "point": spot - away * lot * 0.8, "at": spot, "facing": away, "lot": lot, "level": levels[k], "model": model, "along": (main.along as PackedFloat32Array)[k], "loot": _loot(rng, true, biome, model == "gas_station")})
		return true
	return false

## Lot radius per model (m) and the models of each landscape ("": hand-built, see
## buildings.gd).
const LOTS := {"gas_station": 13.0, "barn01": 10.0, "house02": 10.0, "house01": 8.0, "barn03": 7.0, "house03": 6.0, "barrack02": 6.0, "barrack01": 5.0, "barrack03": 5.0, "barrack04": 7.0}
const ROADSIDE := [["house03", "barrack04", ""], ["house01", "barn03", "house02"], ["barrack01", "barrack03", "barrack04", "house03"], [""], [""], ["barn01", "barn03", "house01", "house02"], ["barrack04", "house03", "barn03", "barrack02"], ["", "house03"], ["house01", "house03", ""]]
const MODEL_NAMES := {"barn01": "Stodola", "barn03": "Sýpka", "house01": "Dom", "house02": "Statok", "house03": "Chalupa", "barrack01": "Sklad", "barrack02": "Sklad", "barrack03": "Sklad", "barrack04": "Dielňa"}

static func _site_name(model: String, biome: int) -> String:
	return MODEL_NAMES[model] if MODEL_NAMES.has(model) else BUILDING_NAMES[biome]

## What a hand-built store is called in each landscape.
const BUILDING_NAMES := ["Horáreň", "Stodola", "Hlinený dom", "Horská chata", "Rybárska chatrč", "Stodola", "Sklad", "Lesnícka chata", "Salaš"]
## Tyre sets found in each landscape (see tyres.gd).
const TYRES_HERE := [["offroad", "mud"], ["offroad", "winter"], ["sand", "offroad"], ["winter", "chains"], ["mud", "offroad"], ["offroad", "mud"], ["sand", "offroad"], ["winter", "offroad"], ["winter", "chains"]]
static var next_station := 900.0

## A site's loot: [item, count] (item: fuel, repair, oil, coolant, or a tyre set
## "tyre:kind"). Buildings hold more, and often a set of tyres fit for the land.
static func _loot(rng: RandomNumberGenerator, store: bool, biome: int, station: bool = false) -> Array:
	var items := [["fuel", rng.randi_range(25, 40) if station else (rng.randi_range(5, 10) if store else rng.randi_range(2, 5))]]
	if rng.randf() < (0.7 if store else 0.2): items.append(["repair", rng.randi_range(1, 2)])
	if rng.randf() < 0.45: items.append(["oil", 1])
	if rng.randf() < 0.55: items.append(["coolant", rng.randi_range(1, 2)])
	if store and rng.randf() < 0.55:
		var here: Array = TYRES_HERE[biome]
		var supply := str(here[rng.randi() % here.size()])
		items.append(["chains" if supply == "chains" else "tyre:" + supply, 1])
	return items

static func _sample_near(points: PackedVector2Array, p: Vector2) -> int:
	var best := 0
	for k in range(maxi(0, points.size() - 400), points.size()):
		if points[k].distance_squared_to(p) < points[best].distance_squared_to(p): best = k
	return best

## The main road's polyline from all its control points (Chaikin's corner
## cutting, three rounds, resampled every 2 m): earlier stretches come out
## the same, only the newest part moves. Its levels are recomputed from 300 m
## behind the old end on (all of it the first time), held where they were.
static func _rebuild_main() -> void:
	var main: Dictionary = roads[0]
	var old_points: PackedVector2Array = main.points
	var old_levels: PackedFloat32Array = main.levels
	var old_along: PackedFloat32Array = main.along
	var line := _grown_line(old_points)
	var keep := maxi(0, old_points.size() - 150)
	# (What lies before the end of the old line's settled part is the same as before.)
	var settled := mini(settled_samples, old_along.size())
	var arc := old_along.slice(0, settled)
	if arc.is_empty(): arc.append(0.0)
	arc.resize(line.size())
	for k in range(maxi(settled, 1), line.size()): arc[k] = arc[k - 1] + line[k].distance_to(line[k - 1])
	# Where a new wall's road begins and ends along the whole road.
	for wall: Dictionary in walls:
		if wall.built and not wall.has("s1"):
			wall.s0 = arc[_nearest_from(line, wall.entry, keep)]
			wall.s1 = arc[_nearest_from(line, wall.exit, keep)]
	var levels := _levels(line, keep, old_levels, 0, arc)
	# Re-index the changed part (the old line's, before it goes).
	_unindex(0, keep, old_points)
	main.points = line
	main.levels = levels
	main.along = arc
	_index(0, keep)

## How many samples of the line stayed as they were in the last growth.
static var settled_samples := 0

## The line through all the controls, smoothing only its end: corner cutting and averaging
## reach a few dozen metres, so the new end is joined on where the old line has settled,
## SETTLE metres back from its last control (see _smooth).
static func _grown_line(old: PackedVector2Array) -> PackedVector2Array:
	const SETTLE := 70.0
	var count := road_controls.size()
	var last := -1
	if old.size() > 80:
		for i in range(count - 1, -1, -1):
			if (road_controls[i] as Vector2).is_equal_approx(old_end_control):
				last = i
				break
	old_end_control = road_controls[count - 1]
	# No line yet, or not long enough to join: all of it.
	if last < 0:
		settled_samples = 0
		return _smooth(road_controls)
	var back := 0.0
	var join := last
	while join > 0 and back < SETTLE:
		back += (road_controls[join] as Vector2).distance_to(road_controls[join - 1])
		join -= 1
	var from := join
	while from > 0 and back < SETTLE * 2.0:
		back += (road_controls[from] as Vector2).distance_to(road_controls[from - 1])
		from -= 1
	if back < SETTLE * 2.0:
		settled_samples = 0
		return _smooth(road_controls)
	var target: Vector2 = road_controls[join]
	var end := _nearest_from(old, target, old.size() - 150)
	var tail := _smooth(road_controls.slice(from))
	var start := _nearest_from(tail, old[end], 0)
	# (They should run together here; where they do not, the whole line is made.)
	if start < 1 or start + 2 >= tail.size() or minf(Geometry2D.get_closest_point_to_segment(old[end], tail[start - 1], tail[start]).distance_to(old[end]), Geometry2D.get_closest_point_to_segment(old[end], tail[start], tail[start + 1]).distance_to(old[end])) > 0.4:
		settled_samples = 0
		return _smooth(road_controls)
	# From the next sample a full step on, the new end takes over.
	if tail[start + 1].distance_to(old[end]) < 1.0: start += 1
	var line := old.slice(0, end + 1)
	line.append_array(tail.slice(start + 1))
	settled_samples = end + 1
	return line

## The last control when the line was last made.
static var old_end_control := Vector2(INF, INF)

static func _add_side_road(controls: Array, title: String, junction: Vector2) -> void:
	var line := _smooth(controls)
	var road := {"name": title, "kind": Surface.DIRT, "half": 2.6, "points": line, "levels": PackedFloat32Array(), "along": _arc(line), "ford": 0.4}
	roads.append(road)
	road.levels = _levels(line, 0, PackedFloat32Array(), roads.size() - 1)
	_index(roads.size() - 1, 0)

static func _arc(line: PackedVector2Array) -> PackedFloat32Array:
	var along := PackedFloat32Array([0.0])
	for k in range(1, line.size()): along.append(along[k - 1] + line[k].distance_to(line[k - 1]))
	return along

## Control points → road polyline: Chaikin's corner cutting (three rounds),
## then corners opened further by averaging each 2 m sample with its
## neighbours over ±10 m (the grid's 45° steps would leave 90° bends a few
## metres round), resampled every 2 m. Everything is local, so extending the
## road changes only its last stretch.
static func _smooth(points: Array) -> PackedVector2Array:
	var smooth := PackedVector2Array(points)
	for cuts in 3:
		var cut := PackedVector2Array([smooth[0]])
		for i in smooth.size() - 1:
			cut.append(smooth[i].lerp(smooth[i + 1], 0.25))
			cut.append(smooth[i].lerp(smooth[i + 1], 0.75))
		cut.append(smooth[smooth.size() - 1])
		smooth = cut
	var line := _resample(smooth)
	for passes in 2:
		var rounded := line.duplicate()
		for k in range(1, line.size() - 1):
			var reach := mini(5, mini(k, line.size() - 1 - k))
			var total := Vector2.ZERO
			for o in range(-reach, reach + 1): total += line[k + o]
			rounded[k] = total / (2 * reach + 1)
		line = rounded
	return _resample(line)

static func _resample(points: PackedVector2Array) -> PackedVector2Array:
	var line := PackedVector2Array([points[0]])
	var carry := 0.0
	for i in points.size() - 1:
		var a := points[i]
		var b := points[i + 1]
		var length := a.distance_to(b)
		var at := 2.0 - carry
		while at < length:
			line.append(a.lerp(b, at / length))
			at += 2.0
		carry = length - (at - 2.0)
	return line

## Road levels (2 m samples) from `from` on: the smoothed land, down to fords
## and pond shores, a side road meeting the main road at its level, the grade
## limited, crests and dips rounded. Samples before `from` keep `held`.
static func _levels(line: PackedVector2Array, from: int, held: PackedFloat32Array, road: int, arc := PackedFloat32Array()) -> PackedFloat32Array:
	var count := line.size()
	var raw := PackedFloat32Array()
	raw.resize(count)
	for k in range(maxi(0, from - 8), count): raw[k] = land_at(line[k].x, line[k].y)
	var centre := held.slice(0, mini(from, held.size()))
	var first := centre.size()
	centre.resize(count)
	for k in range(first, count):
		var total := 0.0
		var weight := 0.0
		for o in range(-6, 7):
			var j := k + o
			if j < maxi(0, from - 8) or j >= count: continue
			total += raw[j]
			weight += 1.0
		centre[k] = total / weight
	# The rollers are laid over the grade-limited road: this works without them.
	if road == 0:
		for k in range(maxi(0, from - 12), mini(from, count)): centre[k] -= _hump(arc[k], line[k])
	var start := maxi(from, 1)
	for c in _river_crossings(line, start):
		for k in range(start, count):
			var off := absf(float(k - c)) * 2.0
			if off < 120.0: centre[k] = minf(centre[k], river_level(line[c].y) + 0.9 + maxf(0.0, off - 8.0) * 0.12)
	var lowest := INF
	var highest := -INF
	for k in range(start, count):
		lowest = minf(lowest, line[k].y)
		highest = maxf(highest, line[k].y)
	for pond: Vector4 in ponds:
		if pond.y < lowest - pond.z - 40.0 or pond.y > highest + pond.z + 40.0: continue
		for k in range(start, count):
			var d := line[k].distance_to(Vector2(pond.x, pond.y))
			if d < pond.z + 40.0: centre[k] = minf(centre[k], pond.w + 0.3 + maxf(0.0, d - pond.z) * 0.12)
	# On a wall the road climbs evenly, whatever the ground does (it follows the road).
	if road == 0:
		for wall: Dictionary in walls:
			if not wall.has("s1"): continue
			for k in range(start, count):
				if arc[k] < wall.s0 or arc[k] > wall.s1: continue
				centre[k] = altitude_of(lerpf(wall.z0, wall.z1, (arc[k] - wall.s0) / (wall.s1 - wall.s0)), altitude) + 3.0
	# A side road starts on the main road at its level.
	var pinned := 0
	if road > 0:
		var main: Dictionary = roads[0]
		centre[0] = (main.levels as PackedFloat32Array)[_sample_near(main.points, line[0])]
		pinned = 1
	for smoothing in 2:
		var rounded := centre.duplicate()
		for k in range(maxi(start, pinned + 2), count - 1):
			var total := 0.0
			var weight := 0.0
			for o in range(-4, 5):
				var j := k + o
				if j < 0 or j >= count: continue
				total += centre[j]
				weight += 1.0
			rounded[k] = total / weight
		centre = rounded
	# Grade limit, both ways so a long climb is shared out (what lies before
	# `from` stays).
	for k in range(count - 2, maxi(start, pinned) - 1, -1):
		var ds := line[k].distance_to(line[k + 1]) * ROAD_GRADE
		centre[k] = clampf(centre[k], centre[k + 1] - ds, centre[k + 1] + ds)
	for k in range(maxi(start, pinned), count):
		var ds := line[k].distance_to(line[k - 1]) * ROAD_GRADE
		centre[k] = clampf(centre[k], centre[k - 1] - ds, centre[k - 1] + ds)
	if road == 0:
		for k in range(maxi(0, from - 12), count): centre[k] += _hump(arc[k], line[k])
	return centre

## Rollers in the main road, from the first few hundred metres on: a crest every few
## hundred metres, up to about 1.5 m high and 18 to 30 m long, to take with care or
## to jump (a little air from about 50 km/h). Lower in marsh and mountains.
const HUMP_CELL := 170.0
const HUMP_SCALE := [0.9, 1.1, 1.2, 0.5, 0.5, 1.0, 1.2, 0.9, 1.0]

static func _hump(s: float, p: Vector2) -> float:
	if s < 170.0: return 0.0
	var cell := floori(s / HUMP_CELL)
	var roll := hash([seed_value, cell, 31])
	if roll % 100 >= 58: return 0.0
	var at := (cell + 0.3 + 0.4 * float((roll >> 8) % 100) / 100.0) * HUMP_CELL
	var half := 9.0 + 6.0 * float((roll >> 16) % 100) / 100.0
	var d := absf(s - at) / half
	if d >= 1.0: return 0.0
	var bump := cos(d * PI * 0.5)
	var high := 0.7 + 0.8 * float((roll >> 4) % 100) / 100.0
	# (Not at the fords.)
	var dry := smoothstep(20.0, 40.0, absf(p.x - river_x(p.y)))
	return high * bump * bump * dry * float(HUMP_SCALE[biome_at(p.x, p.y)]) * (1.0 - wall_share(p.y, wall_spans))

## Fords of the line near or past sample `from` (a ford lowers 60 samples each way).
static func _river_crossings(line: PackedVector2Array, from: int) -> Array[int]:
	var result: Array[int] = []
	for c in range(maxi(0, from - 60), line.size() - 1):
		if signf(line[c].x - river_x(line[c].y)) != signf(line[c + 1].x - river_x(line[c + 1].y)) and river_off(line[c].y, river_gap) < 0.5: result.append(c)
	return result

static func _index(road: int, from: int) -> void:
	var line: PackedVector2Array = roads[road].points
	for k in range(from, line.size()):
		var key := Vector2i(floori(line[k].x / 16.0), floori(line[k].y / 16.0))
		if not road_index.has(key): road_index[key] = PackedInt32Array()
		road_index[key].append_array([road, k])

static func _unindex(road: int, from: int, line: PackedVector2Array) -> void:
	var seen := {}
	for k in range(from, line.size()): seen[Vector2i(floori(line[k].x / 16.0), floori(line[k].y / 16.0))] = true
	for key: Vector2i in seen:
		var entries: PackedInt32Array = road_index.get(key, PackedInt32Array())
		var kept := PackedInt32Array()
		for e in range(0, entries.size(), 2):
			if entries[e] == road and entries[e + 1] >= from: continue
			kept.append_array([entries[e], entries[e + 1]])
		road_index[key] = kept

## Road samples (road, sample) within `reach` of an area.
static func roads_near(area: Rect2) -> Dictionary:
	var found := {}
	for cz in range(floori(area.position.y / 16.0) - 1, floori(area.end.y / 16.0) + 2):
		for cx in range(floori(area.position.x / 16.0) - 1, floori(area.end.x / 16.0) + 2):
			var entries = road_index.get(Vector2i(cx, cz))
			if entries == null: continue
			for e in range(0, entries.size(), 2):
				if not found.has(entries[e]): found[entries[e]] = []
				found[entries[e]].append(entries[e + 1])
	return found

# ---------------------------------------------------------------- routing

## A* over an 8 m grid around the two points (with room to wander), as in
## generated_terrain.gd: grade limited (switchbacks), fords cost a detour,
## ponds blocked, turns of at most 45° per cell; a side road (`kind` 1) is
## also kept off the main road.
static func _route(from: Vector2, to: Vector2, kind: int, heading := Vector2.ZERO) -> PackedVector2Array:
	var margin := 200.0
	var origin := Vector2(minf(from.x, to.x) - margin, minf(from.y, to.y) - margin)
	var w := ceili((absf(to.x - from.x) + 2.0 * margin) / ROUTE) + 1
	var h := ceili((absf(to.y - from.y) + 2.0 * margin) / ROUTE) + 1
	var grid := {"origin": origin, "w": w, "h": h, "height": PackedFloat32Array(), "blocked": PackedByteArray(), "water": PackedByteArray()}
	var heights_g: PackedFloat32Array = grid.height
	var blocked: PackedByteArray = grid.blocked
	var water: PackedByteArray = grid.water
	heights_g.resize(w * h)
	blocked.resize(w * h)
	water.resize(w * h)
	_ensure_bands(origin.y + h * ROUTE)
	_ensure_zones(origin.y + h * ROUTE)
	var peaks := _peaks_near(Rect2(origin, Vector2(w, h) * ROUTE))
	var main: Dictionary = roads[0]
	for iz in h:
		var z := origin.y + iz * ROUTE
		var rx := river_x(z)
		for ix in w:
			var x := origin.x + ix * ROUTE
			var i := iz * w + ix
			heights_g[i] = land(x, z, noises, river, peaks)
			var dv := absf(x - rx)
			water[i] = 2 if dv < 10.0 else (1 if dv < 20.0 else 0)
			if pond_at(x, z, 10.0) >= 0: blocked[i] = 1
	# Stay off roads already laid (the main road's earlier stretches; for a
	# side road, the main road) except where this one leaves it.
	var near := roads_near(Rect2(origin, Vector2(w, h) * ROUTE))
	if near.has(0):
		var points: PackedVector2Array = main.points
		for k: int in near[0]:
			var p := points[k]
			if p.distance_to(from) < 30.0: continue
			var c := Vector2i(roundi((p.x - origin.x) / ROUTE), roundi((p.y - origin.y) / ROUTE))
			for dz in range(-2, 3):
				for dx in range(-2, 3):
					var cell := c + Vector2i(dx, dz)
					if cell.x >= 0 and cell.y >= 0 and cell.x < w and cell.y < h: blocked[cell.y * w + cell.x] = maxi(blocked[cell.y * w + cell.x], 2)
	var start := _cell(grid, from)
	var goal := _cell(grid, to)
	# Going on the way the road already goes, so that no corner is left where stretches meet.
	var way := Vector2i.ZERO
	if heading != Vector2.ZERO:
		var nearest := -2.0
		for d: Vector2i in DIRS:
			var along := Vector2(d).normalized().dot(heading.normalized())
			if along > nearest:
				nearest = along
				way = d
	for attempt in [[ROUTE_GRADE, 0.5, way], [0.18, 0.5, way], [0.26, 0.5, way], [10.0, 0.5, way], [10.0, -0.1, way], [10.0, -0.1, Vector2i.ZERO]]:
		var found := _astar(grid, start, goal, attempt[0], attempt[1], attempt[2])
		if not found.is_empty():
			found[0] = from
			found[found.size() - 1] = to
			return found
	return PackedVector2Array([from, to])

static func _cell(grid: Dictionary, p: Vector2) -> int:
	var o: Vector2 = grid.origin
	return clampi(roundi((p.y - o.y) / ROUTE), 0, grid.h - 1) * grid.w + clampi(roundi((p.x - o.x) / ROUTE), 0, grid.w - 1)

const DIRS := [Vector2i(1, 0), Vector2i(-1, 0), Vector2i(0, 1), Vector2i(0, -1), Vector2i(1, 1), Vector2i(1, -1), Vector2i(-1, 1), Vector2i(-1, -1)]

static func _astar(grid: Dictionary, start: int, goal: int, limit: float, sharpest: float, way := Vector2i.ZERO) -> PackedVector2Array:
	var w: int = grid.w
	var h: int = grid.h
	var origin: Vector2 = grid.origin
	var heights_g: PackedFloat32Array = grid.height
	var blocked: PackedByteArray = grid.blocked
	var water: PackedByteArray = grid.water
	var cost := PackedFloat32Array()
	cost.resize(w * h)
	cost.fill(INF)
	var came := PackedInt32Array()
	came.resize(w * h)
	came.fill(-1)
	var closed := PackedByteArray()
	closed.resize(w * h)
	var heap_f := PackedFloat32Array()
	var heap_n := PackedInt32Array()
	var goal_p := Vector2(goal % w, goal / w) * ROUTE
	cost[start] = 0.0
	_push(heap_f, heap_n, Vector2(start % w, start / w).distance_to(Vector2(goal % w, goal / w)) * ROUTE, start)
	while not heap_n.is_empty():
		var n := _pop(heap_f, heap_n)
		if closed[n] == 1: continue
		closed[n] = 1
		if n == goal: break
		var nx := n % w
		var nz := n / w
		var from := came[n]
		var arrived := Vector2i(nx - from % w, nz - from / w) if from != -1 else way
		for d: Vector2i in DIRS:
			var mx := nx + d.x
			var mz := nz + d.y
			if mx < 0 or mz < 0 or mx >= w or mz >= h: continue
			var m := mz * w + mx
			if closed[m] == 1 or (blocked[m] == 1 and m != goal): continue
			var length := ROUTE * (1.4142 if d.x != 0 and d.y != 0 else 1.0)
			var grade := absf(heights_g[m] - heights_g[n]) / length
			if grade > limit: continue
			var step := length * (1.0 + 30.0 * grade * grade)
			if water[m] == 2: step += 160.0
			elif water[m] == 1: step += length * 0.8
			if blocked[m] == 2: step += length * 6.0
			if arrived != Vector2i.ZERO:
				var turn := Vector2(arrived).normalized().dot(Vector2(d).normalized())
				if turn < sharpest - 0.01: continue
				step += 0.0 if turn > 0.9 else (3.0 if turn > 0.5 else 45.0)
			var total := cost[n] + step
			if total < cost[m]:
				cost[m] = total
				came[m] = n
				_push(heap_f, heap_n, total + (Vector2(mx, mz) * ROUTE).distance_to(goal_p), m)
	if came[goal] == -1 and goal != start: return PackedVector2Array()
	var reversed := PackedVector2Array()
	var at := goal
	while at != -1:
		reversed.append(origin + Vector2(at % w, at / w) * ROUTE)
		at = came[at]
	reversed.reverse()
	return reversed

static func _push(f: PackedFloat32Array, n: PackedInt32Array, value: float, node: int) -> void:
	f.append(value)
	n.append(node)
	var i := f.size() - 1
	while i > 0:
		var parent := (i - 1) / 2
		if f[parent] <= f[i]: break
		var tf := f[parent]
		f[parent] = f[i]
		f[i] = tf
		var tn := n[parent]
		n[parent] = n[i]
		n[i] = tn
		i = parent

static func _pop(f: PackedFloat32Array, n: PackedInt32Array) -> int:
	var top := n[0]
	var last := f.size() - 1
	f[0] = f[last]
	n[0] = n[last]
	f.resize(last)
	n.resize(last)
	var i := 0
	while true:
		var l := i * 2 + 1
		var r := l + 1
		var smallest := i
		if l < last and f[l] < f[smallest]: smallest = l
		if r < last and f[r] < f[smallest]: smallest = r
		if smallest == i: break
		var tf := f[smallest]
		f[smallest] = f[i]
		f[i] = tf
		var tn := n[smallest]
		n[smallest] = n[i]
		n[i] = tn
		i = smallest
	return top

static func _simplify(line: PackedVector2Array) -> Array:
	if line.size() < 3: return Array(line)
	var keep := PackedByteArray()
	keep.resize(line.size())
	keep[0] = 1
	keep[line.size() - 1] = 1
	var stack := [Vector2i(0, line.size() - 1)]
	while not stack.is_empty():
		var span: Vector2i = stack.pop_back()
		var worst := 0.0
		var at := -1
		for k in range(span.x + 1, span.y):
			var d := Geometry2D.get_closest_point_to_segment(line[k], line[span.x], line[span.y]).distance_to(line[k])
			if d > worst:
				worst = d
				at = k
		if worst > 2.0:
			keep[at] = 1
			stack.append(Vector2i(span.x, at))
			stack.append(Vector2i(at, span.y))
	var points: Array = []
	for k in line.size():
		if keep[k] == 0: continue
		if not points.is_empty() and k != line.size() - 1 and line[k].distance_to(points[-1]) < 7.0: continue
		points.append(line[k])
	return points

# ---------------------------------------------------------------- worn ruts

## Lays the old wheel ruts of the unpaved roads into the soil field (0.25 m,
## where the car's own ruts go, so they look and drive the same): for the road
## samples in `window` not in `known`. Each road has two ruts wandering about its
## wheel tracks, now deep, now nearly gone, sometimes a third braided beside
## them, with a low ridge of pushed-up soil; rougher the further the way goes.
static func stamp_worn(soil, window: Rect2, known: Rect2) -> void:
	var near := roads_near(window)
	for r: int in near:
		var road: Dictionary = roads[r]
		var line: PackedVector2Array = road.points
		var along: PackedFloat32Array = road.along
		var half: float = road.half
		var phase := float(r) * 1.7 + float(seed_value % 97)
		for k: int in near[r]:
			if k + 1 >= line.size(): continue
			var a := line[k]
			if not window.has_point(a) or known.has_point(a): continue
			var kind := surface_at(a.x, a.y)
			if kind in [Surface.ROAD, Surface.WET_ROAD, Surface.ROCK, Surface.WATER, Surface.ICE]: continue
			var b := line[k + 1]
			var normal := (b - a).normalized().orthogonal()
			var biome := biome_at(a.x, a.y)
			var hard := clampf(0.25 + a.y / 40000.0, 0.25, 1.0) * (1.35 if biome == BOG else (0.75 if biome == DESERT else 1.0))
			for sub in 8:
				var s: float = along[k] + sub * 0.25
				var p := a.lerp(b, sub / 8.0)
				var wander_a := sin(s * 0.037 + phase) * 0.6 + sin(s * 0.093 + phase * 2.0) * 0.3 + sin(s * 0.23 + phase * 3.0) * 0.1
				var wander_b := sin(s * 0.031 + phase * 5.0) * 0.6 + sin(s * 0.087 + phase * 7.0) * 0.3 + sin(s * 0.27 + phase) * 0.1
				var patch := 0.5 + 0.5 * sin(s * 0.019 + phase * 4.0) * sin(s * 0.057 + phase)
				var depth := (0.07 + 0.17 * hard) * (0.3 + 0.7 * patch)
				var width := 0.34 + 0.1 * sin(s * 0.13 + phase)
				var left := -0.85 + 0.4 * wander_a
				var right := 0.85 + 0.4 * wander_b
				var third := 1.75 * signf(sin(s * 0.011 + phase)) + 0.3 * wander_a
				var braided := maxf(0.0, sin(s * 0.016 + phase * 6.0) - 0.2)
				var lat := -half
				while lat <= half:
					var d := depth * maxf(maxf(_rut(lat - left, width), _rut(lat - right, width)), 0.8 * braided * _rut(lat - third, width))
					var ridge := 0.16 * depth * (_rut(lat - left + 2.2 * width, width) + _rut(lat - right - 2.2 * width, width))
					soil.lay_worn(p + normal * lat, d - ridge * (1.0 - minf(d / maxf(depth, 0.01) * 3.0, 1.0)))
					lat += 0.25
			if k % 4 == 0: soil.touch_worn(a, half + 1.0)

static func _rut(offset: float, width: float) -> float:
	var u := offset / width
	return exp(-u * u)

# ---------------------------------------------------------------- tiles

## Everything a worker thread needs to make tile (tx, tz): copies, so the
## planner can go on meanwhile.
static func tile_job(tile: Vector2i) -> Dictionary:
	var area := Rect2(tile.x * TILE_M, tile.y * TILE_M, TILE_M, TILE_M)
	var reach := area.grow(22.0)
	_ensure_bands(area.end.y)
	var pieces := []
	var near := roads_near(reach)
	for r: int in near:
		var road: Dictionary = roads[r]
		var line: PackedVector2Array = road.points
		var samples: Array = near[r]
		samples.sort()
		# Runs of consecutive samples (plus one either side for the segments).
		var run_points := PackedVector2Array()
		var run_levels := PackedFloat32Array()
		var run_along := PackedFloat32Array()
		var last := -10
		for k: int in samples:
			for j in range(maxi(maxi(k - 1, last + 1), 0), mini(k + 2, line.size())):
				if j > last + 1 and not run_points.is_empty():
					pieces.append({"road": r, "points": run_points, "levels": run_levels, "along": run_along, "half": road.half, "kind": road.kind, "ford": road.ford, "length": (road.along as PackedFloat32Array)[line.size() - 1]})
					run_points = PackedVector2Array()
					run_levels = PackedFloat32Array()
					run_along = PackedFloat32Array()
				run_points.append(line[j])
				run_levels.append((road.levels as PackedFloat32Array)[j])
				run_along.append((road.along as PackedFloat32Array)[j])
				last = j
		if not run_points.is_empty():
			pieces.append({"road": r, "points": run_points, "levels": run_levels, "along": run_along, "half": road.half, "kind": road.kind, "ford": road.ford, "length": (road.along as PackedFloat32Array)[line.size() - 1]})
	# The main road first: its running surface is never overwritten.
	pieces.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.road < b.road)
	var near_ponds := []
	for pond: Vector4 in ponds:
		if reach.grow(pond.z + 14.0).has_point(Vector2(pond.x, pond.y)): near_ponds.append(pond)
	var clearings := []
	var lots := []
	for site: Dictionary in sites:
		if site.has("lot"):
			if reach.grow(site.lot + 12.0).has_point(site.at): lots.append(Vector4(site.at.x, site.at.y, site.lot, site.level))
		elif reach.grow(16.0).has_point(site.point): clearings.append(Vector3(site.point.x, site.point.y, 12.0 if site.kind == "camp" else 8.0))
	if reach.grow(20.0).has_point(Vector2(CAMP.x, CAMP.y)): clearings.append(CAMP)
	_ensure_zones(area.end.y)
	return {"tile": tile, "seeds": noise_seeds, "zones": zones.duplicate(), "altitude": altitude.duplicate(), "walls": wall_spans.duplicate(), "summit": summit, "gap": river_gap, "river": river, "peaks": _peaks_near(reach.grow(200.0)), "pieces": pieces, "ponds": near_ponds, "clearings": clearings, "lots": lots, "seed_value": seed_value}

## Makes one tile (on a worker thread): heights and ground kinds, 80 × 80.
## Returns them with the tile's height range.
static func bake_tile(job: Dictionary) -> Dictionary:
	var tile: Vector2i = job.tile
	var nz := make_noises(job.seeds)
	nz.zones = job.zones
	nz.altitude = job.altitude
	nz.walls = job.walls
	nz.summit = job.summit
	nz.gap = job.gap
	var r: PackedFloat32Array = job.river
	var peaks: Array = job.peaks
	var x0 := tile.x * TILE_M
	var z0 := tile.y * TILE_M
	# 1. The land on a 3 m grid (one node of margin), then per texel by a
	# Catmull-Rom patch, with forest density and wet patches the same way.
	const C := 23
	var land_c := PackedFloat32Array()
	land_c.resize(C * C)
	var woods_c := PackedFloat32Array()
	woods_c.resize(C * C)
	var bog_c := PackedFloat32Array()
	bog_c.resize(C * C)
	# Biome per node (dithered along zone borders) and desert share (dry river).
	var biome_c := PackedByteArray()
	biome_c.resize(C * C)
	var dry_c := PackedFloat32Array()
	dry_c.resize(C * C)
	for j in C:
		var z := z0 + (j - 1) * 3.0
		var rx := river_x_of(r, z)
		for i in C:
			var x := x0 + (i - 1) * 3.0
			var k := j * C + i
			land_c[k] = land(x, z, nz, r, peaks)
			var dv := absf(x - rx)
			woods_c[k] = nz.woods.get_noise_2d(x, z) + 0.25 - 0.45 * (1.0 - smoothstep(12.0, 34.0, dv))
			bog_c[k] = nz.bog.get_noise_2d(x, z)
			var warp: float = nz.warp.get_noise_2d(x, z)
			var mix := zone_mix(z, warp, nz.zones)
			biome_c[k] = int(mix.y) if mix.z + 0.15 * nz.detail.get_noise_2d(x * 3.0, z * 3.0) > 0.5 else int(mix.x)
			dry_c[k] = maxf(desert_share(z, warp, nz.zones), river_off(z, nz.gap))
	var count := TILE * TILE
	var h := PackedFloat32Array()
	h.resize(count)
	var woods := PackedFloat32Array()
	woods.resize(count)
	var wet := PackedFloat32Array()
	wet.resize(count)
	var biomes := PackedByteArray()
	biomes.resize(count)
	var dry := PackedFloat32Array()
	dry.resize(count)
	# How worn and rough the land is: rising all the way to the mountains.
	var hard := clampf(0.25 + z0 / 40000.0, 0.25, 1.0)
	for tz in TILE:
		var gz := tz * STEP / 3.0 + 1.0
		var jz := floori(gz)
		var fz := gz - jz
		var wz := _catmull(fz)
		for tx in TILE:
			var gx := tx * STEP / 3.0 + 1.0
			var jx := floori(gx)
			var fx := gx - jx
			var wx := _catmull(fx)
			var v := 0.0
			for row in 4:
				var base := clampi(jz + row - 1, 0, C - 1) * C
				var a := clampi(jx - 1, 0, C - 1)
				var b := clampi(jx, 0, C - 1)
				var c := clampi(jx + 1, 0, C - 1)
				var d := clampi(jx + 2, 0, C - 1)
				v += wz[row] * (land_c[base + a] * wx.x + land_c[base + b] * wx.y + land_c[base + c] * wx.z + land_c[base + d] * wx.w)
			var x := x0 + tx * STEP
			var z := z0 + tz * STEP
			var nearest := (roundi(gz) * C + roundi(gx))
			var biome := biome_c[nearest]
			# Off-road: rolls every few metres, and (rougher the further the
			# way goes) moguls and humps; small ripples in the sand.
			var rolls := 0.32 * sin(x * 0.29 + z * 0.13) * sin(z * 0.31 - x * 0.07) + 0.16 * sin(x * 0.61 + 1.3) * cos(z * 0.53 + 0.4)
			var moguls := sin(x * 0.83 + sin(z * 0.21) * 2.0) * sin(z * 0.79 - x * 0.17)
			if biome == DESERT: v += rolls * 0.5 + 0.06 * sin((x * 0.8 + z * 0.6) * 1.7)
			elif biome == MOUNTAINS: v += rolls * (0.8 + 0.6 * hard) + 0.3 * hard * maxf(0.0, moguls)
			elif biome == FIELDS: v += rolls * 0.3
			else: v += rolls * (0.6 + 0.8 * hard) + (0.32 if biome == BOG else 0.22) * hard * moguls
			var i := tz * TILE + tx
			h[i] = v
			biomes[i] = biome
			dry[i] = dry_c[nearest]
			woods[i] = woods_c[nearest]
			wet[i] = bog_c[nearest]
	# Clearings (camp, sites): levelled.
	for clearing: Vector3 in job.clearings:
		var level := land(clearing.x, clearing.y, nz, r, peaks)
		for i in count:
			var p := Vector2(x0 + (i % TILE) * STEP, z0 + (i / TILE) * STEP)
			var d := p.distance_to(Vector2(clearing.x, clearing.y))
			if d < clearing.z + 8.0: h[i] = lerpf(h[i], level, 1.0 - smoothstep(clearing.z, clearing.z + 8.0, d))
	for lot: Vector4 in job.lots:
		for i in count:
			var p := Vector2(x0 + (i % TILE) * STEP, z0 + (i / TILE) * STEP)
			var d := p.distance_to(Vector2(lot.x, lot.y))
			if d < lot.z + 12.0: h[i] = lerpf(h[i], lot.w, 1.0 - smoothstep(lot.z, lot.z + 12.0, d))
	# 2. Roads, as in generated_terrain.gd: levelled with crown and ditches,
	# banks 1 : 2.5; an earlier road's running surface is never overwritten.
	var road_dist := PackedFloat32Array()
	road_dist.resize(count)
	road_dist.fill(INF)
	var road_id := PackedInt32Array()
	road_id.resize(count)
	road_id.fill(-1)
	var road_along := PackedFloat32Array()
	road_along.resize(count)
	var laid := PackedInt32Array()
	laid.resize(count)
	laid.fill(-1)
	var road_half := PackedFloat32Array()
	road_half.resize(count)
	var road_kind := PackedInt32Array()
	road_kind.resize(count)
	var road_ford := PackedFloat32Array()
	road_ford.resize(count)
	var road_total := PackedFloat32Array()
	road_total.resize(count)
	var current := -1
	var before := PackedFloat32Array()
	var strongest := PackedFloat32Array()
	var closest := PackedFloat32Array()
	for piece: Dictionary in job.pieces:
		var road: int = piece.road
		if road != current:
			current = road
			before = h.duplicate()
			strongest = PackedFloat32Array()
			strongest.resize(count)
			closest = PackedFloat32Array()
			closest.resize(count)
			closest.fill(INF)
		var points: PackedVector2Array = piece.points
		var levels: PackedFloat32Array = piece.levels
		var alongs: PackedFloat32Array = piece.along
		var half: float = piece.half
		for k in points.size() - 1:
			var a := points[k]
			var b := points[k + 1]
			var length := a.distance_to(b)
			var raw := land(a.x, a.y, nz, r, peaks)
			var bank := minf(absf(levels[k] - raw) * 2.5, 22.0)
			var reach := half + SHOULDER + 0.5 + bank
			var ix0 := maxi(0, floori((minf(a.x, b.x) - reach - x0) / STEP))
			var ix1 := mini(TILE - 1, ceili((maxf(a.x, b.x) + reach - x0) / STEP))
			var iz0 := maxi(0, floori((minf(a.y, b.y) - reach - z0) / STEP))
			var iz1 := mini(TILE - 1, ceili((maxf(a.y, b.y) + reach - z0) / STEP))
			# (Who laid a texel's running surface: road * 1 000 000 + 2 m sample.)
			var me := road * 1000000 + roundi(alongs[k] / 2.0)
			for tz in range(iz0, iz1 + 1):
				for tx in range(ix0, ix1 + 1):
					var p := Vector2(x0 + tx * STEP, z0 + tz * STEP)
					var s := clampf((p - a).dot(b - a) / maxf(length * length, 0.0001), 0.0, 1.0)
					var dist := p.distance_to(a.lerp(b, s))
					if dist >= reach: continue
					var i := tz * TILE + tx
					# A surface laid by another road, or by a far stretch of this one.
					if laid[i] != -1 and (laid[i] / 1000000 != road or absi(laid[i] - me) > 10): continue
					var level := lerpf(levels[k], levels[k + 1], s)
					if dist < road_dist[i]:
						road_dist[i] = dist
						road_id[i] = road
						road_along[i] = lerpf(alongs[k], alongs[k + 1], s)
						road_half[i] = half
						road_kind[i] = piece.kind
						road_ford[i] = piece.ford
						road_total[i] = piece.length
					var wgt := 1.0 - smoothstep(half, reach, dist)
					if wgt < strongest[i] or (wgt == strongest[i] and dist >= closest[i]): continue
					strongest[i] = wgt
					closest[i] = dist
					var crown := 0.05 * (1.0 - (dist / half) * (dist / half)) if dist < half else -0.06 * (1.0 - smoothstep(half, half + 1.5, dist))
					h[i] = lerpf(before[i], level + crown, wgt)
					if dist < half + 0.3: laid[i] = me
	# 3. River channel (shallow and gently banked at fords) and pond bowls.
	for tz in TILE:
		var z := z0 + tz * STEP
		var rx := river_x_of(r, z)
		var half_width := 4.5 + 1.2 * sin(z * 0.021 + r[6])
		var level_row := altitude_of(z, job.altitude)
		# (Where the river has given out it leaves no bed: the channel fades away.)
		var channel := 1.0 - river_off(z, job.gap)
		if channel <= 0.0: continue
		for tx in TILE:
			var dv := absf(x0 + tx * STEP - rx)
			if dv >= 24.0: continue
			var i := tz * TILE + tx
			var ford := 0.0
			var depth := 1.2
			if road_dist[i] != INF:
				ford = 1.0 - smoothstep(road_half[i], road_half[i] + 3.0, road_dist[i])
				depth = lerpf(depth, road_ford[i], ford)
			var c := (1.0 - smoothstep(half_width, lerpf(half_width + 5.0, 24.0, ford), dv)) * channel
			# In the desert the river runs dry: a shallow sandy wash above the water.
			var bed := level_row + lerpf(-depth, 0.6 - depth * 0.3, smoothstep(0.3, 0.7, dry[i]))
			if c > 0.0: h[i] = lerpf(h[i], bed, c)
	for pond: Vector4 in job.ponds:
		for i in count:
			var p := Vector2(x0 + (i % TILE) * STEP, z0 + (i / TILE) * STEP)
			var d := p.distance_to(Vector2(pond.x, pond.y))
			if d >= pond.z + 14.0: continue
			if d < pond.z:
				var f := d / pond.z
				h[i] = pond.w - POND_DEPTH * (1.0 - f * f)
			elif road_dist[i] == INF or road_dist[i] > road_half[i]:
				h[i] = lerpf(pond.w + 0.25 + (d - pond.z) * 0.08, h[i], smoothstep(pond.z + 4.0, pond.z + 14.0, d))
	# 4. Ground kinds and the worn relief of mud and gravel tracks.
	var fits := {}
	var kinds := PackedByteArray()
	kinds.resize(count)
	var low := INF
	var high := -INF
	for tz in TILE:
		var z := z0 + tz * STEP
		var rx := river_x_of(r, z)
		var level_row := altitude_of(z, job.altitude)
		var wall_row := wall_share(z, job.walls)
		for tx in TILE:
			var x := x0 + tx * STEP
			var i := tz * TILE + tx
			var hv := h[i]
			var above := hv - level_row
			var right := h[i + 1] if tx < TILE - 1 else hv
			var left := h[i - 1] if tx > 0 else hv
			var up := h[i + TILE] if tz < TILE - 1 else hv
			var down := h[i - TILE] if tz > 0 else hv
			var slope := sqrt((right - left) * (right - left) + (up - down) * (up - down)) / ((2.0 if tx > 0 and tx < TILE - 1 else 1.0) * STEP)
			var biome := biomes[i]
			var noise := sin(x * 0.37 + sin(z * 0.23) * 2.0) * sin(z * 0.31 - x * 0.11)
			var look := 0
			var k := Surface.GRASS
			var snowy := false
			var boggy := false
			match biome:
				DESERT:
					# Sand; rock on steep dune faces and outcrops, gravel plains.
					k = Surface.SAND
					if wet[i] > 0.5: k = Surface.GRAVEL
					if slope > 0.55 or (above > 18.0 and noise > 0.3): k = Surface.ROCK
				MOUNTAINS:
					# Forest low down, alpine meadow and rock above, snow higher.
					k = Surface.FOREST if woods[i] > 0.15 and hv < TREELINE + 40.0 * noise and slope < 0.6 else Surface.GRASS
					if slope > 0.6 or (hv > TREELINE + 170.0 and noise > 0.1): k = Surface.ROCK
					snowy = hv > SNOWLINE + 30.0 * sin(z * 0.03) + 30.0 * sin(x * 0.05) + 20.0 * noise
					if snowy and slope < 0.95: k = Surface.SNOW
				FIELDS:
					# Fields up to the road, grass on the headlands, a copse here and
					# there; what cannot be ploughed runs to weeds and dry grass.
					k = Surface.FOREST if woods[i] > 0.72 and slope < 0.5 else Surface.GRASS
					if k == Surface.GRASS:
						if parcel_fit(x, z, nz, r, peaks, fits):
							var crop := crop_cell(x, z, job.seed_value)
							if crop >= 0 and road_dist[i] > road_half[i] + 4.0 and absf(x - rx) > 15.0:
								k = Surface.DIRT
								look = LOOK_CROP + crop
						elif noise > -0.15:
							look = LOOK_DRY
				STEPPE:
					# Dry grass, bare gravel and sand, hardly a tree.
					k = Surface.GRASS
					look = LOOK_DRY
					if wet[i] > 0.42:
						k = Surface.GRAVEL
						look = 0
					elif noise > 0.62:
						k = Surface.PACKED
						look = 0
					if slope > 0.6:
						k = Surface.ROCK
						look = 0
				_:
					var threshold := 0.35 if biome == MEADOW else (0.3 if biome == BOG else (-0.15 if biome == TAIGA else (0.3 if biome == FOOTHILLS else 0.0)))
					k = Surface.FOREST if woods[i] > threshold and above < 22.0 and slope < 0.65 else Surface.GRASS
					if above > 21.0: k = Surface.ROCK if noise > 0.25 or slope > 0.5 else Surface.GRASS
					if slope > 0.75: k = Surface.ROCK
					snowy = hv > SNOWLINE + 30.0 * sin(z * 0.03) + 30.0 * sin(x * 0.05) + 20.0 * noise
					if snowy and slope < 0.95: k = Surface.SNOW
					# Wet meadows in the lowlands (much of the bogs).
					boggy = wet[i] > (-0.1 if biome == BOG else 0.45) and above < 12.0 and slope < 0.15
					if boggy: k = Surface.MUD if noise > -0.2 else Surface.GRASS
			for clearing: Vector3 in job.clearings:
				if Vector2(x - clearing.x, z - clearing.y).length() < clearing.z:
					k = Surface.DIRT
					look = 0
			for lot: Vector4 in job.lots:
				if Vector2(x - lot.x, z - lot.y).length() < lot.z:
					k = Surface.GRAVEL
					look = 0
			var dv := absf(x - rx)
			if dv < 13.0 and above < 1.6 and river_off(z, job.gap) < 0.5:
				# The river's banks; a sandy dry wash in the desert.
				k = (Surface.GRAVEL if hv > TREELINE else Surface.SAND) if dry[i] > 0.5 else Surface.MUD
				look = 0 if dry[i] > 0.5 else LOOK_BANK
			var dist := road_dist[i]
			var on_road := false
			if dist != INF:
				var half := road_half[i]
				if dist < half:
					on_road = true
					look = 0
					var s := road_along[i]
					k = road_kind[i]
					if road_id[i] == 0:
						# Asphalt out of camp, then gravel tracks, forest dirt with
						# puddles and rock on the climbs, changing every kilometre or so.
						var stage := posmod(floori(s / 900.0), 3)
						if s < 400.0: k = Surface.ROAD
						elif above > 17.0 and hv > TREELINE + 190.0: k = Surface.ROCK
						elif biome == DESERT or biome == STEPPE: k = Surface.GRAVEL if dist > 0.45 and dist < 1.15 else Surface.PACKED
						elif biome == FIELDS and stage == 0: k = Surface.ROAD
						elif stage == 1 or biome == BOG: k = Surface.DIRT
						else: k = Surface.GRAVEL if dist > 0.45 and dist < 1.15 else Surface.DIRT
						var puddles := 0.65 if biome == BOG else 0.85
						# (No mud on the serpentines: on a hairpin's slope it would stop any car.)
						if wall_row == 0.0:
							if k == Surface.DIRT and (stage == 1 or biome == BOG) and sin(s * 0.11) * sin(s * 0.047 + 1.0) > puddles: k = Surface.MUD
							if boggy and dist > 1.6 and k != Surface.ROAD: k = Surface.MUD
					elif (biome == DESERT or biome == STEPPE) and k == Surface.DIRT: k = Surface.PACKED
					if snowy:
						# (The main road is kept clear but for a drift here and there.)
						k = Surface.GRAVEL if road_id[i] == 0 and (wall_row > 0.0 or sin(s * 0.045) * sin(s * 0.017 + 0.7) < 0.45) else Surface.SNOW
					if road_id[i] == 0 and (wall_row > 0.0 or biome == MOUNTAINS or biome == FOOTHILLS): k = _mountain_road(s, hv)
				elif dist < half + 1.0:
					look = 0
					k = Surface.GRAVEL if road_id[i] == 0 and road_along[i] > 400.0 and posmod(floori(road_along[i] / 900.0), 3) != 1 else Surface.DIRT
					if biome == DESERT or biome == STEPPE: k = Surface.PACKED
					if snowy and road_id[i] != 0: k = Surface.SNOW
			for pond: Vector4 in job.ponds:
				var d := Vector2(x - pond.x, z - pond.y).length()
				if d > pond.z + 6.0: continue
				if pond.w - hv > 0.03:
					k = Surface.WATER
					look = 0
				elif dist == INF and d < pond.z + 2.5:
					k = Surface.MUD
					look = 0
			if dv < 12.0 and -above > 0.03 and dry[i] < 0.5:
				k = Surface.WATER
				look = 0
			kinds[i] = k | (look << 4)
			# Rock breaks into ledges and blocks (felt and seen, off the roads).
			if k == Surface.ROCK and not on_road:
				var crag := absf(sin(x * 0.61 + 2.0 * sin(z * 0.23))) * absf(sin(z * 0.53 - 1.5 * sin(x * 0.19)))
				h[i] = hv + 0.55 * crag * crag + 0.2 * absf(sin(hv * 1.7 + noise))
			if k == Surface.MUD and look == 0:
				var lumps := sin(x * 1.3 + sin(z * 0.9) * 1.5) * sin(z * 1.1 - x * 0.4) * 0.5 + sin(x * 2.9 + z * 2.3) * 0.25
				if on_road:
					var groove := exp(-pow((dist - 0.8) / 0.28, 2.0))
					h[i] = hv - 0.12 * groove - 0.04 * (1.0 + lumps) * (1.0 - groove)
				else:
					h[i] = hv - 0.06 - 0.07 * lumps
			elif on_road and k == Surface.GRAVEL and road_id[i] == 0:
				h[i] = hv - 0.11 * exp(-pow((dist - 0.8) / 0.4, 2.0)) + 0.025 * exp(-pow(dist / 0.3, 2.0))
			# Worn before we come: potholes, and a washboard on gravel, worse the
			# further the way goes, worst in the bogs (the wheel ruts are finer
			# than this grid: stamp_worn lays them in the soil field).
			if on_road and k != Surface.ROAD and k != Surface.ROCK and k != Surface.WATER:
				var wear := hard * (1.4 if biome == BOG else (0.8 if biome == DESERT else 1.0))
				if k == Surface.GRAVEL or k == Surface.PACKED: h[i] += 0.025 * wear * sin(road_along[i] * TAU / 0.9)
				var cell := Vector2i(floori(x / 4.0), floori(z / 4.0))
				for oz in range(-1, 2):
					for ox in range(-1, 2):
						var c := cell + Vector2i(ox, oz)
						var roll := _unit(c, 1)
						if roll > 0.22 * wear: continue
						var centre := (Vector2(c) + Vector2(_unit(c, 2), _unit(c, 3))) * 4.0
						var radius := 0.6 + 0.9 * _unit(c, 4)
						var d := Vector2(x, z).distance_to(centre) / radius
						if d < 1.0: h[i] -= (0.07 + 0.2 * _unit(c, 5)) * wear * (1.0 - d * d)
			low = minf(low, h[i])
			high = maxf(high, h[i])
	return {"tile": tile, "heights": h, "ground": kinds, "low": low, "high": high}

## The road in the mountains by turns: asphalt, gravel, asphalt again, packed dirt, a few
## hundred metres of each; high up, short sheets of ice across it.
static func _mountain_road(s: float, height: float) -> int:
	if height > ICE_FROM and sin(s * 0.21) * sin(s * 0.037 + 1.3) > 0.62: return Surface.ICE
	match posmod(floori(s / 380.0), 4):
		0, 2: return Surface.ROAD
		1: return Surface.GRAVEL
	return Surface.PACKED

## A stable random number 0..1 for a grid cell (and salt).
static func _unit(cell: Vector2i, salt: int) -> float:
	return float(hash(Vector3i(cell.x, cell.y, salt)) & 0xffff) / 65535.0

static func _catmull(f: float) -> Vector4:
	var f2 := f * f
	var f3 := f2 * f
	return Vector4(-0.5 * f3 + f2 - 0.5 * f, 1.5 * f3 - 2.5 * f2 + 1.0, -1.5 * f3 + 2.0 * f2 + 0.5 * f, 0.5 * f3 - 0.5 * f2)

## Puts a made tile into the rings.
static func store_tile(result: Dictionary) -> void:
	var tile: Vector2i = result.tile
	var h: PackedFloat32Array = result.heights
	var g: PackedByteArray = result.ground
	var ox := posmod(tile.x, RING) * TILE
	var oz := posmod(tile.y, RING) * TILE
	for row in TILE:
		var dst := (oz + row) * N + ox
		var src := row * TILE
		for col in TILE:
			heights[dst + col] = h[src + col]
			ground[dst + col] = g[src + col]
	slots[posmod(tile.y, RING) * RING + posmod(tile.x, RING)] = tile

static func drop_tile(tile: Vector2i) -> void:
	var slot := posmod(tile.y, RING) * RING + posmod(tile.x, RING)
	if slots[slot] == tile: slots[slot] = EMPTY

static func has_tile(tile: Vector2i) -> bool:
	return slots[posmod(tile.y, RING) * RING + posmod(tile.x, RING)] == tile

static func tile_of(x: float, z: float) -> Vector2i:
	return Vector2i(floori(x / TILE_M), floori(z / TILE_M))

# ---------------------------------------------------------------- queries

static func ground_image() -> Image:
	return Image.create_from_data(N, N, false, Image.FORMAT_R8, ground)
static func mesh_height_image() -> Image:
	return Image.create_from_data(N, N, false, Image.FORMAT_RF, heights.to_byte_array())

static func camp_layout() -> Dictionary:
	var line: PackedVector2Array = roads[0].points
	var along := (line[mini(6, line.size() - 1)] - line[0]).normalized()
	var side := Vector2(-along.y, along.x)
	var camp := Vector2(CAMP.x, CAMP.y)
	return {"barrack": camp + side * 18.0 + along * 4.0, "heading": atan2(-along.x, -along.y),
		"drums": camp + side * 11.0 + along * 3.0, "across": side, "along": along}

## Bilinear over the ring where the tile is made; the bare land elsewhere.
static func terrain_height(x: float, z: float) -> float:
	var gx := x / STEP
	var gz := z / STEP
	var ix := floori(gx)
	var iz := floori(gz)
	if not has_tile(Vector2i(floori(float(ix) / TILE), floori(float(iz) / TILE))) or not has_tile(Vector2i(floori(float(ix + 1) / TILE), floori(float(iz + 1) / TILE))):
		return land_at(x, z)
	var fx := gx - ix
	var fz := gz - iz
	var x0 := posmod(ix, N)
	var x1 := posmod(ix + 1, N)
	var z0 := posmod(iz, N) * N
	var z1 := posmod(iz + 1, N) * N
	return lerpf(lerpf(heights[z0 + x0], heights[z0 + x1], fx), lerpf(heights[z1 + x0], heights[z1 + x1], fx), fz)

static func height_at(x: float, z: float) -> float:
	if pond_ice > FROZEN:
		var ice := frozen_level(x, z)
		if not is_nan(ice): return maxf(ice, terrain_height(x, z))
	return terrain_height(x, z)

static func surface_at(x: float, z: float) -> int:
	if pond_ice > FROZEN and not is_nan(frozen_level(x, z)) and frozen_level(x, z) > terrain_height(x, z): return Surface.ICE
	var ix := roundi(x / STEP)
	var iz := roundi(z / STEP)
	if not has_tile(Vector2i(floori(float(ix) / TILE), floori(float(iz) / TILE))): return Surface.GRASS
	return ground[posmod(iz, N) * N + posmod(ix, N)] & 15

static func frozen_level(x: float, z: float) -> float:
	var at := pond_at(x, z, 0.0)
	return (ponds[at] as Vector4).w if at >= 0 else NAN

static func pond_at(x: float, z: float, margin: float = 6.0) -> int:
	# (A pond's reach is short of a band; margins are small.)
	var band := floori(z / BAND)
	for b in [band - 1, band, band + 1]:
		for i: int in ponds_in.get(b, []):
			var pond: Vector4 = ponds[i]
			if absf(z - pond.y) < pond.z + margin and Vector2(x - pond.x, z - pond.y).length() < pond.z + margin: return i
	return -1

static func water_depth_at(x: float, z: float) -> float:
	if pond_ice > FROZEN and pond_at(x, z) >= 0: return 0.0
	if river_off(z, river_gap) > 0.5 and pond_at(x, z) < 0: return 0.0
	if surface_at(x, z) != Surface.WATER and (flood <= 0.01 or absf(x - river_x(z)) > 13.0) and pond_at(x, z) < 0: return 0.0
	return maxf(0.0, water_level_at(x, z) - height_at(x, z))

static func water_level_at(x: float, z: float) -> float:
	var pond := pond_at(x, z)
	return ponds[pond].w + flood * 0.5 if pond >= 0 else river_level(z) + flood

static func water_level() -> float: return WATER_LEVEL

## Glacial stream near the massifs, forest river, muddy lowland river: the
## look drifts along the way.
static func river_look(z: float) -> float:
	return 1.0 + 0.8 * sin(z * 0.0021 + river[3])

static func river_current(z: float) -> Vector2:
	var direction := Vector2(river_x(z - 1.0) - river_x(z + 1.0), -2.0).normalized()
	var look := river_look(z)
	return direction * (lerpf(1.7, 0.8, look) if look < 1.0 else lerpf(0.8, 0.55, look - 1.0))

static func water_flow_at(x: float, z: float) -> Vector2:
	if surface_at(x, z) != Surface.WATER or pond_at(x, z) >= 0: return Vector2.ZERO
	return river_current(z)

static func in_test_ground(_x: float, _z: float, _margin: float = 0.0) -> bool: return true
static func hidden_rects() -> Array: return []
static func play_limit() -> float: return DRIVABLE

## Keeps the dressing's lists (trails, paths, spots) current.
static func _refresh_lists() -> void:
	trails = roads
	paths = []
	for road: Dictionary in roads: paths.append(road.points)
	var line: PackedVector2Array = roads[0].points
	spots = [["Tábor · štart", line[0], line[mini(3, line.size() - 1)]]]
	for metres in [300, 600, 850]:
		var k := mini(metres / 2, line.size() - 5)
		spots.append(["Cesta · %d m" % metres, line[k], line[k + 4]])

static func test_spots() -> Array:
	var result := []
	for spot in spots:
		var p: Vector2 = spot[1]
		var d: Vector2 = (spot[2] - p).normalized()
		result.append([spot[0], p.x, p.y, atan2(-d.x, -d.y)])
	return result

## For testing: places on the road before each landscape and the last stage's parts, from
## the start to the summit: [name, z], in order. What each is on the road: road_sample_at().
static func checkpoints() -> Array:
	var list := [["Štart · " + BIOME_NAMES[int((zones[0] as Vector2).y)], 20.0]]
	for i in range(1, zones.size()):
		var zone: Vector2 = zones[i]
		if zone.x > summit.z: break
		list.append([BIOME_NAMES[int(zone.y)], zone.x - BLEND - 100.0])
	list.append(["Serpentíny I", (walls[0] as Dictionary).z0 - 120.0])
	list.append(["Sedlo", (walls[0] as Dictionary).z1 + 180.0])
	list.append(["Serpentíny II", (walls[1] as Dictionary).z0 - 120.0])
	list.append(["Vrchol", (walls[1] as Dictionary).z1 - 2.5 * WALL_LEG])
	list.sort_custom(func(a: Array, b: Array) -> bool: return a[1] < b[1])
	return list

## The first sample of the main road that gets to z (-1: not planned that far).
static func road_sample_at(z: float) -> int:
	var line: PackedVector2Array = roads[0].points
	if planned_z < z + 20.0: return -1
	for k in line.size():
		if line[k].y >= z: return k
	return -1

## The main road at t × 100 m along (endless_expedition.gd keeps the car's
## route_phase there, so a recovery puts it back on the road where it is).
static func route_xz(t: float, lane: int = 0) -> Vector2:
	var line: PackedVector2Array = roads[0].points
	var along: PackedFloat32Array = roads[0].along
	var s := t * 100.0
	# (The samples are about 2 m apart, a little more or less where the road was joined on.)
	var i := clampi(along.bsearch(s) - 1, 0, line.size() - 2)
	var a := line[i]
	var b := line[i + 1]
	var direction := (b - a).normalized()
	return a.lerp(b, clampf((s - along[i]) / maxf(along[i + 1] - along[i], 0.01), 0.0, 1.0)) + Vector2(direction.y, -direction.x) * (1.2 + lane * 0.4)
