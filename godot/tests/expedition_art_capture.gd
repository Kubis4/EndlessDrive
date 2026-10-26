extends SceneTree
## Real GPU captures of the actual saved models and runtime biome materials.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	root.size = Vector2i(1440, 900)
	var gallery := (load("res://scenes/world/expedition_art_gallery.tscn") as PackedScene).instantiate()
	root.add_child(gallery)
	for i in 12: await process_frame
	await _save("gallery")
	var detail_camera := gallery.get_node("GalleryCamera") as Camera3D
	var portal_focus: Vector3 = gallery.get_node("TunnelPortal").position + Vector3.UP * 3.0
	detail_camera.size = 24
	detail_camera.position = portal_focus + Vector3(14, 12, 19)
	detail_camera.look_at(portal_focus)
	for i in 4: await process_frame
	await _save("portal-detail")
	if "--texture-details" in OS.get_cmdline_user_args():
		for entry: Array in [["Locomotive", "train-detail", 16.0, 1.0], ["WoodlandTree", "tree-detail", 17.0, 4.0]]:
			var subject := gallery.get_node(str(entry[0])) as Node3D
			var focus := subject.position + Vector3.UP * float(entry[3])
			detail_camera.size = float(entry[2])
			detail_camera.position = focus + Vector3(12, 11, 16)
			detail_camera.look_at(focus)
			for i in 4: await process_frame
			await _save(str(entry[1]))
	gallery.free()
	if "--gallery-only" in OS.get_cmdline_user_args():
		await process_frame
		quit()
		return
	var environment := WorldEnvironment.new()
	environment.environment = Environment.new()
	environment.environment.background_mode = Environment.BG_COLOR
	environment.environment.background_color = Color("bcc5c0")
	environment.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.environment.ambient_light_color = Color("d0d9d3")
	environment.environment.ambient_light_energy = 0.28
	root.add_child(environment)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-42, 60, 0)
	sun.light_energy = 0.85
	sun.light_color = Color("fff0d7")
	sun.shadow_enabled = true
	sun.shadow_bias = 0.3
	sun.shadow_normal_bias = 3
	sun.directional_shadow_max_distance = 150
	root.add_child(sun)
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.far = 600
	root.add_child(camera)
	camera.make_current()
	for entry: Array in [["sand", 1000001, 180, 65], ["forest", 1000004, 180, 70], ["alpine", 1000005, 180, 80], ["rail", 24601, 744, 85], ["tunnel", 24601, 1704, 108]]:
		if "--vegetation-only" in OS.get_cmdline_user_args() and str(entry[0]) not in ["forest", "alpine"]: continue
		var selected := ""
		for arg: String in OS.get_cmdline_user_args():
			if arg.begins_with("--only="): selected = arg.trim_prefix("--only=")
		if not selected.is_empty() and str(entry[0]) != selected: continue
		var world := DriveWorld.new()
		root.add_child(world)
		world.configure(int(entry[1]))
		world.hazards_enabled = true
		world.set_process(false)
		world.clock = 7.5 - posmod(world.world_seed, 20)
		var x := float(entry[2])
		var index := floori(x / DriveWorld.CHUNK_LENGTH)
		for chunk in range(index - 1, index + 2): world._create_chunk(chunk)
		var center := Vector3(x, world.height_at(x, world.center_at(x)), world.center_at(x))
		var jeep := (load(DriveVehicle.MODEL_PATH) as PackedScene).instantiate() as Node3D
		jeep.position = center + Vector3(-12, 0.5, 1.8)
		world.add_child(jeep)
		camera.size = float(entry[3])
		camera.position = center + Vector3(-42, 62, 50)
		camera.look_at(center)
		for frame in 12: await process_frame
		await _save(str(entry[0]))
		world.free()
		await process_frame
	quit()
func _save(label: String) -> void:
	await RenderingServer.frame_post_draw
	var prefix := "expedition-art"
	for arg: String in OS.get_cmdline_user_args():
		if arg.begins_with("--output-prefix="): prefix = arg.trim_prefix("--output-prefix=").validate_filename()
	var path := "res://../build/%s-%s.png" % [prefix, label]
	var error := root.get_texture().get_image().save_png(ProjectSettings.globalize_path(path))
	print("ART CAPTURE %s: %s" % [label, error_string(error)])
