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
	if not game.has_node("AbandonedRelayStation/EndlessDriveWorkshop/RepairBarrack"):
		failures.append("Endless Drive workshop missing")
	var elapsed := 0.0
	var frames: Array[float] = []
	var capture := DisplayServer.get_name() != "headless"
	var duration := 10.0
	var previous_tick := Time.get_ticks_usec()
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
		if elapsed > 3.0: frames.append(frame_ms)
	for i in vehicles.size():
		var car: CharacterBody3D = vehicles[i]
		var distance := car.position.distance_to(starting[i])
		print("VEHICLE ", car.name, " displacement=", snappedf(distance, 0.01), " speed=", snappedf(car.velocity.length(), 0.01), " track_samples=", car.samples.size())
		if car.get_slide_collision_count() > 0: print("COLLISION ", car.get_last_slide_collision().get_collider().get_path())
		if distance < 8: failures.append("Vehicle failed to travel: " + str(car.name))
		if car.velocity.length() < 2.0: failures.append("Patrol stuck: " + str(car.name) + " at " + str(car.position))
		if not car.position.is_finite(): failures.append("Non-finite vehicle position")
		if car.samples.size() > 650: failures.append("Unbounded track history")
		if car.trail.chunks.size() > 24: failures.append("Unbounded tread mesh cache")
		var expected: float = car.support_height + car.wheel_radius + 0.02
		if absf(car.position.y - expected) > 0.01: failures.append("Vehicle lost ground contact")
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
		var error := root.get_texture().get_image().save_png(output)
		if error != OK: failures.append("Capture failed: " + error_string(error))
		print("CAPTURE ", output)
	frames.sort()
	if not frames.is_empty(): print("FRAME ms median=", frames[frames.size() / 2], " p95=", frames[int(frames.size() * 0.95)], " p99=", frames[int(frames.size() * 0.99)], " max=", frames.back(), " samples=", frames.size())
	print("RENDERER ", RenderingServer.get_current_rendering_method())
	# Exercise the actual no-HUD controls and the reduced-grip handbrake path.
	var key := InputEventKey.new()
	key.keycode = KEY_SPACE
	key.pressed = true
	game._unhandled_key_input(key)
	if not game.driving or not vehicles[0].manual: failures.append("Manual control toggle failed")
	var turn := InputEventKey.new()
	turn.physical_keycode = KEY_A
	turn.pressed = true
	Input.parse_input_event(turn)
	var brake := InputEventKey.new()
	brake.physical_keycode = KEY_SHIFT
	brake.pressed = true
	Input.parse_input_event(brake)
	var old_heading: float = vehicles[0].heading
	var throttle := InputEventKey.new()
	throttle.physical_keycode = KEY_W
	throttle.pressed = true
	Input.parse_input_event(throttle)
	for i in 45: await physics_frame
	if absf(vehicles[0].heading - old_heading) < 0.05: failures.append("Player steering did not turn the vehicle")
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
