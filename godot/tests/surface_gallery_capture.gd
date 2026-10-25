extends SceneTree
## Six real-GPU surface previews. Never reads or writes a player journey.
func _initialize() -> void: call_deferred("_capture")
func _capture() -> void:
	if DisplayServer.get_name() == "headless":
		quit(2)
		return
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.hud.hide()
	game.vehicle.controls_enabled = false
	game.overview_size = 43
	game.run.day_time = 0.32
	game.weather.forced_mode = "Jasno"
	var names := ["sand", "canyon", "countryside", "forest", "snow", "wetlands"]
	var failed := false
	for biome in 6:
		game.world.async_streaming = false
		game.world.configure(DriveTerrain.VARIED_SEED_MIN + biome)
		game.run.world_seed = DriveTerrain.VARIED_SEED_MIN + biome
		game.world.update_stream(180)
		game.vehicle.park()
		var z: float = game.world.center_at(180)
		game.vehicle.position = Vector3(180, game.world.height_at(180, z) + 0.6, z)
		game.vehicle.heading = atan((game.world.center_at(181) - game.world.center_at(179)) * 0.5)
		game.vehicle.reset_physics_interpolation()
		for tick in 20:
			await physics_frame
			await process_frame
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		var path := ProjectSettings.globalize_path("res://../build/surface-%s.png" % names[biome])
		var result := root.get_texture().get_image().save_png(path)
		failed = failed or result != OK
		print("SURFACE CAPTURE %s %s" % [names[biome], error_string(result)])
	game.queue_free()
	await process_frame
	await process_frame
	quit(1 if failed else 0)
