extends SceneTree
## Real rendered regression of approach, ballistic flight and grounded landing.
var failures: Array[String] = []

func _initialize() -> void: call_deferred("run")

func capture(name: String) -> void:
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://../build/handling-" + name + ".png")
	var error := root.get_texture().get_image().save_png(path)
	if error != OK: failures.append("Could not save " + path)
	print("CAPTURE ", path)

func run() -> void:
	root.size = Vector2i(1920, 1080)
	var game := (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	for vehicle in game.get_node("Patrol").get_children(): vehicle.set_physics_process(false)
	var car = game.get_node("Patrol/Scout01")
	car.place_on_ground(Vector3(-25, 0, 45), -PI * 0.5)
	car.velocity = -car.chassis_basis().z * 16.0
	car.manual = true
	game.driving = true
	game.camera_size = 22.0
	game.camera.size = 22.0
	game.focus = car.position
	var airborne_capture := false
	var landed_capture := false
	var camera: Camera3D = game.get_node("Camera3D")
	for i in 480:
		await physics_frame
		car.simulate(1.0 / 60.0, clampf((16.0 - car.speed) * 0.7, 0.0, 1.0), 0.0, 0.0, false)
		if not airborne_capture and car.air_time > 0.12 and car.velocity.y < 0.5:
			game.set_process(false)
			game._process(0.11)
			var focus: Vector3 = car.position.lerp(Vector3(car.position.x, car.support_height, car.position.z), 0.35)
			camera.position = focus + Vector3(18, 16, 27)
			camera.look_at(focus)
			for frame in 4: await process_frame
			await capture("airborne")
			airborne_capture = true
			game.set_process(true)
		if airborne_capture and car.landing_count > 0 and car.grounded:
			game.set_process(false)
			game._process(0.11)
			camera.position = car.position + Vector3(18, 16, 27)
			camera.look_at(car.position)
			for frame in 4: await process_frame
			await capture("landing")
			landed_capture = true
			break
	if not airborne_capture or not landed_capture: failures.append("The rendered dune traversal did not both jump and land")
	print("JUMP CAPTURE ", "PASS" if failures.is_empty() else failures, " flight_s=", car.longest_flight, " impact_m_s=", car.landing_impact_speed)
	# A controlled roof drop verifies the rendered mesh and contact hull agree.
	car.place_on_ground(Vector3(-30, 0, 40), 0.0)
	car.attitude = Basis(Vector3.BACK, PI)
	car.position.y += 3.0
	car.velocity = Vector3.ZERO
	for i in 300:
		await physics_frame
		car.simulate(1.0 / 60.0, 0, 0, 0, false)
	game._process(0.11)
	camera.size = 14.0
	camera.position = car.position + Vector3(10, 6, 13)
	camera.look_at(car.position)
	for frame in 4: await process_frame
	await capture("rollover")
	if not car.overturned or not car.chassis_grounded: failures.append("Rendered roof landing failed")
	print("ROLLOVER CAPTURE ", "PASS" if failures.is_empty() else failures)
	quit(0 if failures.is_empty() else 1)
