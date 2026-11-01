extends RefCounted
## Lesná vrchovina: a forested river valley for off-road driving. Plugged into
## terrain.gd as its `world`, it answers the same height, ground, water, spot
## and route queries as the desert, from two maps baked by build_highlands.gd:
## heights (0.5 m grid, smoothed with a cubic B-spline like the rut field, so
## suspension, ruts and the ground mesh agree) and the ground kind per texel.
## The layout below (river, trails, landmarks) is shared by the bake and play.
const Surface = preload("res://desert_battlefield/terrain.gd")
const HALF := 360.0
const STEP := 0.5
const N := 1441
const WATER_LEVEL := 0.0
const HEIGHTS_PATH := "res://offroad/highlands_heights.res"
const GROUND_PATH := "res://offroad/highlands_ground.res"
## Ground mesh heights on the 0.75 m render grid (961²), baked so the chunks
## are built at load without sampling the spline.
const MESH_HEIGHTS_PATH := "res://offroad/highlands_mesh_heights.res"
const LAYOUT_PATH := "res://offroad/highlands_layout.json"
## Everything beyond this stays behind the boundary mountains.
const DRIVABLE := 345.0

## Trails: ground kind, half width, closed loop, river ford depth, control points
## (Catmull-Rom through them); optional "tracks" (a dirt road with gravel only
## in its two worn wheel tracks) and "smooth" (samples either side, 2 m each)
## evens out the levelled track's grade. The inner dirt road loops the valley
## and fords the river twice; the long road circles the whole map; the
## serpentine climbs the mountain north-west of the valley.
const ROAD := 0
const TRAILS := [
	{"name": "Poľná cesta", "kind": Surface.GRAVEL, "half": 3.4, "closed": true, "ford": 0.3, "tracks": true, "points": [
		Vector2(-30, -108), Vector2(20, -104), Vector2(70, -88), Vector2(100, -50), Vector2(108, 0),
		Vector2(98, 50), Vector2(70, 92), Vector2(30, 110), Vector2(-20, 112), Vector2(-62, 98),
		Vector2(-92, 62), Vector2(-104, 15), Vector2(-96, -35), Vector2(-70, -80)]},
	{"name": "Lesná cesta", "kind": Surface.DIRT, "half": 2.3, "closed": false, "ford": 0.5, "points": [
		Vector2(100, -50), Vector2(78, -30), Vector2(64, -2), Vector2(72, 28), Vector2(60, 58), Vector2(70, 92)]},
	{"name": "Výstup na vrch", "kind": Surface.ROCK, "half": 2.6, "closed": false, "ford": 0.5, "points": [
		Vector2(-104, 15), Vector2(-88, 10), Vector2(-76, 0), Vector2(-66, -12), Vector2(-58, -28)]},
	{"name": "Brod", "kind": Surface.DIRT, "half": 2.5, "closed": false, "ford": 0.75, "points": [
		Vector2(-30, -20), Vector2(-5, -18), Vector2(16, -16), Vector2(35, -14), Vector2(64, -2)]},
	{"name": "Bahnisko", "kind": Surface.DIRT, "half": 2.6, "closed": false, "ford": 0.5, "bog": true, "points": [
		Vector2(-70, -80), Vector2(-50, -65), Vector2(-35, -50), Vector2(-30, -20)]},
	{"name": "Lom", "kind": Surface.GRAVEL, "half": 3.0, "closed": false, "ford": 0.5, "points": [
		Vector2(-62, 98), Vector2(-62, 88), Vector2(-62, 78), Vector2(-62, 70)]},
	{"name": "Diaľková cesta", "kind": Surface.GRAVEL, "half": 3.2, "closed": true, "ford": 0.35, "smooth": 16, "tracks": true, "points": [
		Vector2(0, -262), Vector2(95, -248), Vector2(180, -202), Vector2(238, -122), Vector2(262, -25),
		Vector2(250, 75), Vector2(212, 165), Vector2(140, 232), Vector2(55, 258), Vector2(-34, 178),
		Vector2(-75, 140), Vector2(-150, 126), Vector2(-225, 92), Vector2(-258, 5), Vector2(-245, -92),
		Vector2(-195, -176), Vector2(-110, -236)]},
	{"name": "Severná spojka", "kind": Surface.DIRT, "half": 2.5, "closed": false, "ford": 0.5, "points": [
		Vector2(-20, 112), Vector2(-22, 135), Vector2(-28, 158), Vector2(-34, 178)]},
	{"name": "Východná spojka", "kind": Surface.DIRT, "half": 2.5, "closed": false, "ford": 0.5, "points": [
		Vector2(108, 0), Vector2(150, -8), Vector2(205, -18), Vector2(262, -25)]},
	{"name": "K jazierku", "kind": Surface.DIRT, "half": 2.4, "closed": false, "ford": 0.5, "points": [
		Vector2(98, 50), Vector2(118, 68), Vector2(134, 86), Vector2(145, 97)]},
]
## The serpentine (added to `trails` by _serpentine): legs between two x
## columns, each climbing LEG_RISE in z, joined by hairpins of HAIRPIN_RADIUS.
const SERPENTINE_X := Vector2(-90.0, -185.0)
const SERPENTINE_Z0 := 150.0
const LEG_RISE := 26.0
const HAIRPIN_RADIUS := 10.0
const LEGS := 4
## The mountain: a slope rising north from MOUNTAIN_FOOT across the serpentine
## (centre x, half width), with ridges on both sides and the boundary range.
const MOUNTAIN_X := Vector2(-137.5, 70.0)
const MOUNTAIN_FOOT := 145.0
const MOUNTAIN_SLOPE := 0.33
## Snow above SNOWLINE (ragged); glazed ice patches on the serpentine's straight
## legs above ICELINE.
const SNOWLINE := 34.0
const ICELINE := 46.0
## Landmarks: summit, east forest hills, quarry pit, bog meadow, camp clearing.
const SUMMIT := Vector4(-58, -28, 12.0, 22.0)
const EAST_HILLS := Vector4(80, 20, 6.0, 30.0)
const QUARRY := Vector3(-62, 75, 20.0)
const QUARRY_DEPTH := 6.0
const BOG := Vector3(-45, -62, 22.0)
const CAMP := Vector3(-14, -20, 11.0)
## Ground map byte: ground kind in the low 4 bits (what physics reads), a look
## variant for the ground shader in the high 4 bits.
const LOOK_BANK := 1
## Ponds: centre x, z, radius, water level; POND_DEPTH in the middle.
const DEFAULT_PONDS := [Vector4(150, 110, 16.0, 3.4)]
const POND_DEPTH := 1.4
## Test spots: name, position, a point it faces.
const DEFAULT_SPOTS := [
	["Tábor · štart", Vector2(-24, -19.5), Vector2(-5, -18)],
	["Brod cez rieku", Vector2(-9, -18.6), Vector2(16, -16)],
	["Bahnisko", Vector2(-66, -77), Vector2(-50, -65)],
	["Výstup na vrch", Vector2(-100, 13.5), Vector2(-88, 10)],
	["Lesná cesta", Vector2(96, -46), Vector2(78, -30)],
	["Lom", Vector2(-62, 102), Vector2(-62, 88)],
	["Poľná cesta", Vector2(-52, -94.5), Vector2(-30, -108)],
	["Serpentíny · úpätie", Vector2(-93, 150.8), Vector2(-120, 158.2)],
	["Serpentíny · sneh", Vector2(-93, 242.8), Vector2(-120, 250.2)],
	["Serpentíny · ľad", Vector2(-182, 288.8), Vector2(-150, 297.6)],
	["Diaľková cesta", Vector2(238, -122), Vector2(262, -25)],
	["Lesné jazierko", Vector2(122, 71.5), Vector2(134, 86)],
]

static var heights := PackedFloat32Array()
static var ground := PackedByteArray()
static var loaded := _load()
## The editable layout (trails, ponds, spots, terrain stamps): from
## LAYOUT_PATH, saved by the layout scene (offroad/highlands_layout.tscn), or
## the defaults above (TRAILS plus the generated serpentine).
static var layout := _load_layout()
static var trails: Array = layout.trails
static var ponds: Array = layout.ponds
static var spots: Array = layout.spots
## Terrain stamps: centre x, z, radius, height (+ raises a hill, - digs a hollow).
static var stamps: Array = layout.stamps
## Dense polylines of the trails (2 m), and the road's arc length for the patrol.
static var paths := _paths()

## What highlands_world.gd asks of the world it dresses (generated_terrain.gd
## answers the same): placement seed, the maps and the camp layout.
static var seed_value := 51273
static func ground_image() -> Image: return load(GROUND_PATH) as Image
static func mesh_height_image() -> Image: return load(MESH_HEIGHTS_PATH) as Image
static func camp_layout() -> Dictionary:
	return {"barrack": Vector2(CAMP.x - 4.0, CAMP.y - 8.5), "heading": 0.15,
		"drums": Vector2(CAMP.x + 4.5, CAMP.y - 7.0), "across": Vector2(1, 0), "along": Vector2(0, 1)}

static func _load() -> bool:
	if not ResourceLoader.exists(HEIGHTS_PATH) or not ResourceLoader.exists(GROUND_PATH): return false
	heights = (load(HEIGHTS_PATH) as Image).get_data().to_float32_array()
	ground = (load(GROUND_PATH) as Image).get_data()
	return true

static func river_x(z: float) -> float:
	return 8.0 + 22.0 * sin(z * 0.017 + 0.4) + 7.0 * sin(z * 0.043 + 2.0)

## Catmull-Rom through a trail's control points, sampled every ~2 m.
static func trail_polyline(trail: Dictionary) -> PackedVector2Array:
	var points: Array = trail.points
	var closed: bool = trail.closed
	var count := points.size()
	var result := PackedVector2Array()
	var spans := count if closed else count - 1
	for i in spans:
		var p0: Vector2 = points[(i - 1 + count) % count] if (closed or i > 0) else points[0]
		var p1: Vector2 = points[i]
		var p2: Vector2 = points[(i + 1) % count]
		var p3: Vector2 = points[(i + 2) % count] if (closed or i + 2 < count) else points[count - 1]
		var steps := maxi(1, ceili(p1.distance_to(p2) / 2.0))
		for s in steps:
			var t := float(s) / steps
			var t2 := t * t
			var t3 := t2 * t
			result.append(0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3))
	if not closed: result.append(points[count - 1])
	return result

static func _paths() -> Array:
	var result := []
	for trail: Dictionary in trails: result.append(trail_polyline(trail))
	return result

## Serpentine control points: from the long road at the mountain foot, LEGS
## straight legs alternating west and east, each rising LEG_RISE in z, joined
## by semicircular hairpins. A wide smoothing window evens the grade over the
## hairpins (on the bare slope they would be twice as steep as the legs).
static func _serpentine() -> Dictionary:
	var points: Array = [Vector2(-75, 140), Vector2(-84, 146)]
	var z := SERPENTINE_Z0
	for leg in LEGS:
		var from := SERPENTINE_X.x if leg % 2 == 0 else SERPENTINE_X.y
		var to := SERPENTINE_X.y if leg % 2 == 0 else SERPENTINE_X.x
		for s in 5: points.append(Vector2(lerpf(from, to, s / 4.0), z + LEG_RISE * s / 4.0))
		z += LEG_RISE
		if leg == LEGS - 1: break
		# Hairpin around (to, z + r), bulging away from the legs.
		var side := -1.0 if leg % 2 == 0 else 1.0
		for k in range(1, 6):
			var a := PI * k / 6.0
			points.append(Vector2(to + side * HAIRPIN_RADIUS * sin(a), z + HAIRPIN_RADIUS * (1.0 - cos(a))))
		z += 2.0 * HAIRPIN_RADIUS
	points.append(points[points.size() - 1] + Vector2(6, 2))
	return {"name": "Serpentíny", "kind": Surface.GRAVEL, "half": 3.0, "closed": false, "ford": 0.5, "smooth": 22, "tracks": true, "ice": true, "points": points}

## Inside the serpentine's legs (away from its hairpins).
static func on_serpentine_leg(x: float) -> bool:
	return absf(x - (SERPENTINE_X.x + SERPENTINE_X.y) * 0.5) < absf(SERPENTINE_X.x - SERPENTINE_X.y) * 0.5 - 4.0

## Cubic B-spline over the baked 0.5 m grid (C2-smooth for the suspension).
static func height_at(x: float, z: float) -> float:
	if heights.is_empty(): return 0.0
	# A frozen pond carries the car on its ice.
	if pond_ice > FROZEN:
		var ice := frozen_level(x, z)
		if not is_nan(ice): return maxf(ice, terrain_height(x, z))
	return terrain_height(x, z)

static func terrain_height(x: float, z: float) -> float:
	var gx := (x + HALF) / STEP
	var gz := (z + HALF) / STEP
	var ix := floori(gx)
	var iz := floori(gz)
	var wx := _weights(gx - ix)
	var wz := _weights(gz - iz)
	var x0 := clampi(ix - 1, 0, N - 1)
	var x1 := clampi(ix, 0, N - 1)
	var x2 := clampi(ix + 1, 0, N - 1)
	var x3 := clampi(ix + 2, 0, N - 1)
	var value := 0.0
	for row in 4:
		var offset := clampi(iz + row - 1, 0, N - 1) * N
		value += wz[row] * (heights[offset + x0] * wx.x + heights[offset + x1] * wx.y + heights[offset + x2] * wx.z + heights[offset + x3] * wx.w)
	return value

static func _weights(f: float) -> Vector4:
	var f2 := f * f
	var f3 := f2 * f
	var a := 1.0 - f
	return Vector4(a * a * a, 3.0 * f3 - 6.0 * f2 + 4.0, -3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0, f3) / 6.0

static func surface_at(x: float, z: float) -> int:
	if ground.is_empty(): return Surface.GRASS
	if pond_ice > FROZEN and not is_nan(frozen_level(x, z)) and frozen_level(x, z) > terrain_height(x, z): return Surface.ICE
	var ix := clampi(roundi((x + HALF) / STEP), 0, N - 1)
	var iz := clampi(roundi((z + HALF) / STEP), 0, N - 1)
	return ground[iz * N + ix] & 15

## Rise of the water after rain (m, set by weather.gd): the river by this much,
## the ponds by half. Swollen water spreads over the low banks.
static var flood := 0.0
## How frozen the ponds are (0 open .. 1 solid, from weather.gd); above FROZEN
## the ice carries a car.
static var pond_ice := 0.0
const FROZEN := 0.8

## Ice surface of a frozen pond at a point (its water level), or NAN outside.
static func frozen_level(x: float, z: float) -> float:
	for pond: Vector4 in ponds:
		if Vector2(x - pond.x, z - pond.y).length_squared() < pond.z * pond.z: return pond.w
	return NAN
const MAX_FLOOD := 0.45

static func water_depth_at(x: float, z: float) -> float:
	if pond_ice > FROZEN and pond_at(x, z) >= 0: return 0.0
	if surface_at(x, z) != Surface.WATER and (flood <= 0.01 or absf(x - river_x(z)) > 13.0) and pond_at(x, z) < 0: return 0.0
	return maxf(0.0, water_level_at(x, z) - height_at(x, z))

static func water_level() -> float:
	return WATER_LEVEL

## Pond index around a point (its bowl and banks), or -1.
static func pond_at(x: float, z: float) -> int:
	for i in ponds.size():
		var pond: Vector4 = ponds[i]
		if Vector2(x - pond.x, z - pond.y).length() < pond.z + 6.0: return i
	return -1

## Water surface height at a point: the pond's level, else the river's.
static func water_level_at(x: float, z: float) -> float:
	var pond := pond_at(x, z)
	return ponds[pond].w + flood * 0.5 if pond >= 0 else WATER_LEVEL + flood

## River look along its course (water shader presets): glacial mountain stream
## in the north, forest river in the valley, muddy lowland river in the south.
static func river_look(z: float) -> float:
	if z > 0.0: return 1.0 - smoothstep(110.0, 160.0, z)
	return 1.0 + smoothstep(-110.0, -170.0, z)

## River current (m/s) on the water surface: downstream (towards -z) along the
## channel, quickest in the mountain stream. Ponds are still.
static func water_flow_at(x: float, z: float) -> Vector2:
	if surface_at(x, z) != Surface.WATER or pond_at(x, z) >= 0: return Vector2.ZERO
	return river_current(z)

static func river_current(z: float) -> Vector2:
	var direction := Vector2(river_x(z - 1.0) - river_x(z + 1.0), -2.0).normalized()
	var look := river_look(z)
	return direction * (lerpf(1.7, 0.8, look) if look < 1.0 else lerpf(0.8, 0.55, look - 1.0))

## The whole valley mixes grounds: ruts are evaluated per texel everywhere.
static func in_test_ground(_x: float, _z: float, _margin: float = 0.0) -> bool:
	return true

static func hidden_rects() -> Array:
	return []

static func test_spots() -> Array:
	var result := []
	for spot: Array in spots:
		var p: Vector2 = spot[1]
		var d: Vector2 = (spot[2] - p).normalized()
		result.append([spot[0], p.x, p.y, atan2(-d.x, -d.y)])
	return result

## Patrol loop along the gravel road (t in radians around the loop; lanes keep
## right of the centre line, one car width apart).
static func route_xz(t: float, lane: int = 0) -> Vector2:
	var road: PackedVector2Array = paths[ROAD]
	var f := fposmod(t / TAU, 1.0) * road.size()
	var i := floori(f)
	var a := road[i % road.size()]
	var b := road[(i + 1) % road.size()]
	var p := a.lerp(b, f - i)
	var d := (b - a).normalized()
	return p + Vector2(d.y, -d.x) * (1.2 + float(lane) * 0.4)

static func play_limit() -> float:
	return DRIVABLE + 5.0

## The layout as saved by the layout scene, or the built-in defaults.
static func _load_layout() -> Dictionary:
	var result := {"trails": TRAILS + [_serpentine()], "ponds": DEFAULT_PONDS.duplicate(), "spots": DEFAULT_SPOTS.duplicate(), "stamps": []}
	if not FileAccess.file_exists(LAYOUT_PATH): return result
	var data = JSON.parse_string(FileAccess.get_file_as_string(LAYOUT_PATH))
	if not data is Dictionary: return result
	var loaded_trails := []
	for t: Dictionary in data.get("trails", []):
		var trail := t.duplicate()
		var points: Array = []
		for q: Array in t.points: points.append(Vector2(q[0], q[1]))
		trail.points = points
		trail.kind = int(t.kind)
		if trail.has("smooth"): trail.smooth = int(trail.smooth)
		loaded_trails.append(trail)
	if not loaded_trails.is_empty(): result.trails = loaded_trails
	if data.has("ponds"): result.ponds = data.ponds.map(func(q): return Vector4(q[0], q[1], q[2], q[3]))
	if data.has("spots"): result.spots = data.spots.map(func(s): return [s[0], Vector2(s[1][0], s[1][1]), Vector2(s[2][0], s[2][1])])
	if data.has("stamps"): result.stamps = data.stamps.map(func(q): return Vector4(q[0], q[1], q[2], q[3]))
	return result

## Writes a layout for the game and the bake (used by the layout scene).
static func save_layout(new_trails: Array, new_ponds: Array, new_spots: Array, new_stamps: Array) -> void:
	var data := {"trails": [], "ponds": [], "spots": [], "stamps": []}
	for trail: Dictionary in new_trails:
		var t := trail.duplicate()
		t.points = (trail.points as Array).map(func(p: Vector2): return [snappedf(p.x, 0.01), snappedf(p.y, 0.01)])
		data.trails.append(t)
	for p: Vector4 in new_ponds: data.ponds.append([p.x, p.y, p.z, p.w])
	for s: Array in new_spots: data.spots.append([s[0], [s[1].x, s[1].y], [s[2].x, s[2].y]])
	for s: Vector4 in new_stamps: data.stamps.append([s.x, s.y, s.z, s.w])
	var file := FileAccess.open(LAYOUT_PATH, FileAccess.WRITE)
	file.store_string(JSON.stringify(data, "\t"))
	file.close()

## Re-reads the layout and the baked maps (after a bake from the editor).
static func reload() -> void:
	layout = _load_layout()
	trails = layout.trails
	ponds = layout.ponds
	spots = layout.spots
	stamps = layout.stamps
	paths = _paths()
	if ResourceLoader.exists(HEIGHTS_PATH):
		heights = (ResourceLoader.load(HEIGHTS_PATH, "", ResourceLoader.CACHE_MODE_REPLACE) as Image).get_data().to_float32_array()
		ground = (ResourceLoader.load(GROUND_PATH, "", ResourceLoader.CACHE_MODE_REPLACE) as Image).get_data()

## The river runs at one level in this world.
static func river_level(_z: float) -> float:
	return WATER_LEVEL
