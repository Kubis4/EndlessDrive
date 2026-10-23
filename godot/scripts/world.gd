class_name DriveWorld
extends Node3D
## Original desert art direction, deterministic streaming and shared terrain collision.

const CHUNK_LENGTH := 96.0
const HALF_WIDTH := 120.0
const BOUNDARY_OFFSET := 36.0
const SAFE_HALF_WIDTH := BOUNDARY_OFFSET - 6.0
const BIOMES := DriveTerrain.BIOMES
var world_seed := 24601
var terrain := DriveTerrain.new()
var chunks: Dictionary = {}
var buildings: Dictionary = {}
var _models: Dictionary = {}
var _atlas: Texture2D
var async_streaming := false
var max_commit_ms := 0.0
var last_worker_ms := 0.0
var weather_grip := 1.0
var wind_force := 0.0
var hazards_enabled := false
var clock := 0.0
var _worker: Thread
var _worker_index := 0
var _worker_epoch := 0
var _epoch := 0
var _pending: Array[int] = []
var _jobs: Array[Callable] = []
var _current := 0
var _stop_scenes: Dictionary = {}
var _rut_dirty: Dictionary = {}
var _rut_timer := 0.0
var deformation_enabled := true
var max_deformation_ms := 0.0
var _rut_worker: Thread
var _rut_key := Vector2i.ZERO
var _rut_chunk_id := 0


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	_atlas = load("res://assets/textures/desert-materials.png")
	for key: String in ["adobe_workshop", "saguaro", "desert_scrub", "mesa_rock", "utility_pole", "ranch_fence", "water_tower"]:
		_models[key] = load("res://assets/models/" + key + ".tscn")
	for kind: String in DriveStops.KINDS:
		_stop_scenes[kind] = load("res://assets/models/stops/" + kind + ".tscn")
	_stop_scenes["start"] = load("res://scenes/world/start_camp.tscn")


func configure(value: int) -> void:
	_stop_worker()
	_epoch += 1
	_pending.clear()
	_jobs.clear()
	_rut_dirty.clear()
	terrain.deformation.restore([])
	world_seed = value
	terrain.seed_value = value
	terrain.route_choices.clear()
	terrain.clear_caches()
	for chunk: Node3D in chunks.values():
		remove_child(chunk)
		chunk.queue_free()
	chunks.clear()
	buildings.clear()


func center_at(x: float) -> float:
	return terrain.center_at(x)


func height_at(x: float, z: float) -> float:
	return terrain.height_at(x, z)


func safe_position(pose: Vector3) -> Vector3:
	var result := pose
	var center := terrain.corridor_center(result.x)
	var width := terrain.boundary_width(result.x) - 6.0
	result.z = clampf(result.z, center - width, center + width)
	var ground := height_at(result.x, result.z)
	if result.y < ground - 3.0:
		result.y = ground + 0.6
	return result


func biome_at(x: float) -> int:
	return terrain.biome_at(x)


func update_stream(x: float) -> void:
	var current := int(floor(x / CHUNK_LENGTH))
	_current = current
	for index in range(current - 2, current + 5):
		if not chunks.has(index):
			if async_streaming:
				if not index in _pending and not (_worker != null and _worker_index == index): _pending.append(index)
			else: _create_chunk(index)
	for index: int in chunks.keys():
		if index < current - 2 or index > current + 4:
			var chunk: Node3D = chunks[index]
			remove_child(chunk)
			chunk.queue_free()
			chunks.erase(index)
			buildings.erase(index)
	_pending = _pending.filter(func(index: int) -> bool: return index >= current - 2 and index <= current + 4)


func _process(_dt: float) -> void:
	clock += _dt
	_rut_timer += _dt
	if _rut_worker != null and not _rut_worker.is_alive():
		var result: Dictionary = _rut_worker.wait_to_finish()
		_rut_worker = null
		if chunks.has(_rut_key.x) and chunks[_rut_key.x].get_instance_id() == _rut_chunk_id:
			var commit_start := Time.get_ticks_usec()
			_commit_rut_band(chunks[_rut_key.x], result)
			max_deformation_ms = maxf(max_deformation_ms, (Time.get_ticks_usec() - commit_start) / 1000.0)
	if _rut_worker == null and _rut_timer >= 0.30 and not _rut_dirty.is_empty():
		_rut_timer = 0.0
		var key: Vector2i = _rut_dirty.keys()[0]
		_rut_dirty.erase(key)
		if chunks.has(key.x):
			var chunk: Node3D = chunks[key.x]
			var arrays: Array = chunk.get_meta("terrain_arrays")
			var field := DriveDeformation.new()
			field.cells = terrain.deformation.cells.duplicate()
			_rut_key = key
			_rut_chunk_id = chunk.get_instance_id()
			_rut_worker = Thread.new()
			_rut_worker.start(DriveTerrainBake.deform_band.bind(field, arrays[Mesh.ARRAY_VERTEX], chunk.get_meta("rest_heights"), int(chunk.get_meta("terrain_width")), key.y))
	if not async_streaming: return
	var started := Time.get_ticks_usec()
	if _worker != null and not _worker.is_alive():
		var baked: Dictionary = _worker.wait_to_finish()
		_worker = null
		if _worker_epoch == _epoch and not chunks.has(int(baked.index)) and absi(int(baked.index) - _current) <= 4:
			last_worker_ms = float(baked.worker_ms)
			_create_chunk(int(baked.index), baked)
	while not _jobs.is_empty() and Time.get_ticks_usec() - started < 2500:
		var job := _jobs.pop_front() as Callable
		if job.is_valid(): job.call()
	max_commit_ms = maxf(max_commit_ms, (Time.get_ticks_usec() - started) / 1000.0)
	if _worker == null and _jobs.is_empty() and not _pending.is_empty():
		_pending.sort_custom(func(a: int, b: int) -> bool: return absi(a - _current) < absi(b - _current))
		_worker_index = _pending.pop_front()
		_worker_epoch = _epoch
		var snapshot := DriveTerrain.new()
		snapshot.seed_value = world_seed
		snapshot.route_choices = terrain.route_choices.duplicate()
		snapshot.soil_moisture = terrain.soil_moisture
		snapshot.deformation.cells = terrain.deformation.cells.duplicate()
		_worker = Thread.new()
		_worker.start(DriveTerrainBake.bake.bind(snapshot, _worker_index))


func _stop_worker() -> void:
	if _rut_worker != null:
		_rut_worker.wait_to_finish()
		_rut_worker = null
	if _worker != null:
		_worker.wait_to_finish()
		_worker = null


func _exit_tree() -> void:
	_stop_worker()


func choose_route(pose: Vector3) -> Dictionary:
	var fork := terrain.fork_at(pose.x)
	if fork.is_empty() or pose.x < float(fork.start) + 96.0 or terrain.route_choices.has(str(fork.id)): return {}
	var lanes := terrain.road_centers(pose.x)
	var sign_value := -1 if absf(pose.z - lanes[0]) < absf(pose.z - lanes[1]) else 1
	if minf(absf(pose.z - lanes[0]), absf(pose.z - lanes[1])) > 7.0: return {}
	terrain.route_choices[str(fork.id)] = sign_value
	terrain.clear_caches()
	_epoch += 1
	# Commit before the exit: only distant, unoccupied geometry is replaced.
	var first := int(floor((float(fork.start) + DriveTerrain.SECTION) / CHUNK_LENGTH))
	for index: int in chunks.keys():
		if index >= first:
			chunks[index].queue_free()
			chunks.erase(index)
			buildings.erase(index)
	return {"id": int(fork.id), "side": sign_value}


func nearest_building(pose: Vector3, radius: float = 19.0) -> Dictionary:
	var found: Dictionary = {}
	var best := radius
	for building: Dictionary in buildings.values():
		var target: Vector3 = building.position
		var distance := Vector2(target.x - pose.x, target.z - pose.z).length()
		if distance < best:
			best = distance
			found = building
	for debris: Node in get_tree().get_nodes_in_group("scavenge_debris"):
		if not is_ancestor_of(debris): continue
		var distance := pose.distance_to(debris.global_position)
		if distance < minf(best, 8.0):
			best = distance
			found = {"id": int(debris.get_meta("loot_id")), "kind": "debris", "title": "Rozbitá prepravka", "position": debris.global_position}
	return found


func next_stop(x: float) -> Dictionary:
	var stop := DriveStops.next_stop(x, world_seed)
	if not stop.is_empty():
		stop["position"] = _roadside_point(float(stop.x), float(stop.offset))
	return stop


func _create_chunk(index: int, baked: Dictionary = {}) -> void:
	var chunk := Node3D.new()
	chunk.name = "Desert_%d" % index
	add_child(chunk)
	chunks[index] = chunk
	var start := index * CHUNK_LENGTH
	var stop := DriveStops.for_chunk(index, terrain.seed_at(start))
	# Include adjoining owners: a yard/driveway can extend across a chunk seam.
	var nearby_stops: Array[Dictionary] = []
	for neighbor in range(index - 1, index + 2):
		var nearby := DriveStops.for_chunk(neighbor, terrain.seed_at(neighbor * CHUNK_LENGTH))
		if not nearby.is_empty():
			nearby_stops.append(nearby)
	if baked.is_empty(): baked = DriveTerrainBake.bake(terrain, index)
	var ground := MeshInstance3D.new()
	ground.name = "SandAndTracks"
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, baked.arrays, [], {}, Mesh.ARRAY_FLAG_USE_DYNAMIC_UPDATE)
	ground.mesh = mesh
	# Receives prop shadows, but does not self-shadow its huge grazing-angle
	# triangles: that produced the diagonal shadow-acne film in Compatibility.
	ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var material := ShaderMaterial.new()
	material.shader = load("res://shaders/desert_surface.gdshader")
	material.set_shader_parameter("atlas", _atlas)
	ground.material_override = material
	chunk.add_child(ground)
	var body := StaticBody3D.new()
	ground.add_child(body)
	chunk.set_meta("terrain_arrays", baked.arrays)
	chunk.set_meta("rest_heights", baked.rest_heights)
	chunk.set_meta("terrain_width", baked.width)
	for band in 3:
		var collision := CollisionShape3D.new()
		collision.name = "GroundBand%d" % band
		var shape := ConcavePolygonShape3D.new()
		collision.shape = shape
		body.add_child(collision)
		_schedule(func() -> void:
			if is_instance_valid(collision): shape.set_faces(baked.collision_bands[band]))
	_schedule(func() -> void:
		if is_instance_valid(chunk): _create_deck(chunk, baked.deck))
	for segment in 12: _schedule(func() -> void:
		if is_instance_valid(chunk): _create_boundaries(chunk, start, segment))
	_schedule(func() -> void:
		if is_instance_valid(chunk): _create_baked_water(chunk, baked.water))
	var rng := RandomNumberGenerator.new()
	rng.seed = world_seed + index * 104729
	for count in 38:
		var x := start + rng.randf_range(0.0, CHUNK_LENGTH)
		var side := -1.0 if count % 2 == 0 else 1.0
		var offset := rng.randf_range(9.0, terrain.boundary_width(x) - 10.0) * side
		var pos := _point(x, offset)
		if absf(terrain.road_distance(x, pos.z)) < 8.5 or terrain.water_depth(x, pos.z) > 0.0:
			continue
		if _reserved_for_stop(pos, nearby_stops, 3.0):
			continue
		var scale_value := rng.randf_range(0.65, 1.30)
		var blend := terrain.blend_at(x)
		var local_biome := int(blend.to) if rng.randf() < float(blend.amount) else int(blend.from)
		var snow_cover := lerpf(1.0 if int(blend.from) == 4 else 0.0, 1.0 if int(blend.to) == 4 else 0.0, float(blend.amount))
		var yaw := rng.randf_range(-PI, PI)
		_schedule(func() -> void:
			if not is_instance_valid(chunk): return
			if local_biome in [2, 3, 4, 5] and count % 4 == 0: BiomeModels.tree(chunk, pos, scale_value, local_biome == 4, local_biome == 5, snow_cover)
			elif local_biome < 2 and count % 4 == 0: _place_model(chunk, "saguaro", pos, Vector3.ONE * scale_value * 0.8, yaw)
			elif count % 7 == 0: _place_model(chunk, "mesa_rock", pos, Vector3(1.8, 0.7, 1.2) * scale_value, x)
			else: _place_model(chunk, "desert_scrub", pos, Vector3.ONE * scale_value, x))
	for pole_index in 3:
		var x := start + 12.0 + pole_index * 32.0
		var a := _roadside_point(x, -10.5)
		var b := _roadside_point(x + 32.0, -10.5)
		var river_a := terrain.feature_at(x)
		var river_b := terrain.feature_at(x + 32)
		if (not river_a.is_empty() and river_a.kind == "bridge") or (not river_b.is_empty() and river_b.kind == "bridge"): continue
		if terrain.water_depth(a.x, a.z) > 0.05 or terrain.water_depth(b.x, b.z) > 0.05: continue
		if not _reserved_for_stop(a, nearby_stops, 0.5):
			_schedule(func() -> void:
				if is_instance_valid(chunk): _place_model(chunk, "utility_pole", a))
		for side: float in [0.0]:
			for segment in 6:
				var t0 := segment / 6.0
				var t1 := (segment + 1) / 6.0
				var p0 := a.lerp(b, t0) + Vector3(0, 9.22 - sin(t0 * PI) * 0.85, side)
				var p1 := a.lerp(b, t1) + Vector3(0, 9.22 - sin(t1 * PI) * 0.85, side)
				_schedule(func() -> void:
					if is_instance_valid(chunk): DesertModels.beam(chunk, "PowerLine", p0, p1, 0.022, DesertModels.mat("rubber")))
	for fence_index in 19:
		var x := start + fence_index * 5.0
		var fence_point := _point(x, -24.0)
		var feature := terrain.feature_at(x)
		if not feature.is_empty() and feature.kind == "bridge": continue
		if terrain.water_depth(x, fence_point.z) > 0.05: continue
		if _reserved_for_stop(_point(x + 2.5, -24.0), nearby_stops, 3.0):
			continue
		if terrain.fork_at(x).is_empty():
			var pos := _point(x, -24.0)
			_schedule(func() -> void:
				if is_instance_valid(chunk): _place_model(chunk, "ranch_fence", pos))
	if not stop.is_empty():
		_schedule(func() -> void:
			if is_instance_valid(chunk): _create_stop(chunk, stop))
	if hazards_enabled:
		var feature := terrain.feature_at(start + 48.0)
		if not feature.is_empty() and float(feature.x) >= start and float(feature.x) < start + CHUNK_LENGTH:
			_schedule(func() -> void:
				if not is_instance_valid(chunk): return
				var hazard := RouteHazard.new()
				hazard.world = self
				hazard.kind = str(feature.kind)
				hazard.feature_x = float(feature.x)
				chunk.add_child(hazard)
				hazard.build())
		var region := maxi(0, int(floor(start / 960.0)))
		var obstacle_x := region * 960.0 + 270.0
		if obstacle_x >= start and obstacle_x < start + CHUNK_LENGTH:
			_schedule(func() -> void:
				if not is_instance_valid(chunk): return
				var crate := RigidBody3D.new()
				crate.name = "RoadDebris"
				crate.mass = 22
				crate.position = _point(obstacle_x, 2.3) + Vector3.UP * 0.6
				chunk.add_child(crate)
				crate.set_meta("loot_id", -1000000 - region)
				crate.add_to_group("scavenge_debris")
				DesertModels.box(crate, "Crate", Vector3.ZERO, Vector3(1.0, 1.1, 1.0), DesertModels.mat("wood"))
				var crate_shape := CollisionShape3D.new()
				var crate_box := BoxShape3D.new()
				crate_box.size = Vector3.ONE
				crate_shape.shape = crate_box
				crate.add_child(crate_shape))


func _schedule(job: Callable) -> void:
	if async_streaming: _jobs.append(job)
	else: job.call()


func _reserved_for_stop(pos: Vector3, stops: Array[Dictionary], margin: float = 0.0) -> bool:
	for stop: Dictionary in stops:
		var dx := absf(pos.x - float(stop.x))
		var center_z := center_at(float(stop.x)) + float(stop.offset)
		# Clear the complete model/porch and turning yard, including scenery crowns.
		if dx < 15.0 + margin and absf(pos.z - center_z) < 8.0 + margin:
			return true
		# The connecting lane follows the road even on a bend. Both sides are valid.
		var side := signf(float(stop.offset))
		var lateral := (pos.z - center_at(pos.x)) * side
		# The broad gravel yard follows the same curved centerline as its mesh.
		if dx < 15.0 + margin and lateral > absf(float(stop.offset)) - 9.0 - margin and lateral < absf(float(stop.offset)) + 5.0 + margin:
			return true
		if dx < 8.0 + margin and lateral > 5.0 - margin and lateral < absf(float(stop.offset)) + 5.0 + margin:
			return true
	return false


func _create_stop(chunk: Node3D, metadata: Dictionary) -> void:
	var stop := metadata.duplicate()
	var x := float(stop.x)
	var offset := float(stop.offset)
	var pos := _roadside_point(x, offset)
	var scene: PackedScene = _stop_scenes["start" if int(stop.id) == 0 else str(stop.kind)]
	var model := scene.instantiate() as Node3D
	model.name = "Building_%d_%s" % [int(stop.id), str(stop.kind)]
	model.position = pos
	model.rotation.y = PI if offset > 0.0 else 0.0
	chunk.add_child(model)
	stop["position"] = pos
	buildings[int(stop.id)] = stop
	# Thin, terrain-conforming gravel overlays use the existing terrain collision.
	# No slab/curb across the entrance, and no duplicate plane above the road.
	_create_access_patch(chunk, stop, 6.8, absf(offset) - 3.0, 4.5, "SideDriveway")
	_create_access_patch(chunk, stop, absf(offset) - 8.0, absf(offset) + 3.7, 11.5, "TurningYard")
	var sign_pos := _point(x - 14.0, signf(offset) * 8.4)
	StopModels.roadside_sign(chunk, sign_pos, str(stop.title), offset > 0.0)


func _create_access_patch(chunk: Node3D, stop: Dictionary, inner: float, outer: float, half_length: float, label: String) -> void:
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	var nx := int(ceil(half_length))
	var nz := int(ceil((outer - inner) * 0.5))
	var side := signf(float(stop.offset))
	for xi in nx:
		for zi in nz:
			for corner: Vector2i in [Vector2i(0, 0), Vector2i(1, 0), Vector2i(0, 1), Vector2i(1, 0), Vector2i(1, 1), Vector2i(0, 1)]:
				var x := float(stop.x) - half_length + (xi + corner.x) * half_length * 2.0 / nx
				# Always walk Z in increasing order so both roadside patches face up.
				var t := float(zi + corner.y) / nz
				var offset := lerpf(inner, outer, t if side > 0.0 else 1.0 - t) * side
				var p := _point(x, offset) + Vector3.UP * 0.035
				surface.set_normal(Vector3.UP)
				surface.add_vertex(p)
	var patch := MeshInstance3D.new()
	patch.name = label
	patch.mesh = surface.commit()
	patch.material_override = StopModels.material(Color("a79c80"))
	patch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	chunk.add_child(patch)


func _create_boundaries(chunk: Node3D, start: float, segment: int) -> void:
	var body := StaticBody3D.new()
	body.name = "MapBoundary"
	chunk.add_child(body)
	for side: float in [-1.0, 1.0]:
		for index in range(segment, segment + 1):
			var x := start + index * 8.0
			var next_x := x + 8.0
			var a := _boundary_point(x, side, -2.0)
			var b := _boundary_point(next_x, side, -2.0)
			var shape := CollisionShape3D.new()
			var box := BoxShape3D.new()
			box.size = Vector3(Vector2(b.x - a.x, b.z - a.z).length() + 2.0, 60.0, 8.0)
			shape.shape = box
			shape.position = (a + b) * 0.5 + Vector3.UP * 20.0
			shape.rotation.y = -atan2(b.z - a.z, b.x - a.x)
			body.add_child(shape)
			# Overlapping full-size rocks on BOTH sides, including every chunk seam.
			var rock := _place_model(chunk, "mesa_rock", _boundary_point(x + 4.0, side) - Vector3.UP * 1.0,
				Vector3(8.4, 5.5 + sin(x * 0.19) * 1.2, 8.0), sin(x * 0.03) * 0.22)
			rock.name = "UpperCliff" if side < 0.0 else "LowerCliff"
			var blend := terrain.blend_at(x)
			var snow_cover := lerpf(1.0 if int(blend.from) == 4 else 0.0, 1.0 if int(blend.to) == 4 else 0.0, float(blend.amount))
			if snow_cover > 0.03: BiomeModels.snow_cap(rock, snow_cover)


func _boundary_point(x: float, side: float, inset: float = 0.0) -> Vector3:
	var z := terrain.corridor_center(x) + (terrain.boundary_width(x) + inset) * side
	return Vector3(x, height_at(x, z), z)


func _place_model(parent: Node3D, key: String, pos: Vector3, dimensions := Vector3.ONE, yaw: float = 0.0) -> Node3D:
	var scene: PackedScene = _models[key]
	var node := scene.instantiate() as Node3D
	node.position = pos
	node.scale = dimensions
	node.rotation.y = yaw
	parent.add_child(node)
	# Static props share geometry/materials in their authored scenes. Batch rocks,
	# fences and poles too; the old cliff belt alone submitted thousands of draws.
	_batch_visuals(node, parent)
	return node


func _batch_visuals(node: Node, chunk: Node3D) -> void:
	for child: Node in node.get_children():
		_batch_visuals(child, chunk)
	if not node is MeshInstance3D: return
	var groups: Dictionary = chunk.get_meta("prop_batches", {})
	var key := str(node.mesh.get_instance_id()) + "/" + str(node.material_override.get_instance_id() if node.material_override != null else 0)
	if not groups.has(key):
		var batch := MultiMeshInstance3D.new()
		batch.name = "InstancedVegetation"
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = node.mesh
		mm.instance_count = 64
		mm.visible_instance_count = 0
		batch.multimesh = mm
		batch.material_override = node.material_override
		chunk.add_child(batch)
		groups[key] = batch
		chunk.set_meta("prop_batches", groups)
	var target: MultiMeshInstance3D = groups[key]
	var count := target.multimesh.visible_instance_count
	if count < 64:
		target.multimesh.set_instance_transform(count, chunk.global_transform.affine_inverse() * node.global_transform)
		target.multimesh.visible_instance_count = count + 1
		node.free()


func _point(x: float, offset: float) -> Vector3:
	var z := center_at(x) + offset
	return Vector3(x, height_at(x, z), z)


func _roadside_point(x: float, offset: float) -> Vector3:
	var z := terrain.road_centers(x)[0] + offset
	return Vector3(x, height_at(x, z), z)


func _create_signs(chunk: Node3D, start: float) -> void:
	var next := terrain.junction_ahead(start)
	var sign_x := start + float(next.distance) - 22.0
	if sign_x < start or sign_x >= start + CHUNK_LENGTH:
		return
	var p := _roadside_point(sign_x, -9.0)
	DesertModels.cylinder(chunk, "JunctionPost", p + Vector3.UP * 2.0, 0.10, 4.0, DesertModels.mat("steel"))
	for i in 2:
		DesertModels.box(chunk, "RouteSign", p + Vector3(0, 3.3 - i * 0.7, 0), Vector3(5.5, 0.65, 0.14), DesertModels.mat("wood"))
		var label := Label3D.new()
		label.text = "↑ ASFALT / KOPCE" if i == 0 else "↓ ŠTRK / BROD"
		label.position = p + Vector3(0, 3.3 - i * 0.7, 0.09)
		label.pixel_size = 0.008
		label.font_size = 36
		label.outline_size = 3
		label.modulate = Color("ffe3a3")
		label.billboard = BaseMaterial3D.BILLBOARD_DISABLED
		chunk.add_child(label)


func _create_water(chunk: Node3D, start: float) -> void:
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	var count := 0
	# Use the same cells as the bed; clip each triangle to the actual waterline.
	for xi in 48:
		var x := start + xi * 2.0
		var pools := terrain.pools_near(x + 1.0)
		for pool: Dictionary in pools:
			for zi in range(-4, 4):
				var z := float(pool.z) + zi * 2.0
				var points: Array[Vector3] = []
				for v: Vector2 in [Vector2(x, z), Vector2(x + 2, z), Vector2(x, z + 2), Vector2(x + 2, z), Vector2(x + 2, z + 2), Vector2(x, z + 2)]:
					points.append(Vector3(v.x, float(pool.level) + 0.015, v.y))
				for ti in 2:
					var polygon: Array[Vector3] = []
					for edge in 3:
						var a := points[ti * 3 + edge]
						var b := points[ti * 3 + (edge + 1) % 3]
						var da := _water_depth_for(pool, a)
						var db := _water_depth_for(pool, b)
						if da > 0.0: polygon.append(a)
						if (da > 0.0) != (db > 0.0): polygon.append(a.lerp(b, da / (da - db)))
					for fan in range(1, polygon.size() - 1):
						for p: Vector3 in [polygon[0], polygon[fan], polygon[fan + 1]]:
							surface.set_normal(Vector3.UP)
							surface.set_uv(Vector2(p.x, p.z))
							surface.add_vertex(p)
							count += 1
	if count == 0: return
	var water := MeshInstance3D.new()
	water.name = "DriveableWater"
	water.mesh = surface.commit()
	water.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var material := ShaderMaterial.new()
	material.shader = load("res://shaders/water.gdshader")
	water.material_override = material
	chunk.add_child(water)


func _water_depth_for(pool: Dictionary, p: Vector3) -> float:
	var radius := Vector2((p.x - float(pool.x)) / float(pool.rx), (p.z - float(pool.z)) / float(pool.rz)).length()
	if radius >= 1.0: return -0.01
	return terrain.water_level(pool, p.x, p.z) - height_at(p.x, p.z)


func compact_tyres(points: Array[Vector3], surface: Dictionary, pressure: float) -> void:
	if not deformation_enabled: return
	for point: Vector3 in points:
		if not terrain.deformation.compact(point, surface, pressure): continue
		for strip in range(floori((point.x - 2.0) / 32.0), floori((point.x + 2.0) / 32.0) + 1):
			_rut_dirty[Vector2i(floori(strip / 3.0), posmod(strip, 3))] = true


func load_tyres(points: Array[Vector3], pressure: float, slip: float, effort: float, dt: float) -> void:
	if not deformation_enabled: return
	for point: Vector3 in points:
		# Each tyre samples its own ground: straddling asphalt and mud matters.
		var sample := terrain.sample(point.x, point.z)
		if not terrain.deformation.load_soil(point, sample, pressure, slip, effort, dt): continue
		for strip in range(floori((point.x - 3.0) / 32.0), floori((point.x + 3.0) / 32.0) + 1):
			_rut_dirty[Vector2i(floori(strip / 3.0), posmod(strip, 3))] = true


func _update_rut_band(chunk: Node3D, band: int) -> void:
	if not chunk.has_meta("terrain_arrays"): return
	var arrays: Array = chunk.get_meta("terrain_arrays")
	var width := int(chunk.get_meta("terrain_width"))
	var result := DriveTerrainBake.deform_band(terrain.deformation, arrays[Mesh.ARRAY_VERTEX], chunk.get_meta("rest_heights"), width, band)
	_commit_rut_band(chunk, result)


func _commit_rut_band(chunk: Node3D, result: Dictionary) -> void:
	var arrays: Array = chunk.get_meta("terrain_arrays")
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var changed: PackedVector3Array = result.vertices
	# Another band may have committed while this worker was running.
	for index in range(int(result.first), int(result.end)): vertices[index] = changed[index]
	arrays[Mesh.ARRAY_VERTEX] = vertices
	chunk.set_meta("terrain_arrays", arrays)
	var ground := chunk.get_node("SandAndTracks") as MeshInstance3D
	(ground.mesh as ArrayMesh).surface_update_vertex_region(0, int(result.first) * 12, vertices.slice(int(result.first), int(result.end)).to_byte_array())
	var body := ground.get_child(0) as StaticBody3D
	var shape := body.get_node("GroundBand%d" % int(result.band)) as CollisionShape3D
	(shape.shape as ConcavePolygonShape3D).set_faces(result.faces)


func _create_baked_water(chunk: Node3D, faces: PackedVector3Array) -> void:
	if faces.is_empty(): return
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	for p: Vector3 in faces:
		surface.set_normal(Vector3.UP)
		surface.set_uv(Vector2(p.x, p.z))
		surface.add_vertex(p)
	var water := MeshInstance3D.new()
	water.name = "DriveableWater"
	water.mesh = surface.commit()
	water.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var material := ShaderMaterial.new()
	material.shader = load("res://shaders/water.gdshader")
	water.material_override = material
	chunk.add_child(water)


func _create_deck(chunk: Node3D, faces: PackedVector3Array) -> void:
	if faces.is_empty(): return
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	for p: Vector3 in faces:
		tool.set_normal(Vector3.UP)
		tool.add_vertex(p + Vector3.UP * 0.015)
	var deck := MeshInstance3D.new()
	deck.name = "BridgeDeck"
	deck.mesh = tool.commit()
	deck.material_override = BiomeModels.material(Color("96968a"))
	chunk.add_child(deck)
	deck.create_trimesh_collision()
