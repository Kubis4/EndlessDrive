extends RefCounted
## Refines only touched terrain quads on the worker. Render and collision use
## exactly the same fine triangles; the surrounding world keeps its coarse grid.
static func build(field: DriveDeformation, source: Array, rest: PackedFloat32Array, width: int, band: int) -> Dictionary:
	var started := Time.get_ticks_usec()
	var result := DriveTerrainBake.deform_band(field, source[Mesh.ARRAY_VERTEX], rest, width, band)
	var arrays := DriveTerrainBake.band_arrays(source, width, band)
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
	var colors: PackedColorArray = arrays[Mesh.ARRAY_COLOR]
	var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV]
	var uv2: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV2]
	var weights: Array[PackedFloat32Array] = []
	for channel in 4: weights.append(arrays[Mesh.ARRAY_CUSTOM0 + channel])
	var affected := {}
	var min_x := vertices[0].x - 2.0
	var max_x := vertices[-1].x + 2.0
	for key: Vector2i in field.cells:
		var point := Vector2(key) * DriveDeformation.CELL_SIZE
		if point.x < min_x or point.x > max_x: continue
		if absf(float(field.cells[key])) < 0.00001: continue
		var cell := Vector2i(floori(point.x), floori(point.y))
		for dx in range(-1, 2):
			for dz in range(-1, 2): affected[cell + Vector2i(dx, dz)] = true
	for i in vertices.size():
		vertices[i] = result.vertices[int(result.first) + i]
		var p := vertices[i]
		weights[3][i * 4 + 3] = 1.0 + rest[int(result.first) + i] - p.y
		if affected.has(Vector2i(floori(p.x), floori(p.z))):
			normals[i] = _normal(normals[i], field, p)
	# Coarse attributes remain fixed while extra fine vertices are appended.
	var base_colors := colors
	var base_normals: PackedVector3Array = source[Mesh.ARRAY_NORMAL].slice(int(result.first), int(result.end))
	var base_uvs := uvs
	var base_uv2 := uv2
	var base_weights := weights.duplicate()
	var indices := PackedInt32Array()
	var fine_faces := {}
	var refined := 0
	for row in DriveTerrainBake.BAND_ROWS:
		for col in width - 1:
			var a := row * width + col
			var p := vertices[a]
			var q := vertices[a + width + 1]
			var middle := (p + q) * 0.5
			var touched := affected.has(Vector2i(floori(middle.x), floori(middle.z)))
			# Outer low-detail scenery is never refined into enormous rectangles.
			if not touched or q.z - p.z > 3.0:
				indices.append_array(PackedInt32Array([a, a + width, a + 1, a + width, a + width + 1, a + 1]))
				continue
			refined += 1
			var origin := vertices.size()
			for ix in 5:
				for iz in 5:
					var f := Vector2(ix, iz) * 0.25
					var ids := Vector3i(a, a + width, a + 1) if f.x + f.y <= 1.0 else Vector3i(a + width + 1, a + 1, a + width)
					var w := Vector3(1.0 - f.x - f.y, f.x, f.y) if f.x + f.y <= 1.0 else Vector3(f.x + f.y - 1.0, 1.0 - f.x, 1.0 - f.y)
					var point := vertices[ids.x] * w.x + vertices[ids.y] * w.y + vertices[ids.z] * w.z
					var rest_height := rest[int(result.first) + ids.x] * w.x + rest[int(result.first) + ids.y] * w.y + rest[int(result.first) + ids.z] * w.z
					point.y = rest_height - field.depth_at(point.x, point.z)
					vertices.append(point)
					var n := (base_normals[ids.x] * w.x + base_normals[ids.y] * w.y + base_normals[ids.z] * w.z).normalized()
					normals.append(_normal(n, field, point))
					colors.append(base_colors[ids.x] * w.x + base_colors[ids.y] * w.y + base_colors[ids.z] * w.z)
					uvs.append(base_uvs[ids.x] * w.x + base_uvs[ids.y] * w.y + base_uvs[ids.z] * w.z)
					uv2.append(base_uv2[ids.x] * w.x + base_uv2[ids.y] * w.y + base_uv2[ids.z] * w.z)
					for channel in 4:
						for component in 4:
							weights[channel].append(base_weights[channel][ids.x * 4 + component] * w.x + base_weights[channel][ids.y * 4 + component] * w.y + base_weights[channel][ids.z * 4 + component] * w.z)
					# Fine normals already include soil relief; suppress the coarse
					# derivative correction stored in the two spare shader channels.
					weights[3][(vertices.size() - 1) * 4 + 2] = point.y
					weights[3][(vertices.size() - 1) * 4 + 3] = 1.0 + rest_height - point.y
			var faces := PackedVector3Array()
			for ix in 4:
				for iz in 4:
					var i := origin + ix * 5 + iz
					var triangle := PackedInt32Array([i, i + 5, i + 1, i + 5, i + 6, i + 1])
					indices.append_array(triangle)
					for v in triangle: faces.append(vertices[v])
			fine_faces[Vector2i(row, col)] = faces
	var collision := PackedVector3Array()
	var columns: Array[int] = [0, 1, 2]
	for col in range(3, width - 3, 2): columns.append(col)
	columns.append_array([width - 3, width - 2, width - 1])
	for row in range(0, DriveTerrainBake.BAND_ROWS, 2):
		for column in columns.size() - 1:
			var left := columns[column]
			var right := columns[column + 1]
			var touched := false
			for dx in 2:
				for col in range(left, right): touched = touched or fine_faces.has(Vector2i(row + dx, col))
			if touched:
				for dx in 2:
					for col in range(left, right):
						var key := Vector2i(row + dx, col)
						if fine_faces.has(key): collision.append_array(fine_faces[key])
						else:
							var i := key.x * width + key.y
							for v in [i, i + width, i + 1, i + width, i + width + 1, i + 1]: collision.append(vertices[v])
			else:
				var a := row * width + left
				var b := row * width + right
				for v in [a, a + 2 * width, b, a + 2 * width, b + 2 * width, b]: collision.append(vertices[v])
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colors
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_TEX_UV2] = uv2
	arrays[Mesh.ARRAY_INDEX] = indices
	for channel in 4: arrays[Mesh.ARRAY_CUSTOM0 + channel] = weights[channel]
	result.arrays = arrays
	result.faces = collision
	result.refined_quads = refined
	result.worker_ms = (Time.get_ticks_usec() - started) / 1000.0
	return result

static func _normal(base: Vector3, field: DriveDeformation, p: Vector3) -> Vector3:
	var dx := (field.depth_at(p.x + 0.125, p.z) - field.depth_at(p.x - 0.125, p.z)) * 4.0
	var dz := (field.depth_at(p.x, p.z + 0.125) - field.depth_at(p.x, p.z - 0.125)) * 4.0
	return (base + Vector3(dx, 0.0, dz) * maxf(base.y, 0.2)).normalized()


static func apply_commands(profile: DriveTerrain, commands: Array[Dictionary], jobs: Array[Dictionary]) -> Dictionary:
	# A private numeric profile owns all mutations. Never stamp the live field
	# from a worker or read the scene tree here.
	var field := profile.deformation
	field.track_changes = true
	field.changed_cells.clear()
	for command in commands:
		if int(command.kind) == 0:
			var start: Vector3 = command.start
			var end: Vector3 = command.end
			var steps := maxi(1, ceili(Vector2(end.x - start.x, end.z - start.z).length() / 0.20))
			for step in steps:
				var point := start.lerp(end, float(step + 1) / steps)
				field.compact(point, profile.sample(point.x, point.z), float(command.pressure), command.direction, float(command.shear))
		else:
			var point: Vector3 = command.point
			field.load_soil(point, profile.sample(point.x, point.z), float(command.pressure), float(command.slip), float(command.effort), float(command.dt))
	var dirty := {}
	for key: Vector2i in field.changed_cells:
		var x := key.x * DriveDeformation.CELL_SIZE
		# Include the shared seam when a modified sample is on a band edge.
		for strip in range(floori((x - 0.001) / DriveTerrainBake.BAND_ROWS), floori((x + 0.001) / DriveTerrainBake.BAND_ROWS) + 1):
			dirty[Vector2i(floori(float(strip) / DriveTerrainBake.BAND_COUNT), posmod(strip, DriveTerrainBake.BAND_COUNT))] = true
	var bands: Array[Dictionary] = []
	for job in jobs:
		# Jobs also include explicit rebuilds of restored terrain.
		if not bool(job.get("force", false)) and not dirty.has(job.key): continue
		bands.append({"key": job.key, "owner_id": job.owner_id, "result": build(field, job.arrays, job.rest, int(job.width), int(job.key.y))})
	return {"changes": field.changed_cells, "dirty": dirty, "bands": bands}
