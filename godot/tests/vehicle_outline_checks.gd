extends SceneTree
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func frames(count := 3) -> void:
	for step in count: await process_frame

func _run() -> void:
	var render := OS.get_cmdline_user_args().has("--render")
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.world.set_process(false)
	await frames()
	var outline: Node = game.vehicle.get_node("VisibilityOutline")
	outline._timer = 0
	outline._process(0.1)
	check(not outline.occluded, "A fully visible car on the real open road has no outline")
	var count: int = outline.copies.size()
	outline._prepare()
	check(outline.copies.size() == count and count > 0, "Preparing the outline twice never duplicates car meshes")
	game.world.hide()
	game.effects.hide()
	game.weather.hide()
	game.road_leaves.hide()
	game.hud.hide()
	var target: Vector3 = game.vehicle.global_position + Vector3.UP * 0.7
	game.camera.global_position = target + Vector3(-10, 14, 11)
	game.camera.look_at(target, Vector3.UP)
	game.camera.size = 11
	var boundary := StaticBody3D.new()
	boundary.name = "MapBoundary"
	boundary.set_meta("outline_ignore", true)
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(7, 2, 7)
	shape.shape = box
	boundary.add_child(shape)
	root.add_child(boundary)
	boundary.global_position = game.camera.global_position.lerp(target, 0.5)
	await physics_frame
	await frames()
	outline._timer = 0
	outline._process(0.1)
	check(not outline.occluded and outline.copies.all(func(node: MeshInstance3D) -> bool: return not node.visible), "An invisible safety collider never activates the car outline")
	boundary.queue_free()
	await physics_frame
	await frames()
	outline.set_process(false)
	outline._set_occluded(false)
	var baseline: Image
	if render: baseline = await capture("outline-clear.png")
	# Deliberately activate a conservative canopy false-positive. The shader
	# still must not draw the rear wheels/body through the car's own panels.
	outline._set_occluded(true)
	outline._update_near_depth(game.camera)
	if render:
		var forced := await capture("outline-forced-visible.png")
		var difference := changed_pixels(baseline, forced)
		print("VISIBLE CAR OUTLINE changed_pixels=", difference)
		check(difference < 12, "Even a false-positive probe cannot outline a fully visible car")
	var blocker := StaticBody3D.new()
	var blocker_shape := CollisionShape3D.new()
	blocker_shape.shape = box
	blocker.add_child(blocker_shape)
	var visual := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = box.size
	visual.mesh = mesh
	var material := StandardMaterial3D.new()
	material.albedo_color = Color(0.18, 0.23, 0.26)
	visual.material_override = material
	blocker.add_child(visual)
	root.add_child(blocker)
	blocker.global_position = game.camera.global_position.lerp(target, 0.5)
	await physics_frame
	await frames()
	outline._timer = 0
	outline._process(0.1)
	check(outline.occluded, "A real roof or wall still activates the outline")
	if render:
		outline._set_occluded(false)
		var hidden := await capture("outline-hidden-off.png")
		outline._set_occluded(true)
		outline._update_near_depth(game.camera)
		var highlighted := await capture("outline-hidden-on.png")
		var difference := changed_pixels(hidden, highlighted)
		print("HIDDEN CAR OUTLINE changed_pixels=", difference)
		check(difference > 80, "Depth-tested outline remains visible through a genuine opaque roof")
	blocker.hide()
	await frames()
	outline._timer = 0
	outline._process(0.1)
	check(not outline.occluded, "Hiding an occluder immediately disables outline activation")
	if render:
		var low_wall := MeshInstance3D.new()
		var low_mesh := BoxMesh.new()
		low_mesh.size = Vector3(6, 0.6, 0.3)
		low_wall.mesh = low_mesh
		low_wall.material_override = material
		root.add_child(low_wall)
		low_wall.global_position = game.vehicle.global_position + Vector3(-0.5, 0.2, 1.4)
		outline._set_occluded(false)
		var visible_body := await capture("outline-low-wall-off.png")
		outline._set_occluded(true)
		outline._update_near_depth(game.camera)
		var low_wall_on := await capture("outline-low-wall-on.png")
		var difference := changed_pixels(visible_body, low_wall_on)
		print("VISIBLE BODY LOW WALL changed_pixels=", difference)
		check(difference < 12, "A low wall hiding only tyres cannot outline an otherwise visible car")
		low_wall.queue_free()
	blocker.queue_free()
	game.queue_free()
	await frames()
	print("VEHICLE OUTLINE: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)

func capture(filename: String) -> Image:
	await frames(4)
	await RenderingServer.frame_post_draw
	var result := root.get_texture().get_image()
	result.save_png(ProjectSettings.globalize_path("res://../build/visibility-fix/" + filename))
	return result

func changed_pixels(a: Image, b: Image) -> int:
	var changed := 0
	for y in a.get_height():
		for x in a.get_width():
			var p := a.get_pixel(x, y)
			var q := b.get_pixel(x, y)
			if maxf(absf(p.r - q.r), maxf(absf(p.g - q.g), absf(p.b - q.b))) > 0.035: changed += 1
	return changed
