extends SceneTree
## Native captures and a saved Godot editor gallery of actual streamed roads.
var world: DriveWorld
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	root.size = Vector2i(1280, 800)
	var stage := Node3D.new()
	root.add_child(stage)
	var environment := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color("9eafb5")
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("c8cccb")
	env.ambient_light_energy = 0.4
	environment.environment = env
	stage.add_child(environment)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52, -24, 0)
	sun.light_energy = 0.85
	sun.light_color = Color("f0ede6")
	sun.shadow_enabled = true
	stage.add_child(sun)
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 165.0
	stage.add_child(camera)
	camera.make_current()
	var gallery := Node3D.new()
	gallery.name = "JunctionGallery"
	for seed_value in range(24600, 24603):
		world = DriveWorld.new()
		stage.add_child(world)
		world.configure(seed_value)
		world.batch_scenery = false
		for i in range(3, 7): world._create_chunk(i)
		var layout: String = world.terrain.junction_style(0).layout
		var focus := Vector3(480, 0, world.terrain._base_center(480))
		camera.position = focus + Vector3(-16, 180, 70)
		camera.look_at(focus)
		await frames(3)
		if DisplayServer.get_name() != "headless":
			await RenderingServer.frame_post_draw
			root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/junction-%s.png" % layout))
		var group := Node3D.new()
		group.name = layout.to_pascal_case()
		group.position = Vector3(-480 + (seed_value % 2) * 400, 0, ((seed_value - 24600) / 2) * 330)
		gallery.add_child(group)
		for chunk: Node in world.get_children():
			world.remove_child(chunk)
			group.add_child(chunk)
		_strip(group)
		world.free()
		print("CAPTURED ", layout)
	_owners(gallery, gallery)
	var packed := PackedScene.new()
	var error := packed.pack(gallery)
	if error == OK: error = ResourceSaver.save(packed, "res://scenes/world/generated/junction_gallery.scn", ResourceSaver.FLAG_COMPRESS)
	print("SAVED JUNCTION GALLERY ", error_string(error))
	gallery.free()
	stage.queue_free()
	await frames(2)
	quit(0 if error == OK else 1)
func frames(count: int) -> void:
	for i in count: await process_frame
func _strip(node: Node) -> void:
	node.owner = null
	node.scene_file_path = ""
	for key in node.get_meta_list(): node.remove_meta(key)
	for child in node.get_children():
		if child is CollisionObject3D or child is CollisionShape3D:
			node.remove_child(child)
			child.free()
		else: _strip(child)
func _owners(node: Node, owner_root: Node) -> void:
	for child in node.get_children():
		child.owner = owner_root
		_owners(child, owner_root)
