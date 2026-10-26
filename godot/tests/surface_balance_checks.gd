extends SceneTree
## Surface variety, saved-rut migration and real worn-4x4 slope regressions.
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func frames(n: int) -> void:
	for tick in n:
		await physics_frame
		await process_frame
func _run() -> void:
	var starts: Dictionary = {}
	for offset in 6:
		var profile := DriveTerrain.new()
		profile.seed_value = DriveTerrain.VARIED_SEED_MIN + offset
		starts[profile.biome_at(0)] = true
		check(profile.biome_at(0) == offset, "Seed selects starting region %d" % offset)
	var seed := 24601
	var avoids_repeat := true
	var avoids_snow := true
	for i in 64:
		var next := DriveTerrain.new_journey_seed(seed)
		avoids_repeat = avoids_repeat and DriveTerrain.starting_biome(next) != DriveTerrain.starting_biome(seed)
		avoids_snow = avoids_snow and DriveTerrain.starting_biome(next) != 4
		seed = next
	check(avoids_repeat, "64 new rides avoid repeating the preceding starting biome")
	check(avoids_snow, "New rides never start in alpine snow without winter equipment")
	check(starts.size() == 6, "Explicit and saved seeds preserve all six original environments")
	var legacy_starts := true
	for legacy_seed in [1, 42, 24601, 999999, 1000000]:
		legacy_starts = legacy_starts and DriveTerrain.starting_biome(legacy_seed) == 0
	check(legacy_starts, "Existing journey seeds retain their original starting environment")
	var profile := DriveTerrain.new()
	var materials: Dictionary = {}
	for x in range(180, 5760, 18):
		for side: float in [-29, -24, -16, -10, 0, 10, 16, 24, 29]:
			var sample := profile.sample(x, profile.center_at(x) + side)
			materials[int(sample.material)] = true
	print("MATERIALS ", materials.keys())
	check(materials.size() == DriveTerrain.MATERIAL_NAMES.size() - 1 and not materials.has(DriveTerrain.SurfaceKind.CONCRETE), "All 13 expedition surfaces occur; artificial concrete road is replaced by gravel")
	var point := Vector3(80, 0, profile.center_at(80) + 12)
	var ordinary := profile.sample(point.x, point.z)
	var field := DriveDeformation.new()
	for i in 1800: field.load_soil(point, ordinary, 1.2, 0.8, 1.0, 1.0 / 60.0)
	check(field.depth_at(point.x, point.z) < 0.15, "Thirty seconds of wheelspin in ordinary sand cannot excavate a metre-deep trap")
	var peat := DriveDeformation.new()
	for i in 1800: peat.load_soil(Vector3.ZERO, {"soil_softness": 0.98, "soil_bearing": 0.48, "soil_depth": 0.9}, 1.2, 0.95, 1, 1.0 / 60)
	check(peat.depth_at(0, 0) > 0.7, "Exceptional peat remains a real deep-soil hazard")
	var maximum_rut := 0.0
	for x in range(130, 230):
		maximum_rut = maxf(maximum_rut, profile.authored_rut_depth(x, profile.center_at(x) + 1.15))
	check(maximum_rut > 0.075 and maximum_rut <= 0.121 and profile.authored_rut_depth(128, profile.center_at(128)) == 0, "Maintained-road wheel channels stay 8–12 cm deep with tapered entries")
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var world: DriveWorld = game.world
	world.restore_deformation([[80, floori(point.z), 1.0]])
	check(world.terrain.deformation.depth_at(80, floori(point.z)) > 0.01 and world.terrain.deformation.depth_at(80, floori(point.z)) <= 0.30, "Old metre-deep ordinary-sand saves retain shallow supporting-layer ruts")
	# Cross a bank with a worn starter, using real contacts, engine forces and
	# asynchronous compaction. No speed/position assignments during the drive.
	var car: DriveVehicle = game.vehicle
	DriveProfile.new().prepare_run(game.run)
	game.run.fuel = 30
	game.run.engine_running = true
	car.park()
	var x := 65.0
	var z := world.center_at(x) + 10.0
	car.position = Vector3(x, world.height_at(x, z) + 0.6, z)
	car.heading = 0
	car.reset_physics_interpolation()
	world.async_streaming = true
	# Isolate soil/slope performance from genuine rocks, fences and buildings.
	for chunk: Node3D in world.chunks.values():
		for node: Node in chunk.get_children():
			if node.name != "SandAndTracks": node.queue_free()
	await frames(20)
	var initial := car.position
	for tick in 600:
		var target_z := world.center_at(car.position.x + 5) + 10.0
		var error := wrapf(atan2(target_z - car.position.z, 5) - car.heading, -PI, PI)
		Input.action_press("throttle", 0.7)
		Input.action_press("right", clampf(error * 4, 0, 1))
		Input.action_press("left", clampf(-error * 4, 0, 1))
		await frames(1)
	print("4X4 BANK travel=%.2f sink=%.3f speed=%.2f reason=%s" % [car.position.distance_to(initial), car.sink_depth, car.speed, car.stop_reason])
	check(car.position.x - initial.x > 12 and car.sink_depth < 0.20 and car.boundary_recoveries == 0, "Worn 4x4 climbs out of ordinary sand with no rescue teleport")
	for action: String in ["throttle", "left", "right"]: Input.action_release(action)
	game.queue_free()
	await frames(2)
	print("SURFACE BALANCE: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
