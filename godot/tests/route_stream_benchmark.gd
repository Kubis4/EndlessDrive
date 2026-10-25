extends SceneTree
## Full terrain/prop/stop/hazard streaming at 36 m/s with the actual follow
## camera. Camera traversal is scripted; physical driving has separate tests.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var start := 240.0
	var end := 2775.0
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--from="): start = arg.trim_prefix("--from=").to_float()
		if arg.begins_with("--to="): end = arg.trim_prefix("--to=").to_float()
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.hud.hide()
	game.world.hazards_enabled = true
	game.vehicle.set_physics_process(false)
	game.vehicle.position = Vector3(start, game.world.height_at(start, game.world.center_at(start)) + 0.6, game.world.center_at(start))
	game.weather.forced_mode = "Jasno"
	game.world.update_stream(start)
	game.world.async_streaming = true
	game._update_camera(1, true)
	var warmup := load("res://scripts/render_warmup.gd").new() as SubViewport
	warmup.set("environment_template", game.environment)
	warmup.set("vehicle_template", game.vehicle._visual)
	root.add_child(warmup)
	for i in 72: await process_frame
	game.world.max_commit_ms = 0
	game.world.max_job_ms = 0
	var samples: Array[float] = []
	var features: Array[float] = []
	var phase := clampi(floori((start - 850) / 960) + 1, 0, 2)
	var last := Time.get_ticks_usec()
	for i in ceili((end - start) / 0.6) + 1:
		var x := start + i * 0.6
		var z: float = game.world.center_at(x)
		game.vehicle.position = Vector3(x, game.world.height_at(x, z) + 0.6, z)
		game.vehicle.heading = atan((game.world.center_at(x + 1) - game.world.center_at(x - 1)) * 0.5)
		game.vehicle.reset_physics_interpolation()
		game._update_camera(1, true)
		await physics_frame
		await process_frame
		var now := Time.get_ticks_usec()
		var ms := (now - last) / 1000.0
		last = now
		samples.append(ms)
		if ms > 35:
			print("ROUTE frame %.2f ms at x=%.1f; commit_max=%.2f job_max=%.2f (%s)" % [ms, x, game.world.max_commit_ms, game.world.max_job_ms, game.world.slowest_job])
		if absf(x - (744.0 + phase * 960)) < 100: features.append(ms)
		if x > 850 + phase * 960:
			features.sort()
			print("ROUTE FEATURE %s frame_p99=%.2f max=%.2f; stream_commit=%.2f; job=%.2f ms (%s)" % [["rail", "tunnel", "bridge"][phase], features[int(features.size() * 0.99)], features[-1], game.world.max_commit_ms, game.world.max_job_ms, game.world.slowest_job])
			features.clear()
			phase += 1
			if phase == 3: break
	samples.sort()
	print("ROUTE STREAM %.1f km at 36m/s median=%.2f p99=%.2f max=%.2f ms commit=%.2f worker=%.2f" % [(end - start) / 1000.0, samples[samples.size() / 2], samples[int(samples.size() * 0.99)], samples[-1], game.world.max_commit_ms, game.world.last_worker_ms])
	game.queue_free()
	await process_frame
	await process_frame
	quit()
