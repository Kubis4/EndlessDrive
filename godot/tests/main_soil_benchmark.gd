extends SceneTree
## Uncapped native rendering of the production world, actual car, local soil
## workers and surface spray. Run alone; fixed 60 Hz simulation for comparison.
class ProfileCar extends DriveVehicle:
	var cpu_ms := 0.0
	var visual_ms := 0.0
	var step_ms := 0.0
	func _physics_process(dt: float) -> void:
		step_ms = 0.0
		var started := Time.get_ticks_usec()
		super._physics_process(dt)
		cpu_ms = (Time.get_ticks_usec() - started) / 1000.0
	func _update_visual(dt: float, slope: float) -> void:
		var started := Time.get_ticks_usec()
		super._update_visual(dt, slope)
		visual_ms = (Time.get_ticks_usec() - started) / 1000.0
	func _try_terrain_step(motion: Vector3) -> bool:
		var started := Time.get_ticks_usec()
		var ok := super._try_terrain_step(motion)
		step_ms = (Time.get_ticks_usec() - started) / 1000.0
		return ok
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var selected := ""
	var label := ""
	var live := false
	var cap := 0
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--case="): selected = arg.trim_prefix("--case=")
		if arg.begins_with("--label="): label = "-" + arg.trim_prefix("--label=")
		if arg.begins_with("--fps="): cap = arg.trim_prefix("--fps=").to_int()
		if arg == "--live": live = true
	root.size = Vector2i(1280, 720)
	DisplayServer.window_set_title("Endless Drive | Pacing QA")
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_ENABLED if "--vsync" in OS.get_cmdline_user_args() else DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = cap
	var game = load("res://scenes/main.tscn").instantiate()
	game.get_node("Vehicle").set_script(ProfileCar)
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.weather.set_process(false)
	game.hud.hide()
	var car: DriveVehicle = game.vehicle
	var world: DriveWorld = game.world
	var warmup := load("res://scripts/render_warmup.gd").new() as SubViewport
	warmup.set("environment_template", game.environment)
	warmup.set("vehicle_template", car._visual)
	root.add_child(warmup)
	for i in 72: await process_frame
	var camera_size: float = game.overview_size
	var results := {}
	for entry: Array in [["sand_road", 24601, 0.0], ["sand_offroad", 24601, 16.0], ["grass_offroad", 1000003, 10.0], ["snow_offroad", 1000005, -27.0], ["forest_road", 1000004, 0.0], ["swamp_road", 1000006, 0.0]]:
		if not selected.is_empty() and str(entry[0]) != selected: continue
		world.configure(int(entry[1]))
		world.hazards_enabled = false
		world.async_streaming = false
		for index in range(-1, 3): world._create_chunk(index)
		# Isolate the soil workload from random tree impacts. Keep all scenery
		# rendered and all terrain colliders active; obstacle driving has its
		# own tests with unmodified collision layers.
		for body: Node in world.find_children("*", "StaticBody3D", true, false):
			if str(body.get_parent().name) != "SandAndTracks": body.collision_layer = 0
		game.run.engine_running = true
		game.run.four_wheel_drive = true
		game.run.variants.tires = "allterrain"
		game.run.fuel = 40
		game.run.day_time = 0.42
		for key in game.run.parts: game.run.parts[key] = 1.0
		game._update_lighting()
		place(car, world, float(entry[2]))
		game._update_camera(1, true)
		game.effects.reset_particles()
		for tick in 100:
			steer(car, world, float(entry[2]))
			game._update_camera(game.get_process_delta_time(), false)
			await process_frame
		if paused or not car.controls_enabled:
			push_error("Benchmark was paused; discard this run")
			quit(2)
			return
		world.max_deformation_ms = 0.0
		var samples: Array[float] = []
		var physics_cpu: Array[float] = []
		var car_cpu: Array[float] = []
		var idle_cpu: Array[float] = []
		var visual_cpu: Array[float] = []
		var step_cpu: Array[float] = []
		if "--foreground" in OS.get_cmdline_user_args():
			print("WAITING FOR FOREGROUND QA WINDOW")
			while not DisplayServer.window_is_focused(): await process_frame
		if "--qa-delay" in OS.get_cmdline_user_args():
			print("VISIBLE QA STARTS IN 15 SECONDS")
			await create_timer(15.0).timeout
			place(car, world, float(entry[2]))
			game._update_camera(1, true)
		var focus_lost := false
		var time := Time.get_ticks_usec()
		var measured_start := Time.get_ticks_msec()
		var tick := 0
		while (Time.get_ticks_msec() - measured_start < 8000 if live else tick < 600):
			tick += 1
			focus_lost = focus_lost or not DisplayServer.window_is_focused()
			if car.position.x > 118.0: place(car, world, float(entry[2]))
			steer(car, world, float(entry[2]))
			game._update_camera(game.get_process_delta_time(), false)
			await process_frame
			var now := Time.get_ticks_usec()
			samples.append((now - time) / 1000.0)
			physics_cpu.append(Performance.get_monitor(Performance.TIME_PHYSICS_PROCESS) * 1000.0)
			car_cpu.append((car as ProfileCar).cpu_ms)
			visual_cpu.append((car as ProfileCar).visual_ms)
			step_cpu.append((car as ProfileCar).step_ms)
			idle_cpu.append(Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0)
			time = now
		for key in ["throttle", "right", "left"]: Input.action_release(key)
		if paused or world.terrain.deformation.cells.is_empty():
			push_error("Benchmark did not exercise active soil; discard this run")
			quit(2)
			return
		var total := 0.0
		for ms in samples: total += ms
		samples.sort()
		physics_cpu.sort()
		car_cpu.sort()
		idle_cpu.sort()
		visual_cpu.sort()
		step_cpu.sort()
		results[entry[0]] = {"fps": samples.size() * 1000.0 / total, "p95_ms": samples[mini(samples.size() - 1, int(samples.size() * 0.95))], "p99_ms": samples[mini(samples.size() - 1, int(samples.size() * 0.99))], "frames_over_20_ms": samples.filter(func(ms: float) -> bool: return ms > 20.0).size(), "frames_over_33_ms": samples.filter(func(ms: float) -> bool: return ms > 33.34).size(), "samples": samples.size(), "max_ms": samples[-1], "physics_max_ms": physics_cpu[-1], "car_max_ms": car_cpu[-1], "visual_max_ms": visual_cpu[-1], "step_max_ms": step_cpu[-1], "terrain_steps": car.terrain_steps, "step_rejections": car.terrain_step_rejections, "idle_max_ms": idle_cpu[-1], "soil_commit_max_ms": world.max_deformation_ms, "mesh_upload_ms": world.last_rut_mesh_ms, "collision_upload_ms": world.last_rut_collision_ms, "last_worker_ms": world.last_detail_worker_ms, "soil_cells": world.terrain.deformation.cells.size(), "pending_bands": world._rut_dirty.size(), "draw_calls": Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME), "rescues": car.boundary_recoveries, "camera_size": camera_size, "focus_lost": focus_lost, "refresh_hz": DisplayServer.screen_get_refresh_rate(), "prop_collisions": false, "speed": car.speed, "max_depth": world.terrain.deformation.cells.values().max() if not world.terrain.deformation.cells.is_empty() else 0.0}
		print("MAIN SOIL ", entry[0], " ", JSON.stringify(results[entry[0]]))
		if DisplayServer.get_name() != "headless":
			game.overview_size = camera_size
			game._update_camera(game.get_process_delta_time(), false)
			await process_frame
			await RenderingServer.frame_post_draw
			root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/soil-%s.png" % entry[0]))
			game.overview_size = camera_size
	var file := FileAccess.open("res://../build/main-soil-benchmark%s.json" % label, FileAccess.WRITE)
	file.store_string(JSON.stringify(results, "\t"))
	file.close()
	game.queue_free()
	await process_frame
	quit()
func place(car: DriveVehicle, world: DriveWorld, offset: float) -> void:
	car.park()
	car.position = Vector3(30.0, 0, world.center_at(30.0) + offset)
	car.position.y = world.height_at(car.position.x, car.position.z) + 0.6
	car.heading = atan((world.center_at(31.0) - world.center_at(29.0)) * 0.5)
	car.reset_physics_interpolation()
func steer(car: DriveVehicle, world: DriveWorld, offset: float) -> void:
	var target := Vector2(car.position.x + 6.0, world.center_at(car.position.x + 6.0) + offset)
	var error := wrapf((target - Vector2(car.position.x, car.position.z)).angle() - car.heading, -PI, PI)
	Input.action_press("right", clampf(error * 4.0, 0.0, 1.0))
	Input.action_press("left", clampf(-error * 4.0, 0.0, 1.0))
	Input.action_press("throttle", 0.7 if car.speed < 6.0 else 0.10)
