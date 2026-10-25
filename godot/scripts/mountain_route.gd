extends RefCounted
## A single continuous mountain road with four true hairpins. Y is the
## road's elevation, so returning towards decreasing X still climbs.
static func local_path() -> PackedVector3Array:
	var plan := PackedVector2Array([Vector2(0, 0), Vector2(8, 0), Vector2(20, 0), Vector2(120, -40), Vector2(200, -40)])
	for pair: Vector2 in [Vector2(200, -24), Vector2(146, 8), Vector2(400, 40), Vector2(346, 72)]:
		var backwards := pair.x == 146 or pair.x == 346
		for i in 17:
			var angle := -PI * 0.5 + (float(i) / 16.0 * PI) * (-1.0 if backwards else 1.0)
			plan.append(pair + Vector2(cos(angle), sin(angle)) * 16.0)
	plan.append(Vector2(540, 88))
	plan.append(Vector2(660, 30))
	plan.append(Vector2(716, 0))
	plan.append(Vector2(732, 0))
	plan.append(Vector2(740, 0))
	var length := 0.0
	for i in plan.size() - 1: length += plan[i].distance_to(plan[i + 1])
	var travelled := 0.0
	var result := PackedVector3Array()
	for i in plan.size():
		if i > 0: travelled += plan[i - 1].distance_to(plan[i])
		var t := travelled / length
		var elevation := 60.0 * smoothstep(0.0, 0.70, t) * (1.0 - smoothstep(0.74, 1.0, t))
		result.append(Vector3(plan[i].x, elevation, plan[i].y))
	return result

static func intersections(path: PackedVector3Array, x: float) -> PackedFloat32Array:
	var values: Array[float] = []
	for i in path.size() - 1:
		var a := path[i]
		var b := path[i + 1]
		if x < minf(a.x, b.x) - 0.0001 or x > maxf(a.x, b.x) + 0.0001 or absf(b.x - a.x) < 0.00001: continue
		var z := lerpf(a.z, b.z, (x - a.x) / (b.x - a.x))
		var duplicate := false
		for value in values: duplicate = duplicate or absf(value - z) < 0.05
		if not duplicate: values.append(z)
	values.sort()
	return PackedFloat32Array(values)

static func closest(segments: Array, point: Vector2) -> Dictionary:
	var best := INF
	var result := {}
	for segment: Array in segments:
		var a: Vector3 = segment[0]
		var b: Vector3 = segment[1]
		var origin := Vector2(a.x, a.z)
		var delta := Vector2(b.x - a.x, b.z - a.z)
		var t := clampf((point - origin).dot(delta) / maxf(delta.length_squared(), 0.00001), 0.0, 1.0)
		var closest := origin + delta * t
		var distance := point.distance_squared_to(closest)
		if distance < best:
			best = distance
			var side := signf(delta.cross(point - closest))
			result = {"point": closest, "tangent": delta.normalized(), "height": lerpf(a.y, b.y, t), "distance": sqrt(distance) * (side if side != 0.0 else 1.0)}
	return result
