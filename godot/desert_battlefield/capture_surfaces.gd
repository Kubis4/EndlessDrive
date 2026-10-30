extends SceneTree
## Rendered check of surface sprites, wheelspin, shader LOD and touch controls.
## godot --path . --script res://desert_battlefield/capture_surfaces.gd -- --touch-controls
var game

func _initialize() -> void: call_deferred("run")

func shot(name: String) -> void:
	for i in 3: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://../build/surface-" + name + ".png")
	root.get_texture().get_image().save_png(path)
	print("CAPTURE ", path)

func drive(frames: int, throttle: float, turn: float = 0.0) -> void:
	for i in frames:
		await physics_frame
		game.lead.simulate(1.0 / 60.0, throttle, 0.0, turn, false)

func timed(label: String, frames: int) -> void:
	await process_frame
	var start := Time.get_ticks_usec()
	for i in frames:
		await RenderingServer.frame_post_draw
	print("FRAME ", label, " ms=", (Time.get_ticks_usec() - start) / 1000.0 / frames)

func run() -> void:
	root.size = Vector2i(1600, 900)
	game = (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	game.lead.set_physics_process(false)
	var spots: Array = game.test_spots
	var index := {}
	for i in spots.size(): index[spots[i][0]] = i
	print(index.keys())
	# Mud from standstill: 4x4 sprays from all four tyres, 4x2 only from the rear.
	for four in [true, false]:
		game.teleport_to_test(index["Bahno"])
		game.lead.four_wheel_drive = four
		game._set_camera_mode(6)
		game.camera_drag(Vector2(-260, -40))
		await drive(90, 1.0)
		print("mud 4x4=", four, " speed=", game.lead.speed, " slip=", game.lead.drive_slip, " spin=", game.lead.wheel_spin_rate)
		await shot("mud-4x4" if four else "mud-4x2")
	game.lead.four_wheel_drive = true
	# Moguls with an open diff: lifted wheels spin.
	game.teleport_to_test(index["Hrboľe a skok"])
	game._set_camera_mode(6)
	game.camera_drag(Vector2(300, 10))
	game.camera_pinch(1.4)
	await drive(200, 0.8)
	await shot("free-camera")
	E_done()

func E_done() -> void:
	quit()
