extends DriveDeformation
## Quarter-metre soil used by the isolated sand laboratory.
const CELL := 0.25
const LIMIT := 32768
const RUT_LIMIT := 0.20
const BANK_LIMIT := 0.085

func depth_at(x: float, z: float) -> float:
	var grid := Vector2(x, z) / CELL
	var cell := Vector2i(floori(grid.x), floori(grid.y))
	var f := grid - Vector2(cell)
	return lerpf(lerpf(float(cells.get(cell, 0)), float(cells.get(cell + Vector2i.RIGHT, 0)), f.x), lerpf(float(cells.get(cell + Vector2i.DOWN, 0)), float(cells.get(cell + Vector2i.ONE, 0)), f.x), f.y)

func compact(point: Vector3, surface: Dictionary, pressure := 1.0, direction := Vector2.RIGHT, shear := 0.0) -> bool:
	if float(surface.get("paved", 0)) > 0.65 or not surface.get("deformable", true): return false
	var forward := direction.normalized()
	var side := Vector2(-forward.y, forward.x)
	var center := Vector2(point.x, point.z)
	var changed := false
	var response := 1.0 - exp(-0.30 * clampf(pressure, 0.01, 2.0) * (1.0 + clampf(shear, 0, 1) * 0.4))
	for ix in range(floori((point.x - 0.9) / CELL), ceili((point.x + 0.9) / CELL) + 1):
		for iz in range(floori((point.z - 0.9) / CELL), ceili((point.z + 0.9) / CELL) + 1):
			var key := Vector2i(ix, iz)
			var delta := Vector2(key) * CELL - center
			var along := 1.0 - smoothstep(0.18, 0.65, absf(delta.dot(forward)))
			var lateral := absf(delta.dot(side))
			# A continuous bowl and rounded berm replace the abrupt tread/bank
			# branch. The target profile stays curved even after repeated passes.
			var bowl_distance := lateral / 0.29
			var bank_distance := (lateral - 0.55) / 0.22
			var tread := exp(-bowl_distance * bowl_distance)
			var shoulder := exp(-bank_distance * bank_distance)
			if along < 0.001 or maxf(tread, shoulder) < 0.005: continue
			var old := float(cells.get(key, 0))
			var target := RUT_LIMIT * tread - BANK_LIMIT * shoulder
			var delta_depth := maxf(0, target - old) if target >= 0 else minf(0, target - old)
			var value := old + delta_depth * response * along
			if absf(value - old) < 0.00001: continue
			if not cells.has(key):
				if cells.size() >= LIMIT: cells.erase(_order.pop_front())
				_order.append(key)
			cells[key] = value
			changed = true
	return changed

func load_soil(point: Vector3, surface: Dictionary, pressure: float, slip: float, effort: float, dt: float) -> bool:
	if slip * effort < 0.2: return false
	return compact(point, surface, pressure * dt * slip * effort * 3.0)

func to_data() -> Array:
	var result: Array = []
	for key: Vector2i in _order:
		if cells.has(key): result.append([key.x * CELL, key.y * CELL, float(cells[key])])
	return result

func restore(data: Array) -> void:
	cells.clear()
	_order.clear()
	for entry: Variant in data.slice(maxi(0, data.size() - LIMIT)):
		if not entry is Array or entry.size() != 3: continue
		var valid := true
		for value: Variant in entry:
			if not (value is float or value is int) or not is_finite(value): valid = false
		if not valid: continue
		var key := Vector2i(roundi(float(entry[0]) / CELL), roundi(float(entry[1]) / CELL))
		if not cells.has(key): _order.append(key)
		cells[key] = clampf(float(entry[2]), -BANK_LIMIT, RUT_LIMIT)
