extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	root.size = Vector2i(1600, 900)
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game._focus_pause_enabled = false
	game.set_process(false)
	game.weather.set_process(false)
	game.run.day_time = 0.42
	game.run.world_seed = 24601
	game.vehicle.set_physics_process(false)
	game.world.configure(24601)
	game.world.hazards_enabled = true
	for index in range(30, 40): game.world._create_chunk(index)
	var path: PackedVector3Array = game.world.terrain.mountain_path(3)
	game.vehicle.position = path[35] + Vector3.UP * 0.5
	game.vehicle._update_visual(0.016, 0)
	game.hud.hide()
	game._update_lighting()
	var focus := Vector3(3340, 20, game.world.terrain.corridor_center(3340))
	game.camera.size = 350
	game.camera.far = 1400
	game.camera.position = focus + Vector3(-100, 340, 130)
	game.camera.look_at(game.to_global(focus))
	for i in 15: await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/adventure-mountain.png"))
	print("CAPTURE MOUNTAIN OK")
	game.world.configure(24601)
	game.world.update_stream(180)
	game.vehicle.position = Vector3(180, game.world.height_at(180, game.world.center_at(180)) + 0.6, game.world.center_at(180))
	game.run.day_time = 0.42
	game._update_camera(1, true)
	game.weather.start_sandstorm(0)
	game.weather.storm_warning = 0
	game.weather._storm_left = 50
	game.weather.storm_center = Vector2(game.vehicle.position.x, game.vehicle.position.z)
	game.weather.storm_velocity = Vector2.ZERO
	for i in 150:
		game.weather._process(1.0 / 60.0)
		game._update_lighting()
		await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/adventure-storm.png"))
	print("CAPTURE LOCAL STORM OK visibility=", game.weather.storm_visibility)
	game.queue_free()
	await process_frame
	quit()
