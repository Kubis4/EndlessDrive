extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func frames(count := 2) -> void:
	for i in count:
		await physics_frame
		await process_frame
func check(ok: bool, title: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func _run() -> void:
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.vehicle.position = Vector3(30, game.world.height_at(30, 16) + 0.6, 16)
	game.vehicle.reset_physics_interpolation()
	await frames(3)
	game._update_camera(1, true)
	var before: Vector3 = game.camera.position
	game.world._terrain_visibility_probe_ms = Time.get_ticks_msec() + 10000
	game.world._terrain_blocks_road = true
	game._update_camera(1.0, true)
	check(game._terrain_camera_lift > 0.99 and game.camera.position.y > before.y + 45.0, "Blocked mountain view raises the camera without cutting terrain")
	game.world._terrain_blocks_road = false
	game._update_camera(1.0, true)
	game.world._terrain_visibility_probe_ms = 0
	var point: Vector3 = game.vehicle.position + Vector3(5, 0, 0)
	for i in 40: game.world.terrain.deformation.compact(point, {"paved": 0.0, "deformable": true})
	game._update_camera(1.0 / 60.0)
	check(game.camera.position.distance_to(before) < 0.0001, "New tyre ruts cannot kick the camera's terrain-grade offset")
	game._camera_grade_offset = 4.0
	game._update_camera(1.0 / 60.0)
	check(absf(float(game._camera_grade_offset) - 4.0) < 0.3, "Real slope corrections approach smoothly instead of snapping")
	var world: DriveWorld = game.world
	point = Vector3(50, 0, world.center_at(50) + 16)
	for i in 25: world.compact_tyres([point], {}, 1.0)
	check(world.terrain.deformation.depth_at(point.x, point.z) == 0.0 and not world._soil_commands.is_empty(), "Wheel stamping queues numeric work without blocking the vehicle physics tick")
	var start := Time.get_ticks_msec()
	while (world.terrain.deformation.depth_at(point.x, point.z) < 0.04 or world._rut_worker != null or not world._rut_ready.is_empty()) and Time.get_ticks_msec() - start < 4000:
		await frames()
	check(world.terrain.deformation.depth_at(point.x, point.z) > 0.04 and world.last_refined_quads > 0, "Worker results publish real soil and fine terrain back to the live world")
	var old := DriveDeformation.new()
	for i in DriveDeformation.MAX_CELLS: old._store(Vector2i(i, 0), 0.02)
	var snapshot := DriveTerrain.new()
	snapshot.deformation.cells = old.cells.duplicate()
	snapshot.deformation._order = old._order.duplicate()
	var commands: Array[Dictionary] = [{"kind": 0, "start": Vector3(-20, 0, 0), "end": Vector3(-20, 0, 0), "pressure": 2.0, "direction": Vector2.RIGHT, "shear": 0.5}]
	var no_jobs: Array[Dictionary] = []
	var result := preload("res://scripts/terrain_detail.gd").apply_commands(snapshot, commands, no_jobs)
	for key: Vector2i in result.changes: old._store(key, float(result.changes[key]))
	check(old.cells == snapshot.deformation.cells and old._order == snapshot.deformation._order, "Publishing a delta preserves bounded FIFO history even when the soil pool is full")
	world.set_process(false)
	world.flush_soil_updates()
	world.terrain.deformation = old
	game._ephemeral = false
	game.run.fuel = 12.0
	var save_path := "user://pacing-async-test.json"
	var save_start := Time.get_ticks_usec()
	game._auto_save(save_path)
	print("AUTOSAVE main snapshot ms=", (Time.get_ticks_usec() - save_start) / 1000.0)
	check(game._save_worker != null, "Automatic saving dispatches serialization and disk work to a private worker")
	game.run.fuel = 9.0
	old._store(Vector2i(-2000, -2000), 0.4)
	var pumped := 0
	while game._save_worker.is_alive():
		pumped += 1
		await frames()
	game._finish_auto_save(true)
	var saved := DriveSave.restore(save_path)
	check(pumped > 0 and not saved.is_empty() and float(saved.fuel) == 12.0, "Frames continue while a complete, internally consistent journey is saved")
	var saved_soil := DriveDeformation.new()
	saved_soil.restore(saved.get("terrain_deformation", []))
	var max_save_error := 0.0
	for key: Vector2i in snapshot.deformation.cells:
		max_save_error = maxf(max_save_error, absf(float(saved_soil.cells.get(key, -1)) - float(snapshot.deformation.cells[key])))
	check(saved_soil.cells.size() == snapshot.deformation.cells.size() and max_save_error <= 0.000051 and saved_soil.depth_at(-500, -500) == 0.0, "Autosave soil snapshot stays isolated from later live tyre changes within save precision")
	check(game._save_worker == null, "Completed save workers are joined before a new save or journey can replace their file")
	game._ephemeral = true
	world.configure(24601)
	check(world._soil_commands.is_empty() and world._rut_ready.is_empty() and world.terrain.deformation.cells.is_empty(), "Restart discards pending work from the previous world")
	game.queue_free()
	await frames()
	print("FRAME PACING CHECKS failures=", failures)
	quit(1 if failures else 0)
