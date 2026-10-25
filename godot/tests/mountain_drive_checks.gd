extends SceneTree
## The actual unassisted starter car follows the first pair of uphill hairpins.
func _initialize() -> void: call_deferred("_run")
func frame() -> void:
	await physics_frame
	await process_frame
func _run() -> void:
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.set_process(false)
	game.weather.set_process(false)
	game.effects.set_process(false)
	var world: DriveWorld = game.world
	var car: DriveVehicle = game.vehicle
	world.configure(24601)
	world.deformation_enabled = false
	world.hazards_enabled = false
	var path := world.terrain.mountain_path(3)
	for index in range(30, 40): world._create_chunk(index)
	var points := PackedVector3Array([path[0]])
	for i in path.size() - 1:
		var count := maxi(1, ceili(path[i].distance_to(path[i + 1]) / 2.0))
		for j in count:
			var point := path[i].lerp(path[i + 1], float(j + 1) / count)
			if point.distance_to(points[-1]) > 0.001: points.append(point)
	car.park()
	car.position = points[0] + Vector3.UP * 0.6
	car.heading = Vector2(points[1].x - points[0].x, points[1].z - points[0].z).angle()
	car.reset_physics_interpolation()
	game.run.engine_running = false
	game.run.four_wheel_drive = true
	game.run.fuel = 40
	for i in 20: await frame()
	game.run.engine_running = true
	var target := 1
	var backwards := 0.0
	var previous := car.position
	var max_lateral := 0.0
	var max_height := car.position.y
	var stalled := 0
	for tick in 22000:
		var p := Vector2(car.position.x, car.position.z)
		while target < points.size() - 1 and p.distance_to(Vector2(points[target].x, points[target].z)) < 4.0: target += 1
		var error := wrapf((Vector2(points[target].x, points[target].z) - p).angle() - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 3.2, 0, 1))
		Input.action_press("left", clampf(-error * 3.2, 0, 1))
		Input.action_press("throttle", 0.75 if car.speed < 5.0 else 0.08)
		Input.action_press("brake", clampf((car.speed - 5.5) * 0.35, 0, 1))
		await frame()
		backwards += maxf(0, previous.x - car.position.x)
		previous = car.position
		max_height = maxf(max_height, car.position.y)
		max_lateral = maxf(max_lateral, absf(world.terrain.road_distance(car.position.x, car.position.z)))
		stalled = stalled + 1 if absf(car.speed) < 0.05 else 0
		if target >= points.size() - 2 or stalled > 240 or car.boundary_recoveries > 0: break
	for key in ["throttle", "brake", "right", "left"]: Input.action_release(key)
	var ok := target >= points.size() - 2 and backwards > 100 and max_lateral < 5.5 and car.boundary_recoveries == 0 and max_height > points[0].y + 40
	print("%s MOUNTAIN DRIVE points=%d/%d reverse_X=%.1f m height_gain=%.1f m lateral=%.2f m rescue=%d reason=%s" % ["PASS" if ok else "FAIL", target, points.size() - 1, backwards, max_height - points[0].y, max_lateral, car.boundary_recoveries, car.stop_reason])
	if not ok: print("DIAGNOSTIC pose=", car.position, " target=", points[target], " normal=", car.last_collision_normal)
	game.queue_free()
	await frame()
	quit(0 if ok else 1)
