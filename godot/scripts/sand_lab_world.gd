extends DriveWorld
## Finite pure-sand test ground. Only touched 8 m tiles are rebuilt.
const TILE := 8.0
const STEP := 0.25
const ROW := 33
const Soil = preload("res://scripts/sand_lab_soil.gd")
var tiles: Dictionary = {}
var dirty: Dictionary = {}
var previous_contacts: Array[Vector3] = []
var max_tile_ms := 0.0
var material: ShaderMaterial
var tile_indices := PackedInt32Array()
var fine_indices := PackedInt32Array()

class SandTerrain extends DriveTerrain:
	func center_at(_x: float) -> float: return 0.0
	func corridor_center(_x: float) -> float: return 0.0
	func boundary_width(_x: float) -> float: return 1000.0
	func dry_height(x: float, z: float) -> float: return sin(x * 0.075 + z * 0.035) * 1.15 + sin(z * 0.13 - x * 0.045) * 0.42
	func feature_at(_x: float) -> Dictionary: return {}
	func pools_near(_x: float) -> Array[Dictionary]: return []
	func sample(x: float, z: float, _include_water := true, _visual_blend := false) -> Dictionary:
		return {"color": Color("d7bd88"), "road": 0.0, "lateral": z, "paved": 0.0,
			"grip": 0.88, "drag": 0.50, "water": 0.0, "snow": 0.0, "label": "Piesok",
			"roughness": 0.0, "material": SurfaceKind.SAND, "deformable": true,
			"soil_softness": 0.32, "soil_bearing": 1.6, "soil_depth": 0.20,
			"soil_moisture": 0.0, "rut_depth": maxf(0, deformation.depth_at(x, z))}

func _ready() -> void:
	terrain = SandTerrain.new()
	terrain.deformation = Soil.new()
	material = ShaderMaterial.new()
	material.shader = load("res://shaders/sand_lab.gdshader")
	for x in ROW - 1:
		for z in ROW - 1:
			var a := x * ROW + z
			tile_indices.append_array(PackedInt32Array([a, a + ROW, a + 1, a + ROW, a + ROW + 1, a + 1]))
	for x in 64:
		for z in 64:
			var a := x * 65 + z
			fine_indices.append_array(PackedInt32Array([a, a + 65, a + 1, a + 65, a + 66, a + 1]))
	for x in range(-7, 7):
		for z in range(-5, 5): _build_tile(Vector2i(x, z))

func _process(_dt: float) -> void:
	var started := Time.get_ticks_usec()
	for key: Vector2i in dirty.keys():
		dirty.erase(key)
		if tiles.has(key): _update_tile(key)
		# Spread contact tiles across frames instead of committing four at once.
		if Time.get_ticks_usec() - started >= 3500: break

func _build_tile(key: Vector2i) -> void:
	var tile := Node3D.new()
	tile.name = "Tile_%d_%d" % [key.x, key.y]
	add_child(tile)
	var mesh := MeshInstance3D.new()
	mesh.name = "SandAndTracks"
	mesh.material_override = material
	# Shallow relief is shaded by its smooth normals. Casting its own shadow
	# produced jagged self-shadow acne; the sand still receives the car's shadow.
	mesh.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	tile.add_child(mesh)
	var body := StaticBody3D.new()
	body.name = "SandCollision"
	mesh.add_child(body)
	var shape := CollisionShape3D.new()
	var heightmap := HeightMapShape3D.new()
	heightmap.map_width = ROW
	heightmap.map_depth = ROW
	shape.shape = heightmap
	shape.position = Vector3(key.x * TILE + TILE * 0.5, 0, key.y * TILE + TILE * 0.5)
	shape.scale = Vector3(STEP, 1, STEP)
	body.add_child(shape)
	var rest := PackedFloat32Array()
	for x in range(-2, ROW + 2):
		for z in range(-2, ROW + 2): rest.append(terrain.dry_height(key.x * TILE + x * STEP, key.y * TILE + z * STEP))
	tiles[key] = {"mesh": mesh, "shape": shape, "rest": rest}
	_update_tile(key)

func _update_tile(key: Vector2i) -> void:
	var started := Time.get_ticks_usec()
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var heights := PackedFloat32Array()
	var rest: PackedFloat32Array = tiles[key].rest
	var padding := ROW + 4
	var deformed := false
	# These vertices are exactly on soil cells. One lookup per padded vertex
	# replaces five bilinear samples and repeated evaluation of the base dunes.
	for x in range(-2, ROW + 2):
		for z in range(-2, ROW + 2):
			var cell := key * (ROW - 1) + Vector2i(x, z)
			var depth := float(terrain.deformation.cells.get(cell, 0))
			deformed = deformed or absf(depth) > 0.00001
			heights.append(rest[(x + 2) * padding + z + 2] - depth)
	# Refine only touched render tiles. Physics retains its fast 25 cm grid;
	# interpolating the visual surface does not multiply soil or collision work.
	var visual := _refine_heights(heights) if deformed else heights
	var row := 65 if deformed else ROW
	var stride := 67 if deformed else padding
	var border := 1 if deformed else 2
	var step := STEP * 0.5 if deformed else STEP
	vertices.resize(row * row)
	normals.resize(row * row)
	for x in row:
		for z in row:
			var px := key.x * TILE + x * step
			var pz := key.y * TILE + z * step
			var index := (x + border) * stride + z + border
			vertices[x * row + z] = Vector3(px, visual[index], pz)
			var dx := visual[index + stride] - visual[index - stride]
			var dz := visual[index + 1] - visual[index - 1]
			normals[x * row + z] = Vector3(-dx / (step * 2), 1, -dz / (step * 2)).normalized()
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_INDEX] = fine_indices if deformed else tile_indices
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	tiles[key].mesh.mesh = mesh
	tiles[key]["visual_row"] = row
	# Heightmap remains anchored to exactly the same soil samples. Visual
	# midpoints round the lips; they are not additional physical deformation.
	var collision_heights := PackedFloat32Array()
	collision_heights.resize(ROW * ROW)
	for x in ROW:
		for z in ROW: collision_heights[z * ROW + x] = heights[(x + 2) * padding + z + 2]
	(tiles[key].shape.shape as HeightMapShape3D).map_data = collision_heights
	max_tile_ms = maxf(max_tile_ms, (Time.get_ticks_usec() - started) / 1000.0)

func _refine_heights(source: PackedFloat32Array) -> PackedFloat32Array:
	# Separable Catmull-Rom at half steps: integer samples stay exact. The
	# two-cell halo keeps curves and normals continuous at touched tile seams.
	var horizontal := PackedFloat32Array()
	horizontal.resize(37 * 67)
	for x in 37:
		for z in 33: horizontal[x * 67 + z * 2 + 1] = source[x * 37 + z + 2]
		for z in 34:
			var index := x * 37 + z + 1
			var midpoint := (source[index] + source[index + 1]) * 0.5
			var curved := (-source[index - 1] + 9 * source[index] + 9 * source[index + 1] - source[index + 2]) / 16.0
			horizontal[x * 67 + z * 2] = clampf(curved, midpoint - 0.018, midpoint + 0.018)
	var result := PackedFloat32Array()
	result.resize(67 * 67)
	for x in 33:
		for z in 67: result[(x * 2 + 1) * 67 + z] = horizontal[(x + 2) * 67 + z]
	for x in 34:
		for z in 67:
			var index := (x + 1) * 67 + z
			var curved := (-horizontal[index - 67] + 9 * horizontal[index] + 9 * horizontal[index + 67] - horizontal[index + 134]) / 16.0
			var cell_z := (z >> 1) + (2 if z % 2 == 1 else 1)
			var lower := (x + 1) * 37 + cell_z
			# Bound smoothing against the actual collision triangle, not a
			# bilinear quad: a tyre must never visibly float above its rut.
			var physical := (source[lower] + source[lower + 37]) * 0.5 if z % 2 == 1 else (source[lower + 1] + source[lower + 37]) * 0.5
			result[x * 2 * 67 + z] = clampf(curved, physical - 0.018, physical + 0.018)
	return result

func _mark(point: Vector3) -> void:
	for x in range(floori((point.x - 1) / TILE), floori((point.x + 1) / TILE) + 1):
		for z in range(floori((point.z - 1) / TILE), floori((point.z + 1) / TILE) + 1): dirty[Vector2i(x, z)] = true

func compact_tyres(points: Array[Vector3], surface: Dictionary, pressure: float, direction := Vector2.RIGHT, shear := 0.0, wheel_shear: Array[float] = []) -> void:
	if previous_contacts.size() != points.size(): previous_contacts = points.duplicate()
	for wheel in points.size():
		var end := points[wheel]
		var start := previous_contacts[wheel]
		if start.distance_to(end) > 3.0: start = end
		var steps := maxi(1, ceili(Vector2(end.x - start.x, end.z - start.z).length() / 0.15))
		for step in steps:
			var p := start.lerp(end, float(step + 1) / steps)
			var contact_shear := wheel_shear[wheel] if wheel_shear.size() == points.size() else shear
			if terrain.deformation.compact(p, surface, pressure, direction, contact_shear): _mark(p)
	previous_contacts = points.duplicate()

func load_tyres(points: Array[Vector3], pressure: float, slip: float, effort: float, dt: float) -> void:
	for p in points:
		if terrain.deformation.load_soil(p, terrain.sample(p.x, p.z), pressure, slip, effort, dt): _mark(p)

func reset_sand() -> void:
	terrain.deformation.restore([])
	previous_contacts.clear()
	for key: Vector2i in tiles: dirty[key] = true

func flush_deformation() -> void:
	for key: Vector2i in dirty.keys():
		if tiles.has(key): _update_tile(key)
	dirty.clear()


