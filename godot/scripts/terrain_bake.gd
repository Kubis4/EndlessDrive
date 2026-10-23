class_name DriveTerrainBake
extends RefCounted
## Worker-only numeric mesh construction. Never touches the active scene tree.

static func bake(profile: DriveTerrain, index: int) -> Dictionary:
	var started := Time.get_ticks_usec()
	var offsets := PackedFloat32Array([-144, -120, -104])
	# Both fork lanes remain in the detailed grid even when 84 m apart. Follow
	# the continuous chosen road, never the corridor midpoint which jumps at exits.
	for zi in range(-48, 49): offsets.append(zi * 2.0)
	offsets.append_array(PackedFloat32Array([104, 120, 144]))
	var width := offsets.size()
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var colors := PackedColorArray()
	var uvs := PackedVector2Array()
	var uv2 := PackedVector2Array()
	for xi in 49:
		var x := index * 96.0 + xi * 2.0
		for lateral: float in offsets:
			var z := profile.center_at(x) + lateral
			var data := profile.sample(x, z, false)
			vertices.append(Vector3(x, profile.ground_height_at(x, z), z))
			colors.append(data.color)
			uvs.append(Vector2(x, z) * 0.13)
			# Preserve the sign until the fragment shader. Interpolating abs(distance)
			# across a lane centre produced repeated diamonds instead of straight ruts.
			uv2.append(Vector2(float(data.lateral), float(data.paved)))
	for xi in 49:
		for zi in width:
			var left := vertices[xi * width + maxi(0, zi - 1)]
			var right := vertices[xi * width + mini(width - 1, zi + 1)]
			var before := vertices[maxi(0, xi - 1) * width + zi]
			var after := vertices[mini(48, xi + 1) * width + zi]
			normals.append((right - left).cross(after - before).normalized())
	var indices := PackedInt32Array()
	var faces := PackedVector3Array()
	for xi in 48:
		for zi in range(width - 1):
			var a := xi * width + zi
			for vertex_index: int in [a, a + width, a + 1, a + width, a + width + 1, a + 1]:
				indices.append(vertex_index)
				faces.append(vertices[vertex_index])
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colors
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_TEX_UV2] = uv2
	arrays[Mesh.ARRAY_INDEX] = indices
	var water := PackedVector3Array()
	var water_pools: Dictionary = {}
	for triangle in range(0, faces.size(), 3):
		var cx := (faces[triangle].x + faces[triangle + 1].x + faces[triangle + 2].x) / 3.0
		var row := floori(cx * 0.5)
		if not water_pools.has(row): water_pools[row] = profile.pools_near(row * 2.0 + 1.0)
		for pool: Dictionary in water_pools[row]:
			var polygon: Array[Vector3] = []
			for edge in 3:
				var a := faces[triangle + edge]
				var b := faces[triangle + (edge + 1) % 3]
				var da := _depth(profile, pool, a)
				var db := _depth(profile, pool, b)
				if da > 0: polygon.append(Vector3(a.x, profile.water_level(pool, a.x, a.z) + 0.025, a.z))
				if (da > 0) != (db > 0):
					var p := a.lerp(b, da / (da - db))
					polygon.append(Vector3(p.x, profile.water_level(pool, p.x, p.z) + 0.025, p.z))
			for fan in range(1, polygon.size() - 1):
				water.append_array(PackedVector3Array([polygon[0], polygon[fan], polygon[fan + 1]]))
	var deck := PackedVector3Array()
	for xi in 48:
		var x := index * 96.0 + xi * 2.0
		var feature := profile.feature_at(x + 1)
		if feature.is_empty() or feature.kind != "bridge" or absf(x + 1 - float(feature.x)) > 28: continue
		for corner: Vector2 in [Vector2(0, -5.2), Vector2(2, -5.2), Vector2(0, 5.2), Vector2(2, -5.2), Vector2(2, 5.2), Vector2(0, 5.2)]:
			var dx := x + corner.x
			var z := profile.center_at(dx) + corner.y
			deck.append(Vector3(dx, profile.height_at(dx, z), z))
	var bands: Array[PackedVector3Array] = []
	var rest_heights := PackedFloat32Array()
	for point: Vector3 in vertices: rest_heights.append(point.y + profile.deformation.depth_at(point.x, point.z))
	var band_stride := 16 * (width - 1) * 6
	for band in 3: bands.append(faces.slice(band * band_stride, (band + 1) * band_stride))
	return {"index": index, "arrays": arrays, "rest_heights": rest_heights, "faces": faces, "collision_bands": bands, "width": width, "water": water, "deck": deck, "worker_ms": (Time.get_ticks_usec() - started) / 1000.0}


static func deform_band(field: DriveDeformation, vertices: PackedVector3Array, rest: PackedFloat32Array, width: int, band: int) -> Dictionary:
	var first := band * 16 * width
	var end := (band * 16 + 17) * width
	for index in range(first, end):
		var p := vertices[index]
		p.y = rest[index] - field.depth_at(p.x, p.z)
		vertices[index] = p
	var faces := PackedVector3Array()
	for xi in range(band * 16, (band + 1) * 16):
		for zi in range(width - 1):
			var a := xi * width + zi
			for index: int in [a, a + width, a + 1, a + width, a + width + 1, a + 1]: faces.append(vertices[index])
	return {"vertices": vertices, "faces": faces, "first": first, "end": end, "band": band}


static func _depth(profile: DriveTerrain, pool: Dictionary, point: Vector3) -> float:
	var radius := Vector2((point.x - float(pool.x)) / float(pool.rx), (point.z - float(pool.z)) / float(pool.rz)).length()
	return minf(profile.water_level(pool, point.x, point.z) - point.y,
		(1.0 - radius) * minf(float(pool.rx), float(pool.rz)))
