extends SceneTree
## Native render capture and driving validation, isolated from the existing game/save.
var failures: Array[String] = []

func _initialize() -> void: call_deferred("run")

func run() -> void:
	root.size = Vector2i(1920, 1080)
	var packed := load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene
	if packed == null:
		push_error("Battlefield scene missing; run build_scene.gd first.")
		quit(1)
		return
	var game := packed.instantiate()
	game.patrol_enabled = true
	root.add_child(game)
	var vehicles := game.get_node("Patrol").get_children()
	var starting: Array[Vector3] = []
	for car in vehicles:
		starting.append(car.position)
		var model_id: String = car.get_node("Body").get_meta("endless_drive_model", "")
		if model_id not in ["jeep", "izh"]: failures.append("Endless Drive vehicle model missing")
		var wheels := car.get_node("Body/Wheels").get_children()
		if wheels.size() != 4: failures.append("Vehicle must have four wheel pivots")
		for wheel in wheels:
			if absf(absf(wheel.position.x) - car.half_track) > 0.001 or absf(absf(wheel.position.z) - car.half_wheelbase) > 0.001:
				failures.append("Tyre contact does not match imported wheel pivot")
			if not wheel.has_node("Spin"): failures.append("Imported wheel animation pivot missing")
		if absf(car.trail.half_track - car.half_track) > 0.001: failures.append("Tread width does not match imported model")
	var elapsed := 0.0
	var frames: Array[float] = []
	var cpu_frames: Array[float] = []
	var physics_frames: Array[float] = []
	var maximum_primitives := 0
	var capture := DisplayServer.get_name() != "headless"
	var duration := 10.0
	var previous_tick := Time.get_ticks_usec()
	var progress_positions: Array[Vector3] = starting.duplicate()
	var progress_sampled := false
	if "--detail" in OS.get_cmdline_user_args(): game.camera_size = 23.0
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--duration="): duration = arg.trim_prefix("--duration=").to_float()
	while elapsed < duration:
		await process_frame
		var tick := Time.get_ticks_usec()
		var frame_ms := float(tick - previous_tick) / 1000.0
		previous_tick = tick
		var dt := float(root.get_process_delta_time())
		elapsed += dt
		if not progress_sampled and elapsed >= maxf(0.0, duration - 15.0):
			progress_sampled = true
			for i in vehicles.size(): progress_positions[i] = vehicles[i].position
		if elapsed > 3.0:
			frames.append(frame_ms)
			cpu_frames.append(Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0)
			physics_frames.append(Performance.get_monitor(Performance.TIME_PHYSICS_PROCESS) * 1000.0)
			maximum_primitives = maxi(maximum_primitives, int(Performance.get_monitor(Performance.RENDER_TOTAL_PRIMITIVES_IN_FRAME)))
	for i in vehicles.size():
		var car: CharacterBody3D = vehicles[i]
		var distance := car.position.distance_to(starting[i])
		print("VEHICLE ", car.name, " displacement=", snappedf(distance, 0.01), " speed=", snappedf(car.velocity.length(), 0.01), " track_samples=", car.samples.size())
		if car.get_slide_collision_count() > 0: print("COLLISION ", car.get_last_slide_collision().get_collider().get_path())
		if car.distance_travelled < duration * 2.0: failures.append("Vehicle failed to travel: " + str(car.name))
		# Braking/reversing can legitimately be slow at the final instant. Check
		# recent spatial progress, which still rejects a patrol trapped at a rock.
		if car.position.distance_to(progress_positions[i]) < 8.0: failures.append("Patrol made no recent progress: " + str(car.name) + " at " + str(car.position))
		if not car.position.is_finite(): failures.append("Non-finite vehicle position")
		if car.samples.size() > 650: failures.append("Unbounded track history")
		if car.trail.chunks.size() > 24: failures.append("Unbounded tread mesh cache")
		if car.hull_clearance() < -0.01: failures.append("Chassis penetrated the sand")
		if not car.velocity.is_finite(): failures.append("Non-finite vehicle velocity")
		print("HANDLING ", car.name, " mass_kg=", car.mass_kg, " distance_m=", car.distance_travelled, " flight_s=", car.longest_flight, " landings=", car.landing_count, " recoveries=", car.recovery_count, " up=", car.attitude.y, " hull_contact=", car.chassis_grounded, " wheels=", car.wheel_gaps, " obstacle=", car.last_obstacle, " static=", car.last_static_obstacle, " heading=", car.heading, " phase=", car.route_phase)
	if game.soil.changed_pixels < 100: failures.append("Wheels did not deform the sand")
	if game.soil.tiles.is_empty(): failures.append("Fine deformation geometry missing")
	if game.soil.tiles.size() > 256: failures.append("Unbounded fine terrain geometry")
	print("SOIL tiles=", game.soil.tiles.size(), " edits=", game.soil.changed_pixels, " revision=", game.soil.revision)
	if capture:
		if "--detail" in OS.get_cmdline_user_args():
			game.set_process(false)
			var focus: Vector3 = vehicles[0].position
			var camera: Camera3D = game.get_node("Camera3D")
			camera.position = focus + Vector3(10, 27, 17)
			camera.look_at(focus)
			camera.size = 23
			for i in 3: await process_frame
		await RenderingServer.frame_post_draw
		var output := ProjectSettings.globalize_path("res://../build/desert-battlefield-detail.png" if "--detail" in OS.get_cmdline_user_args() else "res://../build/desert-battlefield-1080p.png")
		for argument in OS.get_cmdline_user_args():
			if argument.begins_with("--output="): output = ProjectSettings.globalize_path(argument.trim_prefix("--output="))
		var error := root.get_texture().get_image().save_png(output)
		if error != OK: failures.append("Capture failed: " + error_string(error))
		print("CAPTURE ", output)
	frames.sort()
	cpu_frames.sort()
	physics_frames.sort()
	if not frames.is_empty(): print("FRAME ms median=", frames[frames.size() / 2], " p95=", frames[int(frames.size() * 0.95)], " p99=", frames[int(frames.size() * 0.99)], " max=", frames.back(), " samples=", frames.size())
	if not cpu_frames.is_empty(): print("CPU process median=", cpu_frames[cpu_frames.size() / 2], " physics median=", physics_frames[physics_frames.size() / 2], " max primitives=", maximum_primitives)
	print("RESOLUTION ", root.size, " scale=", root.scaling_3d_scale, " MSAA=", root.msaa_3d, " QUALITY ", game.quality_name)
	print("RENDERER ", RenderingServer.get_current_rendering_method())
	# Exercise manual controls from an open patch, independently of the patrol's
	# final braking/recovery phase. Patrol progress is checked above.
	vehicles[0].place_on_ground(Vector3(0, 0, 80), 0.0)
	vehicles[0].velocity = -vehicles[0].body.basis.z * 6.0
	var key := InputEventKey.new()
	key.keycode = KEY_SPACE
	key.pressed = true
	game._unhandled_key_input(key)
	if not game.driving or not vehicles[0].manual: failures.append("Manual control toggle failed")
	var turn := InputEventKey.new()
	turn.keycode = KEY_A
	turn.physical_keycode = KEY_A
	turn.pressed = true
	Input.parse_input_event(turn)
	var brake := InputEventKey.new()
	brake.keycode = KEY_SHIFT
	brake.physical_keycode = KEY_SHIFT
	brake.pressed = true
	Input.parse_input_event(brake)
	var old_heading: float = vehicles[0].heading
	var throttle := InputEventKey.new()
	throttle.keycode = KEY_W
	throttle.physical_keycode = KEY_W
	throttle.pressed = true
	Input.parse_input_event(throttle)
	Input.flush_buffered_events()
	for i in 45: await physics_frame
	if absf(vehicles[0].heading - old_heading) < 0.05: failures.append("Player steering did not turn the vehicle")
	if not game.driving_card.visible or not game.driving_readout.text.contains("km/h") or not game.driving_readout.text.contains("kg"):
		failures.append("Manual driving readout is missing speed or mass")
	turn.pressed = false
	brake.pressed = false
	throttle.pressed = false
	Input.parse_input_event(turn)
	Input.parse_input_event(brake)
	Input.parse_input_event(throttle)
	key.keycode = KEY_ESCAPE
	game._unhandled_key_input(key)
	if game.driving or vehicles[0].manual: failures.append("Cinematic return failed")
	print("BATTLEFIELD CHECKS ", "PASS" if failures.is_empty() else failures)
	quit(0 if failures.is_empty() else 1)
