extends RefCounted
## Signed, bounded sand displacement shared by GPU geometry and wheel support.
## Positive values compress the surface; negative values build raised shoulders.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CELL := 0.25
const EXTENT := 128.0
const RESOLUTION := 1025
const TILE_SIZE := 16.0
var geometry_cell := 0.125
var tile_quads := 128
var tile_vertices := 131
const MAX_DEPTH := 0.5
const MAX_BANK := 0.2
const ROLLING_DEPTH := 0.4
const COMPACTION_PER_METRE := 0.55
## Skid marks on hard ground: slip threshold and the tiny encoded depth.
const MARK_SLIP := 0.3
const MARK_DEPTH := 0.004
## Coarse 4 m occupancy blocks: wheel support skips the 16-texel cubic lookup
## wherever no stamp could have changed the field (almost all of the map).
const BLOCK := 4.0
const BLOCKS := 64
var touched := PackedByteArray()
var live_materials: Array[ShaderMaterial] = []
var cars: Array = []
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
## Ruts reach the GPU at 25 Hz (mobile 13 Hz): close enough that the rendered
## surface keeps up with the wheels without uploading the field every frame.
var upload_interval := 0.04
## Background fine-patch construction (see _build_pending).
var threaded := true
var build_task := -1
var build_tile_id := Vector2i.ZERO
var build_result := {}
var revision := 0
var uploaded_revision := 0
var changed_pixels := 0
var container: Node3D
var fine_material: ShaderMaterial
var shared_indices := PackedInt32Array()

func configure(root: Node3D) -> void:
	# At the overview camera 25 cm triangles are only a few pixels wide. The
	# 25 cm physical field and cubic smoothing are identical in both profiles.
	geometry_cell = 0.25 if root.quality_name != "reference" else 0.125
	if OS.has_feature("android"):
		# Live tyre segments cover the gap between uploads, so mobile can send
		# the 2 MB field less often without the ruts lagging behind the car.
		upload_interval = 0.15
	tile_quads = roundi(TILE_SIZE / geometry_cell)
	tile_vertices = tile_quads + 3
	field.resize(RESOLUTION * RESOLUTION)
	field.fill(0.0)
	touched.resize(BLOCKS * BLOCKS)
	touched.fill(0)
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
	if root.quality_name != "reference":
		preload("res://desert_battlefield/terrain_chunks.gd").partition(base, 48 if OS.has_feature("android") else 32)
	fine_material = material.duplicate() as ShaderMaterial
	fine_material.set_shader_parameter("fine_geometry", true)
	live_materials.append(fine_material)
	cars = root.get_node("Patrol").get_children().filter(func(node): return node is CharacterBody3D)
	for car in cars: bind_material(car.track_material, false)
	var side := tile_vertices - 1
	shared_indices.resize(side * side * 6)
	for z in side:
		for x in side:
			var a := z * tile_vertices + x
			var offset := (z * side + x) * 6
			shared_indices[offset] = a
			shared_indices[offset + 1] = a + 1
			shared_indices[offset + 2] = a + tile_vertices
			shared_indices[offset + 3] = a + 1
			shared_indices[offset + 4] = a + tile_vertices + 1
			shared_indices[offset + 5] = a + tile_vertices

func bind_material(material: ShaderMaterial, live: bool = true) -> void:
	material.set_shader_parameter("soil_map", texture)
	material.set_shader_parameter("soil_enabled", true)
	if live and material not in live_materials: live_materials.append(material)

## Each grounded tyre on deformable ground contributes a segment from where it
## was at the last field upload to where it is now, carrying the rut depth the
## CPU field already has there. Ground materials draw these until the next upload.
func _update_live_segments() -> void:
	if live_materials.is_empty(): return
	var segments: Array[Vector4] = []
	var depths := PackedFloat32Array()
	segments.resize(12)
	depths.resize(12)
	var slot := 0
	for car in cars:
		if slot >= 12: break
		if car.wheel_contacts.size() != 4: continue
		for i in 4:
			var now: Vector3 = car.wheel_contacts[i]
			var start: Vector3 = car.upload_contacts[i] if car.upload_contacts.size() == 4 else now
			if Vector2(start.x - now.x, start.z - now.z).length_squared() > 25.0: start = now
			var depth := depth_at(now.x, now.z) if car.wheel_grounded[i] else 0.0
			segments[slot] = Vector4(start.x, start.z, now.x, now.z)
			depths[slot] = maxf(depth, 0.0)
			slot += 1
	var packed: Array[Vector4] = [Vector4(depths[0], depths[1], depths[2], depths[3]),
		Vector4(depths[4], depths[5], depths[6], depths[7]), Vector4(depths[8], depths[9], depths[10], depths[11])]
	# Only slots that actually carry a rut are looped over per pixel.
	var count := 0
	for i in 12:
		if depths[i] > 0.0: count = i + 1
	for material in live_materials:
		material.set_shader_parameter("live_segments", segments)
		material.set_shader_parameter("live_depths", packed)
		material.set_shader_parameter("live_count", count)

func depth_at(x: float, z: float) -> float:
	if field.is_empty() or absf(x) >= EXTENT or absf(z) >= EXTENT: return 0.0
	if touched[mini(int((z + EXTENT) / BLOCK), BLOCKS - 1) * BLOCKS + mini(int((x + EXTENT) / BLOCK), BLOCKS - 1)] == 0: return 0.0
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
	# Test grounds vary per texel: asphalt never ruts, mud compacts readily.
	var mixed_ground := Surface.in_test_ground(point.x, point.z, 1.5)
	var uniform_deform := 1.0
	if mixed_ground:
		# Sample the footprint corners; only a footprint straddling two grounds
		# needs the per-texel lookup below.
		uniform_deform = Surface.deform_at(point.x, point.z)
		mixed_ground = false
		for corner: Vector2 in [Vector2(-1.1, -1.1), Vector2(1.1, -1.1), Vector2(-1.1, 1.1), Vector2(1.1, 1.1)]:
			if Surface.deform_at(point.x + corner.x, point.z + corner.y) != uniform_deform: mixed_ground = true
		if not mixed_ground and uniform_deform <= 0.0 and slip < MARK_SLIP: return
	# Hard ground (asphalt, rock) cannot rut, but a skidding tyre leaves rubber:
	# a 4 mm "depth" that the ground shaders draw as a dark mark.
	var marking := slip >= MARK_SLIP
	var mark_strength := clampf((slip - MARK_SLIP) / 0.45, 0.25, 1.0)
	var direction := Vector2(forward.x, forward.z).normalized()
	var side := Vector2(-direction.y, direction.x)
	var center := Vector2(point.x, point.z)
	# Accumulate wheel travel against a deeper bearing layer. A first passage is
	# shallow; subsequent front/rear axle passages keep compacting the same rut.
	# The exponential response depends on distance, not frame rate/stamp count,
	# and slows naturally near the bearing limit instead of sinking forever.
	var wear_step := COMPACTION_PER_METRE * travel * (1.0 + slip * 1.3)
	var base_limit := lerpf(ROLLING_DEPTH, MAX_DEPTH, slip) * softness
	var limit := minf(base_limit * uniform_deform, MAX_DEPTH)
	var radius := 0.95 + slip * 0.10
	var stamp_changed := false
	for iz in range(floori((point.z - radius + EXTENT) / CELL), ceili((point.z + radius + EXTENT) / CELL) + 1):
		for ix in range(floori((point.x - radius + EXTENT) / CELL), ceili((point.x + radius + EXTENT) / CELL) + 1):
			var delta := Vector2(ix * CELL - EXTENT, iz * CELL - EXTENT) - center
			var along := 1.0 - smoothstep(0.10, 0.46, absf(delta.dot(direction)))
			if along < 0.001: continue
			var deform := uniform_deform
			if mixed_ground:
				deform = Surface.deform_at(ix * CELL - EXTENT, iz * CELL - EXTENT)
				limit = minf(base_limit * deform, MAX_DEPTH)
			var lateral := absf(delta.dot(side))
			if deform <= 0.0:
				if not marking: continue
				var rubber := MARK_DEPTH * mark_strength * along * exp(-lateral * lateral / 0.045)
				var old := field[iz * RESOLUTION + ix]
				if rubber <= old + 0.00005: continue
				field[iz * RESOLUTION + ix] = rubber
				image.set_pixel(ix, iz, Color(rubber, 0, 0, 1))
				dirty = true
				stamp_changed = true
				changed_pixels += 1
				continue
			var tread_distance := lateral / (0.30 + slip * 0.065)
			var shoulder_distance := (lateral - 0.61 - slip * 0.09) / 0.22
			var tread := exp(-tread_distance * tread_distance)
			var shoulder := exp(-shoulder_distance * shoulder_distance)
			var target := limit * tread - minf(MAX_BANK, limit * 0.3) * shoulder
			var index := iz * RESOLUTION + ix
			var previous := field[index]
			# A wheel flattens an old bank before digging; shoulders never fill ruts.
			var difference := maxf(0.0, target - previous) if target > 0 else (minf(0.0, target - previous) if previous <= 0 else 0.0)
			# Footprint weighting belongs inside the exponent, so splitting equal
			# travel into more updates does not change the accumulated compaction.
			var response := 1.0 - exp(-wear_step * along * maxf(deform, 0.35))
			var value := clampf(previous + difference * response, -MAX_BANK, MAX_DEPTH)
			if absf(value - previous) < 0.00002: continue
			field[index] = value
			image.set_pixel(ix, iz, Color(value, 0, 0, 1))
			dirty = true
			stamp_changed = true
			changed_pixels += 1
	if stamp_changed:
		revision += 1
		var reach := radius + CELL * 3.0
		for bz in range(maxi(0, floori((point.z - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.z + reach + EXTENT) / BLOCK)) + 1):
			for bx in range(maxi(0, floori((point.x - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.x + reach + EXTENT) / BLOCK)) + 1):
				touched[bz * BLOCKS + bx] = 1
		# Queue touched tiles once per footprint, not once per changed texel.
		var margin := radius + CELL * 2.0
		for tz in range(maxi(0, floori((point.z - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.z + margin + EXTENT) / TILE_SIZE)) + 1):
			for tx in range(maxi(0, floori((point.x - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.x + margin + EXTENT) / TILE_SIZE)) + 1):
				var tile := Vector2i(tx, tz)
				# Test grounds render their own deformable surface; a fine sand
				# patch hidden entirely beneath one would only cost geometry.
				if not tiles.has(tile) and not _tile_hidden(tile): pending[tile] = true

## Queue the fine patch under a point the car is about to reach, so its ruts
## render on refined geometry from the first contact.
func prefetch(point: Vector3) -> void:
	if container == null or absf(point.x) >= EXTENT or absf(point.z) >= EXTENT: return
	var tile := Vector2i(clampi(floori((point.x + EXTENT) / TILE_SIZE), 0, 15), clampi(floori((point.z + EXTENT) / TILE_SIZE), 0, 15))
	if not tiles.has(tile) and not pending.has(tile) and not _tile_hidden(tile) \
		and Surface.deform_at(point.x, point.z) > 0.0: pending[tile] = true

static func _tile_hidden(tile: Vector2i) -> bool:
	var x0 := tile.x * TILE_SIZE - EXTENT - CELL
	var z0 := tile.y * TILE_SIZE - EXTENT - CELL
	var x1 := x0 + TILE_SIZE + CELL * 2.0
	var z1 := z0 + TILE_SIZE + CELL * 2.0
	for hole: Vector4 in [Surface.COURSE, Surface.PAD, Surface.ROCKS]:
		if x0 >= hole.x and z0 >= hole.y and x1 <= hole.z and z1 <= hole.w: return true
	return false

func update(delta: float, force: bool = false) -> void:
	if container == null: return
	_build_pending(force)
	upload_clock += delta
	if (upload_clock >= upload_interval or force) and dirty:
		texture.update(image)
		dirty = false
		upload_clock = 0.0
		uploaded_revision = revision
		for car in cars: car.upload_contacts.assign(car.wheel_contacts)
	if mask_dirty:
		mask_texture.update(mask_image)
		mask_dirty = false
	# Nothing waiting for upload: the GPU field is current, so segments restart
	# at the wheels (never a straight chord across an earlier turn).
	if not dirty:
		for car in cars: car.upload_contacts.assign(car.wheel_contacts)
	_update_live_segments()

## Fine patches are computed on a worker thread (pure surface maths) and only
## turned into a mesh on the main thread, so driving into new sand costs no
## frame time. `force` (tests, captures) builds synchronously.
func _build_pending(force: bool) -> void:
	if build_task != -1:
		if not force and not WorkerThreadPool.is_task_completed(build_task): return
		WorkerThreadPool.wait_for_task_completion(build_task)
		build_task = -1
		_finish_tile(build_tile_id, build_result)
		pending.erase(build_tile_id)
	if pending.is_empty(): return
	var tile := Vector2i.ZERO
	for key: Vector2i in pending:
		tile = key
		break
	if force or not threaded:
		_finish_tile(tile, _compute_tile(tile))
		pending.erase(tile)
		return
	build_tile_id = tile
	build_task = WorkerThreadPool.add_task(func(): build_result = _compute_tile(tile), false, "Sand patch")

func build_tile(tile: Vector2i) -> void:
	_finish_tile(tile, _compute_tile(tile))

func _compute_tile(tile: Vector2i) -> Dictionary:
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	# A one-cell halo makes both sides of a tile seam use fragment clipping,
	# avoiding MSAA coverage cracks between clipped coarse and fine triangles.
	vertices.resize(tile_vertices * tile_vertices)
	normals.resize(tile_vertices * tile_vertices)
	var origin := Vector2(tile) * TILE_SIZE - Vector2.ONE * EXTENT
	for z in tile_vertices:
		for x in tile_vertices:
			var px := origin.x + (x - 1) * geometry_cell
			var pz := origin.y + (z - 1) * geometry_cell
			var i := z * tile_vertices + x
			var border := maxf(0, minf(minf(x - 1, tile_quads + 1 - x), minf(z - 1, tile_quads + 1 - z))) * geometry_cell
			# Match the exact coarse triangles at the replacement boundary. Without
			# this, tiny analytic/interpolated height differences reveal hairline gaps.
			var height := Surface.height_at(px, pz)
			if border < 0.75: height = lerpf(coarse_height(px, pz), height, smoothstep(0.0, 0.75, border))
			vertices[i] = Vector3(px, height, pz)
			# Near seams use the coarse mesh's analytic normals; interiors are
			# derived from the height grid (no extra surface samples).
			if border < 1.0: normals[i] = Surface.normal_at(px, pz)
	var span := 2.0 * geometry_cell
	for z in range(1, tile_vertices - 1):
		for x in range(1, tile_vertices - 1):
			var border := minf(minf(x - 1, tile_quads + 1 - x), minf(z - 1, tile_quads + 1 - z)) * geometry_cell
			if border < 1.0: continue
			var i := z * tile_vertices + x
			normals[i] = Vector3(vertices[i - 1].y - vertices[i + 1].y, span,
				vertices[i - tile_vertices].y - vertices[i + tile_vertices].y).normalized()
	return {"vertices": vertices, "normals": normals}

func _finish_tile(tile: Vector2i, data: Dictionary) -> void:
	if tiles.has(tile): return
	var origin := Vector2(tile) * TILE_SIZE - Vector2.ONE * EXTENT
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = data.vertices
	arrays[Mesh.ARRAY_NORMAL] = data.normals
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

func coarse_height(x: float, z: float) -> float:
	const STEP := 0.75
	var a := Vector2(floorf(x / STEP), floorf(z / STEP)) * STEP
	var f := (Vector2(x, z) - a) / STEP
	# The authored coarse mesh is the pure dune function (test grounds are separate).
	var h00 := Surface.dune_height(a.x, a.y)
	var h10 := Surface.dune_height(a.x + STEP, a.y)
	var h01 := Surface.dune_height(a.x, a.y + STEP)
	if f.x + f.y <= 1.0: return h00 + (h10 - h00) * f.x + (h01 - h00) * f.y
	var h11 := Surface.dune_height(a.x + STEP, a.y + STEP)
	return h11 + (h01 - h11) * (1.0 - f.x) + (h10 - h11) * (1.0 - f.y)
