extends RefCounted
## A seeded highland for the expedition, built at runtime and answering the same
## queries as highlands.gd (so highlands_world.gd dresses it the same way).
##
## 1. Terrain on a 3 m grid from layered noise: rolling hills, a ridged massif
##    upstream that the river leaves through a gorge, lower hills, and a ragged
##    boundary range; upsampled to the 0.75 m map.
## 2. Roads found over that terrain (A* on a 5 m grid, with a grade limit, so
##    climbs turn into serpentines and crossings seek easy fords), simplified,
##    smoothed and levelled with limited cuts and fills and sloped banks.
## 3. The river channel, fords, ponds in hollows, a bog and a quarry.
## 4. Ground kinds: forest and meadows from noise, rock and snow up high, mud
##    banks, asphalt, gravel tracks and puddles on the roads.
## Results are cached per seed in user://, so a seed is built once.
const Surface = preload("res://desert_battlefield/terrain.gd")
const HALF := 360.0
const STEP := 0.75
const N := 961
const DRIVABLE := 345.0
const WATER_LEVEL := 0.0
const MAX_FLOOD := 0.45
const FROZEN := 0.8
const LOOK_BANK := 1
const POND_DEPTH := 1.4
const SNOWLINE := 30.0
const CACHE_VERSION := 21
const CACHE_DIR := "user://expedition_cache/"
## Coarse terrain grid (3 m) and route grid (8 m: switchback legs never closer).
const COARSE := 3.0
const CN := 241
const ROUTE := 8.0
const RN := 91
## Road grade the router keeps to (the levelled profile allows a little more).
const ROUTE_GRADE := 0.11
const ROAD_GRADE := 0.12
const SHOULDER := 1.5

static var seed_value := 0
static var heights := PackedFloat32Array()
static var ground := PackedByteArray()
static var trails: Array = []
static var paths: Array = []
static var ponds: Array = []
static var spots: Array = []
static var CAMP := Vector3.ZERO
static var QUARRY := Vector3.ZERO
static var BOG := Vector3.ZERO
static var GOAL := Vector2.ZERO
static var mountain := Vector3.ZERO
## River course: offset and three (amplitude, frequency, phase) terms.
static var river := PackedFloat32Array([0, 20, 0.013, 0, 7, 0.037, 0, 2.5, 0.08, 0])
static var flood := 0.0
static var pond_ice := 0.0
## Last build: milliseconds per step (or "cache").
static var timings := ""

static func initial_seed() -> int:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): return int(arg.trim_prefix("--seed="))
	var path := "user://roadtrip_expedition.json"
	if FileAccess.file_exists(path):
		var saved = JSON.parse_string(FileAccess.get_file_as_string(path))
		if saved is Dictionary and saved.has("seed"): return int(saved.seed)
	var random := RandomNumberGenerator.new()
	random.randomize()
	return random.randi()

static func river_x(z: float) -> float:
	return river[0] + river[1] * sin(z * river[2] + river[3]) + river[4] * sin(z * river[5] + river[6]) + river[7] * sin(z * river[8] + river[9])

static func coord(i: int) -> float:
	return -HALF + i * STEP

# ---------------------------------------------------------------- generation

static func generate(new_seed: int, use_cache: bool = true) -> void:
	var started := Time.get_ticks_msec()
	seed_value = new_seed
	flood = 0.0
	pond_ice = 0.0
	if use_cache and _load_cache():
		timings = "cache %d ms" % (Time.get_ticks_msec() - started)
		print("GENERATED seed=%d roads=%d %s" % [seed_value, trails.size(), timings])
		return
	var laps: Array[String] = []
	var clock := [started]
	var lap := func(step: String) -> void:
		var now := Time.get_ticks_msec()
		laps.append("%s %d" % [step, now - clock[0]])
		clock[0] = now
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	_river_course(rng)
	_terrain(rng)
	lap.call("terrain")
	# Roads: where two meet awkwardly (a junction in a hairpin, a road squeezed
	# beside another) their surfaces can end in a step. Such a layout is drawn
	# again, up to eight times, and the smoothest one kept.
	var pristine := heights.duplicate()
	var best := INF
	var best_state := 0
	var last_state := 0
	for attempt in 8:
		if attempt > 0: heights = pristine.duplicate()
		last_state = rng.state
		_layout(rng)
		_bake_roads()
		var steepest := _steepest_road()
		if steepest < best:
			best = steepest
			best_state = last_state
		if steepest <= 0.3: break
	if best_state != last_state:
		heights = pristine.duplicate()
		rng.state = best_state
		_layout(rng)
		_bake_roads()
	laps.append("steepest road %.2f" % best)
	lap.call("routes and roads")
	_carve_water()
	lap.call("water")
	_ground_kinds()
	lap.call("ground")
	_save_cache()
	timings = "%d ms: %s" % [Time.get_ticks_msec() - started, ", ".join(laps)]
	print("GENERATED seed=%d roads=%d %s" % [seed_value, trails.size(), timings])

## Steepest grade along any road (2 m samples) as baked.
static func _steepest_road() -> float:
	var steepest := 0.0
	for line: PackedVector2Array in paths:
		for k in range(1, line.size()):
			var d := line[k].distance_to(line[k - 1])
			if d > 0.1: steepest = maxf(steepest, absf(height_raw(line[k].x, line[k].y) - height_raw(line[k - 1].x, line[k - 1].y)) / d)
	return steepest

static func _river_course(rng: RandomNumberGenerator) -> void:
	river = PackedFloat32Array([rng.randf_range(-40.0, 40.0),
		rng.randf_range(16.0, 28.0), rng.randf_range(0.009, 0.015), rng.randf_range(-PI, PI),
		rng.randf_range(5.0, 10.0), rng.randf_range(0.03, 0.045), rng.randf_range(-PI, PI),
		rng.randf_range(1.5, 3.5), rng.randf_range(0.07, 0.1), rng.randf_range(-PI, PI)])

static func _noise(seed: int, frequency: float, octaves: int, ridged: bool = false) -> FastNoiseLite:
	var noise := FastNoiseLite.new()
	noise.seed = seed
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.frequency = frequency
	noise.fractal_type = FastNoiseLite.FRACTAL_RIDGED if ridged else FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = octaves
	return noise

## Forest density per texel (read by the ground kinds, then dropped).
static var forest_mask := PackedFloat32Array()

## 1. The landscape on the coarse grid, upsampled (cubic) to the map; forest
## density the same way.
static func _terrain(rng: RandomNumberGenerator) -> void:
	var base := _noise(rng.randi(), 1.0 / 260.0, 5)
	var detail := _noise(rng.randi(), 1.0 / 55.0, 3)
	var ridges := _noise(rng.randi(), 1.0 / 150.0, 4, true)
	var warp := _noise(rng.randi(), 1.0 / 90.0, 2)
	var woods := _noise(rng.randi(), 1.0 / 120.0, 3)
	# The massif upstream (north) on one side of the river; lower hills across
	# the valley further south.
	var side := 1.0 if rng.randf() < 0.5 else -1.0
	var mz := rng.randf_range(185.0, 235.0)
	mountain = Vector3(river_x(mz) + side * rng.randf_range(80.0, 130.0), mz, rng.randf_range(155.0, 190.0))
	var hz := rng.randf_range(-60.0, 40.0)
	var hills := Vector3(river_x(hz) - side * rng.randf_range(90.0, 150.0), hz, rng.randf_range(80.0, 110.0))
	var hills_height := rng.randf_range(10.0, 16.0)
	var coarse := PackedFloat32Array()
	coarse.resize(CN * CN)
	var woods_coarse := PackedFloat32Array()
	woods_coarse.resize(CN * CN)
	for iz in CN:
		var z := -HALF + iz * COARSE
		var rx := river_x(z)
		var valley_width := 48.0 + 18.0 * sin(z * 0.011 + river[3])
		for ix in CN:
			var x := -HALF + ix * COARSE
			var h := 6.5 + 6.5 * base.get_noise_2d(x, z) + 1.4 * detail.get_noise_2d(x, z)
			var ridge := 0.5 + 0.5 * ridges.get_noise_2d(x, z)
			var w := warp.get_noise_2d(x, z)
			# Massif: a broad dome scored by ridges.
			var dm := Vector2(x - mountain.x, z - mountain.y).length() / mountain.z
			h += exp(-dm * dm * 1.8) * (1.0 + 0.15 * w) * (26.0 + 30.0 * ridge * ridge)
			var dh := Vector2(x - hills.x, z - hills.y).length() / hills.z
			h += exp(-dh * dh * 2.0) * hills_height * (0.7 + 0.5 * ridge)
			# Boundary range, ragged along its foot.
			var edge := smoothstep(282.0, 350.0, maxf(absf(x), absf(z)) + w * 14.0)
			h += edge * edge * (26.0 + 24.0 * ridge)
			# The valley: hills settle onto a floor gently rising from the river.
			var dv := absf(x - rx)
			h = lerpf(1.0 + 0.06 * maxf(0.0, dv - 6.0), h, smoothstep(10.0, valley_width + 12.0 * w, dv))
			if h < 1.2: h = 1.2 - (1.2 - h) * 0.25
			coarse[iz * CN + ix] = h
			# Forest: patches, thinner along the river.
			woods_coarse[iz * CN + ix] = woods.get_noise_2d(x, z) + 0.25 - 0.45 * (1.0 - smoothstep(12.0, 34.0, dv))
	heights = _upsample(coarse)
	forest_mask = _upsample(woods_coarse)

static func _upsample(values: PackedFloat32Array) -> PackedFloat32Array:
	var image := Image.create_from_data(CN, CN, false, Image.FORMAT_RF, values.to_byte_array())
	image.resize(N, N, Image.INTERPOLATE_CUBIC)
	return image.get_data().to_float32_array()

static func height_raw(x: float, z: float) -> float:
	var gx := clampf((x + HALF) / STEP, 0.0, N - 1.001)
	var gz := clampf((z + HALF) / STEP, 0.0, N - 1.001)
	var ix := int(gx)
	var iz := int(gz)
	var fx := gx - ix
	var fz := gz - iz
	return lerpf(lerpf(heights[iz * N + ix], heights[iz * N + ix + 1], fx), lerpf(heights[(iz + 1) * N + ix], heights[(iz + 1) * N + ix + 1], fx), fz)

static func slope_raw(x: float, z: float) -> float:
	return Vector2(height_raw(x + 3.0, z) - height_raw(x - 3.0, z), height_raw(x, z + 3.0) - height_raw(x, z - 3.0)).length() / 6.0

## 2. Camp, goal and the roads between, ponds, quarry and bog.
static func _layout(rng: RandomNumberGenerator) -> void:
	trails.clear()
	paths.clear()
	ponds.clear()
	_route_grid()
	# Camp in the south, goal high on the massif across the river.
	var goal_side := signf(mountain.x - river_x(mountain.y))
	var camp := _find_spot(rng, Rect2(-240, -300, 480, 60), func(p: Vector2) -> float:
		var across := (p.x - river_x(p.y)) * -goal_side
		return (0.0 if across > 50.0 and across < 170.0 else 50.0) + slope_raw(p.x, p.y) * 60.0 + absf(height_raw(p.x, p.y) - 6.0))
	CAMP = Vector3(camp.x, camp.y, 15.0)
	GOAL = _find_spot(rng, Rect2(mountain.x - mountain.z, mountain.y - mountain.z * 0.9, mountain.z * 2.0, mountain.z * 1.6), func(p: Vector2) -> float:
		var across := (p.x - river_x(p.y)) * goal_side
		var inside := across > 30.0 and absf(p.x) < 300.0 and absf(p.y) < 300.0
		return absf(height_raw(p.x, p.y) - 30.0) * 2.0 + slope_raw(p.x, p.y) * 40.0 + (0.0 if inside else 200.0))
	# Ponds: hollows away from the river (before routing, so roads skirt them).
	for attempt in 2:
		var pond_spot := _find_spot(rng, Rect2(-280, -240, 560, 420), func(p: Vector2) -> float:
			var away := absf(p.x - river_x(p.y))
			var free := 0.0 if away > 45.0 and p.distance_to(camp) > 60.0 and p.distance_to(GOAL) > 80.0 else 100.0
			for pond: Vector4 in ponds: free += 100.0 if p.distance_to(Vector2(pond.x, pond.y)) < 160.0 else 0.0
			return free + slope_raw(p.x, p.y) * 80.0 + maxf(0.0, height_raw(p.x, p.y) - 5.0) * 2.0)
		if slope_raw(pond_spot.x, pond_spot.y) < 0.12:
			ponds.append(Vector4(pond_spot.x, pond_spot.y, rng.randf_range(11.0, 16.0), height_raw(pond_spot.x, pond_spot.y) - 0.3))
	_block_ponds()
	# Main route: camp → waypoints swinging out across its bank and back (the
	# first half on the camp's bank, the rest on the goal's, so the road fords
	# the river once) → goal.
	var waypoints: Array[Vector2] = [camp]
	var legs := 4
	for i in range(1, legs + 1):
		var t := float(i) / (legs + 1)
		var z := lerpf(camp.y, GOAL.y, t)
		var bank := -goal_side if i <= legs / 2 else goal_side
		var out := rng.randf_range(150.0, 230.0) if i % 2 == 1 else rng.randf_range(45.0, 80.0)
		var aim := river_x(z) + bank * out
		waypoints.append(_find_spot(rng, Rect2(aim - 35.0, z - 35.0, 70.0, 70.0), func(p: Vector2) -> float:
			var across := (p.x - river_x(p.y)) * bank
			return slope_raw(p.x, p.y) * 50.0 + (0.0 if across > 30.0 else 200.0) + height_raw(p.x, p.y) * 0.5 + (300.0 if pond_at(p.x, p.y) >= 0 else 0.0)))
	waypoints.append(GOAL)
	var main := PackedVector2Array()
	for i in waypoints.size() - 1:
		var leg := _route(waypoints[i], waypoints[i + 1], PackedByteArray())
		if not main.is_empty(): leg = leg.slice(1)
		main.append_array(leg)
	_add_trail(_simplify(main), Surface.GRAVEL, 3.1, "Výprava", {"tracks": true})
	var road: PackedVector2Array = paths[0]
	# Detour round the middle of the road, kept off it by a repulsion band.
	var detour := _route(road[roundi(road.size() * 0.62)], road[roundi(road.size() * 0.76)], _repel(road, 22.0))
	_add_trail(_simplify(detour), Surface.DIRT, 2.8, "Obchádzka", {})
	# Side roads: a forest clearing, a store, the hills, a pond (or a meadow).
	var branches := [[0.14, "Odbočka · les"], [0.36, "Odbočka · sklad"], [0.78, "Odbočka · hory"], [0.88, "Odbočka · táborisko"]]
	for i in branches.size():
		var at := roundi(road.size() * branches[i][0])
		var start := road[at]
		var tangent := (road[mini(at + 3, road.size() - 1)] - road[maxi(at - 3, 0)]).normalized()
		var normal := Vector2(-tangent.y, tangent.x) * (1.0 if i % 2 == 0 else -1.0)
		var target := Vector2.ZERO
		var near_pond := false
		for pond: Vector4 in ponds: near_pond = near_pond or start.distance_to(Vector2(pond.x, pond.y)) < 180.0
		if i == 3 and near_pond:
			# The nearest pond's shore.
			var best := INF
			for pond: Vector4 in ponds:
				var centre := Vector2(pond.x, pond.y)
				var shore := centre + (start - centre).normalized() * (pond.z + 9.0)
				if shore.distance_to(start) < best:
					best = shore.distance_to(start)
					target = shore
		else:
			var river_side := signf(start.x - river_x(start.y))
			var uphill := i == 2
			target = _find_spot(rng, Rect2(start - Vector2(90, 90), Vector2(180, 180)), func(p: Vector2) -> float:
				var on_side := signf(p.x - river_x(p.y)) == river_side and absf(p.x - river_x(p.y)) > 30.0
				var high := height_raw(p.x, p.y) * 0.8 if uphill else 0.0
				return absf(p.distance_to(start) - 65.0) + slope_raw(p.x, p.y) * 60.0 + (0.0 if on_side else 300.0) + (300.0 if pond_at(p.x, p.y) >= 0 else 0.0) - high + (0.0 if (p - start).dot(normal) > 0.0 else 40.0))
		_add_trail(_simplify(_route(start, target, _repel(road, 10.0, start, 18.0))), Surface.DIRT, 2.6, branches[i][1], {})
	# Quarry pit beside the road a quarter of the way, away from the river.
	var q := roundi(road.size() * 0.25)
	var q_tangent := (road[q + 3] - road[q - 3]).normalized()
	var q_normal := Vector2(-q_tangent.y, q_tangent.x)
	if signf(q_normal.x) == signf(river_x(road[q].y) - road[q].x): q_normal = -q_normal
	var quarry := road[q] + q_normal * 30.0
	QUARRY = Vector3(quarry.x, quarry.y, 20.0)
	# Bog: a wet meadow the road crosses a third of the way.
	var b := roundi(road.size() * 0.36)
	BOG = Vector3(road[b].x, road[b].y, 22.0)
	# Test spots along the way.
	var crossing := _river_crossing(road)
	spots = [["Tábor · štart", road[0], road[3]]]
	if crossing >= 0: spots.append(["Brod", road[maxi(crossing - 12, 0)], road[maxi(crossing - 8, 0)]])
	spots.append(["Lom", road[q - 8], road[q - 4]])
	spots.append(["Bahnisko", road[b - 12], road[b - 8]])
	spots.append(["Obchádzka", detour[0], detour[mini(3, detour.size() - 1)]])
	for t in range(2, trails.size()):
		var line: PackedVector2Array = paths[t]
		spots.append([trails[t].name, line[0], line[mini(4, line.size() - 1)]])
	var climb := roundi(road.size() * 0.92)
	spots.append(["Výstup", road[climb - 20], road[climb - 16]])
	spots.append(["Cieľ výpravy", road[road.size() - 6], road[road.size() - 1]])

## The lowest-scoring of 60 random points in a rectangle (kept off the edge).
static func _find_spot(rng: RandomNumberGenerator, rect: Rect2, score: Callable) -> Vector2:
	var best := rect.get_center()
	var best_score := INF
	for i in 60:
		var p := Vector2(clampf(rng.randf_range(rect.position.x, rect.end.x), -300.0, 300.0), clampf(rng.randf_range(rect.position.y, rect.end.y), -300.0, 300.0))
		var s: float = score.call(p)
		if s < best_score:
			best_score = s
			best = p
	return best

# ---------------------------------------------------------------- routing

## Route grid: height, cells a road may not use (ponds, the boundary range) and
## the river (2 in the channel, 1 on its banks).
static var route_h := PackedFloat32Array()
static var route_blocked := PackedByteArray()
static var route_water := PackedByteArray()

static func _route_grid() -> void:
	route_h.resize(RN * RN)
	route_blocked.resize(RN * RN)
	route_water.resize(RN * RN)
	for iz in RN:
		var z := -HALF + iz * ROUTE
		var rx := river_x(z)
		for ix in RN:
			var x := -HALF + ix * ROUTE
			var i := iz * RN + ix
			route_h[i] = height_raw(x, z)
			route_blocked[i] = 1 if maxf(absf(x), absf(z)) > 318.0 else 0
			var dv := absf(x - rx)
			route_water[i] = 2 if dv < 10.0 else (1 if dv < 20.0 else 0)

static func _block_ponds() -> void:
	for pond: Vector4 in ponds:
		for iz in RN:
			for ix in RN:
				if Vector2(-HALF + ix * ROUTE - pond.x, -HALF + iz * ROUTE - pond.y).length() < pond.z + 10.0: route_blocked[iz * RN + ix] = 1

static func _node(p: Vector2) -> int:
	return clampi(roundi((p.y + HALF) / ROUTE), 0, RN - 1) * RN + clampi(roundi((p.x + HALF) / ROUTE), 0, RN - 1)

static func _node_point(n: int) -> Vector2:
	return Vector2(-HALF + (n % RN) * ROUTE, -HALF + (n / RN) * ROUTE)

## Cells within `reach` of a road, optionally leaving a gap around `except`.
static func _repel(line: PackedVector2Array, reach: float, except: Vector2 = Vector2.INF, gap: float = 0.0) -> PackedByteArray:
	var repel := PackedByteArray()
	repel.resize(RN * RN)
	var cells := ceili(reach / ROUTE)
	for k in range(0, line.size(), 2):
		var p := line[k]
		if except != Vector2.INF and p.distance_to(except) < gap: continue
		var c := _node(p)
		for dz in range(-cells, cells + 1):
			for dx in range(-cells, cells + 1):
				var n := c + dz * RN + dx
				if n >= 0 and n < RN * RN and _node_point(n).distance_to(p) < reach: repel[n] = 1
	return repel

## A route over the grid, relaxing the grade limit only if no route keeps it.
static func _route(from: Vector2, to: Vector2, repel: PackedByteArray) -> PackedVector2Array:
	# Gentle turns only (45° per 8 m cell: hairpins open over four cells, legs
	# 16–24 m apart), relaxing the grade, then the turns, only if nothing fits.
	for attempt in [[ROUTE_GRADE, 0.5], [0.18, 0.5], [0.26, 0.5], [ROUTE_GRADE, -0.1], [10.0, -0.1]]:
		var found := _astar(_node(from), _node(to), repel, attempt[0], attempt[1])
		if not found.is_empty():
			found[0] = from
			found[found.size() - 1] = to
			return found
	return PackedVector2Array([from, to])

const DIRS := [Vector2i(1, 0), Vector2i(-1, 0), Vector2i(0, 1), Vector2i(0, -1), Vector2i(1, 1), Vector2i(1, -1), Vector2i(-1, 1), Vector2i(-1, -1)]

## A* (8 neighbours): length, plus grade (none above the limit, so slopes are
## climbed in switchbacks), the river (a ford costs as much as a long way
## round, so one easy crossing is chosen) and repelled cells.
static func _astar(start: int, goal: int, repel: PackedByteArray, limit: float, sharpest: float) -> PackedVector2Array:
	var cost := PackedFloat32Array()
	cost.resize(RN * RN)
	cost.fill(INF)
	var came := PackedInt32Array()
	came.resize(RN * RN)
	came.fill(-1)
	var closed := PackedByteArray()
	closed.resize(RN * RN)
	var heap_f := PackedFloat32Array()
	var heap_n := PackedInt32Array()
	var goal_p := _node_point(goal)
	cost[start] = 0.0
	_push(heap_f, heap_n, _node_point(start).distance_to(goal_p), start)
	var use_repel := not repel.is_empty()
	while not heap_n.is_empty():
		var n := _pop(heap_f, heap_n)
		if closed[n] == 1: continue
		closed[n] = 1
		if n == goal: break
		var nx := n % RN
		var nz := n / RN
		# Direction we arrived in: sharp turns cost extra, so hairpins open out
		# over several cells and switchback legs keep apart.
		var from := came[n]
		var arrived := Vector2i(nx - from % RN, nz - from / RN) if from != -1 else Vector2i.ZERO
		for d: Vector2i in DIRS:
			var mx := nx + d.x
			var mz := nz + d.y
			if mx < 0 or mz < 0 or mx >= RN or mz >= RN: continue
			var m := mz * RN + mx
			if closed[m] == 1 or (route_blocked[m] == 1 and m != goal): continue
			var length := ROUTE * (1.4142 if d.x != 0 and d.y != 0 else 1.0)
			var grade := absf(route_h[m] - route_h[n]) / length
			if grade > limit: continue
			var step := length * (1.0 + 30.0 * grade * grade)
			if route_water[m] == 2: step += 160.0
			elif route_water[m] == 1: step += length * 0.8
			if use_repel and repel[m] == 1: step += length * 6.0
			if arrived != Vector2i.ZERO:
				var turn := Vector2(arrived).normalized().dot(Vector2(d).normalized())
				if turn < sharpest - 0.01: continue
				step += 0.0 if turn > 0.9 else (3.0 if turn > 0.5 else 45.0)
			var total := cost[n] + step
			if total < cost[m]:
				cost[m] = total
				came[m] = n
				_push(heap_f, heap_n, total + _node_point(m).distance_to(goal_p), m)
	if came[goal] == -1 and goal != start: return PackedVector2Array()
	var reversed := PackedVector2Array()
	var at := goal
	while at != -1:
		reversed.append(_node_point(at))
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

## Grid staircase → a few control points (Douglas-Peucker, 3.5 m), with points
## closer than 7 m merged so the spline cannot loop.
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

## A trail and its polyline: corners rounded by Chaikin's cutting (three
## rounds; it stays inside the control polygon, so it cannot overshoot into a
## loop as a spline can), resampled every 2 m.
static func _add_trail(points: Array, kind: int, half: float, title: String, extra: Dictionary) -> void:
	var trail := {"name": title, "points": points, "closed": false, "kind": kind, "half": half, "ford": 0.4}
	trail.merge(extra)
	trails.append(trail)
	var smooth := PackedVector2Array(points)
	for cuts in 3:
		var cut := PackedVector2Array([smooth[0]])
		for i in smooth.size() - 1:
			cut.append(smooth[i].lerp(smooth[i + 1], 0.25))
			cut.append(smooth[i].lerp(smooth[i + 1], 0.75))
		cut.append(smooth[smooth.size() - 1])
		smooth = cut
	var line := PackedVector2Array([smooth[0]])
	var carry := 0.0
	for i in smooth.size() - 1:
		var a := smooth[i]
		var b := smooth[i + 1]
		var length := a.distance_to(b)
		var at := 2.0 - carry
		while at < length:
			line.append(a.lerp(b, at / length))
			at += 2.0
		carry = length - (at - 2.0)
	if line[line.size() - 1].distance_to(smooth[smooth.size() - 1]) > 0.5: line.append(smooth[smooth.size() - 1])
	paths.append(line)

static func _river_crossings(line: PackedVector2Array) -> Array[int]:
	var result: Array[int] = []
	for c in line.size() - 1:
		if signf(line[c].x - river_x(line[c].y)) != signf(line[c + 1].x - river_x(line[c + 1].y)): result.append(c)
	return result

static func _river_crossing(line: PackedVector2Array) -> int:
	for c in line.size() - 1:
		if signf(line[c].x - river_x(line[c].y)) != signf(line[c + 1].x - river_x(line[c + 1].y)): return c
	return -1

# ---------------------------------------------------------------- baking

## Nearest road per texel while baking: distance, road, levelled height, arc
## length and where its bank ends.
static var road_dist := PackedFloat32Array()
static var road_id := PackedInt32Array()
static var road_h := PackedFloat32Array()
static var road_along := PackedFloat32Array()

static var road_length := PackedFloat32Array()
## Which road sample laid each texel's running surface (road * 100000 + sample).
static var surface := PackedInt32Array()
static var centres: Array[PackedFloat32Array] = []

## Camp, quarry and bog floors, then each road levelled: smoothed terrain, down
## to the ford and pond shores, met at junctions, its grade limited; the ground
## follows it with sloped banks as wide as the cut or fill needs.
static func _bake_roads() -> void:
	var camp_level := height_raw(CAMP.x, CAMP.y)
	var pit_floor := height_raw(QUARRY.x, QUARRY.y) - 5.0
	var bog_level := height_raw(BOG.x, BOG.y)
	_stamp_disc(Vector2(CAMP.x, CAMP.y), CAMP.z, CAMP.z + 10.0, func(old: float, w: float) -> float: return lerpf(old, camp_level, w))
	_stamp_disc(Vector2(QUARRY.x, QUARRY.y), QUARRY.z - 8.0, QUARRY.z, func(old: float, w: float) -> float: return lerpf(old, pit_floor, w))
	_stamp_disc(Vector2(BOG.x, BOG.y), BOG.z * 0.6, BOG.z + 8.0, func(old: float, w: float) -> float: return lerpf(old, minf(old, bog_level), w * 0.8))
	road_dist.resize(N * N)
	road_dist.fill(INF)
	road_id.resize(N * N)
	road_h.resize(N * N)
	road_along.resize(N * N)

	road_length.clear()
	centres.clear()
	road_index.clear()
	var touched := PackedInt32Array()
	# Which road sample laid each texel's running surface (road * 100000 + sample).
	surface = PackedInt32Array()
	surface.resize(N * N)
	surface.fill(-1)
	for t in trails.size():
		var trail: Dictionary = trails[t]
		var line: PackedVector2Array = paths[t]
		var count := line.size()
		var raw := PackedFloat32Array()
		for p in line: raw.append(height_raw(p.x, p.y))
		var centre := PackedFloat32Array()
		for k in count:
			var total := 0.0
			var weight := 0.0
			for o in range(-6, 7):
				if k + o < 0 or k + o >= count: continue
				total += raw[k + o]
				weight += 1.0
			centre.append(total / weight)
		for crossing in _river_crossings(line):
			for k in count:
				var off := absf(float(k - crossing)) * 2.0
				if off < 120.0: centre[k] = minf(centre[k], 0.9 + maxf(0.0, off - 8.0) * 0.12)
		for pond: Vector4 in ponds:
			for k in count:
				var d := line[k].distance_to(Vector2(pond.x, pond.y))
				if d < pond.z + 40.0: centre[k] = minf(centre[k], pond.w + 0.3 + maxf(0.0, d - pond.z) * 0.12)
		# Junctions and crossings: wherever it meets an earlier road it takes that
		# road's level (pinned), and the grade limit below eases it in.
		var pins := PackedFloat32Array()
		pins.resize(count)
		pins.fill(NAN)
		# At its ends, within 7 m of the stretch it joins (and only that stretch:
		# a junction on a switchback must not pin to the leg below); elsewhere
		# only on another road's running surface, where it crosses it.
		var start_join := _joined_sample(line[0], 7.0)
		var end_join := _joined_sample(line[count - 1], 7.0)
		for k in count:
			if k < 12 and start_join != Vector2i(-1, -1): pins[k] = _joined_level(line[k], 7.0, start_join)
			elif k >= count - 12 and end_join != Vector2i(-1, -1): pins[k] = _joined_level(line[k], 7.0, end_join)
			else: pins[k] = _joined_level(line[k], 2.5)
			if not is_nan(pins[k]): centre[k] = pins[k]
		# Grade limit both ways (2 m samples), pins held.
		for k in range(1, count):
			if not is_nan(pins[k]): continue
			var ds := line[k].distance_to(line[k - 1]) * ROAD_GRADE
			centre[k] = clampf(centre[k], centre[k - 1] - ds, centre[k - 1] + ds)
		for k in range(count - 2, -1, -1):
			if not is_nan(pins[k]): continue
			var ds := line[k].distance_to(line[k + 1]) * ROAD_GRADE
			centre[k] = clampf(centre[k], centre[k + 1] - ds, centre[k + 1] + ds)
		# Pins that disagree with each other (two stretches of the joined road)
		# give way: a slightly mismatched joint beats a step in the road.
		for k in range(1, count):
			var ds := line[k].distance_to(line[k - 1]) * ROAD_GRADE * 1.3
			centre[k] = clampf(centre[k], centre[k - 1] - ds, centre[k - 1] + ds)
		# Round the crests and dips the grade limit leaves as kinks (a long car
		# would sit on its belly over a sharp crest): two passes of a 9-sample
		# average (the joints at its ends kept), then the grade limit once more.
		for smoothing in 2:
			var rounded := centre.duplicate()
			for k in range(3, count - 3):
				var total := 0.0
				var weight := 0.0
				for o in range(-4, 5):
					if k + o < 0 or k + o >= count: continue
					total += centre[k + o]
					weight += 1.0
				rounded[k] = total / weight
			centre = rounded
		for k in range(1, count):
			var ds := line[k].distance_to(line[k - 1]) * ROAD_GRADE * 1.3
			centre[k] = clampf(centre[k], centre[k - 1] - ds, centre[k - 1] + ds)
		centres.append(centre)
		_index_road(t)
		# Stamped straight into the heights, road by road. A running surface once
		# laid is never overwritten, by a later road or by a far stretch of the
		# same one (a switchback leg): banks only meet it. Within one road each
		# texel takes the strongest bank weight, blended over the ground as it
		# was before that road.
		var half: float = trail.half
		var before := heights.duplicate()
		var strongest := PackedFloat32Array()
		strongest.resize(N * N)
		# (Equal weights, as across the running surface: the nearest stretch wins.)
		var closest := PackedFloat32Array()
		closest.resize(N * N)
		closest.fill(INF)
		var along := 0.0
		for k in count - 1:
			var a := line[k]
			var b := line[k + 1]
			var length := a.distance_to(b)
			# Cut and fill slopes at 1 : 2.5 (a car cutting a corner rides up them).
			var bank := minf(absf(centre[k] - raw[k]) * 2.5, 12.0)
			var reach := half + SHOULDER + 0.5 + bank
			var cx := roundi((a.x + HALF) / STEP)
			var cz := roundi((a.y + HALF) / STEP)
			var cells := ceili((reach + length) / STEP)
			var me := t * 100000 + k
			for dz in range(-cells, cells + 1):
				var iz := cz + dz
				if iz < 0 or iz >= N: continue
				for dx in range(-cells, cells + 1):
					var ix := cx + dx
					if ix < 0 or ix >= N: continue
					var p := Vector2(coord(ix), coord(iz))
					var s := clampf((p - a).dot(b - a) / maxf(length * length, 0.0001), 0.0, 1.0)
					var dist := p.distance_to(a.lerp(b, s))
					if dist >= reach: continue
					var i := iz * N + ix
					var laid := surface[i]
					if laid != -1 and (laid / 100000 != t or absi(laid - me) > 10): continue
					var level := lerpf(centre[k], centre[k + 1], s)
					if dist < road_dist[i]:
						if road_dist[i] == INF: touched.append(i)
						road_dist[i] = dist
						road_id[i] = t
						road_h[i] = level
						road_along[i] = along + s * length
					var w := 1.0 - smoothstep(half, reach, dist)
					if w < strongest[i] or (w == strongest[i] and dist >= closest[i]): continue
					strongest[i] = w
					closest[i] = dist
					var crown := 0.05 * (1.0 - (dist / half) * (dist / half)) if dist < half else -0.06 * (1.0 - smoothstep(half, half + 1.5, dist))
					heights[i] = lerpf(before[i], level + crown, w)
					if dist < half + 0.3: surface[i] = me
			along += length
		road_length.append(along)

## Blends heights within `outer` of a point (weight 1 inside `inner`).
static func _stamp_disc(at: Vector2, inner: float, outer: float, blend: Callable) -> void:
	var cells := ceili(outer / STEP)
	var cx := roundi((at.x + HALF) / STEP)
	var cz := roundi((at.y + HALF) / STEP)
	for iz in range(maxi(0, cz - cells), mini(N, cz + cells + 1)):
		for ix in range(maxi(0, cx - cells), mini(N, cx + cells + 1)):
			var d := Vector2(coord(ix) - at.x, coord(iz) - at.y).length()
			if d >= outer: continue
			var i := iz * N + ix
			heights[i] = blend.call(heights[i], 1.0 - smoothstep(inner, outer, d))

## Built roads' samples by 8 m cell (key → [road, sample, road, sample, …]).
static var road_index := {}

static func _index_road(t: int) -> void:
	var line: PackedVector2Array = paths[t]
	for k in line.size():
		var key := Vector2i(floori(line[k].x / 8.0), floori(line[k].y / 8.0))
		if not road_index.has(key): road_index[key] = PackedInt32Array()
		road_index[key].append_array([t, k])

## Nearest built road sample (road, sample) within `reach`, or (-1, -1); with
## `near` set, only that road's samples within 15 of it.
static func _joined_sample(p: Vector2, reach: float, near: Vector2i = Vector2i(-1, -1)) -> Vector2i:
	var best := reach
	var found := Vector2i(-1, -1)
	var cell := Vector2i(floori(p.x / 8.0), floori(p.y / 8.0))
	for dz in range(-1, 2):
		for dx in range(-1, 2):
			var entries = road_index.get(cell + Vector2i(dx, dz))
			if entries == null: continue
			for e in range(0, entries.size(), 2):
				if near.x != -1 and (entries[e] != near.x or absi(entries[e + 1] - near.y) > 15): continue
				var line: PackedVector2Array = paths[entries[e]]
				var d := line[entries[e + 1]].distance_to(p)
				if d < best:
					best = d
					found = Vector2i(entries[e], entries[e + 1])
	return found

## Levelled height of that sample, or NAN.
static func _joined_level(p: Vector2, reach: float, near: Vector2i = Vector2i(-1, -1)) -> float:
	var found := _joined_sample(p, reach, near)
	return NAN if found.x == -1 else centres[found.x][found.y]

## 3. River channel (shallow and gently banked at fords) and pond bowls.
static func _carve_water() -> void:
	for iz in N:
		var z := coord(iz)
		var rx := river_x(z)
		var half_width := 4.5 + 1.2 * sin(z * 0.021 + river[6])
		for ix in range(maxi(0, floori((rx - 24.0 + HALF) / STEP)), mini(N, ceili((rx + 24.0 + HALF) / STEP))):
			var dv := absf(coord(ix) - rx)
			var i := iz * N + ix
			var ford := 0.0
			var depth := 1.2
			if road_dist[i] != INF:
				var trail: Dictionary = trails[road_id[i]]
				ford = 1.0 - smoothstep(trail.half, trail.half + 3.0, road_dist[i])
				depth = lerpf(depth, trail.ford, ford)
			var c := 1.0 - smoothstep(half_width, lerpf(half_width + 5.0, 24.0, ford), dv)
			if c > 0.0: heights[i] = lerpf(heights[i], WATER_LEVEL - depth, c)
	for pond: Vector4 in ponds:
		var reach := pond.z + 14.0
		var cells := ceili(reach / STEP)
		var cx := roundi((pond.x + HALF) / STEP)
		var cz := roundi((pond.y + HALF) / STEP)
		for iz in range(maxi(0, cz - cells), mini(N, cz + cells + 1)):
			for ix in range(maxi(0, cx - cells), mini(N, cx + cells + 1)):
				var d := Vector2(coord(ix) - pond.x, coord(iz) - pond.y).length()
				if d >= reach: continue
				var i := iz * N + ix
				if d < pond.z:
					var f := d / pond.z
					heights[i] = pond.w - POND_DEPTH * (1.0 - f * f)
				elif road_dist[i] == INF or road_dist[i] > trails[road_id[i]].half:
					heights[i] = lerpf(pond.w + 0.25 + (d - pond.z) * 0.08, heights[i], smoothstep(pond.z + 4.0, reach, d))

## 4. Ground kinds (low 4 bits) and shader looks (high 4 bits), with the worn
## relief of mud and gravel tracks.
static func _ground_kinds() -> void:
	ground.resize(N * N)
	var road_total: float = road_length[0]
	for iz in N:
		var z := coord(iz)
		var rx := river_x(z)
		var row := iz * N
		var snowline_row := SNOWLINE + 3.0 * sin(z * 0.03)
		# (Per-row parts of the distances to the bog, quarry, camp and ponds, so
		# the 920 000 texels skip what lies far from them.)
		var bog_row := (z - BOG.y) * (z - BOG.y)
		var quarry_row := (z - QUARRY.y) * (z - QUARRY.y)
		var camp_row := (z - CAMP.y) * (z - CAMP.y)
		var pond_row := false
		for pond: Vector4 in ponds: pond_row = pond_row or absf(z - pond.y) < pond.z + 6.0
		var wavy := sin(z * 0.23) * 2.0
		for ix in N:
			var x := coord(ix)
			var i := row + ix
			var h := heights[i]
			var right := heights[i + 1] if ix < N - 1 else h
			var left := heights[i - 1] if ix > 0 else h
			var up := heights[i + N] if iz < N - 1 else h
			var down := heights[i - N] if iz > 0 else h
			var slope := sqrt((right - left) * (right - left) + (up - down) * (up - down)) / (2.0 * STEP)
			var in_bog := (x - BOG.x) * (x - BOG.x) + bog_row < BOG.z * BOG.z
			var noise := sin(x * 0.37 + wavy) * sin(z * 0.31 - x * 0.11) if h > 21.0 or in_bog else 0.0
			var k := Surface.FOREST if forest_mask[i] > 0.0 and h < 22.0 and slope < 0.65 else Surface.GRASS
			var look := 0
			var snowy := h > 24.0 and h > snowline_row + 3.0 * sin(x * 0.05) + 2.0 * noise
			if h > 21.0: k = Surface.ROCK if noise > 0.25 or slope > 0.5 else Surface.GRASS
			if slope > 0.75: k = Surface.ROCK
			if snowy and slope < 0.95: k = Surface.SNOW
			if in_bog: k = Surface.MUD if noise > -0.2 else Surface.GRASS
			if (x - QUARRY.x) * (x - QUARRY.x) + quarry_row < QUARRY.z * QUARRY.z: k = Surface.GRAVEL
			if (x - CAMP.x) * (x - CAMP.x) + camp_row < CAMP.z * CAMP.z: k = Surface.DIRT
			var dv := absf(x - rx)
			if dv < 13.0 and h < 1.6:
				k = Surface.MUD
				look = LOOK_BANK
			var dist := road_dist[i]
			var on_road := false
			if dist != INF:
				var t := road_id[i]
				var trail: Dictionary = trails[t]
				var half: float = trail.half
				if dist < half:
					on_road = true
					look = 0
					var s := road_along[i]
					k = trail.kind
					if t == 0:
						# Asphalt out of camp, gravel tracks, a forest dirt stretch,
						# gravel tracks again, rock on the climb.
						var progress := s / road_total
						if progress < 0.18: k = Surface.ROAD
						elif progress >= 0.5 and progress < 0.66: k = Surface.DIRT
						elif h > 17.0: k = Surface.ROCK
						else: k = Surface.GRAVEL if dist > 0.45 and dist < 1.15 else Surface.DIRT
					var length: float = road_length[t]
					# Puddles on the main road's dirt stretches (the detour and side roads stay firm).
					if t == 0 and k == Surface.DIRT and s > 15.0 and s < length - 15.0 and sin(s * 0.11 + t) * sin(s * 0.047 + 1.0) > 0.7: k = Surface.MUD
					# Through the bog: soft verges, a firm crown.
					if in_bog and t == 0 and dist > 1.6 and k != Surface.ROAD: k = Surface.MUD
					if snowy: k = Surface.SNOW
				elif dist < half + 1.0:
					look = 0
					k = Surface.GRAVEL if t == 0 and road_along[i] / road_total < 0.5 else Surface.DIRT
					if snowy: k = Surface.SNOW
			var pond := pond_at(x, z) if pond_row else -1
			if pond >= 0:
				var shore: Vector4 = ponds[pond]
				if shore.w - h > 0.03:
					k = Surface.WATER
					look = 0
				elif dist == INF and Vector2(x - shore.x, z - shore.y).length() < shore.z + 2.5:
					k = Surface.MUD
					look = 0
			if dv < 12.0 and WATER_LEVEL - h > 0.03:
				k = Surface.WATER
				look = 0
			ground[i] = k | (look << 4)
			# Worn relief: ruts and lumps in mud, sunk gravel wheel tracks.
			if k == Surface.MUD and look == 0:
				var lumps := sin(x * 1.3 + sin(z * 0.9) * 1.5) * sin(z * 1.1 - x * 0.4) * 0.5 + sin(x * 2.9 + z * 2.3) * 0.25
				if on_road:
					var groove := exp(-pow((dist - 0.8) / 0.28, 2.0))
					heights[i] = h - 0.12 * groove - 0.04 * (1.0 + lumps) * (1.0 - groove)
				else:
					heights[i] = h - 0.06 - 0.07 * lumps
			elif on_road and k == Surface.GRAVEL and road_id[i] == 0:
				heights[i] = h - 0.11 * exp(-pow((dist - 0.8) / 0.4, 2.0)) + 0.025 * exp(-pow(dist / 0.3, 2.0))
	road_dist = PackedFloat32Array()
	road_id = PackedInt32Array()
	road_h = PackedFloat32Array()
	road_along = PackedFloat32Array()

	forest_mask = PackedFloat32Array()
	surface = PackedInt32Array()

# ---------------------------------------------------------------- cache

static func _cache_path() -> String:
	return CACHE_DIR + "seed_%d_v%d.bin" % [seed_value, CACHE_VERSION]

## Keeps the newest few seeds (about 4.6 MB each).
const CACHE_KEEP := 4

static func _save_cache() -> void:
	DirAccess.make_dir_recursive_absolute(CACHE_DIR)
	var stored: Array = Array(DirAccess.get_files_at(CACHE_DIR)).filter(func(name: String) -> bool: return name.ends_with(".bin"))
	stored.sort_custom(func(a: String, b: String) -> bool: return FileAccess.get_modified_time(CACHE_DIR + a) < FileAccess.get_modified_time(CACHE_DIR + b))
	while stored.size() >= CACHE_KEEP:
		DirAccess.remove_absolute(CACHE_DIR + stored.pop_front())
	var file := FileAccess.open(_cache_path(), FileAccess.WRITE)
	if file == null: return
	file.store_var({"heights": heights, "ground": ground, "trails": trails, "paths": paths, "ponds": ponds, "spots": spots,
		"camp": CAMP, "quarry": QUARRY, "bog": BOG, "goal": GOAL, "mountain": mountain, "river": river})

static func _load_cache() -> bool:
	if not FileAccess.file_exists(_cache_path()): return false
	var file := FileAccess.open(_cache_path(), FileAccess.READ)
	if file == null: return false
	var data = file.get_var()
	if not data is Dictionary or not data.has("heights"): return false
	heights = data.heights
	ground = data.ground
	trails = data.trails
	paths = data.paths
	ponds = data.ponds
	spots = data.spots
	CAMP = data.camp
	QUARRY = data.quarry
	BOG = data.bog
	GOAL = data.goal
	mountain = data.mountain
	river = data.river
	return heights.size() == N * N and ground.size() == N * N

# ---------------------------------------------------------------- queries

static func ground_image() -> Image:
	return Image.create_from_data(N, N, false, Image.FORMAT_R8, ground)
static func height_image() -> Image:
	return Image.create_from_data(N, N, false, Image.FORMAT_RF, heights.to_byte_array())
## The map is on the render grid already.
static func mesh_height_image() -> Image:
	return height_image()

static func camp_layout() -> Dictionary:
	var road: PackedVector2Array = paths[0]
	var along := (road[mini(6, road.size() - 1)] - road[0]).normalized()
	var side := Vector2(-along.y, along.x)
	var camp := Vector2(CAMP.x, CAMP.y)
	# The building and drums stand clear of the starting lane and the chase camera.
	return {"barrack": camp + side * 18.0 + along * 4.0, "heading": atan2(-along.x, -along.y),
		"drums": camp + side * 11.0 + along * 3.0, "across": side, "along": along}

static func height_at(x: float, z: float) -> float:
	if pond_ice > FROZEN:
		var ice := frozen_level(x, z)
		if not is_nan(ice): return maxf(ice, terrain_height(x, z))
	return terrain_height(x, z)

static func terrain_height(x: float, z: float) -> float:
	return height_raw(x, z)

static func surface_at(x: float, z: float) -> int:
	if pond_ice > FROZEN and not is_nan(frozen_level(x, z)) and frozen_level(x, z) > terrain_height(x, z): return Surface.ICE
	var ix := clampi(roundi((x + HALF) / STEP), 0, N - 1)
	var iz := clampi(roundi((z + HALF) / STEP), 0, N - 1)
	return ground[iz * N + ix] & 15

static func frozen_level(x: float, z: float) -> float:
	for pond: Vector4 in ponds:
		if Vector2(x - pond.x, z - pond.y).length_squared() < pond.z * pond.z: return pond.w
	return NAN

static func pond_at(x: float, z: float) -> int:
	for i in ponds.size():
		var pond: Vector4 = ponds[i]
		if Vector2(x - pond.x, z - pond.y).length() < pond.z + 6.0: return i
	return -1

static func water_depth_at(x: float, z: float) -> float:
	if pond_ice > FROZEN and pond_at(x, z) >= 0: return 0.0
	if surface_at(x, z) != Surface.WATER and (flood <= 0.01 or absf(x - river_x(z)) > 13.0) and pond_at(x, z) < 0: return 0.0
	return maxf(0.0, water_level_at(x, z) - height_at(x, z))

static func water_level_at(x: float, z: float) -> float:
	var pond := pond_at(x, z)
	return ponds[pond].w + flood * 0.5 if pond >= 0 else WATER_LEVEL + flood

static func water_level() -> float: return WATER_LEVEL

## Glacial stream out of the massif's gorge, forest river, muddy lowland river.
static func river_look(z: float) -> float:
	var below := mountain.y - z
	if below < 110.0: return smoothstep(50.0, 110.0, below)
	return 1.0 + smoothstep(260.0, 340.0, below)

static func river_current(z: float) -> Vector2:
	var direction := Vector2(river_x(z - 1.0) - river_x(z + 1.0), -2.0).normalized()
	var look := river_look(z)
	return direction * (lerpf(1.7, 0.8, look) if look < 1.0 else lerpf(0.8, 0.55, look - 1.0))

static func water_flow_at(x: float, z: float) -> Vector2:
	if surface_at(x, z) != Surface.WATER or pond_at(x, z) >= 0: return Vector2.ZERO
	return river_current(z)

static func in_test_ground(_x: float, _z: float, _margin: float = 0.0) -> bool: return true
static func hidden_rects() -> Array: return []
static func play_limit() -> float: return DRIVABLE + 5.0

static func test_spots() -> Array:
	var result := []
	for spot in spots:
		var p: Vector2 = spot[1]
		var d: Vector2 = (spot[2] - p).normalized()
		result.append([spot[0], p.x, p.y, atan2(-d.x, -d.y)])
	return result

static func route_xz(t: float, lane: int = 0) -> Vector2:
	var line: PackedVector2Array = paths[0]
	var f := clampf(t / TAU, 0.0, 0.999) * (line.size() - 1)
	var i := floori(f)
	var a := line[i % line.size()]
	var b := line[(i + 1) % line.size()]
	var direction := (b - a).normalized()
	return a.lerp(b, f - i) + Vector2(direction.y, -direction.x) * (1.2 + lane * 0.4)

## The river runs at one level in this world.
static func river_level(_z: float) -> float:
	return WATER_LEVEL
