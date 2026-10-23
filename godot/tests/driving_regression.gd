extends SceneTree
## Standalone physics regression: --headless --fixed-fps 60 --path godot
## --script res://tests/driving_regression.gd -- --test-session [--baseline]
## Owns no main scene and never calls DriveSave or accesses user://.

const ROUTE_END := 4600.0

var world: DriveWorld
var car: DriveVehicle
var state: DriveRun
var failures := 0
var checks := 0
var baseline := false
var telemetry: FileAccess
var ticks := 0


class FlatTerrain extends DriveTerrain:
	var friction := 1.0
	func sample(_x: float, _z: float, _include_water: bool = true) -> Dictionary:
		return {"road": 1.0, "grip": friction, "drag": 0.0, "roughness": 0.0, "paved": 1.0, "label": "Asfalt", "water": 0.0}


class FlatWorld extends DriveWorld:
	var grade := 0.0
	func _ready() -> void: pass
	func height_at(x: float, _z: float) -> float: return x * grade
	func center_at(_x: float) -> float: return 0.0



func _initialize() -> void:
	if not "--test-session" in OS.get_cmdline_user_args():
		printerr("driving_regression requires --test-session (no player save is opened)")
		quit(2)
		return
	baseline = "--baseline" in OS.get_cmdline_user_args()
	Engine.physics_ticks_per_second = 60
	call_deferred("_run")


func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", message])


func frame() -> void:
	await physics_frame
	await process_frame
	ticks += 1


func _run() -> void:
	# Concurrent integration failures must terminate instead of hanging a CI job.
	if not (load("res://scripts/run_state.gd") as Script).can_instantiate():
		printerr("DriveRun failed to compile; driving regression cannot start")
		quit(2)
		return
	for action: String in ["throttle", "brake", "left", "right"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	var folder := ProjectSettings.globalize_path("res://../build/godot-tests")
	DirAccess.make_dir_recursive_absolute(folder)
	var path := folder.path_join("driving-%s-%d.csv" % ["baseline" if baseline else "regression", OS.get_process_id()])
	telemetry = FileAccess.open(path, FileAccess.WRITE)
	if telemetry == null:
		printerr("Cannot create regression telemetry: " + path)
		quit(2)
		return
	telemetry.store_csv_line(["phase", "tick", "x", "y", "z", "speed", "actual_mps", "cruise", "floor", "wall", "water", "recoveries", "collision_normals", "stop_reason"])
	print("TELEMETRY " + path)
	world = DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	world.update_stream(0.0)
	state = DriveRun.new()
	state.fuel = 40.0
	state.engine_running = true
	state.parts.engine = 1.0
	state.parts.tires = 1.0
	car = DriveVehicle.new()
	car.run = state
	car.world = world
	car.position = Vector3(0, world.height_at(0, world.center_at(0)) + 0.6, world.center_at(0))
	root.add_child(car)
	var args := OS.get_cmdline_user_args()
	check(car._lights.all(func(light: SpotLight3D) -> bool: return not light.visible), "Headlights are off immediately on creation, before the first physics tick")
	if not "--brakes-only" in args: await _route()
	if not baseline:
		if not "--brakes-only" in args:
			if not "--route-only" in args:
				await _obstacles()
				await _ford()
		car.queue_free()
		world.queue_free()
		await process_frame
		await _braking()
	telemetry.close()
	car.queue_free()
	world.queue_free()
	await process_frame
	print("DRIVING RESULT %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)


func lane(x: float) -> float:
	# Upper branch deliberately traverses potholes; first pool and later wetlands
	# are crossed on the shared road. Separate lower-ford case is added below.
	return world.terrain.road_centers(x)[0]


func _route() -> void:
	var travelled := 0.0
	var previous := car.position
	var stuck := 0
	var resets := 0
	var cruise_losses := 0
	var max_water := 0.0
	var max_step_drop := 0.0
	var last_speed := 0.0
	var next_report := 96.0
	var last_chunk := -999
	var abrupt_drops := 0
	var cruise_started := false
	var snow_ticks := 0
	var cruise_ticks := 0
	var released_pedal_ticks := 0
	var max_height := -INF
	var min_height := INF
	for iteration in 45000:
		var x := car.position.x
		if x >= ROUTE_END: break
		var chunk := int(floor(x / 96.0))
		if chunk != last_chunk:
			world.update_stream(x)
			last_chunk = chunk
		var lookahead := 4.0 + absf(car.speed) * 0.12
		var target_x := x + lookahead
		var desired := atan2(lane(target_x) - car.position.z, lookahead)
		var error := wrapf(desired - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 5.0, 0.0, 1.0))
		Input.action_press("left", clampf(-error * 5.0, 0.0, 1.0))
		# A route governor exercises real pedals and steering; cruise remains engaged
		# under analogue throttle overrides, with its captured setting restored downhill.
		if car.speed < 0.6 or not car.cruise_enabled:
			if cruise_started and not car.cruise_enabled: cruise_losses += 1
			Input.action_press("throttle", 1.0)
			if car.speed > 1.0:
				Input.action_press("throttle", 0.12)
				car.toggle_cruise()
				cruise_started = true
		elif car.speed < 9.0:
			Input.action_press("throttle", 1.0)
		else:
			Input.action_release("throttle")
		await frame()
		var movement := car.position.distance_to(previous)
		travelled += movement
		previous = car.position
		max_water = maxf(max_water, float(car.surface_info.get("water", 0.0)))
		if str(car.surface_info.get("label", "")).begins_with("Sneh"): snow_ticks += 1
		if car.cruise_enabled: cruise_ticks += 1
		if car.cruise_enabled and Input.get_action_strength("throttle") < 0.02: released_pedal_ticks += 1
		max_height = maxf(max_height, car.position.y)
		min_height = minf(min_height, car.position.y)
		max_step_drop = maxf(max_step_drop, last_speed - car.speed)
		var normals: Array[String] = []
		for i in car.get_slide_collision_count():
			var hit := car.get_slide_collision(i)
			normals.append("%s:%s" % [hit.get_collider().get_path(), hit.get_normal()])
		if ticks % 60 == 0 or last_speed - car.speed > 1.0:
			_log_tick("route", movement, "|".join(normals))
		if last_speed - car.speed > 1.0:
			abrupt_drops += 1
			print("DROP x=%.3f speed=%.2f->%.2f floor=%s wall=%s recovery=%d hits=%s" % [car.position.x, last_speed, car.speed, car.is_on_floor(), car.is_on_wall(), car.boundary_recoveries, "|".join(normals)])
		last_speed = car.speed
		stuck = stuck + 1 if movement < 0.005 else 0
		if stuck > 180:
			print("STUCK x=%.3f speed=%.2f cruise=%s hits=%s" % [x, car.speed, car.cruise_enabled, "|".join(normals)])
			resets += 1
			if not baseline: break
			car.park()
			var rx := x + 5.0
			car.position = Vector3(rx, world.height_at(rx, lane(rx)) + 0.65, lane(rx))
			car.heading = atan((lane(rx + 1) - lane(rx - 1)) * 0.5)
			previous = car.position
			stuck = 0
		if x >= next_report:
			print("ROUTE x=%.1f travelled=%.1f speed=%.2f recoveries=%d resets=%d water=%.2f" % [x, travelled, car.speed, car.boundary_recoveries, resets, max_water])
			next_report += 96.0
	for action: String in ["throttle", "brake", "left", "right"]: Input.action_release(action)
	check(car.position.x >= ROUTE_END and travelled >= ROUTE_END, "Physically follows curves, potholes, water and >=47 streamed seams over 4.6 km")
	check(resets == 0 and car.boundary_recoveries == 0, "Route requires no unsticking or boundary recovery")
	check(max_water > 0.30, "Route physically enters water")
	check(snow_ticks > 60 and max_height - min_height > 15.0, "Route physically crosses snowy roads and real hills (no position/velocity assignments during the route)")
	check(cruise_ticks > 15000 and released_pedal_ticks > 3000, "Cruise stays engaged for >250 seconds, including >50 seconds with the throttle pedal released")
	check(abrupt_drops == 0 and cruise_losses == 0, "No abrupt (>1 m/s per tick) loss or unexplained cruise cancellation")
	print("ROUTE SUMMARY metres=%.2f x=%.2f max_drop_per_tick=%.3f resets=%d cruise_losses=%d abrupt_drops=%d terrain_steps=%s snow_seconds=%.2f cruise_seconds=%.2f released_pedal_seconds=%.2f height_span=%.2f" % [travelled, car.position.x, max_step_drop, resets, cruise_losses, abrupt_drops, str(car.get("terrain_steps")), snow_ticks / 60.0, cruise_ticks / 60.0, released_pedal_ticks / 60.0, max_height - min_height])


func _place(x: float, z: float, heading: float = 0.0, height: float = 0.6) -> void:
	for action: String in ["throttle", "brake", "left", "right"]: Input.action_release(action)
	car.park()
	state.engine_running = false
	car.position = Vector3(x, world.height_at(x, z) + height, z)
	car.heading = heading
	car.wheel_angle = 0.0
	for i in 20: await frame()


func _obstacles() -> void:
	world.update_stream(0)
	await frame()
	var bpos: Vector3 = world.buildings[0].position
	await _place(bpos.x - 17.0, bpos.z)
	state.parts.engine = 1.0
	state.parts.suspension = 1.0
	state.engine_running = true
	car.speed = 18.0
	Input.action_press("throttle", 0.12)
	car.toggle_cruise()
	Input.action_release("throttle")
	var impacts: int = car.get("obstacle_impacts")
	for i in 100: await frame()
	check(car.position.x < bpos.x - 4.9 and car.speed < 0.5, "Workshop wall physically stops the rounded chassis")
	check(float(state.parts.suspension) < 0.98 and int(car.get("obstacle_impacts")) > impacts and not car.cruise_enabled, "Actual workshop impact damages suspension and cancels cruise")
	car.boundary_recovery_enabled = false
	var recoveries := car.boundary_recoveries
	for x: float in [96.0, 192.0, 480.0]:
		world.update_stream(x)
		await frame()
		for side: float in [-1.0, 1.0]:
			var z := world.terrain.corridor_center(x) + side * (world.terrain.boundary_width(x) - 12.0)
			await _place(x, z, side * PI * 0.5)
			car.speed = 42.0
			state.parts.suspension = 1.0
			impacts = car.get("obstacle_impacts")
			for i in 90: await frame()
			var offset := absf(car.position.z - world.terrain.corridor_center(car.position.x))
			check(offset < world.terrain.boundary_width(car.position.x) - 3.0 and int(car.get("obstacle_impacts")) > impacts and float(state.parts.suspension) < 0.98,
				"Real boundary rock/wall blocks 42 m/s impact, x=%.0f side=%d, with recovery disabled" % [x, int(side)])
	check(car.boundary_recoveries == recoveries, "Wall proofs used zero recovery teleports")
	car.boundary_recovery_enabled = true


func _ford() -> void:
	world.update_stream(430)
	await frame()
	var z := world.terrain.road_centers(430)[1]
	var heading := atan((world.terrain.road_centers(431)[1] - world.terrain.road_centers(429)[1]) * 0.5)
	await _place(430, z, heading)
	state.engine_running = true
	state.parts.engine = 1.0
	state.parts.tires = 1.0
	var max_water := 0.0
	var recoveries := car.boundary_recoveries
	for i in 2400:
		if car.position.x > 550.0: break
		var target_x := minf(599.0, car.position.x + 5.0)
		var target_z := world.terrain.road_centers(target_x)[1]
		var error := wrapf(atan2(target_z - car.position.z, target_x - car.position.x) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 5.0, 0.0, 1.0))
		Input.action_press("left", clampf(-error * 5.0, 0.0, 1.0))
		Input.action_press("throttle", 1.0 if car.speed < 9.0 else 0.12)
		await frame()
		max_water = maxf(max_water, float(car.surface_info.get("water", 0)))
	check(car.position.x > 550.0 and max_water > 0.35 and car.boundary_recoveries == recoveries, "Real lower-fork ford is crossed through deep water without recovery")


func _braking() -> void:
	world = FlatWorld.new()
	world.terrain = FlatTerrain.new()
	root.add_child(world)
	var floor_body := StaticBody3D.new()
	var collision := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(1600.0, 0.5, 200.0)
	collision.shape = box
	collision.position.y = -0.25
	floor_body.add_child(collision)
	world.add_child(floor_body)
	state = DriveRun.new()
	car = DriveVehicle.new()
	car.world = world
	car.run = state
	car.boundary_recovery_enabled = false
	root.add_child(car)
	var results: Dictionary = {}
	for kind: String in ["drum", "disc", "vented"]:
		results[kind] = []
		for speed: float in [10.0, 20.0, 30.0]:
			var result := await _stop(kind, speed)
			results[kind].append(result.distance)
			check(result.stopped and result.distance > speed * 0.55 and result.first_deceleration < result.peak_deceleration - 0.5,
				"%s brakes from %.0f m/s: %.2f m in %.2f s, progressive pressure (%.2f -> %.2f m/s²)" % [kind, speed, result.distance, result.seconds, result.first_deceleration, result.peak_deceleration])
		check(results[kind][2] > results[kind][1] * 1.4 and results[kind][1] > results[kind][0] * 2.0, kind + " stopping distance increases with speed")
	for i in 3:
		check(results.drum[i] > results.disc[i] * 1.10 and results.disc[i] > results.vented[i] * 1.05, "Brake hardware changes measured stopping distance at %.0f m/s" % ([10, 20, 30][i]))
	var worn := await _stop("disc", 20.0, 0.25)
	var loaded := await _stop("disc", 20.0, 1.0, true)
	var slippery := await _stop("vented", 20.0, 1.0, false, 0.25)
	var partial := await _stop("disc", 20.0, 1.0, false, 1.0, 0.35)
	check(worn.distance > results.disc[1] * 1.35, "Worn brakes lengthen real stopping distance: %.2f m" % worn.distance)
	check(loaded.distance > results.disc[1] * 1.05, "Cargo mass lengthens real stopping distance: %.2f m" % loaded.distance)
	check(slippery.distance > results.vented[1] * 1.5, "Tyre/surface grip limits actual braking: %.2f m" % slippery.distance)
	check(partial.distance > results.disc[1] * 1.5, "Partial pedal produces gentler braking: %.2f m" % partial.distance)
	await _combined_pedals()
	await _reverse()
	await _brake_hold(floor_body)
	await _terrain_edges()
	await _recovery()


func _stop(kind: String, start_speed: float, condition: float = 1.0, loaded: bool = false, friction: float = 1.0, pedal: float = 1.0) -> Dictionary:
	await _place(0, 0)
	state.brake_type = kind
	state.parts.brakes = condition
	state.parts.tires = 1.0
	state.inventory.fuel = 230.0 / 0.75 if loaded else 0.0
	state.inventory.oil = 0.0
	state.inventory.coolant = 0.0
	world.terrain.set("friction", friction)
	car.speed = start_speed
	Input.action_press("brake", pedal)
	var start := car.position.x
	var initial := 0.0
	var peak := 0.0
	var frames := 0
	for i in 3000:
		var before := car.speed
		var before_pose := car.position
		await frame()
		if i % 6 == 0: _log_tick("brake_" + kind + "_" + str(start_speed), car.position.distance_to(before_pose))
		frames += 1
		var deceleration := (before - car.speed) * 60.0
		if i == 0: initial = deceleration
		peak = maxf(peak, deceleration)
		if car.speed < 0.08: break
	Input.action_release("brake")
	return {"distance": car.position.x - start, "seconds": frames / 60.0, "stopped": car.speed < 0.08, "first_deceleration": initial, "peak_deceleration": peak}


func _log_tick(phase: String, movement: float, normals: String = "") -> void:
	telemetry.store_csv_line([phase, str(ticks), str(car.position.x), str(car.position.y), str(car.position.z), str(car.speed), str(movement * 60.0), str(car.cruise_enabled), str(car.is_on_floor()), str(car.is_on_wall()), str(car.surface_info.get("water", 0)), str(car.boundary_recoveries), normals, str(car.get("stop_reason"))])


func _reverse() -> void:
	world.terrain.set("friction", 1.0)
	state.parts.engine = 1.0
	state.parts.tires = 1.0
	await _place(0, 0)
	state.engine_running = true
	state.fuel = 40.0
	state.distance = 500.0
	Input.action_press("brake", 1.0)
	for i in 850:
		await frame()
		if car.position.x < -160.0: break
	Input.action_release("brake")
	check(car.position.x < -160.0 and car.speed < -1.0 and state.distance == 500.0, "Reverse travels >160 m behind the odometer without an artificial x clamp")


func _combined_pedals() -> void:
	world.terrain.set("friction", 1.0)
	await _place(0, 0)
	state.parts.engine = 1.0
	state.parts.brakes = 1.0
	state.parts.tires = 1.0
	state.brake_type = "drum"
	state.fuel = 40.0
	state.engine_running = true
	car.speed = 20.0
	Input.action_press("throttle", 1.0)
	car.toggle_cruise()
	Input.action_press("brake", 1.0)
	var start := car.position.x
	var priority := true
	var peak := 0.0
	var stopped := false
	for i in 900:
		var before := car.speed
		await frame()
		priority = priority and car.throttle_input == 0.0 and not car.cruise_enabled
		peak = maxf(peak, (before - car.speed) * 60.0)
		if car.speed == 0.0:
			stopped = true
			break
		if car.speed < -0.001: break
	check(stopped and priority and car.position.x - start > 20.0 and peak < 15.0,
		"W + S / full throttle + brake progressively stops from 20 m/s, suppresses drive torque and cancels cruise (%.2f m)" % (car.position.x - start))
	var held := true
	for i in 24:
		await frame()
		held = held and absf(car.speed) < 0.08
	check(held, "Combined pedals hold the stopped car for at least 0.40 s before reverse")
	for i in 10: await frame()
	check(car.speed < -0.3 and car.throttle_input == 0.0, "Combined pedals engage reverse only after the stationary 0.45 s delay")
	Input.action_release("throttle")
	Input.action_release("brake")


func _brake_hold(floor_body: StaticBody3D) -> void:
	world.set("grade", 0.15)
	floor_body.rotation.z = atan(0.15)
	await _place(0, 0)
	car.park()
	state.engine_running = true
	Input.action_press("brake", 1.0)
	var start := car.position
	var held := true
	for i in 24:
		await frame()
		held = held and absf(car.speed) < 0.08
	check(held and car.position.distance_to(start) < 0.08, "Brake holds a real 15% collidable slope during the reverse delay")
	for i in 15: await frame()
	check(car.speed < -0.3, "Holding brake on the slope engages reverse after 0.45 s")
	Input.action_release("brake")
	car.park()
	world.set("grade", 0.0)
	floor_body.rotation.z = 0.0
	await frame()


func _terrain_edges() -> void:
	# Same concave collision type and classification as the streamed road.
	# The small ledge must pass; a tall terrain face must remain a solid impact.
	for height: float in [0.18, 3.0]:
		var surface := SurfaceTool.new()
		surface.begin(Mesh.PRIMITIVE_TRIANGLES)
		var corners: Array[Vector3] = [Vector3(10, height, -6), Vector3(40, height, -6), Vector3(10, height, 6), Vector3(40, height, 6)]
		for index: int in [0, 1, 2, 1, 3, 2]: surface.add_vertex(corners[index])
		corners = [Vector3(10, 0, -6), Vector3(10, 0, 6), Vector3(10, height, -6), Vector3(10, height, 6)]
		# Clockwise face looking from the approaching car (-x).
		for index: int in [0, 2, 1, 1, 2, 3]: surface.add_vertex(corners[index])
		var mesh := MeshInstance3D.new()
		mesh.name = "SandAndTracks"
		mesh.mesh = surface.commit()
		world.add_child(mesh)
		mesh.create_trimesh_collision()
		await _place(0, 0)
		state.parts.suspension = 1.0
		var impacts: int = car.get("obstacle_impacts")
		car.speed = 12.0
		for i in 120: await frame()
		if height < 1.0:
			check(car.position.x > 13.0 and car.position.y > height + 0.3 and int(car.get("obstacle_impacts")) == impacts, "Rounded chassis traverses a physical 18 cm concave terrain seam")
		else:
			check(car.position.x < 8.0 and int(car.get("obstacle_impacts")) > impacts and float(state.parts.suspension) < 0.99,
				"Tall concave terrain wall is blocked and damages suspension; terrain is not ignored")
		mesh.queue_free()
		await frame()


func _recovery() -> void:
	await _place(0, 0)
	car.boundary_recovery_enabled = true
	state.engine_running = true
	car.speed = 8.0
	Input.action_press("throttle", 0.6)
	car.toggle_cruise()
	Input.action_release("throttle")
	car.position.z = 200.0
	car._ensure_safe_position()
	check(car.speed == 8.0 and car.cruise_enabled and absf(car.position.z) <= 30.0, "Emergency lateral recovery preserves driving speed and cruise instead of parking")
	car.position.y = -500.0
	car._ensure_safe_position()
	check(car.speed == 0.0 and not car.cruise_enabled and car.position.y >= 0.0 and car.get("stop_reason") == "fallen_recovery", "Genuinely fallen vehicle recovers and reports the reason for parking")
