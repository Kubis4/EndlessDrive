extends SceneTree
## Production branch selection + worker streaming + actual tunnel collisions.
var failed := false
func _initialize() -> void: call_deferred("_run")
func frame() -> void:
	await physics_frame
	await process_frame
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.route_decisions_enabled = false
	game.weather.forced_mode = "Jasno"
	game.world.hazards_enabled = true
	game.world.deformation_enabled = false
	game.run.fuel = 40
	game.run.oil = 4
	game.run.coolant = 6
	for slot: String in game.run.parts: game.run.parts[slot] = 1.0
	var lower := not "--upper" in OS.get_cmdline_user_args()
	var start := 430.0
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--from="): start = arg.trim_prefix("--from=").to_float()
	var car: DriveVehicle = game.vehicle
	var world: DriveWorld = game.world
	world.configure(24601)
	world.update_stream(start)
	car.park()
	var lane_index := 1 if lower else 0
	var lane: float = world.terrain.road_centers(start)[lane_index]
	car.position = Vector3(start, world.height_at(start, lane) + 0.6, lane)
	car.heading = atan(world.terrain.road_centers(start + 0.5)[lane_index] - world.terrain.road_centers(start - 0.5)[lane_index])
	game._update_camera(1, true)
	for i in 10: await frame()
	world.async_streaming = true
	game.route_decisions_enabled = true
	game.run.engine_running = true
	var next_report := start
	var previous := car.position
	var max_step := 0.0
	var max_camera := 0.0
	var old_camera: Vector3 = game.camera.global_position
	var max_drop := 0.0
	var stopped := 0
	# Render frames may contain several physics ticks during shader warmup.
	var physics_motion := {"last": car.position, "max": 0.0}
	var monitor := func() -> void:
		physics_motion.max = maxf(float(physics_motion.max), car.position.distance_to(physics_motion.last))
		physics_motion.last = car.position
	physics_frame.connect(monitor)
	for tick in 5000:
		if car.position.x > start + 430: break
		var look := 5.0 + absf(car.speed) * 0.16
		var tx := car.position.x + look
		var centers := world.terrain.road_centers(tx)
		var tz: float = centers[mini(lane_index, centers.size() - 1)]
		var error := wrapf(atan2(tz - car.position.z, look) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 4.0, 0, 1))
		Input.action_press("left", clampf(-error * 4.0, 0, 1))
		Input.action_press("throttle", 0.8 if car.speed < 20 else 0.08)
		var speed_before := car.speed
		await frame()
		max_step = maxf(max_step, previous.distance_to(car.position))
		max_camera = maxf(max_camera, old_camera.distance_to(game.camera.global_position))
		max_drop = maxf(max_drop, speed_before - car.speed)
		previous = car.position
		old_camera = game.camera.global_position
		stopped = stopped + 1 if absf(car.speed) < 0.1 else 0
		if car.position.x > next_report or car.boundary_recoveries > 0 or stopped > 120:
			print("BRANCH x=%.2f y=%.2f speed=%.2f recoveries=%d reason=%s choices=%s floor=%s worker=%d jobs=%d" % [car.position.x, car.position.y, car.speed, car.boundary_recoveries, car.stop_reason, world.terrain.route_choices, car.is_on_floor(), world._worker_index, world._jobs.size()])
			next_report += 48
		if car.boundary_recoveries > 0 or stopped > 120: break
	for action: String in ["throttle", "left", "right"]: Input.action_release(action)
	physics_frame.disconnect(monitor)
	failed = car.position.x < start + 420 or car.boundary_recoveries > 0 or max_drop > 2 or float(physics_motion.max) > 1 or max_camera > 8
	print("BRANCH RESULT %s x=%.2f physics_step=%.3f camera_step=%.3f max_speed_drop=%.3f impacts=%d" % ["FAIL" if failed else "PASS", car.position.x, float(physics_motion.max), max_camera, max_drop, car.obstacle_impacts])
	game.queue_free()
	await frame()
	quit(1 if failed else 0)
