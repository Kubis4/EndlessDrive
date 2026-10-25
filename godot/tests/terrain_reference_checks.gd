extends SceneTree
## Real driving/particle/terrain regression with an optional GPU reference capture.
var failures := 0

func _initialize() -> void: call_deferred("_run")

func check(ok: bool, label: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", label])
	if not ok: failures += 1

func frames(count: int) -> void:
	for i in count:
		await physics_frame
		await process_frame

func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var world: DriveWorld = game.world
	var car: DriveVehicle = game.vehicle
	game.hud.hide()
	game.run.day_time = 0.30
	game.weather.forced_mode = "Jasno"
	game.overview_size = 38
	var reference_offroad := "--offroad" in OS.get_cmdline_user_args()
	var offset := 14.0 if reference_offroad else 5.5
	if reference_offroad: game.overview_size = 46
	world.async_streaming = true
	car.park()
	var x := 400.0 if reference_offroad else 65.0
	if reference_offroad: world.update_stream(x)
	var z := world.center_at(x) + offset
	car.position = Vector3(x, world.height_at(x, z) + 0.6, z)
	car.heading = 0.0
	car.reset_physics_interpolation()
	game.run.fuel = 40
	game.run.parts.engine = 1.0
	game.run.parts.tires = 1.0
	game.run.variants.tires = "allterrain"
	game.run.engine_running = true
	await frames(20)
	var initial := car.position
	var emitted := false
	for tick in 540:
		var look := 5.0
		var target_x := car.position.x + look
		var target_z := world.center_at(target_x) + offset + sin((target_x - x) * 0.09) * 1.2
		var error := wrapf(atan2(target_z - car.position.z, look) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 4, 0, 1))
		Input.action_press("left", clampf(-error * 4, 0, 1))
		Input.action_press("throttle", 0.8 if car.speed < 8 else 0.2)
		await frames(1)
		for emitter: CPUParticles3D in game.effects._emitters:
			emitted = emitted or emitter.emitting
	check(car.position.distance_to(initial) > 12, "Car physically drives across sculpted sand")
	check(emitted and game.effects._emitters.size() == 4 and game.effects._grit.size() == 4, "All four contacts own dust and ballistic grit emitters")
	check(game.effects._count == 0 and game.effects._tracks.multimesh.instance_count == 0, "Driving uses physical soil deformation without allocating decorative tyre trails")
	check(not world.terrain.deformation.cells.is_empty(), "Driving stamps real persistent terrain deformation")
	print("REFERENCE DRIVE travel=%.2f marks=%d speed=%.2f rut commit=%.2f ms" % [car.position.distance_to(initial), game.effects._count, car.speed, world.max_deformation_ms])
	if DisplayServer.get_name() != "headless":
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		var output := ProjectSettings.globalize_path("res://../build/offroad-reference.png" if reference_offroad else "res://../build/terrain-reference.png")
		check(root.get_texture().get_image().save_png(output) == OK, "GPU reference capture saved")
	# Airborne and paved contacts must not keep producing dust/trails.
	var count: int = game.effects._count
	car.position.y += 15
	await frames(2)
	game.effects._process(0)
	var airborne_dust := false
	for emitter: CPUParticles3D in game.effects._emitters: airborne_dust = airborne_dust or emitter.emitting
	check(not airborne_dust and game.effects._count == count, "Airborne wheels stop emitting and do not bridge tyre marks")
	var paved_x := 2200.0
	var paved_z := world.center_at(paved_x)
	var before := world.terrain.deformation.depth_at(paved_x, paved_z)
	world.compact_tyres([Vector3(paved_x, world.height_at(paved_x, paved_z), paved_z)], {"paved": 0.0}, 1.0)
	check(float(world.terrain.sample(paved_x, paved_z).paved) > 0.9 and world.terrain.deformation.depth_at(paved_x, paved_z) == before, "Per-wheel asphalt sample blocks compaction even if the car's shared sample is loose")
	for action: String in ["throttle", "right", "left"]: Input.action_release(action)
	game.queue_free()
	await frames(2)
	quit(0 if failures == 0 else 1)
