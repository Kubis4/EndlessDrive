class_name DriveWorld
extends Node3D
## Original desert art direction, deterministic streaming and shared terrain collision.

const TerrainDetail = preload("res://scripts/terrain_detail.gd")
const GROUND_SHADERS = [preload("res://shaders/desert_surface_low.gdshader"), preload("res://shaders/desert_surface_balanced.gdshader"), preload("res://shaders/desert_surface.gdshader")]
const CHUNK_LENGTH := 96.0
const HALF_WIDTH := 120.0
const BOUNDARY_OFFSET := 36.0
const SAFE_HALF_WIDTH := BOUNDARY_OFFSET - 6.0
const TREE_SPACING := 12.0
const BIOMES := DriveTerrain.BIOMES
var world_seed := 24601
var terrain := DriveTerrain.new()
var chunks: Dictionary = {}
var buildings: Dictionary = {}
var _models: Dictionary = {}
var _atlas: Texture2D
var async_streaming := false
var max_commit_ms := 0.0
var max_job_ms := 0.0
var last_worker_ms := 0.0
var weather_grip := 1.0
var wind_force := 0.0
var hazards_enabled := false
var batch_scenery := true
var clock := 0.0
var _worker: Thread
var _worker_index := 0
var _worker_epoch := 0
var _epoch := 0
var _pending: Array[int] = []
var _jobs: Array[Dictionary] = []
var slowest_job := ""
var _current := 0
var _stop_scenes: Dictionary = {}
var _rut_dirty: Dictionary = {}
var _rut_timer := 0.0
var _rut_ready: Dictionary = {}
var _soil_commands: Array[Dictionary] = []
var _soil_focus_x := 0.0
var last_rut_mesh_ms := 0.0
var last_rut_collision_ms := 0.0
var _previous_tyres: Array[Vector3] = []
var last_detail_worker_ms := 0.0
var last_refined_quads := 0
var deformation_enabled := true
var max_deformation_ms := 0.0
var _rut_worker: Thread
var _material_wetness := -1.0
var route_origin := Vector3.ZERO
var _encounter_cache: Dictionary = {}
var _retiring_chunks: Dictionary = {}
var _wire_mesh: CylinderMesh
var graphics_quality := 2


func set_graphics_quality(level: int) -> void:
	graphics_quality = clampi(level, 0, 2)
	for chunk: Node3D in chunks.values() + _retiring_chunks.values():
		var ground := chunk.get_node_or_null("SandAndTracks") as MeshInstance3D
		if ground != null:
			var material := ground.material_override as ShaderMaterial
			if material != null: _configure_ground_material(material)


func _configure_ground_material(material: ShaderMaterial) -> void:
	material.shader = GROUND_SHADERS[graphics_quality]
	material.set_shader_parameter("surface_atlas", load("res://assets/textures/surface-albedo-atlas.png"))
	material.set_shader_parameter("micro_height", load("res://assets/textures/surface-microheight-2k.png") if graphics_quality == 2 else null)
	material.set_shader_parameter("surface_data_enabled", true)
	material.set_shader_parameter("rain_wetness", terrain.soil_moisture)
	material.set_shader_parameter("route_origin", Vector2(route_origin.x, route_origin.z))


func _ready() -> void:
	_wire_mesh = CylinderMesh.new()
	_wire_mesh.height = 1.0
	_wire_mesh.top_radius = 1.0
	_wire_mesh.bottom_radius = 1.0
	_wire_mesh.radial_segments = 5
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	process_physics_priority = -50
	_atlas = load("res://assets/textures/desert-materials.png")
	preload("res://scripts/expedition_scenery.gd").warmup()
	for key: String in ["adobe_workshop", "saguaro", "desert_scrub", "mesa_rock", "utility_pole", "ranch_fence", "water_tower", "road_delineator"]:
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
	_soil_commands.clear()
	_encounter_cache.clear()
	_previous_tyres.clear()
	terrain.deformation.restore([])
	terrain.soil_moisture = 0.0
	world_seed = value
	terrain.seed_value = value
	terrain.route_choices.clear()
	terrain.clear_caches()
	for old: Node3D in _retiring_chunks.values():
		remove_child(old)
		old.queue_free()
	_retiring_chunks.clear()
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
	for key: Vector2i in _rut_dirty.keys():
		if key.x < current - 2 or key.x > current + 4: _rut_dirty.erase(key)
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
	for index: int in _retiring_chunks.keys():
		if index < current - 2 or index > current + 4:
			var old: Node3D = _retiring_chunks[index]
			remove_child(old)
			old.queue_free()
			_retiring_chunks.erase(index)


func _process(_dt: float) -> void:
	clock += _dt
	if absf(terrain.soil_moisture - _material_wetness) > 0.02:
		_material_wetness = terrain.soil_moisture
		for chunk: Node3D in chunks.values():
			var ground := chunk.get_node_or_null("SandAndTracks") as MeshInstance3D
			if ground != null:
				(ground.material_override as ShaderMaterial).set_shader_parameter("rain_wetness", _material_wetness)
	_rut_timer += _dt
	if _rut_worker != null and not _rut_worker.is_alive():
		_rut_ready = _rut_worker.wait_to_finish()
		_rut_worker = null
	if _rut_worker == null and _rut_ready.is_empty() and _rut_timer >= 0.08 and (not _rut_dirty.is_empty() or not _soil_commands.is_empty()):
		_rut_timer = 0.0
		var jobs: Array[Dictionary] = []
		var candidates := {}
		for command in _soil_commands:
			var a: Vector3 = command.start if int(command.kind) == 0 else command.point
			var b: Vector3 = command.end if int(command.kind) == 0 else command.point
			for strip in range(floori((minf(a.x, b.x) - 1.25) / DriveTerrainBake.BAND_ROWS), floori((maxf(a.x, b.x) + 1.25) / DriveTerrainBake.BAND_ROWS) + 1):
				candidates[Vector2i(floori(float(strip) / DriveTerrainBake.BAND_COUNT), posmod(strip, DriveTerrainBake.BAND_COUNT))] = true
		# Restored history may span many chunks. Only two forced rebuilds may
		# join a batch, prioritised around the car instead of publishing a full
		# saved journey's meshes in one physics tick.
		var forced := _rut_dirty.keys()
		var focus_strip := _soil_focus_x / DriveTerrainBake.BAND_ROWS
		forced.sort_custom(func(a: Vector2i, b: Vector2i) -> bool:
			return absf(a.x * DriveTerrainBake.BAND_COUNT + a.y - focus_strip) < absf(b.x * DriveTerrainBake.BAND_COUNT + b.y - focus_strip))
		for key: Vector2i in forced:
			if candidates.size() >= 2: break
			if chunks.has(key.x): candidates[key] = true
		for key: Vector2i in candidates:
			if not chunks.has(key.x): continue
			var chunk: Node3D = chunks[key.x]
			if not chunk.get_meta("terrain_ready", false): continue
			jobs.append({"key": key, "owner_id": chunk.get_instance_id(), "arrays": chunk.get_meta("terrain_arrays"), "rest": chunk.get_meta("rest_heights"), "width": int(chunk.get_meta("terrain_width")), "force": _rut_dirty.has(key)})
		if not jobs.is_empty() or not _soil_commands.is_empty():
			var snapshot := _soil_snapshot()
			var commands := _soil_commands
			_soil_commands = []
			_rut_worker = Thread.new()
			_rut_worker.start(TerrainDetail.apply_commands.bind(snapshot, commands, jobs), Thread.PRIORITY_LOW)
	if not async_streaming: return
	var started := Time.get_ticks_usec()
	if _worker != null and not _worker.is_alive():
		var baked: Dictionary = _worker.wait_to_finish()
		_worker = null
		if _worker_epoch == _epoch and not chunks.has(int(baked.index)) and absi(int(baked.index) - _current) <= 4:
			last_worker_ms = float(baked.worker_ms)
			_create_chunk(int(baked.index), baked)
	while not _jobs.is_empty() and Time.get_ticks_usec() - started < 2500:
		var entry := _jobs.pop_front() as Dictionary
		var job: Callable = entry.job
		var owner_ref: WeakRef = entry.owner
		if owner_ref != null:
			var owner_node := owner_ref.get_ref() as Node
			# Branch choices invalidate future chunks, not the unfinished collision
			# bands of the fork we are driving through. Detached owners are stale;
			# retained owners must finish even when the route epoch advances.
			if owner_node == null or not owner_node.is_inside_tree(): continue
		elif int(entry.epoch) != _epoch:
			continue
		if job.is_valid():
			var job_started := Time.get_ticks_usec()
			job.call()
			var job_ms := (Time.get_ticks_usec() - job_started) / 1000.0
			if job_ms > max_job_ms:
				max_job_ms = job_ms
				slowest_job = str(entry.label)
	max_commit_ms = maxf(max_commit_ms, (Time.get_ticks_usec() - started) / 1000.0)
	# Numeric preparation can overlap the small publication jobs of the previous
	# chunk. Waiting for every decorative prop starved fast-moving vehicles.
	if _worker == null and _jobs.size() < 512 and not _pending.is_empty():
		_pending.sort_custom(func(a: int, b: int) -> bool: return absi(a - _current) < absi(b - _current))
		_worker_index = _pending.pop_front()
		_worker_epoch = _epoch
		var snapshot := DriveTerrain.new()
		snapshot.seed_value = world_seed
		snapshot.route_choices = terrain.route_choices.duplicate()
		snapshot.soil_moisture = terrain.soil_moisture
		# Most new chunks are ahead of every driven tyre. Do not clone 65k
		# historical cells just to produce a completely untouched future chunk.
		if terrain.deformation.overlaps_x(_worker_index * CHUNK_LENGTH - 1.0, (_worker_index + 1) * CHUNK_LENGTH + 1.0):
			snapshot.deformation.cells = terrain.deformation.cells.duplicate()
		_worker = Thread.new()
		_worker.start(DriveTerrainBake.bake.bind(snapshot, _worker_index), Thread.PRIORITY_LOW)


func _physics_process(_dt: float) -> void:
	_publish_soil_ready()


func _soil_snapshot() -> DriveTerrain:
	var snapshot := DriveTerrain.new()
	snapshot.seed_value = world_seed
	snapshot.route_choices = terrain.route_choices.duplicate()
	snapshot.soil_moisture = terrain.soil_moisture
	snapshot.deformation.cells = terrain.deformation.cells.duplicate()
	snapshot.deformation._order = terrain.deformation._order.slice(terrain.deformation._order_head)
	snapshot.deformation._order_head = 0
	return snapshot


func _publish_soil_ready() -> void:
	if _rut_ready.is_empty(): return
	var ready := _rut_ready
	_rut_ready = {}
	var started := Time.get_ticks_usec()
	for cell: Vector2i in ready.changes: terrain.deformation._store(cell, float(ready.changes[cell]))
	for key: Vector2i in ready.dirty:
		if key.x >= _current - 2 and key.x <= _current + 4: _rut_dirty[key] = true
	# Adjacent changed bands share one snapshot and publish before the next
	# vehicle physics update, never leaving a temporary collision lip.
	for entry: Dictionary in ready.bands:
		var key: Vector2i = entry.key
		if chunks.has(key.x) and chunks[key.x].get_instance_id() == int(entry.owner_id):
			_commit_rut_band(chunks[key.x], entry.result)
			_rut_dirty.erase(key)
	max_deformation_ms = maxf(max_deformation_ms, (Time.get_ticks_usec() - started) / 1000.0)


func flush_soil_updates() -> void:
	# Explicit synchronous tool/test operation; gameplay uses the worker.
	if _rut_worker != null:
		_rut_ready = _rut_worker.wait_to_finish()
		_rut_worker = null
	_publish_soil_ready()
	if not _soil_commands.is_empty():
		var commands := _soil_commands
		_soil_commands = []
		var no_jobs: Array[Dictionary] = []
		_rut_ready = TerrainDetail.apply_commands(_soil_snapshot(), commands, no_jobs)
		_publish_soil_ready()


func _stop_worker() -> void:
	_rut_ready.clear()
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
	if fork.is_empty() or pose.x < float(fork.end) - 72.0: return {}
	var lanes := terrain.road_centers(pose.x)
	if lanes.size() < 2 or lanes[-1] - lanes[0] < 10.0: return {}
	var sign_value := -1 if absf(pose.z - lanes[0]) < absf(pose.z - lanes[-1]) else 1
	if minf(absf(pose.z - lanes[0]), absf(pose.z - lanes[-1])) > 7.0: return {}
	var revised := terrain.route_choices.has(str(fork.id))
	if revised:
		var previous := int(terrain.route_choices[str(fork.id)])
		if previous == sign_value: return {}
		var previous_lane := float(lanes[0] if previous < 0 else lanes[-1])
		var next_lane := float(lanes[0] if sign_value < 0 else lanes[-1])
		if absf(pose.z - previous_lane) - absf(pose.z - next_lane) < 4.0: return {}
	terrain.route_choices[str(fork.id)] = sign_value
	terrain.clear_caches()
	_epoch += 1
	# Commit before the exit: only distant, unoccupied geometry is replaced.
	var first := floori((float(fork.end) + 180.0) / CHUNK_LENGTH)
	if _worker != null and _worker_index < first: _worker_epoch = _epoch
	for index: int in chunks.keys():
		if index >= first:
			var chunk: Node3D = chunks[index]
			# Keep EVERY visible old floor, including the next chunk the car can
			# reach before its worker finishes. Replacement publishes atomically.
			if not _retiring_chunks.has(index): _retiring_chunks[index] = chunk
			else:
				remove_child(chunk)
				chunk.queue_free()
			chunks.erase(index)
			buildings.erase(index)
	# Old pending scenery belongs to the obsolete route. Only its remaining
	# collision installation is allowed to finish while retaining the floor.
	_jobs = _jobs.filter(func(entry: Dictionary) -> bool:
		var owner_node: Node = entry.owner.get_ref() if entry.owner != null else null
		if owner_node == null or owner_node not in _retiring_chunks.values(): return true
		return str(entry.label) in ["collision_bands", "_create_deck", "publish_collision"])
	return {"id": int(fork.id), "side": sign_value, "revised": revised}


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
		var debris_position := to_local(debris.global_position)
		var distance := pose.distance_to(debris_position)
		if distance < minf(best, 8.0):
			best = distance
			found = {"id": int(debris.get_meta("loot_id")), "kind": "debris", "title": str(debris.get_meta("loot_title", "Rozbitá prepravka")), "position": debris_position}
	return found


func find_shelter(pose: Vector3, max_distance: float = 300.0, ahead_only: bool = true) -> Dictionary:
	var found := {}
	var best := max_distance
	for refuge: Node in get_tree().get_nodes_in_group("storm_shelter"):
		if not is_ancestor_of(refuge) or not refuge.is_inside_tree(): continue
		var pos := to_local((refuge as Node3D).global_position)
		if ahead_only and pos.x < pose.x - 8.0: continue
		var distance := Vector2(pos.x - pose.x, pos.z - pose.z).length()
		if distance < best:
			best = distance
			found = {"position": pos, "title": "Cestný prístrešok", "extent": Vector3(4.8, 3.4, 4.8), "distance": distance}
	for building: Dictionary in buildings.values():
		if str(building.kind) not in ["workshop", "repair", "paint"]: continue
		var pos: Vector3 = building.position
		if ahead_only and pos.x < pose.x - 8.0: continue
		var distance := Vector2(pos.x - pose.x, pos.z - pose.z).length()
		if distance < best:
			best = distance
			found = {"position": pos, "title": str(building.title), "extent": Vector3(4.5, 3.5, 3.3), "distance": distance}
	return found


func is_sheltered(pose: Vector3) -> bool:
	for refuge: Node in get_tree().get_nodes_in_group("storm_shelter"):
		if not is_ancestor_of(refuge) or not refuge.is_inside_tree(): continue
		var pos := to_local((refuge as Node3D).global_position)
		if absf(pose.x - pos.x) < 4.4 and absf(pose.z - pos.z) < 4.4 and pose.y >= pos.y - 0.6 and pose.y < pos.y + 3.2: return true
	for building: Dictionary in buildings.values():
		if str(building.kind) not in ["workshop", "repair", "paint"]: continue
		var pos: Vector3 = building.position
		var delta := pose - pos
		# Authored garage roof is 10.5 x 7.5m, open bay 4.8m wide.
		if absf(delta.x) < 4.5 and absf(delta.z) < 3.3 and delta.y > -0.6 and delta.y < 3.5: return true
		var front := delta.z * -signf(float(building.get("offset", -18.0)))
		if absf(delta.x) < 2.8 and front > 3.3 and front < 5.2 and delta.y > -0.6 and delta.y < 2.9: return true
	return false


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
	chunk.visible = not _retiring_chunks.has(index)
	var start := index * CHUNK_LENGTH
	chunk.set_meta("chunk_start", start)
	var stop := DriveStops.for_chunk(index, terrain.seed_at(start))
	if not stop.is_empty() and terrain.road_centers(float(stop.x)).size() > 1: stop = {}
	# Include adjoining owners: a yard/driveway can extend across a chunk seam.
	var nearby_stops: Array[Dictionary] = []
	for neighbor in range(index - 1, index + 2):
		var nearby := DriveStops.for_chunk(neighbor, terrain.seed_at(neighbor * CHUNK_LENGTH))
		if not nearby.is_empty():
			nearby_stops.append(nearby)
	if baked.is_empty(): baked = DriveTerrainBake.bake(terrain, index)
	var ground := MeshInstance3D.new()
	ground.name = "SandAndTracks"
	# Receives prop shadows, but does not self-shadow its huge grazing-angle
	# triangles: that produced the diagonal shadow-acne film in Compatibility.
	ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var material := ShaderMaterial.new()
	_configure_ground_material(material)
	ground.material_override = material
	chunk.add_child(ground)
	var body := StaticBody3D.new()
	ground.add_child(body)
	chunk.set_meta("terrain_arrays", baked.arrays)
	chunk.set_meta("rest_heights", baked.rest_heights)
	chunk.set_meta("terrain_width", baked.width)
	for band in DriveTerrainBake.BAND_COUNT:
		var collision := CollisionShape3D.new()
		collision.name = "GroundBand%d" % band
		var shape := ConcavePolygonShape3D.new()
		collision.shape = shape
		body.add_child(collision)
		_schedule(func() -> void:
			if is_instance_valid(collision): shape.set_faces(baked.collision_bands[band]), chunk, "collision_bands")
	for band in DriveTerrainBake.BAND_COUNT:
		_schedule(_publish_terrain_strip.bind(ground, baked.render_bands[band], band), chunk, "terrain_mesh")
	# The worker already located restored soil; never scan the entire saved
	# field while publishing a new streamed chunk on the render thread.
	for band: int in baked.get("soil_bands", []): _rut_dirty[Vector2i(index, band)] = true
	_schedule(func() -> void:
		if is_instance_valid(chunk): _create_deck(chunk, baked.deck), chunk, "_create_deck")
	_schedule(_finish_chunk_collision.bind(chunk, index), chunk, "publish_collision")
	for segment in 12:
		for side: float in [-1.0, 1.0]:
			_schedule(_create_boundaries.bind(chunk, start, segment, side), chunk, "_create_boundaries")
	for marker in 3:
		var marker_x := start + 12.0 + marker * 32.0
		_schedule(_create_road_markers.bind(chunk, marker_x, nearby_stops), chunk, "road_markers")
	_schedule(func() -> void:
		if is_instance_valid(chunk): _create_baked_water(chunk, baked.water, baked.get("water_depths", PackedFloat32Array())), chunk, "_create_baked_water")
	var rng := RandomNumberGenerator.new()
	rng.seed = world_seed + index * 104729
	var vegetation_blend := terrain.blend_at(start + CHUNK_LENGTH * 0.5)
	var woodland := lerpf(1.0 if int(vegetation_blend.from) in [3, 5] else 0.0, 1.0 if int(vegetation_blend.to) in [3, 5] else 0.0, float(vegetation_blend.amount))
	for count in roundi(lerpf(40.0, 72.0, woodland)):
		# Root probes and terrain/yard tests belong to the frame-budgeted jobs too.
		_schedule(_create_scenery_prop.bind(chunk, start, count, rng.randi(), nearby_stops), chunk, "scenery_prop")
	for pole_index in 3:
		_schedule(_plan_utility_line.bind(chunk, start + 12.0 + pole_index * 32.0, nearby_stops), chunk, "utility_layout")
	for fence_index in 19:
		_schedule(_place_fence.bind(chunk, start + fence_index * 5.0, nearby_stops), chunk, "fence_layout")
	if not stop.is_empty():
		_schedule(func() -> void:
			if is_instance_valid(chunk): _create_stop(chunk, stop, baked.get("stop_access", {})), chunk, "_create_stop")
	for site: Dictionary in terrain.shelter_sites(floori(start / DriveTerrain.REGION)):
		if float(site.x) >= start and float(site.x) < start + CHUNK_LENGTH:
			_schedule(_create_shelter.bind(chunk, site), chunk, "roadside_shelter")
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
				if async_streaming: hazard.build_streamed()
				else: hazard.build(), chunk, "hazard.build")
		var region := maxi(0, int(floor(start / 960.0)))
		var source := supply_source(region)
		if float(source.x) >= start and float(source.x) < start + CHUNK_LENGTH:
			_schedule(func() -> void:
				if is_instance_valid(chunk): _create_supply_source(chunk, source), chunk, "_create_supply_source")
		var obstacle_x := region * 960.0 + 270.0
		if obstacle_x >= start and obstacle_x < start + CHUNK_LENGTH:
			_schedule(func() -> void:
				if not is_instance_valid(chunk): return
				var crate := load("res://scripts/road_debris.gd").new() as RigidBody3D
				crate.name = "RoadDebris"
				crate.mass = 22
				crate.position = _point(obstacle_x, 12.5) + Vector3.UP * 0.6
				chunk.add_child(crate)
				crate.set_meta("loot_id", -1000000 - region)
				crate.add_to_group("scavenge_debris")
				DesertModels.box(crate, "Crate", Vector3.ZERO, Vector3(1.0, 1.1, 1.0), DesertModels.mat("wood"))
				var crate_shape := CollisionShape3D.new()
				var crate_box := BoxShape3D.new()
				crate_box.size = Vector3.ONE
				crate_shape.shape = crate_box
				crate.add_child(crate_shape), chunk, "RoadDebris")


func _plan_utility_line(chunk: Node3D, x: float, stops: Array[Dictionary]) -> void:
	if not terrain.mountain_at(x).is_empty(): return
	var a := _roadside_point(x, -10.5)
	var b := _roadside_point(x + 32.0, -10.5)
	if terrain.feature_at(x).get("kind", "") == "bridge" or terrain.feature_at(x + 32).get("kind", "") == "bridge": return
	if terrain.water_depth(a.x, a.z) > 0.05 or terrain.water_depth(b.x, b.z) > 0.05: return
	if not _reserved_for_stop(a, stops, 0.5) and absf(terrain.road_distance(a.x, a.z)) > 8.5:
		_place_model(chunk, "utility_pole", a)
	for segment in 6:
		var t0 := segment / 6.0
		var t1 := (segment + 1) / 6.0
		var p0 := a.lerp(b, t0) + Vector3.UP * (9.22 - sin(t0 * PI) * 0.85)
		var p1 := a.lerp(b, t1) + Vector3.UP * (9.22 - sin(t1 * PI) * 0.85)
		_schedule(_place_wire.bind(chunk, p0, p1), chunk, "PowerLine")


func _place_wire(chunk: Node3D, a: Vector3, b: Vector3) -> void:
	var wire := MeshInstance3D.new()
	wire.name = "PowerLine"
	wire.mesh = _wire_mesh
	wire.material_override = DesertModels.mat("rubber")
	wire.position = (a + b) * 0.5
	wire.quaternion = Quaternion(Vector3.UP, (b - a).normalized())
	wire.scale = Vector3(0.022, a.distance_to(b), 0.022)
	wire.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	chunk.add_child(wire)
	if batch_scenery: _batch_visuals(wire, chunk)


func _place_fence(chunk: Node3D, x: float, stops: Array[Dictionary]) -> void:
	if not terrain.mountain_at(x).is_empty() or terrain.feature_at(x).get("kind", "") == "bridge": return
	var point := _point(x, -24.0)
	if terrain.water_depth(x, point.z) > 0.05: return
	var middle := _point(x + 2.5, -24.0)
	if _reserved_for_stop(middle, stops, 3.0) or terrain.is_offroad_trail(middle.x, middle.z): return
	if terrain.fork_at(x).is_empty() and absf(terrain.road_distance(point.x, point.z)) > 9.0:
		_place_model(chunk, "ranch_fence", point)


func _publish_terrain_strip(ground: MeshInstance3D, arrays: Array, band: int) -> void:
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, Mesh.ARRAY_FLAG_USE_DYNAMIC_UPDATE | DriveTerrainBake.MATERIAL_FORMAT)
	if band == 0:
		ground.mesh = mesh
		return
	var strip := MeshInstance3D.new()
	strip.name = "TerrainStrip%d" % band
	strip.mesh = mesh
	strip.material_override = ground.material_override
	strip.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	ground.add_child(strip)


func _schedule(job: Callable, owner_node: Node = null, label: String = "") -> void:
	if async_streaming: _jobs.append({"job": job, "owner": weakref(owner_node) if owner_node != null else null, "epoch": _epoch, "label": label})
	else: job.call()


func set_route_origin(value: Vector3) -> void:
	route_origin = value
	for chunk: Node3D in chunks.values():
		var ground := chunk.get_node_or_null("SandAndTracks") as MeshInstance3D
		if ground != null: (ground.material_override as ShaderMaterial).set_shader_parameter("route_origin", Vector2(value.x, value.z))


func _create_scenery_prop(chunk: Node3D, start: float, count: int, random_seed: int, nearby_stops: Array[Dictionary]) -> void:
	if not is_instance_valid(chunk): return
	var rng := RandomNumberGenerator.new()
	rng.seed = random_seed
	var x := start + rng.randf_range(0.0, CHUNK_LENGTH)
	var side := -1.0 if count % 2 == 0 else 1.0
	var offset := rng.randf_range(9.0, terrain.boundary_width(x) - 10.0) * side
	# Hairpins have several road centres at one X. Scenery belongs to the
	# terrain corridor, not to whichever branch happens to be first.
	var pos := Vector3(x, 0, terrain.corridor_center(x) + offset)
	pos.y = rendered_height_at(pos.x, pos.z)
	if absf(terrain.road_distance(x, pos.z)) < 8.5 or terrain.water_depth(x, pos.z) > (0.65 if terrain.biome_at(x) == 5 else 0.12):
		return
	if _reserved_for_stop(pos, nearby_stops, 3.0) or terrain.is_offroad_trail(pos.x, pos.z):
		return
	var scale_value := rng.randf_range(0.65, 1.30)
	var blend := terrain.blend_at(x)
	var local_biome := int(blend.to) if rng.randf() < float(blend.amount) else int(blend.from)
	var yaw := rng.randf_range(-PI, PI)
	if local_biome in [2, 3, 4, 5] and (count % 3 == 0 or (local_biome == 3 and count % 4 != 0) or (local_biome == 5 and count % 4 == 1)):
		_place_spaced_tree(chunk, pos, nearby_stops)
		return
	var support_min := pos.y
	var support_max := pos.y
	for foot: Vector2 in [Vector2(0.28, 0), Vector2(-0.28, 0), Vector2(0, 0.28), Vector2(0, -0.28)]:
		var foot_y := rendered_height_at(pos.x + foot.x * scale_value, pos.z + foot.y * scale_value)
		support_min = minf(support_min, foot_y)
		support_max = maxf(support_max, foot_y)
	# Leave sheer banks bare and bury roots slightly on gentler slopes.
	if support_max - support_min > 0.55: return
	pos.y = support_min - 0.045
	var contacts: PackedVector3Array = chunk.get_meta("vegetation_contacts", PackedVector3Array())
	contacts.append(pos)
	chunk.set_meta("vegetation_contacts", contacts)
	if local_biome == 5:
		var reeds := BiomeModels.reeds(chunk, pos, scale_value, yaw)
		if batch_scenery: _batch_visuals(reeds, chunk)
	elif local_biome in [2, 3, 4]:
		var plant := BiomeModels.undergrowth(chunk, pos, scale_value, yaw, local_biome == 3)
		if batch_scenery: _batch_visuals(plant, chunk)
	elif local_biome < 2 and count % 4 == 0: _place_model(chunk, "saguaro", pos, Vector3.ONE * scale_value * 0.8, yaw)
	elif count % 7 == 0: _place_model(chunk, "mesa_rock", pos, Vector3(1.8, 0.7, 1.2) * scale_value, x)
	else: _place_model(chunk, "desert_scrub", pos, Vector3.ONE * scale_value, x)

func _place_spaced_tree(chunk: Node3D, candidate: Vector3, stops: Array[Dictionary]) -> void:
	# The same world-space lattice spans adjoining chunks. Jitter is bounded,
	# so crowns remain separated even across a seam or after regeneration.
	var cell := Vector2i(floori(candidate.x / TREE_SPACING), floori(candidate.z / TREE_SPACING))
	var occupied: Dictionary = chunk.get_meta("tree_cells", {})
	if occupied.has(cell): return
	var rng := RandomNumberGenerator.new()
	rng.seed = world_seed + cell.x * 73856093 + cell.y * 19349663
	var pos := Vector3((cell.x + 0.5) * TREE_SPACING + rng.randf_range(-0.8, 0.8), 0, (cell.y + 0.5) * TREE_SPACING + rng.randf_range(-0.8, 0.8))
	var start := float(chunk.get_meta("chunk_start", 0.0))
	if pos.x < start or pos.x >= start + CHUNK_LENGTH: return
	if absf(pos.z - terrain.corridor_center(pos.x)) > terrain.boundary_width(pos.x) - 7.0: return
	if absf(terrain.road_distance(pos.x, pos.z)) < 12.0 or terrain.is_offroad_trail(pos.x, pos.z): return
	if _reserved_for_stop(pos, stops, 5.0): return
	var blend := terrain.blend_at(pos.x)
	var biome := int(blend.to) if rng.randf() < float(blend.amount) else int(blend.from)
	if biome not in [2, 3, 4, 5]: return
	if terrain.water_depth(pos.x, pos.z) > (0.65 if biome == 5 else 0.12): return
	var minimum := rendered_height_at(pos.x, pos.z)
	var maximum := minimum
	for foot: Vector2 in [Vector2(0.4, 0), Vector2(-0.4, 0), Vector2(0, 0.4), Vector2(0, -0.4)]:
		var height := rendered_height_at(pos.x + foot.x, pos.z + foot.y)
		minimum = minf(minimum, height)
		maximum = maxf(maximum, height)
	if maximum - minimum > 0.55: return
	pos.y = minimum - 0.045
	var size_value := rng.randf_range(0.75, 1.10)
	var variant := rng.randi_range(0, 2)
	# The supplied cedar is a sparse landmark, not a replacement for every tree.
	if biome in [2, 3, 4] and rng.randf() < 0.08: variant = 4
	if biome == 5 and rng.randf() < 0.08: variant = 3
	var snow := lerpf(1.0 if int(blend.from) == 4 else 0.0, 1.0 if int(blend.to) == 4 else 0.0, float(blend.amount))
	BiomeModels.tree(chunk, pos, size_value, biome == 4, biome == 5, snow, variant)
	var tree := chunk.get_child(chunk.get_child_count() - 1) as Node3D
	_add_anchor(tree, "tree", Vector3.UP * 0.85)
	occupied[cell] = true
	chunk.set_meta("tree_cells", occupied)
	var sites: Array = chunk.get_meta("tree_sites", [])
	sites.append({"position": pos, "radius": float(tree.get_meta("canopy_radius", 2.7)) * size_value, "variant": variant})
	chunk.set_meta("tree_sites", sites)
	var contacts: PackedVector3Array = chunk.get_meta("vegetation_contacts", PackedVector3Array())
	contacts.append(pos)
	chunk.set_meta("vegetation_contacts", contacts)
	if batch_scenery: _batch_visuals(tree, chunk)


func _finish_chunk_collision(chunk: Node3D, index: int) -> void:
	chunk.set_meta("terrain_ready", true)
	if _retiring_chunks.has(index) and _retiring_chunks[index] != chunk:
		var old: Node3D = _retiring_chunks[index]
		remove_child(old)
		old.queue_free()
		_retiring_chunks.erase(index)
	chunk.visible = true


func _reserved_for_stop(pos: Vector3, stops: Array[Dictionary], margin: float = 0.0) -> bool:
	for site: Dictionary in terrain.shelter_sites(floori(pos.x / DriveTerrain.REGION)):
		if absf(pos.x - float(site.x)) < 11.0 + margin:
			var side := signf(float(site.offset))
			var lateral := (pos.z - center_at(pos.x)) * side
			if lateral > 5.0 - margin and lateral < 23.0 + margin: return true
	if hazards_enabled:
		var feature := terrain.feature_at(pos.x)
		if not feature.is_empty():
			if feature.kind == "tunnel" and absf(pos.x - float(feature.x)) < 40 + margin: return true
			if feature.kind == "rail" and absf(pos.x - float(feature.x)) < 5 + margin: return true
		var source := supply_source(maxi(0, floori(pos.x / 960)))
		var source_z := center_at(float(source.x)) + float(source.offset)
		if absf(pos.x - float(source.x)) < 5.0 + margin and absf(pos.z - source_z) < 4.0 + margin: return true
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


func _create_road_markers(chunk: Node3D, x: float, stops: Array[Dictionary]) -> void:
	if not terrain.feature_at(x).is_empty(): return
	var centers := terrain.road_centers(x)
	var fork := terrain.fork_at(x)
	if not fork.is_empty() and float(fork.spread) < 9: centers = PackedFloat32Array([terrain.corridor_center(x)])
	for center: float in centers:
		var frame := terrain.road_frame(x, center)
		var right := Vector3(-float(frame.slope), 0, 1).normalized()
		for side: float in [-1, 1]:
			var p := Vector3(x, 0, center) + right * (side * 7.2)
			p.y = height_at(p.x, p.z)
			if not _reserved_for_stop(p, stops, 0.5) and absf(terrain.road_distance(p.x, p.z)) > 6.5: _place_model(chunk, "road_delineator", p, Vector3.ONE, -atan(float(frame.slope)))


func supply_source(region: int) -> Dictionary:
	if not _encounter_cache.has(region):
		if _encounter_cache.size() > 128: _encounter_cache.clear()
		var rng := RandomNumberGenerator.new()
		rng.seed = world_seed + region * 104729
		var wreck := rng.randi_range(0, 2) == 0
		_encounter_cache[region] = {"x": region * 960.0 + rng.randf_range(245, 310), "offset": (-1 if rng.randf() < 0.5 else 1) * 12.5,
			"id": (-3000000 if wreck else -2000000) - region, "wreck": wreck}
		if region >= 3 and posmod(region - 3, 5) == 0:
			var path := terrain.mountain_path(region)
			var peak := path[0]
			for point: Vector3 in path:
				if point.y > peak.y: peak = point
			_encounter_cache[region] = {"x": peak.x, "offset": peak.z - center_at(peak.x) + 10.0, "id": -2000000 - region, "wreck": false, "camp": true}
	return _encounter_cache[region]


func _create_supply_source(chunk: Node3D, source: Dictionary) -> void:
	var body := StaticBody3D.new()
	body.name = "AbandonedWreck" if source.wreck else "SupplyCache"
	body.position = _point(float(source.x), float(source.offset))
	body.set_meta("loot_id", int(source.id))
	body.set_meta("loot_title", "Hrebeňový tábor" if source.get("camp", false) else ("Opustený vrak" if source.wreck else "Zásobovacia debna"))
	body.add_to_group("scavenge_debris")
	chunk.add_child(body)
	if source.get("camp", false):
		var stone := BiomeModels.material(Color("655e51"))
		for i in 9:
			var angle := i * TAU / 9.0
			var p := body.position + Vector3(2.6 + cos(angle) * 0.7, 0.1, sin(angle) * 0.7)
			p.y = height_at(p.x, p.z) + 0.1
			DesertModels.box(chunk, "CampFireRing", p, Vector3(0.28, 0.18, 0.22), stone)
		var bench := body.position + Vector3(2.5, 0, 2.3)
		bench.y = height_at(bench.x, bench.z) + 0.4
		DesertModels.box(chunk, "LookoutBench", bench, Vector3(2.2, 0.20, 0.55), DesertModels.mat("wood"))
	var material := BiomeModels.material(Color("79634e"))
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	shape.shape = box
	if source.wreck:
		DesertModels.box(body, "RustBody", Vector3(0, 0.6, 0), Vector3(3.7, 0.6, 1.8), material)
		DesertModels.box(body, "RustCabin", Vector3(-0.3, 1.15, 0), Vector3(1.7, 0.65, 1.6), material)
		for axle: float in [-1.2, 1.2]:
			for side: float in [-0.9, 0.9]:
				var wheel := DesertModels.cylinder(body, "FlatTyre", Vector3(axle, 0.3, side), 0.31, 0.20, DesertModels.mat("rubber"), 8)
				wheel.rotation.x = PI * 0.5
		box.size = Vector3(3.7, 1.4, 1.9)
		shape.position.y = 0.7
	else:
		DesertModels.box(body, "SupplyBox", Vector3(0, 0.55, 0), Vector3(1.3, 1.1, 1.1), DesertModels.mat("wood"))
		box.size = Vector3(1.3, 1.1, 1.1)
		shape.position.y = 0.55
	body.add_child(shape)


func _create_stop(chunk: Node3D, metadata: Dictionary, baked_access: Dictionary = {}) -> void:
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
	if baked_access.is_empty():
		_create_access_patch(chunk, stop, 6.8, absf(offset) - 3.0, 4.5, "SideDriveway")
		_create_access_patch(chunk, stop, absf(offset) - 8.0, absf(offset) + 3.7, 11.5, "TurningYard")
	else:
		for label: String in baked_access:
			_schedule(_create_access_mesh.bind(chunk, baked_access[label], label), chunk, "access_mesh")
	var sign_pos := _point(x - 14.0, signf(offset) * 8.4)
	StopModels.roadside_sign(chunk, sign_pos, str(stop.title), offset > 0.0)


func _create_access_patch(chunk: Node3D, stop: Dictionary, inner: float, outer: float, half_length: float, label: String) -> void:
	_create_access_mesh(chunk, DriveTerrainBake.access_patch(terrain, stop, inner, outer, half_length), label)


func _create_access_mesh(chunk: Node3D, vertices: PackedVector3Array, label: String) -> void:
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	var normals := PackedVector3Array()
	normals.resize(vertices.size())
	normals.fill(Vector3.UP)
	arrays[Mesh.ARRAY_NORMAL] = normals
	var patch := MeshInstance3D.new()
	patch.name = label
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	patch.mesh = mesh
	patch.material_override = StopModels.material(Color("a79c80"))
	patch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	chunk.add_child(patch)
	preload("res://scripts/world_materials.gd").decorate(patch)


func _create_boundaries(chunk: Node3D, start: float, segment: int, only_side: float = 0.0) -> void:
	var body := StaticBody3D.new()
	body.name = "MapBoundary"
	chunk.add_child(body)
	for side: float in ([-1.0, 1.0] if only_side == 0.0 else [only_side]):
		for index in range(segment, segment + 1):
			var x := start + index * 8.0
			var next_x := x + 8.0
			var feature := terrain.feature_at(x + 4.0)
			# Rail portals replace this part of the cliff, including its collider.
			if hazards_enabled and not feature.is_empty() and feature.kind == "rail" and absf(x + 4.0 - float(feature.x)) < 8.0: continue
			var a := _boundary_point(x, side, -2.0)
			var b := _boundary_point(next_x, side, -2.0)
			var shape := CollisionShape3D.new()
			var box := BoxShape3D.new()
			box.size = Vector3(Vector2(b.x - a.x, b.z - a.z).length() + 2.0, 60.0, 8.0)
			shape.shape = box
			shape.position = (a + b) * 0.5 + Vector3.UP * 20.0
			shape.rotation.y = -atan2(b.z - a.z, b.x - a.x)
			body.add_child(shape)
			var biome := terrain.biome_at(x + 4.0)
			if biome == 1:
				preload("res://scripts/canyon_scenery.gd").wall(self, chunk, x, side)
				continue
			if biome in [3, 5]:
				# Forest edges use vegetation rather than a belt of desert blocks.
				if segment % 3 == 0:
					_place_spaced_tree(chunk, _boundary_point(x + 4.0, side, -8.0), [])
				continue
			if biome in [2, 4] or not terrain.mountain_at(x + 4).is_empty():
				var ridge := preload("res://scripts/expedition_scenery.gd").instance("alpine_peak" if biome == 4 else "mountain_ridge")
				ridge.position = _boundary_point(x + 4, side) - Vector3.UP * 1.0
				# Mirror only the outward axis; mass never crosses the road-facing
				# edge. Existing solid boundary is authoritative for collisions.
				ridge.scale = Vector3(0.82, 0.85 + 0.12 * sin(x * 0.03), side)
				ridge.name = "UpperMountainRidge" if side < 0 else "LowerMountainRidge"
				chunk.add_child(ridge, true)
				_add_anchor(ridge, "rock", Vector3(0, 1.0, 0.5))
				if batch_scenery: _batch_visuals(ridge, chunk)
				if segment % 4 == 0:
					var peak := preload("res://scripts/expedition_scenery.gd").instance("snowy_peak" if biome == 4 else "rocky_peak")
					peak.position = _boundary_point(x + 4, side) - Vector3.UP * 2.0
					peak.scale = Vector3(0.85, 0.8 + 0.15 * sin(x * 0.017), side * 0.8)
					chunk.add_child(peak, true)
					if batch_scenery: _batch_visuals(peak, chunk)
				continue
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
	preload("res://scripts/world_materials.gd").apply(node)
	if key in ["mesa_rock", "utility_pole"]:
		# Attach at the reachable face of the rock, never its buried centre.
		var anchor_point := Vector3.UP * (0.65 / maxf(dimensions.y, 0.1))
		if key == "mesa_rock":
			var side := signf(center_at(pos.x) - pos.z)
			var up := 1.8 if dimensions.y > 2.0 else 0.65
			anchor_point = node.to_local(to_global(pos + Vector3(0, up, side * dimensions.z * 0.82)))
		_add_anchor(node, "rock" if key == "mesa_rock" else "pole", anchor_point)
	# Static props share geometry/materials in their authored scenes. Batch rocks,
	# fences and poles too; the old cliff belt alone submitted thousands of draws.
	if batch_scenery: _batch_visuals(node, parent)
	return node


func rendered_height_at(x: float, z: float) -> float:
	var index := floori(x / CHUNK_LENGTH)
	if chunks.has(index) and chunks[index].has_meta("terrain_arrays"):
		return DriveTerrainBake.mesh_height(chunks[index].get_meta("terrain_arrays"), int(chunks[index].get_meta("terrain_width")), x, z)
	return height_at(x, z)


func _add_anchor(parent: Node3D, kind: String, local_point: Vector3) -> void:
	var marker := Marker3D.new()
	marker.name = "WinchAnchor"
	marker.position = local_point
	marker.set_meta("anchor_kind", kind)
	marker.add_to_group("winch_anchor", true)
	parent.add_child(marker)


func _create_shelter(chunk: Node3D, site: Dictionary) -> void:
	var shelter := Node3D.new()
	shelter.name = "RoadsideShelter_%d" % int(site.id)
	shelter.position = Vector3(float(site.x), float(site.level), float(site.z))
	shelter.add_to_group("storm_shelter", true)
	chunk.add_child(shelter)
	var steel := DesertModels.mat("steel")
	var wood := DesertModels.mat("wood")
	DesertModels.box(shelter, "CorrugatedRoof", Vector3(0, 3.7, 0), Vector3(11.0, 0.22, 11.0), steel)
	DesertModels.collider(shelter, Vector3(0, 3.7, 0), Vector3(11.0, 0.22, 11.0))
	var side := signf(float(site.offset))
	# Front opening faces the road; the rear and one side stop driven sand.
	DesertModels.box(shelter, "RearWindScreen", Vector3(0, 1.8, side * 5.0), Vector3(10.4, 3.6, 0.16), wood)
	DesertModels.collider(shelter, Vector3(0, 1.8, side * 5.0), Vector3(10.4, 3.6, 0.16))
	DesertModels.box(shelter, "SideWindScreen", Vector3(5.0, 1.8, side), Vector3(0.16, 3.6, 8.0), wood)
	DesertModels.collider(shelter, Vector3(5.0, 1.8, side), Vector3(0.16, 3.6, 8.0))
	for x: float in [-4.8, 4.8]:
		for z: float in [-4.8, 4.8]:
			DesertModels.box(shelter, "SupportPost", Vector3(x, 1.8, z), Vector3(0.18, 3.6, 0.18), steel)
			DesertModels.collider(shelter, Vector3(x, 1.8, z), Vector3(0.18, 3.6, 0.18))
			var anchor := Marker3D.new()
			anchor.name = "WinchAnchor"
			anchor.position = Vector3(x, 0.8, z)
			anchor.set_meta("anchor_kind", "pole")
			anchor.add_to_group("winch_anchor", true)
			shelter.add_child(anchor)
	preload("res://scripts/world_materials.gd").apply(shelter)
	StopModels.roadside_sign(chunk, _point(float(site.x) - 10.0, side * 7.8), "ÚKRYT", side > 0)


func _batch_visuals(node: Node, chunk: Node3D) -> void:
	for child: Node in node.get_children():
		_batch_visuals(child, chunk)
	if not node is MeshInstance3D: return
	var groups: Dictionary = chunk.get_meta("prop_batches", {})
	var key := str(node.mesh.get_instance_id()) + "/" + str(node.material_override.get_instance_id() if node.material_override != null else 0) + "/" + str(node.cast_shadow)
	if not groups.has(key):
		var batch := MultiMeshInstance3D.new()
		batch.name = "InstancedVegetation"
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = node.mesh
		mm.instance_count = 128
		mm.visible_instance_count = 0
		batch.multimesh = mm
		batch.material_override = node.material_override
		batch.cast_shadow = node.cast_shadow
		chunk.add_child(batch)
		groups[key] = batch
		chunk.set_meta("prop_batches", groups)
	var target: MultiMeshInstance3D = groups[key]
	var count := target.multimesh.visible_instance_count
	if count < target.multimesh.instance_count:
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
	var baked := DriveTerrainBake.bake(terrain, floori(start / CHUNK_LENGTH))
	_create_baked_water(chunk, baked.water, baked.water_depths)


func compact_tyres(points: Array[Vector3], _surface: Dictionary, pressure: float, direction := Vector2.RIGHT, shear := 0.0) -> void:
	if not deformation_enabled: return
	if not points.is_empty(): _soil_focus_x = points[0].x
	if _previous_tyres.size() != points.size(): _previous_tyres = points.duplicate()
	for wheel in points.size():
		var point := points[wheel]
		var start := _previous_tyres[wheel]
		if start.distance_to(point) > 3.0: start = point
		if DriveDeformation.rut_limit(terrain.sample(point.x, point.z)) <= 0.0: continue
		_soil_commands.append({"kind": 0, "start": start, "end": point, "pressure": pressure, "direction": direction, "shear": shear})
	_previous_tyres = points.duplicate()


func restore_deformation(data: Array) -> void:
	# Rebalance old metre-deep ordinary-soil saves without discarding their
	# tracks. Peat can still retain deep channels; solid rock cannot compact.
	terrain.deformation.restore([])
	var field := DriveDeformation.new()
	field.restore(data)
	var adjusted: Array = []
	for entry: Array in field.to_data():
		var sample := terrain.sample(float(entry[0]), float(entry[1]))
		var cap := maxf(DriveDeformation.rut_limit(sample), float(sample.soil_depth))
		if int(sample.material) == DriveTerrain.SurfaceKind.PEAT: cap = float(sample.soil_depth)
		if not bool(sample.deformable): cap = 0.0
		adjusted.append([entry[0], entry[1], clampf(float(entry[2]), -DriveDeformation.MAX_RISE, cap) if cap > 0.0 else 0.0, DriveDeformation.CELL_SIZE])
	terrain.deformation.restore(adjusted)


func load_tyres(points: Array[Vector3], pressure: float, slip: float, effort: float, dt: float) -> void:
	if not deformation_enabled: return
	if not points.is_empty(): _soil_focus_x = points[0].x
	for point in points:
		var sample := terrain.sample(point.x, point.z)
		if not bool(sample.get("deformable", true)) or float(sample.get("soil_softness", 0.0)) <= 0.01: continue
		if slip * effort < 0.01 and pressure <= float(sample.get("soil_bearing", 2.0)): continue
		_soil_commands.append({"kind": 1, "point": point, "pressure": pressure, "slip": slip, "effort": effort, "dt": dt})


func _update_rut_band(chunk: Node3D, band: int) -> void:
	flush_soil_updates()
	if not chunk.has_meta("terrain_arrays"): return
	var arrays: Array = chunk.get_meta("terrain_arrays")
	var width := int(chunk.get_meta("terrain_width"))
	var result := TerrainDetail.build(terrain.deformation, arrays, chunk.get_meta("rest_heights"), width, band)
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
	var target := ground if int(result.band) == 0 else ground.get_node("TerrainStrip%d" % int(result.band)) as MeshInstance3D
	var mesh_started := Time.get_ticks_usec()
	var detail_mesh := ArrayMesh.new()
	detail_mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, result.arrays, [], {}, DriveTerrainBake.MATERIAL_FORMAT)
	target.mesh = detail_mesh
	last_rut_mesh_ms = (Time.get_ticks_usec() - mesh_started) / 1000.0
	last_detail_worker_ms = float(result.worker_ms)
	last_refined_quads = int(result.refined_quads)
	var body := ground.get_child(0) as StaticBody3D
	var shape := body.get_node("GroundBand%d" % int(result.band)) as CollisionShape3D
	var collision_started := Time.get_ticks_usec()
	(shape.shape as ConcavePolygonShape3D).set_faces(result.faces)
	last_rut_collision_ms = (Time.get_ticks_usec() - collision_started) / 1000.0


func _create_baked_water(chunk: Node3D, faces: PackedVector3Array, depths := PackedFloat32Array()) -> void:
	if faces.is_empty(): return
	# Worker output is already triangulated. Bulk-upload it instead of issuing
	# thousands of interpreted SurfaceTool calls in a single render frame.
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = faces
	var normals := PackedVector3Array()
	normals.resize(faces.size())
	normals.fill(Vector3.UP)
	arrays[Mesh.ARRAY_NORMAL] = normals
	var uv := PackedVector2Array()
	uv.resize(faces.size())
	for i in faces.size(): uv[i] = Vector2(depths[i] if i < depths.size() else terrain.water_depth(faces[i].x, faces[i].z), 0)
	arrays[Mesh.ARRAY_TEX_UV] = uv
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	var water := MeshInstance3D.new()
	water.name = "DriveableWater"
	water.mesh = mesh
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
	preload("res://scripts/world_materials.gd").decorate(deck)
	deck.create_trimesh_collision()
