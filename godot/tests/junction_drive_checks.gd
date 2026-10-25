extends SceneTree
## Actual tyre/chassis collision while following roads that reverse world X,
## plus taking the other visible exit after a route has already been selected.
var failed := false
func _initialize() -> void: call_deferred("_run")
func frame() -> void:
	await physics_frame
	await process_frame
func _run() -> void:
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.set_process(false)
	game.effects.set_process(false)
	game.weather.set_process(false)
	var car: DriveVehicle = game.vehicle
	var world: DriveWorld = game.world
	for seed_value in [24601, 24602]:
		world.async_streaming = false
		world.configure(seed_value)
		world.deformation_enabled = false
		world.hazards_enabled = false
		if seed_value == 24601:
			world.terrain.route_choices = {"0": -1}
			world.terrain.clear_caches()
		for index in range(3, 9): world._create_chunk(index)
		var layout: String = world.terrain.junction_style(0).layout
		var path := PackedVector2Array()
		path = world.terrain.junction_paths(0)[0]
		if seed_value == 24601:
			# Path 0 crosses from the upper entrance to the LOWER exit, whereas
			# the saved selection is upper. Its continuation must remain real.
			for x in range(602, 741, 2): path.append(Vector2(x, world.terrain.road_centers(x)[-1]))
		var points := resample(path)
		car.park()
		car.position = Vector3(points[0].x, world.height_at(points[0].x, points[0].y) + 0.6, points[0].y)
		car.heading = (points[1] - points[0]).angle()
		car.reset_physics_interpolation()
		game.run.engine_running = false
		game.run.variants.tires = "allterrain"
		game.run.four_wheel_drive = true
		game.run.differential_locked = false
		game.run.fuel = 40
		for key in game.run.parts: game.run.parts[key] = 1.0
		for i in 20: await frame()
		game.run.engine_running = true
		var target := 1
		var backwards := 0.0
		var previous := car.position
		var max_lateral := 0.0
		var stalled := 0
		var rescues := car.boundary_recoveries
		for tick in 9000:
			var pos := Vector2(car.position.x, car.position.z)
			while target < points.size() - 1 and pos.distance_to(points[target]) < 5.0: target += 1
			var error := wrapf((points[target] - pos).angle() - car.heading, -PI, PI)
			Input.action_press("right", clampf(error * 3.2, 0.0, 1.0))
			Input.action_press("left", clampf(-error * 3.2, 0.0, 1.0))
			Input.action_press("throttle", 0.65 if car.speed < 6.0 else 0.08)
			await frame()
			backwards += maxf(0.0, previous.x - car.position.x)
			previous = car.position
			max_lateral = maxf(max_lateral, absf(world.terrain.road_distance(car.position.x, car.position.z)))
			stalled = stalled + 1 if absf(car.speed) < 0.05 else 0
			if stalled > 240 or car.boundary_recoveries > rescues: break
			if target == points.size() - 1 and pos.distance_to(points[-1]) < 5.0: break
		for key in ["throttle", "right", "left"]: Input.action_release(key)
		var ok := target >= points.size() - 2 and car.boundary_recoveries == rescues and max_lateral < 6.5
		if layout == "switchback": ok = ok and backwards > 20.0
		failed = failed or not ok
		print("%s DRIVE %s points=%d/%d backwards=%.1f m lateral=%.2f m rescues=%d reason=%s" % ["PASS" if ok else "FAIL", layout, target, points.size() - 1, backwards, max_lateral, car.boundary_recoveries - rescues, car.stop_reason])
	game.queue_free()
	await frame()
	quit(1 if failed else 0)
func resample(path: PackedVector2Array) -> PackedVector2Array:
	var points := PackedVector2Array([path[0]])
	for i in path.size() - 1:
		var count := maxi(1, ceili(path[i].distance_to(path[i + 1]) / 2.0))
		for step in count:
			var p := path[i].lerp(path[i + 1], float(step + 1) / count)
			if points[-1].distance_to(p) > 0.001: points.append(p)
	return points
