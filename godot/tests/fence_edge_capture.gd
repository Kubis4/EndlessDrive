extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.world.set_process(false)
	game.world.async_streaming = false
	game.world.hazards_enabled = true
	game.hud.hide()
	for entry: Array in [["fence", 1000003, 240.0, 35.0], ["forest", 1000004, 180.0, 110.0], ["mountain", 24601, 3200.0, 115.0], ["rail", 24601, 744.0, 115.0]]:
		game.world.configure(int(entry[1]))
		game.world.update_stream(float(entry[2]))
		var focus := Vector3(float(entry[2]), 0, game.world.center_at(float(entry[2])))
		focus.y = game.world.height_at(focus.x, focus.z)
		game.vehicle.position = focus + Vector3.UP * 0.6
		if entry[0] == "fence":
			var best := -1.0
			for chunk: Node3D in game.world.chunks.values():
				for site: Dictionary in chunk.get_meta("fence_sites", {}).values():
					if absf(site.points[1].x - float(entry[2])) > 70: continue
					var grade := absf(site.points[2].y - site.points[0].y)
					if grade > best: best = grade; focus = site.points[1]
		elif entry[0] == "mountain": focus = game.world._boundary_point(float(entry[2]), 1, -5.8)
		game.camera.size = float(entry[3])
		game.camera.position = focus + Vector3(-32, 70, 48)
		game.camera.look_at(focus)
		game._update_lighting()
		for frame in 25: await process_frame
		await RenderingServer.frame_post_draw
		var path := "res://../build/track-edges/" + str(entry[0]) + ".png"
		var result := root.get_texture().get_image().save_png(ProjectSettings.globalize_path(path))
		print("EDGE CAPTURE ", entry[0], " ", error_string(result))
		if result != OK: quit(1); return
	game.free()
	quit()
