extends SceneTree
## Low-angle evidence from the saved editor snapshot, not the model gallery.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	root.size = Vector2i(1440, 810)
	var scene := (load("res://scenes/world/generated/editor_terrain.scn") as PackedScene).instantiate()
	root.add_child(scene)
	var environment := WorldEnvironment.new()
	environment.environment = Environment.new()
	environment.environment.background_mode = Environment.BG_COLOR
	environment.environment.background_color = Color("9fb5ba")
	environment.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.environment.ambient_light_color = Color("d3d9d5")
	environment.environment.ambient_light_energy = 0.4
	root.add_child(environment)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-40, -25, 0)
	sun.light_energy = 0.9
	sun.shadow_enabled = true
	root.add_child(sun)
	var focus := Vector3.ZERO
	var found := false
	for mesh: MeshInstance3D in scene.find_children("SculptedPeak", "MeshInstance3D", true, false):
		if absf(mesh.global_position.x) < 10.0 and mesh.global_position.z < 0:
			focus = mesh.to_global(mesh.mesh.get_aabb().get_center())
			found = true
			break
	if not found:
		push_error("Saved preview contains no expected roadside peak")
		quit(1)
		return
	var camera := Camera3D.new()
	root.add_child(camera)
	camera.position = focus + Vector3(18, 3, 65)
	camera.look_at(focus)
	camera.far = 500
	for frame in 12: await process_frame
	await RenderingServer.frame_post_draw
	var error := root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/v5-saved-rocks.png"))
	print("SAVED ROCKS CAPTURE: ", error_string(error))
	quit(0 if error == OK else 1)
