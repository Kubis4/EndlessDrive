extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _save(name: String) -> void:
	for frame in 5: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://../build/ui-refresh/" + name + ".png")
	DirAccess.make_dir_recursive_absolute(path.get_base_dir())
	var result := root.get_texture().get_image().save_png(path)
	print("UI CAPTURE ", name, ": ", error_string(result))
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate()
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.vehicle.set_physics_process(false)
	game.hud.expedition_ui.touch_mode = 1
	game.hud.expedition_ui._layout()
	await _save("driving")
	game.hud.expedition_ui.set_equipment_open(true)
	await _save("equipment")
	game._session_started = false
	game._open_menu(false)
	await _save("menu")
	game.menu.show_panel("settings")
	await _save("settings")
	game.queue_free()
	await process_frame
	quit()
