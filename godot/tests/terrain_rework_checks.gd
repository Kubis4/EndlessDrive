extends SceneTree
## Physical regressions for the reported water trap, rutting and swept hazards.
var checks := 0
var failures := 0

func _initialize() -> void: call_deferred("_run")

func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])

func frames(count: int) -> void:
	for i in count:
		await physics_frame
		await process_frame

func ground(world: DriveWorld, point: Vector3, exclude: RID) -> Dictionary:
	var query := PhysicsRayQueryParameters3D.create(point + Vector3.UP * 4, point - Vector3.UP * 4)
	query.exclude = [exclude]
	return world.get_world_3d().direct_space_state.intersect_ray(query)

func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await frames(4)
	var world: DriveWorld = game.world
	var car: DriveVehicle = game.vehicle
	var profile := world.terrain
	check(ProjectSettings.get_setting("physics/common/physics_interpolation", false) and car.physics_interpolation_mode == Node.PHYSICS_INTERPOLATION_MODE_ON and game.camera.physics_interpolation_mode == Node.PHYSICS_INTERPOLATION_MODE_OFF, "Vehicle interpolates physics while camera follows the displayed pose")
	var smooth := true
	for side in [-1, 1]:
		profile.route_choices = {"0": side}
		profile.clear_caches()
		var a := profile.sample(599.99, profile.center_at(599.99))
		var b := profile.sample(600.01, profile.center_at(600.01))
		smooth = smooth and (Vector3(a.color.r, a.color.g, a.color.b) - Vector3(b.color.r, b.color.g, b.color.b)).length() < 0.01 and absf(a.paved - b.paved) < 0.01
	check(smooth, "Both fork exits blend colour and road material without a hard switch")
	world.configure(24601)
	world.update_stream(480)
	car.park()
	car.position = Vector3(480, world.height_at(480, profile.center_at(480)) + 8, profile.center_at(480))
	await frames(3)
	var lanes := profile.road_centers(480)
	var max_mesh_error := 0.0
	for x: float in [466, 478, 480, 492]:
		for z: float in profile.road_centers(x):
			var hit := ground(world, Vector3(x, world.height_at(x, z), z), car.get_rid())
			max_mesh_error = maxf(max_mesh_error, absf(hit.position.y - world.height_at(x, z)) if not hit.is_empty() else INF)
	check(max_mesh_error < 0.15, "Both fork lanes have detailed physical geometry, including the formerly coarse lower ford")
	# Restart from a dead stop in water with genuinely worn starter components.
	for start_x: float in [207, 480, 840, 1080, 3960]:
		world.update_stream(start_x)
		car.park()
		DriveProfile.new().prepare_run(game.run)
		game.run.fuel = 20
		game.run.engine_running = true
		var branch := profile.road_centers(start_x).size() - 1
		var z := profile.road_centers(start_x)[branch]
		car.position = Vector3(start_x, world.height_at(start_x, z) + 0.6, z)
		car.heading = atan((profile.road_centers(start_x + 1)[branch] - profile.road_centers(start_x - 1)[branch]) * 0.5)
		car.reset_physics_interpolation()
		await frames(8)
		var initial := car.position
		var recoveries := car.boundary_recoveries
		Input.action_press("throttle")
		for tick in 900:
			var x := car.position.x
			if x > start_x + 22: break
			var target_x := x + 5
			var target_z := profile.road_centers(target_x)[-1]
			var error := wrapf(atan2(target_z - car.position.z, 5.0) - car.heading, -PI, PI)
			Input.action_press("right", clampf(error * 4, 0, 1))
			Input.action_press("left", clampf(-error * 4, 0, 1))
			await frames(1)
		Input.action_release("throttle")
		Input.action_release("right")
		Input.action_release("left")
		print("FORD restart x=%.0f travelled=%.2f speed=%.2f reason=%s contacts=%.3f" % [start_x, car.position.x - initial.x, car.speed, car.stop_reason, car.max_wheel_contact_error])
		check(car.position.x > start_x + 20 and car.boundary_recoveries == recoveries, "Worn starter restarts and leaves water at x=%.0f without a teleport" % start_x)
		check(car.max_wheel_contact_error < 0.20, "Wheel suspension follows actual collision at x=%.0f" % start_x)
	# Prove physical and visual vertex displacement, not just a painted skidmark.
	car.park()
	car.controls_enabled = false
	car.position = Vector3(-50, 20, 0)
	world.configure(24601)
	world.update_stream(0)
	await frames(3)
	var chunk: Node3D = world.chunks[0]
	var arrays: Array = chunk.get_meta("terrain_arrays")
	var width := int(chunk.get_meta("terrain_width"))
	var index := 5 * width + 51
	var p: Vector3 = arrays[Mesh.ARRAY_VERTEX][index]
	var before := ground(world, p, car.get_rid())
	for pass_index in 30: world.compact_tyres([p], profile.sample(p.x, p.z), 1.0)
	world._update_rut_band(chunk, 0)
	await frames(3)
	var after := ground(world, p, car.get_rid())
	var vertex: Vector3 = (chunk.get_meta("terrain_arrays") as Array)[Mesh.ARRAY_VERTEX][index]
	check(not before.is_empty() and not after.is_empty() and before.position.y - after.position.y > 0.03 and absf(vertex.y - after.position.y) < 0.005, "Tyre compaction lowers BOTH rendered vertices and physical collision")
	check(profile.sample(p.x, p.z).rut_depth > 0.03, "Ruts affect the sampled driving surface and resistance")
	var field := DriveDeformation.new()
	for i in 1500: field.compact(Vector3(i * 3, 0, 0), {"paved": 0.0, "snow": 1.0}, 1.0)
	check(field.cells.size() <= DriveDeformation.MAX_CELLS, "Physical soil history stays bounded on endless routes")
	game.run.terrain_deformation = profile.deformation.to_data()
	var restored := DriveRun.from_data(game.run.to_data())
	check(restored != null and restored.terrain_deformation == game.run.terrain_deformation, "Recent physical ruts survive journey serialization")
	var legacy: Dictionary = game.run.to_data()
	legacy.version = 4
	legacy.erase("terrain_deformation")
	check(DriveRun.from_data(legacy) != null, "Pre-deformation version-4 journeys still load")
	# A true arched, curve-following shell with an open centre aisle.
	world.update_stream(2664)
	car.position = Vector3(2664, world.height_at(2664, world.center_at(2664)) + 8, world.center_at(2664))
	var tunnel := RouteHazard.new()
	tunnel.world = world
	tunnel.kind = "tunnel"
	tunnel.feature_x = 2664
	world.add_child(tunnel)
	tunnel.build()
	await frames(2)
	var arch := tunnel.get_node("TunnelFarArch") as MeshInstance3D
	var vertices: PackedVector3Array = arch.mesh.surface_get_arrays(0)[Mesh.ARRAY_VERTEX]
	var roof_height := -INF
	for point: Vector3 in vertices: roof_height = maxf(roof_height, point.y - world.height_at(point.x, world.center_at(point.x)))
	check(tunnel.has_node("TunnelShellCollision") and roof_height > 6 and vertices.size() > 1000, "Tunnel has a swept arched roof and matching physical shell")
	var centre := Vector3(2664, world.height_at(2664, world.center_at(2664)) + 0.6, world.center_at(2664))
	var shell_shape := SphereShape3D.new()
	shell_shape.radius = 0.4
	var query := PhysicsShapeQueryParameters3D.new()
	query.shape = shell_shape
	query.transform.origin = centre + Vector3.UP
	query.exclude = [car.get_rid()]
	check(world.get_world_3d().direct_space_state.intersect_shape(query).is_empty(), "Arched tunnel leaves a real unobstructed vehicle passage")
	# Crossing fences close both shoulders and rails extend beyond the map.
	world.update_stream(744)
	car.position = Vector3(744, world.height_at(744, world.center_at(744)) + 8, world.center_at(744))
	var crossing := RouteHazard.new()
	crossing.world = world
	crossing.kind = "rail"
	crossing.feature_x = 744
	world.add_child(crossing)
	crossing.build()
	await frames(2)
	var shoulder_blocked := true
	for side: float in [-1, 1]:
		var z := world.center_at(734) + side * 20
		var ray := PhysicsRayQueryParameters3D.create(Vector3(730, world.height_at(744, world.center_at(744)) + 1, z), Vector3(738, world.height_at(744, world.center_at(744)) + 1, z))
		var hit := world.get_world_3d().direct_space_state.intersect_ray(ray)
		shoulder_blocked = shoulder_blocked and not hit.is_empty()
	check(shoulder_blocked, "Crossing fences physically stop bypasses on both shoulders")
	world.clock = -float(posmod(world.world_seed, 20))
	crossing._physics_process(0)
	check(absf(crossing._train.position.z - world.center_at(744)) > DriveWorld.HALF_WIDTH + 30, "Train starts beyond the map rather than appearing beside the road")
	game.weather.forced_mode = "Dážď"
	game.weather._process(4)
	var rain: float = game.weather.rain_mix
	game.weather.forced_mode = "Sneženie"
	game.weather._process(0.1)
	check(game.weather.rain_mix > 0 and game.weather.rain_mix < rain and game.weather.snow_mix > 0 and game.weather.snow_mix < 0.05, "Rain and snow cross-fade instead of changing particle meshes instantly")
	game.run.day_time = 0.82
	game._update_lighting()
	check(game.environment.ambient_light_source == Environment.AMBIENT_SOURCE_COLOR and game.environment.ambient_light_sky_contribution == 0.0 and game.environment.ambient_light_energy >= 0.28 and game.sun.light_energy >= 0.10, "Night uses actual ambient fill, not the nearly-black sky as its light source")
	var sign_root := Node3D.new()
	StopModels.roadside_sign(sign_root, Vector3.ZERO, "Čerpacia stanica", false)
	var title := sign_root.find_child("ServiceTitle", true, false) as Label3D
	check(title != null and title.billboard == BaseMaterial3D.BILLBOARD_DISABLED, "Roadside text stays coplanar with its sign board")
	sign_root.free()
	print("TERRAIN REWORK: %d checks; %d failures; max deformation %.2f ms" % [checks, failures, world.max_deformation_ms])
	game.queue_free()
	await frames(2)
	quit(0 if failures == 0 else 1)
