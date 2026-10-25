extends SceneTree
## Natural generated wetland + real async collision worker, with optional GPU capture.
func _initialize() -> void:
	if not "--test-session" in OS.get_cmdline_user_args():
		quit(2)
		return
	call_deferred("_run")

func frames(n: int) -> void:
	for i in n:
		await physics_frame
		await process_frame

func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.vehicle.park()
	var x := 5000.0
	var z: float = game.world.center_at(x) + 16.0
	game.world.update_stream(x)
	game.vehicle.position = Vector3(x, game.world.height_at(x, z) + 0.6, z)
	game.vehicle.reset_physics_interpolation()
	game.run.parts.tires = 0.3
	game.run.parts.engine = 0.4
	game.run.fuel = 40
	game.run.day_time = 0.27
	game.weather.forced_mode = "Jasno"
	await frames(30)
	var start: Vector3 = game.vehicle.position
	print("FIELD INITIAL ", game.world.terrain.sample(start.x, start.z))
	game.world.async_streaming = true
	game.run.engine_running = true
	Input.action_press("throttle")
	for tick in 900:
		var car: DriveVehicle = game.vehicle
		var target_z: float = game.world.center_at(car.position.x + 5.0) + 16.0
		var error := wrapf(atan2(target_z - car.position.z, 5.0) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 4.0, 0, 1))
		Input.action_press("left", clampf(-error * 4.0, 0, 1))
		await frames(1)
	var car: DriveVehicle = game.vehicle
	var old_ground: float = game.world.height_at(car.position.x, car.position.z) + game.world.terrain.deformation.depth_at(car.position.x, car.position.z)
	var buried: float = old_ground + DriveVehicle.WHEEL_RADIUS - car.position.y
	var passed: bool = car.position.x - start.x > 10.0 and car.sink_depth < 0.25 and car.boundary_recoveries == 0
	print("%s FIELD SOIL: travel=%.2f m buried=%.3f m sink=%.3f m speed=%.3f slip=%.2f reason=%s commit=%.2f ms" % ["PASS" if passed else "FAIL", start.distance_to(car.position), buried, car.sink_depth, car.speed, car.soil_slip, car.stop_reason, game.world.max_deformation_ms])
	if DisplayServer.get_name() != "headless":
		game.overview_size = 30
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		var path := "res://../build/soft-soil-world.png"
		print("CAPTURE ", error_string(root.get_texture().get_image().save_png(ProjectSettings.globalize_path(path))))
	Input.action_release("throttle")
	Input.action_release("right")
	Input.action_release("left")
	game.queue_free()
	await frames(2)
	quit(0 if passed else 1)
