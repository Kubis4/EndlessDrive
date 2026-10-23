class_name DriveDeformation
extends RefCounted
## Bounded compacted-soil height field, shared by physics and rendered terrain.
## Recent local ruts persist in the journey; not an unbounded world voxel sim.
const MAX_CELLS := 4096
const MAX_DEPTH := 1.10
var cells: Dictionary = {}
var _order: Array[Vector2i] = []


func depth_at(x: float, z: float) -> float:
	if cells.is_empty(): return 0.0
	var ix := floori(x)
	var iz := floori(z)
	var tx := x - ix
	var tz := z - iz
	return lerpf(lerpf(float(cells.get(Vector2i(ix, iz), 0.0)), float(cells.get(Vector2i(ix + 1, iz), 0.0)), tx),
		lerpf(float(cells.get(Vector2i(ix, iz + 1), 0.0)), float(cells.get(Vector2i(ix + 1, iz + 1), 0.0)), tx), tz)


func compact(point: Vector3, surface: Dictionary, pressure: float = 1.0) -> bool:
	if float(surface.get("paved", 1.0)) > 0.65: return false
	var snow := float(surface.get("snow", 0.0))
	var wet := float(surface.get("water", 0.0)) > 0.02
	var limit := 0.18 if snow > 0.4 else (0.16 if wet else 0.09)
	var increment := (0.025 if snow > 0.4 else (0.018 if wet else 0.009)) * clampf(pressure, 0.5, 1.8)
	var changed := false
	for x in range(floori(point.x) - 1, floori(point.x) + 3):
		for z in range(floori(point.z) - 1, floori(point.z) + 3):
			var weight := 1.0 - smoothstep(0.0, 1.45, Vector2(x - point.x, z - point.z).length())
			if weight < 0.01: continue
			var key := Vector2i(x, z)
			var previous := float(cells.get(key, 0.0))
			var value := minf(limit, previous + increment * weight)
			if value <= previous + 0.00001: continue
			if not cells.has(key):
				if cells.size() >= MAX_CELLS: cells.erase(_order.pop_front())
				_order.append(key)
			cells[key] = value
			changed = true
	return changed


func load_soil(point: Vector3, surface: Dictionary, pressure: float, slip: float, effort: float, dt: float) -> bool:
	# Time-based bearing failure, including stationary wheelspin. A firm road
	# supports the car; soft soil settles under load and shears when driven.
	var softness := float(surface.get("soil_softness", 0.0))
	if softness <= 0.01 or dt <= 0.0: return false
	var limit := float(surface.get("soil_depth", 0.0))
	var overload := maxf(0.0, pressure / maxf(0.1, float(surface.get("soil_bearing", 2.0))) - 1.0)
	var target := limit * clampf(overload * 0.30 + slip * effort * 0.95, 0.0, 1.0)
	var rate := softness * (0.045 * overload + 0.32 * slip * effort)
	var changed := false
	# Broad pressure bulbs below each tyre; smooth enough for the streamed
	# two-metre collision grid. This is a height field, not a voxel soil solver.
	for x in range(floori(point.x) - 2, floori(point.x) + 4):
		for z in range(floori(point.z) - 2, floori(point.z) + 4):
			var weight := 1.0 - smoothstep(0.65, 2.25, Vector2(x - point.x, z - point.z).length())
			if weight < 0.01: continue
			var key := Vector2i(x, z)
			var previous := float(cells.get(key, 0.0))
			var value := move_toward(previous, maxf(previous, target * weight), rate * weight * dt)
			if value <= previous + 0.000001: continue
			if not cells.has(key):
				if cells.size() >= MAX_CELLS: cells.erase(_order.pop_front())
				_order.append(key)
			cells[key] = minf(value, MAX_DEPTH)
			changed = true
	return changed


func to_data() -> Array:
	var result: Array = []
	for key: Vector2i in _order:
		if cells.has(key): result.append([key.x, key.y, snappedf(float(cells[key]), 0.0001)])
	return result


func restore(data: Array) -> void:
	cells.clear()
	_order.clear()
	for entry: Variant in data.slice(maxi(0, data.size() - MAX_CELLS)):
		if not entry is Array or entry.size() != 3: continue
		var valid := true
		for value: Variant in entry:
			if not (value is float or value is int) or not is_finite(float(value)): valid = false
		if not valid or absf(float(entry[0])) > 100000000 or absf(float(entry[1])) > 100000000: continue
		var key := Vector2i(int(entry[0]), int(entry[1]))
		if not cells.has(key): _order.append(key)
		cells[key] = clampf(float(entry[2]), 0.0, MAX_DEPTH)
