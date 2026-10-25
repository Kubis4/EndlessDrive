extends SceneTree
func _initialize() -> void: call_deferred("_capture")
func _capture() -> void:
	if DisplayServer.get_name() == "headless":
		quit(2)
		return
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.hud.hide()
	game.overview_size = 38
	game.weather.forced_mode = "Jasno"
	game.run.day_time = 0.30
	for biome: int in [0, 3, 4]:
		var car: DriveVehicle = game.vehicle
		var world: DriveWorld = game.world
		car.park()
		game.run.engine_running = false
		world.configure(DriveTerrain.VARIED_SEED_MIN + biome)
		world.update_stream(65)
		game.effects.reset_trails()
		car.position = Vector3(65, world.height_at(65, world.center_at(65) + 5.5) + 0.6, world.center_at(65) + 5.5)
		car.heading = atan((world.center_at(66) - world.center_at(64)) * 0.5)
		car.reset_physics_interpolation()
		game.run.fuel = 40
		game.run.oil = 4
		game.run.coolant = 6
		for slot: String in game.run.parts: game.run.parts[slot] = 1.0
		game.run.variants.tires = "winter" if biome == 4 else "allterrain"
		for tick in 20:
			await physics_frame
			await process_frame
		game.run.engine_running = true
		for tick in 420:
			var target_z := world.center_at(car.position.x + 5) + 5.5
			var error := wrapf(atan2(target_z - car.position.z, 5) - car.heading, -PI, PI)
			Input.action_press("throttle", 0.7 if car.speed < 8 else 0.15)
			Input.action_press("right", clampf(error * 4, 0, 1))
			Input.action_press("left", clampf(-error * 4, 0, 1))
			await physics_frame
			await process_frame
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		var name: String = {0: "sand", 3: "earth", 4: "snow"}[biome]
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/trail-%s.png" % name))
		print("TRAIL CAPTURE ", name, " marks=", game.effects._count)
		for action: String in ["throttle", "left", "right"]: Input.action_release(action)
	game.queue_free()
	await process_frame
	await process_frame
	quit()
