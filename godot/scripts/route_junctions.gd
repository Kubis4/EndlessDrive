extends RefCounted
## Local road networks can cross and double back in world X/Z. Their complete
## geometry exists independently of the exit selected for the next region.
static func local_paths(layout: String) -> Array[PackedVector2Array]:
	var paths: Array[PackedVector2Array] = []
	match layout:
		"x":
			paths.append(PackedVector2Array([Vector2(0, 0), Vector2(44, -34), Vector2(120, 0), Vector2(240, 42)]))
			paths.append(PackedVector2Array([Vector2(0, 0), Vector2(44, 34), Vector2(120, 0), Vector2(240, -42)]))
		"switchback":
			for side: float in [-1.0, 1.0]:
				var path := PackedVector2Array([Vector2(0, 0), Vector2(62, side * 42)])
				# A real hairpin makes the car face back towards decreasing X.
				for i in 17:
					var angle := -PI * 0.5 + float(i) / 16.0 * PI
					path.append(Vector2(112, side * 22) + Vector2(cos(angle), -side * sin(angle)) * 20.0)
				path.append(Vector2(76, side * 2))
				for i in 17:
					var angle := PI * 0.5 + float(i) / 16.0 * PI
					path.append(Vector2(76, -side * 14) + Vector2(cos(angle), side * sin(angle)) * 16.0)
				path.append(Vector2(174, -side * 30))
				path.append(Vector2(240, -side * 42))
				paths.append(path)
	return paths

static func intersections(paths: Array[PackedVector2Array], x: float) -> PackedFloat32Array:
	var values: Array[float] = []
	for path in paths:
		for i in path.size() - 1:
			var a := path[i]
			var b := path[i + 1]
			if x < minf(a.x, b.x) - 0.0001 or x > maxf(a.x, b.x) + 0.0001: continue
			if absf(b.x - a.x) < 0.00001: continue
			var z := lerpf(a.y, b.y, (x - a.x) / (b.x - a.x))
			var duplicate := false
			for value in values: duplicate = duplicate or absf(value - z) < 0.05
			if not duplicate: values.append(z)
	values.sort()
	return PackedFloat32Array(values)

static func closest(segments: Array[Vector4], point: Vector2) -> Dictionary:
	var distance := INF
	var closest := point
	var tangent := Vector2.RIGHT
	for segment in segments:
		var a := Vector2(segment.x, segment.y)
		var b := Vector2(segment.z, segment.w)
		var delta := b - a
		var p := a + delta * clampf((point - a).dot(delta) / maxf(delta.length_squared(), 0.00001), 0.0, 1.0)
		var d := point.distance_squared_to(p)
		if d < distance:
			distance = d
			closest = p
			tangent = delta.normalized()
	var sign_value := signf(tangent.cross(point - closest))
	return {"distance": sqrt(distance) * (sign_value if sign_value != 0.0 else 1.0), "point": closest, "tangent": tangent}
