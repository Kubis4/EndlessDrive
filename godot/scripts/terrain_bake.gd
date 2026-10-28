class_name DriveTerrainBake
extends RefCounted
## Worker-only numeric mesh construction. Never touches the active scene tree.
const STEP := 1.0
const ROWS := 96
const BAND_ROWS := 8
const BAND_COUNT := ROWS / BAND_ROWS
const MATERIAL_FORMAT := (Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM0_SHIFT) | (Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM1_SHIFT) | (Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM2_SHIFT) | (Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM3_SHIFT)

static func bake(profile: DriveTerrain, index: int, include_obstacles: bool = false) -> Dictionary:
	var started := Time.get_ticks_usec()
	var obstacle_regions := {}
	if include_obstacles:
		for region in range(floori((index * 96.0 - 35) / 960), floori(((index + 1) * 96.0 + 35) / 960) + 1):
			obstacle_regions[region] = profile.obstacles_in_region(region)
	var offsets := PackedFloat32Array([-144, -120, -104])
	# Both fork lanes remain in the detailed grid even when 84 m apart. Follow
	# the continuous chosen road, never the corridor midpoint which jumps at exits.
	for zi in range(-96, 97): offsets.append(zi * STEP)
	offsets.append_array(PackedFloat32Array([104, 120, 144]))
	var width := offsets.size()
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var rest_heights := PackedFloat32Array()
	var colors := PackedColorArray()
	var uvs := PackedVector2Array()
	var uv2 := PackedVector2Array()
	var material_weights: Array[PackedFloat32Array] = []
	for channel in 4:
		var weights := PackedFloat32Array()
		weights.resize((ROWS + 1) * width * 4)
		material_weights.append(weights)
	for xi in ROWS + 1:
		var x := index * 96.0 + xi * STEP
		for lateral: float in offsets:
			var z := profile.corridor_center(x) + lateral
			var data := profile.sample(x, z, false, true)
			var height := profile.ground_height_at(x, z)
			vertices.append(Vector3(x, height, z))
			rest_heights.append(height + profile.deformation.depth_at(x, z))
			# Existing wheel channels are shallow physical depressions. A small
			# baked soil tint makes their compacted bottom readable from above.
			var tint := (data.color as Color).darkened(minf(profile.authored_rut_depth(x, z) * 0.35, 0.065))
			# Alpha carries road coverage, not transparency. Refined soil vertices
			# interpolate it with RGB, keeping road wear fixed during excavation.
			tint.a = float(data.road)
			colors.append(tint)
			# World position supplies texture coordinates. UV carries the shared
			# physical material ID and moisture for the surface shader instead.
			uvs.append(Vector2(float(data.get("material", 2)), float(data.get("soil_moisture", 0))))
			var material_id := clampi(int(data.get("material", 2)), 0, 13)
			var layers: PackedFloat32Array = data.get("material_weights", PackedFloat32Array())
			if layers.is_empty():
				material_weights[material_id / 4][(vertices.size() - 1) * 4 + material_id % 4] = 1.0
			else:
				for layer in layers.size():
					material_weights[layer / 4][(vertices.size() - 1) * 4 + layer % 4] = layers[layer]
			# Preserve the sign until the fragment shader. Interpolating abs(distance)
			# across a lane centre produced repeated diamonds instead of straight ruts.
			uv2.append(Vector2(float(data.lateral), float(data.paved)))
	for xi in ROWS + 1:
		for zi in width:
			var left := vertices[xi * width + maxi(0, zi - 1)]
			var right := vertices[xi * width + mini(width - 1, zi + 1)]
			var before := vertices[maxi(0, xi - 1) * width + zi]
			var after := vertices[mini(ROWS, xi + 1) * width + zi]
			# Keep an invariant undisturbed normal field, including restored
			# saves. The shader/fine mesh adds soil relief exactly once.
			left.y = rest_heights[xi * width + maxi(0, zi - 1)]
			right.y = rest_heights[xi * width + mini(width - 1, zi + 1)]
			before.y = rest_heights[maxi(0, xi - 1) * width + zi]
			after.y = rest_heights[mini(ROWS, xi + 1) * width + zi]
			# The neighbouring chunk sees the same central difference. Clamping
			# normals to each chunk's end drew a lit seam across every road.
			if xi == 0 or xi == ROWS:
				var nx := index * 96.0 + (xi - 1 if xi == 0 else xi + 1)
				var nz := profile.corridor_center(nx) + offsets[zi]
				var adjacent := Vector3(nx, profile.ground_height_at(nx, nz) + profile.deformation.depth_at(nx, nz), nz)
				if xi == 0: before = adjacent
				else: after = adjacent
			normals.append((right - left).cross(after - before).normalized())
	var indices := PackedInt32Array()
	var faces := PackedVector3Array()
	for xi in ROWS:
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
	for channel in 4: arrays[Mesh.ARRAY_CUSTOM0 + channel] = material_weights[channel]
	arrays[Mesh.ARRAY_INDEX] = indices
	var water := PackedVector3Array()
	var water_depths := PackedFloat32Array()
	var water_pools: Dictionary = {}
	for triangle in range(0, faces.size(), 3):
		var cx := (faces[triangle].x + faces[triangle + 1].x + faces[triangle + 2].x) / 3.0
		var row := floori(cx * 0.5)
		if not water_pools.has(row): water_pools[row] = profile.pools_near(row * 2.0 + 1.0)
		for pool: Dictionary in water_pools[row]:
			var polygon: Array[Vector4] = []
			for edge in 3:
				var a := faces[triangle + edge]
				var b := faces[triangle + (edge + 1) % 3]
				var da := _depth(profile, pool, a)
				var db := _depth(profile, pool, b)
				if da > 0:
					polygon.append(Vector4(a.x, profile.water_level(pool, a.x, a.z) + 0.004, a.z, da))
				if (da > 0) != (db > 0):
					var p := a.lerp(b, da / (da - db))
					polygon.append(Vector4(p.x, profile.water_level(pool, p.x, p.z) + 0.004, p.z, 0.0))
			for fan in range(1, polygon.size() - 1):
				for p: Vector4 in [polygon[0], polygon[fan], polygon[fan + 1]]:
					water.append(Vector3(p.x, p.y, p.z))
					water_depths.append(p.w)
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
	for vertex_index in vertices.size():
		var point := vertices[vertex_index]
		var rest := rest_heights[vertex_index]
		# Two spare material channels retain rest height and signed soil relief.
		# Lighting and exposed earth therefore survive streaming and save/load.
		material_weights[3][vertex_index * 4 + 2] = rest
		material_weights[3][vertex_index * 4 + 3] = 1.0 + rest - point.y
	arrays[Mesh.ARRAY_CUSTOM3] = material_weights[3]
	for band in BAND_COUNT: bands.append(_collision_faces(vertices, width, band))
	var stop := DriveStops.for_chunk(index, profile.seed_at(index * 96.0))
	var access := {}
	if not stop.is_empty():
		access["SideDriveway"] = access_patch(profile, stop, 6.8, absf(float(stop.offset)) - 3.0, 4.5)
		access["TurningYard"] = access_patch(profile, stop, absf(float(stop.offset)) - 8.0, absf(float(stop.offset)) + 3.7, 11.5)
	var render_bands: Array = []
	for band in BAND_COUNT: render_bands.append(band_arrays(arrays, width, band))
	var soil_bands := {}
	for key: Vector2i in profile.deformation.cells:
		var px := key.x * DriveDeformation.CELL_SIZE
		if px >= index * 96.0 - 1.0 and px <= (index + 1) * 96.0 + 1.0:
			for strip in range(floori((px - 0.001) / BAND_ROWS), floori((px + 0.001) / BAND_ROWS) + 1):
				soil_bands[clampi(strip - index * BAND_COUNT, 0, BAND_COUNT - 1)] = true
	var tunnel_mountain := {}
	var feature := profile.feature_at(index * 96.0 + 48.0)
	if include_obstacles and not feature.is_empty() and feature.kind == "tunnel" and floori(float(feature.x) / 96.0) == index:
		tunnel_mountain = preload("res://scripts/tunnel_geology.gd").bake(profile, float(feature.x))
	return {"index": index, "tunnel_mountain": tunnel_mountain, "obstacle_regions": obstacle_regions, "soil_bands": soil_bands.keys(), "render_bands": render_bands, "arrays": arrays, "rest_heights": rest_heights, "faces": faces, "collision_bands": bands, "width": width, "water": water, "water_depths": water_depths, "deck": deck, "stop_access": access, "worker_ms": (Time.get_ticks_usec() - started) / 1000.0}


static func band_arrays(source: Array, width: int, band: int) -> Array:
	var first := band * BAND_ROWS * width
	var end := (band * BAND_ROWS + BAND_ROWS + 1) * width
	var result: Array = []
	result.resize(Mesh.ARRAY_MAX)
	for attribute in [Mesh.ARRAY_VERTEX, Mesh.ARRAY_NORMAL, Mesh.ARRAY_COLOR, Mesh.ARRAY_TEX_UV, Mesh.ARRAY_TEX_UV2]:
		result[attribute] = source[attribute].slice(first, end)
	for channel in 4: result[Mesh.ARRAY_CUSTOM0 + channel] = source[Mesh.ARRAY_CUSTOM0 + channel].slice(first * 4, end * 4)
	var indices := PackedInt32Array()
	for row in BAND_ROWS:
		for col in width - 1:
			var a := row * width + col
			indices.append_array(PackedInt32Array([a, a + width, a + 1, a + width, a + width + 1, a + 1]))
	result[Mesh.ARRAY_INDEX] = indices
	return result

static func access_patch(profile: DriveTerrain, stop: Dictionary, inner: float, outer: float, half_length: float) -> PackedVector3Array:
	var vertices := PackedVector3Array()
	var nx := int(ceil(half_length))
	var nz := int(ceil((outer - inner) * 0.5))
	var side := signf(float(stop.offset))
	for xi in nx:
		for zi in nz:
			for corner: Vector2i in [Vector2i(0, 0), Vector2i(1, 0), Vector2i(0, 1), Vector2i(1, 0), Vector2i(1, 1), Vector2i(0, 1)]:
				var x := float(stop.x) - half_length + (xi + corner.x) * half_length * 2.0 / nx
				var t := float(zi + corner.y) / nz
				var offset := lerpf(inner, outer, t if side > 0.0 else 1.0 - t) * side
				var z := profile.center_at(x) + offset
				vertices.append(Vector3(x, profile.height_at(x, z) + 0.035, z))
	return vertices


static func deform_band(field: DriveDeformation, vertices: PackedVector3Array, rest: PackedFloat32Array, width: int, band: int) -> Dictionary:
	var first := band * BAND_ROWS * width
	var end := (band * BAND_ROWS + BAND_ROWS + 1) * width
	for index in range(first, end):
		var p := vertices[index]
		p.y = rest[index] - field.depth_at(p.x, p.z)
		vertices[index] = p
	var faces := _collision_faces(vertices, width, band)
	return {"vertices": vertices, "faces": faces, "first": first, "end": end, "band": band}


static func mesh_height(arrays: Array, width: int, x: float, z: float) -> float:
	# Sample the actual warped metre-grid triangles, rather than evaluating
	# the continuous height function above/below the visible sloping triangle.
	var points: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var row := clampi(floori(x - points[0].x), 0, ROWS - 1)
	var u := clampf(x - points[row * width].x, 0, 1)
	var left := 0
	var right := width - 1
	while right - left > 1:
		var middle := (left + right) / 2
		var mz := lerpf(points[row * width + middle].z, points[(row + 1) * width + middle].z, u)
		if mz <= z: left = middle
		else: right = middle
	var a := points[row * width + left]
	var b := points[(row + 1) * width + left]
	var c := points[row * width + right]
	var d := points[(row + 1) * width + right]
	var z0 := lerpf(a.z, b.z, u)
	var z1 := lerpf(c.z, d.z, u)
	var v := clampf((z - z0) / maxf(z1 - z0, 0.00001), 0, 1)
	return a.y * (1 - u - v) + b.y * u + c.y * v if u + v <= 1 else d.y * (u + v - 1) + c.y * (1 - u) + b.y * (1 - v)


static func _collision_faces(vertices: PackedVector3Array, width: int, band: int) -> PackedVector3Array:
	# Preserve the proven two-metre physical mesh. Sub-metre tyre cylinders can
	# catch internal edges in a denser GodotPhysics concave mesh. The one-metre
	# visual grid shares the same height field and exact coarse support vertices.
	var columns: Array[int] = [0, 1, 2]
	for column in range(3, width - 3, 2): columns.append(column)
	columns.append_array([width - 3, width - 2, width - 1])
	var faces := PackedVector3Array()
	for row in range(band * BAND_ROWS, (band + 1) * BAND_ROWS, 2):
		for column in columns.size() - 1:
			var a := row * width + columns[column]
			var b := row * width + columns[column + 1]
			for index: int in [a, a + 2 * width, b, a + 2 * width, b + 2 * width, b]: faces.append(vertices[index])
	return faces


static func _depth(profile: DriveTerrain, pool: Dictionary, point: Vector3) -> float:
	var radius := Vector2((point.x - float(pool.x)) / float(pool.rx), (point.z - float(pool.z)) / float(pool.rz)).length()
	var physical := profile.water_level(pool, point.x, point.z) - point.y
	# A dry bank must clip at the real triangle/water intersection. Replacing
	# its signed depth with a distant ellipse edge made the shore hover.
	if physical <= 0.0: return physical
	return minf(physical, (1.0 - radius) * minf(float(pool.rx), float(pool.rz)))
