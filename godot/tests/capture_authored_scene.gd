extends SceneTree
## GPU check of SAVED scene content, without a journey or player profile.

func _initialize() -> void:
	call_deferred("_capture")

func _capture() -> void:
	var scene: Node
	var mode := "world"
	var view := 0
	for argument: String in OS.get_cmdline_user_args():
		if argument.begins_with("--mode="): mode = argument.trim_prefix("--mode=")
		if argument.begins_with("--view="): view = argument.trim_prefix("--view=").to_int()
	if mode == "world":
		scene = load("res://scenes/main.tscn").instantiate()
		# Only saved geometry, exactly as in the editor; no generated replacement.
		scene.set_script(null)
		scene.get_node("EditorTerrainPreview").set_script(null)
		scene.get_node("StartCampPreview").set_script(null)
		scene.get_node("Vehicle").set_script(null)
		root.add_child(scene)
	elif mode == "hud":
		scene = load("res://scenes/ui/hud.tscn").instantiate()
		root.add_child(scene)
		scene.editor_view = view
		scene._preview_layout()
	else:
		scene = load("res://scenes/ui/main_menu.tscn").instantiate()
		root.add_child(scene)
		scene.editor_view = view
		scene._preview_layout()
	for frame in 12: await process_frame
	await RenderingServer.frame_post_draw
	var path := "res://../build/godot-authored-" + mode + ".png"
	var error := root.get_texture().get_image().save_png(ProjectSettings.globalize_path(path))
	print("AUTHORED CAPTURE ", error_string(error), " ", path)
	scene.queue_free()
	await process_frame
	quit(0 if error == OK else 1)
