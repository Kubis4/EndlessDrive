extends RefCounted
## Numeric, world-locked tree cells shared by worker planning and live scenery.
const SPACING := 14.0
static func cell(point: Vector3) -> Vector2i:
	return Vector2i(floori(point.x / SPACING), floori(point.z / SPACING))
static func point(seed_value: int, key: Vector2i) -> Vector3:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value + key.x * 73856093 + key.y * 19349663
	return Vector3((key.x + 0.5) * SPACING + rng.randf_range(-0.8, 0.8), 0, (key.y + 0.5) * SPACING + rng.randf_range(-0.8, 0.8))
