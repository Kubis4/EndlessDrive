extends SceneTree
## Real render frames, actual structure nodes and world streaming scheduler.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	world.async_streaming = true
	var camera := Camera3D.new()
	root.add_child(camera)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 120
	var light := DirectionalLight3D.new()
	light.rotation_degrees = Vector3(-50, -30, 0)
	light.shadow_enabled = true
	light.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	light.directional_shadow_max_distance = 120
	root.add_child(light)
	if DisplayServer.get_name() != "headless":
		root.add_child(load("res://scripts/render_warmup.gd").new())
		for i in 72: await process_frame
	for kind: String in ["bridge", "rail", "tunnel"]:
		var hazard := RouteHazard.new()
		hazard.world = world
		hazard.kind = kind
		hazard.feature_x = 744 if kind != "tunnel" else 1704
		world.add_child(hazard)
		var center := Vector3(hazard.feature_x, world.height_at(hazard.feature_x, world.center_at(hazard.feature_x)), world.center_at(hazard.feature_x))
		camera.position = center + Vector3(-50, 65, 60)
		camera.look_at(center)
		for i in 30: await process_frame
		world.max_commit_ms = 0
		var samples: Array[float] = []
		var peak := 0.0
		var peak_frame := 0
		var started := Time.get_ticks_usec()
		if hazard.has_method("build_streamed"): hazard.call("build_streamed")
		else: world._schedule(hazard.build)
		var last := Time.get_ticks_usec()
		for i in 180:
			await process_frame
			var now := Time.get_ticks_usec()
			var frame_ms := (now - last) / 1000.0
			samples.append(frame_ms)
			if frame_ms > peak:
				peak = frame_ms
				peak_frame = i
			last = now
		samples.sort()
		print("STRUCTURE %s commit_max=%.2f ms frame_p99=%.2f ms frame_max=%.2f ms nodes=%d elapsed=%.1f ms" % [kind, world.max_commit_ms, samples[int(samples.size() * 0.99)], samples[-1], hazard.get_child_count(), (Time.get_ticks_usec() - started) / 1000.0])
		print("peak frame=", peak_frame, " max single job=", world.max_job_ms)
		hazard.queue_free()
		await process_frame
	world.queue_free()
	await process_frame
	quit()
