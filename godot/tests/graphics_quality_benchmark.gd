extends SceneTree
## Controlled, stationary real-renderer comparison. No physics-frame await
## aliases: each sample is one process/render frame of the same tunnel view.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	if not OS.get_cmdline_user_args().has("--test-session"):
		quit(2)
		return
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.effects.set_process(false)
	game.weather.set_process(false)
	game.hud.hide()
	game.world.hazards_enabled = true
	game.world.update_stream(1704)
	var z: float = game.world.center_at(1704)
	game.vehicle.position = Vector3(1704, game.world.height_at(1704, z) + 0.6, z)
	game._update_camera(1, true)
	game._update_lighting()
	game.world.set_process(false)
	for pass_index in 2:
		for quality in [2, 1, 0]:
			game._settings_changed("quality", quality)
			# Warm each actual shader/framebuffer configuration before timing.
			for frame in 90: await process_frame
			var samples: Array[float] = []
			var last := Time.get_ticks_usec()
			var draws := 0
			for frame in 240:
				await process_frame
				var now := Time.get_ticks_usec()
				samples.append((now - last) / 1000.0)
				last = now
				draws = maxi(draws, int(Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME)))
			samples.sort()
			print("QUALITY pass=%d level=%d median=%.2f p95=%.2f p99=%.2f max=%.2f ms draws=%d" % [pass_index + 1, quality, samples[samples.size() / 2], samples[int(samples.size() * 0.95)], samples[int(samples.size() * 0.99)], samples[-1], draws])
	game.queue_free()
	await process_frame
	await process_frame
	quit()
