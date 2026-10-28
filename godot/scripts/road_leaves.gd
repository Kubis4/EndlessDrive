extends Node3D
## One local leaf emitter, independent of the number of loaded forest chunks.
var world: DriveWorld
var vehicle: DriveVehicle
var _context_timer := 0.0
var foliage_context := false
@onready var particles: CPUParticles3D = $AirborneLeaves

func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	if Engine.is_editor_hint(): set_process(false)

func _process(dt: float) -> void:
	if world == null or vehicle == null: return
	_context_timer -= dt
	if _context_timer <= 0:
		_context_timer = 0.25
		refresh_context()
		if absf(vehicle.speed) > 1.5 and foliage_context: scatter_ground_leaves()
	position = vehicle.position + Vector3.UP * 0.85
	var moving := absf(vehicle.speed) > 1.5
	var wind := clampf(absf(world.wind_force), 0, 2.5)
	particles.emitting = foliage_context and (moving or wind > 0.25)
	particles.direction = Vector3(cos(vehicle.heading) * -0.35 + 0.35, 0.8, sin(vehicle.heading) * -0.35 + 0.2).normalized()
	particles.initial_velocity_max = 1.1 + wind * 0.4 + minf(absf(vehicle.speed), 18) * 0.04

func refresh_context() -> void:
	foliage_context = false
	if world == null or vehicle == null: return
	var point := vehicle.position
	var biome := world.terrain.biome_at(point.x)
	if biome not in [2, 3, 5]: return
	if absf(world.terrain.road_distance(point.x, point.z)) > 19: return
	var feature := world.terrain.feature_at(point.x)
	if feature.get("kind", "") == "tunnel" and absf(point.x - float(feature.x)) < 45: return
	var center := floori(point.x / DriveWorld.CHUNK_LENGTH)
	for index in range(center - 1, center + 2):
		if not world.chunks.has(index): continue
		for tree: Dictionary in world.chunks[index].get_meta("tree_sites", []):
			var tree_point: Vector3 = tree.position
			if Vector2(tree_point.x - point.x, tree_point.z - point.z).length_squared() < 32 * 32:
				foliage_context = true
				return

func reset_particles() -> void:
	foliage_context = false
	_context_timer = 0
	particles.emitting = false
	particles.restart()
	particles.emitting = false

func scatter_ground_leaves() -> void:
	# Remove the few flat leaves under the car while the local emitter carries
	# their visual wake. No rigid bodies, extra emitters or permanent save data.
	var center := floori(vehicle.position.x / DriveWorld.CHUNK_LENGTH)
	var forward := Vector2(cos(vehicle.heading), sin(vehicle.heading))
	var side := Vector2(-forward.y, forward.x)
	for index in range(center - 1, center + 2):
		if not world.chunks.has(index): continue
		var litter: Dictionary = world.chunks[index].get_meta("road_litter", {})
		if litter.is_empty(): continue
		# A streamed/rebuilt chunk can retain metadata for a freed visual. Test
		# the Variant before casting: casting a freed Godot object itself fails.
		var candidate: Variant = litter.get("batch")
		if not is_instance_valid(candidate): continue
		var batch := candidate as MultiMeshInstance3D
		if batch == null: continue
		var contacts: PackedVector3Array = litter.contacts
		var cleared: Dictionary = litter.cleared
		for leaf in contacts.size():
			if cleared.has(leaf): continue
			var delta := Vector2(contacts[leaf].x - vehicle.position.x, contacts[leaf].z - vehicle.position.z)
			if absf(delta.dot(forward)) < 3.1 and absf(delta.dot(side)) < 1.5:
				var pose := batch.multimesh.get_instance_transform(leaf)
				pose.basis = Basis.from_scale(Vector3.ONE * 0.001)
				batch.multimesh.set_instance_transform(leaf, pose)
				cleared[leaf] = true
