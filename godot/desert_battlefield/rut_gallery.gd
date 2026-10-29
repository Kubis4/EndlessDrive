extends SceneTree
## Reproducible tight turns and crossings for before/after shadow inspection.
const Surface = preload("res://desert_battlefield/terrain.gd")
func _initialize() -> void: call_deferred("run")
func run() -> void:
	root.size = Vector2i(1920, 1080)
	var game := (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	game.set_process(false)
	for car in game.get_node("Patrol").get_children():
		car.set_physics_process(false)
		for particle in car.find_children("*", "GPUParticles3D", true, false): particle.emitting = false
	var wheel_passes := 4
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--vehicle-passes="):
			wheel_passes = clampi(arg.trim_prefix("--vehicle-passes=").to_int(), 1, 40) * 2
	for center in [Vector2(-7, 24), Vector2(12, 30)]:
		var radius := 6.0 if center.x < 0 else 3.8
		for pass_index in wheel_passes:
			for side in [-1.0, 1.0]:
				var r: float = radius + side * 1.12
				var count := ceili(TAU * r / 0.18)
				for i in count:
					var t := TAU * float(i) / count
					var p := Vector3(center.x + cos(t) * r, 0, center.y + sin(t) * r)
					game.soil.stamp(p, Vector3(-sin(t), 0, cos(t)), 0.18, 0.8)
	while not game.soil.pending.is_empty():
		game.soil.update(0.1, true)
		await process_frame
	var camera: Camera3D = game.get_node("Camera3D")
	var focus := Vector3(0, Surface.height_at(0, 26), 26)
	camera.position = focus + Vector3(10, 35, 23)
	camera.look_at(focus)
	camera.size = 39
	for i in 45: await process_frame
	if DisplayServer.get_name() != "headless":
		await RenderingServer.frame_post_draw
		var output := "res://../build/rut-gallery.png"
		for arg in OS.get_cmdline_user_args():
			if arg.begins_with("--output="): output = arg.trim_prefix("--output=")
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path(output))
		print("RUT GALLERY ", output)
	quit()
