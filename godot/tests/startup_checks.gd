extends SceneTree
## Production asynchronous boot and save restoration, isolated from player data.
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(message)
func _wait_ready(game: Node) -> void:
	var deadline := Time.get_ticks_msec() + 45000
	while not game.startup_ready and Time.get_ticks_msec() < deadline: await process_frame
	check(game.startup_ready, "Startup completes within the bounded probe")
func _run() -> void:
	if not OS.get_environment("APPDATA").replace("\\", "/").ends_with("/build/startup-profile"):
		push_error("Use isolated build/startup-profile APPDATA")
		quit(1)
		return
	create_timer(120, true).timeout.connect(func() -> void: quit(1))
	for path: String in [DriveProfile.PATH, DriveProfile.PATH + ".bak"]:
		if FileAccess.file_exists(path): DirAccess.remove_absolute(path)
	for suffix: String in ["", ".bak", ".tmp"]:
		var path := DriveSave.PATH + suffix
		if FileAccess.file_exists(path): DirAccess.remove_absolute(path)
	var boot := (load("res://scenes/boot.tscn") as PackedScene).instantiate()
	root.add_child(boot)
	current_scene = boot
	check(boot.get_child_count() > 0 and root.get_child_count() == 1, "Boot displays without instantiating the driving world")
	while current_scene == boot: await process_frame
	var game := current_scene
	check(not game.startup_ready and game.menu.visible and paused, "Menu appears while nearby terrain is still loading")
	check(game.menu._new_button.disabled and game.menu._continue_button.disabled, "Play buttons cannot enter incomplete terrain")
	var initial_seed: int = game.run.world_seed
	game._menu_action("new")
	check(not game._session_started and game.run.world_seed == initial_seed, "Early play requests are safely ignored")
	await _wait_ready(game)
	check(game.world.chunks.size() == 3 and game.world.startup_complete(), "Only three complete nearby chunks are required at startup")
	check(not root.disable_3d and game.world.publication_budget_usec == 2500, "Normal rendering and the driving streaming budget are restored")
	check(game.world.clock == game.run.elapsed and game.run.elapsed == 0, "Loading does not advance survival or encounter clocks")
	check(not game.menu._new_button.disabled and game.menu._continue_button.disabled, "Fresh journey is enabled only after loading completes")
	check(game.run.vehicle_id == "izh" and not game.run.four_wheel_drive, "A fresh production profile uses the front-driven van")
	game._menu_action("vehicle:jeep")
	check(game.profile.selected_vehicle == "jeep" and game.run.vehicle_id == "izh", "Garage changes the next run without changing the current vehicle")
	var prepared_chunk: Node3D = game.world.chunks[0]
	game._menu_action("new")
	check(game.startup_ready and game.world.chunks[0] == prepared_chunk and game.run.world_seed == initial_seed, "First New journey reuses the prepared seed and avoids a second generation")
	check(game.vehicle.vehicle_id == "jeep" and game.run.four_wheel_drive, "The next real journey installs the selected Jeep")
	check(not paused and game.vehicle.controls_enabled and game.hud.visible, "Prepared journey starts with functional controls")
	game.vehicle.park()
	game._open_menu(false)
	game._menu_action("vehicle:izh")
	game._menu_action("new")
	check(not game.startup_ready and paused and not game.hud.visible, "Later New journey is generated behind the loading menu")
	await _wait_ready(game)
	check(game.run.world_seed != initial_seed and not paused and game.vehicle.controls_enabled, "Later New journey changes the seed and resumes after collision publication")
	# Restore away from the origin, with actual soil history and graphics prefs.
	game._open_menu(false)
	var saved_run := DriveRun.new()
	saved_run.world_seed = 24601
	saved_run.distance = 744
	saved_run.elapsed = 321
	var saved_terrain := DriveTerrain.new()
	saved_terrain.seed_value = saved_run.world_seed
	var pose := Vector3(744, saved_terrain.height_at(744, saved_terrain.center_at(744)) + 0.6, saved_terrain.center_at(744))
	check(DriveSave.store(saved_run, pose, 0.2) == OK, "Isolated saved journey was written")
	var preferences := ConfigFile.new()
	preferences.set_value("view", "quality", 0)
	preferences.save("user://preferences.cfg")
	game._ephemeral = true
	game.queue_free()
	await process_frame
	paused = false
	game = (load("res://scenes/main.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await _wait_ready(game)
	check(game.world.chunks.has(7) and game.world.chunks.has(6) and game.world.chunks.has(8), "Saved location loads its own neighbouring chunks")
	check(game.world.graphics_quality == 0 and game._quality_preference == 0, "Saved graphics quality is applied before generating terrain")
	check(game.run.elapsed == 321 and game.world.clock == 321 and not game.menu._continue_button.disabled, "Saved clock is preserved and Continue is available")
	game._menu_action("continue")
	check(not paused and game.vehicle.controls_enabled and not game._in_menu, "Continue resumes the restored car")
	game._ephemeral = true
	game.queue_free()
	await process_frame
	for path: String in [DriveSave.PATH, DriveSave.PATH + ".bak", "user://preferences.cfg"]:
		if FileAccess.file_exists(path): DirAccess.remove_absolute(path)
	print("STARTUP: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
