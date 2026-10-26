class_name DriveDeformation
extends RefCounted
## Bounded compacted-soil height field, shared by physics and rendered terrain.
## Recent local ruts persist in the journey; not an unbounded world voxel sim.
const CELL_SIZE := 0.25
const MAX_CELLS := 65536
const MAX_DEPTH := 1.10
const MAX_RISE := 0.12
var cells: Dictionary = {}
var track_changes := false
var changed_cells: Dictionary = {}
var _order: Array[Vector2i] = []
var _order_head := 0
var _min_x := INF
var _max_x := -INF


func overlaps_x(from_x: float, to_x: float) -> bool:
	# Bounds may remain conservatively wide after FIFO eviction. This can cause
	# an extra copy, never omit a real rut. Externally supplied dictionaries have
	# unknown bounds and also take the safe path.
	return not cells.is_empty() and (_max_x == -INF or (_max_x >= from_x and _min_x <= to_x))


func depth_at(x: float, z: float) -> float:
	if cells.is_empty(): return 0.0
	var grid := Vector2(x, z) / CELL_SIZE
	var key := Vector2i(floori(grid.x), floori(grid.y))
	var f := grid - Vector2(key)
	return lerpf(lerpf(float(cells.get(key, 0.0)), float(cells.get(key + Vector2i.RIGHT, 0.0)), f.x), lerpf(float(cells.get(key + Vector2i.DOWN, 0.0)), float(cells.get(key + Vector2i.ONE, 0.0)), f.x), f.y)


static func rut_limit(surface: Dictionary) -> float:
	if float(surface.get("paved", 0.0)) > 0.65 or not bool(surface.get("deformable", true)): return 0.0
	var kind := int(surface.get("material", 0))
	if kind in [5, 7, 8, 12]: return 0.0
	var loose := 0.28
	match kind:
		1: loose = 0.16
		3, 13: loose = 0.40
		4, 11: loose = 0.24
		6: loose = 0.36
		9: loose = 0.30
		10: loose = 0.14
	# Maintained roads compact to half the off-road depth; mud overrides support.
	var road_scale := lerpf(1.0, 0.5, clampf(float(surface.get("road", 0.0)), 0.0, 1.0))
	if kind in [3, 13]: road_scale = 1.0
	return loose * road_scale


func _store(key: Vector2i, value: float) -> void:
	if _max_x == -INF and not cells.is_empty():
		_min_x = -INF
		_max_x = INF
	_min_x = minf(_min_x, key.x * CELL_SIZE)
	_max_x = maxf(_max_x, key.x * CELL_SIZE)
	if not cells.has(key):
		if cells.size() >= MAX_CELLS:
			var evicted := _order[_order_head]
			cells.erase(evicted)
			if track_changes: changed_cells.erase(evicted)
			_order_head += 1
			if _order_head >= 4096:
				_order = _order.slice(_order_head)
				_order_head = 0
		_order.append(key)
	cells[key] = value
	if track_changes: changed_cells[key] = value


func compact(point: Vector3, surface: Dictionary, pressure: float = 1.0, direction := Vector2.RIGHT, shear := 0.0) -> bool:
	var limit := rut_limit(surface)
	if limit <= 0.0: return false
	var forward := direction.normalized() if direction.length_squared() > 0.01 else Vector2.RIGHT
	var side := Vector2(-forward.y, forward.x)
	var center := Vector2(point.x, point.z)
	var response := 1.0 - exp(-0.24 * clampf(pressure, 0.01, 2.0) * (1.0 + clampf(shear, 0, 1) * 0.45))
	var bank_limit := minf(MAX_RISE, limit * 0.34)
	var changed := false
	for ix in range(floori((point.x - 0.9) / CELL_SIZE), ceili((point.x + 0.9) / CELL_SIZE) + 1):
		for iz in range(floori((point.z - 0.9) / CELL_SIZE), ceili((point.z + 0.9) / CELL_SIZE) + 1):
			var key := Vector2i(ix, iz)
			var delta := Vector2(key) * CELL_SIZE - center
			var along := 1.0 - smoothstep(0.18, 0.65, absf(delta.dot(forward)))
			var lateral := absf(delta.dot(side))
			var tread := exp(-pow(lateral / 0.29, 2.0))
			var shoulder := exp(-pow((lateral - 0.55) / 0.22, 2.0))
			if along < 0.001 or maxf(tread, shoulder) < 0.005: continue
			var previous := float(cells.get(key, 0.0))
			var target := limit * tread - bank_limit * shoulder
			var delta_depth := maxf(0.0, target - previous) if target >= 0 else minf(0.0, target - previous)
			var value := previous + delta_depth * response * along
			if absf(value - previous) < 0.00001: continue
			_store(key, value)
			changed = true
	return changed


func load_soil(point: Vector3, surface: Dictionary, pressure: float, slip: float, effort: float, dt: float) -> bool:
	# Time-based bearing failure, including stationary wheelspin. A firm road
	# supports the car; soft soil settles under load and shears when driven.
	var softness := float(surface.get("soil_softness", 0.0))
	if softness <= 0.01 or dt <= 0.0 or rut_limit(surface) <= 0.0: return false
	var limit := float(surface.get("soil_depth", 0.0))
	var overload := maxf(0.0, pressure / maxf(0.1, float(surface.get("soil_bearing", 2.0))) - 1.0)
	# Ordinary soil compacts slowly and reaches a shallow bearing layer. Peat
	# and exceptional saturated pockets retain genuine deep bearing failure.
	var failure := smoothstep(0.72, 0.98, softness)
	var target := limit * clampf(overload * lerpf(0.12, 0.30, failure) + slip * effort * lerpf(0.25, 0.95, failure), 0.0, 1.0)
	var rate := softness * (0.045 * overload + 0.32 * slip * effort) * lerpf(0.32, 1.0, failure)
	var changed := false
	# Local pressure bulbs use the same fine signed field as rolling ruts.
	for x in range(floori((point.x - 1.1) / CELL_SIZE), ceili((point.x + 1.1) / CELL_SIZE) + 1):
		for z in range(floori((point.z - 1.1) / CELL_SIZE), ceili((point.z + 1.1) / CELL_SIZE) + 1):
			var weight := 1.0 - smoothstep(0.25, 1.05, (Vector2(x, z) * CELL_SIZE - Vector2(point.x, point.z)).length())
			if weight < 0.01: continue
			var key := Vector2i(x, z)
			var previous := float(cells.get(key, 0.0))
			var value := move_toward(previous, maxf(previous, target * weight), rate * weight * dt)
			if value <= previous + 0.000001: continue
			_store(key, minf(value, MAX_DEPTH))
			changed = true
	return changed


func to_data() -> Array:
	var result: Array = []
	for key: Vector2i in _order.slice(_order_head):
		if cells.has(key): result.append([key.x * CELL_SIZE, key.y * CELL_SIZE, snappedf(float(cells[key]), 0.0001), CELL_SIZE])
	return result


func restore(data: Array) -> void:
	cells.clear()
	_min_x = INF
	_max_x = -INF
	changed_cells.clear()
	_order.clear()
	_order_head = 0
	var legacy: Dictionary = {}
	for entry: Variant in data.slice(maxi(0, data.size() - MAX_CELLS)):
		if not entry is Array or entry.size() not in [3, 4]: continue
		var valid := true
		for value: Variant in entry:
			if not (value is float or value is int) or not is_finite(float(value)): valid = false
		if entry.size() == 4 and (not valid or float(entry[3]) != CELL_SIZE): continue
		if not valid or absf(float(entry[0])) > 100000000 or absf(float(entry[1])) > 100000000: continue
		var value := clampf(float(entry[2]), -MAX_RISE, MAX_DEPTH)
		if entry.size() == 3:
			legacy[Vector2i(int(entry[0]), int(entry[1]))] = value
		else:
			_store(Vector2i(roundi(float(entry[0]) / CELL_SIZE), roundi(float(entry[1]) / CELL_SIZE)), value)
	# Preserve the old metre-sampled field without isolated spikes after migration.
	var blocks := {}
	for key: Vector2i in legacy:
		for dx in [-1, 0]:
			for dz in [-1, 0]: blocks[key + Vector2i(dx, dz)] = true
	for block: Vector2i in blocks:
		for ix in 4:
			for iz in 4:
				var tx := ix * CELL_SIZE
				var tz := iz * CELL_SIZE
				var value := lerpf(lerpf(float(legacy.get(block, 0.0)), float(legacy.get(block + Vector2i.RIGHT, 0.0)), tx), lerpf(float(legacy.get(block + Vector2i.DOWN, 0.0)), float(legacy.get(block + Vector2i.ONE, 0.0)), tx), tz)
				if absf(value) > 0.00001: _store(block * 4 + Vector2i(ix, iz), value)
