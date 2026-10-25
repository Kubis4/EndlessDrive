extends SceneTree
## Saved, inspectable production terrain with several real tyre passes.
func _initialize() -> void: call_deferred("_run")
func own_nodes(node: Node, scene_root: Node) -> void:
	for child in node.get_children():
		child.owner = scene_root
		own_nodes(child, scene_root)
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	for step in 130:
		var x := 400.0 + step * 0.4
		var z := world.center_at(x) + 14.0 + sin((x - 400.0) * 0.09) * 1.2
		var forward := Vector2(1, (world.center_at(x + 0.2) - world.center_at(x - 0.2)) / 0.4 + cos((x - 400.0) * 0.09) * 0.108).normalized()
		var side := Vector2(-forward.y, forward.x)
		for wheel in [-1, 1]:
			var p := Vector3(x + side.x * wheel * 0.96, 0, z + side.y * wheel * 0.96)
			for pass_index in 8: world.terrain.deformation.compact(p, world.terrain.sample(p.x, p.z), 1, forward, 0.25)
	world._create_chunk(4)
	var source: Node3D = world.chunks[4].get_node("SandAndTracks")
	var scene := Node3D.new()
	scene.name = "DeformationReferencePreview"
	root.add_child(scene)
	source.reparent(scene)
	var focus := Vector3(440, world.height_at(440, world.center_at(440) + 14), world.center_at(440) + 14)
	var camera := Camera3D.new()
	camera.name = "PreviewCamera"
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 28
	scene.add_child(camera)
	camera.position = focus + Vector3(19, 24, 23)
	camera.look_at(focus)
	camera.current = true
	var sun := DirectionalLight3D.new()
	sun.name = "Sun"
	sun.rotation_degrees = Vector3(-35, -25, 0)
	sun.light_energy = 1.1
	scene.add_child(sun)
	var environment := WorldEnvironment.new()
	environment.environment = Environment.new()
	environment.environment.background_mode = Environment.BG_COLOR
	environment.environment.background_color = Color("bfc4bf")
	environment.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.environment.ambient_light_color = Color("e2e3dd")
	environment.environment.ambient_light_energy = 0.65
	scene.add_child(environment)
	var dust := load("res://assets/effects/wheel_dust.tscn").instantiate() as CPUParticles3D
	scene.add_child(dust)
	dust.name = "DustSpritePreview"
	dust.position = focus + Vector3(3, 1.2, 0)
	dust.direction = Vector3(-1, 0.15, 0)
	dust.initial_velocity_min = 1.2
	dust.initial_velocity_max = 3.5
	dust.gravity = Vector3(0, 0.06, 0)
	dust.scale_amount_min = 1.5
	dust.scale_amount_max = 6
	dust.color = Color(0.85, 0.76, 0.58, 0.65)
	dust.emitting = true
	own_nodes(scene, scene)
	var packed := PackedScene.new()
	var error := packed.pack(scene)
	if error == OK: error = ResourceSaver.save(packed, "res://scenes/world/generated/deformation.scn", ResourceSaver.FLAG_COMPRESS)
	print("DEFORMATION PREVIEW: ", error_string(error))
	world.free()
	scene.free()
	quit(0 if error == OK else 1)
