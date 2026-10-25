extends SceneTree
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(title)
func frames(count := 3) -> void:
	for tick in count:
		await physics_frame
		await process_frame
func _run() -> void:
	var sand := {"paved": 0.0, "snow": 0.0, "water": 0.0, "deformable": true}
	var field := DriveDeformation.new()
	for tick in 30: field.compact(Vector3.ZERO, sand, 1, Vector2.RIGHT, 0.5)
	check(field.depth_at(0, 0) > 0.08 and field.depth_at(0, 0.55) < -0.01, "Tyres excavate a channel and displace physical shoulders beside it")
	check(field.depth_at(0, 0) <= 0.28 and field.depth_at(0, 0.55) >= -DriveDeformation.MAX_RISE, "Ordinary sand ruts and banks remain drivable")
	var rotated := DriveDeformation.new()
	for tick in 30: rotated.compact(Vector3.ZERO, sand, 1, Vector2.DOWN, 0.5)
	check(is_equal_approx(field.depth_at(0, 0.5), rotated.depth_at(0.5, 0)), "Deformation footprint follows the wheel heading")
	var restored := DriveDeformation.new()
	restored.restore(field.to_data())
	check(absf(restored.depth_at(0, 1) - field.depth_at(0, 1)) < 0.0001, "Raised soil survives serialization alongside depressions")
	var hard := DriveDeformation.new()
	hard.compact(Vector3.ZERO, {"paved": 1.0}, 1)
	hard.compact(Vector3.ZERO, {"paved": 0.0, "deformable": false}, 1)
	check(hard.cells.is_empty(), "Asphalt and solid rock receive neither ruts nor raised banks")
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.vehicle.set_physics_process(false)
	game.vehicle.position = Vector3(-50, 20, 0)
	await frames()
	var world: DriveWorld = game.world
	var chunk: Node3D = world.chunks[0]
	var width: int = chunk.get_meta("terrain_width")
	var index := 10 * width + width / 2
	var arrays: Array = chunk.get_meta("terrain_arrays")
	var point: Vector3 = arrays[Mesh.ARRAY_VERTEX][index]
	check(is_equal_approx(arrays[Mesh.ARRAY_CUSTOM3][index * 4 + 2], point.y) and arrays[Mesh.ARRAY_CUSTOM3][index * 4 + 3] == 1.0, "Terrain retains undisturbed geometry for soil lighting and exposure")
	var dust: CPUParticles3D = game.effects._emitters[0]
	check(dust.mesh.material.billboard_keep_scale and dust.mesh.material.albedo_texture.resource_path.ends_with("wheel-dust-reference-v2.png"), "Dust billboards preserve particle growth and use the new transparent sprite")
	var contact := point - Vector3(0, 0, 0.55)
	for tick in 50: world.terrain.deformation.compact(contact, sand, 1, Vector2.RIGHT, 0.5)
	world._update_rut_band(chunk, floori(point.x / DriveTerrainBake.BAND_ROWS))
	await frames()
	var query := PhysicsRayQueryParameters3D.create(point + Vector3.UP * 3, point - Vector3.UP * 3)
	query.exclude = [game.vehicle.get_rid()]
	var hit := world.get_world_3d().direct_space_state.intersect_ray(query)
	var raised: Vector3 = (chunk.get_meta("terrain_arrays") as Array)[Mesh.ARRAY_VERTEX][index]
	check(raised.y > point.y + 0.01 and not hit.is_empty() and absf(raised.y - hit.position.y) < 0.005, "Displaced shoulder raises both the rendered vertex and actual collision")
	world.restore_deformation(world.terrain.deformation.to_data())
	check(world.terrain.deformation.depth_at(point.x, point.z) < 0, "World migration preserves signed shoulder heights")
	game.effects.reset_trails()
	game.effects._stamp(point, Vector3.RIGHT, world.terrain.sample(point.x, point.z))
	var before := world.terrain.deformation.depth_at(point.x, point.z)
	for tick in 30: world.terrain.deformation.compact(point, sand, 1)
	game.effects._refresh_tracks()
	check(world.terrain.deformation.depth_at(point.x, point.z) > before + 0.01 and game.effects._track_records.is_empty() and game.effects._tracks.multimesh.instance_count == 0, "Soil deepens without spawning a floating decorative trail")
	game.effects.reset_trails()
	check(game.effects._track_records.is_empty(), "A new journey clears the bounded relief cache")
	game.queue_free()
	await frames()
	print("DISPLACED SOIL: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
