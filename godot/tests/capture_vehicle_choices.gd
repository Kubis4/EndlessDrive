extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	DirAccess.make_dir_recursive_absolute("res://assets/ui/vehicles")
	for id: String in ["izh", "jeep"]:
		var view := SubViewport.new()
		view.size = Vector2i(320, 200)
		view.own_world_3d = true
		view.transparent_bg = true
		view.render_target_update_mode = SubViewport.UPDATE_ALWAYS
		root.add_child(view)
		var environment := WorldEnvironment.new()
		environment.environment = Environment.new()
		environment.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
		environment.environment.ambient_light_color = Color.WHITE
		environment.environment.ambient_light_energy = 0.8
		view.add_child(environment)
		var sun := DirectionalLight3D.new()
		sun.rotation_degrees = Vector3(-50, -35, 0)
		sun.light_energy = 1.3
		view.add_child(sun)
		var camera := Camera3D.new()
		camera.projection = Camera3D.PROJECTION_ORTHOGONAL
		camera.size = 6.2
		view.add_child(camera)
		camera.position = Vector3(5, 3.4, 5)
		camera.look_at(Vector3(0, 0.45, 0))
		var path: String = preload("res://scripts/vehicle_catalog.gd").DATA[id].scene
		view.add_child((load(path) as PackedScene).instantiate())
		for frame in 5: await process_frame
		await RenderingServer.frame_post_draw
		view.get_texture().get_image().save_png("res://assets/ui/vehicles/" + id + ".png")
		# The service schematic uses the same model from directly overhead.
		view.size = Vector2i(200, 320)
		camera.size = 5.8
		camera.position = Vector3(0, 9, 0)
		camera.look_at(Vector3.ZERO, Vector3.RIGHT)
		for frame in 5: await process_frame
		await RenderingServer.frame_post_draw
		view.get_texture().get_image().save_png("res://assets/ui/vehicles/" + id + "_top.png")
		view.queue_free()
		await process_frame
	quit()
