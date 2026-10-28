extends SceneTree
func _initialize() -> void: call_deferred("_run")
func save_view(label: String) -> void:
	for frame in 12: await process_frame
	await RenderingServer.frame_post_draw
	var directory := ProjectSettings.globalize_path("res://../build/scenery-refresh")
	DirAccess.make_dir_recursive_absolute(directory)
	print("SCENERY CAPTURE ", label, ": ", error_string(root.get_texture().get_image().save_png(directory + "/" + label + ".png")))
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate()
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.world.set_process(false)
	game.vehicle.set_physics_process(false)
	game.weather.set_process(false)
	game.hud.hide()
	game.world.hazards_enabled = true
	var selected: PackedStringArray = []
	for argument: String in OS.get_cmdline_user_args():
		if argument.begins_with("--views="): selected = argument.trim_prefix("--views=").split(",")
	for entry: Array in [["tunnel", 24601, 1704.0, 85.0], ["alpine", 1000005, 180.0, 40.0], ["grass", 1000003, 180.0, 30.0], ["forest", 1000004, 180.0, 40.0]]:
		if not selected.is_empty() and not str(entry[0]) in selected: continue
		game.world.configure(int(entry[1]))
		game.world.update_stream(float(entry[2]))
		var x := float(entry[2])
		game.vehicle.position = Vector3(x, game.world.height_at(x, game.world.center_at(x)) + 0.6, game.world.center_at(x))
		game.overview_size = float(entry[3])
		game._update_camera(1.0, true)
		game._update_lighting()
		await save_view(str(entry[0]))
	game._session_started = false
	game._open_menu(false)
	await save_view("menu")
	game.queue_free()
	await process_frame
	quit()
