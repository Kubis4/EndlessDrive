extends RefCounted
## Pure geology preparation shared by the numeric terrain worker and previews.

static func point(profile: DriveTerrain, x: float, offset: float) -> Vector3:
	var z := profile.center_at(x) + offset
	return Vector3(x, profile.height_at(x, z), z)

static func surface(profile: DriveTerrain, feature_x: float, x: float, offset: float) -> Vector3:
	var center := point(profile, x, 0)
	var tangent := (point(profile, x + 0.5, 0) - point(profile, x - 0.5, 0)).normalized()
	var right := Vector3(-tangent.z, 0, tangent.x).normalized()
	var progress := clampf((x - feature_x + 36.0) / 72.0, 0.0, 1.0)
	var crest := pow(sin(progress * PI), 0.85)
	var width := profile.boundary_width(x) + 16.0
	var shoulder := maxf(0.12, 1.0 - pow(absf(offset + 3.0) / width, 1.25))
	var height := (11.5 + crest * 15.0) * shoulder
	height += sin(x * 0.11 + offset * 0.17) * crest * 0.8
	height *= 1.0 - smoothstep(36.0, 66.0, absf(x - feature_x))
	var point := center + right * offset
	point.y = maxf(profile.height_at(point.x, point.z) + 0.35, center.y + height)
	return point

static func roof(profile: DriveTerrain, feature_x: float, section: int) -> PackedVector3Array:
	var rings: Array[PackedVector3Array] = []
	for row in range(section * 6, section * 6 + 7):
		var x := feature_x - 36 + row * 3.0
		var ring := PackedVector3Array()
		for arc in 13: ring.append(surface(profile, feature_x, x, cos(arc * PI / 12.0) * 6.45))
		rings.append(ring)
	var faces := PackedVector3Array()
	for row in 6:
		for arc in 12:
			faces.append_array(PackedVector3Array([rings[row][arc], rings[row + 1][arc], rings[row][arc + 1], rings[row + 1][arc], rings[row + 1][arc + 1], rings[row][arc + 1]]))
	# Portal faces connect the geological ridge to the actual arch, leaving
	# the vehicle-sized opening intact at both ends of the mountain.
	if section in [0, 3]:
		var x := feature_x + (-36.0 if section == 0 else 36.0)
		var center := point(profile, x, 0)
		var tangent := (point(profile, x + 0.5, 0) - point(profile, x - 0.5, 0)).normalized()
		var right := Vector3(-tangent.z, 0, tangent.x).normalized()
		for arc in 12:
			var angle_a := arc * PI / 12.0
			var angle_b := (arc + 1) * PI / 12.0
			var a := center + right * (cos(angle_a) * 6.4) + Vector3.UP * (2.4 + sin(angle_a) * 6.4)
			var b := center + right * (cos(angle_b) * 6.4) + Vector3.UP * (2.4 + sin(angle_b) * 6.4)
			var outer_a := surface(profile, feature_x, x, cos(angle_a) * 6.45)
			var outer_b := surface(profile, feature_x, x, cos(angle_b) * 6.45)
			faces.append_array(PackedVector3Array([a, outer_a, b, b, outer_a, outer_b]))
	return faces

static func side(profile: DriveTerrain, feature_x: float, side: float) -> PackedVector3Array:
	var rings: Array[PackedVector3Array] = []
	for step in 45:
		var x := feature_x - 66 + step * 3.0
		var center := point(profile, x, 0)
		var tangent := (point(profile, x + 0.5, 0) - point(profile, x - 0.5, 0)).normalized()
		var right := Vector3(-tangent.z, 0, tangent.x).normalized()
		var width := profile.boundary_width(x)
		var ring := PackedVector3Array([center + right * side * 6.45 - Vector3.UP * 5.0])
		var lateral := [6.45, 12.0, 22.0, 31.0, width + 10.0]
		for edge in lateral.size():
			ring.append(surface(profile, feature_x, x, side * float(lateral[edge])))
		ring.append(center + right * side * (width + 10.0) - Vector3.UP * 5.0)
		rings.append(ring)
	var faces := PackedVector3Array()
	for row in rings.size() - 1:
		for edge in rings[row].size() - 1:
			faces.append_array(PackedVector3Array([rings[row][edge], rings[row + 1][edge], rings[row][edge + 1], rings[row + 1][edge], rings[row + 1][edge + 1], rings[row][edge + 1]]))
	for end: int in [0, rings.size() - 1]:
		for edge in range(1, rings[end].size() - 1): faces.append_array(PackedVector3Array([rings[end][0], rings[end][edge], rings[end][edge + 1]]))
	return faces

static func bake(profile: DriveTerrain, feature_x: float) -> Dictionary:
	var result := {"north": side(profile, feature_x, -1.0), "south": side(profile, feature_x, 1.0)}
	for direction: float in [-1.0, 1.0]:
		var label := "north" if direction < 0 else "south"
		var solid: PackedVector3Array = result[label].duplicate()
		# The visible foot slopes continue beyond the portals. Internal core
		# faces close the mountain at its steep section and stop shoulder bypasses
		# even when a ray/car starts inside the outer concave hill surface.
		for end: float in [-36.0, 36.0]:
			var x := feature_x + end
			var center := point(profile, x, 0)
			var tangent := (point(profile, x + 0.5, 0) - point(profile, x - 0.5, 0)).normalized()
			var right := Vector3(-tangent.z, 0, tangent.x).normalized()
			var width := profile.boundary_width(x) + 10.0
			var ring := PackedVector3Array([center + right * direction * 6.45 - Vector3.UP * 5.0])
			for offset: float in [6.45, 12.0, 22.0, 31.0, width]: ring.append(surface(profile, feature_x, x, direction * offset))
			ring.append(center + right * direction * width - Vector3.UP * 5.0)
			for edge in range(1, ring.size() - 1): solid.append_array(PackedVector3Array([ring[0], ring[edge], ring[edge + 1]]))
		result["collision_" + label] = solid
	for section in 4: result["roof%d" % section] = roof(profile, feature_x, section)
	return result
