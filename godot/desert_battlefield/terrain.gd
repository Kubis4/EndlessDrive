extends RefCounted
## Shared analytic surface: the visible mesh, suspension and tracks use one height field.

static func height_at(x: float, z: float) -> float:
	var wind := x * 0.075 + z * 0.029 + 0.66 * sin(z * 0.046)
	var ridge := pow(maxf(0.0, sin(wind)), 3.0)
	var h := 1.1 * sin(x * 0.031 + z * 0.022) + 0.7 * cos(z * 0.047)
	h += ridge * (6.8 + 2.5 * sin(z * 0.021 + 1.0))
	h += 0.21 * sin(x * 0.19 + z * 0.09) * sin(z * 0.13)
	var pad := 1.0 - smoothstep(10.0, 24.0, Vector2(x + 19.0, z + 13.0).length())
	return lerpf(h, 0.6, pad)

static func normal_at(x: float, z: float) -> Vector3:
	return Vector3(height_at(x - 0.3, z) - height_at(x + 0.3, z), 0.6,
		height_at(x, z - 0.3) - height_at(x, z + 0.3)).normalized()

static func route(t: float, lane: int = 0) -> Vector3:
	var x := (43.0 + float(lane) * 0.8) * cos(t) + 3.0 * sin(t * 2.0)
	var z := (25.0 + float(lane) * 0.8) * sin(t) + 17.0
	return Vector3(x, height_at(x, z), z)
