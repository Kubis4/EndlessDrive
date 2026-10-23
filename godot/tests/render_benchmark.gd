extends SceneTree
## Real-GPU moving-camera/streaming smoke test; never opens a player's save.

func _initialize() -> void: call_deferred("_run")

func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.world.async_streaming = true
	game.run.engine_running = true
	game.run.fuel = 40
	game.run.parts.engine = 1
	game.run.parts.tires = 1
	game.hud._message_time = 0
	var samples: Array[float] = []
	var draw_calls := 0
	var last := Time.get_ticks_usec()
	for i in 900:
		var car: DriveVehicle = game.vehicle
		var lookahead := 5.0 + absf(car.speed) * 0.15
		var target_x := car.position.x + lookahead
		var target_z: float = game.world.terrain.road_centers(target_x)[0]
		var error := wrapf(atan2(target_z - car.position.z, lookahead) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 5, 0, 1))
		Input.action_press("left", clampf(-error * 5, 0, 1))
		Input.action_press("throttle", 1.0 if car.speed < 12 else 0.12)
		await process_frame
		var now := Time.get_ticks_usec()
		if i > 120:
			samples.append((now - last) / 1000.0)
			draw_calls = maxi(draw_calls, int(Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME)))
		last = now
	samples.sort()
	print("GPU FRAME ms median=%.2f p95=%.2f p99=%.2f max=%.2f; max draws=%d; distance=%.1f; worker=%.2f ms; stream commit=%.2f ms; rut commit=%.2f ms" % [samples[samples.size() / 2], samples[int(samples.size() * 0.95)], samples[int(samples.size() * 0.99)], samples[-1], draw_calls, game.vehicle.position.x, game.world.last_worker_ms, game.world.max_commit_ms, game.world.max_deformation_ms])
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/godot-rework-driving.png"))
	for action: String in ["right", "left", "throttle"]: Input.action_release(action)
	game.queue_free()
	await process_frame
	quit()
