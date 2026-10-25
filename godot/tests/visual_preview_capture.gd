extends SceneTree
## Renders the saved native editor resources, without generating runtime terrain.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var authored := (load("res://scenes/main.tscn") as PackedScene).instantiate()
	var environment := WorldEnvironment.new()
	environment.environment = authored.get_node("WorldEnvironment").environment
	root.add_child(environment)
	var sun := DirectionalLight3D.new()
	sun.transform = authored.get_node("Sun").transform
	sun.light_color = Color("fff1d8")
	sun.light_energy = 0.85
	sun.shadow_enabled = true
	sun.shadow_bias = 0.3
	sun.shadow_normal_bias = 3
	sun.directional_shadow_max_distance = 120
	root.add_child(sun)
	authored.free()
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	root.add_child(camera)
	for entry: Array in [["editor_terrain", 65.0, 1000003, 43.0], ["rail", 744.0, 24601, 104.0], ["tunnel", 1704.0, 24601, 112.0], ["alpine", 180.0, 1000005, 80.0]]:
		var runtime := "--runtime" in OS.get_cmdline_user_args()
		var geometry: Node3D
		if runtime:
			geometry = DriveWorld.new()
			root.add_child(geometry)
			geometry.configure(int(entry[2]))
			geometry.hazards_enabled = true
			var index := floori(float(entry[1]) / DriveWorld.CHUNK_LENGTH)
			for chunk in range(index - 1, index + 2): geometry._create_chunk(chunk)
		else:
			geometry = (load("res://scenes/world/generated/" + str(entry[0]) + ".scn") as PackedScene).instantiate()
			root.add_child(geometry)
		for node in geometry.find_children("*", "MeshInstance3D", true, false):
			if node.name == "SandAndTracks":
				print("PREVIEW MATERIAL ", entry[0], " enabled=", node.material_override.get_shader_parameter("surface_data_enabled"), " atlas=", node.material_override.get_shader_parameter("surface_atlas"), " micro=", node.material_override.get_shader_parameter("micro_height"))
				break
		var terrain := DriveTerrain.new()
		terrain.seed_value = int(entry[2])
		var x := float(entry[1])
		var center := Vector3(x, terrain.height_at(x, terrain.center_at(x)), terrain.center_at(x))
		camera.size = float(entry[3])
		camera.position = center + Vector3(-36, 52, 44)
		camera.look_at(center)
		for i in 40: await process_frame
		await RenderingServer.frame_post_draw
		var output := "res://../build/hd-%s-%s.png" % [str(entry[0]), "runtime" if runtime else "preview"]
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path(output))
		print("CAPTURE ", output)
		geometry.queue_free()
		await process_frame
	quit()
