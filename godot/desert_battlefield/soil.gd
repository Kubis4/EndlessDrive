extends RefCounted
## Signed, bounded sand displacement shared by GPU geometry and wheel support.
## Positive values compress the surface; negative values build raised shoulders.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CELL := 0.25
const EXTENT := 128.0
const RESOLUTION := 1025
const TILE_SIZE := 16.0
const GEOMETRY_CELL := 0.125
const TILE_QUADS := 128
const TILE_VERTICES := 131
const MAX_DEPTH := 0.80
const MAX_BANK := 0.14
const ROLLING_DEPTH := 0.72
const COMPACTION_PER_METRE := 0.32
var field := PackedFloat32Array()
var image: Image
var texture: ImageTexture
var mask_image: Image
var mask_texture: ImageTexture
var tiles: Dictionary = {}
var pending: Dictionary = {}
var dirty := false
var mask_dirty := false
var upload_clock := 0.0
var revision := 0
var uploaded_revision := 0
var changed_pixels := 0
var container: Node3D
var fine_material: ShaderMaterial
var build_state: Dictionary = {}
var shared_indices := PackedInt32Array()

func configure(root: Node3D) -> void:
	field.resize(RESOLUTION * RESOLUTION)
	field.fill(0.0)
	# Half-float precision is better than 0.25 mm here, at half the upload size.
	image = Image.create(RESOLUTION, RESOLUTION, false, Image.FORMAT_RH)
	image.fill(Color(0, 0, 0, 1))
	texture = ImageTexture.create_from_image(image)
	mask_image = Image.create(16, 16, false, Image.FORMAT_R8)
	mask_image.fill(Color.BLACK)
	mask_texture = ImageTexture.create_from_image(mask_image)
	container = Node3D.new()
	container.name = "DeformedSandTiles"
	root.add_child(container)
	var base := root.get_node("SculptedDunes") as MeshInstance3D
	var material := base.material_override.duplicate() as ShaderMaterial
	base.material_override = material
	bind_material(material)
	material.set_shader_parameter("fine_tile_mask", mask_texture)
	fine_material = material.duplicate() as ShaderMaterial
	fine_material.set_shader_parameter("fine_geometry", true)
	for car in root.get_node("Patrol").get_children(): bind_material(car.track_material)
	shared_indices.resize(130 * 130 * 6)
	for z in 130:
		for x in 130:
			var a := z * TILE_VERTICES + x
			var offset := (z * 130 + x) * 6
			shared_indices[offset] = a
			shared_indices[offset + 1] = a + 1
			shared_indices[offset + 2] = a + TILE_VERTICES
			shared_indices[offset + 3] = a + 1
			shared_indices[offset + 4] = a + TILE_VERTICES + 1
			shared_indices[offset + 5] = a + TILE_VERTICES

func bind_material(material: ShaderMaterial) -> void:
	material.set_shader_parameter("soil_map", texture)
	material.set_shader_parameter("soil_enabled", true)

func depth_at(x: float, z: float) -> float:
	if field.is_empty() or absf(x) >= EXTENT or absf(z) >= EXTENT: return 0.0
	var gx := (x + EXTENT) / CELL
	var gz := (z + EXTENT) / CELL
	var ix := floori(gx)
	var iz := floori(gz)
	var wx := spline_weights(gx - ix)
	var wz := spline_weights(gz - iz)
	var value := 0.0
	var x0 := clampi(ix - 1, 0, RESOLUTION - 1)
	var x1 := clampi(ix, 0, RESOLUTION - 1)
	var x2 := clampi(ix + 1, 0, RESOLUTION - 1)
	var x3 := clampi(ix + 2, 0, RESOLUTION - 1)
	for row in 4:
		var offset := clampi(iz + row - 1, 0, RESOLUTION - 1) * RESOLUTION
		value += wz[row] * (field[offset + x0] * wx.x + field[offset + x1] * wx.y + field[offset + x2] * wx.z + field[offset + x3] * wx.w)
	return value

static func spline_weights(f: float) -> Vector4:
	var f2 := f * f
	var f3 := f2 * f
	var a := 1.0 - f
	return Vector4(a * a * a, 3.0 * f3 - 6.0 * f2 + 4.0, -3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0, f3) / 6.0

func height_at(x: float, z: float) -> float:
	return Surface.height_at(x, z) - depth_at(x, z)

func normal_at(x: float, z: float) -> Vector3:
	return Vector3(height_at(x - CELL, z) - height_at(x + CELL, z), CELL * 2,
		height_at(x, z - CELL) - height_at(x, z + CELL)).normalized()

func stamp_segment(a: Vector3, b: Vector3, forward: Vector3, slip: float) -> void:
	var distance := Vector2(b.x - a.x, b.z - a.z).length()
	# Resets/teleports must never excavate a line across the map.
	if distance < 0.025 or distance > 4.0: return
	var steps := maxi(1, ceili(distance / 0.22))
	for i in steps:
		stamp(a.lerp(b, float(i + 1) / steps), forward, distance / steps, slip)

func stamp(point: Vector3, forward: Vector3, travel: float, slip: float) -> void:
	if field.is_empty() or absf(point.x) > EXTENT - 1 or absf(point.z) > EXTENT - 1: return
	if travel <= 0.0: return
	slip = clampf(slip, 0.0, 1.0)
	# The compacted concrete-station foundation does not excavate like loose dunes.
	var softness := smoothstep(10.0, 15.0, Vector2(point.x + 19, point.z + 13).length())
	if softness < 0.01: return
	var direction := Vector2(forward.x, forward.z).normalized()
	var side := Vector2(-direction.y, direction.x)
	var center := Vector2(point.x, point.z)
	# Accumulate wheel travel against a deeper bearing layer. A first passage is
	# shallow; subsequent front/rear axle passages keep compacting the same rut.
	# The exponential response depends on distance, not frame rate/stamp count,
	# and slows naturally near the bearing limit instead of sinking forever.
	var wear_step := COMPACTION_PER_METRE * travel * (1.0 + slip * 1.3)
	var limit := lerpf(ROLLING_DEPTH, MAX_DEPTH, slip) * softness
	var radius := 0.95 + slip * 0.10
	var stamp_changed := false
	for iz in range(floori((point.z - radius + EXTENT) / CELL), ceili((point.z + radius + EXTENT) / CELL) + 1):
		for ix in range(floori((point.x - radius + EXTENT) / CELL), ceili((point.x + radius + EXTENT) / CELL) + 1):
			var delta := Vector2(ix * CELL - EXTENT, iz * CELL - EXTENT) - center
			var along := 1.0 - smoothstep(0.10, 0.46, absf(delta.dot(direction)))
			if along < 0.001: continue
			var lateral := absf(delta.dot(side))
			var tread_distance := lateral / (0.30 + slip * 0.065)
			var shoulder_distance := (lateral - 0.61 - slip * 0.09) / 0.22
			var tread := exp(-tread_distance * tread_distance)
			var shoulder := exp(-shoulder_distance * shoulder_distance)
			var target := limit * tread - minf(MAX_BANK, limit * 0.20) * shoulder
			var index := iz * RESOLUTION + ix
			var previous := field[index]
			# A wheel flattens an old bank before digging; shoulders never fill ruts.
			var difference := maxf(0.0, target - previous) if target > 0 else (minf(0.0, target - previous) if previous <= 0 else 0.0)
			# Footprint weighting belongs inside the exponent, so splitting equal
			# travel into more updates does not change the accumulated compaction.
			var response := 1.0 - exp(-wear_step * along)
			var value := clampf(previous + difference * response, -MAX_BANK, MAX_DEPTH)
			if absf(value - previous) < 0.00002: continue
			field[index] = value
			image.set_pixel(ix, iz, Color(value, 0, 0, 1))
			dirty = true
			stamp_changed = true
			changed_pixels += 1
	if stamp_changed:
		revision += 1
		# Queue touched tiles once per footprint, not once per changed texel.
		var margin := radius + CELL * 2.0
		for tz in range(maxi(0, floori((point.z - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.z + margin + EXTENT) / TILE_SIZE)) + 1):
			for tx in range(maxi(0, floori((point.x - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.x + margin + EXTENT) / TILE_SIZE)) + 1):
				var tile := Vector2i(tx, tz)
				if not tiles.has(tile): pending[tile] = true

func update(delta: float, force: bool = false) -> void:
	if container == null: return
	# Build a bounded strip of vertices each frame instead of a full patch spike.
	if not pending.is_empty():
		var tile: Vector2i = pending.keys()[0]
		if build_state.is_empty(): _begin_tile(tile)
		if _advance_tile(TILE_VERTICES if force else 8):
			pending.erase(tile)
	upload_clock += delta
	if (upload_clock >= 0.075 or force) and dirty:
		texture.update(image)
		dirty = false
		upload_clock = 0.0
		uploaded_revision = revision
	if mask_dirty:
		mask_texture.update(mask_image)
		mask_dirty = false

func build_tile(tile: Vector2i) -> void:
	_begin_tile(tile)
	_advance_tile(TILE_VERTICES)

func _begin_tile(tile: Vector2i) -> void:
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	# A one-cell halo makes both sides of a tile seam use fragment clipping,
	# avoiding MSAA coverage cracks between clipped coarse and fine triangles.
	vertices.resize(TILE_VERTICES * TILE_VERTICES)
	normals.resize(TILE_VERTICES * TILE_VERTICES)
	build_state = {"tile": tile, "row": 0, "vertices": vertices, "normals": normals}

func _advance_tile(rows: int) -> bool:
	var tile: Vector2i = build_state.tile
	var vertices: PackedVector3Array = build_state.vertices
	var normals: PackedVector3Array = build_state.normals
	var origin := Vector2(tile) * TILE_SIZE - Vector2.ONE * EXTENT
	var last_row := mini(TILE_VERTICES, int(build_state.row) + rows)
	for z in range(int(build_state.row), last_row):
		for x in TILE_VERTICES:
			var px := origin.x + (x - 1) * GEOMETRY_CELL
			var pz := origin.y + (z - 1) * GEOMETRY_CELL
			var i := z * TILE_VERTICES + x
			var border := maxf(0, minf(minf(x - 1, TILE_QUADS + 1 - x), minf(z - 1, TILE_QUADS + 1 - z))) * GEOMETRY_CELL
			# Match the exact coarse triangles at the replacement boundary. Without
			# this, tiny analytic/interpolated height differences reveal hairline gaps.
			var height := Surface.height_at(px, pz)
			if border < 0.75: height = lerpf(coarse_height(px, pz), height, smoothstep(0.0, 0.75, border))
			vertices[i] = Vector3(px, height, pz)
			normals[i] = Surface.normal_at(px, pz)
	build_state.vertices = vertices
	build_state.normals = normals
	build_state.row = last_row
	if last_row < TILE_VERTICES: return false
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_INDEX] = shared_indices
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	var patch := MeshInstance3D.new()
	patch.name = "Sand_%d_%d" % [tile.x, tile.y]
	patch.mesh = mesh
	patch.material_override = fine_material
	patch.set_instance_shader_parameter("patch_bounds", Vector4(origin.x, origin.y, origin.x + TILE_SIZE, origin.y + TILE_SIZE))
	patch.extra_cull_margin = MAX_DEPTH + MAX_BANK
	container.add_child(patch)
	tiles[tile] = patch
	mask_image.set_pixel(tile.x, tile.y, Color.WHITE)
	mask_dirty = true
	build_state.clear()
	return true

func coarse_height(x: float, z: float) -> float:
	const STEP := 0.75
	var a := Vector2(floorf(x / STEP), floorf(z / STEP)) * STEP
	var f := (Vector2(x, z) - a) / STEP
	var h00 := Surface.height_at(a.x, a.y)
	var h10 := Surface.height_at(a.x + STEP, a.y)
	var h01 := Surface.height_at(a.x, a.y + STEP)
	if f.x + f.y <= 1.0: return h00 + (h10 - h00) * f.x + (h01 - h00) * f.y
	var h11 := Surface.height_at(a.x + STEP, a.y + STEP)
	return h11 + (h01 - h11) * (1.0 - f.x) + (h10 - h11) * (1.0 - f.y)
