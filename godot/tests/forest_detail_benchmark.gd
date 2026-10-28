extends SceneTree
## Controlled same-view tree-density comparison; no user save or APK export.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	if not OS.get_cmdline_user_args().has("--test-session"):
		quit(2)
		return
	Engine.max_fps = 0
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.effects.set_process(false)
	game.weather.set_process(false)
	game.road_leaves.set_process(false)
	game.hud.hide()
	game.world.set_process(false)
	game._settings_changed("quality", 1)
	for pass_index in 2:
		for attempts: int in [72, 88]:
			game.world.woodland_scenery_attempts = attempts
			game.world.configure(1000004)
			game.world.update_stream(180)
			game.vehicle.position = Vector3(180, game.world.height_at(180, game.world.center_at(180)) + 0.6, game.world.center_at(180))
			game._update_camera(1, true)
			game._update_lighting()
			for frame in 120: await process_frame
			var samples: Array[float] = []
			var draws := 0
			var last := Time.get_ticks_usec()
			for frame in 160:
				await process_frame
				var now := Time.get_ticks_usec()
				samples.append((now - last) / 1000.0)
				last = now
				draws = maxi(draws, int(Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME)))
			samples.sort()
			var trees := 0
			for chunk: Node3D in game.world.chunks.values(): trees += chunk.get_meta("tree_sites", []).size()
			print("FOREST DENSITY pass=%d attempts=%d trees=%d draws=%d median=%.2f p95=%.2f p99=%.2f max=%.2f ms" % [pass_index + 1, attempts, trees, draws, samples[samples.size() / 2], samples[int(samples.size() * 0.95)], samples[int(samples.size() * 0.99)], samples[-1]])
	game.queue_free()
	await process_frame
	quit()
